#!/usr/bin/env bash
# Build the DirtyFrag (CVE-2026-43284) engine from source into jniLibs.
#
# Faithful port of diabl0w/DFRoot: the engine is two arm64 PIE executables —
# libdfroot.so (the xfrm-ESP page-cache write chain, which also .incbin's the 8
# dfroot-*.ko blobs and the splicehelper) and libbootstrap.so (the post-root
# stage that sets partitions read-only and late-loads ksud). Both are named
# lib*.so so AGP packages them into nativeLibraryDir, which is exec-able (the
# app data dir is not). Compiled with the NDK clang directly, exactly like
# manager/ghostlock/build_payload.sh. Called by gradle preBuild (via bash).
#
# The 8 dfroot-*.ko blobs under jni/ko/ are built by the DDK CI
# (build-dfroot-ko.yml) and are not committed; a local build without them skips
# cleanly instead of hard-failing (mirrors build_extract.sh's cargo skip).
set -euo pipefail

NDK_VERSION="${NDK_VERSION:-29.0.14206865}"
API="35"
HERE="$(cd "$(dirname "$0")" && pwd)"     # manager/dirtyfrag/
ROOT="$(cd "$HERE/.." && pwd)"           # manager/
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
JNI="$HERE/jni"

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
  *MINGW*|*MSYS*|*CYGWIN*) HOST="windows-x86_64"; CLANG="clang.exe"; STRIP="llvm-strip.exe" ;;
  *)                       HOST="linux-x86_64";  CLANG="clang";     STRIP="llvm-strip" ;;
esac
BIN="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST/bin"
CLANG_PATH="$BIN/$CLANG"
STRIP_PATH="$BIN/$STRIP"
[ -x "$CLANG_PATH" ] || { echo "clang not found: $CLANG_PATH"; exit 1; }
[ -x "$STRIP_PATH" ] || { echo "llvm-strip not found: $STRIP_PATH"; exit 1; }

TARGET="aarch64-linux-android$API"

# exp.c .incbin's every ko blob, so the full set must be present (CI supplies
# them). Check the newest KMI as a sentinel for the whole directory.
if [ ! -f "$JNI/ko/dfroot-android17-6.18.ko" ]; then
  echo "==> DirtyFrag: ko blobs missing under $JNI/ko (CI-only input); skipping native build"
  exit 0
fi

mkdir -p "$OUT"

echo "==> Building DirtyFrag engine (arm64, API $API) @ $NDK_ROOT"

(
  # Compile from jni/ so exp.c's `.incbin "ko/…"` and `.incbin "splicehelper"`
  # resolve relative to the working directory (and libcxx.S's `.include
  # "include.inc"` relative to the source file).
  cd "$JNI"

  # 1. splicehelper — freestanding static ELF written into crash_dump64's page
  #    cache and exec'd in its SELinux domain. Raw syscalls only, no libc.
  "$CLANG_PATH" --target="$TARGET" \
    splicehelper.c -o splicehelper \
    -nodefaultlibs -nostartfiles -ffreestanding -static
  "$STRIP_PATH" splicehelper

  # 2. libdfroot.so — the exploit engine, a PIE executable.
  "$CLANG_PATH" --target="$TARGET" \
    -I . -Wall -Wextra -fno-stack-protector -fomit-frame-pointer \
    exp.c libcxx.S elf_parser.c \
    -o "$OUT/libdfroot.so" \
    -fPIE -pie -Wl,-z,max-page-size=16384

  # 3. libbootstrap.so — the post-root stage, a separate PIE executable.
  "$CLANG_PATH" --target="$TARGET" \
    bootstrap.c -o "$OUT/libbootstrap.so" \
    -fPIE -pie -Wl,-z,max-page-size=16384
)

echo "==> Payload ready:"
ls -la "$OUT/libdfroot.so" "$OUT/libbootstrap.so"
