#!/usr/bin/env bash
# Fetch the pinned Root-My-Galaxy CVE-2026-43499 helper into a jniLib.
#
# libcve43499root.so is a prebuilt binary in BuSung-dev/Root-My-Galaxy. It is
# NOT committed into AgnesSU (manager/.gitignore excludes app/src/main/jniLibs/),
# so it is downloaded at build time from a pinned commit and verified by SHA-256
# before it is placed where the Samsung one-tap engine expects it.
#
# Lives alongside the other native jniLib build/fetch scripts in manager/ghostlock/
# even though it serves the Samsung engine (this directory is the repo's home for
# "produce binaries into app/src/main/jniLibs" steps). Called by gradle preBuild.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"   # manager/ghostlock/
ROOT="$(cd "$HERE/.." && pwd)"          # manager/
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"

REPO_COMMIT="23adf39fcd4f1be4e73184ee960a6d5399baf147"
HELPER_URL="https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy/${REPO_COMMIT}/app/src/main/jniLibs/arm64-v8a/libcve43499root.so"
HELPER_SHA256="c6b0612b6bdbd60ded964694284f7e8d81cfff2079abeaf3b8854952c2b49eec"

DEST="$OUT/libcve43499root.so"
mkdir -p "$OUT"

# Skip the download when the pinned artifact is already present and intact, so
# incremental/local builds don't re-hit the network on every assemble.
if [ -f "$DEST" ] && echo "$HELPER_SHA256  $DEST" | sha256sum -c - >/dev/null 2>&1; then
  echo "==> Samsung helper already present: $DEST"
  exit 0
fi

echo "==> Downloading Samsung helper (Root-My-Galaxy @ $REPO_COMMIT)"
tmp="${DEST}.part.$$"
trap 'rm -f "$tmp"' EXIT
curl -fsSL --retry 3 --retry-delay 2 "$HELPER_URL" -o "$tmp"
echo "$HELPER_SHA256  $tmp" | sha256sum -c - >/dev/null
mv -f "$tmp" "$DEST"
trap - EXIT

echo "==> Samsung helper ready: $DEST"
