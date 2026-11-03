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

`build.sh` checks Android SDK package availability with
`sdkmanager --list --newer` before compiling. It reports new or updatable
packages but does not install them, so the SDK platform and Build Tools
selected for the build remain unchanged.
Set `MALO_ANDROID_SDK_UPDATE_CHECK=0` to skip the online check. The script also
downloads the pinned Gradle 9.3.1 distribution on its first run, checks its
SHA-256 checksum, and creates a debug APK. The resulting files are:

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

## Billing and accounts (v2, experimental)

The current gateway provides email sign-in and one-time Ko-fi payment using
PostgreSQL. Legacy client-selected user ID APIs and the old YooKassa/CryptoBot
routes are disabled; existing APKs and old subscriptions are not automatically
migrated. Follow [server/README.md](server/README.md) and [DEPLOY.md](DEPLOY.md)
before deployment. This payment flow is **not yet ready for live use**: operator
reconciliation, refund/dispute handling and production testing are still needed.

Build the Android APK with `MALO_GATEWAY_URL` set to the HTTPS gateway address.
Do not put SMTP, database, authentication pepper, provider tokens or AI keys in
the APK. The app does not need the former shared client key for v2.

## API keys

`.env.example` provides the placeholder `GEMINI_API_KEY` so the APK can be
built without committing secrets. DeepSeek is called through the Node gateway:
set `DEEPSEEK_API_KEY` as a server runtime variable (for example, in Railway),
never in the Android `.env` or APK. Do not commit `.env`, APK artifacts, or
signing keystores.

Keys compiled into an Android APK can be extracted by an end user. DeepSeek's
provider key stays behind the gateway. The Railway image intentionally builds
with placeholders and does not expose provider secrets to the APK.
