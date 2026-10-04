/**
 * Crypto Pay failure mapping.
 *
 * These assertions exist because a single opaque 502 for every provider
 * failure sends the operator looking in the wrong place: a revoked token, a
 * provider outage and a real rate limit each need a different fix. This file
 * deliberately imports only the provider, so it runs without express.
 */

import test from 'node:test';
import assert from 'node:assert/strict';

process.env.CRYPTOBOT_TOKEN = '12345:TEST_TOKEN';
process.env.MALO_MOCK_PROVIDERS = '0';
process.env.CRYPTOBOT_API_BASE = 'https://pay.crypt.bot/api';

const { cryptobot, CryptoBotError } = await import('../src/providers/cryptobot.js');

const realFetch = globalThis.fetch;

/** Replaces fetch with one canned Crypto Pay response. */
function stubFetch({ status, body, headers = {} }) {
  globalThis.fetch = async () =>
    new Response(typeof body === 'string' ? body : JSON.stringify(body), {
      status,
      headers: { 'content-type': 'application/json', ...headers },
    });
}

test.afterEach(() => {
  globalThis.fetch = realFetch;
});

const newInvoice = () =>
  cryptobot.createInvoice({ asset: 'USDT', amount: '4.99', payload: '{}', description: 'test' });

test('a Crypto Pay 429 keeps its own status, code and Retry-After', async () => {
  stubFetch({
    status: 429,
    headers: { 'retry-after': '17' },
    body: { ok: false, error: { code: 429, name: 'TOO_MANY_REQUESTS' } },
  });

  const error = await newInvoice().then(
    () => null,
    (e) => e,
  );

  assert.ok(error instanceof CryptoBotError);
  assert.equal(error.status, 429, 'an upstream rate limit must not be reported as 502');
  assert.equal(error.code, 'CRYPTOBOT_RATE_LIMITED');
  assert.equal(error.retryAfter, 17);
  assert.equal(error.details.upstreamStatus, 429);
});

test('a 429 without Retry-After still maps to the rate-limit code', async () => {
  stubFetch({ status: 429, body: { ok: false, error: { name: 'TOO_MANY_REQUESTS' } } });

  const error = await newInvoice().catch((e) => e);

  assert.equal(error.code, 'CRYPTOBOT_RATE_LIMITED');
  assert.equal(error.retryAfter, null, 'a missing header must not become NaN');
});

test('a revoked or wrong-network token is reported as CRYPTOBOT_UNAUTHORIZED', async () => {
  for (const status of [401, 403]) {
    stubFetch({ status, body: { ok: false, error: { name: 'UNAUTHORIZED' } } });

    const error = await newInvoice().catch((e) => e);

    assert.equal(error.code, 'CRYPTOBOT_UNAUTHORIZED', `status ${status}`);
    assert.equal(error.status, 502);
    // The operator needs to see which endpoint the token was refused by:
    // a mainnet token sent to testnet-pay fails exactly this way.
    assert.equal(error.details.apiBase, 'https://pay.crypt.bot/api');
  }
});

test('an ordinary provider failure stays a 502 and keeps the upstream status', async () => {
  stubFetch({ status: 400, body: { ok: false, error: { name: 'METHOD_NOT_FOUND' } } });

  const error = await newInvoice().catch((e) => e);

  assert.equal(error.status, 502);
  assert.equal(error.code, 'CRYPTOBOT_ERROR');
  assert.equal(error.message, 'METHOD_NOT_FOUND');
  assert.equal(error.details.upstreamStatus, 400);
});

test('an HTML error page from an intermediary is not mistaken for a provider reply', async () => {
  stubFetch({
    status: 429,
    body: '<html><body>error code: 1015</body></html>',
    headers: { 'content-type': 'text/html' },
  });

  const error = await newInvoice().catch((e) => e);

  assert.equal(error.code, 'CRYPTOBOT_BAD_RESPONSE');
  assert.equal(error.details.upstreamStatus, 429);
  assert.match(error.message, /non-JSON response \(429\)/);
});

test('a missing token fails closed before any network call', async () => {
  globalThis.fetch = async () => assert.fail('no request may be sent without a token');
  const { config } = await import('../src/config.js');
  const saved = config.cryptobot.token;
  config.cryptobot.token = '';
  try {
    const error = await newInvoice().catch((e) => e);
    assert.equal(error.code, 'CRYPTOBOT_NOT_CONFIGURED');
    assert.equal(error.status, 503);
  } finally {
    config.cryptobot.token = saved;
  }
});
