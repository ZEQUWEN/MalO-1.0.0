# Deploying MalO on Railway (`https://malo.up.railway.app`)

The Railway service serves the APK download page and the **ЮKassa** payment
gateway from one HTTPS origin. Railway builds the repository automatically from
the tracked `Dockerfile`; do not add a separate Railpack/Nixpacks build command.

| What | URL |
|------|-----|
| Download page | `https://malo.up.railway.app/` |
| Health check | `https://malo.up.railway.app/api/health` |
| Checkout catalogue | `https://malo.up.railway.app/api/catalog` |
| Available payment methods | `https://malo.up.railway.app/api/payment/methods` |
| YooKassa webhook | `https://malo.up.railway.app/api/webhooks/yookassa` |
| CryptoBot webhook | `https://malo.up.railway.app/api/webhooks/cryptobot` |

The app-facing `/api/payment/methods` endpoint requires the
`X-MalO-Client-Key` header and reports the currently configured methods.

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

# Required in production. Use at least 32 random bytes. This is deployment
# pairing, not end-user authentication (APK strings can be extracted).
MALO_CLIENT_KEY=<long-random-value>
MALO_MOCK_PROVIDERS=0

YOOKASSA_SHOP_ID=<shopId>
YOOKASSA_SECRET_KEY=<secret-key>
YOOKASSA_API_BASE=https://api.yookassa.ru/v3
YOOKASSA_RETURN_URL=malo://payment/return
YOOKASSA_VERIFY_NETWORK=1
# Enable only methods activated for this YooKassa shop.
YOOKASSA_BANK_CARD_ENABLED=1
YOOKASSA_SBP_ENABLED=1
YOOKASSA_REQUEST_TIMEOUT_MS=5000

# Keep this ONLY in Railway Variables — never in the APK, Git, or a client app.
CRYPTOBOT_TOKEN=<fresh-crypto-pay-api-token>
CRYPTOBOT_API_BASE=https://pay.crypt.bot/api
CRYPTOBOT_INVOICE_EXPIRES_IN=3600
CRYPTOBOT_REQUEST_TIMEOUT_MS=5000

MALO_PLAN_PERIOD_DAYS=30
MALO_PRICE_RUB=499.00
MALO_PRICE_USD=4.99
```

### Android gateway build values

The hosted APK must contain the same `MALO_GATEWAY_URL` and `MALO_CLIENT_KEY`
as the running gateway. The repository Dockerfile declares those two Railway
Variables as build arguments and writes them to a temporary `.env` only while
Gradle builds the APK. Therefore, after adding or changing either value, trigger
a **new deployment that rebuilds the image**; a runtime restart alone does not
replace the APK in `public/`.

Do **not** commit or upload a root `.env` file to GitHub. It is intentionally
excluded from the Docker build context. Keep `CRYPTOBOT_TOKEN`, YooKassa keys,
and all other provider secrets runtime-only — they are never build arguments
and must never be compiled into the APK.

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

Set the `YOOKASSA_BANK_CARD_ENABLED` and `YOOKASSA_SBP_ENABLED` variables to `1`
only for methods activated in the YooKassa shop; set an unavailable method to
`0`. The app fetches `/api/payment/methods` and does not show disabled checkout
buttons. The gateway independently rejects disabled methods with HTTP 503
before making any provider request.

Provider API calls time out after at most five seconds. Three consecutive
upstream/network failures open an in-memory circuit for 30 seconds; no payment
request is automatically retried. Circuit state resets on restart and is not
shared across replicas, so keep one replica unless the gateway moves to shared
state.

The gateway
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

### `CRYPTOBOT_API_BASE` is not the webhook URL

These two values point in opposite directions and must never be swapped:

| | Value | Direction | Where it is configured |
|---|---|---|---|
| `CRYPTOBOT_API_BASE` | `https://pay.crypt.bot/api` | gateway **→** Crypto Pay (outbound; `createInvoice`, `getInvoices`, `getExchangeRates`) | Railway Variables (optional — this is already the default) |
| Webhook URL | `https://malo.up.railway.app/api/webhooks/cryptobot` | Crypto Pay **→** gateway (inbound `invoice_paid`) | @CryptoBot → Crypto Pay → My Apps → Webhooks. **Not** a Railway variable |

Setting `CRYPTOBOT_API_BASE` to the webhook URL makes the gateway POST
`createInvoice` back to its own webhook route, which answers `400 BAD_PAYLOAD`;
the app then reports `CRYPTOBOT_ERROR` with `details.upstreamStatus: 400`. It
does not produce a 429.

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

When the app reports a gateway error, run the responder forensics instead of
guessing which layer produced it. It reports whether the MalO Express app
answered (its JSON body and `error.code`) or an intermediary did:

```sh
./server/scripts/diagnose-gateway.sh --url https://malo.up.railway.app --key "$MALO_CLIENT_KEY"
./server/scripts/diagnose-gateway.sh --invoice   # also replays the real createInvoice call
```

`/api/health` must report `providers.yookassa: true` after the shop ID and
secret key are configured. `GET /api/catalog` must include:

```json
{"paymentMethods":["bank_card","sbp"],"supportsSavedCards":true,"supportsSbp":true}
```

## Troubleshooting

