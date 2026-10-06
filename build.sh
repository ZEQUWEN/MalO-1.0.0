#!/bin/sh
# Build a debug APK and place it in public/ for the Railway download service.
# This project intentionally does not require a globally installed Gradle.
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$PROJECT_DIR"

GRADLE_VERSION="9.3.1"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PROJECT_DIR/.gradle}"
MALO_ANDROID_SDK_UPDATE_CHECK="${MALO_ANDROID_SDK_UPDATE_CHECK:-1}"
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
require_command awk
require_command grep
require_command sed
require_command head

case "$MALO_ANDROID_SDK_UPDATE_CHECK" in
  0|1) ;;
  *) fail "MALO_ANDROID_SDK_UPDATE_CHECK must be 0 or 1." ;;
esac

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ] && [ ! -f "$PROJECT_DIR/local.properties" ]; then
  fail "Android SDK was not found. Set ANDROID_HOME (or ANDROID_SDK_ROOT), or add sdk.dir to local.properties."
fi

if [ "$MALO_ANDROID_SDK_UPDATE_CHECK" != "0" ]; then
  SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [ -z "$SDK_ROOT" ] && [ -f "$PROJECT_DIR/local.properties" ]; then
    SDK_ROOT=$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' "$PROJECT_DIR/local.properties" | head -n 1)
  fi

  SDKMANAGER="${ANDROID_SDKMANAGER:-}"
  if [ -z "$SDKMANAGER" ] && [ -n "$SDK_ROOT" ]; then
    for candidate in \
      "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
      "$SDK_ROOT/tools/bin/sdkmanager"
    do
      if [ -x "$candidate" ]; then
        SDKMANAGER="$candidate"
        break
      fi
    done
  fi
  if [ -z "$SDKMANAGER" ]; then
    SDKMANAGER=$(command -v sdkmanager || true)
  fi
  [ -n "$SDKMANAGER" ] || fail "sdkmanager was not found. Install Android command-line tools, set ANDROID_SDKMANAGER, or set MALO_ANDROID_SDK_UPDATE_CHECK=0 to skip the online check."

  echo "[MalO build] Checking for new or updatable Android SDK packages..."
  if [ -n "$SDK_ROOT" ]; then
    SDK_LIST_OUTPUT=$("$SDKMANAGER" --list --newer --sdk_root="$SDK_ROOT" 2>&1) || {
      printf '%s\n' "$SDK_LIST_OUTPUT" >&2
      fail "Could not check Android SDK updates."
    }
  else
    SDK_LIST_OUTPUT=$("$SDKMANAGER" --list --newer 2>&1) || {
      printf '%s\n' "$SDK_LIST_OUTPUT" >&2
      fail "Could not check Android SDK updates."
    }
  fi

  AVAILABLE_UPDATES=$(printf '%s\n' "$SDK_LIST_OUTPUT" | awk -F '|' '
    NF >= 3 {
      package = $1
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", package)
      if (package != "" && package != "Path" && package !~ /^-+$/) print
    }
  ')
  if printf '%s\n' "$AVAILABLE_UPDATES" | grep -q '[^[:space:]]'; then
    echo "[MalO build] New or updatable Android SDK packages:"
    printf '%s\n' "$AVAILABLE_UPDATES"
    echo "[MalO build] No packages were installed; pinned SDK versions remain unchanged."
  else
    echo "[MalO build] No new or updatable Android SDK packages were reported."
  fi
elif [ "$MALO_ANDROID_SDK_UPDATE_CHECK" = "0" ]; then
  echo "[MalO build] Skipping Android SDK update check (MALO_ANDROID_SDK_UPDATE_CHECK=0)."
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
