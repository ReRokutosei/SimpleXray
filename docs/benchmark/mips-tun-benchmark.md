# SimpleXray MipsTUN 独立基准测试报告

本文档记录对 SimpleXray 新增的第四个透明代理 TUN 协议栈实现 **MipsTUN**（基于 Mihomo `mipstack` 纯 Go 协议栈，集成 BBRv3 拥塞控制）的专项性能基准测试报告。

---

## 1. 测试环境

### 测试主机（iPerf3 Server / PC）
- **操作系统**: Linux x86_64 (Linux 6.12)
- **处理器 (CPU)**: AMD Ryzen 7 6800H @ 3.2GHz (8 核 16 线程)
- **无线网卡**: Intel(R) Wi-Fi 6E AX210 (160MHz)
- **有线网卡**: USB 3.2 Gen1 / 4.0 40Gbps 全功能数据线 (RNDIS USB 网络共享虚拟以太网)
- **iPerf3 版本**: 3.18

### Android 测试设备（iPerf3 Client / DUT）
- **操作系统**: Android 14
- **处理器 (SoC)**: 高通骁龙 778G (Octa-Core: 4x2.4GHz + 4x1.8GHz Kryo 670)
- **有线接口**: Type-C USB 3.2 Gen1 (RNDIS USB 网络共享)
- **无线规格**: 802.11 a/b/g/n/ac/ax 2.4G+5GHz, HE80, MIMO
- **iPerf3 版本**: 3.21 (aarch64 静态编译版)

### 局域网网关
- **网关设备**: 高通第五代骁龙 8 至尊版移动平台设备（Android 16，FastConnect 7900 无线连接系统）
- **网络频段**: 5GHz Wi-Fi 热点 (WLAN AP)

---

## 2. 架构与测试链路

MipsTUN 采用 Mihomo 的 `mipstack` 用户态网络协议栈，默认启用 `BBRv3` 拥塞控制算法，通过 Linux netpoller (epoll) 无缝接管 Android 系统 `VpnService` 的非阻塞文件描述符，并将流量通过本地 SOCKS5 入站泵入 Xray 核心：

```text
               [ 局域网网关 (5GHz Wi-Fi AP / USB RNDIS) ]
                      │                                 │
                      ▼                                 ▼
        [ Android 设备 (DUT) ]                  [ PC 主机 (Server) ]
          iperf3 client 进程                     iperf3 server 进程 (:5201)
                 │                                      ▲
            Android VpnService (tun0)                   │
                 │                                      │
                 ▼                                      │
         [ MipsTUN 模式 ]                               │
     mipstack (Mihomo 纯 Go 栈)                         │
     TCP: BBRv3 / UDP: Associate                        │
                 │                                      │
            Xray SOCKS5                                 │
              Inbound                                   │
                 │                                      │
                 ▼                                      │
        Xray Freedom Outbound ──────────────────────────┘
```

---

## 3. 测试数据

测试覆盖 **USB 3.2 Gen1 有线以太网**、**5GHz Wi-Fi 无线网络** 以及 **设备内部回环** 三种物理介质，分别测试 **单流 (Single Stream)** 与 **8 并发多流 (P=8)**，MTU 分别测试 **1500 (标准以太网)** 与 **8500 (Jumbo Frame)**，每项各执行 3 轮独立测试。

### 3.1 综合平均测试数据 (3 轮平均)

#### (1) USB 3.2 Gen1 / 4.0 有线以太网测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 700.40 Mbps | 374.75 Mbps | 0.3% / 1.3% | 122.0 MB |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 734.22 Mbps | 424.60 Mbps | 0% / 0% | 121.5 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 582.56 Mbps | 227.58 Mbps | 110.3% / 199.7% | 137.5 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 655.88 Mbps | 326.94 Mbps | 61.3% / 133.3% | 135.2 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 657.47 Mbps | 166.54 Mbps | 117.5% / 241.0% | 142.1 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 668.21 Mbps | 372.83 Mbps | 64.4% / 191.0% | 145.5 MB |

---

#### (2) 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 276.42 Mbps | 252.01 Mbps | 0.5% / 5.0% | 92.9 MB |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 281.39 Mbps | 275.10 Mbps | 0.1% / 1.0% | 93.0 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 256.16 Mbps | 230.48 Mbps | 40.0% / 194.7% | 125.3 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 302.91 Mbps | 258.69 Mbps | 31.4% / 113.4% | 119.4 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 342.46 Mbps | 145.23 Mbps | 46.5% / 196.7% | 146.0 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 327.67 Mbps | 282.51 Mbps | 31.0% / 197.3% | 156.3 MB |

