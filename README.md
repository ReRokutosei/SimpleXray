# SimpleXray (Personal Fork)

<img src="https://raw.githubusercontent.com/lhear/SimpleXray/main/metadata/en-US/images/icon.png" alt="icon" width="150">

SimpleXray is a lightweight proxy client for Android, built upon **Xray-core** ([@XTLS/Xray-core](https://github.com/XTLS/Xray-core)) and Android `VpnService` / `hev-socks5-tunnel`. It isolates core proxy logic from the Android application layer by directly executing the native Xray-core binary (`libxray.so`) as a child process via `ProcessBuilder`.

This repository is a personal fork based on upstream [SimpleXray](https://github.com/lhear/SimpleXray).

---

## Architectural Comparison with Upstream

| Dimension                       | Upstream Implementation (`4c78901`)                         | Personal Fork                                                                                                                                                                              |
| :------------------------------ | :---------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Process**                     | Custom Native C JNI (`nativeSpawnXray`)                     | Refactored to standard `ProcessBuilder` streaming JSON configs via STDIN pipe; utilizes Unix Domain Sockets (UDS) / loopback gRPC for statistics and IPC control                           |
| **Config &**                    | Single-format JSON manual import                            | Supports JSON and **YAML** formats via Storage Access Framework (SAF), with in-app searchable config editor                                                                                |
| **Dat Rule**                    | Embedded `geoip.dat` / `geosite.dat`                        | Retains embedded dat files; adds local file import/replacement and expands support for arbitrary non-standard custom `.dat` files (e.g., `ext:custom.dat:tag`) with individual update URLs |
| **Config**                      | Basic regex/removal logic for inbounds                      | SnakeYAML-driven AST semantic parser with one-way data pipeline; strips desktop `protocol: tun` and file write paths while preserving sniffing (`destOverride`)                            |
| **Build System & NDK**          | Legacy `ndkBuild` (`Android.mk`) and standard Gradle script | Modern CMake (`CMakeLists.txt`) supporting Android 16KB page alignment; Gradle 9.7.0 with Version Catalogs (TOML) and Plugins DSL                                                          |
| **Persistence & Serialization** | Standard `SharedPreferences` + `Gson`                       | Provider-backed `Preferences` + `kotlinx.serialization` with Compose `StateFlow` state management                                                                                          |
| **Core Components**             | Xray-core v26.3.27 & `hev-socks5-tunnel` v2.14.3            | Xray-core updated to `v26.7.28`; `hev-socks5-tunnel` updated to `v2.17.0` (with updated `hev-socks5-core`, `hev-task-system`, and `lwip`)                                                  |

---

## Modifications Summary

The following structural and technical modifications have been applied in this fork:

### 1. Multi-Format Configuration & SAF Import
- **SAF & File Import**: Supports importing configurations directly from Android file system via Storage Access Framework (SAF).
- **YAML Format Support**: Full support for both JSON and YAML configuration files, automatically parsed and converted during AST sanitization.
- **In-App Config Editor**: Built-in editor supporting syntax display, real-time search, and editing for imported profiles.

### 2. Custom Rule File (`.dat`) Management & Arbitrary Dat Support
- **Local File Import**: Adds support for importing local `geoip.dat` and `geosite.dat` files directly from device storage to replace embedded defaults.
- **Arbitrary Non-Standard Dat Support**: Supports importing and managing arbitrary non-standard `.dat` rule files (e.g., `custom.dat`), enabling routing rules with custom tags such as `ext:custom.dat:subcategory`.
- **Dedicated Update URLs for Custom Dats**: Allows configuring individual online update URLs for non-standard custom `.dat` files with background downloading.

### 3. Process Management & IPC Modernization
- **STDIN Config Streaming**: Refactored process execution to pass memory JSON configurations via STDIN stream, eliminating file disk IO and preventing temporary sensitive config file leaks.
- **IPC & Statistics**: Leverages Unix Domain Sockets (UDS) and loopback gRPC for core status and real-time bandwidth statistics.

### 4. Routing & Core Optimization
- **Hybrid Domain Matcher**: Upgrades `domainMatcher` from `mph` to `hybrid` to optimize memory usage and lookup speed.
- **DoH Static Host Mapping**: Injects static IP hosts for DoH providers to prevent recursive DNS lookup deadlocks.

### 5. AST Config Sanitization & Inbound Safety
- **Desktop TUN Inbound Filtering**: Implemented AST config sanitizer to automatically filter out desktop-only `protocol: tun` inbounds when running on non-root Android environments.
- **Listen Address Convergence**: Automatically converts bind addresses `::` and `0.0.0.0` to `127.0.0.1` for non-root socket compliance.
- **Rule Preservation**: Preserves user-defined `socks` / `http` inbounds and full traffic sniffing settings (`sniffing` / `destOverride`).

### 6. Configurable LogLevel
- Added LogLevel preference setting (`Auto`, `Debug`, `Info`, `Warning`, `Error`, `None`) with UI selection and single-direction AST injection. Automatically strips file paths `access` and `error` to prevent filesystem permission errors on Android.

### 7. Core Component & Submodule Upgrades
- **Xray-core Kernel**: Updated embedded Xray-core binary to `v26.7.28`.
- **hev-socks5-tunnel Submodule**: Updated `hev-socks5-tunnel` from `2.14.3` to `2.17.0`, along with inner submodules (`hev-socks5-core`, `hev-task-system`, `lwip`).

### 8. Build System & Dependency Modernization
- **Gradle & Catalogs**: Upgraded Gradle to `v9.7.0`, migrated project build configuration to Gradle Version Catalogs and Plugins DSL.
- **CMake & 16KB Page Alignment**: Migrated NDK build from legacy `ndkBuild` (`Android.mk`) to CMake (`CMakeLists.txt`) with Android 16KB page alignment support.
- **Preference & Serializer Architecture**: Replaced Gson with `kotlinx.serialization` for type-safe JSON handling.

---

## Quick Start & Build Guide

### Prerequisites
- Android 10 (API Level 29) or higher;
- Android SDK Build Tools (Target SDK 36), NDK, and CMake;
- JDK 21.

### Building from Source
```bash
git clone --recursive https://github.com/ReRokutosei/SimpleXray.git
cd SimpleXray
./gradlew assembleRelease
```

The compiled APK will be generated at `app/build/outputs/apk/release/simplexray-universal.apk`.

---

## License

This project retains the original license and is licensed under the **[Mozilla Public License Version 2.0 (MPL 2.0)](LICENSE)**.
