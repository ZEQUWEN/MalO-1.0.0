import crypto from 'node:crypto';
import { Router } from 'express';
import { config } from '../config.js';
import { db } from '../store.js';
import { yookassa } from '../providers/yookassa.js';
import { asyncRoute, requireClientKey, requireUserId } from '../middleware.js';
import {
  activateSubscription,
  cancelSubscription,
  dueForRenewal,
  getSubscription,
  resumeSubscription,
  serializeSubscription,
} from '../subscriptions.js';
import { publicCard } from './cards.js';

export const subscriptionRouter = Router();

subscriptionRouter.get(
  '/subscription',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    const sub = getSubscription(req.userId);
    res.json({
      ok: true,
      subscription: serializeSubscription(sub),
      cards: db.listCards(req.userId).map(publicCard),
    });
  }),
);

/** Cancel auto-renewal (Pro stays until the paid period ends by default). */
subscriptionRouter.post(
  '/subscription/cancel',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    const sub = cancelSubscription(req.userId, {
      immediate: req.body?.immediate === true,
      reason: String(req.body?.reason || 'user_request').slice(0, 200),
    });
    res.json({ ok: true, subscription: serializeSubscription(sub) });
  }),
);

subscriptionRouter.post(
  '/subscription/resume',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    res.json({ ok: true, subscription: serializeSubscription(resumeSubscription(req.userId)) });
  }),
);

/**
 * Charges the default saved card for the next period.
 * Called by the in-app "Продлить сейчас" button and by the renewal sweeper.
 */
export async function chargeRenewal(userId) {
  const sub = getSubscription(userId);
  const card = sub.cardId ? db.getCard(sub.cardId) : db.defaultCard(userId);
  if (!card || !card.paymentMethodId) {
    const error = new Error('No saved card available for recurring charge');
    error.status = 409;
    error.code = 'NO_SAVED_CARD';
    throw error;
  }

  const payment = await yookassa.chargeSavedCard({
    amount: config.subscription.priceRub,
    description: `${config.subscription.planName} — автопродление`,
    metadata: { userId, planId: config.subscription.planId, recurring: 'true' },
    paymentMethodId: card.paymentMethodId,
    idempotenceKey: crypto.randomUUID(),
  });

  db.savePayment({
    paymentId: payment.id,
    userId,
    provider: 'yookassa',
    kind: 'recurring',
    status: payment.status,
    amount: `${config.subscription.priceRub} RUB`,
    saveCard: false,
    cardId: card.cardId,
    createdAt: Date.now(),
    confirmationUrl: null,
  });

  if (payment.status === 'succeeded') {
    return activateSubscription({
      userId,
      paymentMethod: 'CARD_RECURRING',
      transactionId: payment.id,
      amount: `${config.subscription.priceRub} RUB`,
      autoRenew: true,
      cardId: card.cardId,
      meta: { recurring: true },
    });
  }
  return getSubscription(userId);
}

subscriptionRouter.post(
  '/subscription/charge',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    const sub = await chargeRenewal(req.userId);
    res.json({ ok: true, subscription: serializeSubscription(sub) });
  }),
);

/** Sweeper endpoint for an external cron (Railway cron / GitHub Action). */
subscriptionRouter.post(
  '/subscription/run-renewals',
  requireClientKey,
  asyncRoute(async (_req, res) => {
    const due = dueForRenewal();
    const results = [];
    for (const sub of due) {
      try {
        const updated = await chargeRenewal(sub.userId);
        results.push({ userId: sub.userId, status: updated.status });
      } catch (error) {
        results.push({ userId: sub.userId, status: 'failed', error: error.message });
      }
    }
    res.json({ ok: true, processed: results.length, results });
  }),
);
