import crypto from 'node:crypto';
import { Router } from 'express';
import { config } from '../config.js';
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
 * Starts a card payment. The response contains a `confirmationUrl` that the
 * app opens in a Chrome Custom Tab; 3-D Secure and PAN entry happen there, so
 * card data never touches the APK or this gateway.
 */
cardsRouter.post(
  '/cards/checkout',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    const saveCard = req.body?.saveCard !== false; // card-holder / autopay by default
    const idempotenceKey = String(req.body?.idempotenceKey || crypto.randomUUID());

    const payment = await yookassa.createPayment({
      amount: config.subscription.priceRub,
      currency: 'RUB',
      description: `${config.subscription.planName} — ${config.subscription.periodDays} дней`,
      savePaymentMethod: saveCard,
      returnUrl: req.body?.returnUrl || config.yookassa.returnUrl,
      metadata: { userId: req.userId, planId: config.subscription.planId, saveCard: String(saveCard) },
      idempotenceKey,
    });

    const record = {
      paymentId: payment.id,
      userId: req.userId,
      provider: 'yookassa',
      kind: 'checkout',
      status: payment.status,
      amount: `${config.subscription.priceRub} RUB`,
      saveCard,
      createdAt: Date.now(),
      confirmationUrl: payment.confirmation?.confirmation_url || null,
    };
    db.savePayment(record);

    // Mock/instant-success path (saved-card reuse inside the sandbox).
    if (payment.status === 'succeeded') {
      const card = saveCard ? upsertCardFromPaymentMethod(req.userId, payment.payment_method) : null;
      activateSubscription({
        userId: req.userId,
        paymentMethod: 'CARD',
        transactionId: payment.id,
        amount: record.amount,
        autoRenew: Boolean(card),
        cardId: card?.cardId || null,
      });
    }

    return res.status(201).json({
      ok: true,
      payment: {
        paymentId: payment.id,
        status: payment.status,
        confirmationUrl: record.confirmationUrl,
        amount: record.amount,
        saveCard,
      },
      subscription: serializeSubscription(getSubscription(req.userId)),
    });
  }),
);

/** Poll a pending card payment (fallback when the webhook is late). */
cardsRouter.get(
  '/cards/payments/:paymentId',
  requireClientKey,
  asyncRoute(async (req, res) => {
    const stored = db.getPayment(req.params.paymentId);
    if (!stored) {
      return res.status(404).json({ ok: false, error: { code: 'PAYMENT_NOT_FOUND', message: 'Unknown payment' } });
    }
    if (['pending', 'waiting_for_capture'].includes(stored.status)) {
      try {
        const remote = await yookassa.getPayment(stored.paymentId);
        if (remote?.status && remote.status !== stored.status) {
          stored.status = remote.status;
          db.savePayment(stored);
          if (remote.status === 'succeeded') {
            const card = stored.saveCard
              ? upsertCardFromPaymentMethod(stored.userId, remote.payment_method)
              : null;
            activateSubscription({
              userId: stored.userId,
              paymentMethod: 'CARD',
              transactionId: remote.id,
              amount: stored.amount,
              autoRenew: Boolean(card),
              cardId: card?.cardId || null,
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
