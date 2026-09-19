# SimpleXray Android TUN 性能基准测试报告

本文档记录对 SimpleXray 支持的四种透明代理 TUN 协议栈实现 `hev-socks5-tunnel`、`Xray Native TUN`、`SingTUN` 与 `MipsTUN`，在**吞吐量**、**CPU 占用率**、**能效比**以及**空闲连接驻留内存增长**维度进行的客观实测与对比分析。

---

## 1. 测试环境与硬件规格

### 测试主机（iPerf3 Server / PC）
- **操作系统**: Linux x86_64 (Linux 6.12.107+deb13-amd64)
- **处理器 (CPU)**: AMD Ryzen 7 6800H @ 3.2GHz (8 核 16 线程)
- **无线网卡**: Intel(R) Wi-Fi 6E AX210 160MHz
- **有线接口**: USB Type-C 物理连接，基于 RNDIS 网络共享虚拟以太网，实际系统链路协商速率为 1Gbps；线材支持 USB 3.2 Gen1 / USB4 高规格，不构成物理瓶颈。
- **iPerf3 版本**: 3.18 (cJSON 1.7.15)

### Android 测试设备（iPerf3 Client / DUT）
- **操作系统**: Android 14 (One UI 6.0, Linux 5.4 内核)
- **处理器 (SoC)**: 高通骁龙 778G (Kryo 670，8 核架构: 1×2.4GHz Cortex-A78 超大核 + 3×2.2GHz Cortex-A78 大核 + 4×1.9GHz Cortex-A55 能效核)
- **有线接口**: USB Type-C 物理连接，基于 RNDIS 网络共享虚拟以太网，实际系统链路协商速率为 1Gbps；线材支持 USB 3.2 Gen1 / USB4 高规格，不构成物理瓶颈。
- **无线规格**: 802.11 a/b/g/n/ac/ax 2.4G+5GHz, HE80, 2×2 MIMO
- **iPerf3 版本**: 3.21 (aarch64 静态编译版)

### 局域网网关
- **网关设备**: Android 16 手机，高通 Snapdragon 8 Elite SoC，高通 FastConnect 7900 无线连接系统。
- **网络频段**: 5GHz Wi-Fi 热点 (WLAN AP，80MHz 频宽，物理协商速率 1201 Mbps)

---

## 2. 测试方法

### 2.1 组件版本
- **测试应用**: SimpleXray (Debug build)
- **Xray 核心**: Xray-core v26.9.9 (Android arm64)
- **SingTUN 后端**: sing-tun `fbc0c3dff312`
- **MipsTUN 后端**: Mihomo mipstack `5e78149cf123`
- **Hev 后端**: hev-socks5-tunnel `d1178b52` (2.17.0)

### 2.2 测试链路
测试全程采用 **局域网 Direct/Freedom 纯透明代理** 链路，流量经由 Android 系统 `VpnService` 虚拟网卡 (`tun0`) 路由至各 TUN 协议栈处理，并直连 PC 出站：

```text
               [ 局域网网关 (5GHz Wi-Fi AP / USB RNDIS) ]
                      │                                 │
                      ▼                                 ▼
        [ Android 设备 (DUT) ]                  [ PC 主机 (Server) ]
          iperf3 client 进程                     iperf3 server 进程 (:5201)
                 │                                      ▲
            Android VpnService (tun0)                   │
                 │                                      │
           ┌─────┴────────────────────────────────┐     │
           │ (协议栈后端切换)                       │     │
           ▼              ▼            ▼          ▼     │
     [ Hev 模式 ]   [ SingTUN 模式 ] [ MipsTUN ] [ Xray 原生 TUN ]
   hev-socks5-tunnel SingTUN (Go)   mipstack (Go) Xray TUN Inbound
   (精简配置 lwIP) (sing-box专有栈)(mipstack Go栈)(Go/gVisor 协议栈)
           │              │            │          │     │
      Xray SOCKS5    Xray SOCKS5  Xray SOCKS5     │     │
        Inbound        Inbound      Inbound       │     │
           │              │            │          │     │
           └──────────────┴────────────┴──────────┘     │
                               │                        │
                               ▼                        │
                      Xray Freedom Outbound ────────────┘
```

### 2.3 iPerf3 测试参数
- **TCP 测试规范**：单流（`-P 1`）与多流（`-P 8`）并发；测试持续时长 10 秒（`-t 10`），采样间隔 1 秒（`-i 1`）。
- **UDP 测试规范**：参数配置为 `-u -b 200M`，目标限速 200 Mbps。200 Mbps 为统一设定的目标限速阈值，用于横向评估不同协议栈的丢包率、抖动与转发保真度，并非物理信道带宽极限。
- **数据传输方向**：Upload 为 Android Client → PC Server；Download 为 PC Server → Android Client，通过 iPerf3 `-R` 逆向流参数实现。

### 2.4 CPU 统计口径说明
- **数据采集方式**：CPU 使用率通过 Android `/proc/stat` 与各进程时间片采样统计。
- **多核累计占用百分比**：测试设备高通骁龙 778G 为 8 核架构（1× 超大核 + 3× 大核 + 4× 能效核），单核跑满计为 100%，理论上限为 **800%**。多流并发或高负载下出现 150% ~ 240%，代表占用了约 1.5 ~ 2.4 个物理核心，而非单核超频。

### 2.5 内存采样口径说明
- **采样指标**：采用 PSS（Proportional Set Size），通过 Android `dumpsys meminfo` 采集整进程物理内存，反映代理服务对设备物理内存的实际占用。
- **空闲连接压测采样**：在空闲连接阶梯压测（0 → 250 → 500 → 750 → 1000 连接）中，系统在达到各阶梯连接数目标后保持稳定 1.5 秒再执行 PSS 采样，以剔除握手突发波动的干扰。

### 2.6 免责声明

> [!NOTE]
> 
> 本报告所有综合对比表格、可视化图表及总结分析中的数据，均为 **3 轮实测的算术平均值**。
> 
> Wi-Fi 无线测试受空间电磁环境与空口信道调度影响存在正常瞬时波动，测试中个别代理场景略高于 No VPN 物理基线，属于无线空口信道协商与突发调度波动，不应解读为代理层具备物理链路加速功能。

---

## 3. 测试数据

