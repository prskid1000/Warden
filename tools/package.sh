#!/usr/bin/env bash
# Reproducible artifact build: signed APKs + the layer-B `su` shim.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?set ANDROID_HOME}"
NDK="${NDK:-$ANDROID_HOME/ndk/27.1.12297006}"
HOST=$(ls "$NDK/toolchains/llvm/prebuilt" | head -1)
TC="$NDK/toolchains/llvm/prebuilt/$HOST/bin"

./gradlew :app:assembleRelease :app:assembleDebug
"$TC/aarch64-linux-android26-clang" -O2 sushim/su.c -o sushim/su-arm64

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/warden-release.apk
echo "artifacts in dist/"
