# SimpleXray Android TUN 性能基准测试报告

本文档记录对 SimpleXray 支持的四种透明代理 TUN 协议栈实现 `hev-socks5-tunnel`、`Xray Native TUN`、`SingTUN` 与 `MipsTUN` (Mihomo mipstack)，在 **Throughput**、**CPU Usage**、**Efficiency** 以及 **Idle Memory Growth** 维度进行实测与对比分析。

---

## 1. 测试环境

### 测试主机（iPerf3 Server / PC）
- **操作系统**: Linux x86_64 (Linux 6.12.107+deb13-amd64)
- **处理器 (CPU)**: AMD Ryzen 7 6800H @ 3.2GHz (8 核 16 线程)
- **无线网卡**: Intel(R) Wi-Fi 6E AX210 160MHz
- **有线接口**: USB 3.2 Gen1 / 4.0 40Gbps 全功能数据线 (RNDIS USB 网络共享虚拟以太网，链路速率 1Gbps)
- **iPerf3 版本**: 3.18 (cJSON 1.7.15)

### Android 测试设备（iPerf3 Client / DUT）
- **操作系统**: Android 14 (Linux 5.4 内核)
- **处理器 (SoC)**: 高通骁龙 778G (Octa-Core: 4x2.4GHz + 4x1.8GHz Kryo 670)
- **有线接口**: Type-C USB 3.2 Gen1 (RNDIS USB 网络共享)
- **无线规格**: 802.11 a/b/g/n/ac/ax 2.4G+5GHz, HE80, MIMO
- **iPerf3 版本**: 3.21 (aarch64 静态编译版)

### 局域网网关
- **网关设备**: 高通第五代骁龙 8 至尊版移动平台设备（Android 16，FastConnect 7900 无线连接系统）
- **网络频段**: 5GHz Wi-Fi 热点 (WLAN AP，80MHz 频宽，物理协商速率 1201 Mbps)

---

## 2. 测试链路

测试采用 **局域网 Direct/Freedom 纯透明代理** 链路，流量经由 Android 系统 `VpnService` 虚拟网卡由各 TUN 协议栈处理并直连 PC 出站：

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
       (C/lwIP)      (gVisor-tun)     (BBRv3)    (Go/gVisor 协议栈)
           │              │            │          │     │
      Xray SOCKS5    Xray SOCKS5  Xray SOCKS5     │     │
        Inbound        Inbound      Inbound       │     │
           │              │            │          │     │
           └──────────────┴────────────┴──────────┘     │
                               │                        │
                               ▼                        │
                      Xray Freedom Outbound ────────────┘
