# Differences from Upstream

**English** | **[中文](./upstream-differences_CN.md)**

This document compares the current fork against the frozen upstream snapshot `4c78901`. It describes architecture and behavior only; version pins are maintained in [`version.properties`](../version.properties), [`gradle/libs.versions.toml`](../gradle/libs.versions.toml), [`gradle/wrapper/gradle-wrapper.properties`](../gradle/wrapper/gradle-wrapper.properties), and [`.github/workflows/verify.yml`](../.github/workflows/verify.yml).

| Area | Upstream snapshot (`4c78901`) | Personal Fork |
|-|-|-|
| **Process & Configuration Delivery** | Separate Xray subprocess receives its configuration through stdin | Single-process Android app layer (UI + `VpnService`) drives an independent Xray child process over stdin; native Xray TUN uses the JNI launcher, while Hev/SingTUN/SimpleTUN use `ProcessBuilder` and forward traffic through their own SOCKS5 path. APK is `arm64-v8a` only |
| **Traffic & IPC** | `hev-socks5-tunnel` reads the Android VPN fd and forwards to Xray's local SOCKS5 inbound; statistics use a dynamic loopback gRPC port | Data plane is selected in Settings among Xray TUN, Hev, SingTUN, and SimpleTUN; service state and logs use in-memory `VpnStateHub`, while statistics use a dynamic `127.0.0.1` gRPC port |
| **Configuration Import** | JSON configurations, `vless://` links, and `simplexray://config/` links | Full JSON and YAML configurations imported through the Storage Access Framework (SAF) or clipboard; share links are not supported |
| **Rule Files** | Embedded `geoip.dat` and `geosite.dat` files, with local replacement and URL updates for these two files | Retains the standard rule-file management and adds arbitrary custom `.dat` files, `ext:` file references, per-file update URLs, validation, and background updates |
| **Configuration Sanitization** | JSON formatting with removal of `log.access` and `log.error` | SnakeYAML-based parsing with a one-way Android compatibility pipeline that modifies inbounds, routing rules, DNS bootstrap hosts, logging, and selected outbound settings |
| **Build System** | Legacy `ndkBuild` (`Android.mk`) and standard Gradle configuration | CMake (`CMakeLists.txt`) with Android 16 KB page-alignment linker options, Version Catalogs, and Plugins DSL. Toolchain and dependency versions are not duplicated here |
| **UI & Layout** | Standard Material 3 UI | Xiaomi HyperOS / MIUI-inspired UI implemented with `compose-miuix-ui`, with adaptive layouts for phones and large screens, NavigationRail support, and Android 12+ dynamic colors |
| **Persistence & Communication** | ContentProvider-backed `SharedPreferences` and `Gson` | Direct lightweight `SharedPreferences` with `kotlinx.serialization`; UI and background service communicate reactively via in-memory `StateFlow` / `SharedFlow` |
| **Core Components** | Xray-core launcher with `hev-socks5-tunnel` as the only TUN backend | Xray-core launcher plus selectable Hev (C/lwIP), SingTUN (Go), SimpleTUN (in-tree Zig), and native Xray TUN backends; external component revisions are pinned in `version.properties` |
| **ABI Packaging** | `arm64-v8a` and `x86_64` split APKs, plus a universal APK | `arm64-v8a` APK only |
| **TUN Backend Setting** | No Xray TUN backend setting | `Xray TUN`, `SingTUN`, `SimpleTUN`, and `Hev Socks5 Tunnel` selector, defaulting to `Hev Socks5 Tunnel` |

---

[Back to project README](./README.md)
