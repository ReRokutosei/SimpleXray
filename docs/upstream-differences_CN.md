# 与上游的差异

**[English](./upstream-differences.md)** | **中文**

本文以固定的上游快照 `4c78901` 为基准。内容只覆盖架构与行为差异；版本 pin 统一维护在 [`version.properties`](../version.properties)、[`gradle/libs.versions.toml`](../gradle/libs.versions.toml)、[`gradle/wrapper/gradle-wrapper.properties`](../gradle/wrapper/gradle-wrapper.properties) 和 [`.github/workflows/verify.yml`](../.github/workflows/verify.yml) 中。

| 项目 | 上游快照（`4c78901`） | 本仓库 |
|-|-|-|
| **进程与配置传递** | 独立的 Xray 子进程，通过标准输入接收配置 | Android 应用层（UI + `VpnService`）采用单进程架构，Xray 仍为独立子进程并通过标准输入接收配置；原生 Xray TUN 使用 JNI 启动器，Hev/SimpleTUN 使用 `ProcessBuilder` 并各自通过 SOCKS5 路径转发流量。APK 仅打包 `arm64-v8a` |
| **流量与 IPC** | 由 `hev-socks5-tunnel` 读取 Android VPN fd 并转发至 Xray 本地 SOCKS5 入站；状态统计使用动态回环 gRPC 端口 | 数据面由设置页在 Xray TUN、Hev、SimpleTUN 之间切换；服务状态与日志通过内存级 `VpnStateHub` 传递，状态统计使用动态 `127.0.0.1` gRPC 端口 |
| **配置导入** | 支持 JSON 配置、`vless://` 链接和 `simplexray://config/` 链接 | 仅支持通过 Android Storage Access Framework 或剪贴板导入完整 JSON、YAML 配置，不支持节点分享链接 |
| **规则文件** | 内置 `geoip.dat` 和 `geosite.dat`，并支持本地替换及这两个文件的 URL 更新 | 保留标准规则文件管理，并增加任意自定义 `.dat` 文件、`ext:` 文件引用、独立更新地址、文件校验和后台更新 |
| **配置处理** | 对 JSON 进行格式化，并删除 `log.access` 和 `log.error` | 使用 SnakeYAML 解析配置，并通过单向 Android 兼容处理流程调整入站、路由规则、DNS 引导主机、日志及部分出站配置 |
| **构建系统** | 使用 `ndkBuild` 和 `Android.mk`，配合标准 Gradle 配置 | 使用 CMake 和 `CMakeLists.txt`；原生隧道目标包含 Android 16 KB 内存页对齐链接选项，并使用 Version Catalog 和 Plugins DSL。工具链与依赖版本不在此重复 |
| **界面与布局** | 使用标准 Material 3 界面 | 使用 `compose-miuix-ui` 实现 Xiaomi HyperOS / MIUI 风格的界面，并针对手机和平板提供自适应布局、NavigationRail 支持和 Android 12+ 动态取色 |
| **数据存储与通信架构** | 使用 ContentProvider 封装的 `SharedPreferences` 和 `Gson` | 直接使用轻量级原生 `SharedPreferences` 与 `kotlinx.serialization`；UI 与后台服务通过内存级 `StateFlow` / `SharedFlow` 实现响应式通信 |
| **核心组件** | 仅 Xray-core launcher + `hev-socks5-tunnel` | Xray-core launcher 加可选 Hev（C/lwIP）、SimpleTUN（源码内 Zig）和原生 Xray TUN 后端；外部组件 revision 由 `version.properties` 固定 |
| **ABI 打包** | 提供 `arm64-v8a` 和 `x86_64` 分包 APK，以及通用 APK | 仅提供 `arm64-v8a` APK |
| **TUN 后端设置** | 不提供 Xray TUN 后端设置 | 可选 `Xray TUN`、`SimpleTUN` 和 `Hev Socks5 Tunnel`，默认值为 `Hev Socks5 Tunnel` |

---

[返回项目 README](./README_CN.md)
