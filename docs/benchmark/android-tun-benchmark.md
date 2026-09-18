# SimpleXray Android TUN 性能基准测试报告

本文档记录对 SimpleXray 支持的四种透明代理 TUN 协议栈实现 `hev-socks5-tunnel`、`Xray Native TUN`、`SingTUN` 与 `MipsTUN` (Mihomo mipstack)，在 **Speed**、**CPU usage** 以及 **Memory usage** 维度的对比分析。

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

#### (1) USB 3.2 Gen1 / 4.0 有线以太网测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 309.40 Mbps | 396.83 Mbps | 0% / 0% | 119.1 MB |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 293.87 Mbps | 429.45 Mbps | 0% / 0% | 119.1 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 339.31 Mbps | 262.15 Mbps | 37.3% / 74.0% | 119.6 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 334.37 Mbps | 58.96 Mbps | 89.3% / 190.7% | 119.3 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | 单流 | 356.41 Mbps | 165.38 Mbps | 41.7% / 138.0% | 123.4 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 582.56 Mbps | 227.58 Mbps | 110.3% / 199.7% | 137.5 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 355.75 Mbps | 346.50 Mbps | 27.3% / 70.0% | 112.4 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 325.34 Mbps | 62.35 Mbps | 89.8% / 195.0% | 111.9 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | 单流 | 327.61 Mbps | 344.63 Mbps | 26.6% / 108.0% | 119.5 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 655.88 Mbps | 326.94 Mbps | 61.3% / 133.3% | 135.2 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 312.85 Mbps | 262.95 Mbps | 36.1% / 89.3% | 116.4 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 353.00 Mbps | 333.85 Mbps | 97.9% / 346.0% | 116.3 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | P=8 | 215.46 Mbps | 390.62 Mbps | 63.2% / 214.7% | 121.8 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 657.47 Mbps | 166.54 Mbps | 117.5% / 241.0% | 142.1 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 337.93 Mbps | 335.27 Mbps | 27.6% / 91.0% | 121.6 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 362.62 Mbps | 152.34 Mbps | 99.9% / 216.3% | 121.0 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | P=8 | 320.11 Mbps | 391.80 Mbps | 31.1% / 129.3% | 151.0 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 668.21 Mbps | 372.83 Mbps | 64.4% / 191.0% | 145.5 MB |

---

#### (2) 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 372.08 Mbps | 387.23 Mbps | 0% / 0% | 99.6 MB |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 414.49 Mbps | 433.80 Mbps | 0.1% / 1.7% | 99.6 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 346.99 Mbps | 329.50 Mbps | 23.9% / 82.0% | 102.6 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 251.65 Mbps | 58.08 Mbps | 82.4% / 183.0% | 101.7 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | 单流 | 367.32 Mbps | 148.32 Mbps | 32.2% / 143.7% | 110.6 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 256.16 Mbps | 230.48 Mbps | 40.0% / 194.7% | 125.3 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 377.79 Mbps | 378.87 Mbps | 17.0% / 56.0% | 107.1 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 269.71 Mbps | 74.61 Mbps | 70.6% / 172.7% | 106.9 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | 单流 | 356.07 Mbps | 376.85 Mbps | 23.5% / 104.0% | 111.7 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 302.91 Mbps | 258.69 Mbps | 31.4% / 113.4% | 119.4 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 392.57 Mbps | 250.95 Mbps | 30.3% / 86.7% | 108.6 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 357.63 Mbps | 163.51 Mbps | 111.5% / 242.7% | 108.6 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | P=8 | 376.81 Mbps | 446.14 Mbps | 42.1% / 242.3% | 144.1 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 342.46 Mbps | 145.23 Mbps | 46.5% / 196.7% | 146.0 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 397.57 Mbps | 441.80 Mbps | 18.7% / 60.7% | 112.7 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 351.56 Mbps | 179.53 Mbps | 111.5% / 243.0% | 112.3 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | P=8 | 423.52 Mbps | 458.91 Mbps | 27.0% / 125.7% | 146.2 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 327.67 Mbps | 282.51 Mbps | 31.0% / 197.3% | 156.3 MB |

---

