# SimpleXray Android TUN 性能基准测试报告

本文档记录对 SimpleXray 支持的两种透明代理 TUN 协议栈实现（`hev-socks5-tunnel` 与 `Xray Native TUN`），在 **Speed (吞吐量)**、**CPU usage (CPU 占用率)** 以及 **Memory usage (内存占用)** 进行测试与对比。

---

## 1. 测试环境

### 测试主机（iPerf3 Server / PC）
- **操作系统**: Microsoft Windows 10 IoT 企业版 LTSC (Build 19044)
- **处理器 (CPU)**: AMD Ryzen 7 6800H @ 3.2GHz (8 核 16 线程)
- **无线网卡**: Intel(R) Wi-Fi 6E AX210 (160MHz)
- **有线网卡**: Remote NDIS based Internet Sharing Device (USB 3.2 Gen1 / 4.0 40Gbps 全功能数据线)
- **iPerf3 版本**: 3.21

### Android 测试设备（iPerf3 Client / DUT）
- **操作系统**: Android 14
- **处理器 (SoC)**: 高通骁龙 778G (Octa-Core: 2x2.2GHz + 6x1.8GHz)
- **接口规格**: Type-C USB 3.2 Gen1
- **无线规格**: 802.11 a/b/g/n/ac/ax 2.4G+5GHz, HE80, MIMO, 1024-QAM
- **iPerf3 版本**: 3.21 (aarch64 静态编译版)

### 局域网网关
- **网关设备**: 高通第五代骁龙 8 至尊版移动平台设备（Android 16，FastConnect 7900 无线连接系统）
- **网络频段**: 5GHz Wi-Fi hotspot

---

## 2. 测试链路

测试采用 **局域网 Direct/Freedom 纯 TUN 转发** 模式，流量经由 Android 系统 `VpnService` 虚拟网卡由代理核心进行封包解包并直连出站：

```text
               [ 局域网网关 (5GHz Wi-Fi AP / USB RNDIS) ]
                      │                                 │
                      ▼                                 ▼
        [ Android 设备 (DUT) ]                  [ PC 主机 (Server) ]
          iperf3 client 进程                     iperf3 server 进程 (:5201)
                 │                                      ▲
            Android VpnService (tun0)                   │
                 │                                      │
           ┌─────┴────────────────────────┐             │
           │ (协议栈后端切换)             │             │
           ▼                              ▼             │
        [ Hev 模式 ]               [ Xray 原生 TUN ]    │
      hev-socks5-tunnel (C/lwIP)   Xray TUN Inbound     │
                 │                 (Go/gVisor 协议栈)   │
        Xray SOCKS5 Inbound               │             │
                 │                        │             │
           └─────┬────────────────────────┘             │
                 ▼                                      │
        Xray Freedom Outbound ──────────────────────────┘
```

---

## 3. 图表可视化

<details open>
<summary><b>第 1 轮测试可视化图表</b></summary>

### 3.1 Speed (吞吐量 / 速度)

#### (1) USB 3.2 Gen1 / 4.0 有线以太网吞吐量 (Mbps)
![USB Upload Speed](../images/r1_usb_speed_upload.webp)
![USB Download Speed](../images/r1_usb_speed_download.webp)

#### (2) 5GHz Wi-Fi 无线网络吞吐量 (Mbps)
![Wi-Fi Upload Speed](../images/r1_wifi_speed_upload.webp)
![Wi-Fi Download Speed](../images/r1_wifi_speed_download.webp)

#### (3) 设备内部回环核心协议处理吞吐量 (Gbps)
![Loopback Processing Speed](../images/r1_loopback_speed.webp)

---

### 3.2 CPU usage (CPU 占用率 - %)

![Upload CPU Usage](../images/r1_cpu_usage_upload.webp)
![Download CPU Usage](../images/r1_cpu_usage_download.webp)

---

### 3.3 Memory usage (内存占用 - MB PSS)

![Upload Memory Usage](../images/r1_memory_usage_upload.webp)
![Download Memory Usage](../images/r1_memory_usage_download.webp)

</details>

<br>

<details>
<summary><b>第 2 轮测试可视化图表</b></summary>

### 3.1 Speed (吞吐量 / 速度)

