import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import { PGlite } from '@electric-sql/pglite';
import { createKoFiIntent, hasValidVerificationToken, parseMinorUnits, settleKoFiEvent } from '../src/v2/kofi.js';

let db;
let pool;
const USER = '5bced501-c0f1-4200-acd4-b13dd6311c8e';
const plan = { amountMinor: 499, currency: 'USD', periodDays: 30 };
const verificationToken = 'test-ko-fi-verification-secret';
const settle = (payment) => settleKoFiEvent(pool, payment, { verificationToken });

test.before(async () => {
  db = new PGlite();
  await db.exec(await fs.readFile(new URL('../src/v2/schema.sql', import.meta.url), 'utf8'));
  await db.query('INSERT INTO v2_users (id, email) VALUES ($1, $2)', [USER, 'test@example.invalid']);
  // PGlite exercises PostgreSQL SQL semantics in one process. Real multi-client
  // locking and network failures still require integration tests on PostgreSQL.
  pool = { query: (...args) => db.query(...args), connect: async () => ({ query: (...args) => db.query(...args), release() {} }) };
});
test.after(async () => db?.close());

const event = (token, overrides = {}) => ({
  message_id: crypto.randomUUID(), kofi_transaction_id: crypto.randomUUID(),
  type: 'Donation', is_subscription_payment: false, amount: '4.99', currency: 'USD', message: token, verification_token: verificationToken,
  ...overrides,
});

test('money and shared verification token fail closed', () => {
  assert.equal(parseMinorUnits('4.99'), '499');
  assert.equal(parseMinorUnits('4.9'), '490');
  for (const invalid of [4.99, '4.999', '-1.00', '1e3', 'NaN', ' 4.99', '']) assert.equal(parseMinorUnits(invalid), null);
  assert.equal(hasValidVerificationToken('secret', 'secret'), true);
  for (const bad of ['', 'wrong', null]) assert.equal(hasValidVerificationToken(bad, 'secret'), false);
  assert.equal(hasValidVerificationToken('', ''), false);
});

test('a paid intent is atomically consumed and both provider IDs are replay protected', async () => {
  const { paymentToken } = await createKoFiIntent(pool, USER, plan);
  const raw = await db.query('SELECT token_hash FROM v2_kofi_intents');
  assert.notEqual(raw.rows[0].token_hash, paymentToken);
  const paid = event(paymentToken);
  assert.deepEqual(await settle(paid), { result: 'settled' });
  const before = (await db.query('SELECT current_period_end FROM v2_subscriptions WHERE user_id = $1', [USER])).rows[0].current_period_end;
  assert.deepEqual(await settle(paid), { result: 'duplicate' });
  assert.deepEqual(await settle({ ...paid, message_id: crypto.randomUUID() }), { result: 'conflict' });
  assert.deepEqual(await settle({ ...paid, kofi_transaction_id: crypto.randomUUID() }), { result: 'conflict' });
  assert.deepEqual(await settle(event(paymentToken)), { result: 'review' });
  const after = (await db.query('SELECT current_period_end FROM v2_subscriptions WHERE user_id = $1', [USER])).rows[0].current_period_end;
  assert.equal(+new Date(after), +new Date(before));
});

test('wrong amount, currency, missing code and recurring payments go to review without consuming code', async () => {
  const { paymentToken } = await createKoFiIntent(pool, USER, plan);
  for (const overrides of [
    { amount: '4.98' }, { currency: 'EUR' }, { message: '' },
    { type: 'Subscription', is_subscription_payment: true },
  ]) assert.deepEqual(await settle(event(paymentToken, overrides)), { result: 'review' });
  assert.deepEqual(await settle(event(paymentToken)), { result: 'settled' });
});

test('expired codes go to review, without granting entitlement', async () => {
  const { paymentToken } = await createKoFiIntent(pool, USER, plan);
  await db.query("UPDATE v2_kofi_intents SET created_at = now() - interval '2 hours', expires_at = now() - interval '1 hour' WHERE token_hash = $1", [crypto.createHash('sha256').update(paymentToken).digest('hex')]);
  const before = (await db.query('SELECT current_period_end FROM v2_subscriptions WHERE user_id = $1', [USER])).rows[0].current_period_end;
  assert.deepEqual(await settle(event(paymentToken)), { result: 'review' });
  const after = (await db.query('SELECT current_period_end FROM v2_subscriptions WHERE user_id = $1', [USER])).rows[0].current_period_end;
  assert.equal(+new Date(after), +new Date(before));
});

test('subscription extension uses the existing end date', async () => {
  const before = (await db.query('SELECT current_period_end FROM v2_subscriptions WHERE user_id = $1', [USER])).rows[0].current_period_end;
  const { paymentToken } = await createKoFiIntent(pool, USER, plan);
  assert.deepEqual(await settle(event(paymentToken)), { result: 'settled' });
  const after = (await db.query('SELECT current_period_end FROM v2_subscriptions WHERE user_id = $1', [USER])).rows[0].current_period_end;
  assert.equal(+new Date(after) - +new Date(before), 30 * 24 * 60 * 60 * 1000);
});

test('missing or incorrect Ko-fi verification token cannot create any event or subscription', async () => {
  const { paymentToken } = await createKoFiIntent(pool, USER, plan);
  const before = (await db.query('SELECT count(*) AS n FROM v2_kofi_events')).rows[0].n;
  const payload = event(paymentToken, { verification_token: 'wrong' });
  await assert.rejects(settle(payload), /verification token/);
  await assert.rejects(settleKoFiEvent(pool, event(paymentToken)), /verification token/);
  const after = (await db.query('SELECT count(*) AS n FROM v2_kofi_events')).rows[0].n;
  assert.equal(after, before);
  assert.deepEqual(await settle(event(paymentToken)), { result: 'settled' });
});

test('a database error rolls back the event and consumption so delivery can retry', async () => {
  const { paymentToken } = await createKoFiIntent(pool, USER, plan);
  const paid = event(paymentToken);
  const failingPool = {
    connect: async () => ({
      query: (sql, params) => {
        if (sql.includes('INSERT INTO v2_subscriptions')) throw new Error('simulated write failure');
        return db.query(sql, params);
      },
      release() {},
    }),
  };
  await assert.rejects(settleKoFiEvent(failingPool, paid, { verificationToken }), /simulated write failure/);
  assert.equal((await db.query('SELECT * FROM v2_kofi_events WHERE transaction_id = $1', [paid.kofi_transaction_id])).rows.length, 0);
  assert.deepEqual(await settle(paid), { result: 'settled' });
});
