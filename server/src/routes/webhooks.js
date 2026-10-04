/**
 * Inbound webhooks.
 *
 *  POST /api/webhooks/cryptobot  — Crypto Pay (Telegram @CryptoBot)
 *  POST /api/webhooks/yookassa   — ЮKassa card notifications
 *
 * Both handlers are idempotent: Telegram and YooKassa retry deliveries until
 * they receive 2xx, so a repeated event must never grant a second period.
 */

import { Router } from 'express';
import { config } from '../config.js';
import { db } from '../store.js';
import { cryptobot, verifyCryptoBotSignature } from '../providers/cryptobot.js';
import { isTrustedYooKassaIp } from '../providers/yookassa.js';
import { asyncRoute } from '../middleware.js';
import { activateSubscription, getSubscription } from '../subscriptions.js';
import { settleCryptoInvoice } from './crypto.js';
import { upsertCardFromPaymentMethod } from './cards.js';
import { logWebhook } from '../logger.js';

export const webhooksRouter = Router();

const parseJson = (raw) => {
  try {
    return JSON.parse(raw.toString('utf8'));
  } catch {
    return null;
  }
};

/* ------------------------------------------------------------ CryptoBot -- */

webhooksRouter.post(
  '/webhooks/cryptobot',
  asyncRoute(async (req, res) => {
    // Crypto Pay signs the exact JSON bytes. This route is mounted with
    // express.raw() before the JSON parser; never stringify/reformat first.
    const raw = Buffer.isBuffer(req.body) ? req.body : Buffer.from(String(req.body || ''), 'utf8');
    const signature = req.get('crypto-pay-api-signature');
    if (!config.mockProviders && !verifyCryptoBotSignature(raw, signature)) {
      logWebhook('cryptobot', 'rejected', 'bad signature');
      return res.status(401).json({ ok: false, error: { code: 'BAD_SIGNATURE' } });
    }

    const update = parseJson(raw);
    if (!update || update.update_type !== 'invoice_paid' || !update.payload?.invoice_id) {
      return res.status(400).json({ ok: false, error: { code: 'BAD_PAYLOAD' } });
    }

    // Crypto Pay documents update_id as non-unique. Deduplication must use the
    // immutable invoice id (and the invoice's `paid` state), not update_id.
    const stored = db.getInvoice(update.payload.invoice_id);
    if (!stored) {
      logWebhook('cryptobot', 'ignored', `unknown invoice=${update.payload.invoice_id}`);
      return res.status(202).json({ ok: true, ignored: 'unknown_invoice' });
    }
    const dedupeKey = `cryptobot:invoice_paid:${stored.invoiceId}`;
    if (stored.status === 'paid' || db.seenWebhook(dedupeKey)) {
      return res.json({ ok: true, duplicate: true });
    }

    // Do not trust a webhook field merely because it looks plausible. After
    // HMAC verification, request the provider invoice and compare id, status,
    // asset, decimal amount, and the server-issued opaque payload.
    const remote = await cryptobot.getInvoice(stored.invoiceId);
    if (!remote) {
      const error = new Error('Crypto Pay did not return the invoice for webhook verification');
      error.status = 502;
      error.code = 'CRYPTOBOT_INVOICE_UNAVAILABLE';
      throw error;
    }
    const settled = settleCryptoInvoice(stored, remote, 'webhook');
    if (settled.validation) {
      logWebhook('cryptobot', 'rejected', `${settled.validation.code} invoice=${stored.invoiceId}`);
      return res.status(409).json({ ok: false, error: settled.validation });
    }

    db.markWebhook(dedupeKey);
    logWebhook('cryptobot', 'invoice_paid', `invoice=${stored.invoiceId} ${stored.amount} ${stored.asset} via ${stored.network}`);
    return res.json({ ok: true, duplicate: Boolean(settled.duplicate) });
  }),
);

/* ------------------------------------------------------------- YooKassa -- */

webhooksRouter.post('/webhooks/yookassa', (req, res) => {
  if (config.yookassa.verifyNetwork && !config.mockProviders) {
    const remote =
      (req.get('x-forwarded-for') || '').split(',')[0].trim() || req.socket?.remoteAddress || '';
    if (!isTrustedYooKassaIp(remote)) {
      logWebhook('yookassa', 'rejected', `untrusted source ${remote}`);
      return res.status(401).json({ ok: false, error: { code: 'UNTRUSTED_SOURCE' } });
    }
  }

  const raw = Buffer.isBuffer(req.body) ? req.body : Buffer.from(String(req.body || ''), 'utf8');
  const notification = parseJson(raw);
  const object = notification?.object;
  if (!notification?.event || !object?.id) {
    return res.status(400).json({ ok: false, error: { code: 'BAD_PAYLOAD' } });
  }

  const dedupeKey = `yookassa:${notification.event}:${object.id}`;
  if (db.seenWebhook(dedupeKey)) return res.json({ ok: true, duplicate: true });
  db.markWebhook(dedupeKey);

  const stored = db.getPayment(object.id);
  const userId = stored?.userId || object.metadata?.userId;
  if (!userId) return res.status(202).json({ ok: true, ignored: 'unknown_payment' });

  if (stored) {
    stored.status = object.status;
    db.savePayment(stored);
  }

  switch (notification.event) {
    case 'payment.succeeded': {
      logWebhook('yookassa', 'payment.succeeded', `payment=${object.id} ${object.amount?.value ?? ''} ${object.amount?.currency ?? ''}`);
      const isSbp = stored?.paymentMethod === 'sbp' || object.payment_method?.type === 'sbp';
      const wantsSave = !isSbp && (stored ? stored.saveCard : object.metadata?.saveCard === 'true');
      const card = wantsSave ? upsertCardFromPaymentMethod(userId, object.payment_method) : null;
      const previous = getSubscription(userId);
      const cardId = card?.cardId || previous.cardId || null;
      activateSubscription({
        userId,
        paymentMethod: stored?.kind === 'recurring' ? 'CARD_RECURRING' : isSbp ? 'SBP' : 'CARD',
        transactionId: object.id,
        amount: `${object.amount?.value ?? config.subscription.priceRub} ${object.amount?.currency ?? 'RUB'}`,
        // A manual SBP extension must not silently turn off an existing card
        // auto-renewal. Conversely, SBP itself never creates a reusable token.
        autoRenew: card?.cardId != null || (previous.autoRenew && cardId != null),
        cardId,
        meta: { source: 'webhook', brand: card?.brand, last4: card?.last4, paymentMethod: isSbp ? 'sbp' : 'bank_card' },
      });
      break;
    }
    case 'payment.canceled':
    case 'payment.waiting_for_capture':
    case 'refund.succeeded':
    default:
      break;
  }

  return res.json({ ok: true });
});
