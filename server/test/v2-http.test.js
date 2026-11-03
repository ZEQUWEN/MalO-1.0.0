import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import { PGlite } from '@electric-sql/pglite';
import { createApp } from '../src/app.js';

let db, server, baseUrl;
const delivered = new Map();
const verificationToken = 'test-kofi-token';
test.before(async () => {
  db = new PGlite();
  await db.exec(await fs.readFile(new URL('../src/v2/schema.sql', import.meta.url), 'utf8'));
  const pool = { query: (...a) => db.query(...a), connect: async () => ({ query: (...a) => db.query(...a), release() {} }) };
  const app = createApp({ v2: { pool, pepper: 'test-only-32-byte-pepper-never-production',
    sendCode: async (email, code) => delivered.set(email, code),
    kofiVerificationToken: verificationToken, kofiUrl: 'https://ko-fi.com/example', priceUsd: '4.99', periodDays: 30 } });
  server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});
test.after(async () => { server?.close(); await db?.close(); });
const post = (url, data, token) => fetch(baseUrl + url, { method: 'POST', headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(data) });
const get = (url, token) => fetch(baseUrl + url, { headers: token ? { authorization: `Bearer ${token}` } : {} });
const hook = (body) => fetch(baseUrl + '/api/v2/webhooks/kofi', { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ data: JSON.stringify(body) }) });
async function login(email) {
  assert.equal((await post('/api/v2/auth/request-code', { email })).status, 200);
  const res = await post('/api/v2/auth/verify-code', { email, code: delivered.get(email) });
  assert.equal(res.status, 200);
  return (await res.json()).sessionToken;
}

test('authenticated HTTP checkout and webhook activate only the intended account exactly once', async () => {
  const owner = await login('owner@example.invalid');
  const other = await login('other@example.invalid');
  const unauthorized = await post('/api/v2/kofi/intents', { userId: 'someone-else' });
  assert.equal(unauthorized.status, 401);
  const checkout = await post('/api/v2/kofi/intents', { userId: 'someone-else', amount: '0.01' }, owner);
  assert.equal(checkout.status, 201);
  const intent = await checkout.json();
  assert.equal(intent.amount, '4.99');
  assert.equal(intent.userId, undefined);
  assert.equal((await post('/api/v2/kofi/intent-status', { paymentToken: intent.paymentToken }, other)).status, 404);
  assert.equal((await (await post('/api/v2/kofi/intent-status', { paymentToken: intent.paymentToken }, owner)).json()).status, 'pending');
  const event = { message_id: crypto.randomUUID(), kofi_transaction_id: crypto.randomUUID(), type: 'Donation',
    is_subscription_payment: false, message: intent.paymentToken, amount: '4.99', currency: 'USD', verification_token: verificationToken };
  assert.equal((await hook({ ...event, verification_token: 'bad' })).status, 401);
  assert.equal((await (await get('/api/v2/subscription', owner)).json()).subscription.status, 'inactive');
  assert.equal((await hook(event)).status, 200);
  assert.equal((await (await post('/api/v2/kofi/intent-status', { paymentToken: intent.paymentToken }, owner)).json()).status, 'paid');
  const first = (await (await get('/api/v2/subscription', owner)).json()).subscription;
  assert.equal(first.status, 'active');
  assert.equal((await (await get('/api/v2/subscription?userId=owner', other)).json()).subscription.status, 'inactive');
  assert.equal((await post('/api/v2/malo/chat', { messages: [] }, other)).status, 403);
  // The active account reaches the provider-configuration guard, not 403.
  assert.equal((await post('/api/v2/malo/chat', { messages: [] }, owner)).status, 503);
  assert.equal((await hook(event)).status, 200);
  assert.equal((await (await get('/api/v2/subscription', owner)).json()).subscription.currentPeriodEnd, first.currentPeriodEnd);
  assert.equal((await hook({ ...event, amount: '100.00' })).status, 200);
  assert.equal((await db.query('SELECT * FROM v2_kofi_conflicts')).rows.length, 1);
  assert.equal((await post('/api/v2/auth/logout', {}, owner)).status, 200);
  assert.equal((await get('/api/v2/subscription', owner)).status, 401);
});

test('unsafe legacy APIs are disabled even with an arbitrary userId and old app key', async () => {
  assert.equal((await get('/api/subscription?userId=malo-123456')).status, 404);
  assert.equal((await post('/api/webhooks/yookassa', { event: 'payment.succeeded', object: { id: 'x' } })).status, 404);
  assert.equal((await post('/api/malo/chat', { userId: 'malo-123456' })).status, 404);
});

test('webhook parser rejects malformed data and requires a configured database', async () => {
  assert.equal((await post('/api/v2/webhooks/kofi', {})).status, 400);
  const app = createApp();
  const s = app.listen(0, '127.0.0.1');
  await new Promise(resolve => s.once('listening', resolve));
  const res = await fetch(`http://127.0.0.1:${s.address().port}/api/v2/subscription`);
  assert.equal(res.status, 503);
  s.close();
});
