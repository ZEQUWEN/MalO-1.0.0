#!/bin/sh
set -e

PORT="${PORT:-8080}"
echo "[MalO-1.0.0] Starting MalO Web Distribution Server on port $PORT..."

mkdir -p public

# Ensure APK files exist in public directory
if [ -f ".build-outputs/app-debug.apk" ] && [ ! -f "public/MalO-1.0.0.apk" ]; then
    cp .build-outputs/app-debug.apk public/MalO-1.0.0.apk
    cp .build-outputs/app-debug.apk public/app-debug.apk
fi

cd public
exec python3 -m http.server "$PORT"