#### (1) USB 3.2 Gen1 / 4.0 有线以太网吞吐量 (Mbps)
![USB Upload Speed](../images/r2_usb_speed_upload.webp)
![USB Download Speed](../images/r2_usb_speed_download.webp)

#### (2) 5GHz Wi-Fi 无线网络吞吐量 (Mbps)
![Wi-Fi Upload Speed](../images/r2_wifi_speed_upload.webp)
![Wi-Fi Download Speed](../images/r2_wifi_speed_download.webp)

#### (3) 设备内部回环核心协议处理吞吐量 (Gbps)
![Loopback Processing Speed](../images/r2_loopback_speed.webp)

---

### 3.2 CPU usage (CPU 占用率 - %)

![Upload CPU Usage](../images/r2_cpu_usage_upload.webp)
![Download CPU Usage](../images/r2_cpu_usage_download.webp)

---

### 3.3 Memory usage (内存占用 - MB PSS)

![Upload Memory Usage](../images/r2_memory_usage_upload.webp)
![Download Memory Usage](../images/r2_memory_usage_download.webp)

</details>

---

## 4. 全场景标准化实测数据

<details open>
<summary><b>第 1 轮测试实测数据</b></summary>

### 4.1 USB 3.2 Gen1 / 4.0 高速有线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 峰值 CPU% | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | **983.91 Mbps** | 383.42 Mbps | 0% | 118.3 MB |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | **900.42 Mbps** | 432.75 Mbps | 0% | 118.3 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | **997.46 Mbps** | 396.32 Mbps | 0% | 118.4 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | **1000.84 Mbps** | 390.55 Mbps | 0% | 118.4 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | **985.54 Mbps** | 393.91 Mbps | 0% | 118.4 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | **998.66 Mbps** | 389.71 Mbps | 0% | 118.4 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | **898.15 Mbps** | 423.16 Mbps | 0% | 118.4 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | **894.83 Mbps** | **436.37 Mbps** | 0% | 118.4 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | **899.42 Mbps** | 430.81 Mbps | 0% | 118.2 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | **896.21 Mbps** | 428.40 Mbps | 0% | 118.3 MB |

---

### 4.2 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 峰值 CPU% | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 303.24 Mbps | 297.34 Mbps | 0% | 118.4 MB |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 324.12 Mbps | 319.41 Mbps | 1% | 118.4 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 347.20 Mbps | 297.45 Mbps | 0% | 118.5 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 326.08 Mbps | 336.34 Mbps | 0% | 118.5 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 384.44 Mbps | 299.65 Mbps | 0% | 118.4 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | **431.36 Mbps** | 318.21 Mbps | 0% | 118.4 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | **425.99 Mbps** | **406.01 Mbps** | 1% | 118.4 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | **425.21 Mbps** | 311.07 Mbps | 0% | 118.4 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 335.04 Mbps | 382.21 Mbps | 0% | 118.4 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 302.93 Mbps | 334.78 Mbps | 0% | 118.4 MB |

---

### 4.3 设备内部纯回环测试 (On-Device Loopback 127.0.0.1)

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 极限吞吐 (Gbps) | 极限吞吐 (Mbps) | 峰值 CPU% | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | 单流 | **20.08 Gbps** | 20,075.92 Mbps | 0% | 118.2 MB |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | P=8 | **17.93 Gbps** | 17,933.62 Mbps | 0% | 118.2 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | **21.40 Gbps** | 21,395.03 Mbps | 0% | 118.3 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | **22.18 Gbps** | 22,179.73 Mbps | 0% | 118.3 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | **22.22 Gbps** | 22,220.74 Mbps | 0% | 118.3 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | **22.21 Gbps** | 22,211.80 Mbps | 0% | 118.4 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | **17.88 Gbps** | 17,881.28 Mbps | 0% | 118.3 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | **18.08 Gbps** | 18,076.60 Mbps | 0% | 118.3 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | **17.95 Gbps** | 17,950.79 Mbps | 0% | 115.2 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | **17.69 Gbps** | 17,692.60 Mbps | 0% | 115.2 MB |

</details>

<br>

<details>
<summary><b>第 2 轮测试实测数据</b></summary>

