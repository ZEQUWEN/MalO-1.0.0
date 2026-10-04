# Deploying MalO on Railway (`https://malo.up.railway.app`)

The Railway service serves the APK download page and the **ЮKassa** payment
gateway from one HTTPS origin. Railway builds the repository automatically from
the tracked `Dockerfile`; do not add a separate Railpack/Nixpacks build command.

| What | URL |
|------|-----|
| Download page | `https://malo.up.railway.app/` |
| Health check | `https://malo.up.railway.app/api/health` |
| Checkout catalogue | `https://malo.up.railway.app/api/catalog` |
| YooKassa webhook | `https://malo.up.railway.app/api/webhooks/yookassa` |
| CryptoBot webhook | `https://malo.up.railway.app/api/webhooks/cryptobot` |

## 1. Railway service settings

1. Create a service from this repository and keep **Builder = Dockerfile**
   (`railway.json` already defines it).
2. Leave the service root at the repository root. Railway runs `Dockerfile`,
   builds the Android APK, then runs the lightweight Node gateway.
3. Attach a **Railway Volume** at `/app/.data` and keep this service at
   **one replica**. The included JSON store is intentionally single-instance.
4. Generate a Railway domain (or attach your custom HTTPS domain) and set that
   exact origin as `MALO_PUBLIC_URL` before registering provider webhooks.
   Railway's `RAILWAY_PUBLIC_DOMAIN` is used only as a fallback.
5. Create a deployment. Railway injects `$PORT`; do not hard-code a public port.

A successful runtime log includes:

```text
[MalO start] Serving MalO distribution + payment gateway on 0.0.0.0:<PORT>
[malo-gateway] listening on 0.0.0.0:<PORT>
[malo-gateway] public url: https://malo.up.railway.app
[malo-gateway] yookassa webhook:  https://malo.up.railway.app/api/webhooks/yookassa
[malo-gateway] Crypto Pay verified for app <app_id>.
```

If the last line says `Crypto Pay verification failed`, the token, API base, or
outbound connectivity is wrong. Do **not** register/publish until it is fixed.
The gateway starts but CryptoBot checkout fails closed.

The container binds to `0.0.0.0:$PORT`, which is required by Railway. The
`/api/health` check in `railway.json` is the deployment health check.

## 2. Railway Variables

Set these values in **Service → Variables**. `PORT` is supplied by Railway.
Never commit real keys or put them in the Android `.env` file.

```dotenv
# Use your actual Railway/custom public HTTPS origin, not an old deployment.
MALO_PUBLIC_URL=https://malo.up.railway.app
MALO_DATA_DIR=/app/.data

# Required in production. Use at least 32 random bytes and put the same value
# in the *release-build environment*, never in Git. This is deployment pairing,
# not end-user authentication (APK strings can be extracted).
MALO_CLIENT_KEY=<long-random-value>
MALO_MOCK_PROVIDERS=0

YOOKASSA_SHOP_ID=<shopId>
YOOKASSA_SECRET_KEY=<secret-key>
YOOKASSA_API_BASE=https://api.yookassa.ru/v3
YOOKASSA_RETURN_URL=malo://payment/return
YOOKASSA_VERIFY_NETWORK=1

# Keep this ONLY in Railway Variables — never in the APK, Git, or a client app.
CRYPTOBOT_TOKEN=<fresh-crypto-pay-api-token>
CRYPTOBOT_API_BASE=https://pay.crypt.bot/api
CRYPTOBOT_INVOICE_EXPIRES_IN=3600
CRYPTOBOT_REQUEST_TIMEOUT_MS=10000

MALO_PLAN_PERIOD_DAYS=30
MALO_PRICE_RUB=499.00
MALO_PRICE_USD=4.99
```

> **Persistence is mandatory.** `/app/.data` contains opaque provider-issued card
> tokens, subscription state, processed webhook IDs, and pending payments. A
> deploy without the mounted volume starts with an empty JSON store. For a
> multi-instance production service, replace `server/src/store.js` with a
> transactional PostgreSQL implementation before scaling — JSON storage is
> deliberately single-instance only.

## 3. Configure YooKassa

In YooKassa dashboard → **Интеграция → HTTP-уведомления**, register:

```text
https://malo.up.railway.app/api/webhooks/yookassa
```

Enable these events:

```text
payment.succeeded
payment.canceled
refund.succeeded
```

Enable both **Банковские карты** and **СБП** in the YooKassa shop. The gateway
creates either:

* `bank_card` checkout with `save_payment_method: true` when the user chooses
  to save a card; or
* `sbp` checkout with redirect confirmation for an initial purchase or manual
  subscription renewal.

The app does not collect a PAN, expiry, or CVC. It opens YooKassa's
`confirmation_url`; only the webhook may activate/extend the subscription.
СБП is presented as a manual payment/renewal method. Card auto-renewal requires
a separately saved bank-card payment method.

## 4. Configure Telegram CryptoBot webhooks

1. **Rotate the Crypto Pay API token if it has ever been sent in a chat, issue,
   screenshot, or commit.** Treat it as compromised. Do not paste it into any
   Android file — it belongs only in Railway as `CRYPTOBOT_TOKEN`.
