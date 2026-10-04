# Deploying MalO on Railway (`https://malo.up.railway.app`)

The single Railway service now serves **both** the APK download page and the
payment gateway, so webhooks arrive on your own domain.

| What | URL |
|------|-----|
| Download page | `https://malo.up.railway.app/` |
| Health check | `https://malo.up.railway.app/api/health` |
| Webhook cheat-sheet | `https://malo.up.railway.app/api/webhooks` |
| **CryptoBot webhook** | `https://malo.up.railway.app/api/webhooks/cryptobot` |
| **ЮKassa webhook** | `https://malo.up.railway.app/api/webhooks/yookassa` |

## 1. Redeploy

The container must pick up the new `Dockerfile` / `start.sh`. You are still on
the old image if the logs say:

```
[MalO start] Serving MalO APK distribution on 0.0.0.0:8080
100.64.0.2 - - [...] code 404, message File not found
```

That is Python's `http.server` (it writes its access log to **stderr**, which
is why Railway painted every normal request red). After redeploying you should
see instead:

```
[MalO start] Serving MalO distribution + payment gateway on 0.0.0.0:8080
[malo-gateway] listening on 0.0.0.0:8080
[malo-gateway] public url: https://malo.up.railway.app
[malo-gateway] cryptobot webhook: https://malo.up.railway.app/api/webhooks/cryptobot
[malo-gateway] yookassa webhook:  https://malo.up.railway.app/api/webhooks/yookassa
```

Access logs now go to stdout (`[inf]`), and only 5xx responses go to stderr.

## 2. Railway variables

Set these on the service (Variables tab). `PORT` is injected by Railway.

```
MALO_PUBLIC_URL=https://malo.up.railway.app
MALO_CLIENT_KEY=<long random string, also put it in the app .env>
MALO_DATA_DIR=/app/.data

YOOKASSA_SHOP_ID=<shopId>
YOOKASSA_SECRET_KEY=<secret key>
YOOKASSA_VERIFY_NETWORK=1

CRYPTOBOT_TOKEN=<Crypto Pay app token>
CRYPTOBOT_API_BASE=https://pay.crypt.bot/api
```

`MALO_PUBLIC_URL` already defaults to `https://malo.up.railway.app`, so the
webhook URLs are correct even if you forget to set it.

> **Persistence:** `MALO_DATA_DIR` holds subscriptions and card tokens in a
> JSON file. Attach a Railway **Volume** mounted at `/app/.data`, otherwise the
> state is lost on every redeploy.

## 3. Register the webhooks

**CryptoBot** — Telegram `@CryptoBot` → *Crypto Pay* → *My Apps* → your app →
*Webhooks* → **Enable** and paste:

```
https://malo.up.railway.app/api/webhooks/cryptobot
```

Signature check: `HMAC_SHA256(SHA256(app_token), rawBody)` from the
`crypto-pay-api-signature` header. A wrong token ⇒ `401 BAD_SIGNATURE`.

**ЮKassa** — dashboard → *Интеграция* → *HTTP-уведомления* →

```
https://malo.up.railway.app/api/webhooks/yookassa
```

events: `payment.succeeded`, `payment.canceled`, `refund.succeeded`.

## 4. Point the app at the domain

`.env` (used by the Secrets Gradle plugin at build time):

```
MALO_GATEWAY_URL=https://malo.up.railway.app
MALO_CLIENT_KEY=<same value as on Railway>
```

Rebuild the APK afterwards — these end up in `BuildConfig`.

## 5. Smoke test

```sh
curl https://malo.up.railway.app/api/health
curl https://malo.up.railway.app/api/webhooks
curl https://malo.up.railway.app/api/catalog
curl -I https://malo.up.railway.app/favicon.ico      # 200, no more 404 noise
```

`/api/health` reports which providers are configured:

```json
{"ok":true,"providers":{"yookassa":true,"cryptobot":true}, ... }
```

## Troubleshooting

| Symptom | Cause |
|---------|-------|
| `code 404, message File not found` for `/favicon.ico` | old Python image; the icon set now lives in `public/` |
| Every request shown as `[err]` | Python's logger wrote to stderr; the Node gateway logs to stdout |
| `401 BAD_SIGNATURE` on CryptoBot | `CRYPTOBOT_TOKEN` differs from the app that sent the invoice |
| `401 UNTRUSTED_SOURCE` on ЮKassa | notification came from outside the published subnets; set `YOOKASSA_VERIFY_NETWORK=0` only to debug |
| Subscription resets after redeploy | no volume mounted at `MALO_DATA_DIR` |
