/**
 * CryptoBot (Telegram @CryptoBot) — Crypto Pay API client + webhook verifier.
 *
 * Current docs: https://help.send.tg/en/articles/10279948-crypto-pay-api
 *
 * Webhook signature scheme used by Crypto Pay:
 *   secret    = SHA256(app_token)
 *   signature = HMAC_SHA256(secret, raw_request_body)  (hex)
 * delivered in the `crypto-pay-api-signature` header.
 */

import crypto from 'node:crypto';
import { config } from '../config.js';

export class CryptoBotError extends Error {
  constructor(message, { status = 502, code = 'CRYPTOBOT_ERROR', details = null, retryAfter = null } = {}) {
    super(message);
    this.name = 'CryptoBotError';
    this.status = status;
    this.code = code;
    this.details = details;
    /** Seconds reported by the provider; surfaced as a Retry-After header. */
    this.retryAfter = retryAfter;
  }
}

async function call(method, params = {}) {
  // A token is necessary even in mock mode: webhook verification must never be
  // disabled just because a Railway variable was set incorrectly.
  if (!config.cryptobot.token) {
    throw new CryptoBotError('CRYPTOBOT_TOKEN is not configured', {
      status: 503,
      code: 'CRYPTOBOT_NOT_CONFIGURED',
    });
  }

  if (config.mockProviders) return mockCall(method, params);

  let response;
  try {
    response = await fetch(`${config.cryptobot.apiBase}/${method}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Crypto-Pay-API-Token': config.cryptobot.token,
      },
      body: JSON.stringify(params),
      signal: AbortSignal.timeout(config.cryptobot.requestTimeoutMs),
    });
  } catch (error) {
    const timedOut = error?.name === 'TimeoutError' || error?.name === 'AbortError';
    throw new CryptoBotError(timedOut ? `Crypto Pay ${method} timed out` : `Crypto Pay ${method} is unavailable`, {
      status: 502,
      code: timedOut ? 'CRYPTOBOT_TIMEOUT' : 'CRYPTOBOT_UNAVAILABLE',
    });
  }

  let body;
  try {
    body = await response.json();
  } catch {
    throw new CryptoBotError(`Crypto Pay ${method}: non-JSON response (${response.status})`, {
      code: 'CRYPTOBOT_BAD_RESPONSE',
      details: { upstreamStatus: response.status },
    });
  }

  if (!response.ok || body.ok === false) {
    const upstreamStatus = response.status;
    const upstreamError = body?.error ?? null;

    // Collapsing every provider failure into one opaque 502 is what makes a
    // Crypto Pay outage indistinguishable from a revoked token or a genuine
    // rate limit. Keep the cause in the error code so the operator is not left
    // guessing which of the three it was.
    if (upstreamStatus === 429) {
      const header = Number(response.headers?.get?.('retry-after'));
      throw new CryptoBotError(`Crypto Pay rate limit reached on ${method}`, {
        status: 429,
        code: 'CRYPTOBOT_RATE_LIMITED',
        retryAfter: Number.isFinite(header) && header > 0 ? header : null,
        details: { upstreamStatus, error: upstreamError },
      });
    }

    // 401/403 here means CRYPTOBOT_TOKEN is wrong, revoked, or points at the
    // other network (mainnet token against testnet-pay, or the reverse).
    if (upstreamStatus === 401 || upstreamStatus === 403) {
      throw new CryptoBotError(`Crypto Pay rejected the app token on ${method}`, {
        status: 502,
        code: 'CRYPTOBOT_UNAUTHORIZED',
        details: { upstreamStatus, error: upstreamError, apiBase: config.cryptobot.apiBase },
      });
    }

    throw new CryptoBotError(upstreamError?.name || `Crypto Pay ${method} failed`, {
      details: { upstreamStatus, error: upstreamError ?? body },
    });
  }
  if (!Object.hasOwn(body || {}, 'result')) {
    throw new CryptoBotError(`Crypto Pay ${method}: response has no result`, {
      code: 'CRYPTOBOT_BAD_RESPONSE',
    });
  }
  return body.result;
}

/* ------------------------------------------------------------------ mock -- */

const mockInvoices = new Map();

function mockCall(method, params) {
  if (method === 'createInvoice') {
    const invoiceId = Math.floor(Math.random() * 9_000_000) + 1_000_000;
    const invoice = {
      invoice_id: invoiceId,
      hash: crypto.randomBytes(8).toString('hex'),
      asset: params.asset,
      amount: params.amount,
      status: 'active',
      payload: params.payload,
      description: params.description,
      bot_invoice_url: `https://t.me/CryptoBot?start=mock_${invoiceId}`,
      mini_app_invoice_url: `https://t.me/CryptoBot/app?startapp=mock_${invoiceId}`,
      created_at: new Date().toISOString(),
      expiration_date: new Date(Date.now() + config.cryptobot.invoiceExpiresIn * 1000).toISOString(),
    };
    mockInvoices.set(invoiceId, invoice);
    return Promise.resolve(invoice);
  }
  if (method === 'getInvoices') {
    const ids = String(params.invoice_ids || '')
      .split(',')
      .filter(Boolean)
      .map(Number);
    const items = ids.map((id) => mockInvoices.get(id)).filter(Boolean);
    return Promise.resolve({ items });
  }
  if (method === 'getMe') {
    return Promise.resolve({ app_id: 0, name: 'MalO Mock', payment_processing_bot_username: 'CryptoBot' });
  }
  if (method === 'getExchangeRates') {
    return Promise.resolve([
      { source: 'USDT', target: 'USD', rate: '1.0' },
      { source: 'TON', target: 'USD', rate: '2.70' },
      { source: 'BTC', target: 'USD', rate: '66000' },
      { source: 'ETH', target: 'USD', rate: '3100' },
      { source: 'SOL', target: 'USD', rate: '145' },
      { source: 'TRX', target: 'USD', rate: '0.12' },
      { source: 'BNB', target: 'USD', rate: '580' },
      { source: 'LTC', target: 'USD', rate: '72' },
      { source: 'USDC', target: 'USD', rate: '1.0' },
    ]);
  }
  return Promise.resolve({});
}

