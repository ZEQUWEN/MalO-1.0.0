import { Router } from 'express';
import { config } from '../config.js';
import { db, newId } from '../store.js';
import { findAsset, isNetworkAllowed, NETWORKS } from '../networks.js';
import { CryptoBotError, cryptobot } from '../providers/cryptobot.js';
import { asyncRoute, requireClientKey, requireUserId } from '../middleware.js';
import { activateSubscription, getSubscription, serializeSubscription } from '../subscriptions.js';

export const cryptoRouter = Router();

/** USD plan price converted into the selected Crypto Pay asset. */
async function quoteAmount(assetCode) {
  const asset = findAsset(assetCode);
  const usd = Number(config.subscription.priceUsd);
  if (!asset || !Number.isFinite(usd) || usd <= 0) return null;
  if (['USDT', 'USDC'].includes(asset.asset)) return usd.toFixed(asset.decimals);

  const rates = await cryptobot.getExchangeRates();
  const rate = rates.find(
    (item) => item.source?.toUpperCase() === asset.asset && item.target?.toUpperCase() === 'USD',
  );
  if (!rate || !Number.isFinite(Number(rate.rate)) || Number(rate.rate) <= 0) return null;
  return (usd / Number(rate.rate)).toFixed(asset.decimals);
}

/** Exact decimal comparison without floating point rounding surprises. */
export function decimalUnits(value, scale = 12) {
  const match = String(value ?? '').trim().match(/^(\d+)(?:\.(\d+))?$/);
  if (!match || match[2]?.length > scale) return null;
  const fraction = (match[2] || '').padEnd(scale, '0');
  return BigInt(match[1]) * 10n ** BigInt(scale) + BigInt(fraction);
}

const sameAmount = (left, right) => {
  const expected = decimalUnits(left);
  const received = decimalUnits(right);
  return expected !== null && received !== null && expected === received;
};

const isSecureUrl = (value) => {
  try {
    return new URL(String(value)).protocol === 'https:';
  } catch {
    return false;
  }
};

/**
 * Crypto Pay has already received the expected values in createInvoice. Check
 * its response before recording any local state: an incomplete/proxy response
 * must never turn into an invoice that could later grant Pro.
 */
export function validateCreatedCryptoInvoice(remote, expected) {
  const invoiceId = Number(remote?.invoice_id);
  if (!Number.isSafeInteger(invoiceId) || invoiceId < 1) {
    return { ok: false, code: 'CREATE_RESPONSE_ID_INVALID', message: 'Crypto Pay did not return a valid invoice id' };
  }
  if (String(remote.status || '').toLowerCase() !== 'active') {
    return { ok: false, code: 'CREATE_RESPONSE_STATUS_INVALID', message: 'Crypto Pay invoice is not active' };
  }
  if (String(remote.asset || '').toUpperCase() !== String(expected.asset).toUpperCase()) {
    return { ok: false, code: 'CREATE_RESPONSE_ASSET_MISMATCH', message: 'Crypto Pay returned another asset' };
  }
  if (!sameAmount(expected.amount, remote.amount)) {
    return { ok: false, code: 'CREATE_RESPONSE_AMOUNT_MISMATCH', message: 'Crypto Pay returned another amount' };
  }
  if (String(remote.payload || '') !== expected.payload) {
    return { ok: false, code: 'CREATE_RESPONSE_PAYLOAD_MISMATCH', message: 'Crypto Pay returned another invoice payload' };
  }
  const payUrl = remote.bot_invoice_url || remote.mini_app_invoice_url || remote.pay_url;
  if (!isSecureUrl(payUrl)) {
    return { ok: false, code: 'CREATE_RESPONSE_URL_INVALID', message: 'Crypto Pay did not return a secure payment URL' };
  }
  return { ok: true };
}

/**
 * Validates the provider invoice against the immutable invoice we created.
 * A signed webhook proves who sent the update; this check proves that it is the
 * exact MalO invoice, user, plan, asset and amount that may grant access.
 */
