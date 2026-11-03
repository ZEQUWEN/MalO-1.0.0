/**
 * Ko-fi settlement primitives. The HTTP adapter must authenticate user sessions
 * and protect the webhook secret. Reconciliation and production validation are
 * still required before accepting live payments.
 */
import crypto from 'node:crypto';

const TOKEN = /^MALO-[A-Za-z0-9_-]{43}$/;
const hashToken = (token) => crypto.createHash('sha256').update(token).digest('hex');
const validId = (value) => typeof value === 'string' && value.length > 0 && value.length <= 128;

/** Strict decimal conversion: never use floats for monetary comparisons. */
export function parseMinorUnits(value) {
  if (typeof value !== 'string' || !/^(?:0|[1-9]\d{0,10})(?:\.\d{1,2})?$/.test(value)) return null;
  const [whole, fraction = ''] = value.split('.');
  return (BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'))).toString();
}

/** Ko-fi sends a shared bearer secret in its payload, not a signature. */
export function hasValidVerificationToken(provided, expected) {
  if (typeof provided !== 'string' || typeof expected !== 'string' || !expected) return false;
  const a = crypto.createHash('sha256').update(provided).digest();
  const b = crypto.createHash('sha256').update(expected).digest();
  return crypto.timingSafeEqual(a, b);
}

/** Caller MUST obtain userId from an authenticated server-side session. */
export async function createKoFiIntent(pool, userId, { amountMinor, currency, periodDays, ttlMinutes = 30 }) {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(String(userId)) ||
      !Number.isSafeInteger(amountMinor) || amountMinor <= 0 || !/^[A-Z]{3}$/.test(currency) ||
      !Number.isInteger(periodDays) || periodDays < 1 || periodDays > 366 ||
      !Number.isInteger(ttlMinutes) || ttlMinutes < 1 || ttlMinutes > 1440) {
    throw new Error('Invalid Ko-fi intent parameters');
  }
  const token = `MALO-${crypto.randomBytes(32).toString('base64url')}`;
  const result = await pool.query(
    `INSERT INTO v2_kofi_intents (token_hash, user_id, amount_minor, currency, period_days, expires_at)
     VALUES ($1, $2, $3, $4, $5, now() + $6 * interval '1 minute') RETURNING expires_at`,
    [hashToken(token), userId, amountMinor, currency, periodDays, ttlMinutes],
  );
  return { paymentToken: token, expiresAt: result.rows[0].expires_at };
}

/**
 * Atomically deduplicate both provider IDs, consume one code and extend Pro.
 * Expired, malformed, unmatched and unexpected payments are retained for
 * manual review, never silently granted. This must not be used for renewals.
 * Reject unauthenticated events before accessing the database. Note that the
 * shared verification token is not a signature and cannot prove integrity if
 * it is leaked; an HTTP adapter must not log request bodies or secrets.
 */
export async function settleKoFiEvent(pool, event, { verificationToken } = {}) {
  if (!hasValidVerificationToken(event?.verification_token, verificationToken)) {
    throw new Error('Invalid Ko-fi verification token');
  }
  if (!event || !validId(event.kofi_transaction_id) || !validId(event.message_id)) {
    throw new Error('Missing Ko-fi transaction or message ID');
  }
  const message = typeof event.message === 'string' ? event.message.trim() : '';
  const codeHash = TOKEN.test(message) ? hashToken(message) : null;
  const amount = parseMinorUnits(event.amount);
  const currency = typeof event.currency === 'string' && /^[A-Z]{3}$/.test(event.currency) ? event.currency : null;
  const eventType = typeof event.type === 'string' && event.type.length <= 32 ? event.type : null;
  const isSubscription = typeof event.is_subscription_payment === 'boolean' ? event.is_subscription_payment : null;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const inserted = await client.query(
      `INSERT INTO v2_kofi_events (transaction_id, message_id, token_hash, amount_minor, currency, event_type, is_subscription_payment, result)
       VALUES ($1, $2, $3, $4, $5, $6, $7, 'processing') ON CONFLICT DO NOTHING RETURNING transaction_id`,
      [event.kofi_transaction_id, event.message_id, codeHash, amount, currency, eventType, isSubscription],
    );
    if (!inserted.rows.length) {
      const prior = await client.query(
        `SELECT * FROM v2_kofi_events WHERE transaction_id = $1 OR message_id = $2`,
        [event.kofi_transaction_id, event.message_id]);
      const identical = prior.rows.length === 1 && prior.rows[0].transaction_id === event.kofi_transaction_id &&
        prior.rows[0].message_id === event.message_id && prior.rows[0].token_hash === codeHash &&
        (prior.rows[0].amount_minor === null ? null : String(prior.rows[0].amount_minor)) === amount &&
        prior.rows[0].currency === currency && prior.rows[0].event_type === eventType &&
        prior.rows[0].is_subscription_payment === isSubscription;
      if (!identical) {
        await client.query(
          `INSERT INTO v2_kofi_conflicts (transaction_id, message_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
          [event.kofi_transaction_id, event.message_id]);
      }
      await client.query('COMMIT');
      return { result: identical ? 'duplicate' : 'conflict' };
    }

    let intent = null;
    if (codeHash) {
      const found = await client.query('SELECT * FROM v2_kofi_intents WHERE token_hash = $1 FOR UPDATE', [codeHash]);
      intent = found.rows[0] || null;
    }
    // Compare against the database clock after acquiring the lock (a webhook
    // that arrives after expiration goes to review, rather than losing money).
    const consumed = intent && eventType === 'Donation' && event.is_subscription_payment === false &&
      amount !== null && currency === intent.currency.trim() && amount === String(intent.amount_minor)
      ? await client.query(
        `UPDATE v2_kofi_intents SET consumed_at = clock_timestamp()
         WHERE token_hash = $1 AND consumed_at IS NULL AND expires_at > clock_timestamp()
         RETURNING user_id, period_days`, [codeHash])
      : { rows: [] };

    if (!consumed.rows.length) {
      await client.query("UPDATE v2_kofi_events SET result = 'review' WHERE transaction_id = $1", [event.kofi_transaction_id]);
      await client.query('COMMIT');
      return { result: 'review' };
    }
    const { user_id: userId, period_days: days } = consumed.rows[0];
    await client.query(
      `INSERT INTO v2_subscriptions (user_id, current_period_end)
       VALUES ($1, clock_timestamp() + $2 * interval '1 day')
       ON CONFLICT (user_id) DO UPDATE SET
         current_period_end = GREATEST(v2_subscriptions.current_period_end, clock_timestamp()) + $2 * interval '1 day',
         updated_at = clock_timestamp()`, [userId, days]);
    await client.query("UPDATE v2_kofi_events SET result = 'settled' WHERE transaction_id = $1", [event.kofi_transaction_id]);
    await client.query('COMMIT');
    return { result: 'settled' };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
}