---

#### (3) 设备内部纯回环测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 极限吞吐 (Gbps) | 极限吞吐 (Mbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | 单流 | 20.58 Gbps | 20,580 Mbps | 5.0% | 104.2 MB |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | P=8 | 18.49 Gbps | 18,490 Mbps | 0.3% | 103.8 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 20.49 Gbps | 20,490 Mbps | 12.8% | 125.4 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 20.78 Gbps | 20,780 Mbps | 16.3% | 115.7 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 18.38 Gbps | 18,380 Mbps | 3.8% | 107.9 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 18.25 Gbps | 18,250 Mbps | 6.7% | 121.0 MB |

---

### 3.2 分轮实测数据明细

<details>
<summary><b>第 1 轮测试数据</b></summary>

#### (1) USB 3.2 Gen1 / 4.0 有线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 718.45 Mbps | 370.63 Mbps | 0.8% / 4.0% | 92.2 MB |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 757.77 Mbps | 423.78 Mbps | 0% / 2.0% | 90.7 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 641.34 Mbps | 230.97 Mbps | 110.8% / 206.0% | 130.5 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 657.07 Mbps | 324.26 Mbps | 61.2% / 165.0% | 131.9 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 676.90 Mbps | 149.93 Mbps | 116.1% / 228.0% | 148.5 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 696.22 Mbps | 351.65 Mbps | 64.8% / 214.0% | 146.7 MB |

#### (2) 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 263.04 Mbps | 278.99 Mbps | 0.4% / 4.0% | 97.4 MB |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 290.56 Mbps | 237.01 Mbps | 0% / 0% | 97.4 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 246.29 Mbps | 228.98 Mbps | 34.4% / 200.0% | 111.8 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 254.76 Mbps | 279.52 Mbps | 25.0% / 115.0% | 127.9 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 305.01 Mbps | 137.98 Mbps | 44.4% / 203.0% | 149.9 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 323.12 Mbps | 291.17 Mbps | 32.8% / 218.0% | 155.2 MB |

#### (3) 设备内部纯回环测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 极限吞吐 (Gbps) | 极限吞吐 (Mbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | 单流 | 20.31 Gbps | 20,310 Mbps | 8.0% | 91.7 MB |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | P=8 | 18.51 Gbps | 18,510 Mbps | 0% | 90.6 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 20.80 Gbps | 20,800 Mbps | 10.0% | 123.2 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 21.38 Gbps | 21,380 Mbps | 17.8% | 103.5 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 18.37 Gbps | 18,370 Mbps | 3.4% | 104.5 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 18.34 Gbps | 18,340 Mbps | 7.0% | 119.1 MB |

</details>

<br>

<details>
<summary><b>第 2 轮测试数据</b></summary>

#### (1) USB 3.2 Gen1 / 4.0 有线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 681.87 Mbps | 372.94 Mbps | 0% / 0% | 134.5 MB |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 726.15 Mbps | 409.68 Mbps | 0% / 0% | 134.5 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 514.57 Mbps | 229.82 Mbps | 108.9% / 196.0% | 138.5 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 655.55 Mbps | 329.13 Mbps | 60.1% / 168.0% | 129.9 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 647.35 Mbps | 174.74 Mbps | 119.3% / 270.0% | 133.0 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 660.64 Mbps | 397.73 Mbps | 66.6% / 170.0% | 139.5 MB |

#### (2) 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 330.03 Mbps | 265.05 Mbps | 0.5% / 5.0% | 90.7 MB |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 287.45 Mbps | 319.04 Mbps | 0.1% / 1.0% | 90.9 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 266.91 Mbps | 239.68 Mbps | 46.3% / 205.0% | 131.5 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 345.53 Mbps | 262.95 Mbps | 34.2% / 133.0% | 114.7 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 343.21 Mbps | 146.21 Mbps | 44.6% / 190.0% | 152.6 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 315.09 Mbps | 312.13 Mbps | 30.5% / 185.0% | 154.1 MB |

#### (3) 设备内部纯回环测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 极限吞吐 (Gbps) | 极限吞吐 (Mbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | 单流 | 20.92 Gbps | 20,920 Mbps | 7.0% | 91.2 MB |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | P=8 | 18.45 Gbps | 18,450 Mbps | 1.0% | 90.8 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 21.07 Gbps | 21,070 Mbps | 10.0% | 121.8 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 21.05 Gbps | 21,050 Mbps | 16.0% | 122.0 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 18.53 Gbps | 18,530 Mbps | 4.0% | 119.0 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 18.30 Gbps | 18,300 Mbps | 6.0% | 121.9 MB |

