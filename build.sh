#!/bin/sh
# Build a debug APK and place it in public/ for the Railway download service.
# This project intentionally does not require a globally installed Gradle.
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$PROJECT_DIR"

GRADLE_VERSION="9.3.1"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PROJECT_DIR/.gradle}"
export GRADLE_USER_HOME

DISTRIBUTION_DIR="$PROJECT_DIR/public"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

fail() {
  echo "[MalO build] ERROR: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command '$1' was not found."
}

# The project uses AGP 9.1.x with Java 17 and Android SDK platform 36.1.
require_command java
require_command curl
require_command unzip
require_command sha256sum

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ] && [ ! -f "$PROJECT_DIR/local.properties" ]; then
  fail "Android SDK was not found. Set ANDROID_HOME (or ANDROID_SDK_ROOT), or add sdk.dir to local.properties."
fi

# A Gradle wrapper was not included in the original Android Studio export. Bootstrap
# the exact Gradle version required by AGP, cache it in .gradle, and verify its hash.
GRADLE_HOME="$GRADLE_USER_HOME/bootstrap/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  echo "[MalO build] Downloading Gradle $GRADLE_VERSION..."
  require_command mktemp

  TEMP_DIR=$(mktemp -d)
  trap 'rm -rf "$TEMP_DIR"' EXIT HUP INT TERM
  ARCHIVE="$TEMP_DIR/gradle-$GRADLE_VERSION-bin.zip"
  CHECKSUM_FILE="$ARCHIVE.sha256"
  DIST_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

  curl --fail --location --retry 3 --silent --show-error "$DIST_URL" --output "$ARCHIVE"
  curl --fail --location --retry 3 --silent --show-error "$DIST_URL.sha256" --output "$CHECKSUM_FILE"

  EXPECTED_SHA256=$(awk 'NR == 1 { print $1 }' "$CHECKSUM_FILE")
  [ "${#EXPECTED_SHA256}" -eq 64 ] || fail "Unexpected checksum format for Gradle $GRADLE_VERSION."
  printf '%s  %s\n' "$EXPECTED_SHA256" "$ARCHIVE" | sha256sum --check --status || \
    fail "Gradle archive checksum verification failed."

  rm -rf "$GRADLE_HOME"
  mkdir -p "$(dirname "$GRADLE_HOME")"
  unzip -q "$ARCHIVE" -d "$(dirname "$GRADLE_HOME")"
fi

[ -x "$GRADLE_BIN" ] || fail "Gradle bootstrap did not produce an executable."

echo "[MalO build] Building debug APK with Gradle $GRADLE_VERSION..."
"$GRADLE_BIN" --no-daemon --console=plain :app:assembleDebug

[ -f "$APK_PATH" ] || fail "Gradle completed, but the expected APK was not created: $APK_PATH"
unzip -t "$APK_PATH" >/dev/null || fail "Gradle output is not a valid APK archive: $APK_PATH"

mkdir -p "$DISTRIBUTION_DIR"
cp "$APK_PATH" "$DISTRIBUTION_DIR/MalO-1.0.0.apk"
# Keep the existing mirror URL on the download page working.
cp "$APK_PATH" "$DISTRIBUTION_DIR/app-debug.apk"

echo "[MalO build] APK published to public/MalO-1.0.0.apk"
