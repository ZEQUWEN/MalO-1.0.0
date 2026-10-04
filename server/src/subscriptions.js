/**
 * Subscription lifecycle shared by both payment rails (card + crypto).
 *
 * A subscription is signed with HMAC-SHA256 so the Android client can verify
 * that the payload it caches locally really came from this gateway.
 */

import crypto from 'node:crypto';
import { config } from './config.js';
import { db, newId } from './store.js';

const DAY_MS = 24 * 60 * 60 * 1000;

const signingKey = () =>
  config.clientKey || config.yookassa.secretKey || config.cryptobot.token || 'malo-dev-signing-key';

export function signSubscription(sub) {
  const payload = [
    sub.subscriptionId,
    sub.userId,
    sub.planId,
    sub.status,
    sub.paymentMethod,
    sub.currentPeriodEnd,
  ].join('|');
  return crypto.createHmac('sha256', signingKey()).update(payload).digest('hex');
}

export function serializeSubscription(sub) {
  if (!sub) return null;
  return { ...sub, signature: signSubscription(sub) };
}

export function emptySubscription(userId) {
  return {
    subscriptionId: null,
    userId,
    planId: config.subscription.planId,
    planName: config.subscription.planName,
    status: 'inactive',
    paymentMethod: null,
    autoRenew: false,
    cardId: null,
    currentPeriodStart: null,
    currentPeriodEnd: null,
    cancelAtPeriodEnd: false,
    canceledAt: null,
    lastTransactionId: null,
    lastAmount: null,
    history: [],
  };
}

export function getSubscription(userId) {
  const existing = db.getSubscription(userId);
  if (!existing) return emptySubscription(userId);
  // Lazily expire.
  if (existing.status === 'active' && existing.currentPeriodEnd && existing.currentPeriodEnd < Date.now()) {
    existing.status = existing.autoRenew && !existing.cancelAtPeriodEnd ? 'past_due' : 'expired';
    db.saveSubscription(existing);
  }
  return existing;
}

/**
 * Activates (or extends) the Pro plan after a confirmed payment.
 */
export function activateSubscription({
  userId,
  paymentMethod,
  transactionId,
  amount,
  autoRenew = false,
  cardId = null,
  meta = {},
}) {
  const current = getSubscription(userId);
  const now = Date.now();
  const base =
    current.status === 'active' && current.currentPeriodEnd && current.currentPeriodEnd > now
      ? current.currentPeriodEnd
      : now;

  const sub = {
    ...current,
    subscriptionId: current.subscriptionId || newId('sub'),
    userId,
    planId: config.subscription.planId,
    planName: config.subscription.planName,
    status: 'active',
    paymentMethod,
    autoRenew,
    cardId,
    currentPeriodStart: now,
    currentPeriodEnd: base + config.subscription.periodDays * DAY_MS,
    cancelAtPeriodEnd: false,
    canceledAt: null,
    lastTransactionId: transactionId,
    lastAmount: amount,
    history: [
      ...(current.history || []).slice(-19),
      { at: now, event: 'payment_succeeded', paymentMethod, transactionId, amount, ...meta },
    ],
  };

  db.saveSubscription(sub);
  return sub;
}

/**
 * Cancels auto-renewal. By default the user keeps Pro until the paid period
 * ends — matching the "Возможность отмены в любой момент" promise on the
 * pricing card.
 */
export function cancelSubscription(userId, { immediate = false, reason = 'user_request' } = {}) {
  const current = getSubscription(userId);
  if (!current.subscriptionId) return current;
  const now = Date.now();

  const sub = {
    ...current,
    autoRenew: false,
    cancelAtPeriodEnd: !immediate,
    canceledAt: now,
    status: immediate ? 'canceled' : current.status,
    currentPeriodEnd: immediate ? now : current.currentPeriodEnd,
    history: [...(current.history || []).slice(-19), { at: now, event: 'canceled', immediate, reason }],
  };

  db.saveSubscription(sub);
  return sub;
}

/** Re-enables auto-renewal before the period runs out. */
export function resumeSubscription(userId) {
  const current = getSubscription(userId);
  if (!current.subscriptionId) return current;
  const sub = {
    ...current,
    cancelAtPeriodEnd: false,
    canceledAt: null,
    autoRenew: Boolean(current.cardId),
    status: current.currentPeriodEnd > Date.now() ? 'active' : current.status,
    history: [...(current.history || []).slice(-19), { at: Date.now(), event: 'resumed' }],
  };
  db.saveSubscription(sub);
  return sub;
}

export function setAutoRenewCard(userId, cardId) {
  const current = getSubscription(userId);
  const sub = { ...current, cardId, autoRenew: Boolean(cardId) };
  if (!sub.subscriptionId) sub.subscriptionId = newId('sub');
  db.saveSubscription(sub);
  return sub;
}

/** Subscriptions whose paid period lapsed and that should be auto-charged. */
export function dueForRenewal(now = Date.now()) {
  return Object.values(db.raw.subscriptions).filter(
    (sub) =>
      sub.autoRenew &&
      !sub.cancelAtPeriodEnd &&
      sub.cardId &&
      sub.currentPeriodEnd &&
      sub.currentPeriodEnd <= now &&
      ['active', 'past_due'].includes(sub.status),
  );
}
