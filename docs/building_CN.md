# 从源码构建

**[English](./building.md)** | **中文**

本文说明构建环境、原生依赖、APK 构建、签名、测试与 CI 发布流程。

## 1. 构建环境

- Linux 构建主机。
- 运行验证需要 Android 14 及以上设备；仅支持 `arm64-v8a`。
- Android SDK，包含 `compileSdk 37`、Build Tools 和对应版本的 Platform SDK。`minSdk` 为 34，`targetSdk` 为 36。
- Android NDK，版本由 [`version.properties`](../version.properties) 中的 `NDK_VERSION` 固定。需要设置 `ANDROID_NDK_HOME` 或 `NDK_HOME`。
- Zig，具体版本以 CI 工作流 [`.github/workflows/verify.yml`](../.github/workflows/verify.yml) 为准。
- CMake 3.22.1 或更高版本。
- JDK 25 供 Gradle 使用；Kotlin/Java 字节码目标仍为 21。
- Go，版本由 `version.properties` 中的 `GO_VERSION` 固定。
- 支持子模块的 Git。
- Python 3，用于运行工具链单元测试。

Gradle 版本由 Wrapper 提供；AGP、依赖库版本及其余 pin 见 Gradle version catalog。

## 2. 获取源码

```bash
git clone --recursive https://github.com/ReRokutosei/SimpleXray.git
cd SimpleXray
```

已有仓库未初始化子模块时执行：

```bash
git submodule update --init --recursive
```

## 3. 准备 Geo 资源

`geoip.dat` 与 `geosite.dat` 不纳入版本控制。本地调试构建可将当前文件放入 `app/src/main/assets/`：

```bash
mkdir -p app/src/main/assets
wget https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.dat -O app/src/main/assets/geoip.dat
wget https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat -O app/src/main/assets/geosite.dat
```

发布构建会在构建时下载上游 `latest` 的当前文件。使用 `-PnoGeo=true` 时改为打包 `app/src/main/assets-no-geo/`。

## 4. 构建原生依赖

### Hev

`hev-socks5-tunnel` 由 Gradle 的 CMake 配置阶段从 `third_party/hev-socks5-tunnel/` 编译，无需手动处理。

### SimpleTUN

CMake 配置阶段会导入预编译的 Zig 静态库，缺失时直接报错：

```bash
(cd third_party/simpletun && zig build android && zig build test)
```

产物：`third_party/simpletun/zig-out/android/prebuilt/arm64-v8a/libsimpletun.a`。


### Xray-core

Gradle 的 `ensureXrayCore` 任务会下载与 `XRAY_CORE_VERSION` 匹配的官方 `Xray-android-arm64-v8a.zip`，校验 `XRAY_CORE_ZIP_SHA256`，并在 `preBuild` 前安装为 `app/src/main/jniLibs/arm64-v8a/libxray.so`。首次构建，或 pinned 版本与哈希发生变化时需要网络；正常构建不需要手动交叉编译 Xray。

## 5. 本地构建

### Debug

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/simplexray-arm64-v8a.apk`。

### Release

Release 变体启用 R8、资源压缩和 V3/V4 签名。在仓库根目录创建 `store.properties`，或提供同等环境变量：

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

对应环境变量：`KEYSTORE_PATH`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

```bash
./gradlew assembleRelease -x lint -PappVerName=vX.Y.Z
```

产物：`app/build/outputs/apk/release/simplexray-arm64-v8a.apk`。

未配置有效 release 签名时，Gradle 会退回 debug 签名；该 APK 不可用于分发。

No-GEO 版本改为打包 `app/src/main/assets-no-geo/`：

```bash
./gradlew clean assembleRelease -x lint -PnoGeo=true -PappVerName=vX.Y.Z
```
No-GEO 版本在缺少内置资源时仍可正常使用：Settings 会将缺失的 `geoip.dat`/`geosite.dat` 显示为缺失状态，可导入或下载，恢复操作会退化为从配置的 URL 下载。若所选配置引用了未提供的 GEO 数据，Xray 仍会按其正常流程启动失败。

## 6. 验证命令

```bash
./gradlew :app:testDebugUnitTest
(cd third_party/simpletun && zig build test)
python3 -m unittest discover -s tools/tests -v
python3 tools/sync_versions.py --check
```

## 7. CI 与发布

`verify.yml` 在 `main`/`dev` 分支推送和 pull request 时执行 version pin 检查、Go 格式检查、SimpleTUN 构建与测试，以及 `testDebugUnitTest assembleDebug`。

`release.yml` 仅在匹配 `v*` 的语义化版本 tag 上触发。该流程会校验 tag、Xray release commit 与压缩包哈希，下载滚动 Geo 资源，构建 SimpleTUN，运行单元测试，签名普通版和 No-GEO 版 APK，并创建 GitHub Draft Release。使用前需配置以下 Repository Secrets：

- `SIGNING_KEY`：JKS keystore 的 Base64 编码。
- `KEY_STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

内核升级由 `version.properties` 控制：修改 `XRAY_CORE_VERSION`、`XRAY_CORE_COMMIT`、`XRAY_CORE_ZIP_SHA256`，然后执行 `python3 tools/sync_versions.py --check`。Geo 规则文件在构建时从上游 `latest` 下载，不做版本固定。

---

[返回项目 README](./README_CN.md)