export function validateCryptoInvoice(stored, remote) {
  if (!stored || !remote) return { ok: false, code: 'INVOICE_NOT_FOUND', message: 'Invoice is unavailable' };
  if (Number(remote.invoice_id) !== Number(stored.invoiceId)) {
    return { ok: false, code: 'INVOICE_ID_MISMATCH', message: 'Crypto Pay invoice id does not match' };
  }
  if (String(remote.status || '').toLowerCase() !== 'paid') {
    return { ok: false, code: 'INVOICE_NOT_PAID', message: 'Crypto Pay invoice is not paid' };
  }
  if (String(remote.asset || '').toUpperCase() !== String(stored.asset).toUpperCase()) {
    return { ok: false, code: 'INVOICE_ASSET_MISMATCH', message: 'Crypto Pay asset does not match' };
  }
  if (!sameAmount(stored.amount, remote.amount)) {
    return { ok: false, code: 'INVOICE_AMOUNT_MISMATCH', message: 'Crypto Pay amount does not match' };
  }

  // Crypto Pay returns the exact `payload` string supplied at invoice creation.
  // Comparing it byte-for-byte rejects fields appended/reordered by a forged or
  // mismatched invoice; parsing is then only a defensive schema check.
  if (!stored.payload || String(remote.payload || '') !== stored.payload) {
    return { ok: false, code: 'INVOICE_PAYLOAD_MISMATCH', message: 'Crypto Pay invoice payload does not match' };
  }

  let payload = null;
  try {
    payload = JSON.parse(stored.payload);
  } catch {
    return { ok: false, code: 'INVOICE_PAYLOAD_INVALID', message: 'Stored invoice payload is not valid JSON' };
  }
  if (
    payload?.v !== 1 ||
    payload.payloadId !== stored.payloadId ||
    payload.userId !== stored.userId ||
    // The plan is frozen at invoice issue time. A later configuration deploy
    // must not turn an already-paid, otherwise valid invoice into a rejection.
    payload.planId !== stored.planId ||
    String(payload.network || '').toUpperCase() !== String(stored.network || '').toUpperCase()
  ) {
    return { ok: false, code: 'INVOICE_PAYLOAD_MISMATCH', message: 'Crypto Pay invoice payload does not match' };
  }
  return { ok: true, payload };
}

/** Activates/extends access while preserving an existing saved-card auto-renewal. */
export function activateCryptoSubscription(stored, source) {
  const previous = getSubscription(stored.userId);
  return activateSubscription({
    userId: stored.userId,
    paymentMethod: `CRYPTO:${stored.asset}:${stored.network}`,
    transactionId: String(stored.invoiceId),
    amount: `${stored.amount} ${stored.asset}`,
    autoRenew: Boolean(previous.autoRenew && previous.cardId),
    cardId: previous.cardId || null,
    meta: { source, network: stored.network, networkTitle: stored.networkTitle || null },
  });
}

/** Saves the verified invoice result exactly once and activates the subscription. */
export function settleCryptoInvoice(stored, remote, source) {
  if (stored.status === 'paid') return { duplicate: true, subscription: getSubscription(stored.userId) };
  const validation = validateCryptoInvoice(stored, remote);
  if (!validation.ok) return { validation };

  // Persist both the entitlement and the paid invoice before the handler may
  // acknowledge the provider. `flushNow` uses an atomic file replacement on
  // the Railway Volume, so a restart cannot acknowledge a payment and lose it.
  const subscription = activateCryptoSubscription(stored, source);
  stored.status = 'paid';
  stored.paidAt = remote.paid_at ? Date.parse(remote.paid_at) || Date.now() : Date.now();
  stored.txHash = remote.hash || stored.hash;
  db.saveInvoice(stored);
  db.flushNow();
  return { subscription };
}

// The shipped JSON store is intentionally a single-Railway-instance store. A
// per-invoice promise lock also serialises a UI poll and a webhook that arrive
// in the same event-loop tick, preventing an entitlement from being extended
// twice before `stored.status` has been observed as paid.
const settlementLocks = new Map();

export async function settleCryptoInvoiceOnce(stored, remote, source) {
  const key = String(stored.invoiceId);
  const active = settlementLocks.get(key);
  if (active) return active;

  const task = Promise.resolve().then(() => settleCryptoInvoice(stored, remote, source));
  settlementLocks.set(key, task);
  try {
    return await task;
  } finally {
    if (settlementLocks.get(key) === task) settlementLocks.delete(key);
  }
}

/** Keep provider/internal fields out of the Android API response. */
export function publicCryptoInvoice(invoice) {
  if (!invoice) return null;
  return {
    invoiceId: invoice.invoiceId,
    asset: invoice.asset,
    // Crypto Pay invoices are paid from the Crypto Bot balance. The old client
    // field remains for backwards compatibility, but is only an app-selected
    // funding hint and never a chain transaction confirmation.
    network: invoice.network,
    networkTitle: invoice.networkTitle,
    amount: invoice.amount,
    status: invoice.status,
    payUrl: invoice.payUrl,
    miniAppUrl: invoice.miniAppUrl,
    webAppUrl: invoice.webAppUrl,
    createdAt: invoice.createdAt,
    expiresAt: invoice.expiresAt,
    paidAt: invoice.paidAt,
  };
}

