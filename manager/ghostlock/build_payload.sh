#!/usr/bin/env bash
# Build the GhostLock kernel-exploit payload (arm64 PIE) into a jniLib.
# Compiles the vendored core with the NDK clang directly (no `make` needed).
# Called by gradle preBuild (via bash) and by build_local.sh.
set -euo pipefail

NDK_VERSION="29.0.14206865"
API="35"
HERE="$(cd "$(dirname "$0")" && pwd)"     # manager/ghostlock/
ROOT="$(cd "$HERE/.." && pwd)"           # manager/
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"

# Resolve NDK root: explicit env first, then ANDROID_HOME/ndk/<ver>, then local.properties sdk.dir.
NDK_ROOT="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ -z "$NDK_ROOT" ] || [ ! -d "$NDK_ROOT" ]; then
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -n "$SDK" ] && [ -d "$SDK/ndk/$NDK_VERSION" ]; then
    NDK_ROOT="$SDK/ndk/$NDK_VERSION"
  fi
fi
if [ -z "$NDK_ROOT" ] || [ ! -d "$NDK_ROOT" ]; then
  SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" 2>/dev/null | tr -d '\r' | head -1)"
  if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR/ndk/$NDK_VERSION" ]; then
    NDK_ROOT="$SDK_DIR/ndk/$NDK_VERSION"
  fi
fi
: "${NDK_ROOT:?NDK not found — set ANDROID_HOME / ANDROID_NDK_HOME or sdk.dir in local.properties}"

case "$(uname -s)" in
  *MINGW*|*MSYS*|*CYGWIN*) HOST="windows-x86_64"; CLANG="clang.exe" ;;
  *)                       HOST="linux-x86_64";  CLANG="clang" ;;
esac
CLANG_PATH="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST/bin/$CLANG"
[ -x "$CLANG_PATH" ] || { echo "clang not found: $CLANG_PATH"; exit 1; }

cd "$HERE"
mkdir -p "$OUT"
echo "==> Building GhostLock payload (arm64, API $API) @ $NDK_ROOT"
"$CLANG_PATH" --target="aarch64-linux-android$API" \
  -O2 -flto -Wall -Wno-unused-parameter -Wno-sign-compare -Wno-unused-function \
  -Isrc/core -Isrc/kernels -DTARGET_CONFIG_H=\"target.h\" \
  -fPIE -pie -pthread -flto \
  src/core/main.c src/core/offsets_json.c src/core/util.c src/core/fops.c \
  -o "$OUT/libghostlock.so"

echo "==> Payload ready: $OUT/libghostlock.so"
ls -la "$OUT/libghostlock.so"
