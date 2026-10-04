#!/bin/sh
# Serve the already-built APK and download page. Railway supplies PORT at runtime.
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PORT="${PORT:-8080}"
PUBLIC_DIR="$PROJECT_DIR/public"

[ -f "$PUBLIC_DIR/index.html" ] || {
  echo "[MalO start] ERROR: public/index.html is missing." >&2
  exit 1
}

[ -f "$PUBLIC_DIR/MalO-1.0.0.apk" ] || {
  echo "[MalO start] ERROR: APK is missing. Run ./build.sh before starting the server." >&2
  exit 1
}

if ! command -v python3 >/dev/null 2>&1; then
  echo "[MalO start] ERROR: python3 is required to serve the distribution." >&2
  exit 1
fi

echo "[MalO start] Serving MalO APK distribution on 0.0.0.0:$PORT"
cd "$PUBLIC_DIR"
exec python3 -m http.server "$PORT" --bind 0.0.0.0