### 4.1 USB 3.2 Gen1 / 4.0 高速有线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 峰值 CPU% | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | **1050.67 Mbps** | 379.75 Mbps | 1% | 77.0 MB |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | **931.07 Mbps** | 426.30 Mbps | 1% | 77.0 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | **994.99 Mbps** | 379.23 Mbps | 3.7% | 77.1 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | **978.89 Mbps** | 383.42 Mbps | 1% | 77.2 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | **989.27 Mbps** | 384.37 Mbps | 1% | 77.2 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | **961.05 Mbps** | 394.33 Mbps | 2% | 77.2 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | **889.72 Mbps** | 421.08 Mbps | 1% | 77.2 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | **888.64 Mbps** | 422.27 Mbps | 1% | 77.2 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | **894.82 Mbps** | 420.01 Mbps | 2% | 77.2 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | **876.86 Mbps** | 420.12 Mbps | 1% | 77.2 MB |

---

### 4.2 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 峰值 CPU% | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 310.62 Mbps | 263.91 Mbps | 1% | 76.3 MB |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 292.65 Mbps | 295.82 Mbps | 1% | 76.3 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 316.88 Mbps | 297.13 Mbps | 1% | 76.5 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | **377.36 Mbps** | 318.20 Mbps | 1% | 76.6 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 345.01 Mbps | 292.31 Mbps | 3% | 77.0 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 310.89 Mbps | 326.91 Mbps | 1% | 77.0 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 342.83 Mbps | 343.00 Mbps | 1% | 77.0 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 335.68 Mbps | 351.54 Mbps | 1% | 77.0 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 331.56 Mbps | 362.97 Mbps | 1% | 77.1 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 345.22 Mbps | 349.45 Mbps | 1% | 77.1 MB |

---

### 4.3 设备内部纯回环测试 (On-Device Loopback 127.0.0.1)

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 极限吞吐 (Gbps) | 极限吞吐 (Mbps) | 峰值 CPU% | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | 单流 | **21.50 Gbps** | 21,500.85 Mbps | 1% | 77.1 MB |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | P=8 | **17.92 Gbps** | 17,923.87 Mbps | 1% | 77.1 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | **21.09 Gbps** | 21,092.14 Mbps | 1% | 77.1 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | **20.96 Gbps** | 20,963.45 Mbps | 1% | 77.2 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | **20.37 Gbps** | 20,367.82 Mbps | 1% | 77.2 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | **20.43 Gbps** | 20,426.60 Mbps | 0% | 77.1 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | **17.69 Gbps** | 17,689.43 Mbps | 0% | 61.9 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | **17.92 Gbps** | 17,921.51 Mbps | 0% | 61.9 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | **17.96 Gbps** | 17,958.70 Mbps | 0% | 61.9 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | **17.62 Gbps** | 17,616.95 Mbps | 23% | 89.5 MB |

</details>

---

## 5. 分析

1. **Speed**：
   - 在 USB 3.2 Gen1 / 4.0 有线测试中，两轮测试均稳定显示：`Hev` 与 `Xray Native TUN` 单流上传吞吐均达到 **961 ~ 1000 Mbps**，8 并发流上传稳定在 **876 ~ 898 Mbps**，均跑满 1Gbps 物理有线以太网链路。
   - 在 5GHz Wi-Fi 无线环境下，两轮实测吞吐均维持在 300~380 Mbps 之间。
   - 设备内部回环极限速率在两轮测试中均稳定在 **17.6 ~ 22.2 Gbps** 级别。
2. **CPU & Memory**：
   - 两种实现平均 CPU 占用率在常规网络传输下均低于 1%，未对 SoC 造成额外压力；
   - 常驻内存（TOTAL PSS）维持在 **62 MB ~ 118.5 MB** 之间，无内存泄漏与异常堆积。

---

## 6. 附：Raw Output

<details open>
<summary><b>第 1 轮 Raw Output</b></summary>