#### (3) 设备内部纯回环测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 极限吞吐 (Gbps) | 极限吞吐 (Mbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | 单流 | 20.48 Gbps | 20,480 Mbps | 0% | 113.8 MB |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | P=8 | 17.88 Gbps | 17,880 Mbps | 0% | 113.0 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 18.68 Gbps | 18,680 Mbps | 4.9% | 113.5 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 17.25 Gbps | 17,250 Mbps | 1.7% | 113.5 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | 单流 | 17.53 Gbps | 17,530 Mbps | 3.7% | 113.6 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 20.49 Gbps | 20,490 Mbps | 12.8% | 125.4 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 17.77 Gbps | 17,770 Mbps | 6.0% | 113.7 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 17.83 Gbps | 17,830 Mbps | 2.2% | 114.4 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | 单流 | 17.87 Gbps | 17,870 Mbps | 4.8% | 113.7 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 20.78 Gbps | 20,780 Mbps | 16.3% | 115.7 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 18.07 Gbps | 18,070 Mbps | 3.4% | 114.2 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 18.15 Gbps | 18,150 Mbps | 1.3% | 113.8 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | P=8 | 18.17 Gbps | 18,170 Mbps | 3.5% | 113.8 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 18.38 Gbps | 18,380 Mbps | 3.8% | 107.9 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 18.25 Gbps | 18,250 Mbps | 3.4% | 113.9 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 18.20 Gbps | 18,200 Mbps | 1.3% | 113.9 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | P=8 | 18.08 Gbps | 18,080 Mbps | 4.7% | 113.9 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 18.25 Gbps | 18,250 Mbps | 6.7% | 121.0 MB |

---

### 3.2 分轮实测数据明细

<details>
<summary><b>第 1 轮测试数据</b></summary>

#### (1) USB 3.2 Gen1 / 4.0 有线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 314.25 Mbps | 398.60 Mbps | 0% / 0% | 115.7 MB |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 283.86 Mbps | 429.13 Mbps | 0% / 0% | 115.7 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 337.91 Mbps | 267.57 Mbps | 37.6% / 75.0% | 116.2 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 324.92 Mbps | 60.50 Mbps | 90.0% / 196.0% | 115.8 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | 单流 | 353.91 Mbps | 168.59 Mbps | 42.0% / 142.0% | 119.8 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 641.34 Mbps | 230.97 Mbps | 110.8% / 206.0% | 130.5 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 357.52 Mbps | 341.51 Mbps | 27.9% / 69.0% | 94.2 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 324.61 Mbps | 61.75 Mbps | 89.5% / 200.0% | 93.3 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | 单流 | 326.37 Mbps | 359.52 Mbps | 26.4% / 118.0% | 106.6 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 657.07 Mbps | 324.26 Mbps | 61.2% / 165.0% | 131.9 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 309.69 Mbps | 263.11 Mbps | 35.6% / 84.0% | 103.7 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 352.91 Mbps | 336.82 Mbps | 96.8% / 354.0% | 103.7 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | P=8 | 217.63 Mbps | 372.66 Mbps | 65.2% / 189.0% | 115.3 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 676.90 Mbps | 149.93 Mbps | 116.1% / 228.0% | 148.5 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 338.78 Mbps | 404.33 Mbps | 29.0% / 97.0% | 115.3 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 363.18 Mbps | 157.63 Mbps | 99.9% / 218.0% | 114.7 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | P=8 | 321.48 Mbps | 396.78 Mbps | 30.7% / 135.0% | 144.6 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 696.22 Mbps | 351.65 Mbps | 64.8% / 214.0% | 146.7 MB |

---

#### (2) 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 323.47 Mbps | 339.59 Mbps | 0% / 0% | 97.5 MB |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 392.41 Mbps | 375.58 Mbps | 0% / 1.0% | 97.4 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 327.67 Mbps | 299.44 Mbps | 25.5% / 82.0% | 98.0 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 260.27 Mbps | 59.24 Mbps | 83.6% / 203.0% | 98.1 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | 单流 | 332.83 Mbps | 148.46 Mbps | 33.4% / 139.0% | 106.8 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 246.29 Mbps | 228.98 Mbps | 34.4% / 200.0% | 111.8 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 339.83 Mbps | 350.18 Mbps | 14.0% / 56.0% | 103.4 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 321.27 Mbps | 95.31 Mbps | 47.9% / 181.0% | 103.2 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | 单流 | 275.27 Mbps | 330.58 Mbps | 21.5% / 85.0% | 109.0 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 254.76 Mbps | 279.52 Mbps | 25.0% / 115.0% | 127.9 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 330.22 Mbps | 252.10 Mbps | 40.3% / 99.0% | 106.2 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 359.64 Mbps | 136.25 Mbps | 101.4% / 244.0% | 106.2 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | P=8 | 353.77 Mbps | 405.60 Mbps | 39.6% / 239.0% | 142.0 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 305.01 Mbps | 137.98 Mbps | 44.4% / 203.0% | 149.9 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 376.35 Mbps | 382.75 Mbps | 22.1% / 69.0% | 110.4 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 344.78 Mbps | 179.04 Mbps | 101.5% / 238.0% | 110.2 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | P=8 | 422.87 Mbps | 453.14 Mbps | 29.0% / 130.0% | 142.9 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 323.12 Mbps | 291.17 Mbps | 32.8% / 218.0% | 155.2 MB |

