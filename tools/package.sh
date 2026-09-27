#!/usr/bin/env bash
# Reproducible artifact build: APKs, su shim, Zygisk .so, Magisk module zip.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?set ANDROID_HOME}"
NDK="${NDK:-$ANDROID_HOME/ndk/27.1.12297006}"
HOST=$(ls "$NDK/toolchains/llvm/prebuilt" | head -1)
TC="$NDK/toolchains/llvm/prebuilt/$HOST/bin"

./gradlew :app:assembleRelease :app:assembleDebug
"$TC/aarch64-linux-android26-clang" -O2 sushim/su.c -o sushim/su-arm64
( cd module && "$NDK/ndk-build" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=jni/Android.mk NDK_APPLICATION_MK=jni/Application.mk )

rm -rf build/magisk && mkdir -p build/magisk/zygisk build/magisk/warden dist
cp module/module.prop module/customize.sh build/magisk/
cp module/libs/arm64-v8a/libwarden.so build/magisk/zygisk/arm64-v8a.so
cp sushim/su-arm64 build/magisk/warden/su
( cd build/magisk && zip -r -X ../../dist/warden-magisk-module.zip . )
cp app/build/outputs/apk/release/app-release.apk dist/warden-release.apk
echo "artifacts in dist/"
