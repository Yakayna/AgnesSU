#!/usr/bin/env bash
# Build the GhostLock offset extractor (YuKongA/ghostlock-app tools/extract_rs)
# for Android aarch64 into a jniLib, packaged as libextract.so. The Kotlin
# repository runs it as `libextract.so <boot.img> [--xbl-config ...] [--uefi ...]
# --format conf` to produce a flattened HOCON profile from a boot image.
#
# This is OPTIONAL at build time: if no Rust toolchain is present the script
# prints a warning and exits 0, so `preBuild` never fails a manager build on a
# machine (or CI job) without cargo. The extraction feature then degrades to a
# clean "missing native binary" message instead of shipping a broken APK.
set -euo pipefail

NDK_VERSION="${NDK_VERSION:-29.0.14206865}"
API="35"
HERE="$(cd "$(dirname "$0")" && pwd)"     # manager/ghostlock/
ROOT="$(cd "$HERE/.." && pwd)"           # manager/
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
SRC="$HERE/extract_rs"
TARGET_DIR="$ROOT/build/extract"
TARGET="aarch64-linux-android"

# --- locate cargo -----------------------------------------------------------------
cargo_bin="${CARGO:-}"
if [ -z "$cargo_bin" ] || ! command -v "$cargo_bin" >/dev/null 2>&1; then
  cargo_bin="$(command -v cargo 2>/dev/null || true)"
fi
if [ -z "$cargo_bin" ] && [ -x "$HOME/.cargo/bin/cargo" ]; then
  cargo_bin="$HOME/.cargo/bin/cargo"
fi
if [ -z "$cargo_bin" ]; then
  echo "==> ghostlock-extract SKIPPED: cargo not found (install Rust to build libextract.so)"
  exit 0
fi

# --- resolve NDK (same precedence as build_payload.sh) ----------------------------
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
  *MINGW*|*MSYS*|*CYGWIN*) HOST="windows-x86_64"; CLANG="aarch64-linux-android$API-clang.cmd"; AR="llvm-ar.exe" ;;
  *)                       HOST="linux-x86_64";  CLANG="aarch64-linux-android$API-clang"; AR="llvm-ar" ;;
esac
CLANG_PATH="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST/bin/$CLANG"
AR_PATH="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST/bin/$AR"
[ -x "$CLANG_PATH" ] || [ -f "$CLANG_PATH" ] || { echo "clang not found: $CLANG_PATH"; exit 1; }

# --- ensure the bare Rust std target is installed (skip build if unavailable) ----
if ! "$cargo_bin" build --help >/dev/null 2>&1; then
  echo "==> ghostlock-extract SKIPPED: cargo is not functional"
  exit 0
fi
if rustup target list --installed 2>/dev/null | grep -qx "$TARGET"; then
  : # target already installed
elif command -v rustup >/dev/null 2>&1; then
  echo "==> installing rust target $TARGET"
  rustup target add "$TARGET" || { echo "==> ghostlock-extract SKIPPED: could not add rust target $TARGET"; exit 0; }
else
  echo "==> ghostlock-extract SKIPPED: rust target $TARGET not installed and rustup unavailable"
  exit 0
fi

mkdir -p "$OUT" "$TARGET_DIR"
cd "$SRC"

echo "==> Building GhostLock extractor ($TARGET) @ $NDK_ROOT"
CC_aarch64_linux_android="$CLANG_PATH" \
AR_aarch64_linux_android="$AR_PATH" \
CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$CLANG_PATH" \
CARGO_TARGET_DIR="$TARGET_DIR" \
RUSTFLAGS="-C force-unwind-tables=no -C link-arg=-Wl,--icf=all" \
  "$cargo_bin" build --release --target "$TARGET"

BIN="$TARGET_DIR/$TARGET/release/ghostlock-extract"
[ -f "$BIN" ] || { echo "==> ghostlock-extract build produced no binary: $BIN"; exit 1; }
cp -f "$BIN" "$OUT/libextract.so"
echo "==> Extractor ready: $OUT/libextract.so"
ls -la "$OUT/libextract.so"