2. In Telegram open **@CryptoBot → Crypto Pay → My Apps → your app → Webhooks**,
   enable webhooks and enter:

```text
https://malo.up.railway.app/api/webhooks/cryptobot
```

Crypto Pay sends an HTTPS `invoice_paid` update and retries a non-2xx response
up to 17 times. It is **Crypto Pay that sends the webhook to Railway**; the
Android app never marks an invoice paid. The gateway fails closed and grants
Pro only after all of these checks:

1. The untouched request bytes match `crypto-pay-api-signature` using
   `HMAC-SHA256(SHA256(CRYPTOBOT_TOKEN), rawBody)`.
2. The server fetches the invoice directly from Crypto Pay with its private
   token; it does not trust the webhook's amount or status fields.
3. The remote invoice ID, paid status, asset, exact decimal amount, and the
   **byte-for-byte server-generated payload** match the stored invoice.
4. The paid invoice and Pro entitlement are atomically persisted on the
   Railway Volume before a 2xx acknowledgement is returned. Replays and a UI
   polling request cannot extend the period twice.

A Crypto Pay invoice is paid from the user's Crypto Bot balance; its API does
not accept a blockchain-network parameter. The legacy `network` field in an
older APK is retained only as a funding hint/receipt field and is never used as
on-chain proof.

Use `https://pay.crypt.bot/api` for mainnet. Set a testnet endpoint/token only
in a separate Railway environment; never mix testnet and production state.

## 5. Build the Android app against Railway

At the repository root, set non-secret gateway settings before making the APK:

```dotenv
MALO_GATEWAY_URL=https://malo.up.railway.app
MALO_CLIENT_KEY=<same-long-random-value>
```

Then rebuild the APK. The app uses `malo://payment/return`, which is declared
in `AndroidManifest.xml`; after 3-D Secure or СБП it refreshes «Мои карты» and
the subscription from the server.

`MALO_CLIENT_KEY` is only a deployment pairing value — any value compiled into
an APK can be extracted. Before a commercial RuStore release, protect real
users with an authenticated account/session and enforce user ownership on the
gateway; do not treat an APK-embedded key as a credential.

## 6. Commercial / RuStore release checklist

This repository handles the technical payment confirmation; it does not itself
certify legal or marketplace compliance. Before submitting a commercial build:

1. Confirm the current RuStore rules for digital subscriptions and external
   payment methods, and use the required RuStore billing flow if one applies to
   your category. Do not represent CryptoBot as a RuStore payment method unless
   that review permits it.
2. Publish the price, billing period, renewal/cancellation/refund terms,
   developer contact, privacy policy, and personal-data processing information
   required for your audience. Keep a support path for payment disputes.
3. Use a real account/session service before release. `MALO_CLIENT_KEY` is only
   a deployment-pairing header: because it is present in an APK, it is not a
   user credential and cannot establish ownership of a subscription.
4. Keep `CRYPTOBOT_TOKEN`, YooKassa secrets, Railway Volume backups, and release
   signing keys out of Git and out of the APK. Rotate any token ever shared in
   a log, screenshot, or chat.
5. Test an actual Crypto Pay invoice in a Railway **staging** environment with
   its own token/domain/Volume. Verify the webhook-delivery log, activation,
   duplicate delivery, cancellation, and an expired invoice before production.

## 7. Smoke test

```sh
curl https://malo.up.railway.app/api/health
curl https://malo.up.railway.app/api/catalog
curl https://malo.up.railway.app/api/webhooks
# Verify that health reports both configured providers without exposing secrets:
# curl https://malo.up.railway.app/api/health
curl -I https://malo.up.railway.app/favicon.ico
```

`/api/health` must report `providers.yookassa: true` after the shop ID and
secret key are configured. `GET /api/catalog` must include:

```json
{"paymentMethods":["bank_card","sbp"],"supportsSavedCards":true,"supportsSbp":true}
```

## Troubleshooting

| Symptom | Cause / action |
|---|---|
| `YOOKASSA_NOT_CONFIGURED` | Set `YOOKASSA_SHOP_ID` and `YOOKASSA_SECRET_KEY` in Railway Variables, then redeploy. |
| `CRYPTOBOT_NOT_CONFIGURED` | Add a fresh `CRYPTOBOT_TOKEN` in Railway Variables and redeploy. Never put this secret into the APK. |
| `BAD_SIGNATURE` / `INVOICE_*_MISMATCH` from CryptoBot | Check that the app token is from the same Crypto Pay app and that the invoice was created by this gateway; the server intentionally refuses mismatched invoices. |
| `401 UNTRUSTED_SOURCE` on a YooKassa notification | Verify that `YOOKASSA_VERIFY_NETWORK=1` uses the published source ranges. Disable it only temporarily while diagnosing. |
| Payment succeeded but Pro is not active | Check the YooKassa webhook delivery log and the Railway log; the gateway is idempotent, so it is safe for YooKassa to retry. |
| Saved cards/subscriptions disappear after deploy | Attach the Volume to `/app/.data` and set `MALO_DATA_DIR=/app/.data`. |
| Railway says `start.sh not found` | The service points at another branch/commit or an incorrect root directory. `Dockerfile`, `start.sh`, and `railway.json` must be at repository root. |