```text
==========================================================================================
                             COMPREHENSIVE BENCHMARK RESULTS
==========================================================================================

Name                                       Backend     MTU Medium             Upload                    Download                  UpCpu DownCpu PeakCpu PeakMem 
----                                       -------     --- ------             ------                    --------                  ----- ------- ------- ------- 
Wi-Fi Baseline (No VPN) [Single Stream]    direct_none   0 5GHz Wi-Fi         303.24 Mbps               297.34 Mbps               0%    0%      0%      118.4 MB
Wi-Fi Baseline (No VPN) [P=8]              direct_none   0 5GHz Wi-Fi         324.12 Mbps               319.41 Mbps               0%    0.1%    1%      118.4 MB
Hev (MTU 1500) [Single Stream]             hev        1500 5GHz Wi-Fi         347.20 Mbps               297.45 Mbps               0%    0%      0%      118.5 MB
Xray TUN (MTU 1500) [Single Stream]        xray       1500 5GHz Wi-Fi         326.08 Mbps               336.34 Mbps               0%    0%      0%      118.5 MB
Hev (MTU 8500) [Single Stream]             hev        8500 5GHz Wi-Fi         384.44 Mbps               299.65 Mbps               0%    0%      0%      118.4 MB
Xray TUN (MTU 8500) [Single Stream]        xray       8500 5GHz Wi-Fi         431.36 Mbps               318.21 Mbps               0%    0%      0%      118.4 MB
Hev (MTU 1500) [P=8]                       hev        1500 5GHz Wi-Fi         425.99 Mbps               406.01 Mbps               0%    0.1%    1%      118.4 MB
Xray TUN (MTU 1500) [P=8]                  xray       1500 5GHz Wi-Fi         425.21 Mbps               311.07 Mbps               0%    0%      0%      118.4 MB
Hev (MTU 8500) [P=8]                       hev        8500 5GHz Wi-Fi         335.04 Mbps               382.21 Mbps               0%    0%      0%      118.4 MB
Xray TUN (MTU 8500) [P=8]                  xray       8500 5GHz Wi-Fi         302.93 Mbps               334.78 Mbps               0%    0%      0%      118.4 MB
USB Baseline (No VPN) [Single Stream]      direct_none   0 USB 3.2 / 4.0      983.91 Mbps               383.42 Mbps               0%    0%      0%      118.3 MB
USB Baseline (No VPN) [P=8]                direct_none   0 USB 3.2 / 4.0      900.42 Mbps               432.75 Mbps               0%    0%      0%      118.3 MB
Hev (MTU 1500) [Single Stream]             hev        1500 USB 3.2 / 4.0      997.46 Mbps               396.32 Mbps               0%    0%      0%      118.4 MB
Xray TUN (MTU 1500) [Single Stream]        xray       1500 USB 3.2 / 4.0      1000.84 Mbps              390.55 Mbps               0%    0%      0%      118.4 MB
Hev (MTU 8500) [Single Stream]             hev        8500 USB 3.2 / 4.0      985.54 Mbps               393.91 Mbps               0%    0%      0%      118.4 MB
Xray TUN (MTU 8500) [Single Stream]        xray       8500 USB 3.2 / 4.0      998.66 Mbps               389.71 Mbps               0%    0%      0%      118.4 MB
Hev (MTU 1500) [P=8]                       hev        1500 USB 3.2 / 4.0      898.15 Mbps               423.16 Mbps               0%    0%      0%      118.4 MB
Xray TUN (MTU 1500) [P=8]                  xray       1500 USB 3.2 / 4.0      894.83 Mbps               436.37 Mbps               0%    0%      0%      118.4 MB
Hev (MTU 8500) [P=8]                       hev        8500 USB 3.2 / 4.0      899.42 Mbps               430.81 Mbps               0%    0%      0%      118.2 MB
Xray TUN (MTU 8500) [P=8]                  xray       8500 USB 3.2 / 4.0      896.21 Mbps               428.40 Mbps               0%    0%      0%      118.3 MB
Loopback Baseline (No VPN) [Single Stream] direct_none   0 On-Device Loopback 20.08 Gbps (20075.92 Mbps) 20.08 Gbps (20075.92 Mbps) 0%    0%      0%      118.2 MB
Loopback Baseline (No VPN) [P=8]           direct_none   0 On-Device Loopback 17.93 Gbps (17933.62 Mbps) 17.93 Gbps (17933.62 Mbps) 0%    0%      0%      118.2 MB
Hev (MTU 1500) [Single Stream]             hev        1500 On-Device Loopback 21.40 Gbps (21395.03 Mbps) 21.40 Gbps (21395.03 Mbps) 0%    0%      0%      118.3 MB
Xray TUN (MTU 1500) [Single Stream]        xray       1500 On-Device Loopback 22.18 Gbps (22179.73 Mbps) 22.18 Gbps (22179.73 Mbps) 0%    0%      0%      118.3 MB
Hev (MTU 8500) [Single Stream]             hev        8500 On-Device Loopback 22.22 Gbps (22220.74 Mbps) 22.22 Gbps (22220.74 Mbps) 0%    0%      0%      118.3 MB
Xray TUN (MTU 8500) [Single Stream]        xray       8500 On-Device Loopback 22.21 Gbps (22211.80 Mbps) 22.21 Gbps (22211.80 Mbps) 0%    0%      0%      118.4 MB
Hev (MTU 1500) [P=8]                       hev        1500 On-Device Loopback 17.88 Gbps (17881.28 Mbps) 17.88 Gbps (17881.28 Mbps) 0%    0%      0%      118.3 MB
Xray TUN (MTU 1500) [P=8]                  xray       1500 On-Device Loopback 18.08 Gbps (18076.60 Mbps) 18.08 Gbps (18076.60 Mbps) 0%    0%      0%      118.3 MB
Hev (MTU 8500) [P=8]                       hev        8500 On-Device Loopback 17.95 Gbps (17950.79 Mbps) 17.95 Gbps (17950.79 Mbps) 0%    0%      0%      115.2 MB
Xray TUN (MTU 8500) [P=8]                  xray       8500 On-Device Loopback 17.69 Gbps (17692.60 Mbps) 17.69 Gbps (17692.60 Mbps) 0%    0%      0%      115.2 MB
```

