# MalO 1.0.0

MalO is an Android application. A Railway deployment cannot run an APK directly;
it builds the APK and serves a small download page with the package.

## Build an APK locally

The build requires:

- JDK **17**;
- Android SDK Platform **36** and Build Tools **36.0.0**;
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
36, and Build Tools 36.0.0; then it calls `./build.sh`. The final lightweight
image only starts `./start.sh`, which listens on Railway's `$PORT` and serves
the contents of `public/`.

After pushing these files, create a new deployment from the same repository and
branch. If the Railway log still lists only `app/`, `assets/`, and Gradle files
and says `start.sh not found`, that service is building a different commit,
branch, or root directory: the tracked `start.sh`, `build.sh`, `Dockerfile`,
and `railway.json` must be visible at the deployment root.

## API key

`.env.example` provides a placeholder `GEMINI_API_KEY` so that the APK builds
without committing a secret. For local development, copy it to `.env` and put
your own key there. Do not commit `.env` or a signing keystore.
