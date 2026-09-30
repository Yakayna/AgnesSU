#!/usr/bin/env bash
# Build the GhostLock kernel-exploit payload (arm64 PIE) into a jniLib.
# Compiles the vendored C++ core (YuKongA/ghostlock-app) with the NDK clang++
# directly (no `make` needed). Called by gradle preBuild (via bash) and by build_local.sh.
set -euo pipefail

NDK_VERSION="${NDK_VERSION:-29.0.14206865}"
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
  *MINGW*|*MSYS*|*CYGWIN*) HOST="windows-x86_64"; CLANGXX="clang++.exe" ;;
  *)                       HOST="linux-x86_64";  CLANGXX="clang++" ;;
esac
CLANGXX_PATH="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST/bin/$CLANGXX"
[ -x "$CLANGXX_PATH" ] || { echo "clang++ not found: $CLANGXX_PATH"; exit 1; }

cd "$HERE"
mkdir -p "$OUT"

# Production translation units from YuKongA/ghostlock-app (src/Makefile CXX_SRCS).
# Host-only tests under src/core/tests/ are not part of the payload.
CXX_SRCS=(
  src/core/main.cpp
  src/core/attack/ops.cpp
  src/core/profile/entry.cpp
  src/core/memory/address_space.cpp
  src/core/memory/heap_context.cpp
  src/core/race/pi_race.cpp
  src/core/route/route_controller.cpp
  src/core/route/route_middleware.cpp
  src/core/race/threads.cpp
  src/core/route/tcp_zerocopy_route.cpp
  src/core/route/select_stack_route.cpp
  src/core/route/multicast_waiter_route.cpp
  src/core/memory/payload_builder.cpp
  src/core/session/runtime_config.cpp
  src/core/profile/binary.cpp
  src/core/support/util.cpp
  src/core/session/exploit_session.cpp
  src/core/session/root_child_frontend.cpp
  src/core/session/backend/cve_2026_43499_backend.cpp
  src/core/session/handoff_probe.cpp
  src/core/session/victim_process.cpp
  src/core/session/victim_context.cpp
  src/core/support/native_resource.cpp
  src/core/support/run_state.cpp
)

echo "==> Building GhostLock payload (arm64, API $API) @ $NDK_ROOT"
"$CLANGXX_PATH" --target="aarch64-linux-android$API" \
  -O2 -flto -Wall -Wextra -Wconversion -Wsign-conversion \
  -Wno-unused-parameter -Wno-sign-compare -Wno-unused-function \
  -Isrc/core -DTARGET_CONFIG_H=\"kernel/target.h\" \
  -std=c++23 -fno-rtti \
  -fPIE -pie -pthread -flto -static-libstdc++ \
  "${CXX_SRCS[@]}" \
  -o "$OUT/libghostlock.so"

echo "==> Payload ready: $OUT/libghostlock.so"
ls -la "$OUT/libghostlock.so"
