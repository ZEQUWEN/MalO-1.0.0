import { Router } from 'express';
import { config } from '../config.js';
import { db, newId } from '../store.js';
import { findAsset, isNetworkAllowed, NETWORKS } from '../networks.js';
import { cryptobot } from '../providers/cryptobot.js';
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
function decimalUnits(value, scale = 12) {
  const match = String(value ?? '').trim().match(/^(\d+)(?:\.(\d+))?$/);
  if (!match || match[2]?.length > scale) return null;
  const fraction = (match[2] || '').padEnd(scale, '0');
  return BigInt(match[1]) * 10n ** BigInt(scale) + BigInt(fraction);
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
  const expectedAmount = decimalUnits(stored.amount);
  const receivedAmount = decimalUnits(remote.amount);
  if (expectedAmount === null || receivedAmount === null || expectedAmount !== receivedAmount) {
    return { ok: false, code: 'INVOICE_AMOUNT_MISMATCH', message: 'Crypto Pay amount does not match' };
  }

  let payload = null;
  try {
    payload = JSON.parse(String(remote.payload || ''));
  } catch {
    return { ok: false, code: 'INVOICE_PAYLOAD_INVALID', message: 'Crypto Pay payload is not valid JSON' };
  }
  if (
    payload?.v !== 1 ||
    payload.payloadId !== stored.payloadId ||
    payload.userId !== stored.userId ||
    payload.planId !== config.subscription.planId ||
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

  stored.status = 'paid';
  stored.paidAt = remote.paid_at ? Date.parse(remote.paid_at) || Date.now() : Date.now();
  stored.txHash = remote.hash || stored.hash;
  db.saveInvoice(stored);
  return { subscription: activateCryptoSubscription(stored, source) };
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
        details: error.message,
      });
    }
    if (!amount || Number(amount) <= 0) {
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

    const invoice = {
      invoiceId: remote.invoice_id,
      payloadId,
      userId: req.userId,
      asset: asset.asset,
      network,
      networkTitle: NETWORKS[network]?.title || network,
      amount,
      status: String(remote.status || 'active').toLowerCase(),
      payUrl: remote.bot_invoice_url || remote.pay_url,
      miniAppUrl: remote.mini_app_invoice_url || null,
      webAppUrl: remote.web_app_invoice_url || null,
      hash: remote.hash || null,
      createdAt: Date.now(),
      expiresAt: remote.expiration_date ? Date.parse(remote.expiration_date) : Date.now() + config.cryptobot.invoiceExpiresIn * 1000,
      paidAt: null,
      txHash: null,
    };
    db.saveInvoice(invoice);
    return res.status(201).json({ ok: true, invoice });
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
        const settled = settleCryptoInvoice(stored, remote, 'poll');
        if (settled.validation) {
          return res.status(409).json({ ok: false, error: settled.validation });
        }
      }
    }
    return res.json({ ok: true, invoice: stored, subscription: serializeSubscription(getSubscription(stored.userId)) });
  }),
);

cryptoRouter.get(
  '/crypto/invoices',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    res.json({ ok: true, invoices: db.listInvoices(req.userId).sort((a, b) => b.createdAt - a.createdAt) });
  }),
);
