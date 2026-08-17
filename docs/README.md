# SimpleXray (Personal Fork)


<div align="center">
<img src="images/lineal.svg" alt="SimpleXray icon" width="150">

**English** | **[中文](./README_CN.md)**

<img src="https://app.fossa.com/api/projects/git%2Bgithub.com%2FReRokutosei%2FSimpleXray.svg?type=shield" alt="FOSSA Status" width="150">

</div>

SimpleXray is an Android proxy client built on [Xray-core](https://github.com/XTLS/Xray-core), Android `VpnService`, and `hev-socks5-tunnel`. The packaged Xray-core executable, `libxray.so`, runs as a separate child process. In Hev mode, Xray is started with `ProcessBuilder`; native Xray TUN mode uses a small JNI launcher to pass the VPN file descriptor to the child process.

## Scope

SimpleXray is primarily an Android frontend and launcher for Xray-core. It accepts complete Xray-core configuration files in JSON or YAML format and executes the resulting configuration on Android.

The application does not parse or generate configurations from share links or subscription URIs such as `vless://`, `vmess://`, or `trojan://`. Users are expected to have a basic understanding of Xray-core configuration files.

Imported configurations may be processed before execution to accommodate Android-specific requirements. This includes removing or modifying configuration elements that are specific to desktop or root environments.

This repository is a personal fork based on the upstream [SimpleXray](https://github.com/lhear/SimpleXray) project.

## UI Preview

### Mobile

<div align="center">
  <img src="./images/mobile_01.webp" alt="Mobile UI 1" width="48%">
  <img src="./images/mobile_02.webp" alt="Mobile UI 2" width="48%">
  <br>
  <img src="./images/mobile_03.webp" alt="Mobile UI 3" width="48%">
  <img src="./images/mobile_04.webp" alt="Mobile UI 4" width="48%">
</div>

### Tablet

<div align="center">
  <img src="./images/table_01.webp" alt="Tablet UI 1" width="48%">
  <img src="./images/table_02.webp" alt="Tablet UI 2" width="48%">
  <br>
  <img src="./images/table_03.webp" alt="Tablet UI 3" width="48%">
  <img src="./images/table_04.webp" alt="Tablet UI 4" width="48%">
</div>

---

<details>
<summary><b>Click to expand / collapse: Differences from Upstream</b></summary>

| Area | Upstream (4c78901) | Personal Fork |
|-|-|-|
| **Process & Execution**         | Runs Xray in a separate child process and sends the configuration through stdin | Runs Xray in a separate child process and sends the configuration through stdin. Native Xray TUN mode starts the child through the JNI launcher; Hev mode uses `ProcessBuilder`. APK packaging includes `arm64-v8a` only |
| **Traffic & IPC**               | `hev-socks5-tunnel` reads the Android VPN file descriptor and forwards traffic to Xray through its local SOCKS5 inbound. Statistics use a dynamically allocated loopback TCP gRPC port | The selected TUN backend determines the data path. Native Xray TUN mode receives the VPN file descriptor through the JNI launcher; Hev mode forwards traffic through the local SOCKS5 inbound. Core status and traffic statistics use a dynamically allocated `127.0.0.1` TCP gRPC port |
| **Configuration Import**        | JSON configurations, `vless://` links, and `simplexray://config/` links | Full JSON and YAML configurations imported through the Storage Access Framework (SAF) or clipboard; share links are not supported |
| **Rule Files**                  | Embedded `geoip.dat` and `geosite.dat` files, with local replacement and URL updates for these two files | Retains the standard rule-file management and adds arbitrary custom `.dat` files, `ext:` file references, per-file update URLs, validation, and background updates |
| **Configuration Sanitization**  | JSON formatting with removal of `log.access` and `log.error` | SnakeYAML-based parsing with a one-way Android compatibility pipeline that modifies inbounds, routing rules, DNS bootstrap hosts, logging, and selected outbound settings |
| **Build System**                | Legacy `ndkBuild` (`Android.mk`) and standard Gradle configuration                | CMake (`CMakeLists.txt`); the native tunnel target includes Android 16 KB page-alignment linker options. Gradle Wrapper `9.7.0`, Android Gradle Plugin `9.3.1`, Version Catalogs, and Plugins DSL |
| **UI & Layout**                 | Standard Material 3 UI                                                            | Xiaomi HyperOS / MIUI-inspired UI implemented with `compose-miuix-ui`, with adaptive layouts for phones and large screens, NavigationRail support, and Android 12+ dynamic colors |
| **Persistence & Serialization** | ContentProvider-backed `SharedPreferences` and `Gson`                             | The same ContentProvider-backed `SharedPreferences` with `kotlinx.serialization` for structured values and Compose `StateFlow` for UI state |
| **Core Components**             | Xray-core `v26.3.27` and `hev-socks5-tunnel` `v2.14.3`                            | Xray-core `v26.7.28` and `hev-socks5-tunnel` `v2.17.0`, including updated `hev-socks5-core`, `hev-task-system`, and `lwip` components                                             |
| **ABI Packaging**               | `arm64-v8a` and `x86_64` split APKs, plus a universal APK                                | `arm64-v8a` APK only                                                                                                                                                |
| **TUN Backend Setting**         | No Xray TUN backend setting                                      | `Xray TUN` and `Hev Socks5 Tunnel` selector, defaulting to `Xray TUN`                                                                                              |

</details>

## Features and Modifications

### 1. UI and Adaptive Layout

The user interface has been refactored around `compose-miuix-ui`, using a design language inspired by Xiaomi HyperOS / MIUI.

* **Miuix components**: Uses Miuix components and shapes, including `TopAppBar`, `Card`, `InputField`, `Checkbox`, `OverlayIconDropdownMenu`, `OverlayDialog`, and `OverlayBottomSheet`.
* **Theme support**: Provides Light, Dark, and Automatic theme modes, together with Android 12+ Monet dynamic colors integrated with the Miuix color scheme.
* **Adaptive navigation**: Uses a vertical Miuix `NavigationRail` on wide screens when `screenWidthDp >= 600dp`.
* **Master-detail layout**: Uses a dual-pane layout for `ConfigScreen` on larger landscape displays when `screenWidthDp >= 840dp`, allowing profile selection and configuration editing to be displayed side by side.
* **Editor layout**: Provides a fullscreen editor mode. Dashboard, Settings, and App-Based Proxy content use a maximum width of `840dp` on wide screens.
* **Edge-to-edge navigation**: Uses a floating navigation bar that allows page content to scroll beneath the translucent navigation surface.
* **Per-app proxy filtering**: Adds an option in the App-Based Proxy screen to show or hide applications that do not declare `android.permission.INTERNET`.
* **Direct controls**: Provides direct controls for log search, log export, log clearing, and dashboard latency refresh. Configuration import remains available from the configuration screen.

### 2. Xray-core Configuration Import

SimpleXray accepts complete Xray-core configuration files rather than individual proxy nodes or share links.

Supported input formats include:

* JSON configuration files;
* YAML configuration files;
* `.json`, `.yaml`, and `.yml` files imported through the Android Storage Access Framework (SAF);
* Configuration text pasted from the clipboard.

Share links and subscription URIs, such as `vless://`, `vmess://`, and `trojan://`, are not supported.

YAML configurations are parsed into structured data, processed by the configuration sanitization pipeline, and serialized before being passed to Xray-core.

The application also provides an in-app configuration editor with text editing, search, and bracket matching.

### 3. Rule File Management

SimpleXray retains the embedded `geoip.dat` and `geosite.dat` rule files and provides additional local and remote management capabilities.

* **Local replacement**: Import local `geoip.dat` and `geosite.dat` files from device storage.
* **Custom rule files**: Import and manage arbitrary non-standard `.dat` files.
* **Custom tags**: Support custom rule references such as `ext:custom.dat:subcategory`.
* **Per-file update URLs**: Configure individual update URLs for non-standard `.dat` files.
* **Background updates**: Download configured custom rule files in the background.

### 4. Process Management and IPC

SimpleXray uses separate channels for configuration, VPN traffic, process logs, and statistics queries.

* **Configuration**: Generated JSON is written to Xray-core through stdin; no intermediate configuration file is required.
* **Native TUN mode**: The JNI launcher passes the Android `VpnService` file descriptor to the Xray child process, which attaches it to the TUN inbound.
* **Process logs**: Xray stdout and stderr are collected through pipes.
* **Statistics**: Core status and traffic statistics are queried through plaintext gRPC on a dynamically allocated `127.0.0.1` TCP port.
* **Hev tunnel mode**: When selected, `hev-socks5-tunnel` reads the Android VPN file descriptor and forwards traffic to Xray through its local SOCKS5 inbound.
* **Benchmark & Profiling**: For detailed throughput benchmarks and resource profiling results on Android devices, see [Android TUN Benchmark Report](./benchmark/android-tun-benchmark.md).

### 5. Routing and Core Configuration

The fork includes several configuration-level optimizations and Android-specific adjustments.

* **Hybrid domain matcher**: Changes `domainMatcher` from `mph` to `hybrid` to balance memory usage and domain lookup performance.
* **DoH bootstrap configuration**: Adds static host mappings for matching AliDNS DoH hostnames to avoid DNS bootstrap dependencies for those endpoints.
* **Listen address normalization**: Converts wildcard listen addresses such as `::` and `0.0.0.0` to `127.0.0.1` where required by the Android execution environment.
* **Dashboard latency display**: The dashboard shows the TCP handshake time to each outbound's server endpoint. Endpoints are parsed from the configuration: vless and vmess use `settings.vnext[0]`, while trojan, shadowsocks, HTTP, and SOCKS use `settings.servers[0]`. Probes run once when the dashboard is shown and can also be started manually. UDP-only protocols such as WireGuard and Hysteria2, QUIC transports, and private, loopback, or link-local IP literals are skipped. The result measures the network path from the device to the node and does not measure Xray processing time. Unreachable nodes are marked as failed.

### 6. Platform-Specific Configuration Sanitization

Complete Xray-core configuration files can be imported without requiring users to manually remove every desktop-specific setting. Before execution, the imported configuration passes through an Android-specific sanitization pipeline.

The pipeline currently handles the following cases:

* **Windows process rules**: Removes Windows-specific executable paths such as `chrome.exe` from `routing.rules` and removes rules that become invalid as a result.
* **TUN inbounds**: When VPN and Xray TUN mode are enabled, keeps a `protocol: tun` inbound, supplies an Android-compatible name, and removes desktop automatic-routing fields. In Hev mode, or when VPN is disabled, removes `protocol: tun` inbounds.
* **File-based logging**: Removes filesystem paths configured through the `access` and `error` logging fields where required to avoid Android filesystem permission errors.
* **Listen addresses**: Normalizes `::` and `0.0.0.0` to `127.0.0.1` where required by the Android execution environment.
* **Sniffing configuration**: Preserves supported sniffing-related settings, including `destOverride`, when sanitizing the configuration.

The sanitization pipeline is intentionally one-way: imported configuration data is transformed into an Android-compatible configuration before being passed to the core.

### 7. Log Level Configuration

SimpleXray provides a LogLevel preference with the following options:

* `Auto`
* `Debug`
* `Info`
* `Warning`
* `Error`
* `None`

The selected log level is applied to the imported configuration during the sanitization process. File-based `access` and `error` logging paths are removed where necessary to avoid filesystem permission issues on Android.

### 8. Build System and Dependencies

The native build system has been migrated from the legacy Android NDK build system to CMake.

* **CMake**: Uses `CMakeLists.txt` instead of `Android.mk` / `ndkBuild`.
* **Android 16 KB page alignment**: The native build configuration includes support for Android devices using 16 KB memory page sizes.
* **Gradle Wrapper**: Updated to `v9.7.0`.
* **Android Gradle Plugin**: Uses `v9.3.1`.
* **Version Catalogs**: Project dependencies are managed through Gradle Version Catalogs.
* **Plugins DSL**: Gradle plugins are configured through the Plugins DSL.
* **Serialization**: `Gson` has been replaced with `kotlinx.serialization` for type-safe serialization and deserialization.

---

## Requirements

The following environment is required to build the project:

* Android 10 (API level 29) or later.
* Android SDK with Build Tools and Platform SDK for the configured target SDK (`36`).
* Android NDK.
* CMake.
* JDK 21.
* Git with submodule support.

The project uses Gradle Wrapper, so the required Gradle version is obtained automatically from the repository's Gradle Wrapper configuration.

---

## Building from Source

Clone the repository and its submodules:

```bash
git clone --recursive https://github.com/ReRokutosei/SimpleXray.git
cd SimpleXray
```

Build the release APK:

```bash
./gradlew assembleRelease
```

The generated APK is located at:

```text
app/build/outputs/apk/release/simplexray-arm64-v8a.apk
```

If the repository has already been cloned without its submodules, initialize them with:

```bash
git submodule update --init --recursive
```

---

## Upstream Projects and Dependencies

SimpleXray incorporates or builds upon the following open-source projects:

* [**`compose-miuix-ui`**](https://github.com/compose-miuix-ui/miuix) — A Jetpack Compose UI component library for Kotlin Multiplatform, inspired by Xiaomi HyperOS / MIUI.
* [**`Xray-core`**](https://github.com/XTLS/Xray-core) — The proxy and network core used by SimpleXray.
* [**`SimpleXray`**](https://github.com/lhear/SimpleXray) — The upstream Android client on which this fork is based.
* [**`hev-socks5-tunnel`**](https://github.com/heiher/hev-socks5-tunnel) — A SOCKS5 VPN tunnel implementation used for handling Android network traffic.

### Acknowledgements

This project uses free icons provided by Magnific. We would like to express our gratitude for the original creator's work:

* [Cookie Icons (Special Lineal, Flat, Lineal Color)](https://www.magnific.com/icon/cookie_1047813) — Designed by [Magnific](https://www.magnific.com)

---

## Privacy Policy and Disclaimer

For details, please refer to the [Privacy Policy](./PrivacyPolicy_EN.md) and [Disclaimer](./Disclaimer_EN.md).

By using this application, you acknowledge that you have read and agree to the Privacy Policy and Disclaimer. If you do not agree with either document, please uninstall the application and discontinue its use.

---

## License

Unless otherwise stated, this project is distributed under the Mozilla Public License 2.0 (MPL-2.0), in accordance with the licensing terms of the upstream project.

See [`LICENSE`](../LICENSE) for the complete license text.

<div align="center">

<img src="https://app.fossa.com/api/projects/git%2Bgithub.com%2FReRokutosei%2FSimpleXray.svg?type=large" alt="FOSSA Status"  width="300">

</div>
