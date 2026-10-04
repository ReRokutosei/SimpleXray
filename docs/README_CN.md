# SimpleXray 个人分支

<div align="center">
<img src="images/lineal.svg" alt="SimpleXray 图标" width="150">

**[English](./README.md)** | **中文**

<img src="https://app.fossa.com/api/projects/git%2Bgithub.com%2FReRokutosei%2FSimpleXray.svg?type=shield" alt="FOSSA Status" width="150">

<sub>Android 14+ (API 34) | arm64-v8a | MPL-2.0</sub>
</div>

SimpleXray 是 Xray-core 的 Android 前端与启动器。它接受完整的 JSON/YAML 配置，在 Android 环境中完成必要适配后，通过 `VpnService` 启动内核；不解析分享链接与订阅。

本仓库基于上游 [SimpleXray](https://github.com/lhear/SimpleXray)。除保留其完整配置模式外，本分支主要增加可选 TUN 数据面、重写的配置处理流程、规则文件管理、Miuix 界面与基准测试工具。

> [!NOTE]
> 本分支为个人使用与实验项目。不接受 Issue 与 PR，也不提供支持。如需长期维护的客户端，请使用上游，或 Fork 后通过 CI 自行构建。

## 本分支的主要差异

- **四种 TUN 后端**：Hev（C/lwIP，默认）、SingTUN（Go）、SimpleTUN（源码内 Zig）和原生 Xray TUN。
- **完整配置处理流程**：JSON/YAML 导入、内置编辑器、单向 Android 适配、日志级别控制，以及运行时统计注入。
- **规则文件管理**：内置 `geoip.dat`/`geosite.dat`，支持任意自定义 `.dat`、`ext:` 引用、独立更新 URL 与后台更新。
- **Miuix 界面**：Xiaomi HyperOS/MIUI 风格的 Compose 界面，支持手机/平板自适应布局、NavigationRail、浅色/深色/跟随系统主题与 Android 12+ 动态取色。
- **Android 集成**：按应用代理、本地直连 SOCKS5、网络切换处理与 16 KB 内存页对齐。
- **基准测试工具**：headless 基准测试运行器、报告与图表生成，以及设备测试数据集。
- **平台范围**：仅支持 Android 14+、仅提供`arm64-v8a`。

完整对照见 [与上游的差异](./upstream-differences_CN.md)。

## 界面预览

### 手机

<div align="center">
  <img src="./images/mobile_01.webp" alt="手机端界面 1" width="48%">
  <img src="./images/mobile_02.webp" alt="手机端界面 2" width="48%">
  <br>
  <img src="./images/mobile_03.webp" alt="手机端界面 3" width="48%">
  <img src="./images/mobile_04.webp" alt="手机端界面 4" width="48%">
</div>

### 平板

<div align="center">
  <img src="./images/table_01.webp" alt="平板端界面 1" width="48%">
  <img src="./images/table_02.webp" alt="平板端界面 2" width="48%">
  <br>
  <img src="./images/table_03.webp" alt="平板端界面 3" width="48%">
  <img src="./images/table_04.webp" alt="平板端界面 4" width="48%">
</div>

## 功能

### Xray-core

- Xray-core 作为独立子进程运行，通过标准输入接收配置，不产生中间配置文件。
- 原生 Xray TUN 模式由 JNI 启动器 `xray_exec.c` 将 Android VPN 文件描述符传入子进程。
- 仪表盘在打开或手动刷新时探测受支持出站端点的 TCP 握手延迟。仅 UDP 协议、QUIC 传输以及私网、环回、链路本地 IP 不参与探测。
- Hev、SingTUN、SimpleTUN 模式下，Xray 使用 `ProcessBuilder` 启动；所选 TUN 后端将流量转发至其本地 SOCKS5 入站。
- 内核状态与流量统计通过动态分配的 `127.0.0.1` 明文 gRPC 端口查询。标准输出与标准错误以内存流推送至界面，并支持导出。

### 配置

- 通过 SAF 或剪贴板导入完整 JSON/YAML 配置（`.json`、`.yaml`、`.yml`）。
- 单向 sanitizer 会移除面向桌面/Root 的字段，并适配 inbounds、路由规则、DNS 引导 hosts、日志与部分出站传输参数。详见 [配置覆写与删除](./configuration_CN.md)。
- 错误日志、访问日志与 DNS 日志分别控制；未显式设置级别时，`Auto` 映射为 `warning`。
- 内置编辑器支持文本编辑、搜索与括号匹配。

### TUN 后端

- **Hev**：默认后端，基于 C/lwIP。
- **SingTUN**：基于源码内 `sing-tun` 集成的 Go 用户态协议栈。
- **SimpleTUN**：源码内 Zig 引擎。仅支持 IPv4 与无认证 SOCKS5，MTU 固定为 1500，不配置 IPv6 路由或地址。详见 [架构说明](./simpletun_spec.md)。
- **Xray TUN**：由 JNI 启动器传入 VPN 文件描述符，使用 Xray 原生 TUN inbound。

### 规则文件

- 导入并替换标准 `geoip.dat` 与 `geosite.dat`。
- 导入任意自定义 `.dat` 文件，并通过 `ext:<file>:<tag>` 引用条目。
- 每个自定义规则文件可单独设置更新 URL；后台下载完成校验后才会安装。

### Android 集成

- Android 应用层（UI + `TProxyService`）运行于同一进程。服务状态与日志流通过内存 `StateFlow` / `SharedFlow` 传递。
- 支持分应用代理、`127.0.0.1:<socksPort>` 本机 SOCKS5 直连、网络切换回调，以及手机与平板自适应布局。

### 基准测试与工具

- `tools/benchmark.py` 通过 headless `BenchmarkService` 调度吞吐、空闲内存、bufferbloat、稳定性、弱网、CPS 与 QUIC 测试。
- 包含图表与报告生成。结果见 [Snapdragon 8 Elite Gen 5](./benchmark/8-elite-gen-5/report/benchmark_report.md) 与 [Snapdragon 778G](./benchmark/778g/report/benchmark_report.md)。

## 已知问题

### Telegram 可能复用失效的本地 SOCKS5 连接

Android 切换默认网络后，客户端到 `127.0.0.1:<socksPort>` 的既有 TCP 连接可能仍然存在，而对应的 Xray 上游传输已经失效。已观察到 Telegram 官方客户端会持续复用该回环连接；通过 TUN 转发的应用以及具备自身探活机制的客户端通常能自行恢复。

处理方式：

- 关闭再开启 VPN 或 Xray 内核。
- 重新连接当前网络接口。
- 改用会在网络切换后重建 SOCKS5 连接的客户端。

这属于客户端侧对失效连接的复用，不是监听器或 Xray 内核缺陷。SimpleXray 会注入更短的 outbound keepalive 与 TCP user-timeout，以更快识别被黑洞化的上游传输；不会强制关闭仍然健康的本地回环会话。

## 快速开始

构建环境见 [从源码构建](./building_CN.md)。首次构建需要网络：Gradle 会下载 `version.properties` 中固定的 Xray-core 预编译产物并校验其哈希。

```bash
git clone --recursive https://github.com/ReRokutosei/SimpleXray.git
cd SimpleXray

mkdir -p app/src/main/assets
wget https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.dat -O app/src/main/assets/geoip.dat
wget https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat -O app/src/main/assets/geosite.dat

(cd third_party/simpletun && zig build android)
ANDROID_NDK_HOME=/path/to/ndk bash third_party/sing-tun/build.sh

./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/simplexray-arm64-v8a.apk`。发布签名、No-GEO 构建与 CI 说明见 [从源码构建](./building_CN.md)。

## 文档

- [从源码构建](./building_CN.md)
- [配置覆写与删除](./configuration_CN.md)
- [与上游的差异](./upstream-differences_CN.md)
- [SimpleTUN 架构说明](./simpletun_spec.md)
- [基准测试报告](./benchmark/8-elite-gen-5/report/benchmark_report.md)
- [更新日志](../CHANGELOG.md)

## 上游与第三方

项目基于 [Xray-core](https://github.com/XTLS/Xray-core)、[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)、[sing-tun](https://github.com/SagerNet/sing-tun) 与 [compose-miuix-ui](https://github.com/compose-miuix-ui/miuix) 构建，并沿用上游 [SimpleXray](https://github.com/lhear/SimpleXray) 项目。应用图标使用 [Magnific](https://www.magnific.com) 提供的免费 Cookie Icons。

## 隐私与许可

隐私政策与免责声明见[《隐私政策》](./PrivacyPolicy_CN.md)和[《免责声明》](./Disclaimer_CN.md)。

除另有说明外，本项目以 Mozilla Public License 2.0（MPL-2.0）分发。完整条款见 [`LICENSE`](../LICENSE)。