---

#### (3) 设备内部纯回环测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 极限吞吐 (Gbps) | 极限吞吐 (Mbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | 单流 | 20.22 Gbps | 20,220 Mbps | 0% | 110.5 MB |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | P=8 | 17.78 Gbps | 17,780 Mbps | 0% | 110.5 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 20.72 Gbps | 20,720 Mbps | 7.4% | 111.1 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 20.61 Gbps | 20,610 Mbps | 2.0% | 111.1 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | 单流 | 20.93 Gbps | 20,930 Mbps | 3.7% | 111.2 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 20.80 Gbps | 20,800 Mbps | 10.0% | 123.2 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 20.58 Gbps | 20,580 Mbps | 7.4% | 111.3 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 20.84 Gbps | 20,840 Mbps | 2.0% | 113.6 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | 单流 | 21.02 Gbps | 21,020 Mbps | 3.7% | 111.3 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 21.38 Gbps | 21,380 Mbps | 17.8% | 103.5 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 17.78 Gbps | 17,780 Mbps | 7.1% | 112.7 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 17.92 Gbps | 17,920 Mbps | 2.0% | 111.5 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | P=8 | 18.02 Gbps | 18,020 Mbps | 3.5% | 111.5 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 18.37 Gbps | 18,370 Mbps | 3.4% | 104.5 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 18.19 Gbps | 18,190 Mbps | 1.0% | 111.6 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 18.26 Gbps | 18,260 Mbps | 1.0% | 111.6 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | P=8 | 18.13 Gbps | 18,130 Mbps | 7.1% | 111.6 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 18.34 Gbps | 18,340 Mbps | 7.0% | 119.1 MB |

</details>

<br>

<details>
<summary><b>第 2 轮测试数据</b></summary>

#### (1) USB 3.2 Gen1 / 4.0 有线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 308.07 Mbps | 399.15 Mbps | 0% / 0% | 117.7 MB |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 300.68 Mbps | 440.82 Mbps | 0% / 0% | 117.6 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 342.99 Mbps | 250.79 Mbps | 37.2% / 75.0% | 118.1 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 328.58 Mbps | 61.75 Mbps | 85.2% / 176.0% | 117.8 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | 单流 | 360.33 Mbps | 162.62 Mbps | 41.8% / 134.0% | 122.0 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 514.57 Mbps | 229.82 Mbps | 108.9% / 196.0% | 138.5 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 350.81 Mbps | 329.84 Mbps | 27.1% / 72.0% | 118.4 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 328.09 Mbps | 64.69 Mbps | 90.8% / 200.0% | 118.2 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | 单流 | 332.26 Mbps | 339.27 Mbps | 25.8% / 111.0% | 123.6 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 655.55 Mbps | 329.13 Mbps | 60.1% / 168.0% | 129.9 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 313.86 Mbps | 260.80 Mbps | 38.6% / 97.0% | 120.4 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 352.73 Mbps | 327.51 Mbps | 99.0% / 310.0% | 120.3 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | P=8 | 213.85 Mbps | 399.46 Mbps | 62.8% / 218.0% | 124.3 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 647.35 Mbps | 174.74 Mbps | 119.3% / 270.0% | 133.0 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 336.48 Mbps | 362.02 Mbps | 26.4% / 96.0% | 123.9 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 362.49 Mbps | 143.48 Mbps | 100.5% / 218.0% | 123.3 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | P=8 | 318.54 Mbps | 399.10 Mbps | 31.0% / 137.0% | 153.0 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 660.64 Mbps | 397.73 Mbps | 66.6% / 170.0% | 139.5 MB |

---

