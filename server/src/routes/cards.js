import crypto from 'node:crypto';
import { Router } from 'express';
import { config, isYooKassaMethodAvailable } from '../config.js';
import { db, newId } from '../store.js';
import { yookassa, normalizeBrand } from '../providers/yookassa.js';
import { asyncRoute, requireClientKey, requireUserId } from '../middleware.js';
import {
  activateSubscription,
  getSubscription,
  serializeSubscription,
  setAutoRenewCard,
} from '../subscriptions.js';

export const cardsRouter = Router();

/** Shape returned to the app. Contains no PAN — only the provider's token. */
export function publicCard(card) {
  if (!card) return null;
  return {
    cardId: card.cardId,
    brand: card.brand,
    last4: card.last4,
    first6: card.first6,
    expiryMonth: card.expiryMonth,
    expiryYear: card.expiryYear,
    holderName: card.holderName || null,
    issuerCountry: card.issuerCountry || null,
    isDefault: Boolean(card.isDefault),
    createdAt: card.createdAt,
  };
}

/**
 * Upserts a saved card from a YooKassa `payment_method` object.
 * `payment_method.id` is the re-usable token for recurring charges.
 */
export function upsertCardFromPaymentMethod(userId, paymentMethod, { makeDefault = true } = {}) {
  if (!paymentMethod?.id || paymentMethod.saved === false) return null;
  const card = paymentMethod.card || {};

  const existing = db
    .listCards(userId)
    .find((item) => item.paymentMethodId === paymentMethod.id);

  const record = existing || {
    cardId: newId('card'),
    userId,
    createdAt: Date.now(),
  };

  record.paymentMethodId = paymentMethod.id;
  record.brand = normalizeBrand(card.card_type);
  record.first6 = card.first6 || null;
  record.last4 = card.last4 || '****';
  record.expiryMonth = card.expiry_month || null;
  record.expiryYear = card.expiry_year || null;
  record.issuerCountry = card.issuer_country || null;
  record.holderName = paymentMethod.card_holder || null;
  record.title = paymentMethod.title || null;
  record.deletedAt = null;

  if (makeDefault) {
    db.clearDefaultCards(userId);
    record.isDefault = true;
  } else if (record.isDefault === undefined) {
    record.isDefault = db.listCards(userId).length === 0;
  }

  db.saveCard(record);
  return record;
}

/* ----------------------------------------------------------- checkout --- */

/**
 * Starts a YooKassa checkout. The app only opens the confirmation URL:
 * 3-D Secure PAN entry and the SBP banking flow both happen on YooKassa's
 * protected page, never in the APK or on this server.
 *
 * `/cards/checkout` is retained as a backwards-compatible alias for existing
 * APKs. New clients call `/checkout` and pass `paymentMethod`: `bank_card` or
 * `sbp`. SBP is a one-time renewal rail; only a bank card may be stored for
 * automatic recurring charges.
 */
async function startCheckout(req, res) {
  const requestedMethod = String(req.body?.paymentMethod || 'bank_card').toLowerCase();
  if (!['bank_card', 'sbp'].includes(requestedMethod)) {
    return res.status(400).json({
      ok: false,
      error: { code: 'UNSUPPORTED_PAYMENT_METHOD', message: 'Use bank_card or sbp' },
    });
  }
  if (!isYooKassaMethodAvailable(requestedMethod)) {
    return res.status(503).json({
      ok: false,
      error: {
        code: 'PAYMENT_METHOD_UNAVAILABLE',
        message: `YooKassa ${requestedMethod} checkout is not configured`,
      },
    });
  }

  const saveCard = requestedMethod === 'bank_card' && req.body?.saveCard !== false;
  const idempotenceKey = String(req.body?.idempotenceKey || crypto.randomUUID());

  const payment = await yookassa.createPayment({
    amount: config.subscription.priceRub,
    currency: 'RUB',
    description: `${config.subscription.planName} — ${config.subscription.periodDays} дней`,
    paymentMethod: requestedMethod,
    savePaymentMethod: saveCard,
    returnUrl: req.body?.returnUrl || config.yookassa.returnUrl,
    metadata: {
      userId: req.userId,
      planId: config.subscription.planId,
      paymentMethod: requestedMethod,
      saveCard: String(saveCard),
    },
    idempotenceKey,
  });

  const record = {
    paymentId: payment.id,
    userId: req.userId,
    provider: 'yookassa',
    kind: 'checkout',
    paymentMethod: requestedMethod,
    status: payment.status,
    amount: `${config.subscription.priceRub} RUB`,
    saveCard,
    createdAt: Date.now(),
    confirmationUrl: payment.confirmation?.confirmation_url || null,
  };
  db.savePayment(record);

  // Mock/instant-success path (used only by the automated test suite).
  if (payment.status === 'succeeded') {
    const card = saveCard ? upsertCardFromPaymentMethod(req.userId, payment.payment_method) : null;
    const previous = getSubscription(req.userId);
    activateSubscription({
      userId: req.userId,
      paymentMethod: requestedMethod === 'sbp' ? 'SBP' : 'CARD',
      transactionId: payment.id,
      amount: record.amount,
      autoRenew: card?.cardId != null || (previous.autoRenew && previous.cardId != null),
      cardId: card?.cardId || previous.cardId || null,
    });
  }

  return res.status(201).json({
    ok: true,
    payment: {
      paymentId: payment.id,
      status: payment.status,
      paymentMethod: requestedMethod,
      confirmationUrl: record.confirmationUrl,
      amount: record.amount,
      saveCard,
    },
    subscription: serializeSubscription(getSubscription(req.userId)),
  });
}

