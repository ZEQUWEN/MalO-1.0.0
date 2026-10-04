# MalO payment gateway

Node/Express gateway for the Android app. It is designed for a Railway service
that owns the YooKassa integration, so the APK contains no YooKassa secret and
never receives card data.

## Payment model

* **Card checkout** — `bank_card` redirect flow in YooKassa. PAN, expiry, CVC,
  and 3-D Secure are entered on YooKassa's page. After `payment.succeeded`, the
  gateway stores only YooKassa's reusable `payment_method.id`; the app receives
  only payment scheme, masked number, expiry, and default-card state.
* **Saved cards / auto-renewal** — the server charges the selected saved
  `payment_method.id`. It never returns that token to Android.
* **СБП** — explicit `sbp` redirect checkout, usable for initial payment and
  manual extension of an active subscription. СБП creates no local saved card;
  auto-renewal remains a selected-card operation.
* **Telegram CryptoBot** — the gateway creates a Crypto Pay invoice for the
  server price and opens the returned bot/Mini App URL. Crypto Pay — not the
  Android app — POSTs `invoice_paid` to Railway. The server verifies the
  raw-body HMAC, refetches the provider invoice, then compares invoice ID,
  `paid` status, asset, exact decimal amount and the byte-for-byte
  server-generated payload before granting Pro. The paid invoice and
  entitlement are durably stored before it returns a 2xx response.
* **«Мои карты»** — Android renders a swipeable card pager from `GET /api/cards`
  and makes default/remove operations through this gateway. It has no raw-card
  input fields.

## Run locally

```sh
cd server
npm ci
cp .env.example .env     # fill in the provider keys
npm start                # http://localhost:8080
```

Automated tests use no real provider requests:

```sh
MALO_MOCK_PROVIDERS=1 npm test
```

## YooKassa webhook

Register this HTTPS URL in YooKassa dashboard → **Интеграция →
HTTP-уведомления**:

```text
https://malo.up.railway.app/api/webhooks/yookassa
```

Select `payment.succeeded`, `payment.canceled`, and `refund.succeeded`.
`YOOKASSA_VERIFY_NETWORK=1` restricts notifications to YooKassa's documented
source subnets. The handler is replay-protected and idempotent by
`event:payment_id`, so a retry cannot add a second subscription period.

## CryptoBot webhook

Set a **fresh** Crypto Pay API token as the Railway-only `CRYPTOBOT_TOKEN`
variable. Do not place it in `app/.env`, `BuildConfig`, source code, or Git.
In Telegram: **@CryptoBot → Crypto Pay → My Apps → [app] → Webhooks → Enable**
and enter:

```text
https://malo.up.railway.app/api/webhooks/cryptobot
```

Crypto Pay retries failed webhook delivery. The route reads the unparsed body
and accepts it only when `crypto-pay-api-signature` equals
`HMAC-SHA256(SHA256(CRYPTOBOT_TOKEN), rawBody)`. This signature is required
**even when `MALO_MOCK_PROVIDERS=1`** so a mistaken mock setting cannot make an
unsigned entitlement endpoint. `update_id` is documented as non-unique, so
deduplication is by immutable paid invoice ID rather than update ID. A valid
signature alone is not enough: the server refetches the invoice and checks the
provider's `paid` status, asset and exact decimal amount against the locally
created invoice and compares the opaque JSON payload byte-for-byte.

Crypto Pay invoices are paid from a Crypto Bot balance. The Crypto Pay invoice
API accepts an asset, not an external blockchain-network transaction; the
legacy `network` request field is a backwards-compatible app hint only and is
not treated as payment proof.

## App-facing API

All app endpoints require `userId`. In a non-mock gateway, `MALO_CLIENT_KEY`
is required and requests must include it in `X-MalO-Client-Key`; a missing
variable fails closed with `CLIENT_AUTH_NOT_CONFIGURED`.

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/health` | liveness and provider configuration |
| `GET` | `/api/catalog` | plan, accepted brands, `bank_card` and `sbp` methods |
| `POST` | `/api/checkout` | start a YooKassa checkout (`paymentMethod: bank_card\|sbp`) |
| `POST` | `/api/crypto/invoices` | create a fixed-price CryptoBot invoice (`asset`; `network` is a legacy hint) |
| `GET` | `/api/crypto/invoices/:id?userId=…` | read/poll only the caller's CryptoBot invoice |
| `POST` | `/api/cards/checkout` | compatibility alias for old Android builds |
| `GET` | `/api/cards/payments/:id?userId=…` | poll a pending YooKassa confirmation |
| `GET` | `/api/cards?userId=…` | saved card descriptors only — never PAN/token |
| `POST` | `/api/cards/:id/default` | select a card for auto-renewal |
| `DELETE` | `/api/cards/:id?userId=…` | forget a card and revoke its stored token |
| `GET` | `/api/subscription?userId=…` | current entitlement (HMAC-signed) |
| `POST` | `/api/subscription/cancel` | cancel auto-renewal (`immediate: true` revokes now) |
| `POST` | `/api/subscription/resume` | re-enable selected-card auto-renewal |
| `POST` | `/api/subscription/charge` | charge the selected saved card |
| `POST` | `/api/subscription/run-renewals` | single-instance cron/worker endpoint |

Example request bodies:

```json
{"userId":"malo-installation-id","paymentMethod":"bank_card","saveCard":true}
```

```json
{"userId":"malo-installation-id","paymentMethod":"sbp","saveCard":false}
```

## Railway and production notes

Set `MALO_DATA_DIR=/app/.data` and attach a Railway Volume at that path. The
included JSON store is safe for a single Railway instance only; use a real
transactional store (for example PostgreSQL) before running more than one
instance or worker.

A key bundled in an APK is not a user-authentication system. For a commercial
RuStore release, add an authenticated account/session layer before relying on
`userId` ownership or using a client key as an access-control boundary.
