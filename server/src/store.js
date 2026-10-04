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
let writeTimer = null;

export function initStore(dir = config.dataDir) {
  const resolved = path.resolve(dir);
  fs.mkdirSync(resolved, { recursive: true, mode: 0o700 });
  filePath = path.join(resolved, 'gateway-state.json');
  if (fs.existsSync(filePath)) {
    try {
      state = { ...structuredClone(EMPTY), ...JSON.parse(fs.readFileSync(filePath, 'utf8')) };
      fs.chmodSync(filePath, 0o600);
    } catch {
      state = structuredClone(EMPTY);
      flush();
    }
  } else {
    state = structuredClone(EMPTY);
    flush();
  }
  return state;
}

export function resetStore() {
  state = structuredClone(EMPTY);
  if (filePath) flushNow();
}

/**
 * Write to a sibling temporary file and rename it into place. POSIX rename is
 * atomic on Railway's mounted Linux Volume, so a restart cannot leave a
 * half-written JSON file after a confirmed payment.
 */
function flush() {
  if (!filePath) return;
  const temporary = `${filePath}.${process.pid}.${crypto.randomUUID()}.tmp`;
  try {
    fs.writeFileSync(temporary, JSON.stringify(state, null, 2), { mode: 0o600 });
    fs.renameSync(temporary, filePath);
    fs.chmodSync(filePath, 0o600);
  } finally {
    // If write/rename threw, do not leave sensitive card-token state in a temp
    // file that may be included in a future Volume backup.
    try {
      if (fs.existsSync(temporary)) fs.rmSync(temporary, { force: true });
    } catch {
      /* best effort cleanup */
    }
  }
}

function persist() {
  if (!filePath || writeQueued) return;
  writeQueued = true;
  writeTimer = setTimeout(() => {
    writeQueued = false;
    writeTimer = null;
    try {
      flush();
    } catch {
      /* best effort for non-payment UI state */
    }
  }, 50);
}

/** Flush the current in-memory state before a payment webhook receives 2xx. */
function flushNow() {
  if (!filePath) return;
  if (writeTimer) clearTimeout(writeTimer);
  writeTimer = null;
  writeQueued = false;
  flush();
}

export const db = {
  get raw() {
    return state;
  },

  /**
   * Durability boundary for confirmed payment/entitlement changes. Do not call
   * this in every UI read; callers use it immediately before a webhook 2xx.
   */
  flushNow,

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
