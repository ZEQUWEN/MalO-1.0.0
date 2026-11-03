# Deployment notes

The payment/account architecture has changed. Read [server/README.md](server/README.md)
for the required PostgreSQL, SMTP, email-session and Ko-fi setup and limitations.

**Do not deploy this as a drop-in replacement for the old gateway.** The legacy
API and its YooKassa/CryptoBot webhooks are disabled, and old APKs cannot use
v2. Keep backups of the legacy JSON volume and reconcile pending payments before
cutover. Existing subscriptions are deliberately not migrated automatically.

The v2 implementation requires an explicit PostgreSQL schema migration
(`cd server && npm ci && npm run db:migrate` with `DATABASE_URL` set), runtime
secrets, and a newly built APK with an HTTPS `MALO_GATEWAY_URL`. Follow the
operational safeguards and test prerequisites in the server README before
considering real payments. Ko-fi's verification token is a shared secret rather
than a signed webhook; payment review, refunds and dispute tooling are not yet
implemented. No code change alone makes this payment flow risk-free.