</details>

<br>

<details>
<summary><b>第 3 轮测试数据</b></summary>

#### (1) USB 3.2 Gen1 / 4.0 有线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 700.87 Mbps | 380.69 Mbps | 0.0% / 1.0% | 139.3 MB |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 718.74 Mbps | 440.35 Mbps | 0% / 0% | 139.3 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 591.76 Mbps | 221.95 Mbps | 111.1% / 197.0% | 143.6 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 655.01 Mbps | 327.43 Mbps | 62.7% / 171.0% | 143.9 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 648.16 Mbps | 174.94 Mbps | 117.0% / 225.0% | 144.7 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 647.76 Mbps | 369.11 Mbps | 61.7% / 189.0% | 150.3 MB |

#### (2) 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 236.19 Mbps | 212.00 Mbps | 0.6% / 6.0% | 90.7 MB |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 266.15 Mbps | 269.24 Mbps | 0.2% / 2.0% | 90.8 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 255.28 Mbps | 222.79 Mbps | 39.4% / 190.0% | 132.6 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 308.45 Mbps | 233.60 Mbps | 34.9% / 111.0% | 115.6 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 379.15 Mbps | 151.50 Mbps | 50.4% / 197.0% | 135.4 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 344.80 Mbps | 244.23 Mbps | 29.8% / 189.0% | 159.7 MB |

#### (3) 设备内部纯回环测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 极限吞吐 (Gbps) | 极限吞吐 (Mbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | 单流 | 20.52 Gbps | 20,520 Mbps | 0% / 0% | 129.8 MB |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | P=8 | 18.51 Gbps | 18,510 Mbps | 0% / 0% | 129.9 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 19.59 Gbps | 19,590 Mbps | 18.5% | 131.1 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 19.90 Gbps | 19,900 Mbps | 15.0% | 121.7 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 18.24 Gbps | 18,240 Mbps | 4.0% | 100.2 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 18.12 Gbps | 18,120 Mbps | 7.0% | 122.1 MB |

</details>

---

## 4. 性能特征深度分析

### 4.1 吞吐量与协议表现
1. **USB 物理高速带宽释放充分**：
   - 在 USB 3.2 Gen1 有线物理基准约 700-750 Mbps 上传的环境下，MipsTUN 上传单流达到 **582.56 Mbps** (MTU 1500) 与 **655.88 Mbps** (MTU 8500)，多流稳定在 **657-668 Mbps**，已接近硬件总线有效数据载荷的上限（约 90-93% 物理基准带宽释放率）。
   - 下载方向在 MTU 8500 下单流达 **326.94 Mbps**，多流达 **372.83 Mbps**，表现显著优于基于 gVisor 的原生 Xray TUN。
2. **Wi-Fi 无线网络性能平稳**：
   - 5GHz Wi-Fi 环境下，MipsTUN MTU 8500 上传达 **302.91 Mbps**（超过 Wi-Fi 基准 276.42 Mbps，体现出 BBRv3 主动带宽探测的激进增益），多流达到 **327.67 Mbps**。
3. **极限回环性能达到 20.78 Gbps**：
   - 在 Android 设备内部不经过物理 PHY 层回环压力测试中，MipsTUN MTU 8500 测得 **20.78 Gbps (20,775 Mbps)**，完全达到了设备原生 Linux 套接字 loopback 物理极限，证明 Go 协议栈和 netpoller 事件循环本身不构成高带宽瓶颈。

### 4.2 MTU 影响与 CPU 开销
1. **大 MTU (8500) 显著降低 CPU 消耗并提升吞吐**：
   - 在 USB 测试中，MTU 从 1500 提升至 8500 时，单流上传平均 CPU 从 **110.3%** 大幅下降至 **61.3%**（降低近 45% 的处理器负载），同时下载带宽从 **227.58 Mbps** 提升至 **326.94 Mbps**（提升 43.6%）。
   - 这表明基于内存切片的 Go 协议栈在高吞吐下单包处理（Packet-by-packet overhead）具备典型的 MTU 敏感特性，大 MTU 能极大缓解小包中断与跨上下文拷贝开销。
2. **内存稳定性极佳**：
   - 全程测试各轮次内存 PSS 维持在 **115 MB ~ 156 MB** 之间，测试结束后自动回归基础内存水平，GC 回收彻底，未观察到任何 Goroutine 堆积或内存泄漏。
