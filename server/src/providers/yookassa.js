/**
 * YooKassa (ЮKassa) client — card billing for Visa / Mastercard / МИР.
 *
 * Docs: https://yookassa.ru/developers/api
 *
 * Flow implemented here:
 *   1. `createPayment({ savePaymentMethod: true })` -> returns a `confirmation_url`
 *      that the app opens in a Custom Tab (3-D Secure happens there).
 *   2. YooKassa calls `/api/webhooks/yookassa` with `payment.succeeded`;
 *      the notification carries `payment_method.id` + `payment_method.card`.
 *   3. We store that `payment_method.id` as a re-usable card token, which lets
 *      us charge later periods without any card data touching our servers.
 *
 * PCI DSS note: raw PAN/CVC never reach this gateway nor the APK — the user
 * types them on YooKassa's hosted confirmation page.
 */

import crypto from 'node:crypto';
import { config } from '../config.js';

export class YooKassaError extends Error {
  constructor(message, { status = 502, code = 'YOOKASSA_ERROR', details = null } = {}) {
    super(message);
    this.name = 'YooKassaError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

async function call(path, { method = 'POST', body, idempotenceKey } = {}) {
  if (config.mockProviders) return mockCall(path, { method, body });

  if (!config.yookassa.shopId || !config.yookassa.secretKey) {
    throw new YooKassaError('YOOKASSA_SHOP_ID / YOOKASSA_SECRET_KEY are not configured', {
      status: 503,
      code: 'YOOKASSA_NOT_CONFIGURED',
    });
  }

  const auth = Buffer.from(`${config.yookassa.shopId}:${config.yookassa.secretKey}`).toString('base64');
  const response = await fetch(`${config.yookassa.apiBase}${path}`, {
    method,
    headers: {
      Authorization: `Basic ${auth}`,
      'Content-Type': 'application/json',
      ...(method === 'POST' ? { 'Idempotence-Key': idempotenceKey || crypto.randomUUID() } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  const text = await response.text();
  let payload = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    throw new YooKassaError(`YooKassa ${path}: non-JSON response (${response.status})`);
  }

  if (!response.ok) {
    throw new YooKassaError(payload?.description || `YooKassa ${path} failed`, {
      status: response.status >= 500 ? 502 : 400,
      code: payload?.code || 'YOOKASSA_ERROR',
      details: payload,
    });
  }
  return payload;
}

/* ------------------------------------------------------------------ mock -- */

const mockPayments = new Map();

function mockCall(path, { body }) {
  if (path === '/payments' && body) {
    const id = crypto.randomUUID();
    const savedMethod = body.payment_method_id;
    const methodType = savedMethod ? 'bank_card' : body.payment_method_data?.type || 'bank_card';
    const canSave = methodType === 'bank_card';
    const payment = {
      id,
      status: savedMethod ? 'succeeded' : 'pending',
      paid: Boolean(savedMethod),
      amount: body.amount,
      description: body.description,
      metadata: body.metadata,
      created_at: new Date().toISOString(),
      confirmation: savedMethod
        ? undefined
        : {
            type: 'redirect',
            confirmation_url:
              methodType === 'sbp'
                ? `https://yoomoney.ru/checkout/payments/sbp?orderId=${id}`
                : `https://yoomoney.ru/checkout/payments/v2/contract?orderId=${id}`,
          },
      payment_method: canSave
        ? {
            type: 'bank_card',
            id: savedMethod || crypto.randomUUID(),
            saved: Boolean(body.save_payment_method || savedMethod),
            title: 'Bank card *4477',
            card: {
              first6: '220220',
              last4: '4477',
              expiry_month: '12',
              expiry_year: '2030',
              card_type: 'MIR',
              issuer_country: 'RU',
            },
          }
        : {
            type: 'sbp',
            id: crypto.randomUUID(),
            saved: false,
          },
    };
    mockPayments.set(id, payment);
    return Promise.resolve(payment);
  }
  if (path.startsWith('/payments/') && path.endsWith('/capture')) {
    const id = path.split('/')[2];
    const payment = mockPayments.get(id);
    if (payment) payment.status = 'succeeded';
    return Promise.resolve(payment || {});
  }
  if (path.startsWith('/payments/')) {
    const id = path.split('/')[2];
    return Promise.resolve(mockPayments.get(id) || { id, status: 'pending' });
  }
  return Promise.resolve({});
}

/** Test helper: flip a mock payment to succeeded. */
export function __mockMarkSucceeded(paymentId) {
  const payment = mockPayments.get(paymentId);
  if (payment) {
    payment.status = 'succeeded';
    payment.paid = true;
  }
  return payment;
}

/* ------------------------------------------------------------------- api -- */

export const yookassa = {
  /** Charge with a redirect confirmation; optionally remember the card. */
  createPayment: ({
    amount,
    currency = 'RUB',
    description,
    metadata,
    savePaymentMethod = false,
    paymentMethod = 'bank_card',
    returnUrl = config.yookassa.returnUrl,
    idempotenceKey,
  }) => {
    const isSbp = paymentMethod === 'sbp';
    return call('/payments', {
      idempotenceKey,
      body: {
        amount: { value: String(amount), currency },
        capture: true,
        description,
        metadata,
        // Explicitly select the YooKassa flow. It keeps the in-app surface out
        // of PCI scope: PAN/CVC or the SBP banking app are handled by YooKassa.
        payment_method_data: { type: isSbp ? 'sbp' : 'bank_card' },
        save_payment_method: !isSbp && savePaymentMethod,
        confirmation: { type: 'redirect', return_url: returnUrl },
      },
    });
  },

  /** Recurring charge against a previously saved `payment_method.id`. */
  chargeSavedCard: ({ amount, currency = 'RUB', description, metadata, paymentMethodId, idempotenceKey }) =>
    call('/payments', {
      idempotenceKey,
      body: {
        amount: { value: String(amount), currency },
        capture: true,
        description,
        metadata,
        payment_method_id: paymentMethodId,
      },
    }),

  getPayment: (paymentId) => call(`/payments/${paymentId}`, { method: 'GET' }),
};

/* ------------------------------------------------------- network allowlist */

const ipToBigInt = (ip) => {
  if (ip.includes(':')) {
    // Expand IPv6.
    const [head, tail = ''] = ip.split('::');
    const headParts = head ? head.split(':').filter(Boolean) : [];
    const tailParts = tail ? tail.split(':').filter(Boolean) : [];
    const fill = new Array(8 - headParts.length - tailParts.length).fill('0');
    const parts = [...headParts, ...fill, ...tailParts];
    return parts.reduce((acc, part) => (acc << 16n) + BigInt(parseInt(part || '0', 16)), 0n);
  }
  return ip.split('.').reduce((acc, part) => (acc << 8n) + BigInt(Number(part)), 0n);
};

/** Checks a remote address against YooKassa's documented notification subnets. */
export function isTrustedYooKassaIp(remoteAddress, networks = config.yookassa.allowedNetworks) {
  if (!remoteAddress) return false;
  const ip = String(remoteAddress).replace(/^::ffff:/, '');
  const isV6 = ip.includes(':');
  let value;
  try {
    value = ipToBigInt(ip);
  } catch {
    return false;
  }

  return networks.some((cidr) => {
    const [base, bitsRaw] = cidr.split('/');
    if (base.includes(':') !== isV6) return false;
    const total = isV6 ? 128 : 32;
    const bits = Number(bitsRaw ?? total);
    let baseValue;
    try {
      baseValue = ipToBigInt(base);
    } catch {
      return false;
    }
    const mask = bits === 0 ? 0n : ((1n << BigInt(bits)) - 1n) << BigInt(total - bits);
    return (value & mask) === (baseValue & mask);
  });
}

/** Normalises YooKassa card types into the brands the UI knows about. */
export function normalizeBrand(cardType) {
  const value = String(cardType || '').toUpperCase();
  if (value.includes('MIR')) return 'MIR';
  if (value.includes('MASTER')) return 'MASTERCARD';
  if (value.includes('VISA')) return 'VISA';
  if (value.includes('MAESTRO')) return 'MAESTRO';
  if (value.includes('AMERICAN') || value === 'AMEX') return 'AMEX';
  if (value.includes('UNION')) return 'UNIONPAY';
  if (value.includes('JCB')) return 'JCB';
  if (value.includes('DISCOVER')) return 'DISCOVER';
  return 'UNKNOWN';
}