#### (2) 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 396.34 Mbps | 417.81 Mbps | 0% / 0% | 87.7 MB |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 425.65 Mbps | 462.63 Mbps | 0% / 3.0% | 87.8 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 395.24 Mbps | 345.05 Mbps | 23.7% / 84.0% | 95.9 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 250.02 Mbps | 57.98 Mbps | 81.1% / 171.0% | 93.4 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | 单流 | 386.69 Mbps | 150.66 Mbps | 31.0% / 149.0% | 106.3 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 266.91 Mbps | 239.68 Mbps | 46.3% / 205.0% | 131.5 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 396.12 Mbps | 377.95 Mbps | 15.6% / 55.0% | 103.0 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 241.69 Mbps | 65.00 Mbps | 80.6% / 167.0% | 102.7 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | 单流 | 395.86 Mbps | 392.43 Mbps | 27.0% / 112.0% | 107.1 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 345.53 Mbps | 262.95 Mbps | 34.2% / 133.0% | 114.7 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 423.99 Mbps | 249.53 Mbps | 23.7% / 80.0% | 104.2 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 354.71 Mbps | 181.18 Mbps | 115.6% / 241.0% | 104.2 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | P=8 | 393.90 Mbps | 463.68 Mbps | 42.1% / 238.0% | 142.7 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 343.21 Mbps | 146.21 Mbps | 44.6% / 190.0% | 152.6 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 424.58 Mbps | 468.92 Mbps | 18.6% / 54.0% | 111.6 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 356.63 Mbps | 178.60 Mbps | 116.9% / 246.0% | 110.9 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | P=8 | 423.79 Mbps | 461.89 Mbps | 28.2% / 119.0% | 145.2 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 315.09 Mbps | 312.13 Mbps | 30.5% / 185.0% | 154.1 MB |

---

#### (3) 设备内部纯回环测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 极限吞吐 (Gbps) | 极限吞吐 (Mbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | 单流 | 19.93 Gbps | 19,930 Mbps | 0% | 112.9 MB |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | P=8 | 17.87 Gbps | 17,870 Mbps | 0% | 112.8 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 18.55 Gbps | 18,550 Mbps | 3.7% | 113.2 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 17.11 Gbps | 17,110 Mbps | 2.0% | 113.3 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | 单流 | 16.39 Gbps | 16,390 Mbps | 3.5% | 113.4 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 21.07 Gbps | 21,070 Mbps | 10.0% | 121.8 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 16.51 Gbps | 16,510 Mbps | 3.5% | 113.6 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 16.15 Gbps | 16,150 Mbps | 3.5% | 113.5 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | 单流 | 16.32 Gbps | 16,320 Mbps | 7.1% | 113.6 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 21.05 Gbps | 21,050 Mbps | 16.0% | 122.0 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 18.22 Gbps | 18,220 Mbps | 0% | 113.7 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 18.19 Gbps | 18,190 Mbps | 1.0% | 113.8 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | P=8 | 18.29 Gbps | 18,290 Mbps | 3.5% | 113.8 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 18.53 Gbps | 18,530 Mbps | 4.0% | 119.0 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 18.31 Gbps | 18,310 Mbps | 2.0% | 114.0 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 18.21 Gbps | 18,210 Mbps | 1.0% | 113.9 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | P=8 | 17.93 Gbps | 17,930 Mbps | 3.5% | 113.9 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 18.30 Gbps | 18,300 Mbps | 6.0% | 121.9 MB |

</details>

<br>

<details>
<summary><b>第 3 轮测试数据</b></summary>

#### (1) USB 3.2 Gen1 / 4.0 有线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 305.89 Mbps | 392.75 Mbps | 0% / 0% | 124.0 MB |
| **USB 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 297.07 Mbps | 418.41 Mbps | 0% / 0% | 123.9 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 337.04 Mbps | 268.08 Mbps | 37.2% / 72.0% | 124.4 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 349.61 Mbps | 54.62 Mbps | 92.8% / 200.0% | 124.2 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | 单流 | 354.99 Mbps | 164.92 Mbps | 41.4% / 138.0% | 128.3 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 591.76 Mbps | 221.95 Mbps | 111.1% / 197.0% | 143.6 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 358.92 Mbps | 368.15 Mbps | 26.8% / 69.0% | 124.5 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 323.32 Mbps | 60.60 Mbps | 89.0% / 185.0% | 124.3 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | 单流 | 324.20 Mbps | 335.09 Mbps | 27.5% / 95.0% | 128.2 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 655.01 Mbps | 327.43 Mbps | 62.7% / 171.0% | 143.9 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 315.01 Mbps | 264.94 Mbps | 34.2% / 87.0% | 125.0 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 353.36 Mbps | 337.23 Mbps | 97.9% / 374.0% | 124.8 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | P=8 | 214.89 Mbps | 399.74 Mbps | 61.7% / 237.0% | 125.7 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 648.16 Mbps | 174.94 Mbps | 117.0% / 225.0% | 144.7 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 338.52 Mbps | 239.47 Mbps | 27.3% / 80.0% | 125.5 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 362.19 Mbps | 155.91 Mbps | 99.3% / 213.0% | 124.9 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | P=8 | 320.31 Mbps | 379.51 Mbps | 31.6% / 116.0% | 155.4 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 647.76 Mbps | 369.11 Mbps | 61.7% / 189.0% | 150.3 MB |

