# SimpleXray 个人分支
<div align="center">
<img src="images/lineal.svg" alt="SimpleXray 图标" width="150">

**[English](./README.md)** | **中文**

<img src="https://app.fossa.com/api/projects/git%2Bgithub.com%2FReRokutosei%2FSimpleXray.svg?type=shield" alt="FOSSA Status" width="150">

</div>

SimpleXray 是一款面向 Android 的代理客户端。项目使用 [Xray-core](https://github.com/XTLS/Xray-core) 作为代理内核，并结合 Android `VpnService` 和 `hev-socks5-tunnel` 处理网络流量。

应用层与代理内核彼此独立。SimpleXray 将打包的 Xray-core 可执行文件 `libxray.so` 作为独立子进程启动，并通过标准输入传递配置。Hev 模式下，Xray 使用 `ProcessBuilder` 启动；Xray 原生 TUN 模式则使用 JNI 启动器将 VPN 文件描述符传递给子进程。

## 项目定位

SimpleXray 主要负责在 Android 上运行和管理 Xray-core。应用接受完整的 Xray-core 配置文件，并在 Android 环境中完成必要的适配后启动代理内核。

使用本项目需要具备基本的 Xray-core 配置知识，目前支持 JSON 和 YAML 两种配置格式。应用不会解析或生成 `vless://`、`vmess://`、`trojan://` 等分享链接，也不会根据单个节点信息自动生成完整配置，并且不负责处理订阅链接。

导入的配置在启动前会经过适配处理。部分仅适用于桌面系统或 Root 环境的配置会被删除或调整。

本仓库是基于上游 [SimpleXray](https://github.com/lhear/SimpleXray) 开发的个人分支。

## 界面预览

### 手机端

<div align="center">
  <img src="./images/mobile_01.webp" alt="手机端界面 1" width="48%">
  <img src="./images/mobile_02.webp" alt="手机端界面 2" width="48%">
  <br>
  <img src="./images/mobile_03.webp" alt="手机端界面 3" width="48%">
  <img src="./images/mobile_04.webp" alt="手机端界面 4" width="48%">
</div>

### 平板端

<div align="center">
  <img src="./images/table_01.webp" alt="平板端界面 1" width="48%">
  <img src="./images/table_02.webp" alt="平板端界面 2" width="48%">
  <br>
  <img src="./images/table_03.webp" alt="平板端界面 3" width="48%">
  <img src="./images/table_04.webp" alt="平板端界面 4" width="48%">
</div>

---

<details>
<summary><b>点击展开 / 折叠：与上游版本的主要区别</b></summary>

| 项目 | 上游版本（4c78901） | 本仓库 |
|-|-|-|
| **进程与配置传递**  | 使用独立子进程运行 Xray，并通过标准输入传递配置 | 使用独立子进程运行 Xray，并通过标准输入传递配置。Xray 原生 TUN 模式通过 JNI 启动器启动子进程，Hev 模式使用 `ProcessBuilder`。APK 仅打包 `arm64-v8a` |
| **流量与进程间通信** | 由 `hev-socks5-tunnel` 读取 Android VPN 文件描述符，并通过本地 SOCKS5 入站将流量转发至 Xray。状态统计使用动态分配的本机回环 TCP gRPC 端口 | 数据面由设置页选择的 TUN 后端决定。Xray 原生 TUN 模式通过 JNI 启动器接收 VPN 文件描述符，Hev 模式通过本地 SOCKS5 入站转发流量。内核状态和流量统计使用动态分配的 `127.0.0.1` TCP gRPC 端口 |
| **配置导入**     | 支持 JSON 配置、`vless://` 链接和 `simplexray://config/` 链接 | 仅支持通过 Android Storage Access Framework 或剪贴板导入完整 JSON、YAML 配置，不支持节点分享链接 |
| **规则文件**     | 内置 `geoip.dat` 和 `geosite.dat`，并支持本地替换及这两个文件的 URL 更新 | 保留标准规则文件管理，并增加任意自定义 `.dat` 文件、`ext:` 文件引用、独立更新地址、文件校验和后台更新 |
| **配置处理**     | 对 JSON 进行格式化，并删除 `log.access` 和 `log.error` | 使用 SnakeYAML 解析配置，并通过单向 Android 兼容处理流程调整入站、路由规则、DNS 引导主机、日志及部分出站配置 |
| **构建系统**     | 使用 `ndkBuild` 和 `Android.mk`，配合标准 Gradle 配置 | 使用 CMake 和 `CMakeLists.txt`；原生隧道目标包含 Android 16 KB 内存页对齐链接选项，并使用 Gradle Wrapper `9.7.0`、Android Gradle Plugin `9.3.1`、Version Catalog 和 Plugins DSL |
| **界面与布局**    | 使用标准 Material 3 界面                                   | 使用 `compose-miuix-ui` 实现 Xiaomi HyperOS / MIUI 风格的界面，并针对手机和平板提供自适应布局                                                           |
| **数据存储与序列化** | 使用 ContentProvider 封装的 `SharedPreferences` 和 `Gson` | 使用 ContentProvider 封装的 `SharedPreferences`，使用 `kotlinx.serialization` 处理结构化数据，并通过 Compose `StateFlow` 管理界面状态 |
| **核心组件**     | Xray-core `v26.3.27` 和 `hev-socks5-tunnel` `v2.14.3` | Xray-core `v26.7.28` 和 `hev-socks5-tunnel` `v2.17.0`                                  |
| **ABI 打包**     | 提供 `arm64-v8a` 和 `x86_64` 分包 APK，以及通用 APK | 仅提供 `arm64-v8a` APK |
| **TUN 后端设置** | 不提供 Xray TUN 后端设置 | 可选 `Xray TUN` 和 `Hev Socks5 Tunnel`，默认值为 `Xray TUN` |

</details>

## 功能与修改

### 1. 界面与自适应布局

项目重新设计了应用界面，并使用 `compose-miuix-ui` 实现具有 Xiaomi HyperOS / MIUI 风格的界面。

主要修改包括以下内容。

* 使用 Miuix 提供的 `TopAppBar`、`Card`、`InputField`、`Checkbox`、`OverlayIconDropdownMenu`、`OverlayDialog` 和 `OverlayBottomSheet` 等组件。
* 支持浅色、深色和跟随系统三种主题模式。
* 支持 Android 12 及以上版本的 Monet 动态取色，并将动态颜色应用于 Miuix 配色方案。
* 当屏幕宽度达到 `600dp` 时，将主要导航切换为垂直方向的 Miuix `NavigationRail`。
* 当屏幕宽度达到 `840dp` 且处于横屏状态时，`ConfigScreen` 使用双栏布局，同时显示配置列表和配置编辑区域。
* 在较大屏幕上将仪表盘、设置页和基于应用的代理页面的主要内容区域限制为最大 `840dp`。
* 使用悬浮式导航栏，并允许页面内容在半透明导航区域下方继续滚动。
* 在基于应用的代理页面中增加应用筛选功能，可以选择是否显示未声明 `android.permission.INTERNET` 权限的应用。
* 提供日志搜索、日志导出、日志清除和仪表盘延迟刷新等直接操作。
* 配置编辑器支持全屏编辑模式。

### 2. Xray-core 配置导入

SimpleXray 接受完整的 Xray-core 配置文件，不负责将单个代理节点或分享链接转换为配置。

支持以下配置来源。

* JSON 配置文件。
* YAML 配置文件。
* 通过 Android Storage Access Framework 导入的 `.json`、`.yaml` 和 `.yml` 文件。
* 从剪贴板粘贴的配置文本。

以下内容不受支持。

* `vless://` 等节点分享链接。
* `vmess://` 等节点分享链接。
* `trojan://` 等节点分享链接。
* 订阅链接。

YAML 配置会先被解析为结构化数据。应用随后对配置进行平台适配处理，完成处理后重新序列化，再将配置传递给 Xray-core。

应用同时提供内置配置编辑器，支持文本编辑、搜索。

### 3. 规则文件管理

SimpleXray 保留内置的 `geoip.dat` 和 `geosite.dat` 规则文件，并增加本地导入和在线更新功能。

* 可以从设备存储导入新的 `geoip.dat` 和 `geosite.dat` 文件，并替换内置文件。
* 可以导入和管理其他自定义 `.dat` 规则文件。
* 支持使用自定义规则标签，例如 `ext:custom.dat:subcategory`。
* 可以为不同的自定义 `.dat` 文件设置独立的在线更新地址。
* 配置了更新地址的规则文件可以在后台下载更新。

### 4. 进程管理与进程间通信

SimpleXray 根据配置传递、VPN 流量、内核日志和状态统计查询的不同用途，分别采用相应的通信通道。

* **配置传递**：生成的 JSON 配置直接写入 Xray-core 的标准输入，无需生成中间配置文件。
* **原生 TUN 模式**：JNI 启动器将 Android `VpnService` 提供的文件描述符传递给 Xray 子进程，再接入 Xray 的 TUN 入站。
* **内核日志**：通过管道读取 Xray 的标准输出和标准错误。
* **状态统计**：通过动态分配的 `127.0.0.1` TCP 端口提供明文 gRPC，用于查询内核状态和流量统计。
* **Hev 隧道模式**：选中该模式时，由 `hev-socks5-tunnel` 读取 Android VPN 文件描述符中的流量，再通过本地 SOCKS5 入站转发给 Xray。

> 实测数据请参阅 [Android TUN 性能基准测试报告](./benchmark/android-tun-benchmark.md)。


### 5. 路由与内核配置优化

项目对部分 Xray-core 配置进行了调整。

* 将 `domainMatcher` 从 `mph` 调整为 `hybrid`，以改善内存占用和域名匹配性能之间的平衡。
* 为匹配的 AliDNS DoH 主机名增加静态主机映射，以避免这些端点依赖本地 DNS 引导。
* 在 Android 环境需要时，将 `::` 和 `0.0.0.0` 等通配监听地址调整为 `127.0.0.1`。
* **仪表盘延迟展示**：仪表盘显示设备到各出站服务器端点的 TCP 握手耗时。端点解析规则为：vless 和 vmess 使用 `settings.vnext[0]`，trojan、shadowsocks、HTTP 和 SOCKS 使用 `settings.servers[0]`。进入仪表盘时自动探测一次，也可以手动刷新。WireGuard、Hysteria2 等仅支持 UDP 的协议、QUIC 传输，以及私网、环回和链路本地 IP 字面量不参与探测。该数值反映设备到节点的网络路径，不代表 Xray 内核的处理耗时。无法连接的节点显示为失败。

### 6. Android 平台配置适配

SimpleXray 可以直接导入完整的 Xray-core 配置文件。配置启动前会经过针对 Android 环境设计的适配流程，因此不要求用户手动删除所有桌面系统相关配置。

目前主要处理以下内容。

* **Windows 进程规则**

  删除 `routing.rules` 中的 Windows 可执行文件路径，例如 `chrome.exe`。如果相关规则因此失去有效条件，也会一并删除。

* **TUN 入站**

  启用 VPN 和 Xray 原生 TUN 模式时，保留 `protocol: tun` 入站，补充 Android 所需的名称并删除桌面自动路由字段。使用 Hev 模式或关闭 VPN 时，删除 `protocol: tun` 入站。

* **文件日志**

  根据 Android 文件系统权限限制，删除 `access` 和 `error` 中不适用的文件写入路径，避免 Xray-core 因无法访问指定路径而启动失败。

* **监听地址**

  在 Android 环境需要时，将 `::` 和 `0.0.0.0` 等监听地址调整为 `127.0.0.1`。

* **流量嗅探**

  在配置适配过程中保留受支持的流量嗅探配置，包括 `destOverride`。

配置适配采用单向处理流程。原始配置经过处理后生成适用于 Android 环境的新配置，并将处理结果交给 Xray-core 执行。

### 7. 日志级别设置

应用提供日志级别设置，可以选择以下级别。

* `Auto`
* `Debug`
* `Info`
* `Warning`
* `Error`
* `None`

用户选择的日志级别会在配置处理过程中写入 Xray-core 配置。

如果配置中包含不适用于 Android 环境的 `access` 或 `error` 文件日志路径，应用会在处理过程中将其删除。

### 8. 构建系统与依赖更新

项目将原有的 Android NDK 构建方式迁移至 CMake。

* 使用 `CMakeLists.txt` 替代 `Android.mk` 和 `ndkBuild`。
* 原生构建配置支持 Android 16 KB 内存页。
* Gradle Wrapper 更新至 `v9.7.0`。
* Android Gradle Plugin 使用 `v9.3.1`。
* 使用 Gradle Version Catalog 管理项目依赖。
* 使用 Gradle Plugins DSL 管理 Gradle 插件。
* 使用 `kotlinx.serialization` 替代 `Gson`，负责类型安全的序列化和反序列化。

---

## 构建要求

构建本项目需要以下环境。

* Android 10 或更高版本，对应 API Level 29。
* Android SDK，包括项目所需的 Build Tools 和 Android Platform SDK。
* Target SDK 36。
* Android NDK。
* CMake。
* JDK 21。
* 支持子模块操作的 Git。

项目使用 Gradle Wrapper，因此构建时会根据仓库中的 Wrapper 配置使用指定的 Gradle 版本。

---

## 从源码构建

首先克隆仓库及其子模块。

```bash
git clone --recursive https://github.com/ReRokutosei/SimpleXray.git
cd SimpleXray
```

执行以下命令构建 Release APK。

```bash
./gradlew assembleRelease
```

构建完成后，APK 位于以下路径。

```text
app/build/outputs/apk/release/simplexray-arm64-v8a.apk
```

如果仓库在克隆时没有初始化子模块，可以执行以下命令。

```bash
git submodule update --init --recursive
```

---

## 上游项目与依赖

SimpleXray 使用或基于以下开源项目开发：

* [**`compose-miuix-ui`**](https://github.com/compose-miuix-ui/miuix) — 面向 Kotlin Multiplatform 的 Jetpack Compose UI 组件库，界面设计参考 Xiaomi HyperOS / MIUI
* [**`Xray-core`**](https://github.com/XTLS/Xray-core) — SimpleXray 使用的代理网络核心
* [**`SimpleXray`**](https://github.com/lhear/SimpleXray) — 本项目所基于的上游 Android 客户端
* [**`hev-socks5-tunnel`**](https://github.com/heiher/hev-socks5-tunnel) — 用于 Android 网络流量处理的 SOCKS5 VPN 隧道实现

### 致谢

本项目的应用图标使用了来自 Magnific 平台的免费资源，在此感谢原作者的创作：

* 由 [Magnific](https://www.magnific.com) 设计的 [Cookie 图标（包含 Special Lineal, Flat, Lineal Color 三种风格）](https://www.magnific.com/icon/cookie_1047813)

---

## 隐私政策与免责声明

隐私政策请参阅[《隐私政策》](./PrivacyPolicy_CN.md)，免责声明请参阅[《免责声明》](./Disclaimer_CN.md)。

使用本应用即表示您已阅读并同意隐私政策与免责声明。如您不同意其中任何内容，请卸载本应用并停止使用。

---

## 许可证

除另有说明外，本项目按照上游项目的许可条款使用 Mozilla Public License 2.0（也称 MPL-2.0）

完整许可证文本请参阅 [`LICENSE`](../LICENSE) 文件。

<div align="center">

<img src="https://app.fossa.com/api/projects/git%2Bgithub.com%2FReRokutosei%2FSimpleXray.svg?type=large" alt="FOSSA Status"  width="300">

</div>
