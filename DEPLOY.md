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
3. Attach a **Railway Volume** at `/app/.data`.
4. Create a deployment. Railway injects `$PORT`; do not hard-code a public port.

A successful runtime log includes:

```text
[MalO start] Serving MalO distribution + payment gateway on 0.0.0.0:<PORT>
[malo-gateway] listening on 0.0.0.0:<PORT>
[malo-gateway] public url: https://malo.up.railway.app
[malo-gateway] yookassa webhook:  https://malo.up.railway.app/api/webhooks/yookassa
```

The container binds to `0.0.0.0:$PORT`, which is required by Railway. The
`/api/health` check in `railway.json` is the deployment health check.

## 2. Railway Variables

Set these values in **Service → Variables**. `PORT` is supplied by Railway.
Never commit real keys or put them in the Android `.env` file.

```dotenv
MALO_PUBLIC_URL=https://malo.up.railway.app
MALO_DATA_DIR=/app/.data

# A high-entropy value. It must also be MALO_CLIENT_KEY in the Android build.
MALO_CLIENT_KEY=<long-random-value>

YOOKASSA_SHOP_ID=<shopId>
YOOKASSA_SECRET_KEY=<secret-key>
YOOKASSA_API_BASE=https://api.yookassa.ru/v3
YOOKASSA_RETURN_URL=malo://payment/return
YOOKASSA_VERIFY_NETWORK=1

# Keep this ONLY in Railway Variables — never in the APK, Git, or a client app.
CRYPTOBOT_TOKEN=<fresh-crypto-pay-api-token>
CRYPTOBOT_API_BASE=https://pay.crypt.bot/api
CRYPTOBOT_INVOICE_EXPIRES_IN=3600

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
up to 17 times. The gateway keeps the raw request body, verifies
`crypto-pay-api-signature` using `HMAC-SHA256(SHA256(CRYPTOBOT_TOKEN), rawBody)`,
then fetches the provider invoice itself. Pro is granted only when the paid
provider invoice has the same invoice ID, asset, decimal amount and
server-generated payload as a locally issued MalO invoice. Duplicate webhook
updates cannot extend the period twice.

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

## 6. Smoke test

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
