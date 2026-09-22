# SimpleXray Android TUN 性能基准测试报告

本文档记录高通骁龙 8 第五代至尊版旗舰设备上四种透明代理 TUN 协议栈实现 `hev-socks5-tunnel`、`Xray Native TUN`、`SingTUN` 与 `MipsTUN` 在**吞吐量**、**CPU 占用率**、**能效比**以及**空闲连接驻留内存增长**维度的客观实测。

---

## 1. 测试环境与硬件规格

### 测试主机（iPerf3 Server / PC）
- **操作系统**: Linux x86_64 (Linux 6.12.107+deb13-amd64)
- **处理器**: AMD Ryzen 7 6800H @ 3.2GHz (8 核 16 线程)
- **无线网卡**: Intel(R) Wi-Fi 6E AX210 160MHz
- **有线网卡**: 千兆以太网接口，连接千兆路由器 LAN 口，协商速率 1 Gbps
- **USB 接口**: USB Type-C 物理接口，基于 RNDIS 网络共享虚拟以太网，协商速率为 1 Gbps；线材支持 USB 3.2 Gen1 / USB4 高规格，不构成物理瓶颈。
- **iPerf3 版本**: 3.18 (cJSON 1.7.15)

### Android 测试设备（iPerf3 Client / DUT）
- **操作系统**: Android 16
- **处理器**: 高通第五代骁龙 8 至尊版（Snapdragon 8 Elite Gen 5；第三代 Qualcomm Oryon；8 核，2×Prime Core + 6×Performance Core，最高 4.6 GHz）
- **无线规格**: Wi-Fi 7（802.11be），2×2 MIMO；支持 Wi-Fi 6E / Wi-Fi 6。(连接至千兆路由器 5GHz Wi-Fi 6，物理协商速率 1201 Mbps)
- **有线接口**: USB Type-C 物理连接，基于 RNDIS 网络共享虚拟以太网，实际系统链路协商速率为 1 Gbps；线材支持 USB 3.2 Gen1 / USB4 高规格，不构成物理瓶颈。
- **iPerf3 版本**: 3.21 (aarch64 静态编译版)

### 局域网网关与路由设备
- **网关设备**: 家用千兆 Wi-Fi 6 无线路由器 ( RTL8197H-单核1.0G CPU、128MB RAM)
- **网络频段**: 5GHz Wi-Fi 6 (80MHz 频宽，HE80，物理协商速率 1201 Mbps)
- **互联拓扑**: 测试主机通过千兆有线以太网直连路由器 LAN 口；Android 设备通过 5GHz Wi-Fi 6 无线接入路由器。

---

## 2. 测试方法

### 2.1 组件版本
- **测试应用**: SimpleXray (Debug build)
- **Xray 核心**: Xray-core v26.9.9 (Android arm64)
- **SingTUN 后端**: sing-tun `a39eab51450b`
- **MipsTUN 后端**: MetaCubeX mipstack `802d64336f8c`
- **Hev 后端**: hev-socks5-tunnel `b514150` (2.17.1)

### 2.2 测试链路
测试全程采用 **局域网 Direct/Freedom 纯透明代理** 链路，流量经由 Android 系统 `VpnService` 虚拟网卡 (`tun0`) 路由至各 TUN 协议栈处理，并直连 PC 出站。

#### Wi-Fi 6 无线测试拓扑
```text
           [ 局域网千兆 Wi-Fi 6 路由器 (网关 / AP) ]
                  │                          │
      5GHz Wi-Fi 6│                          │ 千兆有线以太网 (RJ45)
      (1201 Mbps) ▼                          ▼ (1 Gbps)
        [ Android 设备 (DUT) ]       [ PC 主机 (Server) ]
          iperf3 client 进程          iperf3 server 进程 (:5201)
                 │                            ▲
            Android VpnService (tun0)         │
                 │                            │
           ┌─────┴─────────────────────┐      │
           │ (协议栈后端切换)             │      │
           ▼          ▼        ▼       ▼      │
         [Hev]    [SingTUN] [MipsTUN] [Xray 原生 TUN]
        (lwIP)    (pure go)  (BBRv3)   (gVisor)
           │          │        │       │      │
       Xray SOCKS5   ...      ...      │      │
         Inbound                       │      │
           │                           │      │
           └──────────┬────────────────┘      │
                      ▼                       │
            Xray Freedom Outbound ────────────┘
```

#### USB 有线测试拓扑
```text
               [ USB 3.2 Gen1 / USB4 物理线缆 (RNDIS 1Gbps) ]
                      │                                      │
                      ▼                                      ▼
        [ Android 设备 (DUT) ]                       [ PC 主机 (Server) ]
          iperf3 client 进程                          iperf3 server 进程 (:5201)
                 │                                           ▲
            Android VpnService (tun0)                        │
                 │                                           │
           ┌─────┴─────────────────────────────────────┐     │
           │ (协议栈后端切换)                             │     │
           ▼              ▼            ▼               ▼     │
     [   Hev  ]      [ SingTUN ]  [ MipsTUN ]     [ Xray 原生 TUN ]
hev-socks5-tunnel      SingTUN      mipstack      Xray TUN Inbound
       (C/lwIP)       (pure go)     (BBRv3)          (gVisor)
           │              │            │               │     │
      Xray SOCKS5    Xray SOCKS5  Xray SOCKS5          │     │
        Inbound        Inbound      Inbound            │     │
           │              │            │               │     │
           └──────────────┴────────────┴───────────────┘     │
                               │                             │
                               ▼                             │
                      Xray Freedom Outbound ─────────────────┘
```

