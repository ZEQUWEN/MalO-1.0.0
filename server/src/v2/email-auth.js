/**
 * Passwordless email/session storage. HTTP adapters add SMTP and rate limits;
 * deployment still needs trusted edge controls, TLS and operational testing.
 */
import crypto from 'node:crypto';

function normalizeEmail(input) {
  if (typeof input !== 'string') throw new Error('Invalid email');
  const email = input.trim().toLowerCase();
  if (email.length > 254 || !/^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9-]+(?:\.[a-z0-9-]+)+$/.test(email)) {
    throw new Error('Invalid email');
  }
  return email;
}

function requirePepper(pepper) {
  if (typeof pepper !== 'string' || Buffer.byteLength(pepper, 'utf8') < 32) throw new Error('Email code pepper is not configured');
}
const hashSession = (token) => crypto.createHash('sha256').update(token).digest('hex');
const hashCode = (email, code, pepper) => crypto.createHmac('sha256', pepper).update(`${email}\0${code}`).digest('hex');

/** Returns the code only to the email transport, never to an HTTP client/log. */
export async function requestEmailCode(pool, rawEmail, { pepper, sendCode }) {
  requirePepper(pepper);
  if (typeof sendCode !== 'function') throw new Error('Email transport is not configured');
  const email = normalizeEmail(rawEmail);
  const code = String(crypto.randomInt(0, 100_000_000)).padStart(8, '0');
  const result = await pool.query(
    `INSERT INTO v2_email_challenges (email, code_hash, created_at, expires_at, attempts)
     VALUES ($1, $2, clock_timestamp(), clock_timestamp() + interval '10 minutes', 0)
     ON CONFLICT (email) DO UPDATE SET code_hash = EXCLUDED.code_hash, created_at = EXCLUDED.created_at,
       expires_at = EXCLUDED.expires_at, attempts = 0
     WHERE v2_email_challenges.created_at <= clock_timestamp() - interval '1 minute'
     RETURNING email`, [email, hashCode(email, code, pepper)]);
  if (!result.rows.length) return { result: 'rate_limited' };
  await sendCode(email, code);
  return { result: 'sent' };
}

/** Consumes one code transactionally and issues a random, hashed-at-rest session. */
export async function verifyEmailCode(pool, rawEmail, code, { pepper }) {
  requirePepper(pepper);
  const email = normalizeEmail(rawEmail);
  if (typeof code !== 'string' || !/^\d{8}$/.test(code)) return null;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const found = await client.query(
      `SELECT code_hash, attempts, expires_at > clock_timestamp() AS unexpired
       FROM v2_email_challenges WHERE email = $1 FOR UPDATE`, [email]);
    const challenge = found.rows[0];
    if (!challenge || !challenge.unexpired || challenge.attempts >= 5) {
      await client.query('COMMIT');
      return null;
    }
    const actual = Buffer.from(hashCode(email, code, pepper), 'hex');
    const expected = Buffer.from(challenge.code_hash, 'hex');
    if (expected.length !== actual.length || !crypto.timingSafeEqual(expected, actual)) {
      await client.query('UPDATE v2_email_challenges SET attempts = attempts + 1 WHERE email = $1', [email]);
      await client.query('COMMIT');
      return null;
    }
    const user = await client.query(
      `INSERT INTO v2_users (id, email) VALUES ($1, $2)
       ON CONFLICT (email) DO UPDATE SET email = EXCLUDED.email RETURNING id`, [crypto.randomUUID(), email]);
    const token = crypto.randomBytes(32).toString('base64url');
    await client.query(
      `INSERT INTO v2_sessions (token_hash, user_id, expires_at)
       VALUES ($1, $2, clock_timestamp() + interval '30 days')`, [hashSession(token), user.rows[0].id]);
    await client.query('DELETE FROM v2_email_challenges WHERE email = $1', [email]);
    await client.query('COMMIT');
    return { sessionToken: token };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
}

/** Resolve identity on the server; never accept userId from a request. */
export async function resolveSession(pool, token) {
  if (typeof token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(token)) return null;
  const result = await pool.query(
    `SELECT user_id FROM v2_sessions WHERE token_hash = $1
     AND revoked_at IS NULL AND expires_at > clock_timestamp()`, [hashSession(token)]);
  return result.rows[0]?.user_id || null;
}

export async function revokeSession(pool, token) {
  if (typeof token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(token)) return;
  await pool.query('UPDATE v2_sessions SET revoked_at = clock_timestamp() WHERE token_hash = $1', [hashSession(token)]);
}