/** Test helper: mark a mock invoice as paid so webhook flows can be exercised. */
export function __mockMarkPaid(invoiceId) {
  const invoice = mockInvoices.get(Number(invoiceId));
  if (invoice) {
    invoice.status = 'paid';
    invoice.paid_at = new Date().toISOString();
  }
  return invoice;
}

/* ------------------------------------------------------------------- api -- */

export const cryptobot = {
  getMe: () => call('getMe'),

  /**
   * @param {{asset: string, amount: string, payload: string, description: string, hiddenMessage?: string}} opts
   */
  createInvoice: ({ asset, amount, payload, description, hiddenMessage }) =>
    call('createInvoice', {
      currency_type: 'crypto',
      asset,
      amount: String(amount),
      description: description.slice(0, 1024),
      hidden_message: (hiddenMessage || '').slice(0, 2048) || undefined,
      payload,
      paid_btn_name: 'openBot',
      paid_btn_url: config.cryptobot.paidBtnUrl,
      allow_comments: false,
      allow_anonymous: true,
      expires_in: config.cryptobot.invoiceExpiresIn,
    }),

  getInvoice: async (invoiceId) => {
    const result = await call('getInvoices', { invoice_ids: String(invoiceId) });
    return result?.items?.[0] || null;
  },

  getExchangeRates: () => call('getExchangeRates'),
};

/* ------------------------------------------------------------- signature -- */

/**
 * Verifies a Crypto Pay webhook delivery using the exact bytes received over
 * HTTP. Do not parse and re-stringify the JSON before this check: whitespace
 * or key order would invalidate the provider signature.
 *
 * @param {Buffer|string} rawBody exact bytes received on the wire
 * @param {string} signatureHeader value of `crypto-pay-api-signature`
 * @param {string} [token] override (tests)
 */
export function verifyCryptoBotSignature(rawBody, signatureHeader, token = config.cryptobot.token) {
  const signature = String(signatureHeader || '').trim().toLowerCase();
  if (!token || !/^[a-f0-9]{64}$/.test(signature)) return false;

  const secret = crypto.createHash('sha256').update(token).digest();
  const expected = crypto
    .createHmac('sha256', secret)
    .update(Buffer.isBuffer(rawBody) ? rawBody : Buffer.from(String(rawBody), 'utf8'))
    .digest();

  // Decode rather than compare strings so timingSafeEqual always receives two
  // equal-size byte arrays. Invalid/non-hex headers are rejected above.
  const received = Buffer.from(signature, 'hex');
  return received.length === expected.length && crypto.timingSafeEqual(received, expected);
}

/** Convenience used by docs/tests to produce a valid signature. */
export function signCryptoBotPayload(rawBody, token = config.cryptobot.token) {
  const secret = crypto.createHash('sha256').update(token).digest();
  return crypto
    .createHmac('sha256', secret)
    .update(Buffer.isBuffer(rawBody) ? rawBody : Buffer.from(String(rawBody), 'utf8'))
    .digest('hex');
}
