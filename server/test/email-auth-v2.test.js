import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { requestEmailCode, verifyEmailCode, resolveSession, revokeSession } from '../src/v2/email-auth.js';

let db;
let pool;
const pepper = 'test-only-32-byte-pepper-never-use-in-production';
const delivered = new Map();
const sendCode = async (email, code) => delivered.set(email, code);
test.before(async () => {
  db = new PGlite();
  await db.exec(await fs.readFile(new URL('../src/v2/schema.sql', import.meta.url), 'utf8'));
  pool = { query: (...args) => db.query(...args), connect: async () => ({ query: (...args) => db.query(...args), release() {} }) };
});
test.after(async () => db?.close());

test('email code creates a hashed session and cannot be replayed', async () => {
  const email = 'user@example.invalid';
  assert.deepEqual(await requestEmailCode(pool, ' USER@example.invalid ', { pepper, sendCode }), { result: 'sent' });
  const code = delivered.get(email);
  assert.match(code, /^\d{8}$/);
  assert.deepEqual(await requestEmailCode(pool, email, { pepper, sendCode }), { result: 'rate_limited' });
  assert.equal(await verifyEmailCode(pool, email, 'xxxxxxxx', { pepper }), null);
  const session = await verifyEmailCode(pool, email, code, { pepper });
  assert.match(session.sessionToken, /^[A-Za-z0-9_-]{43}$/);
  assert.equal(await verifyEmailCode(pool, email, code, { pepper }), null);
  const userId = await resolveSession(pool, session.sessionToken);
  assert.equal(typeof userId, 'string');
  const record = await db.query('SELECT token_hash FROM v2_sessions WHERE user_id = $1', [userId]);
  assert.notEqual(record.rows[0].token_hash, session.sessionToken);
  assert.equal(await resolveSession(pool, 'not-a-session'), null);
  await revokeSession(pool, session.sessionToken);
  assert.equal(await resolveSession(pool, session.sessionToken), null);
});

test('wrong codes and expiration prevent login', async () => {
  const email = 'wrong@example.invalid';
  await requestEmailCode(pool, email, { pepper, sendCode });
  const actual = delivered.get(email);
  const wrong = actual === '12345678' ? '12345679' : '12345678';
  for (let i = 0; i < 5; i++) assert.equal(await verifyEmailCode(pool, email, wrong, { pepper }), null);
  assert.equal(await verifyEmailCode(pool, email, actual, { pepper }), null);

  const expiredEmail = 'expired@example.invalid';
  await requestEmailCode(pool, expiredEmail, { pepper, sendCode });
  await db.query("UPDATE v2_email_challenges SET expires_at = clock_timestamp() - interval '1 second' WHERE email = $1", [expiredEmail]);
  assert.equal(await verifyEmailCode(pool, expiredEmail, delivered.get(expiredEmail), { pepper }), null);
});

test('the auth functions reject missing configuration and invalid addresses', async () => {
  await assert.rejects(requestEmailCode(pool, 'ok@example.invalid', { pepper: '', sendCode }));
  await assert.rejects(requestEmailCode(pool, 'not an email', { pepper, sendCode }));
  await assert.rejects(requestEmailCode(pool, 'ok@example.invalid', { pepper }));
});
