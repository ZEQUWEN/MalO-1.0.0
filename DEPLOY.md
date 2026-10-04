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

MALO_PLAN_PERIOD_DAYS=30
MALO_PRICE_RUB=499.00
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

## 4. Build the Android app against Railway

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

## 5. Smoke test

```sh
curl https://malo.up.railway.app/api/health
curl https://malo.up.railway.app/api/catalog
curl https://malo.up.railway.app/api/webhooks
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
| `401 UNTRUSTED_SOURCE` on a YooKassa notification | Verify that `YOOKASSA_VERIFY_NETWORK=1` uses the published source ranges. Disable it only temporarily while diagnosing. |
| Payment succeeded but Pro is not active | Check the YooKassa webhook delivery log and the Railway log; the gateway is idempotent, so it is safe for YooKassa to retry. |
| Saved cards/subscriptions disappear after deploy | Attach the Volume to `/app/.data` and set `MALO_DATA_DIR=/app/.data`. |
| Railway says `start.sh not found` | The service points at another branch/commit or an incorrect root directory. `Dockerfile`, `start.sh`, and `railway.json` must be at repository root. |
