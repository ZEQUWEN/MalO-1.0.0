# MalO payment gateway

Node/Express service that handles everything money-related for the MalO
Android app, so that **no acquirer or CryptoBot secret is ever compiled into the
APK**.

* **Card billing** — ЮKassa (Visa / Mastercard / МИР), hosted 3‑D Secure page,
  saved cards (`payment_method.id`) and recurring charges.
* **Crypto billing** — CryptoBot (Telegram *Crypto Pay API*) invoices with an
  explicit network choice: TRON (TRC‑20), TON, Ethereum (ERC‑20), BNB Smart
  Chain (BEP‑20), Solana (SPL), Bitcoin, Litecoin, Polygon.
* **Webhooks** — signature-verified, replay-protected and idempotent.
* **Subscriptions** — 30-day periods, auto-renewal, cancel/resume.

## Run locally

```sh
cd server
npm install
cp .env.example .env     # fill in the provider keys
npm start                # http://localhost:8080
```

Mock mode needs no credentials at all and is what the test suite uses:

```sh
MALO_MOCK_PROVIDERS=1 npm start
npm test                 # 9 tests, node:test
```

## Webhook endpoints

| Provider  | URL                                      | Verification |
|-----------|------------------------------------------|--------------|
| CryptoBot | `POST /api/webhooks/cryptobot`           | `crypto-pay-api-signature` = `HMAC_SHA256(SHA256(token), rawBody)` |
| ЮKassa    | `POST /api/webhooks/yookassa`            | source IP against YooKassa's published subnets (`YOOKASSA_VERIFY_NETWORK=1`) |

For the production deployment (`MALO_PUBLIC_URL`, default
`https://malo.up.railway.app`) register:

* **CryptoBot** → `@CryptoBot` → *Crypto Pay* → *My Apps* → *Webhooks* →
  `https://malo.up.railway.app/api/webhooks/cryptobot`
* **ЮKassa** → dashboard → *Интеграция* → *HTTP-уведомления* →
  `https://malo.up.railway.app/api/webhooks/yookassa`, events
  `payment.succeeded`, `payment.canceled`, `refund.succeeded`.

`GET /api/webhooks` returns exactly these URLs at runtime, and they are printed
on startup — see [../DEPLOY.md](../DEPLOY.md).

Both handlers:

* reject deliveries with an invalid signature / untrusted source (`401`);
* drop replays older than `CRYPTOBOT_WEBHOOK_MAX_AGE` seconds;
* de-duplicate by `update_id` / `event:payment_id`, so a retried delivery can
  never grant a second subscription period.

## App-facing API

All of these accept `userId` (an opaque installation id generated on device)
and require the `X-MalO-Client-Key` header when `MALO_CLIENT_KEY` is set.

| Method | Path | Purpose |
|--------|------|---------|
| `GET`  | `/api/health` | liveness + which providers are configured |
| `GET`  | `/api/webhooks` | the exact webhook URLs to register with each provider |
| `GET`  | `/api/catalog` | plan price, accepted card brands, crypto asset/network matrix |
| `POST` | `/api/crypto/invoices` | create a CryptoBot invoice (`asset`, `network`) |
| `GET`  | `/api/crypto/invoices/:id` | poll invoice status (fallback for a late webhook) |
| `POST` | `/api/cards/checkout` | start a ЮKassa payment, optionally saving the card |
| `GET`  | `/api/cards/payments/:id` | poll a card payment |
| `GET`  | `/api/cards` | list saved cards (brand, last4, expiry — never the PAN) |
| `POST` | `/api/cards/:id/default` | choose the card used for auto-renewal |
| `DELETE` | `/api/cards/:id` | forget the card and revoke its recurring token |
| `GET`  | `/api/subscription` | current entitlement (HMAC-signed) |
| `POST` | `/api/subscription/cancel` | cancel auto-renewal (`immediate: true` to revoke now) |
| `POST` | `/api/subscription/resume` | re-enable auto-renewal |
| `POST` | `/api/subscription/charge` | charge the saved card for the next period |
| `POST` | `/api/subscription/run-renewals` | cron sweeper for all due subscriptions |

### Asset / network matrix

`GET /api/catalog` returns, for every asset, the networks it may be paid on —
the server rejects impossible combinations (e.g. `BTC` on `SOLANA`) with
`UNSUPPORTED_NETWORK`. The chosen network is embedded in the invoice payload
and echoed back by the webhook, so the receipt always names the exact rail.

## Security notes

* Card data never reaches this service: the PAN and the CVC are typed on the
  acquirer's own 3‑D Secure page. We only persist `payment_method.id`.
* Subscription payloads are signed with HMAC-SHA256 so the Android client can
  detect tampering with its local cache.
* State lives in a single JSON file (`MALO_DATA_DIR`). Swap `src/store.js` for
  Postgres/Redis before scaling beyond one instance.
