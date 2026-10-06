#!/bin/sh
# Serve the APK download page together with the MalO payment gateway API.
# Railway supplies PORT at runtime.
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PORT="${PORT:-8080}"
PUBLIC_DIR="${PUBLIC_DIR:-$PROJECT_DIR/public}"
SERVER_DIR="$PROJECT_DIR/server"

[ -f "$PUBLIC_DIR/index.html" ] || {
  echo "[MalO start] ERROR: public/index.html is missing." >&2
  exit 1
}

if [ ! -f "$PUBLIC_DIR/MalO-1.0.0.apk" ]; then
  echo "[MalO start] WARNING: APK is missing. Run ./build.sh to produce it." >&2
fi

# Preferred runtime: the Node gateway (static page + /api/* + webhooks).
if command -v node >/dev/null 2>&1 && [ -f "$SERVER_DIR/src/index.js" ]; then
  echo "[MalO start] Serving MalO distribution + payment gateway on 0.0.0.0:$PORT"
  PORT="$PORT" PUBLIC_DIR="$PUBLIC_DIR" exec node "$SERVER_DIR/src/index.js"
fi

# Fallback: static-only hosting.
if command -v python3 >/dev/null 2>&1; then
  echo "[MalO start] Node not found — serving static files only on 0.0.0.0:$PORT"
  cd "$PUBLIC_DIR"
  exec python3 -m http.server "$PORT" --bind 0.0.0.0
fi

echo "[MalO start] ERROR: neither node nor python3 is available." >&2
exit 1
