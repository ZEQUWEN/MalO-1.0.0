import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

process.env.MALO_MOCK_PROVIDERS = '1';
process.env.CRYPTOBOT_TOKEN = '12345:TEST_TOKEN';
process.env.MALO_DATA_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'malo-gw-'));

const { initStore, resetStore, db } = await import('../src/store.js');
const { createApp } = await import('../src/app.js');
const { signCryptoBotPayload, verifyCryptoBotSignature, __mockMarkPaid } = await import('../src/providers/cryptobot.js');
const { isTrustedYooKassaIp } = await import('../src/providers/yookassa.js');
const { isNetworkAllowed, buildCatalog } = await import('../src/networks.js');
const { validateCreatedCryptoInvoice } = await import('../src/routes/crypto.js');

initStore();

let server;
let baseUrl;

test.before(async () => {
  server = createApp().listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

test.after(() => server?.close());

const api = (path, init = {}) =>
  fetch(`${baseUrl}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers || {}) },
  });

const USER = 'malo-test-user-0001';

test('health endpoint reports mock mode', async () => {
  const res = await api('/api/health');
  const body = await res.json();
  assert.equal(res.status, 200);
  assert.equal(body.ok, true);
  assert.equal(body.mock, true);
});

test('catalog exposes crypto networks for every asset', async () => {
  const res = await api('/api/catalog');
  const body = await res.json();
  assert.equal(body.ok, true);
  const usdt = body.crypto.assets.find((a) => a.asset === 'USDT');
  const networkIds = usdt.networks.map((n) => n.id);
  assert.ok(networkIds.includes('TRON'));
  assert.ok(networkIds.includes('TON'));
  assert.ok(networkIds.includes('ETH'));
  assert.ok(networkIds.includes('SOLANA'));
  assert.ok(body.crypto.assets.some((a) => a.asset === 'BTC'));
  assert.deepEqual(body.card.brands.slice(0, 3), ['VISA', 'MASTERCARD', 'MIR']);
  assert.deepEqual(body.card.paymentMethods, ['bank_card', 'sbp']);
  assert.equal(body.card.supportsSbp, true);
});

test('network validation rejects impossible asset/network pairs', () => {
  assert.equal(isNetworkAllowed('USDT', 'TRON'), true);
  assert.equal(isNetworkAllowed('BTC', 'TON'), false);
  assert.equal(buildCatalog().length > 5, true);
});

test('crypto invoice creation requires a supported network', async () => {
  const res = await api('/api/crypto/invoices', {
    method: 'POST',
    body: JSON.stringify({ userId: USER, asset: 'BTC', network: 'SOLANA' }),
  });
  assert.equal(res.status, 400);
  const body = await res.json();
  assert.equal(body.error.code, 'UNSUPPORTED_NETWORK');
});

test('cryptobot webhook activates Pro and is idempotent', async () => {
  const created = await api('/api/crypto/invoices', {
    method: 'POST',
    body: JSON.stringify({ userId: USER, asset: 'USDT', network: 'TRON' }),
  });
  const { invoice } = await created.json();
  assert.equal(created.status, 201);
  assert.equal(invoice.network, 'TRON');
  assert.ok(invoice.payUrl.startsWith('https://t.me/'));
  // Android receives no internal ownership/payload values that could be reused
  // to manufacture a different provider invoice.
  assert.equal(invoice.payloadId, undefined);
  assert.equal(invoice.userId, undefined);
  assert.equal(invoice.txHash, undefined);

  const stored = db.getInvoice(invoice.invoiceId);
  // The webhook handler independently reads this provider invoice before granting Pro.
  __mockMarkPaid(invoice.invoiceId);
  const update = {
    update_id: 90001,
    update_type: 'invoice_paid',
    request_date: new Date().toISOString(),
    payload: {
      invoice_id: invoice.invoiceId,
      asset: 'USDT',
      amount: invoice.amount,
      status: 'paid',
      paid_at: new Date().toISOString(),
      payload: JSON.stringify({ v: 1, payloadId: stored.payloadId, userId: USER, network: 'TRON' }),
      hash: 'abc123',
    },
  };
  const raw = JSON.stringify(update);

  const hook = await fetch(`${baseUrl}/api/webhooks/cryptobot`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'crypto-pay-api-signature': signCryptoBotPayload(raw, process.env.CRYPTOBOT_TOKEN),
    },
    body: raw,
  });
  assert.equal(hook.status, 200);
  // The webhook acknowledges only after the paid invoice + entitlement have
  // been durably written to the Railway-compatible state file.
  const persisted = JSON.parse(
    fs.readFileSync(path.join(process.env.MALO_DATA_DIR, 'gateway-state.json'), 'utf8'),
  );
  assert.equal(persisted.invoices[String(invoice.invoiceId)].status, 'paid');
  assert.equal(persisted.subscriptions[USER].status, 'active');

  const sub = await (await api(`/api/subscription?userId=${USER}`)).json();
  assert.equal(sub.subscription.status, 'active');
  assert.equal(sub.subscription.paymentMethod, 'CRYPTO:USDT:TRON');
  const firstEnd = sub.subscription.currentPeriodEnd;

  // Replay must not extend the period.
  const replay = await fetch(`${baseUrl}/api/webhooks/cryptobot`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'crypto-pay-api-signature': signCryptoBotPayload(raw, process.env.CRYPTOBOT_TOKEN),
    },
    body: raw,
  });
  const replayBody = await replay.json();
  assert.equal(replayBody.duplicate, true);
  const sub2 = await (await api(`/api/subscription?userId=${USER}`)).json();
  assert.equal(sub2.subscription.currentPeriodEnd, firstEnd);
});

test('cryptobot webhook rejects a signed update when the provider invoice amount differs', async () => {
  const user = 'malo-crypto-amount-user';
  const created = await api('/api/crypto/invoices', {
    method: 'POST',
    body: JSON.stringify({ userId: user, asset: 'USDT', network: 'TRON' }),
  });
  const { invoice } = await created.json();
  const stored = db.getInvoice(invoice.invoiceId);
  const providerInvoice = __mockMarkPaid(invoice.invoiceId);
  providerInvoice.amount = '999.00';

  const update = {
    update_id: 90002,
    update_type: 'invoice_paid',
    payload: {
      invoice_id: invoice.invoiceId,
      asset: 'USDT',
      amount: invoice.amount,
      status: 'paid',
      payload: JSON.stringify({ v: 1, payloadId: stored.payloadId, userId: user, network: 'TRON' }),
    },
  };
  const raw = JSON.stringify(update);
  const hook = await fetch(`${baseUrl}/api/webhooks/cryptobot`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'crypto-pay-api-signature': signCryptoBotPayload(raw, process.env.CRYPTOBOT_TOKEN),
    },
    body: raw,
  });
  assert.equal(hook.status, 409);
  const error = await hook.json();
  assert.equal(error.error.code, 'INVOICE_AMOUNT_MISMATCH');
  const subscription = await (await api(`/api/subscription?userId=${user}`)).json();
  assert.equal(subscription.subscription.status, 'inactive');
});

test('cryptobot webhook rejects an unsigned paid update even in mock mode', async () => {
  const user = 'malo-unsigned-crypto-user';
  const created = await api('/api/crypto/invoices', {
    method: 'POST',
    body: JSON.stringify({ userId: user, asset: 'USDT', network: 'TRON' }),
  });
  const { invoice } = await created.json();
  __mockMarkPaid(invoice.invoiceId);

  const raw = JSON.stringify({
    update_id: 90003,
    update_type: 'invoice_paid',
    payload: { invoice_id: invoice.invoiceId },
  });
  const hook = await fetch(`${baseUrl}/api/webhooks/cryptobot`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: raw,
  });
  assert.equal(hook.status, 401);
  const error = await hook.json();
  assert.equal(error.error.code, 'BAD_SIGNATURE');

  const subscription = await (await api(`/api/subscription?userId=${user}`)).json();
  assert.equal(subscription.subscription.status, 'inactive');
});

test('created Crypto Pay invoice must preserve the exact server payload', () => {
  const payload = '{"v":1,"payloadId":"inv_123","userId":"malo-test-user","planId":"malo_pro_monthly","network":"TRON"}';
  const expected = { asset: 'USDT', amount: '4.99', payload };
  const valid = {
    invoice_id: 42,
    status: 'active',
    asset: 'USDT',
    amount: '4.9900',
    payload,
    bot_invoice_url: 'https://t.me/CryptoBot?start=42',
  };
  assert.equal(validateCreatedCryptoInvoice(valid, expected).ok, true);
  assert.equal(
    validateCreatedCryptoInvoice({ ...valid, payload: `${payload} ` }, expected).code,
    'CREATE_RESPONSE_PAYLOAD_MISMATCH',
  );
  assert.equal(
    validateCreatedCryptoInvoice({ ...valid, bot_invoice_url: 'http://not-secure.example' }, expected).code,
    'CREATE_RESPONSE_URL_INVALID',
  );
});

test('cryptobot signature verification rejects tampered payloads', () => {
  const body = '{"update_id":1}';
  const sig = signCryptoBotPayload(body, 'secret-token');
  assert.equal(verifyCryptoBotSignature(body, sig, 'secret-token'), true);
  assert.equal(verifyCryptoBotSignature('{"update_id":2}', sig, 'secret-token'), false);
  assert.equal(verifyCryptoBotSignature(body, 'deadbeef', 'secret-token'), false);
});

test('yookassa webhook saves the card and enables autopay, cancel works', async () => {
  const user = 'malo-card-user-0002';
  const checkout = await api('/api/cards/checkout', {
    method: 'POST',
    body: JSON.stringify({ userId: user, saveCard: true }),
  });
  assert.equal(checkout.status, 201);
  const { payment } = await checkout.json();
  assert.ok(payment.confirmationUrl);

  const notification = {
    type: 'notification',
    event: 'payment.succeeded',
    object: {
      id: payment.paymentId,
      status: 'succeeded',
      paid: true,
      amount: { value: '499.00', currency: 'RUB' },
      metadata: { userId: user, saveCard: 'true' },
      payment_method: {
        type: 'bank_card',
        id: 'pm-token-1',
        saved: true,
        title: 'Bank card *4477',
        card: { first6: '220220', last4: '4477', expiry_month: '12', expiry_year: '2030', card_type: 'MIR' },
      },
    },
  };

  const hook = await fetch(`${baseUrl}/api/webhooks/yookassa`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(notification),
  });
  assert.equal(hook.status, 200);

  const cards = await (await api(`/api/cards?userId=${user}`)).json();
  assert.equal(cards.cards.length, 1);
  assert.equal(cards.cards[0].brand, 'MIR');
  assert.equal(cards.cards[0].last4, '4477');
  assert.equal(cards.cards[0].isDefault, true);
  assert.equal(cards.subscription.status, 'active');
  assert.equal(cards.subscription.autoRenew, true);
  // The PAN token must never leave the gateway.
  assert.equal(cards.cards[0].paymentMethodId, undefined);

  const canceled = await (
    await api('/api/subscription/cancel', { method: 'POST', body: JSON.stringify({ userId: user }) })
  ).json();
  assert.equal(canceled.subscription.autoRenew, false);
  assert.equal(canceled.subscription.cancelAtPeriodEnd, true);
  assert.equal(canceled.subscription.status, 'active'); // keeps access until period end

  const resumed = await (
    await api('/api/subscription/resume', { method: 'POST', body: JSON.stringify({ userId: user }) })
  ).json();
  assert.equal(resumed.subscription.cancelAtPeriodEnd, false);
  assert.equal(resumed.subscription.autoRenew, true);

  // Recurring charge against the saved token.
  const charged = await (
    await api('/api/subscription/charge', { method: 'POST', body: JSON.stringify({ userId: user }) })
  ).json();
  assert.equal(charged.subscription.status, 'active');
  assert.equal(charged.subscription.paymentMethod, 'CARD_RECURRING');

  // Deleting the card disables autopay.
  const cardId = cards.cards[0].cardId;
  const deleted = await (
    await api(`/api/cards/${cardId}?userId=${user}`, { method: 'DELETE' })
  ).json();
  assert.equal(deleted.cards.length, 0);
  assert.equal(deleted.subscription.autoRenew, false);
});

test('SBP checkout is a YooKassa one-time rail and never creates a saved card', async () => {
  const user = 'malo-sbp-user-0003';
  const checkout = await api('/api/checkout', {
    method: 'POST',
    body: JSON.stringify({ userId: user, paymentMethod: 'sbp', saveCard: true }),
  });
  assert.equal(checkout.status, 201);
  const { payment } = await checkout.json();
  assert.equal(payment.paymentMethod, 'sbp');
  assert.equal(payment.saveCard, false);
  assert.ok(payment.confirmationUrl.includes('/sbp?'));

  const notification = {
    type: 'notification',
    event: 'payment.succeeded',
    object: {
      id: payment.paymentId,
      status: 'succeeded',
      paid: true,
      amount: { value: '499.00', currency: 'RUB' },
      metadata: { userId: user, paymentMethod: 'sbp', saveCard: 'false' },
      payment_method: { type: 'sbp', id: 'sbp-operation-1', saved: false },
    },
  };
  const hook = await api('/api/webhooks/yookassa', { method: 'POST', body: JSON.stringify(notification) });
  assert.equal(hook.status, 200);

  const result = await (await api(`/api/cards?userId=${user}`)).json();
  assert.equal(result.cards.length, 0);
  assert.equal(result.subscription.status, 'active');
  assert.equal(result.subscription.paymentMethod, 'SBP');
  assert.equal(result.subscription.autoRenew, false);
});

test('yookassa ip allowlist matches documented subnets', () => {
  assert.equal(isTrustedYooKassaIp('185.71.76.5'), true);
  assert.equal(isTrustedYooKassaIp('77.75.156.11'), true);
  assert.equal(isTrustedYooKassaIp('8.8.8.8'), false);
  assert.equal(isTrustedYooKassaIp('2a02:5180::1'), true);
});

test('unknown api route returns a structured 404', async () => {
  const res = await api('/api/nope');
  assert.equal(res.status, 404);
  const body = await res.json();
  assert.equal(body.error.code, 'NOT_FOUND');
});

test.after(() => {
  resetStore();
  fs.rmSync(process.env.MALO_DATA_DIR, { recursive: true, force: true });
});