/* ------------------------------------------------------------ create ----- */

cryptoRouter.post(
  '/crypto/invoices',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    const assetCode = String(req.body?.asset || 'USDT').toUpperCase();
    const network = String(req.body?.network || '').toUpperCase() || findAsset(assetCode)?.defaultNetwork;
    const asset = findAsset(assetCode);
    if (!asset) {
      return res.status(400).json({ ok: false, error: { code: 'UNSUPPORTED_ASSET', message: `Asset ${assetCode} is not supported` } });
    }
    if (!isNetworkAllowed(assetCode, network)) {
      return res.status(400).json({
        ok: false,
        error: { code: 'UNSUPPORTED_NETWORK', message: `${assetCode} cannot be paid on ${network}` },
      });
    }

    let amount;
    try {
      amount = await quoteAmount(assetCode);
    } catch (error) {
      throw Object.assign(new Error('Crypto Pay exchange rate is temporarily unavailable'), {
        status: 502,
        code: 'RATE_UNAVAILABLE',
      });
    }
    if (!amount || !sameAmount(amount, amount) || Number(amount) <= 0) {
      return res.status(502).json({ ok: false, error: { code: 'RATE_UNAVAILABLE', message: 'Exchange rate is temporarily unavailable' } });
    }

    const payloadId = newId('inv');
    const payload = JSON.stringify({ v: 1, payloadId, userId: req.userId, planId: config.subscription.planId, network });
    const remote = await cryptobot.createInvoice({
      asset: asset.asset,
      amount,
      payload,
      description: `${config.subscription.planName} — ${config.subscription.periodDays} дней`,
      hiddenMessage: 'Подписка MalO Pro активирована. Возвращайся в приложение — я уже жду. 💜',
    });

    const created = validateCreatedCryptoInvoice(remote, { asset: asset.asset, amount, payload });
    if (!created.ok) {
      throw new CryptoBotError(created.message, { status: 502, code: created.code });
    }

    const invoice = {
      invoiceId: remote.invoice_id,
      payloadId,
      payload,
      userId: req.userId,
      planId: config.subscription.planId,
      asset: asset.asset,
      // Crypto Pay settles its own wallet balance and does not accept a chain
      // parameter for an invoice. Retain the app hint for older APKs only; it
      // is never treated as an on-chain payment proof.
      network,
      networkTitle: NETWORKS[network]?.title || network,
      amount,
      status: 'active',
      payUrl: remote.bot_invoice_url || remote.mini_app_invoice_url || remote.pay_url,
      miniAppUrl: remote.mini_app_invoice_url || null,
      webAppUrl: remote.web_app_invoice_url || null,
      hash: remote.hash || null,
      createdAt: Date.now(),
      expiresAt: remote.expiration_date ? Date.parse(remote.expiration_date) : Date.now() + config.cryptobot.invoiceExpiresIn * 1000,
      paidAt: null,
      txHash: null,
    };
    db.saveInvoice(invoice);
    db.flushNow();
    return res.status(201).json({ ok: true, invoice: publicCryptoInvoice(invoice) });
  }),
);

/* ------------------------------------------------------------- status ---- */

cryptoRouter.get(
  '/crypto/invoices/:invoiceId',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    const stored = db.getInvoice(req.params.invoiceId);
    if (!stored || stored.userId !== req.userId) {
      return res.status(404).json({ ok: false, error: { code: 'INVOICE_NOT_FOUND', message: 'Unknown invoice' } });
    }

    // Webhooks are authoritative, but provider polling gives the UI a fallback
    // if Crypto Pay has queued a retry. It applies the same strict validation.
    if (stored.status === 'active') {
      const remote = await cryptobot.getInvoice(stored.invoiceId);
      if (remote && String(remote.status || '').toLowerCase() === 'paid') {
        const settled = await settleCryptoInvoiceOnce(stored, remote, 'poll');
        if (settled.validation) {
          return res.status(409).json({ ok: false, error: settled.validation });
        }
      }
    }
    return res.json({
      ok: true,
      invoice: publicCryptoInvoice(stored),
      subscription: serializeSubscription(getSubscription(stored.userId)),
    });
  }),
);

cryptoRouter.get(
  '/crypto/invoices',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    res.json({
      ok: true,
      invoices: db.listInvoices(req.userId).sort((a, b) => b.createdAt - a.createdAt).map(publicCryptoInvoice),
    });
  }),
);
