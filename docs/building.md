# Building from Source

**English** | **[中文](./building_CN.md)**

This document covers toolchain requirements, native dependency builds, local APK builds, signing, tests, and CI releases.

## 1. Requirements

- Linux build host.
- Android 14+ device for runtime testing; only `arm64-v8a` is supported.
- Android SDK with `compileSdk 37`, Build Tools, and Platform SDK for that API level. `minSdk` is 34, `targetSdk` is 36.
- Android NDK, pinned by `NDK_VERSION` in [`version.properties`](../version.properties). Set `ANDROID_NDK_HOME` or `NDK_HOME`.
- Zig. The exact version is pinned by the CI workflows; see [`.github/workflows/verify.yml`](../.github/workflows/verify.yml).
- CMake 3.22.1 or newer.
- JDK 25 for Gradle; Kotlin/Java bytecode targets remain 21.
- Go, pinned by `GO_VERSION` in `version.properties`.
- Git with submodule support.
- Python 3 for the tooling unit tests.

The Gradle Wrapper supplies the Gradle version. Android Gradle Plugin, library versions, and the remaining dependency pins live in the Gradle version catalog.

## 2. Clone the Repository

```bash
git clone --recursive https://github.com/ReRokutosei/SimpleXray.git
cd SimpleXray
```

For an existing clone without submodules:

```bash
git submodule update --init --recursive
```

## 3. Prepare Geo Assets

The `geoip.dat` and `geosite.dat` assets are not tracked. For a local debug build, place the current files under `app/src/main/assets/`:

```bash
mkdir -p app/src/main/assets
wget https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.dat -O app/src/main/assets/geoip.dat
wget https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat -O app/src/main/assets/geosite.dat
```

Release builds verify the hashes recorded as `GEOIP_SHA256` and `GEOSITE_SHA256` in `version.properties`. Use `-PnoGeo=true` to build from `app/src/main/assets-no-geo/` instead.

## 4. Build Native Dependencies

### Hev

`hev-socks5-tunnel` is compiled by the Gradle CMake configure step from `third_party/hev-socks5-tunnel/`; no manual step is required.

### SimpleTUN

The CMake configure step imports a prebuilt Zig archive and fails fast if it is missing:

```bash
(cd third_party/simpletun && zig build android && zig build test)
```

Output: `third_party/simpletun/zig-out/android/prebuilt/arm64-v8a/libsimpletun.a`.

### SingTUN

SingTUN is built as a Go shared library and placed in the JNI library directory:

```bash
ANDROID_NDK_HOME="$ANDROID_HOME/ndk/<ndk-version>" bash third_party/sing-tun/build.sh
```

Output: `app/src/main/jniLibs/arm64-v8a/libsingtun.so`.

### Xray-core

Gradle's `ensureXrayCore` task downloads the official `Xray-android-arm64-v8a.zip` release matching `XRAY_CORE_VERSION`, verifies `XRAY_CORE_ZIP_SHA256`, and installs the executable as `app/src/main/jniLibs/arm64-v8a/libxray.so` before `preBuild`. A network connection is required on the first build, or when the pinned version or hash changes. Manual Xray cross-compilation is not required.

## 5. Local Builds

### Debug

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/simplexray-arm64-v8a.apk`.

### Release

The release variant enables R8, resource shrinking, and V3/V4 APK signing. Create `store.properties` in the repository root, or provide the equivalent environment variables:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Environment overrides: `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

```bash
./gradlew assembleRelease -x lint -PappVerName=vX.Y.Z
```

Output: `app/build/outputs/apk/release/simplexray-arm64-v8a.apk`.

Without a valid release signing configuration, Gradle falls back to debug signing; such an APK is not suitable for distribution.

A no-GEO release builds from `app/src/main/assets-no-geo/`:

```bash
./gradlew clean assembleRelease -x lint -PnoGeo=true -PappVerName=vX.Y.Z
```

## 6. Verification

```bash
./gradlew :app:testDebugUnitTest
(cd third_party/simpletun && zig build test)
python3 -m unittest discover -s tools/tests -v
python3 tools/sync_versions.py --check
```

## 7. CI and Releases

The `verify.yml` workflow runs version-pin checks, Go formatting checks, SimpleTUN build/tests, and `testDebugUnitTest assembleDebug` on pushes to `main`/`dev` and on pull requests.

The `release.yml` workflow triggers only on semver-like tags matching `v*`. It validates the tag, verifies the pinned Xray release commit and archive hash, verifies the Geo asset hashes, builds SingTUN and SimpleTUN, runs unit tests, signs both the normal and no-GEO APKs, and creates a draft GitHub release. Configure these repository secrets before using it:

- `SIGNING_KEY`: Base64-encoded JKS keystore.
- `KEY_STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Kernel and rule-file upgrades are controlled by `version.properties`: update `XRAY_CORE_VERSION`, `XRAY_CORE_COMMIT`, `XRAY_CORE_ZIP_SHA256`, and the Geo hashes as needed, then run `python3 tools/sync_versions.py --check`.

---

[Back to project README](./README.md)
