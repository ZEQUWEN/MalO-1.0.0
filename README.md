# MalO 1.0.0

MalO is an Android application. A Railway deployment cannot run an APK directly;
it builds the APK and serves a small download page with the package.

## Build an APK locally

The build requires:

- JDK **17**;
- Android SDK Platform **36.1** and Build Tools **36.0.0**;
- `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) pointing to that SDK, or `sdk.dir` in
  `local.properties`.

Run:

```sh
chmod +x build.sh start.sh
./build.sh
```

`build.sh` downloads the pinned Gradle 9.3.1 distribution on its first run,
checks its SHA-256 checksum, and creates a debug APK. The resulting files are:

- `public/MalO-1.0.0.apk` — primary download;
- `public/app-debug.apk` — compatibility mirror for the download page.

For a local check of the distribution page, run:

```sh
PORT=8080 ./start.sh
```

## Deploy to Railway

The repository contains `railway.json` with `builder: "DOCKERFILE"`. Do not
set a custom Railpack/Nixpacks build command in the Railway service: Railway
must use the repository `Dockerfile`.

The Docker build stage installs JDK 17, Android command-line tools, Android API
36.1, and Build Tools 36.0.0; then it calls `./build.sh`. The final lightweight
image only starts `./start.sh`, which listens on Railway's `$PORT` and serves
the contents of `public/`.

After pushing these files, create a new deployment from the same repository and
branch. If the Railway log still lists only `app/`, `assets/`, and Gradle files
and says `start.sh not found`, that service is building a different commit,
branch, or root directory: the tracked `start.sh`, `build.sh`, `Dockerfile`,
and `railway.json` must be visible at the deployment root.

## Billing (ЮKassa, СБП + Telegram CryptoBot)

Payments live in [`server/`](server/README.md) — a small Node/Express gateway
that Railway runs alongside the APK download page:

* **Банковские карты** — ЮKassa (Visa / Mastercard / МИР and other schemes
  returned by the acquirer). The Android app does **not** render a PAN/CVC
  form. It opens ЮKassa's hosted 3‑D Secure checkout, and the gateway retains
  only the reusable `payment_method.id` needed for card auto-renewal.
* **СБП** — the app creates an explicit ЮKassa `sbp` redirect checkout. This is
  available both for the initial payment and for manual subscription renewal;
  it never creates a locally saved card or silently enables auto-renewal.
* **Telegram CryptoBot** — an additional Crypto Pay option. The server creates
  a fixed invoice for the plan price and opens its `bot_invoice_url` / Mini App
  URL. Crypto Pay sends the signed `invoice_paid` webhook to Railway; the app
  never self-confirms an invoice. The gateway refetches the provider invoice,
  then requires invoice ID, paid status, asset, exact decimal amount and the
  byte-for-byte server-issued payload to match before Pro is activated.
* **«Мои карты»** — a dedicated mini-app-style submenu with a horizontal swipe
  pager. It mirrors only the scheme, masked number, expiry, and selected-card
  state received from the gateway. Users can swipe to a card, choose it for
  auto-renewal, add another card through ЮKassa, or delete it.
* **Webhooks** — `POST /api/webhooks/yookassa` is source-IP allowlisted;
  `POST /api/webhooks/cryptobot` verifies `HMAC-SHA256(SHA256(token), rawBody)`.
  Both are replay-protected and idempotent, and are the authority that
  activates or extends a subscription after payment confirmation.

`CardBrand.kt` retains correct local rendering for the scheme supplied by
ЮKassa and other masked descriptors. In particular, the МИР range `2200`–
`2205` is checked before Mastercard's range, so `22051387` resolves to **МИР**
and is displayed with the correct mark.

The production Railway deployment lives at **https://malo.up.railway.app**.
Register both payment webhooks:

```
https://malo.up.railway.app/api/webhooks/yookassa
https://malo.up.railway.app/api/webhooks/cryptobot
```

`GET /api/webhooks` prints the runtime URLs. Full Railway variables, volume,
webhook, and smoke-test instructions are in [DEPLOY.md](DEPLOY.md).

For a **local** APK build, point the app at the gateway via an untracked `.env`:

```sh
MALO_GATEWAY_URL=https://malo.up.railway.app
MALO_CLIENT_KEY=<same value as on the gateway>
```

For the APK built by Railway's Dockerfile, set those two values in Railway
**Variables** instead. The Dockerfile opts them into the build as arguments, so
changing either value requires a new image build/deployment. Never commit `.env`
or any provider secret to GitHub.

Setting `MALO_GATEWAY_URL=MY_MALO_GATEWAY_URL` disables checkout in that build;
it never replaces a real payment with a demo card transaction.

## API keys

`.env.example` provides the placeholder `GEMINI_API_KEY` so the APK can be
built without committing secrets. DeepSeek is called through the Node gateway:
set `DEEPSEEK_API_KEY` as a server runtime variable (for example, in Railway),
never in the Android `.env` or APK. Do not commit `.env`, APK artifacts, or
signing keystores.

Keys compiled into an Android APK can be extracted by an end user. DeepSeek's
provider key stays behind the gateway. The Railway image intentionally builds
with placeholders and does not expose provider secrets to the APK.
