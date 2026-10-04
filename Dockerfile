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
    && sdkmanager "platform-tools" "platforms;android-36.1" "build-tools;36.0.0"

WORKDIR /workspace
COPY . .
RUN chmod +x build.sh start.sh && ./build.sh

FROM python:3.12-alpine AS runtime

WORKDIR /app
COPY --from=builder /workspace/public /app/public
COPY start.sh /app/start.sh

RUN chmod +x /app/start.sh

ENV PORT=8080
EXPOSE 8080
CMD ["./start.sh"]
