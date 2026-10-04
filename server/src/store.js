/**
 * Tiny append-safe JSON store.
 *
 * The gateway intentionally has no database dependency: Railway deployments of
 * this repository are single-instance. Swap this module for Postgres/Redis by
 * keeping the same exported surface.
 */

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { config } from './config.js';

const EMPTY = {
  subscriptions: {}, // userId -> subscription
  invoices: {}, // invoiceId -> crypto invoice
  payments: {}, // paymentId -> card payment
  cards: {}, // cardId -> saved card
  webhookEvents: {}, // dedupe key -> timestamp
};

let state = structuredClone(EMPTY);
let filePath = null;
let writeQueued = false;

export function initStore(dir = config.dataDir) {
  const resolved = path.resolve(dir);
  fs.mkdirSync(resolved, { recursive: true });
  filePath = path.join(resolved, 'gateway-state.json');
  if (fs.existsSync(filePath)) {
    try {
      state = { ...structuredClone(EMPTY), ...JSON.parse(fs.readFileSync(filePath, 'utf8')) };
    } catch {
      state = structuredClone(EMPTY);
    }
  } else {
    state = structuredClone(EMPTY);
    flush();
  }
  return state;
}

export function resetStore() {
  state = structuredClone(EMPTY);
  if (filePath) flush();
}

function flush() {
  if (!filePath) return;
  fs.writeFileSync(filePath, JSON.stringify(state, null, 2));
}

function persist() {
  if (!filePath || writeQueued) return;
  writeQueued = true;
  setTimeout(() => {
    writeQueued = false;
    try {
      flush();
    } catch {
      /* best effort */
    }
  }, 50);
}

export const db = {
  get raw() {
    return state;
  },

  /* ---------------------------------------------------------- subscriptions */
  getSubscription(userId) {
    return state.subscriptions[userId] || null;
  },
  saveSubscription(sub) {
    state.subscriptions[sub.userId] = sub;
    persist();
    return sub;
  },

  /* -------------------------------------------------------------- invoices */
  getInvoice(id) {
    return state.invoices[String(id)] || null;
  },
  findInvoiceByPayload(payloadId) {
    return Object.values(state.invoices).find((inv) => inv.payloadId === payloadId) || null;
  },
  saveInvoice(invoice) {
    state.invoices[String(invoice.invoiceId)] = invoice;
    persist();
    return invoice;
  },
  listInvoices(userId) {
    return Object.values(state.invoices).filter((inv) => inv.userId === userId);
  },

  /* -------------------------------------------------------------- payments */
  getPayment(id) {
    return state.payments[String(id)] || null;
  },
  savePayment(payment) {
    state.payments[String(payment.paymentId)] = payment;
    persist();
    return payment;
  },

  /* ----------------------------------------------------------------- cards */
  getCard(id) {
    return state.cards[String(id)] || null;
  },
  listCards(userId) {
    return Object.values(state.cards)
      .filter((card) => card.userId === userId && !card.deletedAt)
      .sort((a, b) => Number(b.isDefault) - Number(a.isDefault) || b.createdAt - a.createdAt);
  },
  saveCard(card) {
    state.cards[String(card.cardId)] = card;
    persist();
    return card;
  },
  defaultCard(userId) {
    return this.listCards(userId).find((card) => card.isDefault) || null;
  },
  clearDefaultCards(userId) {
    for (const card of Object.values(state.cards)) {
      if (card.userId === userId) card.isDefault = false;
    }
    persist();
  },

  /* ------------------------------------------------------- webhook dedupe */
  seenWebhook(key) {
    return Boolean(state.webhookEvents[key]);
  },
  markWebhook(key) {
    state.webhookEvents[key] = Date.now();
    // Keep the dedupe table bounded.
    const entries = Object.entries(state.webhookEvents);
    if (entries.length > 5000) {
      entries
        .sort((a, b) => a[1] - b[1])
        .slice(0, entries.length - 5000)
        .forEach(([k]) => delete state.webhookEvents[k]);
    }
    persist();
  },
};

export const newId = (prefix) => `${prefix}_${crypto.randomBytes(12).toString('hex')}`;