### 2.3 iPerf3 测试参数
- **TCP 测试规范**：单流（`-P 1`）与多流（`-P 8`）并发；测试持续时长 5 秒（`-t 5`），采样间隔 1 秒（`-i 1`）。
- **UDP 测试规范**：参数配置为 `-u -b 200M`，目标限速 200 Mbps。200 Mbps 为统一设定的目标限速阈值，用于横向评估不同协议栈的丢包率、抖动与转发保真度，并非物理信道带宽极限。
- **数据传输方向**：Upload 为 Android Client → PC Server；Download 为 PC Server → Android Client，通过 iPerf3 `-R` 逆向流参数实现。

### 2.4 CPU 统计口径说明
- **数据采集方式**：CPU 使用率通过 Android `/proc/stat` 与各进程时间片采样统计。
- **多核累计占用百分比**：测试设备高通第五代骁龙 8 至尊版为 8 核架构（2×Prime Core + 6×Performance Core，最高 4.6 GHz），单核跑满计为 100%，理论上限为 **800%**。多流并发或高负载下实测平均 CPU 最高约 258%、峰值最高约 402%，代表占用了约 1.5 ~ 4.0 个物理核心，而非单核超频。

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

> ![](../charts/mega_benchmark_infographic.webp)
>
> [详细数据可视化请看第五章](#5-数据可视化)

### 3.1 综合平均测试数据 (3 轮平均)

注：本节各表格数据均为 3 轮完整实测的算术平均值。

#### 3.1.1 USB 3.2 Gen1 / 4.0 有线以太网测试 (TCP & UDP)

| 测试用例 / 配置 | 后端协议栈 | MTU | 协议 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **USB Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | TCP | 单流 | 1473.61 Mbps | 1503.98 Mbps | 0.1% / 1.3% | 158.8 MB |
| **USB Baseline (No VPN) [P=8]** | `direct_none` | 0 | TCP | P=8 | 1480.80 Mbps | 1182.51 Mbps | 0.3% / 1.0% | 158.5 MB |
| **USB Baseline (No VPN) [UDP] [Single Stream]** | `direct_none` | 0 | UDP | 单流 | 199.97 Mbps | 200.00 Mbps | 0.3% / 2.5% | 158.5 MB |
| **USB Baseline (No VPN) [UDP] [P=8]** | `direct_none` | 0 | UDP | P=8 | 25.00 Mbps | 25.00 Mbps | 0.3% / 1.0% | 158.5 MB |
| **Hev (MTU 1500) [Single Stream]** | `hev` | 1500 | TCP | 单流 | 1621.43 Mbps | 1591.55 Mbps | 37.9% / 89.0% | 163.0 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | TCP | 单流 | 1524.34 Mbps | 1532.57 Mbps | 24.7% / 81.0% | 163.3 MB |
| **Hev (MTU 1500) [P=8]** | `hev` | 1500 | TCP | P=8 | 1696.83 Mbps | 1787.65 Mbps | 47.4% / 116.0% | 164.1 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | TCP | P=8 | 1643.40 Mbps | 1775.43 Mbps | 28.8% / 87.7% | 164.9 MB |
| **Hev (MTU 1500) [UDP] [Single Stream]** | `hev` | 1500 | UDP | 单流 | 199.98 Mbps | 200.00 Mbps | 77.5% / 108.7% | 164.4 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 25.00 Mbps | 25.00 Mbps | 43.3% / 52.0% | 164.1 MB |
| **SingTUN (MTU 1500) [Single Stream]** | `sing` | 1500 | TCP | 单流 | 1748.64 Mbps | 1798.91 Mbps | 58.3% / 124.3% | 152.5 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | TCP | 单流 | 1611.46 Mbps | 1631.85 Mbps | 33.7% / 76.9% | 130.3 MB |
| **SingTUN (MTU 1500) [P=8]** | `sing` | 1500 | TCP | P=8 | 1798.90 Mbps | 1773.78 Mbps | 81.5% / 150.8% | 130.6 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | TCP | P=8 | 1778.31 Mbps | 1774.84 Mbps | 49.7% / 126.8% | 130.6 MB |
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 199.97 Mbps | 200.00 Mbps | 113.1% / 124.0% | 130.7 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 25.00 Mbps | 25.00 Mbps | 70.3% / 80.8% | 131.2 MB |
| **MipsTUN (MTU 1500) [Single Stream]** | `mips` | 1500 | TCP | 单流 | 1770.66 Mbps | 1617.21 Mbps | 120.1% / 189.3% | 152.6 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | TCP | 单流 | 1621.97 Mbps | 1653.22 Mbps | 55.5% / 118.1% | 130.1 MB |
| **MipsTUN (MTU 1500) [P=8]** | `mips` | 1500 | TCP | P=8 | 1816.88 Mbps | 1792.30 Mbps | 159.9% / 315.4% | 130.5 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | TCP | P=8 | 1769.30 Mbps | 1790.50 Mbps | 87.1% / 242.4% | 130.4 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 199.97 Mbps | 200.00 Mbps | 113.7% / 134.7% | 130.4 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 25.00 Mbps | 25.00 Mbps | 73.0% / 79.2% | 130.6 MB |
| **Xray TUN (MTU 1500) [Single Stream]** | `xray` | 1500 | TCP | 单流 | 1794.58 Mbps | 1022.18 Mbps | 195.9% / 229.5% | 182.9 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | TCP | 单流 | 1728.80 Mbps | 1017.65 Mbps | 178.9% / 200.0% | 159.9 MB |
| **Xray TUN (MTU 1500) [P=8]** | `xray` | 1500 | TCP | P=8 | 1893.29 Mbps | 1796.18 Mbps | 257.5% / 392.1% | 160.4 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | TCP | P=8 | 1882.82 Mbps | 1800.77 Mbps | 252.6% / 401.9% | 160.4 MB |
| **Xray TUN (MTU 1500) [UDP] [Single Stream]** | `xray` | 1500 | UDP | 单流 | 199.97 Mbps | 200.00 Mbps | 92.2% / 112.0% | 160.4 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 25.00 Mbps | 25.00 Mbps | 38.8% / 46.0% | 160.8 MB |


#### 3.1.2 5GHz Wi-Fi 无线网络测试 (TCP & UDP)

| 测试用例 / 配置 | 后端协议栈 | MTU | 协议 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | TCP | 单流 | 638.11 Mbps | 756.39 Mbps | 0.5% / 1.8% | 161.4 MB |
| **Wi-Fi Baseline (No VPN) [P=8]** | `direct_none` | 0 | TCP | P=8 | 658.33 Mbps | 807.87 Mbps | 0.4% / 2.7% | 160.4 MB |
| **Wi-Fi Baseline (No VPN) [UDP] [Single Stream]** | `direct_none` | 0 | UDP | 单流 | 199.97 Mbps | 200.00 Mbps (0.1% 丢包) | 0.4% / 1.0% | 160.4 MB |
| **Wi-Fi Baseline (No VPN) [UDP] [P=8]** | `direct_none` | 0 | UDP | P=8 | 24.99 Mbps | 25.00 Mbps | 0.3% / 1.0% | 160.4 MB |
| **Hev (MTU 1500) [Single Stream]** | `hev` | 1500 | TCP | 单流 | 604.10 Mbps | 714.12 Mbps | 28.2% / 51.7% | 163.8 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | TCP | 单流 | 637.09 Mbps | 762.24 Mbps | 20.1% / 50.3% | 164.4 MB |
| **Hev (MTU 1500) [P=8]** | `hev` | 1500 | TCP | P=8 | 611.02 Mbps | 797.05 Mbps | 36.9% / 87.7% | 165.1 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | TCP | P=8 | 624.88 Mbps | 815.33 Mbps | 22.9% / 69.3% | 165.8 MB |
| **Hev (MTU 1500) [UDP] [Single Stream]** | `hev` | 1500 | UDP | 单流 | 199.97 Mbps | 200.00 Mbps (0.7% 丢包) | 81.8% / 115.0% | 165.4 MB |
| **Hev (MTU 1500) [UDP] [P=8]** | `hev` | 1500 | UDP | P=8 | 25.00 Mbps | 25.00 Mbps | 43.7% / 58.0% | 164.8 MB |
| **SingTUN (MTU 1500) [Single Stream]** | `sing` | 1500 | TCP | 单流 | 601.77 Mbps | 680.61 Mbps | 47.8% / 79.0% | 152.3 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | TCP | 单流 | 643.84 Mbps | 797.76 Mbps | 31.7% / 70.2% | 130.5 MB |
| **SingTUN (MTU 1500) [P=8]** | `sing` | 1500 | TCP | P=8 | 628.95 Mbps | 786.76 Mbps | 73.0% / 118.4% | 130.7 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | TCP | P=8 | 607.30 Mbps | 729.34 Mbps | 49.9% / 139.6% | 130.8 MB |
| **SingTUN (MTU 1500) [UDP] [Single Stream]** | `sing` | 1500 | UDP | 单流 | 199.97 Mbps (0.4% 丢包) | 200.00 Mbps (0.7% 丢包) | 131.2% / 140.7% | 131.0 MB |
| **SingTUN (MTU 1500) [UDP] [P=8]** | `sing` | 1500 | UDP | P=8 | 25.00 Mbps | 25.00 Mbps | 69.2% / 73.0% | 130.8 MB |
| **MipsTUN (MTU 1500) [Single Stream]** | `mips` | 1500 | TCP | 单流 | 608.85 Mbps | 717.49 Mbps | 70.4% / 118.1% | 152.3 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | TCP | 单流 | 609.32 Mbps | 755.23 Mbps | 46.5% / 96.3% | 130.1 MB |
| **MipsTUN (MTU 1500) [P=8]** | `mips` | 1500 | TCP | P=8 | 599.15 Mbps | 805.56 Mbps | 107.8% / 271.5% | 130.5 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | TCP | P=8 | 614.77 Mbps | 707.58 Mbps | 71.4% / 217.6% | 130.7 MB |
| **MipsTUN (MTU 1500) [UDP] [Single Stream]** | `mips` | 1500 | UDP | 单流 | 199.96 Mbps | 200.00 Mbps (0.8% 丢包) | 115.0% / 140.3% | 130.7 MB |
| **MipsTUN (MTU 1500) [UDP] [P=8]** | `mips` | 1500 | UDP | P=8 | 25.00 Mbps | 25.00 Mbps | 73.8% / 78.3% | 130.6 MB |
| **Xray TUN (MTU 1500) [Single Stream]** | `xray` | 1500 | TCP | 单流 | 603.07 Mbps | 710.66 Mbps | 92.5% / 135.9% | 182.8 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | TCP | 单流 | 619.60 Mbps | 752.39 Mbps | 95.2% / 135.7% | 159.8 MB |
| **Xray TUN (MTU 1500) [P=8]** | `xray` | 1500 | TCP | P=8 | 620.09 Mbps | 756.19 Mbps | 150.9% / 355.9% | 160.5 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | TCP | P=8 | 607.89 Mbps | 729.67 Mbps | 150.0% / 347.1% | 160.3 MB |
| **Xray TUN (MTU 1500) [UDP] [Single Stream]** | `xray` | 1500 | UDP | 单流 | 199.97 Mbps | 200.00 Mbps (0.6% 丢包) | 95.2% / 119.0% | 160.7 MB |
| **Xray TUN (MTU 1500) [UDP] [P=8]** | `xray` | 1500 | UDP | P=8 | 25.00 Mbps | 25.00 Mbps | 39.6% / 48.3% | 160.5 MB |


#### 3.1.3 设备内部纯回环压力测试

| 测试用例 / 配置 | 后端协议栈 | MTU | 流模式 | 单流/多流吞吐 (Gbps) | 峰值 CPU (%) | 内存占用 (PSS) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Loopback Baseline (No VPN) [Single Stream]** | `direct_none` | 0 | 单流 | 22.83 Gbps | 1.9% | 158.8 MB |
| **Loopback Baseline (No VPN) [P=8]** | `direct_none` | 0 | P=8 | 29.82 Gbps | 1.0% | 158.2 MB |
| **Hev (MTU 9000) [Single Stream]** | `hev` | 9000 | 单流 | 20.12 Gbps | 14.8% | 162.8 MB |
| **SingTUN (MTU 9000) [Single Stream]** | `sing` | 9000 | 单流 | 19.70 Gbps | 17.3% | 152.0 MB |
| **MipsTUN (MTU 9000) [Single Stream]** | `mips` | 9000 | 单流 | 19.71 Gbps | 17.3% | 152.6 MB |
| **Xray TUN (MTU 9000) [Single Stream]** | `xray` | 9000 | 单流 | 19.34 Gbps | 17.3% | 182.4 MB |
| **Hev (MTU 9000) [P=8]** | `hev` | 9000 | P=8 | 46.99 Gbps | 14.8% | 185.1 MB |
| **SingTUN (MTU 9000) [P=8]** | `sing` | 9000 | P=8 | 43.02 Gbps | 8.0% | 152.6 MB |
| **MipsTUN (MTU 9000) [P=8]** | `mips` | 9000 | P=8 | 31.42 Gbps | 18.5% | 152.9 MB |
| **Xray TUN (MTU 9000) [P=8]** | `xray` | 9000 | P=8 | 43.14 Gbps | 13.6% | 183.0 MB |


#### 3.1.4 空闲连接驻留与内存增长 (TCP & UDP)

注：本表数据为 3 轮独立实测的算术平均值，基准 PSS 为建立连接前的空闲内存，1000 连接 PSS 为阶梯压测达到 1000 连接并稳定 1.5 秒后采样的物理内存。

| 后端协议栈 | 传输协议 | 连接范围 | 基准 PSS (3轮均值) | 1000 连接 PSS (3轮均值) | 内存增长斜率 (3轮均值) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **HEV** | TCP | 0 -> 1000 | 161.6 MB | 175.4 MB | 14.13 KiB/conn |
| **HEV** | UDP | 0 -> 1000 | 162.9 MB | 181.3 MB | 18.77 KiB/conn |
| **SING** | TCP | 0 -> 1000 | 134.0 MB | 132.6 MB | ~0.00 KiB/conn (GC 稳态) |
| **SING** | UDP | 0 -> 1000 | 133.4 MB | 134.1 MB | 0.72 KiB/conn |
| **MIPS** | TCP | 0 -> 1000 | 134.1 MB | 133.0 MB | ~0.00 KiB/conn (GC 稳态) |
| **MIPS** | UDP | 0 -> 1000 | 133.7 MB | 134.3 MB | 0.65 KiB/conn |
| **XRAY** | TCP | 0 -> 1000 | 164.3 MB | 162.5 MB | ~0.00 KiB/conn (GC 稳态) |
| **XRAY** | UDP | 0 -> 1000 | 164.0 MB | 163.2 MB | ~0.00 KiB/conn (GC 稳态) |

---

### 3.2 分轮实测数据明细

请查看[原始数据](#8-原始数据)

---

## 4. 纯底层协议栈独立微基准测试

### 4.1 隔离设计

为区分 Android 设备端到端测试中**协议栈自身开销**与 **Android 系统/JNI/ART 运行时开销**的边界，并还原多进程架构下的协议栈真实占用，设计并实施了 Scheme 2，在 Linux 宿主上以无特权用户命名空间隔离运行各协议栈，通过统一的 Go SOCKS5 Sink 服务模拟 0 → 1000 连接阶梯驻留，直接采集 PSS。该方案脱离 Android Framework、ART 虚拟机与温控调频调度，仅测量协议栈本身的内存开销。

### 4.2 微基准实测数据对比 (3 轮平均)

| 后端协议栈 | 传输协议 | 基础 PSS | 1000 连接 PSS | 纯底层增长斜率 (Scheme 2) | Android 端到端斜率 (Scheme 1) | 层级差值与说明 |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **`hev-socks5-tunnel`** (C/lwIP) | TCP | 2.1 MB | 14.4 MB | **12.59 KiB/conn** | 14.13 KiB/conn | +1.54 KiB/conn（旗舰机 Android 端三轮均值；JNI 封装与应用内状态管理附加开销，与 Scheme 2 同量级） |
| **`hev-socks5-tunnel`** (C/lwIP) | UDP | 2.1 MB | 18.3 MB | **16.59 KiB/conn** | 18.77 KiB/conn | +2.18 KiB/conn（Android JNI 状态管理附加开销，两侧数据吻合） |
| **`SingTUN`** (Go/pure user-space) | TCP | 9.5 MB | 60.4 MB | **52.14 KiB/conn** | ~0.00 KiB/conn (GC 稳态) | 约 -52.14 KiB/conn（Android 宿主进程进入 Go GC 稳态，堆增量被回收复用；Scheme 2 反映 goroutine 栈与 ring buffer 真实开销） |
| **`SingTUN`** (Go/pure user-space) | UDP | 9.5 MB | 54.3 MB | **45.84 KiB/conn** | 0.72 KiB/conn | 约 -45.12 KiB/conn（同上；Android 端 UDP 映射表在 GC 稳态下内存占用极低） |
| **`MipsTUN`** (Go/BBRv3) | TCP | 11.6 MB | 63.0 MB | **52.66 KiB/conn** | ~0.00 KiB/conn (GC 稳态) | 约 -52.66 KiB/conn（Android 宿主进程进入 Go GC 稳态；Scheme 2 为纯栈真实斜率） |
| **`MipsTUN`** (Go/BBRv3) | UDP | 12.2 MB | 123.4 MB | **113.88 KiB/conn** | 0.65 KiB/conn | 约 -113.23 KiB/conn（Scheme 2 反映 BBRv3 环形缓冲区开销；Android 端 GC 大幅消化 UDP 连接块） |
| **`Xray TUN`** (Go/gVisor) | TCP | 37.6 MB | 99.7 MB | **63.54 KiB/conn** | ~0.00 KiB/conn (GC 稳态)* | Android 主进程仅传递 fd，子进程承载真实开销 |
| **`Xray TUN`** (Go/gVisor) | UDP | 36.8 MB | 93.0 MB | **57.55 KiB/conn** | ~0.00 KiB/conn (GC 稳态)* | Android 主进程仅传递 fd，子进程承载真实开销 |

> 注：Xray TUN 仅统计了 SimpleXray 主进程的 PSS 增长，未覆盖独立 Fork 运行的 Xray 守护子进程。
>
> 旗舰平台上 SingTUN、MipsTUN 与 Xray 主进程在 0 → 1000 连接区间进入 Go GC 稳态或堆复用状态，Scheme 1 增量接近 0，不能代表协议栈真实驻留成本；纯栈开销以 Scheme 2 为准。

### 4.3 全栈损耗归因

![Full-Stack Attribution Dashboard](../charts/fullstack_attribution_dashboard.webp)

### 4.4 层级归因

**JNI 进程内直驱模型（Hev / SingTUN / MipsTUN）**

lwIP 协议栈以纯 C 实现，PCB 开销仅为 12.59 KiB/conn（TCP）与 16.59 KiB/conn（UDP）。引入 Android 宿主后，旗舰机 UDP 斜率实测 18.77 KiB/conn，比 Scheme 2 高约 2.18 KiB；TCP 斜率实测 14.13 KiB/conn，比 Scheme 2 高约 1.54 KiB。两个方向都处于 JNI 封装、应用内状态管理与分配器开销的合理范围内，C/lwIP 后端的层级归因仍然清晰。

SingTUN Scheme 2 独立测量的纯栈开销为 52.14 KiB/conn（TCP）与 45.84 KiB/conn（UDP），开销来自 Go 运行时的 goroutine 初始栈、Channel 缓冲区及环形 buffer。旗舰机 Android 端主进程 TCP 斜率进入 GC 稳态（~0.00），UDP 仅为 0.72 KiB/conn，远低于纯栈开销；这说明宿主进程的堆增量被 GC 复用掩盖，不能据此认为 SingTUN 的实际连接驻留成本消失。

MipsTUN 的 Scheme 2 TCP 斜率为 52.66 KiB/conn，UDP 为 113.88 KiB/conn（BBRv3 环形缓冲区开销）。旗舰机 Android 端主进程 TCP 接近 0、UDP 为 0.65 KiB/conn，同样是 Go GC 稳态的结果，实际纯栈成本应以 Scheme 2 为准。

**多进程隔离模型（Xray Native TUN）**

旗舰机 Android 端测试中，Xray Native TUN 主进程 TCP/UDP PSS 增长均处于 GC 稳态（~0.00 KiB/conn）。

原因在于 SimpleXray 的工程集成采用了跨进程模式：`TProxyService` 通过 JNI Fork 出独立的 Xray 守护子进程，并将 `VpnService` 的 fd 注入给子进程，主进程仅持有轻量的 IPC 句柄与代理上下文。

Scheme 2 独立测量了 Xray 核心内部 gVisor netstack 的完整开销，实际连接控制块与 buffer 开销为 63.54 KiB/conn（TCP）与 57.55 KiB/conn（UDP），在四种协议栈中属于偏高一档。多进程部署架构将这部分内存压力完全隔离在子进程内部，因此主进程 PSS 斜率不能代表 Xray TUN 的真实驻留成本。

---

## 5. 数据可视化

### 5.1 综合平均可视化 (3 轮平均)

#### (1) 全栈损耗归因全景
![Full-Stack Attribution Dashboard](../charts/fullstack_attribution_dashboard.webp)

#### (2) 5GHz Wi-Fi 无线吞吐量
![5GHz Wi-Fi Throughput Dashboard](../charts/avg_wifi_throughput_dashboard.webp)

#### (3) USB 3.2 / 4.0 有线吞吐量
![USB 3.2 Throughput Dashboard](../charts/avg_usb_throughput_dashboard.webp)

#### (4) 核心 CPU 传输能效比
![CPU Efficiency Dashboard](../charts/avg_cpu_efficiency_dashboard.webp)

#### (5) 设备内部协议处理纯回环
![Loopback Dashboard](../charts/avg_loopback_dashboard.webp)

#### (6) 空闲连接驻留与内存增长
![Idle Memory Dashboard](../charts/avg_idle_memory_dashboard.webp)

#### (7) UDP 数据包传输抖动
![UDP Jitter Dashboard](../charts/avg_udp_jitter_dashboard.webp)

#### (8) 载荷时延膨胀与缓冲膨胀
![Bufferbloat Dashboard](../charts/bufferbloat_dashboard.webp)

> [!NOTE]
> **测试方法说明**：载荷时延膨胀衡量的是链路满载时交互式通信的延迟恶化（$\Delta RTT = L_{loaded} - L_{idle}$）。在 5GHz Wi-Fi、标准 MTU 1500 环境下持续 TCP 下行打流 10 秒，并发每 200ms 发起 TCP echo RTT 探针；探针与被测流量经过同一 Android/TUN 数据路径，直接取空载与受载 RTT 的中位数。TCP 探针替代 ICMP，避免 ICMP 未被某些用户态协议栈接管而产生伪零值。

#### (9) 60秒持续高吞吐长跑稳定性曲线
![Long-Run Stability Dashboard](../charts/long_run_stability_dashboard.webp)

> [!NOTE]
> **测试方法说明**：本测试同样未采用多轮平均平滑处理，改为单次 60 秒连续长程高并发打流（Wi-Fi 5GHz, TCP MTU 1500, P=8），每 1 秒输出 1 次瞬时吞吐切片。专门用于捕获短跑测试无法暴露的动态特征，包括 Go 运行时周期性 GC 顿挫、TCP 拥塞控制稳态收敛过程、单线程处理器的算力疲劳，以及持续满载下的吞吐衰减率与变异系数。

---

### 5.2 分轮可视化明细

<details>
<summary><b>第 1 轮测试可视化 (点击展开)</b></summary>

![Round 1 Wi-Fi](../charts/r1_wifi_throughput_dashboard.webp)
![Round 1 USB](../charts/r1_usb_throughput_dashboard.webp)
![Round 1 Efficiency](../charts/r1_cpu_efficiency_dashboard.webp)
![Round 1 Loopback](../charts/r1_loopback_dashboard.webp)
![Round 1 Idle](../charts/r1_idle_memory_dashboard.webp)
![Round 1 Jitter](../charts/r1_udp_jitter_dashboard.webp)

</details>

<br>

<details>
<summary><b>第 2 轮测试可视化 (点击展开)</b></summary>

![Round 2 Wi-Fi](../charts/r2_wifi_throughput_dashboard.webp)
![Round 2 USB](../charts/r2_usb_throughput_dashboard.webp)
![Round 2 Efficiency](../charts/r2_cpu_efficiency_dashboard.webp)
![Round 2 Loopback](../charts/r2_loopback_dashboard.webp)
![Round 2 Idle](../charts/r2_idle_memory_dashboard.webp)
![Round 2 Jitter](../charts/r2_udp_jitter_dashboard.webp)

</details>

<br>

<details>
<summary><b>第 3 轮测试可视化 (点击展开)</b></summary>

![Round 3 Wi-Fi](../charts/r3_wifi_throughput_dashboard.webp)
![Round 3 USB](../charts/r3_usb_throughput_dashboard.webp)
![Round 3 Efficiency](../charts/r3_cpu_efficiency_dashboard.webp)
![Round 3 Loopback](../charts/r3_loopback_dashboard.webp)
![Round 3 Idle](../charts/r3_idle_memory_dashboard.webp)
![Round 3 Jitter](../charts/r3_udp_jitter_dashboard.webp)

</details>

---

## 6. 分析

### 6.0 本平台结论

 USB 物理链路基线约为 1.47–1.50 Gbps；Wi-Fi 物理基线约为 638/756 Mbps（上行/下行）。在该旗舰平台上，协议栈差异主要体现为数据路径、队列、内存拷贝、运行时调度和高包率处理开销。

### 6.1 `hev-socks5-tunnel` (C / lwIP)

**吞吐量**

在 Wi-Fi 6 路由器网关测试中，MTU 9000 下 8 流并发上/下行达到 624.88/815.33 Mbps；标准 MTU 1500 单流上/下行达到 604.10/714.12 Mbps。在 USB 3.2 有线环境下，标准 MTU 单流上/下行达到 1621.43/1591.55 Mbps，MTU 9000 多流上/下行达到 1643.40/1775.43 Mbps。60 秒持续高吞吐中，8 流平均吞吐为 511.69 Mbps，CV 为 16.74%，末段较首段上升 40.11%，未出现吞吐断崖式下跌。

UDP 传输方面，Wi-Fi 8 流 UDP 可达 200 Mbps 设定限速（200.26 Mbps，0% 丢包），USB 8 流 UDP 测得 196.69 ~ 199.38 Mbps。Wi-Fi 单流 UDP 下行为 119.36 Mbps。

**资源特性**

采用 C 语言与 lwIP 实现，在 Wi-Fi MTU 9000 8 流满载时，平均 CPU 占用为 22.9%（上行）与 52.9%（下行），算力开销低于 Go 实现。端到端 UDP 内存增长斜率为 18.77 KiB/conn（3 轮均值），比独立微基准测得的 16.59 KiB/conn 高约 2.18 KiB；TCP 端到端斜率为 14.13 KiB/conn，纯底层斜率为 12.59 KiB/conn，JNI 与宿主状态管理开销处于合理范围。

**局限性**

lwIP 采用单线程事件循环，在多流并发打满时存在排队瓶颈。Wi-Fi MTU 1500 下 8 流上/下行达到 611.02/797.05 Mbps；在载荷时延膨胀测试中，8 流时延膨胀为 +35.9 ms；长跑 CV 为 16.74%。启动时需要向文件系统写入临时配置文件 `tproxy.conf`。

---

### 6.2 `SingTUN` (Go / pure user-space TCP/IP)

**吞吐量**

多流并发下行性能较好。在 Wi-Fi 6 路由器网关下，MTU 1500 与 MTU 9000 的 8 流并发 TCP 下行分别达到 786.76 与 729.34 Mbps；60 秒持续高吞吐测试中，8 流平均吞吐为 586.96 Mbps，CV 为 4.40%，衰减率为 -5.60%，未观察到 GC 停顿导致的吞吐骤降。

USB 单流 UDP 传输抖动为 0.067 ms。8 流 UDP 在 Wi-Fi 与 USB 下均达到 200 Mbps 设定限速（200.25 Mbps），单流 UDP 上行在 Wi-Fi 为 148.00 Mbps。

**资源特性**

通过 JNI 直接接管 `VpnService` 文件描述符，无需临时配置文件。独立微基准测得 TCP 纯栈斜率为 52.14 KiB/conn，UDP 为 45.84 KiB/conn。在载荷时延膨胀测试中，8 流时延膨胀为 +33.4 ms（吞吐 422.2 Mbps），低于 direct baseline 的 +72.3 ms。

**局限性**

Go 运行时多协程调度导致高并发下 CPU 开销增加。Wi-Fi MTU 1500、8 流下行平均 CPU 为 107.9%，USB 同场景为 143.6%；Wi-Fi MTU 1500 单流下行达到 680.61 Mbps。

---

### 6.3 `MipsTUN` (Go / Mihomo mipstack)

**吞吐量**

在 60 秒持续高吞吐测试中，8 流平均吞吐为 559.22 Mbps，CV 为 15.72%，衰减率为 +14.68%；仍体现出内置 BBRv3 的速率控制特征，但稳定性不再是四款协议栈中最低波动。

Wi-Fi 6 路由器网关下，MTU 9000 8 流上/下行达到 614.77/707.58 Mbps，MTU 1500 单流下行达到 717.49 Mbps。USB 环境下 MTU 1500 单流上行达到 1770.66 Mbps，MTU 9000 多流上行达到 1769.30 Mbps。8 流 UDP 下行测得 186.81 ~ 200.25 Mbps，上行为 199.69 Mbps。

**资源特性**

基于非阻塞 epoll 与 netpoller 实现，无外部 C 依赖。独立微基准测得 TCP 纯栈斜率为 52.66 KiB/conn；载荷时延膨胀在 P=1/P=8 下分别为 +32.3/+86.6 ms。

**局限性**

在 MTU 1500 下多流并发仍受锁竞争与调度影响，但 Wi-Fi 8 流下行达到 805.56 Mbps、USB 达到 1792.30 Mbps。UDP 高包率场景下 CPU 占用较高；UDP 纯底层斜率在四款协议栈中最高（113.88 KiB/conn，受 BBRv3 环形缓冲区分配影响）。

---

### 6.4 `Xray Native TUN`

**吞吐量**

在 60 秒持续测试中平均吞吐为 557.82 Mbps，CV 为 19.15%，衰减率为 -39.12%。Wi-Fi MTU 1500 单流/多流下行分别达到 710.66/756.19 Mbps，USB 分别为 1022.18/1796.18 Mbps；8 流 UDP 上下行均达到 197.41 ~ 200.25 Mbps。

**资源特性**

在载荷时延膨胀测试中，P=1/P=8 时延膨胀分别为 0/+87.3 ms（P=8 吞吐 754.0 Mbps）。主进程连接增长斜率处于 GC 稳态（TCP/UDP 均约 0.00 KiB/conn），独立微基准测得子进程内部 gVisor 真实开销为 63.54 KiB/conn（TCP）与 57.55 KiB/conn（UDP）。

**局限性**

Xray 的 gVisor 数据路径仍有较高 CPU 成本：Wi-Fi MTU 1500 单流/多流下行 CPU 为 109.0%/129.1%，USB 为 157.1%/255.1%；在旗舰机上吞吐已达到 710.66/756.19 Mbps（Wi-Fi）和 1022.18/1796.18 Mbps（USB），但单位吞吐能效仍高于其他方案。

---

## 7. 选择参考

| 协议栈后端 | 适用场景 | 优势 | 代价 |
| :--- | :--- | :--- | :--- |
| **`hev-socks5-tunnel`** | 日常轻量续航、低功耗保活、发热敏感环境 | CPU 占用低、能效比高、纯底层开销仅 12.59 KiB/conn、UDP 抖动较低、长跑平均 511.69 Mbps | MTU 1500 多流下行约 797 Mbps、8 流缓冲膨胀 +35.9 ms、长跑 CV 16.74% |
| **`SingTUN`** | 高速无线 Wi-Fi、大带宽下载、多流并发 | 多流下行并发性能强、载荷时延膨胀较小 (+33.4 ms)、60s 平均 586.96 Mbps、CV 4.40%、无临时文件 I/O | Go 调度带来额外 CPU 开销（Wi-Fi TCP P=8 下行 107.9%）、MTU 1500 单流下行弱于 Hev、基础内存占用偏高 |
| **`MipsTUN`** | 有线网络、大吞吐传输、巨型帧环境 | 有线上行吞吐高、内置 BBRv3、纯 Go 架构无 C 依赖、长跑平均 559.22 Mbps | 8 流缓冲膨胀 +86.6 ms、长跑 CV 15.72%、UDP 高包率下 CPU 负载高于 HEV |
| **`Xray Native TUN`** | 主进程内存隔离、进程级防护、原生核心直驱 | 主进程内存增长处于 GC 稳态（~0.00 KiB/conn）、进程级隔离、长跑平均 557.82 Mbps | 8 流载荷时延膨胀 +87.3 ms、长跑 CV 19.15%、末段衰减 -39.12%、单位吞吐能效比偏低 |

---

## 8. 原始数据

- Android 端到端实测原始数据集：[benchmark_results.json](../data/benchmark_results.json)
- 纯底层协议栈独立微基准原始数据集：[microbench_results.json](../data/microbench_results.json)
- 载荷膨胀与长跑稳定性原始数据集：[advanced_benchmark_results.json](../data/advanced_benchmark_results.json)