cardsRouter.post('/checkout', requireClientKey, requireUserId, asyncRoute(startCheckout));
cardsRouter.post('/cards/checkout', requireClientKey, requireUserId, asyncRoute(startCheckout));

/** Poll a pending card payment (fallback when the webhook is late). */
cardsRouter.get(
  '/cards/payments/:paymentId',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    const stored = db.getPayment(req.params.paymentId);
    if (!stored || stored.userId !== req.userId) {
      return res.status(404).json({ ok: false, error: { code: 'PAYMENT_NOT_FOUND', message: 'Unknown payment' } });
    }
    if (['pending', 'waiting_for_capture'].includes(stored.status)) {
      try {
        const remote = await yookassa.getPayment(stored.paymentId);
        if (remote?.status && remote.status !== stored.status) {
          stored.status = remote.status;
          db.savePayment(stored);
          if (remote.status === 'succeeded') {
            const isSbp = stored.paymentMethod === 'sbp' || remote.payment_method?.type === 'sbp';
            const card = !isSbp && stored.saveCard
              ? upsertCardFromPaymentMethod(stored.userId, remote.payment_method)
              : null;
            const previous = getSubscription(stored.userId);
            activateSubscription({
              userId: stored.userId,
              paymentMethod: isSbp ? 'SBP' : 'CARD',
              transactionId: remote.id,
              amount: stored.amount,
              autoRenew: card?.cardId != null || (previous.autoRenew && previous.cardId != null),
              cardId: card?.cardId || previous.cardId || null,
              meta: { source: 'poll' },
            });
          }
        }
      } catch {
        /* keep cached status */
      }
    }
    return res.json({
      ok: true,
      payment: stored,
      subscription: serializeSubscription(getSubscription(stored.userId)),
    });
  }),
);

/* -------------------------------------------------------- card holder --- */

cardsRouter.get(
  '/cards',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    res.json({
      ok: true,
      cards: db.listCards(req.userId).map(publicCard),
      subscription: serializeSubscription(getSubscription(req.userId)),
    });
  }),
);

cardsRouter.post(
  '/cards/:cardId/default',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    const card = db.getCard(req.params.cardId);
    if (!card || card.userId !== req.userId || card.deletedAt) {
      return res.status(404).json({ ok: false, error: { code: 'CARD_NOT_FOUND', message: 'Unknown card' } });
    }
    db.clearDefaultCards(req.userId);
    card.isDefault = true;
    db.saveCard(card);
    const sub = setAutoRenewCard(req.userId, card.cardId);
    res.json({ ok: true, card: publicCard(card), subscription: serializeSubscription(sub) });
  }),
);

cardsRouter.delete(
  '/cards/:cardId',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    const card = db.getCard(req.params.cardId);
    if (!card || card.userId !== req.userId || card.deletedAt) {
      return res.status(404).json({ ok: false, error: { code: 'CARD_NOT_FOUND', message: 'Unknown card' } });
    }
    card.deletedAt = Date.now();
    card.isDefault = false;
    card.paymentMethodId = null; // forget the recurring token
    db.saveCard(card);

    const remaining = db.listCards(req.userId);
    let sub = getSubscription(req.userId);
    if (sub.cardId === card.cardId) {
      const fallback = remaining[0] || null;
      if (fallback) {
        fallback.isDefault = true;
        db.saveCard(fallback);
      }
      sub = setAutoRenewCard(req.userId, fallback?.cardId || null);
    }

    res.json({ ok: true, cards: remaining.map(publicCard), subscription: serializeSubscription(sub) });
  }),
);
