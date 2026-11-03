import { Router, urlencoded } from 'express';
import crypto from 'node:crypto';
import { requestEmailCode, verifyEmailCode, resolveSession, revokeSession } from './email-auth.js';
import { createKoFiIntent, hasValidVerificationToken, parseMinorUnits, settleKoFiEvent } from './kofi.js';
import { createAiRouter } from '../routes/ai.js';

const fail = (res, status, code, message) => res.status(status).json({ ok: false, error: { code, message } });
const wrap = (fn) => (req, res) => Promise.resolve(fn(req, res)).catch(() => {
  // Provider errors may contain connection strings or email addresses.
  console.error('[malo-v2] Request failed; check service configuration and database availability.');
  if (!res.headersSent) fail(res, 503, 'SERVICE_UNAVAILABLE', 'Service temporarily unavailable');
});
const hmac = (secret, input) => crypto.createHmac('sha256', secret).update(input).digest('hex');

/** A DB-backed fixed window. The proxy address is deliberately conservative:
 * we do not trust arbitrary X-Forwarded-For headers. Behind a proxy all clients
 * may share a bucket until a trusted proxy configuration is deployed. */
async function takeLimit(pool, key, limit, minutes) {
  const result = await pool.query(
    `INSERT INTO v2_rate_limits (bucket_key, request_count, expires_at)
     VALUES ($1, 1, clock_timestamp() + $2 * interval '1 minute')
     ON CONFLICT (bucket_key) DO UPDATE SET
       request_count = CASE WHEN v2_rate_limits.expires_at <= clock_timestamp() THEN 1 ELSE v2_rate_limits.request_count + 1 END,
       expires_at = CASE WHEN v2_rate_limits.expires_at <= clock_timestamp() THEN clock_timestamp() + $2 * interval '1 minute' ELSE v2_rate_limits.expires_at END
     RETURNING request_count`, [key, minutes]);
  return result.rows[0].request_count <= limit;
}