</details>

<br>

<details>
<summary><b>第 2 轮 Raw Output</b></summary>

```text
==========================================================================================
                             COMPREHENSIVE BENCHMARK RESULTS
==========================================================================================

Name                                       Backend     MTU Medium             Upload                    Download                  UpCpu DownCpu PeakCpu PeakMem 
----                                       -------     --- ------             ------                    --------                  ----- ------- ------- ------- 
Wi-Fi Baseline (No VPN) [Single Stream]    direct_none   0 5GHz Wi-Fi         310.62 Mbps               263.91 Mbps               0.3%  0.2%    1%      76.3 MB 
Wi-Fi Baseline (No VPN) [P=8]              direct_none   0 5GHz Wi-Fi         292.65 Mbps               295.82 Mbps               0.2%  0.2%    1%      76.3 MB 
Hev (MTU 1500) [Single Stream]             hev        1500 5GHz Wi-Fi         316.88 Mbps               297.13 Mbps               0.2%  0.3%    1%      76.5 MB 
Xray TUN (MTU 1500) [Single Stream]        xray       1500 5GHz Wi-Fi         377.36 Mbps               318.20 Mbps               0.2%  0.1%    1%      76.6 MB 
Hev (MTU 8500) [Single Stream]             hev        8500 5GHz Wi-Fi         345.01 Mbps               292.31 Mbps               0.4%  0.6%    3%      77 MB   
Xray TUN (MTU 8500) [Single Stream]        xray       8500 5GHz Wi-Fi         310.89 Mbps               326.91 Mbps               0.2%  0.2%    1%      77 MB   
Hev (MTU 1500) [P=8]                       hev        1500 5GHz Wi-Fi         342.83 Mbps               343.00 Mbps               0.2%  0.1%    1%      77 MB   
Xray TUN (MTU 1500) [P=8]                  xray       1500 5GHz Wi-Fi         335.68 Mbps               351.54 Mbps               0.2%  0.1%    1%      77 MB   
Hev (MTU 8500) [P=8]                       hev        8500 5GHz Wi-Fi         331.56 Mbps               362.97 Mbps               0.2%  0.2%    1%      77.1 MB 
Xray TUN (MTU 8500) [P=8]                  xray       8500 5GHz Wi-Fi         345.22 Mbps               349.45 Mbps               0.3%  0.2%    1%      77.1 MB 
USB Baseline (No VPN) [Single Stream]      direct_none   0 USB 3.2 / 4.0      1050.67 Mbps              379.75 Mbps               0.1%  0.3%    1%      77 MB   
USB Baseline (No VPN) [P=8]                direct_none   0 USB 3.2 / 4.0      931.07 Mbps               426.30 Mbps               0.3%  0.3%    1%      77 MB   
Hev (MTU 1500) [Single Stream]             hev        1500 USB 3.2 / 4.0      994.99 Mbps               379.23 Mbps               0.6%  0.2%    3.7%    77.1 MB 
Xray TUN (MTU 1500) [Single Stream]        xray       1500 USB 3.2 / 4.0      978.89 Mbps               383.42 Mbps               0.2%  0.2%    1%      77.2 MB 
Hev (MTU 8500) [Single Stream]             hev        8500 USB 3.2 / 4.0      989.27 Mbps               384.37 Mbps               0.3%  0.3%    1%      77.2 MB 
Xray TUN (MTU 8500) [Single Stream]        xray       8500 USB 3.2 / 4.0      961.05 Mbps               394.33 Mbps               0.4%  0.3%    2%      77.2 MB 
Hev (MTU 1500) [P=8]                       hev        1500 USB 3.2 / 4.0      889.72 Mbps               421.08 Mbps               0.3%  0.2%    1%      77.2 MB 
Xray TUN (MTU 1500) [P=8]                  xray       1500 USB 3.2 / 4.0      888.64 Mbps               422.27 Mbps               0.3%  0.3%    1%      77.2 MB 
Hev (MTU 8500) [P=8]                       hev        8500 USB 3.2 / 4.0      894.82 Mbps               420.01 Mbps               0.1%  0.5%    2%      77.2 MB 
Xray TUN (MTU 8500) [P=8]                  xray       8500 USB 3.2 / 4.0      876.86 Mbps               420.12 Mbps               0.2%  0.3%    1%      77.2 MB 
Loopback Baseline (No VPN) [Single Stream] direct_none   0 On-Device Loopback 21.50 Gbps (21500.85 Mbps) 21.50 Gbps (21500.85 Mbps) 0.1%  0.1%    1%      77.1 MB 
Loopback Baseline (No VPN) [P=8]           direct_none   0 On-Device Loopback 17.92 Gbps (17923.87 Mbps) 17.92 Gbps (17923.87 Mbps) 0.1%  0.1%    1%      77.1 MB 
Hev (MTU 1500) [Single Stream]             hev        1500 On-Device Loopback 21.09 Gbps (21092.14 Mbps) 21.09 Gbps (21092.14 Mbps) 0.1%  0.1%    1%      77.1 MB 
Xray TUN (MTU 1500) [Single Stream]        xray       1500 On-Device Loopback 20.96 Gbps (20963.45 Mbps) 20.96 Gbps (20963.45 Mbps) 0.3%  0.3%    1%      77.2 MB 
Hev (MTU 8500) [Single Stream]             hev        8500 On-Device Loopback 20.37 Gbps (20367.82 Mbps) 20.37 Gbps (20367.82 Mbps) 0.1%  0.1%    1%      77.2 MB 
Xray TUN (MTU 8500) [Single Stream]        xray       8500 On-Device Loopback 20.43 Gbps (20426.60 Mbps) 20.43 Gbps (20426.60 Mbps) 0%    0%      0%      77.1 MB 
Hev (MTU 1500) [P=8]                       hev        1500 On-Device Loopback 17.69 Gbps (17689.43 Mbps) 17.69 Gbps (17689.43 Mbps) 0%    0%      0%      61.9 MB 
Xray TUN (MTU 1500) [P=8]                  xray       1500 On-Device Loopback 17.92 Gbps (17921.51 Mbps) 17.92 Gbps (17921.51 Mbps) 0%    0%      0%      61.9 MB 
Hev (MTU 8500) [P=8]                       hev        8500 On-Device Loopback 17.96 Gbps (17958.70 Mbps) 17.96 Gbps (17958.70 Mbps) 0%    0%      0%      61.9 MB 
Xray TUN (MTU 8500) [P=8]                  xray       8500 On-Device Loopback 17.62 Gbps (17616.95 Mbps) 17.62 Gbps (17616.95 Mbps) 2.9%  2.9%    23%     89.5 MB 
```

</details>
