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

## Billing (cards + crypto)

Payments live in [`server/`](server/README.md) — a small Node/Express gateway
that the Railway image now runs alongside the APK download page:

* **Банковские карты** — ЮKassa (Visa / Mastercard / МИР). The PAN and the CVC
  are entered on the acquirer's hosted 3‑D Secure page; the app only keeps the
  scheme, the last four digits and a recurring token.
* **Криптовалюта** — CryptoBot (Telegram *Crypto Pay API*) with an explicit
  network selector: TRON (TRC‑20), TON, Ethereum (ERC‑20), BNB Smart Chain,
  Solana, Bitcoin, Litecoin, Polygon.
* **Вебхуки** — `POST /api/webhooks/cryptobot` (HMAC‑SHA256 over the raw body
  with `SHA256(token)` as the key) and `POST /api/webhooks/yookassa`
  (source-IP allowlist). Both are replay-protected and idempotent.
* **Картхолдер** — saved cards with a card-shaped UI, auto-payment toggle and
  in-app subscription cancellation.

In the app, the payment system (Visa / Mastercard / МИР / AmEx / UnionPay /
JCB / Maestro) is detected live from the typed BIN and its logo is drawn with
Compose primitives — see `app/src/main/java/com/example/payments/CardBrand.kt`
and `app/src/main/java/com/example/ui/payments/`.

The production deployment lives at **https://malo.up.railway.app**, so the
webhooks to register with the providers are:

```
https://malo.up.railway.app/api/webhooks/cryptobot
https://malo.up.railway.app/api/webhooks/yookassa
```

`GET /api/webhooks` prints that cheat-sheet at runtime. Full instructions
(Railway variables, volume, smoke tests) are in [DEPLOY.md](DEPLOY.md).

Point the app at the gateway via `.env`:

```sh
MALO_GATEWAY_URL=https://malo.up.railway.app
MALO_CLIENT_KEY=<same value as on the gateway>
```

Setting `MALO_GATEWAY_URL=MY_MALO_GATEWAY_URL` builds an offline/demo APK that
falls back to the bundled `SubscriptionValidator`.

## API keys

`.env.example` provides placeholder `GEMINI_API_KEY` and `DEEPSEEK_API_KEY`
values so the APK can be built without committing secrets. For local
development, copy it to `.env` and put your own keys there. Do not commit
`.env`, APK artifacts, or signing keystores.

Keys compiled into an Android APK can be extracted by an end user. For a
production release, keep provider keys behind an authenticated backend instead
of distributing them in `BuildConfig`. The Railway image intentionally builds
with placeholders and only hosts the download page; it does not expose API
provider secrets to the APK.
