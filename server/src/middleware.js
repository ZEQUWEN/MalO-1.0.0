import { config } from './config.js';

/** Shared-secret guard for the app-facing API (webhooks have their own proof). */
export function requireClientKey(req, res, next) {
  if (!config.clientKey) return next(); // open in local/dev deployments
  const provided = req.get('X-MalO-Client-Key');
  if (provided && provided === config.clientKey) return next();
  return res.status(401).json({ ok: false, error: { code: 'UNAUTHORIZED', message: 'Invalid client key' } });
}

/** Every app call is scoped to an opaque installation id generated on device. */
export function requireUserId(req, res, next) {
  const userId = String(req.body?.userId || req.query?.userId || req.get('X-MalO-User-Id') || '').trim();
  if (!userId || userId.length < 6 || userId.length > 128) {
    return res
      .status(400)
      .json({ ok: false, error: { code: 'INVALID_USER_ID', message: 'userId (6..128 chars) is required' } });
  }
  req.userId = userId;
  return next();
}

export function asyncRoute(handler) {
  return (req, res, next) => Promise.resolve(handler(req, res, next)).catch(next);
}

export function errorHandler(err, _req, res, _next) {
  const status = err.status || 500;
  if (status >= 500) console.error('[malo-gateway]', err);
  res.status(status).json({
    ok: false,
    error: {
      code: err.code || 'INTERNAL_ERROR',
      message: err.message || 'Unexpected gateway error',
      details: err.details ?? undefined,
    },
  });
}