```

---

## 3. 测试数据


> [数据可视化请看下一章](#4-数据可视化)

### 3.1 综合平均测试数据 (3 轮平均)

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
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 198.09 Mbps (0.9% 丢包) | 117.06 Mbps (29.5% 丢包) | 190.2% / 236.0% | 111.5 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 148.51 Mbps (15.0% 丢包) | 71.11 Mbps (42.4% 丢包) | 189.4% / 225.0% | 124.0 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 199.90 Mbps | 200.43 Mbps | 135.3% / 203.5% | 104.6 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 199.68 Mbps | 200.14 Mbps | 77.2% / 203.0% | 102.1 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 199.92 Mbps | 200.50 Mbps | 202.2% / 238.0% | 111.7 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 185.51 Mbps (7.0% 丢包) | 199.29 Mbps | 187.2% / 227.0% | 128.0 MB |
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
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 159.59 Mbps (9.8% 丢包) | 122.41 Mbps (27.9% 丢包) | 181.2% / 222.0% | 117.2 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 144.88 Mbps (17.1% 丢包) | 121.69 Mbps (28.1% 丢包) | 187.4% / 230.0% | 134.1 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 186.89 Mbps | 200.12 Mbps | 132.0% / 208.5% | 110.7 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 197.41 Mbps (0.4% 丢包) | 199.61 Mbps | 80.0% / 217.0% | 106.5 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 199.66 Mbps | 200.03 Mbps | 195.4% / 238.0% | 115.1 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 166.03 Mbps | 199.83 Mbps (0.2% 丢包) | 201.9% / 230.0% | 130.1 MB |
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
#### 3.1. 空闲连接驻留与内存增长 (TCP & UDP)
| 后端协议栈 | 传输协议 | 连接范围 | 基准 PSS 内存 | 1000 连接 PSS 内存 | 内存增长斜率 |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **HEV** | TCP | 0 -> 1000 | 99.1 MB | 115.8 MB | 17.68 KiB/conn |
| **HEV** | UDP | 0 -> 1000 | 148.3 MB | 165.9 MB | 18.02 KiB/conn |
| **XRAY** | TCP | 0 -> 1000 | 93.7 MB | 98.2 MB | 4.61 KiB/conn |
| **XRAY** | UDP | 0 -> 1000 | 149.4 MB | 153.1 MB | 3.79 KiB/conn |
| **SING** | TCP | 0 -> 1000 | 101.9 MB | 149.7 MB | 48.95 KiB/conn |
| **SING** | UDP | 0 -> 1000 | 150.7 MB | 167.5 MB | 17.20 KiB/conn |
| **MIPS** | TCP | 0 -> 1000 | 156.2 MB | 208.3 MB | 53.35 KiB/conn |
| **MIPS** | UDP | 0 -> 1000 | 126.5 MB | 163.4 MB | 37.79 KiB/conn |
---

### 3.2 分轮实测数据明细

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
| **HEV** | TCP | 0 -> 1000 | 99.1 MB | 115.8 MB | 17.10 KiB/conn |
| **HEV** | UDP | 0 -> 1000 | 148.3 MB | 165.9 MB | 18.02 KiB/conn |
| **XRAY** | TCP | 0 -> 1000 | 93.7 MB | 98.2 MB | 4.61 KiB/conn |
| **XRAY** | UDP | 0 -> 1000 | 149.4 MB | 153.1 MB | 3.79 KiB/conn |
| **SING** | TCP | 0 -> 1000 | 101.9 MB | 149.7 MB | 48.95 KiB/conn |
| **SING** | UDP | 0 -> 1000 | 150.7 MB | 167.5 MB | 17.20 KiB/conn |
| **MIPS** | TCP | 0 -> 1000 | 156.2 MB | 208.3 MB | 53.35 KiB/conn |
| **MIPS** | UDP | 0 -> 1000 | 126.5 MB | 163.4 MB | 37.79 KiB/conn |
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
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 198.09 Mbps (0.9% 丢包) | 117.06 Mbps (29.5% 丢包) | 190.2% / 236.0% | 111.5 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 148.51 Mbps (15.0% 丢包) | 71.11 Mbps (42.4% 丢包) | 189.4% / 225.0% | 124.0 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 199.92 Mbps | 200.13 Mbps | 135.1% / 204.0% | 104.9 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 199.57 Mbps | 200.09 Mbps | 77.2% / 204.0% | 101.8 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 199.92 Mbps | 200.50 Mbps | 202.2% / 238.0% | 111.7 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 185.51 Mbps (7.0% 丢包) | 199.29 Mbps | 187.2% / 227.0% | 128.0 MB |
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
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 159.59 Mbps (9.8% 丢包) | 122.41 Mbps (27.9% 丢包) | 181.2% / 222.0% | 117.2 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 144.88 Mbps (17.2% 丢包) | 121.69 Mbps (28.1% 丢包) | 187.4% / 230.0% | 134.1 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 174.09 Mbps | 200.02 Mbps | 135.2% / 220.0% | 114.8 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 198.43 Mbps (0.5% 丢包) | 199.46 Mbps | 79.8% / 214.0% | 110.8 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 199.66 Mbps | 200.03 Mbps | 195.4% / 238.0% | 115.1 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 166.03 Mbps | 199.83 Mbps (0.3% 丢包) | 201.9% / 230.0% | 130.1 MB |
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
| **HEV** | TCP | 0 -> 1000 | 103.6 MB | 122.0 MB | 18.84 KiB/conn |
| **HEV** | UDP | 0 -> 1000 | 148.3 MB | 165.9 MB | 18.02 KiB/conn |
| **XRAY** | TCP | 0 -> 1000 | 93.7 MB | 98.2 MB | 4.61 KiB/conn |
| **XRAY** | UDP | 0 -> 1000 | 149.4 MB | 153.1 MB | 3.79 KiB/conn |
| **SING** | TCP | 0 -> 1000 | 101.9 MB | 149.7 MB | 48.95 KiB/conn |
| **SING** | UDP | 0 -> 1000 | 150.7 MB | 167.5 MB | 17.20 KiB/conn |
| **MIPS** | TCP | 0 -> 1000 | 156.2 MB | 208.3 MB | 53.35 KiB/conn |
| **MIPS** | UDP | 0 -> 1000 | 126.5 MB | 163.4 MB | 37.79 KiB/conn |
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
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 198.09 Mbps (0.9% 丢包) | 117.06 Mbps (29.5% 丢包) | 190.2% / 236.0% | 111.5 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 148.51 Mbps (15.0% 丢包) | 71.11 Mbps (42.4% 丢包) | 189.4% / 225.0% | 124.0 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 199.90 Mbps | 200.43 Mbps | 135.3% / 203.5% | 104.6 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 199.68 Mbps | 200.14 Mbps | 77.2% / 203.0% | 102.1 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 199.92 Mbps | 200.50 Mbps | 202.2% / 238.0% | 111.7 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 185.51 Mbps (7.0% 丢包) | 199.29 Mbps | 187.2% / 227.0% | 128.0 MB |
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
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 159.59 Mbps (9.8% 丢包) | 122.41 Mbps (27.9% 丢包) | 181.2% / 222.0% | 117.2 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 144.88 Mbps (17.1% 丢包) | 121.69 Mbps (28.1% 丢包) | 187.4% / 230.0% | 134.1 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 186.89 Mbps | 200.12 Mbps | 132.0% / 208.5% | 110.7 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 197.41 Mbps (0.4% 丢包) | 199.61 Mbps | 80.0% / 217.0% | 106.5 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 199.66 Mbps | 200.03 Mbps | 195.4% / 238.0% | 115.1 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 166.03 Mbps | 199.83 Mbps (0.2% 丢包) | 201.9% / 230.0% | 130.1 MB |
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
| **HEV** | TCP | 0 -> 1000 | 99.1 MB | 115.8 MB | 17.10 KiB/conn |
| **HEV** | UDP | 0 -> 1000 | 148.3 MB | 165.9 MB | 18.02 KiB/conn |
| **XRAY** | TCP | 0 -> 1000 | 93.7 MB | 98.2 MB | 4.61 KiB/conn |
| **XRAY** | UDP | 0 -> 1000 | 149.4 MB | 153.1 MB | 3.79 KiB/conn |
| **SING** | TCP | 0 -> 1000 | 101.9 MB | 149.7 MB | 48.95 KiB/conn |
| **SING** | UDP | 0 -> 1000 | 150.7 MB | 167.5 MB | 17.20 KiB/conn |
| **MIPS** | TCP | 0 -> 1000 | 156.2 MB | 208.3 MB | 53.35 KiB/conn |
| **MIPS** | UDP | 0 -> 1000 | 126.5 MB | 163.4 MB | 37.79 KiB/conn |
</details>

<br>

---

## 4. 数据可视化

### 4.1 综合平均可视化 (3 轮平均)

#### (1) 5GHz Wi-Fi 无线吞吐量
![5GHz Wi-Fi Throughput Dashboard](../images/avg_wifi_throughput_dashboard.webp)

#### (2) USB 3.2 / 4.0 有线吞吐量
![USB 3.2 Throughput Dashboard](../images/avg_usb_throughput_dashboard.webp)

#### (3) 核心 CPU 传输能效比
![CPU Efficiency Dashboard](../images/avg_cpu_efficiency_dashboard.webp)

#### (4) 设备内部协议处理纯回环
![Loopback Dashboard](../images/avg_loopback_dashboard.webp)

#### (5) 空闲连接驻留与内存增长
![Idle Memory Dashboard](../images/avg_idle_memory_dashboard.webp)

---

### 4.2 分轮可视化明细

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

## 5. 分析

综合三轮测试数据，各 TUN 后端的性能与开销特征如下：

### 5.1 `hev-socks5-tunnel` (C / lwIP)

* **吞吐量表现**：
  - **TCP 吞吐**：在 MTU 9000 巨型帧下，5GHz Wi-Fi 多流下行达到 **411.21 Mbps**，USB 有线单流上行达 **647.39 Mbps**（多流 **513.97 Mbps**）；在标准 MTU 1500 单流下行维持在 **277.22 ~ 303.01 Mbps**。
  - **UDP 性能**：8 流并发 UDP 跑满 **200 Mbps** 限制（0% 丢包）；单流 UDP 上行维持在 **148.50 ~ 199.57 Mbps**，单流下行在 **123.75 ~ 137.12 Mbps**（丢包率 20.8% ~ 29.6%）。
* **资源开销与优势**：
  - **能效比突出，功耗低**：纯 C 实现配合微裁剪 lwIP 协议栈，TCP 测试中平均 CPU 占用率仅在 **16.0% ~ 48.5%** 区间，能效比（Mbps/CPU%）显著高于其他后端。
  - **内存控制平稳**：常驻 PSS 维持在 **95.7 ~ 106.4 MB**，在 1000 连接空闲驻留测试中，TCP 增长斜率为 **17.10 KiB/conn**，UDP 增长斜率为 **18.02 KiB/conn**，各阶梯点形态平滑。
* **局限性**：
  - **多流下行扩展受限**：受 lwIP 单线程事件循环模型影响，在 MTU 1500 多流下行并发时，下行吞吐收窄至 **~300 Mbps**，未充分利用移动 SoC 多核心能力。
  - **架构依赖**：需要向文件系统生成并写入临时配置文件 `tproxy.conf`，增加了一次文件 I/O 开销。

---

### 5.2 `SingTUN` (Go / sing-box 自研栈)

* **吞吐量表现**：
  - **高并发多流优势明显**：在标准 MTU 1500 下，8 并发 TCP 下行达到 **429.29 Mbps**（5GHz Wi-Fi）与 **395.75 Mbps**（USB），并发吞吐表现高于 Hev。
  - **UDP 吞吐良好**：修复 SOCKS5 UDP relay 握手参数后，8 流 UDP 上下行均达到 **200 Mbps** 物理带宽上限（0% 丢包）；单流上行在 USB 环境下测得 **198.09 Mbps**（0.9% 丢包）。
* **资源开销与优势**：
  - **无文件 I/O 依赖**：通过 JNI 直接接管 `VpnService` 的文件描述符，无临时磁盘配置文件开销。
  - **UDP 内存增长平缓**：在 1000 连接驻留测试中，UDP 内存斜率为 **17.20 KiB/conn**，表现非常稳定。
* **局限性**：
  - **高负载 CPU 消耗较大**：Go 运行时调度与内存分配开销，导致高并发冲刺时的 CPU 峰值达到 **220% ~ 238%**，能效比低于纯 C 实现。
  - **TCP 驻留连接内存开销偏大**：1000 个 TCP 连接驻留使进程 PSS 从 101.9 MB 上升至 149.7 MB，增长斜率为 **48.95 KiB/conn**。

---

### 5.3 `MipsTUN` (Go / Mihomo mipstack 自研栈)

* **吞吐量表现**：
  - **巨型帧吞吐突出**：MTU 1500 单流上行达 **608.62 Mbps**，MTU 9000 多流下行达到 **393.05 Mbps**。
  - **无线 TCP 表现均衡**：内置 BBRv3 拥塞控制算法，在 MTU 9000 下 Wi-Fi 单流跑出上行 **343.19 Mbps** 与下行 **353.59 Mbps**。
  - **UDP 吞吐达标**：8 流 UDP 下行测得 **199.29 ~ 199.83 Mbps**，上行达到 **166.03 ~ 185.51 Mbps**。
  - **内部回环吞吐最高**：在本地回环压力测试中单流测得 **20.92 Gbps**，位列第一。
* **资源开销与优势**：
  - **纯 Go 现代架构**：完全基于非阻塞 epoll/netpoller 实现，架构独立简洁，无外部 C 依赖。
* **局限性**：
  - **密集小包 CPU 开销显著**：在 MTU 1500 单流小包场景下，平均 CPU 开销为 **55.0% ~ 111.8%**，峰值突破 **200%**；建议在支持大帧的网络环境下开启大 MTU。
  - **连接驻留内存基数与增长偏大**：基础空闲 PSS 偏高（125.1 ~ 156.2 MB），1000 连接测试下 TCP 斜率为 **53.35 KiB/conn**（1000 连达到 208.3 MB），UDP 斜率为 **37.79 KiB/conn**。

---

### 5.4 `Xray Native TUN` (Go / gVisor netstack)

* **吞吐量表现**：
  - **下行传输存在明显瓶颈**：单流下行在 Wi-Fi 与 USB 环境下均处于 **58.13 ~ 63.33 Mbps** 水平，多流并发下行维持在 **141.93 ~ 180.25 Mbps**，下行产出偏低。
  - **上行及回环性能正常**：上行测速单流可达 **313.11 ~ 445.62 Mbps**，内部回环单流达到 **20.71 Gbps**，说明协议转换通路正常，下行收窄主要受 gVisor 协议栈单核事件循环与内部 buffer 拷贝调度制约。
  - **UDP 多流表现稳定**：8 流 UDP 上下行均达到 **197.41 ~ 200.14 Mbps**。
* **资源开销与优势**：
  - **连接驻留内存极低**：在 1000 空闲连接驻留测试中，TCP 斜率仅为 **4.61 KiB/conn**，UDP 斜率仅为 **3.79 KiB/conn**，1000 连接建立后整机 PSS 仅增长约 3.7 MB，驻留开销在四种后端中最低。
  - **核心直接集成**：由 Xray 核心主进程持有 VPN 文件描述符，无额外的代理转发层。
* **局限性**：
  - **下行能效比较低**：在下行仅 60 Mbps 左右的情况下，平均 CPU 占用仍达到 **65.4% ~ 96.1%**，峰值达到 **172% ~ 202%**，单位吞吐消耗的算力显著偏高。

---

## 6. Raw Output

- [benchmark_results.json](./benchmark_results.json)
- [benchmark_summary.md](./benchmark_summary.md)