export function createV2Router({ pool, pepper, sendCode, kofiVerificationToken, kofiUrl, priceUsd, periodDays }) {
  const router = Router();
  router.use((_req, res, next) => { res.set('Cache-Control', 'no-store'); next(); });
  const database = (_req, res, next) => pool ? next() : fail(res, 503, 'DATABASE_NOT_CONFIGURED', 'Account service is not configured');
  const authConfigured = typeof pepper === 'string' && Buffer.byteLength(pepper) >= 32 && typeof sendCode === 'function';
  const price = parseMinorUnits(priceUsd);
  let validKofiUrl = null;
  try {
    const url = new URL(kofiUrl);
    if (url.protocol === 'https:' && url.hostname.toLowerCase() === 'ko-fi.com' && !url.username && !url.password) validKofiUrl = url.toString();
  } catch { /* disabled until configured */ }
  const paymentsConfigured = Boolean(kofiVerificationToken && validKofiUrl && price && Number(price) > 0 &&
    Number.isSafeInteger(Number(price)) && Number.isInteger(periodDays) && periodDays >= 1 && periodDays <= 366);

  async function throttle(req, kind, limit, minutes) {
    // Protect the database and mail transport even if many different addresses are targeted.
    const ip = req.socket.remoteAddress || 'unknown';
    const global = await takeLimit(pool, `${kind}:global`, limit * 10, minutes);
    const client = await takeLimit(pool, `${kind}:ip:${hmac(pepper, ip)}`, limit, minutes);
    return global && client;
  }
  async function authenticate(req, res) {
    const match = /^Bearer ([A-Za-z0-9_-]{43})$/.exec(req.get('Authorization') || '');
    const userId = match && await resolveSession(pool, match[1]);
    if (!userId) { fail(res, 401, 'UNAUTHORIZED', 'Valid session required'); return null; }
    return { userId, token: match[1] };
  }

  const sessionMiddleware = (req, res, next) => {
    if (!pool) return fail(res, 503, 'DATABASE_NOT_CONFIGURED', 'Account service is not configured');
    return Promise.resolve(authenticate(req, res)).then(session => {
      if (session) { req.userId = session.userId; next(); }
    }).catch(() => fail(res, 503, 'SERVICE_UNAVAILABLE', 'Service temporarily unavailable'));
  };
  router.use(createAiRouter({
    middleware: [sessionMiddleware],
    subscriptionForUser: async (userId) => {
      const result = await pool.query(
        `SELECT current_period_end, current_period_end > clock_timestamp() AS active
         FROM v2_subscriptions WHERE user_id = $1`, [userId]);
      const row = result.rows[0];
      return { status: row?.active ? 'active' : 'inactive', currentPeriodEnd: row ? new Date(row.current_period_end).getTime() : null };
    },
  }));

  router.post('/auth/request-code', database, wrap(async (req, res) => {
    if (!authConfigured) return fail(res, 503, 'AUTH_NOT_CONFIGURED', 'Email sign-in is not configured');
    if (typeof req.body?.email !== 'string' || req.body.email.length > 254) return fail(res, 400, 'INVALID_EMAIL', 'Invalid email');
    if (!await throttle(req, 'request', 15, 10)) return fail(res, 429, 'RATE_LIMITED', 'Try again later');
    try {
      const result = await requestEmailCode(pool, req.body.email, { pepper, sendCode });
      if (result.result === 'rate_limited') return fail(res, 429, 'RATE_LIMITED', 'Try again later');
      return res.json({ ok: true });
    } catch (error) {
      if (error.message === 'Invalid email') return fail(res, 400, 'INVALID_EMAIL', 'Invalid email');
      throw error;
    }
  }));

  router.post('/auth/verify-code', database, wrap(async (req, res) => {
    if (!authConfigured) return fail(res, 503, 'AUTH_NOT_CONFIGURED', 'Email sign-in is not configured');
    if (!await throttle(req, 'verify', 40, 10)) return fail(res, 429, 'RATE_LIMITED', 'Try again later');
    if (typeof req.body?.email !== 'string' || req.body.email.length > 254 || typeof req.body?.code !== 'string')
      return fail(res, 400, 'INVALID_INPUT', 'Invalid email or code');
    let session;
    try { session = await verifyEmailCode(pool, req.body.email, req.body.code, { pepper }); }
    catch (error) { if (error.message === 'Invalid email') return fail(res, 400, 'INVALID_INPUT', 'Invalid email or code'); throw error; }
    if (!session) return fail(res, 401, 'INVALID_CODE', 'Invalid or expired code');
    return res.json({ ok: true, sessionToken: session.sessionToken });
  }));

  router.post('/auth/logout', database, wrap(async (req, res) => {
    const session = await authenticate(req, res); if (!session) return;
    await revokeSession(pool, session.token);
    return res.json({ ok: true });
  }));

  router.get('/subscription', database, wrap(async (req, res) => {
    const session = await authenticate(req, res); if (!session) return;
    const data = await pool.query(
      `SELECT current_period_end, current_period_end > clock_timestamp() AS active
       FROM v2_subscriptions WHERE user_id = $1`, [session.userId]);
    const row = data.rows[0];
    return res.json({ ok: true, subscription: {
      status: row?.active ? 'active' : row ? 'expired' : 'inactive',
      currentPeriodEnd: row ? new Date(row.current_period_end).getTime() : null,
      paymentMethod: row ? 'KOFI' : null,
    } });
  }));

  router.post('/kofi/intents', database, wrap(async (req, res) => {
    const session = await authenticate(req, res); if (!session) return;
    if (!paymentsConfigured) return fail(res, 503, 'KOFI_NOT_CONFIGURED', 'Ko-fi payments are not configured');
    const ok = await takeLimit(pool, `kofi:user:${hmac(pepper, session.userId)}`, 10, 10);
    if (!ok) return fail(res, 429, 'RATE_LIMITED', 'Try again later');
    const intent = await createKoFiIntent(pool, session.userId, { amountMinor: Number(price), currency: 'USD', periodDays });
    return res.status(201).json({ ok: true, paymentToken: intent.paymentToken, expiresAt: intent.expiresAt,
      amount: priceUsd, currency: 'USD', paymentUrl: validKofiUrl });
  }));

  router.post('/kofi/intent-status', database, wrap(async (req, res) => {
    const session = await authenticate(req, res); if (!session) return;
    const token = req.body?.paymentToken;
    if (typeof token !== 'string' || !/^MALO-[A-Za-z0-9_-]{43}$/.test(token))
      return fail(res, 400, 'INVALID_TOKEN', 'Invalid payment code');
    const hash = crypto.createHash('sha256').update(token).digest('hex');
    const result = await pool.query(
      `SELECT consumed_at, expires_at <= clock_timestamp() AS expired
       FROM v2_kofi_intents WHERE token_hash = $1 AND user_id = $2`, [hash, session.userId]);
    const row = result.rows[0];
    if (!row) return fail(res, 404, 'NOT_FOUND', 'Unknown payment code');
    return res.json({ ok: true, status: row.consumed_at ? 'paid' : row.expired ? 'expired' : 'pending' });
  }));

  router.post('/webhooks/kofi', database, urlencoded({ extended: false, limit: '32kb', parameterLimit: 4 }), wrap(async (req, res) => {
    if (!paymentsConfigured) return fail(res, 503, 'KOFI_NOT_CONFIGURED', 'Ko-fi payments are not configured');
    if (!req.is('application/x-www-form-urlencoded') || typeof req.body?.data !== 'string')
      return fail(res, 400, 'INVALID_PAYLOAD', 'Expected form-encoded data');
    let event;
    try { event = JSON.parse(req.body.data); } catch { return fail(res, 400, 'INVALID_PAYLOAD', 'Invalid webhook'); }
    if (!hasValidVerificationToken(event?.verification_token, kofiVerificationToken))
      return fail(res, 401, 'UNAUTHORIZED', 'Invalid webhook');
    if (typeof event?.message_id !== 'string' || !event.message_id || event.message_id.length > 128 ||
        typeof event.kofi_transaction_id !== 'string' || !event.kofi_transaction_id || event.kofi_transaction_id.length > 128)
      return fail(res, 400, 'INVALID_PAYLOAD', 'Missing transaction identifiers');
    await settleKoFiEvent(pool, event, { verificationToken: kofiVerificationToken });
    // Review/conflict and duplicates are durably recorded; don't expose details.
    return res.status(200).json({ ok: true });
  }));
  return router;
}
