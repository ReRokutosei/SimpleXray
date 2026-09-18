#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

echo "=== Building MipsTUN bridge for Android (arm64-v8a) ==="

# Find Android NDK
NDK_ROOT="${ANDROID_NDK_HOME:-${NDK_HOME:-}}"
if [[ -z "${NDK_ROOT}" ]]; then
    if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}/ndk" ]]; then
        NDK_ROOT="$(find "${ANDROID_HOME}/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
    elif [[ -d "/home/vanitas/Android/Sdk/ndk" ]]; then
        NDK_ROOT="$(find "/home/vanitas/Android/Sdk/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
    fi
fi

if [[ -z "${NDK_ROOT}" || ! -d "${NDK_ROOT}" ]]; then
    echo "ERROR: Android NDK not found. Please set ANDROID_NDK_HOME." >&2
    exit 1
fi

echo "Using NDK: ${NDK_ROOT}"

TOOLCHAIN="${NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin"
CC_BIN="$(find "${TOOLCHAIN}" -name "aarch64-linux-android34-clang" -o -name "aarch64-linux-android*-clang" | head -n 1)"

if [[ -z "${CC_BIN}" || ! -x "${CC_BIN}" ]]; then
    echo "ERROR: aarch64 clang compiler not found in ${TOOLCHAIN}" >&2
    exit 1
fi

echo "Using Compiler: ${CC_BIN}"

cd "${SCRIPT_DIR}"

export CGO_ENABLED=1
export GOOS=android
export GOARCH=arm64
export CC="${CC_BIN}"

OUTPUT_SO="${SCRIPT_DIR}/libmipstun.so"
DEST_DIR="${ROOT_DIR}/app/src/main/jniLibs/arm64-v8a"

go build -buildmode=c-shared \
    -o "${OUTPUT_SO}" \
    -trimpath \
    -buildvcs=false \
    -ldflags="-s -w -extldflags=-Wl,-z,max-page-size=16384" \
    .

mkdir -p "${DEST_DIR}"
cp -f "${OUTPUT_SO}" "${DEST_DIR}/libmipstun.so"

echo "=== Successfully built and installed: ${DEST_DIR}/libmipstun.so ==="
