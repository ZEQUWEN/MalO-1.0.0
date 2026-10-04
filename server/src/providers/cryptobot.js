/**
 * CryptoBot (Telegram @CryptoBot) — Crypto Pay API client + webhook verifier.
 *
 * Docs: https://help.crypt.bot/crypto-pay-api
 *
 * Webhook signature scheme used by Crypto Pay:
 *   secret    = SHA256(app_token)
 *   signature = HMAC_SHA256(secret, raw_request_body)  (hex)
 * delivered in the `crypto-pay-api-signature` header.
 */

import crypto from 'node:crypto';
import { config } from '../config.js';

export class CryptoBotError extends Error {
  constructor(message, { status = 502, code = 'CRYPTOBOT_ERROR', details = null } = {}) {
    super(message);
    this.name = 'CryptoBotError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

async function call(method, params = {}) {
  if (config.mockProviders) return mockCall(method, params);

  if (!config.cryptobot.token) {
    throw new CryptoBotError('CRYPTOBOT_TOKEN is not configured', {
      status: 503,
      code: 'CRYPTOBOT_NOT_CONFIGURED',
    });
  }

  const response = await fetch(`${config.cryptobot.apiBase}/${method}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Crypto-Pay-API-Token': config.cryptobot.token,
    },
    body: JSON.stringify(params),
  });

  let body;
  try {
    body = await response.json();
  } catch {
    throw new CryptoBotError(`Crypto Pay ${method}: non-JSON response (${response.status})`);
  }

  if (!response.ok || body.ok === false) {
    throw new CryptoBotError(body?.error?.name || `Crypto Pay ${method} failed`, {
      details: body?.error ?? body,
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
 * Verifies a Crypto Pay webhook delivery.
 *
 * @param {Buffer|string} rawBody exact bytes received on the wire
 * @param {string} signatureHeader value of `crypto-pay-api-signature`
 * @param {string} [token] override (tests)
 */
export function verifyCryptoBotSignature(rawBody, signatureHeader, token = config.cryptobot.token) {
  if (!token || !signatureHeader) return false;
  const secret = crypto.createHash('sha256').update(token).digest();
  const expected = crypto
    .createHmac('sha256', secret)
    .update(Buffer.isBuffer(rawBody) ? rawBody : Buffer.from(String(rawBody), 'utf8'))
    .digest('hex');

  const received = Buffer.from(String(signatureHeader).trim(), 'utf8');
  const expectedBuf = Buffer.from(expected, 'utf8');
  if (received.length !== expectedBuf.length) return false;
  return crypto.timingSafeEqual(received, expectedBuf);
}

/** Convenience used by docs/tests to produce a valid signature. */
export function signCryptoBotPayload(rawBody, token = config.cryptobot.token) {
  const secret = crypto.createHash('sha256').update(token).digest();
  return crypto
    .createHmac('sha256', secret)
    .update(Buffer.isBuffer(rawBody) ? rawBody : Buffer.from(String(rawBody), 'utf8'))
    .digest('hex');
}