> [数据可视化请看第五章](#5-数据可视化)

### 3.1 综合平均测试数据 (3 轮平均)

注：本节各表格数据均为 3 轮完整实测的算术平均值。

#### 3.1.1 USB 3.2 Gen1 / 4.0 有线以太网测试 (TCP & UDP)

| 测试用例 / 配置 | 后端协议栈 | MTU | 协议 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | TCP | 单流 | 692.00 Mbps | 381.48 Mbps | 0.8% / 4.0% | 92.2 MB |
| **USB Baseline (No VPN) [P=8]** | `direct_none` | 0 | TCP | P=8 | 758.48 Mbps | 424.92 Mbps | 0.0% / 2.5% | 91.7 MB |
| **Hev (MTU 1500) [Single Stream]** | `hev` | 1500 | TCP | 单流 | 638.39 Mbps | 303.01 Mbps | 48.5% / 83.0% | 100.5 MB |
| **Xray TUN (MTU 1500) [Single Stream]** | `xray` | 1500 | TCP | 单流 | 445.62 Mbps | 58.13 Mbps | 96.1% / 199.0% | 98.0 MB |
| **SingTUN (MTU 1500) [Single Stream]** | `sing` | 1500 | TCP | 单流 | 643.88 Mbps | 177.25 Mbps | 54.6% / 144.0% | 111.5 MB |
| **MipsTUN (MTU 1500) [Single Stream]** | `mips` | 1500 | TCP | 单流 | 608.62 Mbps | 232.40 Mbps | 111.8% / 205.0% | 129.8 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | TCP | 单流 | 647.39 Mbps | 358.30 Mbps | 38.6% / 70.0% | 97.3 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | TCP | 单流 | 320.62 Mbps | 59.71 Mbps | 91.7% / 187.5% | 98.0 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | TCP | 单流 | 490.16 Mbps | 361.88 Mbps | 31.6% / 116.0% | 111.7 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | TCP | 单流 | 492.88 Mbps | 323.97 Mbps | 50.2% / 150.0% | 126.3 MB |
| **Hev (MTU 1500) [P=8]** | `hev` | 1500 | TCP | P=8 | 514.52 Mbps | 301.95 Mbps | 42.2% / 93.5% | 98.2 MB |
| **Xray TUN (MTU 1500) [P=8]** | `xray` | 1500 | TCP | P=8 | 361.19 Mbps | 156.85 Mbps | 106.0% / 224.5% | 99.0 MB |
| **SingTUN (MTU 1500) [P=8]** | `sing` | 1500 | TCP | P=8 | 220.77 Mbps | 395.75 Mbps | 64.2% / 222.5% | 111.8 MB |
| **MipsTUN (MTU 1500) [P=8]** | `mips` | 1500 | TCP | P=8 | 504.16 Mbps | 158.94 Mbps | 107.0% / 221.5% | 150.8 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | TCP | P=8 | 513.97 Mbps | 362.60 Mbps | 33.9% / 92.0% | 98.2 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | TCP | P=8 | 363.00 Mbps | 141.93 Mbps | 106.8% / 222.0% | 98.7 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | TCP | P=8 | 505.80 Mbps | 381.72 Mbps | 37.2% / 136.5% | 152.2 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | TCP | P=8 | 510.15 Mbps | 393.05 Mbps | 55.9% / 184.0% | 191.1 MB |
| **USB Baseline (No VPN) [UDP] [Single Stream]** | `direct_none` | 0 | UDP | 单流 | 199.88 Mbps | 200.01 Mbps | 0.8% / 4.3% | 92.5 MB |
| **USB Baseline (No VPN) [UDP] [P=8]** | `direct_none` | 0 | UDP | P=8 | 199.90 Mbps | 200.00 Mbps | 0.0% / 2.0% | 91.7 MB |
| **Hev (MTU 1500) [UDP] [Single Stream]** | `hev` | 1500 | UDP | 单流 | 199.57 Mbps | 137.12 Mbps (20.8% 丢包) | 159.2% / 192.0% | 106.4 MB |
| **Xray TUN (MTU 1500) [UDP] [Single Stream]** | `xray` | 1500 | UDP | 单流 | 199.73 Mbps | 99.34 Mbps (37.9% 丢包) | 94.8% / 206.0% | 101.5 MB |
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 178.77 Mbps (7.4% 丢包) | 107.08 Mbps (34.0% 丢包) | 189.0% / 236.3% | 74.7 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 181.70 Mbps (5.1% 丢包) | 107.86 Mbps (32.9% 丢包) | 193.1% / 231.7% | 117.8 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 199.90 Mbps | 200.43 Mbps | 135.3% / 203.5% | 104.6 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 199.68 Mbps | 200.14 Mbps | 77.2% / 203.0% | 102.1 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 198.50 Mbps | 200.03 Mbps | 201.3% / 234.3% | 102.5 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 195.12 Mbps (2.3% 丢包) | 194.89 Mbps (2.4% 丢包) | 199.2% / 239.0% | 107.6 MB |

#### 3.1.2 5GHz Wi-Fi 无线网络测试 (TCP & UDP)

| 测试用例 / 配置 | 后端协议栈 | MTU | 协议 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | TCP | 单流 | 391.26 Mbps | 346.51 Mbps | 0.3% / 2.7% | 100.1 MB |
| **Wi-Fi Baseline (No VPN) [P=8]** | `direct_none` | 0 | TCP | P=8 | 384.49 Mbps | 395.61 Mbps | 0.0% / 0.0% | 100.0 MB |
| **Hev (MTU 1500) [Single Stream]** | `hev` | 1500 | TCP | 单流 | 353.42 Mbps | 277.22 Mbps | 27.5% / 73.5% | 102.2 MB |
| **Xray TUN (MTU 1500) [Single Stream]** | `xray` | 1500 | TCP | 单流 | 313.11 Mbps | 63.33 Mbps | 65.3% / 172.0% | 102.3 MB |
| **SingTUN (MTU 1500) [Single Stream]** | `sing` | 1500 | TCP | 单流 | 360.38 Mbps | 170.37 Mbps | 28.4% / 142.5% | 109.2 MB |
| **MipsTUN (MTU 1500) [Single Stream]** | `mips` | 1500 | TCP | 单流 | 356.37 Mbps | 241.88 Mbps | 55.0% / 201.0% | 136.6 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | TCP | 单流 | 371.82 Mbps | 361.40 Mbps | 17.3% / 74.5% | 97.2 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | TCP | 单流 | 248.38 Mbps | 63.01 Mbps | 84.0% / 186.5% | 98.0 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | TCP | 单流 | 384.61 Mbps | 277.53 Mbps | 27.1% / 97.8% | 111.0 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | TCP | 单流 | 343.19 Mbps | 353.59 Mbps | 34.1% / 125.0% | 126.5 MB |
| **Hev (MTU 1500) [P=8]** | `hev` | 1500 | TCP | P=8 | 366.06 Mbps | 299.38 Mbps | 23.9% / 94.0% | 98.5 MB |
| **Xray TUN (MTU 1500) [P=8]** | `xray` | 1500 | TCP | P=8 | 330.88 Mbps | 154.88 Mbps | 110.0% / 238.5% | 99.0 MB |
| **SingTUN (MTU 1500) [P=8]** | `sing` | 1500 | TCP | P=8 | 344.33 Mbps | 429.29 Mbps | 48.4% / 226.0% | 155.3 MB |
| **MipsTUN (MTU 1500) [P=8]** | `mips` | 1500 | TCP | P=8 | 383.46 Mbps | 141.44 Mbps | 48.6% / 179.5% | 171.3 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | TCP | P=8 | 377.85 Mbps | 411.21 Mbps | 16.0% / 92.0% | 144.5 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | TCP | P=8 | 323.65 Mbps | 180.25 Mbps | 102.9% / 244.5% | 144.3 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | TCP | P=8 | 346.84 Mbps | 400.62 Mbps | 25.9% / 116.0% | 191.0 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | TCP | P=8 | 348.47 Mbps | 377.00 Mbps | 35.8% / 179.5% | 170.7 MB |
| **Wi-Fi Baseline (No VPN) [UDP] [Single Stream]** | `direct_none` | 0 | UDP | 单流 | 195.57 Mbps (1.6% 丢包) | 199.97 Mbps | 0.8% / 4.5% | 92.8 MB |
| **Wi-Fi Baseline (No VPN) [UDP] [P=8]** | `direct_none` | 0 | UDP | P=8 | 199.46 Mbps | 200.00 Mbps | 0.0% / 2.5% | 91.7 MB |
| **Hev (MTU 1500) [UDP] [Single Stream]** | `hev` | 1500 | UDP | 单流 | 148.50 Mbps (17.7% 丢包) | 123.75 Mbps (29.6% 丢包) | 154.4% / 182.5% | 105.2 MB |
| **Xray TUN (MTU 1500) [UDP] [Single Stream]** | `xray` | 1500 | UDP | 单流 | 148.05 Mbps (25.2% 丢包) | 130.29 Mbps (26.0% 丢包) | 123.9% / 217.5% | 103.1 MB |
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 150.88 Mbps (15.0% 丢包) | 123.63 Mbps (27.7% 丢包) | 181.6% / 225.0% | 116.2 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 155.45 Mbps (12.8% 丢包) | 124.03 Mbps (28.5% 丢包) | 182.9% / 227.7% | 83.5 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 186.89 Mbps | 200.12 Mbps | 132.0% / 208.5% | 110.7 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 197.41 Mbps (0.4% 丢包) | 199.61 Mbps | 80.0% / 217.0% | 106.5 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 199.58 Mbps | 200.06 Mbps | 194.7% / 228.7% | 115.1 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 188.36 Mbps | 198.50 Mbps (0.9% 丢包) | 197.1% / 232.0% | 82.1 MB |

#### 3.1.3 设备内部纯回环压力测试

| 测试用例 / 配置 | 后端协议栈 | MTU | 流模式 | 单流/多流吞吐 (Gbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Loopback Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | 单流 | 20.74 Gbps | 0.0% | 103.6 MB |
| **Loopback Baseline (No VPN) [P=8]** | `direct_none` | 0 | P=8 | 18.39 Gbps | 1.3% | 103.6 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | 单流 | 20.81 Gbps | 12.9% | 105.7 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | 单流 | 20.71 Gbps | 4.2% | 102.8 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | 单流 | 20.89 Gbps | 8.7% | 104.0 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | 单流 | 20.92 Gbps | 12.3% | 118.4 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | P=8 | 18.38 Gbps | 17.1% | 95.7 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | P=8 | 18.03 Gbps | 2.0% | 97.0 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | P=8 | 18.29 Gbps | 18.5% | 99.0 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | P=8 | 18.48 Gbps | 9.8% | 102.3 MB |

#### 3.1.4 空闲连接驻留与内存增长 (TCP & UDP)

注：本表数据为 3 轮独立实测的算术平均值，基准 PSS 为建立连接前的空闲内存，1000 连接 PSS 为阶梯压测达到 1000 连接并稳定 1.5 秒后采样的物理内存。

| 后端协议栈 | 传输协议 | 连接范围 | 基准 PSS (3轮均值) | 1000 连接 PSS (3轮均值) | 内存增长斜率 (3轮均值) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **HEV** | TCP | 0 -> 1000 | 97.2 MB | 114.0 MB | 17.20 KiB/conn |
| **HEV** | UDP | 0 -> 1000 | 105.8 MB | 115.4 MB | 9.83 KiB/conn |
| **XRAY** | TCP | 0 -> 1000 | 98.7 MB | 100.8 MB | 2.15 KiB/conn |
| **XRAY** | UDP | 0 -> 1000 | 99.2 MB | 102.3 MB | 3.24 KiB/conn |
| **SING** | TCP | 0 -> 1000 | 103.6 MB | 150.3 MB | 47.82 KiB/conn |
| **SING** | UDP | 0 -> 1000 | 107.2 MB | 124.5 MB | 17.72 KiB/conn |
| **MIPS** | TCP | 0 -> 1000 | 110.5 MB | 155.7 MB | 46.35 KiB/conn |
| **MIPS** | UDP | 0 -> 1000 | 101.1 MB | 162.5 MB | 62.81 KiB/conn |

---

### 3.2 分轮实测数据明细

注：本小节包含 3 轮独立实测的完整原始采样记录，每轮测试均经历冷启动、基线建立与完整的 TCP/UDP/回环/空闲连接压测流程。


<details>
<summary><b>第 1 轮测试数据明细 (点击展开)</b></summary>

#### USB 3.2 Gen1 / 4.0 有线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU | 协议 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | TCP | 单流 | 702.50 Mbps | 381.32 Mbps | 0.8% / 4.0% | 92.3 MB |
| **USB Baseline (No VPN) [P=8]** | `direct_none` | 0 | TCP | P=8 | 768.20 Mbps | 422.16 Mbps | 0.0% / 3.0% | 91.8 MB |
| **Hev (MTU 1500) [Single Stream]** | `hev` | 1500 | TCP | 单流 | 642.11 Mbps | 306.04 Mbps | 48.2% / 82.0% | 100.5 MB |
| **Xray TUN (MTU 1500) [Single Stream]** | `xray` | 1500 | TCP | 单流 | 317.75 Mbps | 61.12 Mbps | 94.2% / 196.0% | 98.2 MB |
| **SingTUN (MTU 1500) [Single Stream]** | `sing` | 1500 | TCP | 单流 | 654.91 Mbps | 183.49 Mbps | 55.0% / 143.0% | 111.6 MB |
| **MipsTUN (MTU 1500) [Single Stream]** | `mips` | 1500 | TCP | 单流 | 621.59 Mbps | 228.25 Mbps | 110.6% / 200.0% | 132.6 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | TCP | 单流 | 646.64 Mbps | 359.29 Mbps | 39.1% / 70.0% | 97.4 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | TCP | 单流 | 318.74 Mbps | 56.20 Mbps | 89.9% / 182.0% | 98.0 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | TCP | 单流 | 646.48 Mbps | 367.48 Mbps | 35.8% / 118.0% | 111.9 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | TCP | 单流 | 632.57 Mbps | 362.13 Mbps | 58.1% / 150.0% | 127.8 MB |
| **Hev (MTU 1500) [P=8]** | `hev` | 1500 | TCP | P=8 | 703.66 Mbps | 301.85 Mbps | 48.6% / 98.0% | 98.2 MB |
| **Xray TUN (MTU 1500) [P=8]** | `xray` | 1500 | TCP | P=8 | 358.52 Mbps | 157.32 Mbps | 110.6% / 229.0% | 98.9 MB |
| **SingTUN (MTU 1500) [P=8]** | `sing` | 1500 | TCP | P=8 | 218.33 Mbps | 397.99 Mbps | 65.9% / 227.0% | 117.5 MB |
| **MipsTUN (MTU 1500) [P=8]** | `mips` | 1500 | TCP | P=8 | 663.89 Mbps | 156.01 Mbps | 119.8% / 251.0% | 150.8 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | TCP | P=8 | 688.48 Mbps | 364.76 Mbps | 38.3% / 90.0% | 98.8 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | TCP | P=8 | 363.04 Mbps | 156.53 Mbps | 111.5% / 232.0% | 98.9 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | TCP | P=8 | 686.21 Mbps | 372.31 Mbps | 41.4% / 122.0% | 156.4 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | TCP | P=8 | 685.29 Mbps | 393.28 Mbps | 66.0% / 165.0% | 198.8 MB |
| **USB Baseline (No VPN) [UDP] [Single Stream]** | `direct_none` | 0 | UDP | 单流 | 199.81 Mbps | 200.01 Mbps | 0.8% / 5.0% | 92.0 MB |
| **USB Baseline (No VPN) [UDP] [P=8]** | `direct_none` | 0 | UDP | P=8 | 199.94 Mbps | 199.99 Mbps | 0.0% / 2.0% | 91.5 MB |
| **Hev (MTU 1500) [UDP] [Single Stream]** | `hev` | 1500 | UDP | 单流 | 199.74 Mbps | 153.26 Mbps (12.4% 丢包) | 158.9% / 193.0% | 106.2 MB |
| **Xray TUN (MTU 1500) [UDP] [Single Stream]** | `xray` | 1500 | UDP | 单流 | 199.70 Mbps | 99.01 Mbps (38.7% 丢包) | 95.0% / 207.0% | 101.5 MB |
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 198.09 Mbps (0.9% 丢包) | 117.06 Mbps (29.5% 丢包) | 190.2% / 236.0% | 111.5 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 148.51 Mbps (15.0% 丢包) | 71.11 Mbps (42.4% 丢包) | 189.4% / 225.0% | 124.0 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 199.88 Mbps | 200.72 Mbps | 135.5% / 203.0% | 104.3 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 199.79 Mbps | 200.19 Mbps | 77.3% / 202.0% | 102.4 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 199.92 Mbps | 200.50 Mbps | 202.2% / 238.0% | 111.7 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 185.51 Mbps (7.0% 丢包) | 199.29 Mbps | 187.2% / 227.0% | 128.0 MB |
#### 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU | 协议 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | TCP | 单流 | 386.05 Mbps | 380.80 Mbps | 0.0% / 0.0% | 99.5 MB |
| **Wi-Fi Baseline (No VPN) [P=8]** | `direct_none` | 0 | TCP | P=8 | 418.34 Mbps | 425.36 Mbps | 0.0% / 0.0% | 99.4 MB |
| **Hev (MTU 1500) [Single Stream]** | `hev` | 1500 | TCP | 单流 | 395.82 Mbps | 304.79 Mbps | 33.2% / 78.0% | 100.7 MB |
| **Xray TUN (MTU 1500) [Single Stream]** | `xray` | 1500 | TCP | 单流 | 379.66 Mbps | 60.50 Mbps | 50.3% / 171.0% | 100.7 MB |
| **SingTUN (MTU 1500) [Single Stream]** | `sing` | 1500 | TCP | 单流 | 397.20 Mbps | 167.23 Mbps | 32.7% / 141.0% | 109.1 MB |
| **MipsTUN (MTU 1500) [Single Stream]** | `mips` | 1500 | TCP | 单流 | 391.45 Mbps | 247.86 Mbps | 65.1% / 202.0% | — |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | TCP | 单流 | 393.12 Mbps | 373.35 Mbps | 17.7% / 78.0% | 97.1 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | TCP | 单流 | 246.97 Mbps | 61.96 Mbps | 83.2% / 177.0% | 97.9 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | TCP | 单流 | 394.03 Mbps | 279.41 Mbps | 27.8% / 103.0% | 111.2 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | TCP | 单流 | 397.32 Mbps | 397.26 Mbps | 35.6% / 128.0% | — |
| **Hev (MTU 1500) [P=8]** | `hev` | 1500 | TCP | P=8 | 420.59 Mbps | 300.59 Mbps | 25.9% / 97.0% | 98.5 MB |
| **Xray TUN (MTU 1500) [P=8]** | `xray` | 1500 | TCP | P=8 | 349.04 Mbps | 175.93 Mbps | 116.8% / 238.0% | 99.3 MB |
| **SingTUN (MTU 1500) [P=8]** | `sing` | 1500 | TCP | P=8 | 387.99 Mbps | 453.56 Mbps | 43.0% / 232.0% | — |
| **MipsTUN (MTU 1500) [P=8]** | `mips` | 1500 | TCP | P=8 | 422.61 Mbps | 138.55 Mbps | 45.4% / 174.0% | 152.6 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | TCP | P=8 | 424.31 Mbps | 450.68 Mbps | 17.5% / 96.0% | 97.6 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | TCP | P=8 | 359.05 Mbps | 182.37 Mbps | 118.3% / 245.0% | 98.3 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | TCP | P=8 | 422.73 Mbps | 454.03 Mbps | 27.2% / 114.0% | — |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | TCP | P=8 | 419.63 Mbps | 381.63 Mbps | 41.6% / 159.0% | 185.7 MB |
| **Wi-Fi Baseline (No VPN) [UDP] [Single Stream]** | `direct_none` | 0 | UDP | 单流 | 196.28 Mbps (1.7% 丢包) | 199.97 Mbps | 0.9% / 5.0% | 92.6 MB |
| **Wi-Fi Baseline (No VPN) [UDP] [P=8]** | `direct_none` | 0 | UDP | P=8 | 199.75 Mbps | 200.02 Mbps | 0.0% / 2.0% | 91.6 MB |
| **Hev (MTU 1500) [UDP] [Single Stream]** | `hev` | 1500 | UDP | 单流 | 151.46 Mbps (16.2% 丢包) | 127.36 Mbps (28.4% 丢包) | 155.3% / 180.0% | 104.7 MB |
| **Xray TUN (MTU 1500) [UDP] [Single Stream]** | `xray` | 1500 | UDP | 单流 | 127.15 Mbps (35.6% 丢包) | 105.33 Mbps (37.2% 丢包) | 129.9% / 212.0% | 103.5 MB |
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 159.59 Mbps (9.8% 丢包) | 122.41 Mbps (27.9% 丢包) | 181.2% / 222.0% | 117.2 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 144.88 Mbps (17.2% 丢包) | 121.69 Mbps (28.1% 丢包) | 187.4% / 230.0% | 134.1 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 199.69 Mbps | 200.23 Mbps | 128.8% / 197.0% | 106.5 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 196.39 Mbps (0.3% 丢包) | 199.76 Mbps | 80.3% / 220.0% | 102.3 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 199.66 Mbps | 200.03 Mbps | 195.4% / 238.0% | 115.1 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 166.03 Mbps | 199.83 Mbps (0.3% 丢包) | 201.9% / 230.0% | 130.1 MB |

#### 设备内部纯回环压力测试

| 测试用例 / 配置 | 后端协议栈 | MTU | 流模式 | 单流/多流吞吐 (Gbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Loopback Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | 单流 | 20.46 Gbps | 0.0% | 99.9 MB |
| **Loopback Baseline (No VPN) [P=8]** | `direct_none` | 0 | P=8 | 18.41 Gbps | 2.0% | 99.9 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | 单流 | 20.97 Gbps | 18.5% | 103.2 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | 单流 | 20.80 Gbps | 7.4% | 97.4 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | 单流 | 21.14 Gbps | 10.0% | 99.1 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | 单流 | 20.98 Gbps | 14.0% | 122.9 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | P=8 | 18.46 Gbps | 13.7% | 95.6 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | P=8 | 18.18 Gbps | 1.0% | 97.3 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | P=8 | 18.12 Gbps | 33.3% | 100.4 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | P=8 | 18.43 Gbps | 16.1% | 103.3 MB |

#### 空闲连接驻留与内存增长

| 后端协议栈 | 传输协议 | 连接范围 | 基准 PSS 内存 | 1000 连接 PSS 内存 | 内存增长斜率 |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **HEV** | TCP | 0 -> 1000 | 95.6 MB | 113.7 MB | 18.53 KiB/conn |
| **HEV** | UDP | 0 -> 1000 | 98.2 MB | 115.5 MB | 17.71 KiB/conn |
| **XRAY** | TCP | 0 -> 1000 | 98.4 MB | 100.9 MB | 2.56 KiB/conn |
| **XRAY** | UDP | 0 -> 1000 | 98.9 MB | 103.6 MB | 4.81 KiB/conn |
| **SING** | TCP | 0 -> 1000 | 103.3 MB | 149.3 MB | 47.10 KiB/conn |
| **SING** | UDP | 0 -> 1000 | 121.4 MB | 127.9 MB | 6.66 KiB/conn |
| **MIPS** | TCP | 0 -> 1000 | 103.6 MB | 155.5 MB | 53.15 KiB/conn |
| **MIPS** | UDP | 0 -> 1000 | 99.9 MB | 163.9 MB | 65.54 KiB/conn |
</details>

<br>

<details>
<summary><b>第 2 轮测试数据明细 (点击展开)</b></summary>

#### USB 3.2 Gen1 / 4.0 有线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU | 协议 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | TCP | 单流 | 681.51 Mbps | 381.64 Mbps | 0.8% / 4.0% | 92.1 MB |
| **USB Baseline (No VPN) [P=8]** | `direct_none` | 0 | TCP | P=8 | 748.75 Mbps | 427.67 Mbps | 0.0% / 2.0% | 91.5 MB |
| **Hev (MTU 1500) [Single Stream]** | `hev` | 1500 | TCP | 单流 | 634.67 Mbps | 299.98 Mbps | 48.7% / 84.0% | 100.4 MB |
| **Xray TUN (MTU 1500) [Single Stream]** | `xray` | 1500 | TCP | 单流 | 573.50 Mbps | 55.15 Mbps | 98.0% / 202.0% | 97.9 MB |
| **SingTUN (MTU 1500) [Single Stream]** | `sing` | 1500 | TCP | 单流 | 632.86 Mbps | 171.01 Mbps | 54.2% / 145.0% | 111.3 MB |
| **MipsTUN (MTU 1500) [Single Stream]** | `mips` | 1500 | TCP | 单流 | 595.64 Mbps | 236.55 Mbps | 113.1% / 210.0% | 127.1 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | TCP | 单流 | 648.14 Mbps | 357.31 Mbps | 38.2% / 70.0% | 97.3 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | TCP | 单流 | 322.51 Mbps | 63.22 Mbps | 93.4% / 193.0% | 97.9 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | TCP | 单流 | 333.84 Mbps | 356.27 Mbps | 27.3% / 114.0% | 111.4 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | TCP | 单流 | 353.20 Mbps | 285.81 Mbps | 42.3% / 150.0% | 124.9 MB |
| **Hev (MTU 1500) [P=8]** | `hev` | 1500 | TCP | P=8 | 325.38 Mbps | 302.05 Mbps | 35.9% / 89.0% | 98.2 MB |
| **Xray TUN (MTU 1500) [P=8]** | `xray` | 1500 | TCP | P=8 | 363.85 Mbps | 156.38 Mbps | 101.3% / 220.0% | 99.0 MB |
| **SingTUN (MTU 1500) [P=8]** | `sing` | 1500 | TCP | P=8 | 223.21 Mbps | 393.51 Mbps | 62.5% / 218.0% | 106.1 MB |
| **MipsTUN (MTU 1500) [P=8]** | `mips` | 1500 | TCP | P=8 | 344.44 Mbps | 161.88 Mbps | 94.3% / 192.0% | — |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | TCP | P=8 | 339.46 Mbps | 360.44 Mbps | 29.4% / 94.0% | 97.7 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | TCP | P=8 | 362.95 Mbps | 127.33 Mbps | 102.2% / 212.0% | 98.4 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | TCP | P=8 | 325.38 Mbps | 391.13 Mbps | 33.1% / 151.0% | 148.0 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | TCP | P=8 | 335.02 Mbps | 392.83 Mbps | 45.7% / 203.0% | 183.3 MB |
| **USB Baseline (No VPN) [UDP] [Single Stream]** | `direct_none` | 0 | UDP | 单流 | 199.96 Mbps | 200.02 Mbps | 0.7% / 3.7% | 92.9 MB |
| **USB Baseline (No VPN) [UDP] [P=8]** | `direct_none` | 0 | UDP | P=8 | 199.86 Mbps | 200.00 Mbps | 0.0% / 2.0% | 91.8 MB |
| **Hev (MTU 1500) [UDP] [Single Stream]** | `hev` | 1500 | UDP | 单流 | 199.40 Mbps | 120.99 Mbps (29.1% 丢包) | 159.6% / 191.0% | 106.6 MB |
| **Xray TUN (MTU 1500) [UDP] [Single Stream]** | `xray` | 1500 | UDP | 单流 | 199.76 Mbps | 99.68 Mbps (37.1% 丢包) | 94.7% / 205.0% | 101.4 MB |
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 140.11 Mbps (20.3% 丢包) | 85.41 Mbps (41.1% 丢包) | 185.9% / 233.0% | 0.0 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 198.00 Mbps (0.1% 丢包) | 121.75 Mbps (29.5% 丢包) | 196.4% / 248.0% | 114.7 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 199.92 Mbps | 200.13 Mbps | 135.1% / 204.0% | 104.9 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 199.57 Mbps | 200.09 Mbps | 77.2% / 204.0% | 101.8 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 195.69 Mbps | 199.55 Mbps | 198.4% / 227.0% | 131.3 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 199.92 Mbps | 191.72 Mbps (4.1% 丢包) | 205.9% / 244.0% | 121.2 MB |
#### 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU | 协议 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | TCP | 单流 | 396.48 Mbps | 312.23 Mbps | 0.4% / 4.0% | 100.7 MB |
| **Wi-Fi Baseline (No VPN) [P=8]** | `direct_none` | 0 | TCP | P=8 | 350.63 Mbps | 365.86 Mbps | 0.0% / 0.0% | 100.6 MB |
| **Hev (MTU 1500) [Single Stream]** | `hev` | 1500 | TCP | 单流 | 311.02 Mbps | 249.64 Mbps | 21.8% / 69.0% | 103.8 MB |
| **Xray TUN (MTU 1500) [Single Stream]** | `xray` | 1500 | TCP | 单流 | 246.56 Mbps | 66.16 Mbps | 80.4% / 173.0% | 103.9 MB |
| **SingTUN (MTU 1500) [Single Stream]** | `sing` | 1500 | TCP | 单流 | 323.57 Mbps | 173.51 Mbps | 24.1% / 144.0% | 109.4 MB |
| **MipsTUN (MTU 1500) [Single Stream]** | `mips` | 1500 | TCP | 单流 | 321.29 Mbps | 235.90 Mbps | 45.0% / 200.0% | 136.6 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | TCP | 单流 | 350.53 Mbps | 349.45 Mbps | 16.9% / 71.0% | 97.2 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | TCP | 单流 | 249.79 Mbps | 64.06 Mbps | 84.7% / 196.0% | 98.0 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | TCP | 单流 | 375.18 Mbps | 275.65 Mbps | 26.3% / 92.5% | 110.8 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | TCP | 单流 | 289.05 Mbps | 309.92 Mbps | 32.7% / 122.0% | 126.5 MB |
| **Hev (MTU 1500) [P=8]** | `hev` | 1500 | TCP | P=8 | 311.54 Mbps | 298.18 Mbps | 21.8% / 91.0% | 98.4 MB |
| **Xray TUN (MTU 1500) [P=8]** | `xray` | 1500 | TCP | P=8 | 312.72 Mbps | 133.84 Mbps | 103.1% / 239.0% | 98.8 MB |
| **SingTUN (MTU 1500) [P=8]** | `sing` | 1500 | TCP | P=8 | 300.67 Mbps | 405.02 Mbps | 53.7% / 220.0% | 155.3 MB |
| **MipsTUN (MTU 1500) [P=8]** | `mips` | 1500 | TCP | P=8 | 344.30 Mbps | 144.33 Mbps | 51.8% / 185.0% | 190.0 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | TCP | P=8 | 331.39 Mbps | 371.73 Mbps | 14.5% / 88.0% | 191.4 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | TCP | P=8 | 288.25 Mbps | 178.14 Mbps | 87.5% / 244.0% | 190.4 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | TCP | P=8 | 270.95 Mbps | 347.20 Mbps | 24.6% / 118.0% | 191.0 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | TCP | P=8 | 277.31 Mbps | 372.36 Mbps | 30.0% / 200.0% | 155.6 MB |
| **Wi-Fi Baseline (No VPN) [UDP] [Single Stream]** | `direct_none` | 0 | UDP | 单流 | 194.86 Mbps (1.4% 丢包) | 199.97 Mbps | 0.8% / 4.0% | 92.9 MB |
| **Wi-Fi Baseline (No VPN) [UDP] [P=8]** | `direct_none` | 0 | UDP | P=8 | 199.17 Mbps | 199.98 Mbps | 0.0% / 3.0% | 91.8 MB |
| **Hev (MTU 1500) [UDP] [Single Stream]** | `hev` | 1500 | UDP | 单流 | 145.53 Mbps (19.2% 丢包) | 120.15 Mbps (30.8% 丢包) | 153.6% / 185.0% | 105.8 MB |
| **Xray TUN (MTU 1500) [UDP] [Single Stream]** | `xray` | 1500 | UDP | 单流 | 168.95 Mbps (14.7% 丢包) | 155.26 Mbps (14.8% 丢包) | 117.9% / 223.0% | 102.7 MB |
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 150.13 Mbps (15.3% 丢包) | 124.79 Mbps (30.3% 丢包) | 182.1% / 226.0% | 117.7 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 177.08 Mbps (2.7% 丢包) | 128.22 Mbps (26.7% 丢包) | 175.9% / 227.0% | 0.0 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 174.09 Mbps | 200.02 Mbps | 135.2% / 220.0% | 114.8 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 198.43 Mbps (0.5% 丢包) | 199.46 Mbps | 79.8% / 214.0% | 110.8 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 199.43 Mbps (0.1% 丢包) | 200.00 Mbps | 192.9% / 222.0% | 115.0 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 199.73 Mbps | 197.75 Mbps (1.4% 丢包) | 197.1% / 228.0% | 0.0 MB |

#### 设备内部纯回环压力测试

| 测试用例 / 配置 | 后端协议栈 | MTU | 流模式 | 单流/多流吞吐 (Gbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Loopback Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | 单流 | 21.01 Gbps | 0.0% | 107.3 MB |
| **Loopback Baseline (No VPN) [P=8]** | `direct_none` | 0 | P=8 | 18.36 Gbps | 0.0% | 107.3 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | 单流 | 20.65 Gbps | 7.4% | 108.1 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | 单流 | 20.62 Gbps | 1.0% | 108.1 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | 单流 | 20.63 Gbps | 7.4% | 108.8 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | 单流 | 20.86 Gbps | 10.7% | 113.9 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | P=8 | 18.31 Gbps | 20.6% | 95.8 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | P=8 | 17.88 Gbps | 3.0% | 96.8 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | P=8 | 18.45 Gbps | 3.7% | 97.5 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | P=8 | 18.53 Gbps | 3.5% | 101.3 MB |


#### 空闲连接驻留与内存增长

| 后端协议栈 | 传输协议 | 连接范围 | 基准 PSS 内存 | 1000 连接 PSS 内存 | 内存增长斜率 |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **HEV** | TCP | 0 -> 1000 | 98.3 MB | 114.1 MB | 16.18 KiB/conn |
| **HEV** | UDP | 0 -> 1000 | 120.1 MB | 114.9 MB | -5.33 KiB/conn |
| **XRAY** | TCP | 0 -> 1000 | 98.2 MB | 100.8 MB | 2.66 KiB/conn |
| **XRAY** | UDP | 0 -> 1000 | 98.8 MB | 103.7 MB | 5.02 KiB/conn |
| **SING** | TCP | 0 -> 1000 | 102.9 MB | 151.4 MB | 49.66 KiB/conn |
| **SING** | UDP | 0 -> 1000 | 99.2 MB | 123.4 MB | 24.78 KiB/conn |
| **MIPS** | TCP | 0 -> 1000 | 123.7 MB | 155.7 MB | 32.77 KiB/conn |
| **MIPS** | UDP | 0 -> 1000 | 101.5 MB | 160.8 MB | 60.72 KiB/conn |
</details>

<br>

<details>
<summary><b>第 3 轮测试数据明细 (点击展开)</b></summary>

#### USB 3.2 Gen1 / 4.0 有线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU | 协议 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | TCP | 单流 | 692.00 Mbps | 381.48 Mbps | 0.8% / 4.0% | 92.2 MB |
| **USB Baseline (No VPN) [P=8]** | `direct_none` | 0 | TCP | P=8 | 758.48 Mbps | 424.92 Mbps | 0.0% / 2.5% | 91.7 MB |
| **Hev (MTU 1500) [Single Stream]** | `hev` | 1500 | TCP | 单流 | 638.39 Mbps | 303.01 Mbps | 48.5% / 83.0% | 100.5 MB |
| **Xray TUN (MTU 1500) [Single Stream]** | `xray` | 1500 | TCP | 单流 | 445.62 Mbps | 58.13 Mbps | 96.1% / 199.0% | 98.0 MB |
| **SingTUN (MTU 1500) [Single Stream]** | `sing` | 1500 | TCP | 单流 | 643.88 Mbps | 177.25 Mbps | 54.6% / 144.0% | 111.5 MB |
| **MipsTUN (MTU 1500) [Single Stream]** | `mips` | 1500 | TCP | 单流 | 608.62 Mbps | 232.40 Mbps | 111.8% / 205.0% | 129.8 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | TCP | 单流 | 647.39 Mbps | 358.30 Mbps | 38.6% / 70.0% | 97.3 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | TCP | 单流 | 320.62 Mbps | 59.71 Mbps | 91.7% / 187.5% | 98.0 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | TCP | 单流 | 490.16 Mbps | 361.88 Mbps | 31.6% / 116.0% | 111.7 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | TCP | 单流 | 492.88 Mbps | 323.97 Mbps | 50.2% / 150.0% | 126.3 MB |
| **Hev (MTU 1500) [P=8]** | `hev` | 1500 | TCP | P=8 | 514.52 Mbps | 301.95 Mbps | 42.2% / 93.5% | 98.2 MB |
| **Xray TUN (MTU 1500) [P=8]** | `xray` | 1500 | TCP | P=8 | 361.19 Mbps | 156.85 Mbps | 106.0% / 224.5% | 99.0 MB |
| **SingTUN (MTU 1500) [P=8]** | `sing` | 1500 | TCP | P=8 | 220.77 Mbps | 395.75 Mbps | 64.2% / 222.5% | 111.8 MB |
| **MipsTUN (MTU 1500) [P=8]** | `mips` | 1500 | TCP | P=8 | 504.16 Mbps | 158.94 Mbps | 107.0% / 221.5% | 150.8 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | TCP | P=8 | 513.97 Mbps | 362.60 Mbps | 33.9% / 92.0% | 98.2 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | TCP | P=8 | 363.00 Mbps | 141.93 Mbps | 106.8% / 222.0% | 98.7 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | TCP | P=8 | 505.80 Mbps | 381.72 Mbps | 37.2% / 136.5% | 152.2 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | TCP | P=8 | 510.15 Mbps | 393.05 Mbps | 55.9% / 184.0% | 191.1 MB |
| **USB Baseline (No VPN) [UDP] [Single Stream]** | `direct_none` | 0 | UDP | 单流 | 199.88 Mbps | 200.01 Mbps | 0.8% / 4.3% | 92.5 MB |
| **USB Baseline (No VPN) [UDP] [P=8]** | `direct_none` | 0 | UDP | P=8 | 199.90 Mbps | 200.00 Mbps | 0.0% / 2.0% | 91.7 MB |
| **Hev (MTU 1500) [UDP] [Single Stream]** | `hev` | 1500 | UDP | 单流 | 199.57 Mbps | 137.12 Mbps (20.8% 丢包) | 159.2% / 192.0% | 106.4 MB |
| **Xray TUN (MTU 1500) [UDP] [Single Stream]** | `xray` | 1500 | UDP | 单流 | 199.73 Mbps | 99.34 Mbps (37.9% 丢包) | 94.8% / 206.0% | 101.5 MB |
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 198.11 Mbps (0.9% 丢包) | 118.78 Mbps (31.5% 丢包) | 190.8% / 240.0% | 112.6 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 198.58 Mbps | 130.73 Mbps (26.8% 丢包) | 193.5% / 222.0% | 114.6 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 199.90 Mbps | 200.43 Mbps | 135.3% / 203.5% | 104.6 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 199.68 Mbps | 200.14 Mbps | 77.2% / 203.0% | 102.1 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 199.90 Mbps | 200.04 Mbps | 203.2% / 238.0% | 64.4 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 199.93 Mbps | 193.66 Mbps (3.1% 丢包) | 204.5% / 246.0% | 73.7 MB |
#### 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU | 协议 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | TCP | 单流 | 391.26 Mbps | 346.51 Mbps | 0.4% / 4.0% | 100.1 MB |
| **Wi-Fi Baseline (No VPN) [P=8]** | `direct_none` | 0 | TCP | P=8 | 384.49 Mbps | 395.61 Mbps | 0.0% / 0.0% | 100.0 MB |
| **Hev (MTU 1500) [Single Stream]** | `hev` | 1500 | TCP | 单流 | 353.42 Mbps | 277.22 Mbps | 27.5% / 73.5% | 102.2 MB |
| **Xray TUN (MTU 1500) [Single Stream]** | `xray` | 1500 | TCP | 单流 | 313.11 Mbps | 63.33 Mbps | 65.3% / 172.0% | 102.3 MB |
| **SingTUN (MTU 1500) [Single Stream]** | `sing` | 1500 | TCP | 单流 | 360.38 Mbps | 170.37 Mbps | 28.4% / 142.5% | 109.2 MB |
| **MipsTUN (MTU 1500) [Single Stream]** | `mips` | 1500 | TCP | 单流 | 356.37 Mbps | 241.88 Mbps | 55.0% / 201.0% | 136.6 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | TCP | 单流 | 371.82 Mbps | 361.40 Mbps | 17.3% / 74.5% | 97.2 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | TCP | 单流 | 248.38 Mbps | 63.01 Mbps | 84.0% / 186.5% | 98.0 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | TCP | 单流 | 384.61 Mbps | 277.53 Mbps | 27.1% / 97.8% | 111.0 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | TCP | 单流 | 343.19 Mbps | 353.59 Mbps | 34.1% / 125.0% | 126.5 MB |
| **Hev (MTU 1500) [P=8]** | `hev` | 1500 | TCP | P=8 | 366.06 Mbps | 299.38 Mbps | 23.9% / 94.0% | 98.5 MB |
| **Xray TUN (MTU 1500) [P=8]** | `xray` | 1500 | TCP | P=8 | 330.88 Mbps | 154.88 Mbps | 110.0% / 238.5% | 99.0 MB |
| **SingTUN (MTU 1500) [P=8]** | `sing` | 1500 | TCP | P=8 | 344.33 Mbps | 429.29 Mbps | 48.4% / 226.0% | 155.3 MB |
| **MipsTUN (MTU 1500) [P=8]** | `mips` | 1500 | TCP | P=8 | 383.46 Mbps | 141.44 Mbps | 48.6% / 179.5% | 171.3 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | TCP | P=8 | 377.85 Mbps | 411.21 Mbps | 16.0% / 92.0% | 144.5 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | TCP | P=8 | 323.65 Mbps | 180.25 Mbps | 102.9% / 244.5% | 144.3 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | TCP | P=8 | 346.84 Mbps | 400.62 Mbps | 25.9% / 116.0% | 191.0 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | TCP | P=8 | 348.47 Mbps | 377.00 Mbps | 35.8% / 179.5% | 170.7 MB |
| **Wi-Fi Baseline (No VPN) [UDP] [Single Stream]** | `direct_none` | 0 | UDP | 单流 | 195.57 Mbps (1.6% 丢包) | 199.97 Mbps | 0.8% / 4.5% | 92.8 MB |
| **Wi-Fi Baseline (No VPN) [UDP] [P=8]** | `direct_none` | 0 | UDP | P=8 | 199.46 Mbps | 200.00 Mbps | 0.0% / 2.5% | 91.7 MB |
| **Hev (MTU 1500) [UDP] [Single Stream]** | `hev` | 1500 | UDP | 单流 | 148.50 Mbps (17.7% 丢包) | 123.75 Mbps (29.6% 丢包) | 154.4% / 182.5% | 105.2 MB |
| **Xray TUN (MTU 1500) [UDP] [Single Stream]** | `xray` | 1500 | UDP | 单流 | 148.05 Mbps (25.2% 丢包) | 130.29 Mbps (26.0% 丢包) | 123.9% / 217.5% | 103.1 MB |
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 142.91 Mbps (19.9% 丢包) | 123.68 Mbps (24.9% 丢包) | 181.4% / 227.0% | 113.6 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 144.38 Mbps (18.5% 丢包) | 122.19 Mbps (30.6% 丢包) | 185.4% / 226.0% | 116.3 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 186.89 Mbps | 200.12 Mbps | 132.0% / 208.5% | 110.7 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 197.41 Mbps (0.4% 丢包) | 199.61 Mbps | 80.0% / 217.0% | 106.5 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 199.66 Mbps | 200.16 Mbps | 195.9% / 226.0% | 115.2 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 199.33 Mbps (0.2% 丢包) | 197.91 Mbps (1.0% 丢包) | 192.3% / 238.0% | 116.3 MB |
#### 设备内部纯回环压力测试

| 测试用例 / 配置 | 后端协议栈 | MTU | 流模式 | 单流/多流吞吐 (Gbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Loopback Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | 单流 | 20.74 Gbps | 0.0% | 103.6 MB |
| **Loopback Baseline (No VPN) [P=8]** | `direct_none` | 0 | P=8 | 18.39 Gbps | 2.0% | 103.6 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | 单流 | 20.81 Gbps | 12.9% | 105.7 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | 单流 | 20.71 Gbps | 4.2% | 102.8 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | 单流 | 20.89 Gbps | 8.7% | 104.0 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | 单流 | 20.92 Gbps | 12.3% | 118.4 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | P=8 | 18.38 Gbps | 17.1% | 95.7 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | P=8 | 18.03 Gbps | 2.0% | 97.0 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | P=8 | 18.29 Gbps | 18.5% | 99.0 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | P=8 | 18.48 Gbps | 9.8% | 102.3 MB |

#### 空闲连接驻留与内存增长

| 后端协议栈 | 传输协议 | 连接范围 | 基准 PSS 内存 | 1000 连接 PSS 内存 | 内存增长斜率 |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **HEV** | TCP | 0 -> 1000 | 97.6 MB | 114.1 MB | 16.90 KiB/conn |
| **HEV** | UDP | 0 -> 1000 | 99.2 MB | 115.9 MB | 17.10 KiB/conn |
| **XRAY** | TCP | 0 -> 1000 | 99.5 MB | 100.7 MB | 1.23 KiB/conn |
| **XRAY** | UDP | 0 -> 1000 | 99.8 MB | 99.7 MB | -0.10 KiB/conn |
| **SING** | TCP | 0 -> 1000 | 104.6 MB | 150.2 MB | 46.69 KiB/conn |
| **SING** | UDP | 0 -> 1000 | 100.9 MB | 122.1 MB | 21.71 KiB/conn |
| **MIPS** | TCP | 0 -> 1000 | 104.1 MB | 156.0 MB | 53.15 KiB/conn |
| **MIPS** | UDP | 0 -> 1000 | 102.0 MB | 162.7 MB | 62.16 KiB/conn |
</details>

<br>

---

## 4. 纯底层协议栈独立微基准测试

### 4.1 隔离设计

为区分 Android 设备端到端测试中**协议栈自身开销**与 **Android 系统/JNI/ART 运行时开销**的边界，并还原多进程架构下的协议栈真实占用，设计并实施了Scheme 2，在 Linux 宿主上以无特权用户命名空间隔离运行各协议栈，通过统一的 Go SOCKS5 Sink 服务模拟 0 → 1000 连接阶梯驻留，直接采集 PSS。该方案脱离 Android Framework、ART 虚拟机与温控调频调度，仅测量协议栈本身的内存开销。

### 4.2 微基准实测数据对比 (3 轮平均)

| 后端协议栈 | 传输协议 | 基础 PSS | 1000 连接 PSS | 纯底层增长斜率 (Scheme 2) | Android 端到端斜率 (Scheme 1) | 层级差值与说明 |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **`hev-socks5-tunnel`** (C/lwIP) | TCP | 2.13 MB | 14.43 MB | **12.59 KiB/conn** | 17.20 KiB/conn | +4.61 KiB/conn（JNI 交互与 Android 状态管理） |
| **`hev-socks5-tunnel`** (C/lwIP) | UDP | 2.14 MB | 18.33 MB | **16.58 KiB/conn** | 9.83 KiB/conn | -6.75 KiB/conn（底层打流为持续双向 Session） |
| **`SingTUN`** (Go/sing-box) | TCP | 8.87 MB | 57.73 MB | **50.01 KiB/conn** | 47.82 KiB/conn | 约 -2.19 KiB/conn（Go GC 周期内正常浮动） |
| **`SingTUN`** (Go/sing-box) | UDP | 9.39 MB | 54.26 MB | **45.94 KiB/conn** | 17.72 KiB/conn | -28.22 KiB/conn（Android 端 UDP 采用轻量映射表） |
| **`MipsTUN`** (Go/BBRv3) | TCP | 13.91 MB | 63.78 MB | **51.05 KiB/conn** | 46.35 KiB/conn | 约 -4.70 KiB/conn（Go GC 周期内正常浮动） |
| **`MipsTUN`** (Go/BBRv3) | UDP | 14.54 MB | 90.96 MB | **78.23 KiB/conn** | 62.81 KiB/conn | -15.42 KiB/conn（连接块与环形缓冲区分配） |
| **`Xray TUN`** (Go/gVisor) | TCP | 17.75 MB | 80.50 MB | **64.24 KiB/conn** | 2.15 KiB/conn* | Android 主进程仅传递 fd，子进程承载真实开销 |
| **`Xray TUN`** (Go/gVisor) | UDP | 18.25 MB | 70.78 MB | **53.77 KiB/conn** | 3.24 KiB/conn* | Android 主进程仅传递 fd，子进程承载真实开销 |

> 注：Xray TUN 仅统计了 SimpleXray 主进程的 PSS 增长，未覆盖独立 Fork 运行的 Xray 守护子进程。

### 4.3 全栈损耗归因

![Full-Stack Attribution Dashboard](../images/fullstack_attribution_dashboard.webp)

### 4.4 层级归因

**JNI 进程内直驱模型（Hev / SingTUN / MipsTUN）**

lwIP 协议栈以纯 C 实现，PCB 开销仅为 12.59 KiB/conn（TCP）与 16.58 KiB/conn（UDP）。引入 Android 宿主后，加上 JNI 封装与应用内状态管理，TCP 斜率上升至 17.20 KiB/conn，层级附加开销约 4.6 KiB。

SingTUN 与 MipsTUN 的纯协议栈开销均在 46 ~ 78 KiB/conn 区间，核心开销来自 Go 运行时的 goroutine 初始栈、Channel 缓冲区及 Ring Buffer。

两种测试中 TCP 斜率吻合，差值在 Go GC 周期内的正常波动范围内。
  - SingTUN: 50.01 KiB vs 47.82 KiB；
  - MipsTUN: 51.05 KiB vs 46.35 KiB

**多进程隔离模型（Xray Native TUN）**

Android 端测试中 Xray Native TUN 主进程 PSS 增长极低（TCP 2.15 KiB/conn，UDP 3.24 KiB/conn）

原因在于 SimpleXray 的工程集成采用了跨进程模式

`TProxyService` 通过 JNI Fork 出独立的 Xray 守护子进程，并将 `VpnService` 的 fd 注入给子进程。主进程仅持有轻量的 IPC 句柄与代理上下文。

Scheme 2 独立测量了 Xray 核心内部 gVisor netstack 的完整开销，实际连接控制块与 buffer 开销为 64.24 KiB/conn（TCP）与 53.77 KiB/conn（UDP），在四种协议栈中属于偏高一档。多进程部署架构将这部分内存压力完全隔离在子进程内部。

---

## 5. 数据可视化

### 5.1 综合平均可视化 (3 轮平均)

#### (1) 全栈损耗归因全景
![Full-Stack Attribution Dashboard](../images/fullstack_attribution_dashboard.webp)

#### (2) 5GHz Wi-Fi 无线吞吐量
![5GHz Wi-Fi Throughput Dashboard](../images/avg_wifi_throughput_dashboard.webp)

#### (3) USB 3.2 / 4.0 有线吞吐量
![USB 3.2 Throughput Dashboard](../images/avg_usb_throughput_dashboard.webp)

#### (4) 核心 CPU 传输能效比
![CPU Efficiency Dashboard](../images/avg_cpu_efficiency_dashboard.webp)

#### (5) 设备内部协议处理纯回环
![Loopback Dashboard](../images/avg_loopback_dashboard.webp)

#### (6) 空闲连接驻留与内存增长
![Idle Memory Dashboard](../images/avg_idle_memory_dashboard.webp)

---

### 5.2 分轮可视化明细

<details>
<summary><b>第 1 轮测试可视化 (点击展开)</b></summary>

![Round 1 Wi-Fi](../images/r1_wifi_throughput_dashboard.webp)
![Round 1 USB](../images/r1_usb_throughput_dashboard.webp)
![Round 1 Efficiency](../images/r1_cpu_efficiency_dashboard.webp)
![Round 1 Loopback](../images/r1_loopback_dashboard.webp)
![Round 1 Idle](../images/r1_idle_memory_dashboard.webp)

</details>

<br>

<details>
<summary><b>第 2 轮测试可视化 (点击展开)</b></summary>

![Round 2 Wi-Fi](../images/r2_wifi_throughput_dashboard.webp)
![Round 2 USB](../images/r2_usb_throughput_dashboard.webp)
![Round 2 Efficiency](../images/r2_cpu_efficiency_dashboard.webp)
![Round 2 Loopback](../images/r2_loopback_dashboard.webp)
![Round 2 Idle](../images/r2_idle_memory_dashboard.webp)

</details>

<br>

<details>
<summary><b>第 3 轮测试可视化 (点击展开)</b></summary>

![Round 3 Wi-Fi](../images/r3_wifi_throughput_dashboard.webp)
![Round 3 USB](../images/r3_usb_throughput_dashboard.webp)
![Round 3 Efficiency](../images/r3_cpu_efficiency_dashboard.webp)
![Round 3 Loopback](../images/r3_loopback_dashboard.webp)
![Round 3 Idle](../images/r3_idle_memory_dashboard.webp)

</details>

---

## 6. 分析

### 6.1 `hev-socks5-tunnel` (C / lwIP)

**吞吐量**

在 MTU 9000 下，5GHz Wi-Fi 多流下行达到 411.21 Mbps，USB 有线单流上行达 647.39 Mbps，多流 513.97 Mbps；标准 MTU 1500 单流下行维持在 277.22 ~ 303.01 Mbps。

USB 有线 8 流 UDP 可达 200 Mbps 目标限速（0% 丢包）；Wi-Fi 环境下上行约 186.89 Mbps，下行约 200.12 Mbps；单流 UDP 下行在 123.75 ~ 137.12 Mbps（丢包率 20.8% ~ 29.6%）。

**资源特性**

纯 C 实现配合精简 lwIP，TCP 测试平均 CPU 在 16.0% ~ 48.5%，能效比在四款协议栈中最高。空闲基准 PSS 为 97.2 MB（TCP）/ 105.8 MB（UDP），1000 连接后为 114.0 MB / 115.4 MB；TCP 增长斜率 17.20 KiB/conn，UDP 9.83 KiB/conn；独立微基准纯底层 TCP 仅 12.59 KiB/conn。

**局限性**

lwIP 单线程事件循环在 MTU 1500 多流下行并发时吞吐收窄至约 300 Mbps，未能充分利用多核算力。启动时需向文件系统写入临时配置文件 `tproxy.conf`，增加一次文件 I/O。

---

### 6.2 `SingTUN` (Go / sing-box)

**吞吐量**

多流下行并发场景优于 Hev，单流及上行场景 Hev 能效更优。MTU 1500 下 8 并发 TCP 下行达 429.29 Mbps（Wi-Fi）与 395.75 Mbps（USB）。8 流 UDP 上下行在 Wi-Fi 与 USB 下均接近 200 Mbps 目标限速（198.50 ~ 200.06 Mbps）；单流 UDP 上行测得 150.88 ~ 178.77 Mbps。

**资源特性**

通过 JNI 直接接管 `VpnService` fd，无临时配置文件。空闲基准 PSS 为 107.2 MB，1000 连接后为 124.5 MB；UDP 内存增长斜率 17.72 KiB/conn。

**局限性**

Go 运行时调度开销导致高并发时 CPU 峰值达 220% ~ 238%，能效比低于纯 C 实现。1000 个 TCP 连接驻留使 PSS 从 103.6 MB 升至 150.3 MB，增长斜率 47.82 KiB/conn（与独立微基准 50.01 KiB/conn 吻合）。

---

### 6.3 `MipsTUN` (Go / Mihomo mipstack)

**吞吐量**

USB 有线环境下 MTU 1500 单流上行达 608.62 Mbps，MTU 9000 多流下行达 393.05 Mbps。内置 BBRv3 拥塞控制，MTU 9000 下 Wi-Fi 单流上行 343.19 Mbps，下行 353.59 Mbps。8 流 UDP 下行测得 194.89 ~ 198.50 Mbps，上行达到 188.36 ~ 195.12 Mbps。单流回环约 20.92 Gbps，与其他后端处于同一水平。

**资源特性**

完全基于非阻塞 epoll/netpoller 实现，无外部 C 依赖。

**局限性**

MTU 1500 单流场景平均 CPU 开销为 55.0% ~ 111.8%，峰值突破 200%；建议在支持大帧的环境下启用大 MTU。1000 连接下 TCP PSS 升至 155.7 MB（斜率 46.35 KiB/conn），UDP PSS 升至 162.5 MB（斜率 62.81 KiB/conn）；独立微基准纯底层 UDP 斜率达 78.23 KiB/conn，在四种后端中最高。

---

### 6.4 `Xray Native TUN` (Go / gVisor)

**吞吐量**

单流下行在 Wi-Fi 与 USB 环境下均处于 58.13 ~ 63.33 Mbps，多流并发下行维持在 141.93 ~ 180.25 Mbps，下行吞吐偏低。上行单流可达 248.38 ~ 445.62 Mbps，内部回环单流达 20.71 Gbps，说明协议转换通路本身无明显瓶颈，下行收窄主要受 gVisor 单核事件循环与内部 buffer 拷贝调度制约。8 流 UDP 上下行均达到 197.41 ~ 200.14 Mbps。

**资源特性**

SimpleXray 主进程通过 JNI Fork 出 Xray 守护子进程并传递 fd，主进程测得的连接增长斜率仅为 2.15 KiB/conn（TCP）与 3.24 KiB/conn（UDP）。Scheme 2 独立测量了 Xray 核心内部 gVisor netstack 的实际开销为 64.24 KiB/conn（TCP）与 53.77 KiB/conn（UDP），多进程架构将这部分压力隔离在子进程内部。

**局限性**

下行约 60 Mbps 时平均 CPU 占用仍达 65.4% ~ 96.1%，峰值约 172% ~ 199%，单位吞吐算力消耗偏高。

---

## 7. 选择参考

| 协议栈后端 | 适用场景 | 优势 | 代价 |
| :--- | :--- | :--- | :--- |
| **`hev-socks5-tunnel`** | 日常轻量续航、低功耗保活、发热敏感环境 | CPU 占用低、能效比高、纯底层开销仅 12.59 KiB/conn | MTU 1500 多流下行受限、启动需写入临时配置文件 |
| **`SingTUN`** | 高速无线 Wi-Fi、大带宽流媒体、多流并发下载 | 多流下行并发强、UDP 转发稳定、无临时文件 I/O | 高并发 CPU 瞬时占用高、能效比低于 Hev |
| **`MipsTUN`** | 有线网络、局域网大吞吐、巨型帧环境 | 有线上行吞吐高、内置 BBRv3、纯 Go 架构无 C 依赖 | 基础内存占用偏高、标准 MTU 下 CPU 负载较重 |
| **`Xray Native TUN`** | 主进程内存隔离、进程级防护、原生核心直驱 | 主进程内存增长极低（2.15 KiB/conn），子进程隔离 64.24 KiB | 单流及多流下行吞吐受限、下行能效比低 |

---

## 8. 原始数据

- Android 端到端实测原始数据集：[benchmark_results.json](./benchmark_results.json)
- 纯底层协议栈独立微基准原始数据集：[microbench_results.json](./microbench_results.json)

