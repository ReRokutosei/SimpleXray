#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ZEPTUN="$ROOT/zeptun"
NDK=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}

if [ -z "$NDK" ]; then
    echo "ANDROID_NDK_HOME or ANDROID_NDK_ROOT is required" >&2
    exit 1
fi

cd "$ZEPTUN"
exec zig build android -Doptimize=ReleaseSmall
