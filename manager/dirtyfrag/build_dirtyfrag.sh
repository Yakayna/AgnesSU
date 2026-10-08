#!/usr/bin/env bash
# Bundle the DirtyFrag (CVE-2026-43284) engine into a jniLib.
#
# libexp.so is the repacked DFRoot fast-channel engine (package remapped from
# rmi.x -> com.agnessu.yakayn, and the staged ksud path remapped from
# /data/user_de/0/rmi.x/ksud -> /data/user_de/0/com.agnessu.yakayn/ksud inside
# all seven embedded dirtyfrag_ko_* kernel-module blobs). Unlike the Samsung
# helper it is committed (the exploit itself is safe to vendor per the project
# rules; only secrets are excluded), and jniLibs/ remains gitignored — so this
# script simply copies the vendored binary into place at build time. It runs on
# Windows Git Bash exactly like manager/ghostlock/build_*.sh.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"   # manager/dirtyfrag/
ROOT="$(cd "$HERE/.." && pwd)"          # manager/
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
SRC="$HERE/libexp.so"
DEST="$OUT/libexp.so"

mkdir -p "$OUT"

# Skip when the staged copy already matches the vendored source, so incremental
# builds don't rewrite the file every assemble.
if [ -f "$DEST" ] && cmp -s "$SRC" "$DEST"; then
  echo "==> DirtyFrag engine already present: $DEST"
  exit 0
fi

cp -f "$SRC" "$DEST"
echo "==> DirtyFrag engine staged: $DEST"
