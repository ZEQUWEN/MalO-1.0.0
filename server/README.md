# MalO gateway: account and Ko-fi v2

**Status: experimental; not cleared for live payments.** The Node service has
PostgreSQL-backed email accounts, sessions, one-time Ko-fi payments and paid AI
routes. The old client-selected `userId` API and YooKassa/CryptoBot webhooks are
disabled in the deployed app; old subscriptions are not migrated. Existing APKs
cannot use the v2 API. Back up the legacy JSON state and reconcile any in-flight
payments before switching deployments; do not discard it.

## Setup

1. Configure a PostgreSQL instance and runtime `DATABASE_URL`, with authenticated
   connection and encryption appropriate to the deployment. Limit database
   access; use backups and a least-privilege DB account.
2. Configure `MALO_AUTH_PEPPER` (random secret of at least 32 bytes), `SMTP_URL`
   and `MALO_MAIL_FROM`. The SMTP server must support TLS (`smtps://`) or
   STARTTLS (`smtp://`). Codes never appear in API responses or request logs.
3. Configure `KOFI_PAGE_URL` (an `https://ko-fi.com/...` URL),
   `KOFI_VERIFICATION_TOKEN`, `MALO_PRICE_USD` and `MALO_PLAN_PERIOD_DAYS`.
   The Ko-fi page must accept **one-time donations** in USD at the exact plan
   amount. Webhook: `https://YOUR_DOMAIN/api/v2/webhooks/kofi`. Set secrets in
   Railway runtime variables only; never send them in chat or put them in an APK.
4. Run `cd server && npm ci && npm run db:migrate` against the intended database
   before starting the service. Migration is explicit and uses a transaction;
   it creates v2 tables without reading or deleting legacy JSON. This is an
   initial schema, not a full versioned migration system.
5. Set `MALO_GATEWAY_URL` to the HTTPS public gateway URL at **Android build
   time**. Rebuild the APK. No shared client key is used for v2.

The application listens on `$PORT`. Missing PostgreSQL/SMTP/Ko-fi configuration
fails closed for the relevant endpoints. The Node server does not load `.env`
automatically; export variables or configure them on Railway.

## API

- `POST /api/v2/auth/request-code` — `{ "email": "..." }`; sends an 8-digit
  code over SMTP, valid 10 minutes. Per-email cooldown and DB-backed shared/IP
  limits apply; because client-supplied forwarded headers aren't trusted, all
  clients behind a reverse proxy may share an IP bucket. Configure and test a
  trusted edge rate limiter for the actual deployment before going live.
- `POST /api/v2/auth/verify-code` — `{ "email": "...", "code": "..." }`;
  returns a random bearer `sessionToken`, valid 30 days. Only its hash is stored.
- `POST /api/v2/auth/logout` — bearer token; revokes that session.
- `GET /api/v2/subscription` — bearer token; resolves account on the server.
- `POST /api/v2/kofi/intents` — bearer token; creates a 30-minute one-time
  payment code, exact USD price and Ko-fi URL. Android copies the code for the
  user to paste **as the entire Ko-fi message**. There is no documented prefill.
- `POST /api/v2/kofi/intent-status` — bearer token and `{ "paymentToken": "..." }`;
  only the owner can poll the status. Never put codes in URLs or logs.
- `POST /api/v2/webhooks/kofi` — form-encoded `data` containing Ko-fi JSON. The
  verification token, both provider IDs, event type, amount, currency, intent
  expiry and one-time consumption are checked before granting a prepaid period.
  The DB transaction records both IDs, consumes the intent, and updates the
  entitlement atomically. Duplicates do not add a period; conflicting IDs are
  recorded for investigation. Unknown, incorrect, expired or recurring events
  are recorded for manual review, not automatically credited.
- `POST /api/v2/malo/chat` and `/api/v2/malo/burn` — bearer token; paid chat is
  checked against PostgreSQL on every request. Configure `DEEPSEEK_API_KEY` on
  the server. No legacy `userId` from the request is used.

## Before accepting money

Ko-fi's verification token is a shared bearer secret, **not a cryptographic
signature**. Anyone with that token can forge a webhook. Restrict infrastructure
access, avoid logging payloads, rotate compromised tokens and independently
reconcile payments; risk is not zero. A public payment message can expose its
code, so never use the payment code for login/recovery. Ko-fi test notifications
and payment reversals require operational review; refunds, disputes, recurring
payments, support tools, and reconciliation are **not implemented**. A webhook
received after the 30-minute deadline is queued for manual review even if the
payment may have been timely. Do not promise instant activation in that case.

Operators must monitor `v2_kofi_events WHERE result='review'` and
`v2_kofi_conflicts`; verify real transactions in Ko-fi/payment-provider records
before correcting anything. There is currently no safe automated operator
approval UI. Plan for retention and cleanup of rate buckets, expired challenges,
sessions and intents; configure backups and observability without secrets.

## Tests

`npm test` runs unit and HTTP tests with PGlite. To additionally run the real
multi-connection PostgreSQL concurrency test against an isolated test database,
set `TEST_DATABASE_URL` and run `npm test`. The test creates/drops its own schema.
Real SMTP, Ko-fi and Android device testing are still required before launch.