---

#### (2) 5GHz Wi-Fi 无线网络测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | 单流 | 396.44 Mbps | 404.28 Mbps | 0% / 0% | 113.6 MB |
| **Wi-Fi 物理基准 (No VPN)** | 原生网络栈 | - | P=8 | 425.40 Mbps | 463.20 Mbps | 0% / 1.0% | 113.5 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 318.05 Mbps | 344.01 Mbps | 22.5% / 80.0% | 114.0 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 244.65 Mbps | 57.03 Mbps | 82.5% / 175.0% | 113.7 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | 单流 | 382.44 Mbps | 145.83 Mbps | 32.1% / 143.0% | 118.8 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 255.28 Mbps | 222.79 Mbps | 39.4% / 190.0% | 132.6 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 397.42 Mbps | 408.47 Mbps | 21.4% / 57.0% | 115.0 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 246.17 Mbps | 63.53 Mbps | 83.2% / 170.0% | 114.9 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | 单流 | 397.09 Mbps | 407.53 Mbps | 22.0% / 115.0% | 118.9 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 308.45 Mbps | 233.60 Mbps | 34.9% / 111.0% | 115.6 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 423.49 Mbps | 251.21 Mbps | 26.9% / 81.0% | 115.5 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 358.54 Mbps | 173.10 Mbps | 117.4% / 243.0% | 115.4 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | P=8 | 382.75 Mbps | 469.13 Mbps | 44.5% / 250.0% | 147.7 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 379.15 Mbps | 151.50 Mbps | 50.4% / 197.0% | 135.4 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 391.79 Mbps | 473.74 Mbps | 15.5% / 59.0% | 116.1 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 353.28 Mbps | 180.96 Mbps | 116.1% / 245.0% | 115.7 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | P=8 | 423.91 Mbps | 461.69 Mbps | 23.8% / 128.0% | 150.5 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 344.80 Mbps | 244.23 Mbps | 29.8% / 189.0% | 159.7 MB |

---

#### (3) 设备内部纯回环测试

