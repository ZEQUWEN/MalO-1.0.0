/**
 * Request logging.
 *
 * The previous Python `http.server` wrote its access log to **stderr**, which
 * is why Railway painted every ordinary request red (`[err] "GET / HTTP/1.1"
 * 200`). Everything here goes to stdout; only real 5xx failures go to stderr.
 */

const now = () => new Date().toISOString();

export function requestLogger(req, res, next) {
  const started = process.hrtime.bigint();

  res.on('finish', () => {
    const ms = Number(process.hrtime.bigint() - started) / 1e6;
    const ip = (req.get('x-forwarded-for') || req.socket?.remoteAddress || '-').split(',')[0].trim();
    const line = `[malo-gateway] ${now()} ${ip} "${req.method} ${req.originalUrl}" ${res.statusCode} ${ms.toFixed(1)}ms`;

    if (res.statusCode >= 500) {
      console.error(line);
    } else {
      console.log(line);
    }
  });

  next();
}

/** Webhook deliveries deserve a dedicated, greppable line. */
export function logWebhook(provider, outcome, details = '') {
  console.log(`[malo-gateway] ${now()} webhook:${provider} ${outcome} ${details}`.trimEnd());
}
