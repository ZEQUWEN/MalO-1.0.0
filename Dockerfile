# Build the Android package in a JDK/Android-SDK image, then serve only the
# distribution files. Railway therefore never has to guess the project type.
FROM eclipse-temurin:17-jdk-jammy AS builder

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_CMDLINE_TOOLS_VERSION=15859902 \
    ANDROID_CMDLINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583

RUN apt-get update \
    && apt-get install --yes --no-install-recommends ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && curl --fail --location --retry 3 --silent --show-error \
      "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" \
      --output /tmp/android-commandline-tools.zip \
    && echo "${ANDROID_CMDLINE_TOOLS_SHA256}  /tmp/android-commandline-tools.zip" | sha256sum --check --strict \
    && unzip -q /tmp/android-commandline-tools.zip -d "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm /tmp/android-commandline-tools.zip

ENV PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

WORKDIR /workspace
COPY . .

# Railway only passes service variables into a Docker build when the Dockerfile
# explicitly declares matching ARGs. These two values are compiled into the
# Android APK so it can reach the same gateway as the runtime service. They are
# a deployment-pairing value, not a substitute for user authentication. Keep
# provider secrets (especially CRYPTOBOT_TOKEN) runtime-only: never add them
# as Docker build args or to an APK.
ARG MALO_GATEWAY_URL
ARG MALO_CLIENT_KEY

# `.env` is deliberately excluded from the Docker build context. Start with
# non-secret defaults, then append Railway's build-time gateway variables in
# this *single* layer. The temporary file is deleted after Gradle has generated
# BuildConfig, and the final runtime image contains only the generated APK.
RUN cp .env.example .env \
    && if [ -n "$MALO_GATEWAY_URL" ]; then printf '\nMALO_GATEWAY_URL=%s\n' "$MALO_GATEWAY_URL" >> .env; fi \
    && if [ -n "$MALO_CLIENT_KEY" ]; then printf 'MALO_CLIENT_KEY=%s\n' "$MALO_CLIENT_KEY" >> .env; fi \
    && chmod +x build.sh start.sh \
    && ./build.sh \
    && rm -f .env

FROM node:20-alpine AS runtime

WORKDIR /app

# Payment gateway (ЮKassa card billing + CryptoBot webhooks).
COPY server/package.json server/package-lock.json* /app/server/
RUN cd /app/server && npm ci --omit=dev || npm install --omit=dev
COPY server/src /app/server/src

COPY --from=builder /workspace/public /app/public
COPY start.sh /app/start.sh

# Railway mounts its persistent Volume at /app/.data. Creating it in the image
# also keeps local/container runs deterministic before a volume is attached.
RUN mkdir -p /app/.data && chmod 700 /app/.data && chmod +x /app/start.sh

ENV PORT=8080 \
    MALO_DATA_DIR=/app/.data
EXPOSE 8080
CMD ["./start.sh"]