| 测试用例 / 配置 | 后端协议栈 | MTU 配置 | 流模式 | 极限吞吐 (Gbps) | 极限吞吐 (Mbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | 单流 | 21.29 Gbps | 21,290 Mbps | 0% | 118.1 MB |
| **回环基准 (No VPN)** | 原生 Linux 网络栈 | - | P=8 | 17.99 Gbps | 17,990 Mbps | 0% | 115.7 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | 单流 | 16.76 Gbps | 16,760 Mbps | 3.7% | 116.1 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | 单流 | 14.04 Gbps | 14,040 Mbps | 1.0% | 116.2 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | 单流 | 15.28 Gbps | 15,280 Mbps | 4.0% | 116.2 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | 单流 | 19.59 Gbps | 19,590 Mbps | 18.5% | 131.1 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | 单流 | 16.22 Gbps | 16,220 Mbps | 7.1% | 116.3 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | 单流 | 16.50 Gbps | 16,500 Mbps | 1.0% | 116.1 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | 单流 | 16.28 Gbps | 16,280 Mbps | 3.5% | 116.1 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | 单流 | 19.90 Gbps | 19,900 Mbps | 15.0% | 121.7 MB |
| **Hev (MTU 1500)** | `hev-socks5-tunnel` | 1500 | P=8 | 18.20 Gbps | 18,200 Mbps | 3.2% | 116.2 MB |
| **Xray TUN (MTU 1500)** | `xray-core` | 1500 | P=8 | 18.33 Gbps | 18,330 Mbps | 1.0% | 116.1 MB |
| **SingTUN (MTU 1500)** | `sing-box tun` | 1500 | P=8 | 18.21 Gbps | 18,210 Mbps | 3.4% | 116.1 MB |
| **MipsTUN (MTU 1500)** | `mipstack (BBRv3)` | 1500 | P=8 | 18.24 Gbps | 18,240 Mbps | 4.0% | 100.2 MB |
| **Hev (MTU 8500)** | `hev-socks5-tunnel` | 8500 | P=8 | 18.25 Gbps | 18,250 Mbps | 7.1% | 116.2 MB |
| **Xray TUN (MTU 8500)** | `xray-core` | 8500 | P=8 | 18.12 Gbps | 18,120 Mbps | 2.0% | 116.1 MB |
| **SingTUN (MTU 8500)** | `sing-box tun` | 8500 | P=8 | 18.19 Gbps | 18,190 Mbps | 3.5% | 116.2 MB |
| **MipsTUN (MTU 8500)** | `mipstack (BBRv3)` | 8500 | P=8 | 18.12 Gbps | 18,120 Mbps | 7.0% | 122.1 MB |

</details>


---

## 4. 数据可视化

### 4.1 综合平均可视化图表 (3 轮平均)

#### (1) 传输速度

* **USB 3.2 Gen1 / 4.0 有线以太网吞吐量 (Mbps)**
![USB Upload Speed](../images/avg_usb_speed_upload.webp)
![USB Download Speed](../images/avg_usb_speed_download.webp)

* **5GHz Wi-Fi 无线网络吞吐量 (Mbps)**
![Wi-Fi Upload Speed](../images/avg_wifi_speed_upload.webp)
![Wi-Fi Download Speed](../images/avg_wifi_speed_download.webp)

* **设备内部回环核心协议处理吞吐量 (Gbps)**
![Loopback Processing Speed](../images/avg_loopback_speed.webp)

---

#### (2) 处理器占用率 (%)

![Upload CPU Usage](../images/avg_cpu_usage_upload.webp)
![Download CPU Usage](../images/avg_cpu_usage_download.webp)

---

#### (3) 内存占用 (MB PSS)

![Upload Memory Usage](../images/avg_memory_usage_upload.webp)
![Download Memory Usage](../images/avg_memory_usage_download.webp)

---

### 4.2 分轮可视化图表明细

<details>
<summary><b>第 1 轮测试可视化图表</b></summary>

#### (1) 传输速度

* **USB 3.2 Gen1 / 4.0 有线以太网吞吐量 (Mbps)**
![USB Upload Speed](../images/r1_usb_speed_upload.webp)
![USB Download Speed](../images/r1_usb_speed_download.webp)

* **5GHz Wi-Fi 无线网络吞吐量 (Mbps)**
![Wi-Fi Upload Speed](../images/r1_wifi_speed_upload.webp)
![Wi-Fi Download Speed](../images/r1_wifi_speed_download.webp)

* **设备内部回环核心协议处理吞吐量 (Gbps)**
![Loopback Processing Speed](../images/r1_loopback_speed.webp)

---

#### (2) 处理器占用率 (%)

![Upload CPU Usage](../images/r1_cpu_usage_upload.webp)
![Download CPU Usage](../images/r1_cpu_usage_download.webp)

---

#### (3) 内存占用 (MB PSS)

![Upload Memory Usage](../images/r1_memory_usage_upload.webp)
![Download Memory Usage](../images/r1_memory_usage_download.webp)

</details>

<br>

<details>
<summary><b>第 2 轮测试可视化图表</b></summary>

#### (1) 传输速度

* **USB 3.2 Gen1 / 4.0 有线以太网吞吐量 (Mbps)**
![USB Upload Speed](../images/r2_usb_speed_upload.webp)
![USB Download Speed](../images/r2_usb_speed_download.webp)

* **5GHz Wi-Fi 无线网络吞吐量 (Mbps)**
![Wi-Fi Upload Speed](../images/r2_wifi_speed_upload.webp)
![Wi-Fi Download Speed](../images/r2_wifi_speed_download.webp)

* **设备内部回环核心协议处理吞吐量 (Gbps)**
![Loopback Processing Speed](../images/r2_loopback_speed.webp)

---

#### (2) 处理器占用率 (%)

![Upload CPU Usage](../images/r2_cpu_usage_upload.webp)
![Download CPU Usage](../images/r2_cpu_usage_download.webp)

---

#### (3) 内存占用 (MB PSS)

![Upload Memory Usage](../images/r2_memory_usage_upload.webp)
![Download Memory Usage](../images/r2_memory_usage_download.webp)

</details>

<br>

<details>
<summary><b>第 3 轮测试可视化图表</b></summary>

#### (1) 传输速度

* **USB 3.2 Gen1 / 4.0 有线以太网吞吐量 (Mbps)**
![USB Upload Speed](../images/r3_usb_speed_upload.webp)
![USB Download Speed](../images/r3_usb_speed_download.webp)

* **5GHz Wi-Fi 无线网络吞吐量 (Mbps)**
![Wi-Fi Upload Speed](../images/r3_wifi_speed_upload.webp)
![Wi-Fi Download Speed](../images/r3_wifi_speed_download.webp)

* **设备内部回环核心协议处理吞吐量 (Gbps)**
![Loopback Processing Speed](../images/r3_loopback_speed.webp)

---

#### (2) 处理器占用率 (%)

![Upload CPU Usage](../images/r3_cpu_usage_upload.webp)
![Download CPU Usage](../images/r3_cpu_usage_download.webp)

---

#### (3) 内存占用 (MB PSS)

![Upload Memory Usage](../images/r3_memory_usage_upload.webp)
![Download Memory Usage](../images/r3_memory_usage_download.webp)

</details>


---


## 5. 分析

综合三轮数据，进行分析：

### 5.1 `hev-socks5-tunnel` (C / lwIP 协议栈)

* **吞吐表现**：
  - 在 **MTU 8500** 巨型帧模式下，Hev 表现出优秀的吞吐能力，5GHz Wi-Fi 下行达到 **382 \~ 473 Mbps**，USB 3.2 有线下行达到 **362 \~ 404 Mbps**，基本跑满当前硬件链路。
  - 在 **MTU 1500** 单流模式下，下行吞吐维持在 **250 \~ 345 Mbps**，表现较为平稳。
* **资源开销与优势**：
  - **能效比居全场首位**：得益于纯 C 语言编写与精简裁剪版 lwIP，常规单流及多流传输下的平均 CPU 占用率仅为 **14% \~ 38%**，峰值稳定在 **55% \~ 97%**，发热和能耗控制极佳。
  - **内存平稳**：PSS 基本稳定在 **94 \~ 124 MB** 区间，无垃圾回收引起的内存抖动与暂停。
* **主要局限与缺点**：
  - **多流下行吞吐收窄**：受限于 lwIP 单线程协程调度与事件驱动架构，在 MTU 1500 多流（P=8）并发下行时出现明显的吞吐瓶颈，Wi-Fi 下行收窄至 **\~250 Mbps**，无法有效利用多核 CPU 算力进一步突破信道瓶颈。
  - **维护与工程成本高**：强依赖包含成百上千个 C 源文件的 Git 子模块及复杂的 CMake 构建链；且启动前必须在磁盘临时写入 `tproxy.conf`，存在文件 I/O 开销。

### 5.2 `SingTUN` (Go / sing-box 专有自研栈)

* **吞吐表现**：
  - **高并发多流调度性能优异**：在 **MTU 1500** 默认帧下，8 并发下行吞吐达到 **405 \~ 469 Mbps**（Wi-Fi）与 **372 \~ 399 Mbps**（USB），比 Hev 同配置高出约 **60% \~ 80%**，多核调度优势明显。
  - 在 **MTU 8500** 模式下，单流与多流下行均可达到 **450 \~ 470 Mbps**，同样能够打满无线链路的物理上限。
* **资源开销与优势**：
  - 核心转发路径基于 Slab 槽位池与无锁环形缓冲区实现低内存分配，空闲及常规单流传输下常驻内存仅在 **106 \~ 128 MB** 之间，相比 Hev 增量不足 10 MB。
  - 架构高度内聚，通过单个轻量动态库直接与 `VpnService` 的文件描述符 `vpnFd` 进行 JNI 内存交互，消除了磁盘配置文件与外部进程启动开销。
* **主要局限与缺点**：
  - **CPU 算力与功耗开销较高**：为维持高并发满速吞吐，多核并发协程与 Go runtime 带来了显著的算力开销。单流平均 CPU 为 **21% \~ 42%**；在 8 流满载冲刺 470 Mbps 时，多核峰值占用达 **120% \~ 250%**，占用了 1 \~ 2 个大核满负荷算力，在持续高带宽传输下的设备发热与电池消耗显著高于 Hev。
  - **内存峰值存在动态扩容弹性**：在高负载并发冲击下，Go runtime 堆内存策略会导致短时扩容，PSS 短暂上升至 **142 \~ 155 MB**，虽在压力释放后能被 GC 迅速回收，但峰值占用仍高于纯 C 实现。
  - **新栈成熟度需持续跟踪**：作为较新引入的协议栈，边缘稳定性仍有待更长周期的验证。

### 5.3 `Xray Native TUN` (Go / gVisor netstack)

* **吞吐表现**：
  - **下行性能存在严重瓶颈**：单流下行无论在 Wi-Fi 还是 USB 有线环境下，均受困于 **54 \~ 95 Mbps** 的低速水平；8 并发下行也仅能达到 **136 \~ 181 Mbps**，与前两个后端存在数倍性能差距。
* **资源开销与技术缺陷**：
  - **算力产出比严重倒挂**：在下行吞吐仅 50 \~ 60 Mbps 的低产出下，平均 CPU 占用率仍高达 **50% \~ 100%**，多流并发峰值甚至高达 **170% \~ 374%**，占满 3 \~ 4 个 CPU 核心。
  - **原因**：Xray 原生 TUN 所集成的 Google gVisor 网络栈设计初衷为云原生容器的安全沙箱隔离，并非面向移动平台低功耗网络转发优化。在 Android 上解包海量网络小包时，高频的用户态上下文切换、大量切片对象分配与 Go GC 造成了极大的算力空转。

### 5.4 `MipsTUN` (Go / Mihomo mipstack 纯 Go 协议栈)

* **吞吐表现**：
  - **USB 有线上行释放充分，接近物理瓶颈**：在 USB 3.2 Gen1 有线以太网环境下，MipsTUN 上行表现最为突出。单流在 MTU 1500 下达到 **582.56 Mbps**，在 MTU 8500 下达到 **655.88 Mbps**；8 多流并发稳定在 **657 \~ 668 Mbps**，基本达到硬件总线有效数据载荷的上限（约 90% \~ 93% 物理基准释放率）。
  - **下行性能均衡**：在 MTU 8500 巨型帧下，单流下行达到 **326.94 Mbps**，8 多流达到 **372.83 Mbps**，与 Hev 相当，显著超越 Xray 原生 TUN。
  - **5GHz Wi-Fi 环境适应性强**：在无线信道下，MipsTUN MTU 8500 上行达到 **302.91 Mbps**（多流 **327.67 Mbps**），下行达到 **258.69 Mbps**（多流 **282.51 Mbps**）。得益于内核默认启用的 `BBRv3` 算法，在应对高频无线信道波动和轻微抖动时具有出色的平滑适应性。
  - **极限回环性能达到 20.78 Gbps**：在不经过物理层传输的设备内部压力测试中，MipsTUN 测得 **20.78 Gbps (20,775 Mbps)**，完全达到了设备原生 Linux 套接字 loopback 物理极限，证明其用户态网络栈在吞吐架构上无瓶颈。
* **资源开销与优势**：
  - **纯用户态 JNI 驱动集成**：通过 Linux netpoller (epoll) 无缝对接 Android `VpnService` 的非阻塞文件描述符，消除了临时配置文件与外部进程 IPC 开销。
  - **内存稳定性与 GC 表现优异**：全程测试各轮次常驻内存稳定在 **115 \~ 156 MB** 区间，高并发压测结束后内存回收迅速，未观察到任何 Goroutine 泄露或内存堆积。
* **主要局限与缺点**：
  - **小包算力负载敏感**：在 MTU 1500 默认帧下处理高速传输时，单流平均 CPU 为 **40% \~ 110%**，峰值达 **190% \~ 240%**；当开启 MTU 8500 后，平均 CPU 显著下降至 **31% \~ 64%**。这表明协议栈在应对密集小数据包时，用户态封包解包的单包 CPU 消耗较为明显。
  - **MTU 1500 多流下行存在收窄**：在 MTU 1500 且 8 并发下行时，USB 与 Wi-Fi 吞吐收窄至 **145 \~ 166 Mbps**，建议在支持的环境下配置更大 MTU 以获得最佳高并发吞吐体验。

---

## 6. Raw Output

请看 [benchmark_summary.md](./benchmark_summary.md) 及 [mips_benchmark_summary.md](./mips_benchmark_summary.md)