| Symptom | Cause / action |
|---|---|
| App shows «Шлюз вернул ошибку 429» | **Not produced by this codebase** — `server/src` has no rate limiter, and a Crypto Pay 429 is reported as `CRYPTOBOT_RATE_LIMITED` with a readable message instead. A bare `429` is the app's fallback text for a response whose body was *not* MalO JSON, i.e. an intermediary (Railway edge, Cloudflare on a custom domain) answered before Express. Confirm with `./server/scripts/diagnose-gateway.sh --url <gateway> --key <MALO_CLIENT_KEY>`; if the Railway deploy log has no matching `"POST /api/crypto/invoices"` line, the request never reached the app. |
| `CRYPTOBOT_RATE_LIMITED` | Crypto Pay itself throttled the gateway. Honour the `Retry-After` header; check whether the same app token is in use by another deployment or environment. |
| `CRYPTOBOT_UNAUTHORIZED` | `CRYPTOBOT_TOKEN` is wrong, revoked, or belongs to the other network — a mainnet token sent to `testnet-pay.crypt.bot` fails exactly this way. The reported `details.apiBase` shows which endpoint refused it. |
| `YOOKASSA_NOT_CONFIGURED` | Set `YOOKASSA_SHOP_ID` and `YOOKASSA_SECRET_KEY` in Railway Variables, then redeploy. |
| `CRYPTOBOT_NOT_CONFIGURED` | Add a fresh `CRYPTOBOT_TOKEN` in Railway Variables and redeploy. Never put this secret into the APK. |
| `BAD_SIGNATURE` / `INVOICE_*_MISMATCH` from CryptoBot | Check that the app token is from the same Crypto Pay app and that the invoice was created by this gateway; the server intentionally refuses mismatched invoices. |
| `401 UNTRUSTED_SOURCE` on a YooKassa notification | Verify that `YOOKASSA_VERIFY_NETWORK=1` uses the published source ranges. Disable it only temporarily while diagnosing. |
| Payment succeeded but Pro is not active | Check the YooKassa webhook delivery log and the Railway log; the gateway is idempotent, so it is safe for YooKassa to retry. |
| Saved cards/subscriptions disappear after deploy | Attach the Volume to `/app/.data` and set `MALO_DATA_DIR=/app/.data`. |
| App shows «failed to connect to malo.up.railway.app» (`GATEWAY_UNREACHABLE` / `GATEWAY_TIMEOUT` / `GATEWAY_TLS_BLOCKED` / `GATEWAY_DNS_FAILED`) | The request never reached the gateway, so **CryptoBot was never called** — this is not a payment decline. See «Gateway unreachable» below. |
| Railway says `start.sh not found` | The service points at another branch/commit or an incorrect root directory. `Dockerfile`, `start.sh`, and `railway.json` must be at repository root. |

### Gateway unreachable («failed to connect to …»)

The Android client talks only to this gateway; `pay.crypt.bot` is called
*server-side* from Railway. A connection error on the device therefore says
nothing about Crypto Pay — it means the device could not open a TLS session
with the gateway host. Walk the three causes in order.

1. **Is the deployment alive?** From any network outside the device:

   ```bash
   curl -sS -o /dev/null -w '%{http_code}\n' https://malo.up.railway.app/api/health
   ./server/scripts/diagnose-gateway.sh --url https://malo.up.railway.app --key "$MALO_CLIENT_KEY"
   ```

   No answer anywhere → the Railway service is crashed, sleeping, or the
   generated domain changed. Check the Railway deploy log and that
   `MALO_PUBLIC_URL` still matches the real domain (a changed domain also
   leaves the registered Crypto Pay webhook pointing at the old deployment).

2. **Does the app point at the right host?** `MALO_GATEWAY_URL` is baked into
   the APK at build time from `.env`. An APK built before a domain change keeps
   calling the dead host; rebuild and reinstall.

3. **Network-level filtering (typical from RU ISPs).** If the health check
   succeeds from a server abroad but the phone fails — especially with
   `GATEWAY_TLS_BLOCKED` (TLS handshake reset after Client Hello) or with
   `GATEWAY_DNS_FAILED` — the device's network is filtering the route, not the
   payment flow. Verify by retrying on mobile data vs Wi-Fi, and by running
   `curl -v https://malo.up.railway.app/api/health` from the same network.
   The durable fix is to serve the gateway from a custom domain with its own
   certificate (Railway → Settings → Networking → Custom Domain), then set
   `MALO_PUBLIC_URL` and `MALO_GATEWAY_URL` to it and re-register both webhook
   URLs. Shared `*.up.railway.app` space is the part that gets filtered
   wholesale; a dedicated domain is not.

#### Diagnosing the resolved IP

Railway's public edge addresses can change, so do not classify an address as
invalid from a hard-coded IP range. For example, `69.46.46.21` is currently
announced by AS400940 (Railway). Compare the answers from the device's resolver
and a public resolver, using the exact hostname configured on the service:

```bash
dig +short <service-domain>            # what the local resolver says
dig +short @1.1.1.1 <service-domain>   # compare with a public resolver
```

Different answers suggest resolver/network interference, but matching answers
do not prove that the route is reachable. A connection timeout (`after
15000ms`) means the TCP connection to port 443 did not complete in time; it can
be caused by network filtering/routing, an unassigned or incorrect hostname, or
an unavailable service. Check `/api/health` from the same network and compare
with Railway deploy logs. A custom domain can avoid filtering specific to
`*.up.railway.app`, but it only works after both Railway-provided DNS records
are configured and the domain is verified.
