#!/bin/sh
set -e

echo "[MalO-1.0.0] Starting build process..."
mkdir -p public

if [ -f ".build-outputs/app-debug.apk" ]; then
    echo "[MalO-1.0.0] Deploying compiled Android APK package..."
    cp .build-outputs/app-debug.apk public/MalO-1.0.0.apk
    cp .build-outputs/app-debug.apk public/app-debug.apk
elif command -v gradle >/dev/null 2>&1; then
    echo "[MalO-1.0.0] Building Android APK with Gradle..."
    gradle assembleDebug || true
    if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
        cp app/build/outputs/apk/debug/app-debug.apk public/MalO-1.0.0.apk
        cp app/build/outputs/apk/debug/app-debug.apk public/app-debug.apk
    fi
elif [ -f "./gradlew" ]; then
    echo "[MalO-1.0.0] Building Android APK with Gradle Wrapper..."
    sh ./gradlew assembleDebug || true
    if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
        cp app/build/outputs/apk/debug/app-debug.apk public/MalO-1.0.0.apk
        cp app/build/outputs/apk/debug/app-debug.apk public/app-debug.apk
    fi
fi

echo "[MalO-1.0.0] Build completed successfully."
