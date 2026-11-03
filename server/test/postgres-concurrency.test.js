import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import pg from 'pg';
import { createKoFiIntent, settleKoFiEvent } from '../src/v2/kofi.js';
import { migrate } from '../src/v2/database.js';
import { requestEmailCode, verifyEmailCode, resolveSession } from '../src/v2/email-auth.js';

// Opt-in only: this creates/drops an isolated schema in TEST_DATABASE_URL.
test('real PostgreSQL serializes duplicate and competing Ko-fi deliveries', { skip: !process.env.TEST_DATABASE_URL }, async () => {
  const schema = `malo_test_${crypto.randomBytes(8).toString('hex')}`;
  const admin = new pg.Pool({ connectionString: process.env.TEST_DATABASE_URL });
  const pool = new pg.Pool({ connectionString: process.env.TEST_DATABASE_URL, options: `-c search_path=${schema}`, max: 12 });
  try {
    await admin.query(`CREATE SCHEMA ${schema}`);
    await migrate(pool);
    await migrate(pool); // migration is repeatable
    const pepper = 'test-only-32-byte-pepper-not-for-real-use';
    let code;
    await requestEmailCode(pool, 'race@example.invalid', { pepper, sendCode: async (_email, value) => { code = value; } });
    const sessions = await Promise.all([
      verifyEmailCode(pool, 'race@example.invalid', code, { pepper }),
      verifyEmailCode(pool, 'race@example.invalid', code, { pepper }),
    ]);
    const issued = sessions.filter(Boolean);
    assert.equal(issued.length, 1);
    assert.ok(await resolveSession(pool, issued[0].sessionToken));
    const user = crypto.randomUUID();
    await pool.query('INSERT INTO v2_users (id, email) VALUES ($1, $2)', [user, 'test@example.invalid']);
    const plan = { amountMinor: 499, currency: 'USD', periodDays: 30 };
    const { paymentToken } = await createKoFiIntent(pool, user, plan);
    const event = { message_id: crypto.randomUUID(), kofi_transaction_id: crypto.randomUUID(),
      verification_token: 'test-secret', message: paymentToken, amount: '4.99', currency: 'USD',
      type: 'Donation', is_subscription_payment: false };
    const results = await Promise.all(Array.from({ length: 8 }, () => settleKoFiEvent(pool, event, { verificationToken: 'test-secret' })));
    assert.equal(results.filter(x => x.result === 'settled').length, 1);
    assert.equal(results.filter(x => x.result === 'duplicate').length, 7);
    const end = (await pool.query('SELECT current_period_end FROM v2_subscriptions WHERE user_id = $1', [user])).rows[0].current_period_end;
    const other = { ...event, kofi_transaction_id: crypto.randomUUID(), message_id: crypto.randomUUID() };
    assert.equal((await settleKoFiEvent(pool, other, { verificationToken: 'test-secret' })).result, 'review');
    assert.equal(+((await pool.query('SELECT current_period_end FROM v2_subscriptions WHERE user_id = $1', [user])).rows[0].current_period_end), +end);
    const nextIntent = await createKoFiIntent(pool, user, plan);
    const a = { ...event, message: nextIntent.paymentToken, message_id: crypto.randomUUID(), kofi_transaction_id: crypto.randomUUID() };
    const b = { ...a, message_id: crypto.randomUUID(), kofi_transaction_id: crypto.randomUUID() };
    const competing = await Promise.all([settleKoFiEvent(pool, a, { verificationToken: 'test-secret' }), settleKoFiEvent(pool, b, { verificationToken: 'test-secret' })]);
    assert.deepEqual(competing.map(x => x.result).sort(), ['review', 'settled']);
    const extended = (await pool.query('SELECT current_period_end FROM v2_subscriptions WHERE user_id = $1', [user])).rows[0].current_period_end;
    assert.equal(+extended - +end, 30 * 24 * 60 * 60 * 1000);
  } finally {
    await pool.end();
    await admin.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`);
    await admin.end();
  }
});
