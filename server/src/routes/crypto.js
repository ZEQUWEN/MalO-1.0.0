import { Router } from 'express';
import { config } from '../config.js';
import { db, newId } from '../store.js';
import { findAsset, isNetworkAllowed, NETWORKS } from '../networks.js';
import { cryptobot } from '../providers/cryptobot.js';
import { asyncRoute, requireClientKey, requireUserId } from '../middleware.js';
import { activateSubscription, getSubscription, serializeSubscription } from '../subscriptions.js';

export const cryptoRouter = Router();

/** USD price of the plan converted into the chosen asset via Crypto Pay rates. */
async function quoteAmount(assetCode) {
  const asset = findAsset(assetCode);
  const usd = Number(config.subscription.priceUsd);
  if (!asset) return null;
  if (['USDT', 'USDC'].includes(asset.asset)) return usd.toFixed(asset.decimals);

  try {
    const rates = await cryptobot.getExchangeRates();
    const rate = rates.find(
      (item) => item.source?.toUpperCase() === asset.asset && item.target?.toUpperCase() === 'USD',
    );
    if (rate && Number(rate.rate) > 0) {
      return (usd / Number(rate.rate)).toFixed(asset.decimals);
    }
  } catch {
    /* fall through to a conservative default */
  }
  return null;
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
      return res
        .status(400)
        .json({ ok: false, error: { code: 'UNSUPPORTED_ASSET', message: `Asset ${assetCode} is not supported` } });
    }
    if (!isNetworkAllowed(assetCode, network)) {
      return res.status(400).json({
        ok: false,
        error: {
          code: 'UNSUPPORTED_NETWORK',
          message: `${assetCode} cannot be paid on ${network}. Allowed: ${asset.networks.join(', ')}`,
        },
      });
    }

    const amount = await quoteAmount(assetCode);
    if (!amount || Number(amount) <= 0) {
      return res
        .status(502)
        .json({ ok: false, error: { code: 'RATE_UNAVAILABLE', message: 'Exchange rate is temporarily unavailable' } });
    }

    const payloadId = newId('inv');
    const payload = JSON.stringify({
      v: 1,
      payloadId,
      userId: req.userId,
      planId: config.subscription.planId,
      network,
    });

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
      status: 'active',
      payUrl: remote.bot_invoice_url || remote.pay_url,
      miniAppUrl: remote.mini_app_invoice_url || null,
      webAppUrl: remote.web_app_invoice_url || null,
      hash: remote.hash || null,
      createdAt: Date.now(),
      expiresAt: remote.expiration_date
        ? Date.parse(remote.expiration_date)
        : Date.now() + config.cryptobot.invoiceExpiresIn * 1000,
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
  asyncRoute(async (req, res) => {
    const stored = db.getInvoice(req.params.invoiceId);
    if (!stored) {
      return res.status(404).json({ ok: false, error: { code: 'INVOICE_NOT_FOUND', message: 'Unknown invoice' } });
    }

    // The webhook is the source of truth, but polling keeps the UI responsive
    // if Telegram retries are delayed or the device reinstalled the app.
    if (stored.status === 'active') {
      try {
        const remote = await cryptobot.getInvoice(stored.invoiceId);
        if (remote && remote.status && remote.status !== stored.status) {
          stored.status = remote.status;
          stored.paidAt = remote.paid_at ? Date.parse(remote.paid_at) : stored.paidAt;
          db.saveInvoice(stored);
          if (remote.status === 'paid') {
            activateSubscription({
              userId: stored.userId,
              paymentMethod: `CRYPTO:${stored.asset}:${stored.network}`,
              transactionId: String(stored.invoiceId),
              amount: `${stored.amount} ${stored.asset}`,
              meta: { source: 'poll' },
            });
          }
        }
      } catch {
        /* keep the cached status on provider hiccups */
      }
    }

    return res.json({
      ok: true,
      invoice: stored,
      subscription: serializeSubscription(getSubscription(stored.userId)),
    });
  }),
);

/* --------------------------------------------------------------- list ---- */

cryptoRouter.get(
  '/crypto/invoices',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    res.json({ ok: true, invoices: db.listInvoices(req.userId).sort((a, b) => b.createdAt - a.createdAt) });
  }),
);
