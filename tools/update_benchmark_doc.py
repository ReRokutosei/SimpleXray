#!/usr/bin/env python3
import json
import os

def render_table(cases, title=None, is_loopback=False):
    lines = []
    if title:
        lines.append(f"#### {title}\n")
    if is_loopback:
        lines.append("| 测试用例 / 配置 | 后端协议栈 | MTU | 流模式 | 单流/多流吞吐 (Gbps) | 峰值 CPU (%) | 内存占用 (PSS) |")
        lines.append("| :--- | :--- | :---: | :---: | :---: | :---: | :---: |")
        for c in cases:
            speed_val = c.get("upload_mbps", 0.0)
            speed_gbps = round(speed_val / 1000.0, 2)
            par = "单流" if c.get("parallel") == 1 else "P=8"
            name = c.get("name")
            backend = c.get("backend")
            mtu = c.get("mtu")
            peak_cpu = c.get("peak_cpu", 0.0)
            peak_mem = c.get("peak_mem", c.get("peak_mem_mb", 0.0))
            mem_str = f"{peak_mem:.1f} MB" if peak_mem > 0.0 else "—"
            lines.append(f"| **{name}** | `{backend}` | {mtu} | {par} | {speed_gbps:.2f} Gbps | {peak_cpu:.1f}% | {mem_str} |")
    else:
        lines.append("| 测试用例 / 配置 | 后端协议栈 | MTU | 协议 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |")
        lines.append("| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")
        for c in cases:
            par = "单流" if c.get("parallel") == 1 else "P=8"
            net = c.get("network", "tcp").upper()
            up_loss_val = c.get("up_loss", c.get("upload_loss_percent", 0.0))
            down_loss_val = c.get("down_loss", c.get("download_loss_percent", 0.0))
            up_loss = f" ({up_loss_val:.1f}% 丢包)" if net == "UDP" and up_loss_val >= 0.1 else ""
            down_loss = f" ({down_loss_val:.1f}% 丢包)" if net == "UDP" and down_loss_val >= 0.1 else ""
            up_str = f"{c.get('upload_mbps', 0.0):.2f} Mbps{up_loss}"
            down_str = f"{c.get('download_mbps', 0.0):.2f} Mbps{down_loss}"
            up_cpu = c.get("up_cpu", c.get("upload_cpu_avg", 0.0))
            peak_cpu = c.get("peak_cpu", 0.0)
            peak_mem = c.get("peak_mem", c.get("peak_mem_mb", 0.0))
            mem_str = f"{peak_mem:.1f} MB" if peak_mem > 0.0 else "—"
            cpu_str = f"{up_cpu:.1f}% / {peak_cpu:.1f}%"
            name = c.get("name")
            backend = c.get("backend")
            mtu = c.get("mtu")
            lines.append(f"| **{name}** | `{backend}` | {mtu} | {net} | {par} | {up_str} | {down_str} | {cpu_str} | {mem_str} |")
    return "\n".join(lines) + "\n"

def render_idle_table(cases, title=None):
    lines = []
    if title:
        lines.append(f"#### {title}\n")
    lines.extend([
        "| 后端协议栈 | 传输协议 | 连接范围 | 基准 PSS 内存 | 1000 连接 PSS 内存 | 内存增长斜率 |",
        "| :--- | :---: | :---: | :---: | :---: | :---: |"
    ])
    for c in cases:
        backend_str = c.get("backend", "").upper()
        network_str = c.get("network", "").upper()
        flows = c.get("idle_flows", [])
        base = flows[0].get("pss_mb", 0.0) if flows else 0.0
        end = flows[-1].get("pss_mb", 0.0) if flows else 0.0
        slope = c.get("slope_kib_per_conn", 0.0)
        lines.append(f"| **{backend_str}** | {network_str} | 0 -> 1000 | {base:.1f} MB | {end:.1f} MB | {slope:.2f} KiB/conn |")
    return "\n".join(lines) + "\n"

def main():
    with open("docs/benchmark/benchmark_results.json") as f:
        raw_data = json.load(f)

    rounds_data = raw_data["rounds"]

    # Compute clean averages across rounds
    first_round = list(rounds_data.values())[0]
    numeric_fields = [
        "upload_mbps", "download_mbps", "speed_gbps",
        "upload_cpu_avg", "upload_cpu_peak",
        "download_cpu_avg", "download_cpu_peak",
        "peak_cpu", "peak_mem_mb",
        "upload_loss_percent", "download_loss_percent",
        "upload_jitter_ms", "download_jitter_ms",
        "slope_kib_per_conn"
    ]
    avg_cases = []
    for rec_template in first_round:
        rec = dict(rec_template)
        medium = rec.get("medium")
        backend = rec.get("backend")
        mtu = rec.get("mtu")
        parallel = rec.get("parallel")
        net = rec.get("network", "tcp")
        is_idle = rec.get("type") == "idle_memory"
        for field in numeric_fields:
            vals = []
            for r_list in rounds_data.values():
                for match in r_list:
                    if is_idle:
                        if (match.get("type") == "idle_memory" and
                            match.get("backend") == backend and
                            match.get("network") == net):
                            if field in match:
                                vals.append(match[field])
                            break
                    else:
                        if (match.get("medium") == medium and
                            match.get("backend") == backend and
                            match.get("mtu") == mtu and
                            match.get("parallel") == parallel and
                            match.get("network", "tcp") == net):
                            if field in match:
                                if field == "peak_mem_mb" and match[field] == 0.0:
                                    continue
                                vals.append(match[field])
                            break
            if vals:
                rec[field] = round(sum(vals) / len(vals), 2)
            elif field == "peak_mem_mb":
                rec[field] = 0.0
        avg_cases.append(rec)

    wifi_avg = [c for c in avg_cases if c.get("medium") == "5GHz Wi-Fi"]
    usb_avg = [c for c in avg_cases if c.get("medium") == "USB 3.2 / 4.0"]
    loop_avg = [c for c in avg_cases if c.get("medium") == "On-Device Loopback"]
    idle_avg = [c for c in avg_cases if c.get("type") == "idle_memory"]

    doc = []
    doc.append("""# SimpleXray Android TUN 性能基准测试报告

本文档记录对 SimpleXray 支持的四种透明代理 TUN 协议栈实现 `hev-socks5-tunnel`、`Xray Native TUN`、`SingTUN` 与 `MipsTUN` (Mihomo mipstack)，在 **吞吐量 (Throughput)**、**处理器开销 (CPU Usage)**、**能效比 (Efficiency)** 以及 **连接驻留内存开销 (Idle Memory Growth)** 维度的客观实测与对比分析。

---

## 1. 测试环境与规格

### 测试主机（iPerf3 Server / PC）
- **操作系统**: Linux x86_64 (Linux 6.12.107+deb13-amd64)
- **处理器 (CPU)**: AMD Ryzen 7 6800H @ 3.2GHz (8 核 16 线程)
- **无线网卡**: Intel(R) Wi-Fi 6E AX210 160MHz
- **有线接口**: USB 3.2 Gen1 / 4.0 40Gbps 全功能接口 (RNDIS USB 网络共享虚拟以太网，链路速率 1Gbps)
- **iPerf3 版本**: 3.18 (cJSON 1.7.15)

### Android 测试设备（iPerf3 Client / DUT）
- **设备标识**: `R52W****0WL` (Samsung Galaxy Tab S7 FE Wi-Fi)
- **操作系统**: Android 14 (One UI 6.0，Linux 5.4 内核)
- **处理器 (SoC)**: 高通骁龙 778G (SM7325: 4x2.4GHz Kryo 670 Prime/Gold + 4x1.8GHz Kryo 670 Silver)
- **有线接口**: Type-C USB 3.2 Gen1 (RNDIS 物理网络共享)
- **无线规格**: 802.11 a/b/g/n/ac/ax 2.4G+5GHz，HE80，2x2 MIMO
- **iPerf3 版本**: 3.21 (aarch64 静态编译版)

### 局域网网关
- **网关设备**: 高通骁龙移动平台设备 (Android 16，FastConnect 7900 无线连接系统)
- **网络频段**: 5GHz Wi-Fi 热点 (WLAN AP，80MHz 频宽，物理协商速率 1201 Mbps)

---

## 2. 测试链路与架构

测试全程采用 **局域网 Direct/Freedom 纯透明代理** 链路，流量经由 Android 系统 `VpnService` 虚拟网卡由各 TUN 协议栈处理并直连 PC 出站：

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

## 3. 实测数据

### 3.1 综合平均测试数据 (3 轮平均)

#### (1) USB 3.2 Gen1 / 4.0 有线以太网测试 (TCP & UDP)
""")
    doc.append(render_table(usb_avg))
    doc.append("""#### (2) 5GHz Wi-Fi 无线网络测试 (TCP & UDP)
""")
    doc.append(render_table(wifi_avg))
    doc.append("""#### (3) 设备内部纯回环极限压力测试 (On-Device Loopback)
""")
    doc.append(render_table(loop_avg, is_loopback=True))
    doc.append("""#### (4) 空闲连接驻留与内存增长 (0 -> 1000 连接，TCP & UDP)
""")
    doc.append(render_idle_table(idle_avg))

    doc.append("""---

### 3.2 分轮实测数据明细

""")
    for r_idx in [1, 2, 3]:
        r_name = f"round_{r_idx}"
        r_list = rounds_data[r_name]
        r_wifi = [c for c in r_list if c.get("medium") == "5GHz Wi-Fi"]
        r_usb = [c for c in r_list if c.get("medium") == "USB 3.2 / 4.0"]
        r_loop = [c for c in r_list if c.get("medium") == "On-Device Loopback"]
        r_idle = [c for c in r_list if c.get("type") == "idle_memory"]

        doc.append(f"""<details>
<summary><b>第 {r_idx} 轮测试数据明细 (点击展开)</b></summary>

""")
        doc.append(render_table(r_usb, "USB 3.2 Gen1 / 4.0 有线网络测试"))
        doc.append(render_table(r_wifi, "5GHz Wi-Fi 无线网络测试"))
        doc.append(render_table(r_loop, "设备内部纯回环极限压力测试", is_loopback=True))
        doc.append(render_idle_table(r_idle, "空闲连接驻留与内存增长 (0 -> 1000 连接)"))
        doc.append("""</details>

<br>

""")

    doc.append("""---

## 4. 数据可视化

测试生成了标准化的超宽综合分析看板（包含基准线对比、吞吐量、能效比、以及连接内存增长折线）：

### 4.1 综合平均可视化仪表盘 (3 轮汇总)

#### (1) 5GHz Wi-Fi 无线吞吐量看板
![5GHz Wi-Fi Throughput Dashboard](../images/avg_wifi_throughput_dashboard.webp)

#### (2) USB 3.2 / 4.0 有线吞吐量看板
![USB 3.2 Throughput Dashboard](../images/avg_usb_throughput_dashboard.webp)

#### (3) 核心 CPU 传输能效比看板 (Throughput / CPU Usage)
![CPU Efficiency Dashboard](../images/avg_cpu_efficiency_dashboard.webp)

#### (4) 设备内部协议处理纯回环看板 (Loopback Baseline)
![Loopback Dashboard](../images/avg_loopback_dashboard.webp)

#### (5) 空闲连接驻留与内存增长看板 (0 -> 1000 连接)
![Idle Memory Dashboard](../images/avg_idle_memory_dashboard.webp)

---

### 4.2 分轮可视化看板明细

<details>
<summary><b>第 1 轮测试可视化看板 (点击展开)</b></summary>

![Round 1 Wi-Fi](../images/r1_wifi_throughput_dashboard.webp)
![Round 1 USB](../images/r1_usb_throughput_dashboard.webp)
![Round 1 Efficiency](../images/r1_cpu_efficiency_dashboard.webp)
![Round 1 Loopback](../images/r1_loopback_dashboard.webp)
![Round 1 Idle](../images/r1_idle_memory_dashboard.webp)

</details>

<br>

<details>
<summary><b>第 2 轮测试可视化看板 (点击展开)</b></summary>

![Round 2 Wi-Fi](../images/r2_wifi_throughput_dashboard.webp)
![Round 2 USB](../images/r2_usb_throughput_dashboard.webp)
![Round 2 Efficiency](../images/r2_cpu_efficiency_dashboard.webp)
![Round 2 Loopback](../images/r2_loopback_dashboard.webp)
![Round 2 Idle](../images/r2_idle_memory_dashboard.webp)

</details>

<br>

<details>
<summary><b>第 3 轮测试可视化看板 (点击展开)</b></summary>

![Round 3 Wi-Fi](../images/r3_wifi_throughput_dashboard.webp)
![Round 3 USB](../images/r3_usb_throughput_dashboard.webp)
![Round 3 Efficiency](../images/r3_cpu_efficiency_dashboard.webp)
![Round 3 Loopback](../images/r3_loopback_dashboard.webp)
![Round 3 Idle](../images/r3_idle_memory_dashboard.webp)

</details>

---

## 5. 客观分析与技术总结

综合 3 轮物理实测数据，涵盖 TCP（单流/多流、标准帧/巨型帧）、UDP（单流/多流）以及 1000 连接驻留测试，各 TUN 后端在不同维度的性能与开销特征如下：

### 5.1 `hev-socks5-tunnel` (C / lwIP)

* **吞吐量表现**：
  - **TCP 吞吐**：在 MTU 9000 巨型帧下，5GHz Wi-Fi 多流下行达到 **411.21 Mbps**，USB 有线单流上行达 **647.39 Mbps**（多流 **513.97 Mbps**）；在标准 MTU 1500 单流下行维持在 **277.22 ~ 303.01 Mbps**。
  - **UDP 性能**：8 流并发 UDP 跑满 **200 Mbps** 限制（0% 丢包）；单流 UDP 上行维持在 **148.50 ~ 199.57 Mbps**，单流下行在 **123.75 ~ 137.12 Mbps**（丢包率 20.8% ~ 29.6%）。
* **资源开销与优势**：
  - **能效比突出，功耗低**：纯 C 实现配合微裁剪 lwIP 协议栈，TCP 测试中平均 CPU 占用率仅在 **16.0% ~ 48.5%** 区间，能效比（Mbps/CPU%）显著高于其他后端。
  - **内存控制平稳**：常驻 PSS 维持在 **95.7 ~ 106.4 MB**，在 1000 连接空闲驻留测试中，TCP 增长斜率为 **17.10 KiB/conn**，UDP 增长斜率为 **18.02 KiB/conn**，各阶梯点形态平滑。
* **局限性**：
  - **多流下行扩展受限**：受 lwIP 单线程事件循环模型影响，在 MTU 1500 多流下行并发时，下行吞吐收窄至 **~300 Mbps**，未充分利用移动 SoC 多核心能力。
  - **架构依赖**：需要向文件系统生成并写入临时配置文件 `tproxy.conf`。

---

### 5.2 `SingTUN` (Go / sing-box 专有自研栈)

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

### 5.3 `MipsTUN` (Go / Mihomo mipstack 纯 Go 协议栈)

* **吞吐量表现**：
  - **有线上行与巨型帧吞吐突出**：在 USB 3.2 有线环境下表现抢眼，MTU 1500 单流上行达 **608.62 Mbps**，MTU 9000 多流下行达到 **393.05 Mbps**。
  - **无线 TCP 表现均衡**：内置 BBRv3 拥塞控制算法，在 MTU 9000 下 Wi-Fi 单流跑出 **343.19 Mbps**（上行）与 **353.59 Mbps**（下行）。
  - **UDP 吞吐达标**：8 流 UDP 下行测得 **199.29 ~ 199.83 Mbps**，上行达到 **166.03 ~ 185.51 Mbps**。
  - **内部回环吞吐最高**：在本地 Loopback 压力测试中单流测得 **20.92 Gbps**，位列各后端第一。
* **资源开销与优势**：
  - **纯 Go 现代架构**：完全基于非阻塞 epoll/netpoller 实现，架构独立简洁，无外部 C 依赖。
* **局限性**：
  - **密集小包 CPU 开销显著**：在 MTU 1500 单流小包场景下，平均 CPU 开销为 **55.0% ~ 111.8%**，峰值突破 **200%**；建议在支持大帧的网络环境下开启大 MTU。
  - **连接驻留内存基数与增长偏大**：基础空闲 PSS 偏高（125.1 ~ 156.2 MB），1000 连接测试下 TCP 斜率为 **53.35 KiB/conn**（1000 连达到 208.3 MB），UDP 斜率为 **37.79 KiB/conn**。

---

### 5.4 `Xray Native TUN` (Go / gVisor netstack)

* **吞吐量表现**：
  - **下行传输存在架构级收窄**：单流下行在 Wi-Fi 与 USB 环境下均处于 **58.13 ~ 63.33 Mbps** 水平，多流并发下行维持在 **141.93 ~ 180.25 Mbps**，下行产出偏低。
  - **上行及回环性能正常**：上行测速单流可达 **313.11 ~ 445.62 Mbps**，内部回环单流达到 **20.71 Gbps**，说明协议转换通路正常，下行收窄主要受 gVisor 协议栈单核事件循环与内部 buffer 拷贝调度制约。
  - **UDP 多流表现稳定**：8 流 UDP 上下行均达到 **197.41 ~ 200.14 Mbps**。
* **资源开销与优势**：
  - **连接驻留内存极低**：在 1000 空闲连接驻留测试中，TCP 斜率仅为 **4.61 KiB/conn**，UDP 斜率仅为 **3.79 KiB/conn**，1000 连接建立后整机 PSS 仅增长约 3.7 MB，驻留开销在四种后端中最低。
  - **核心直接集成**：由 Xray 核心主进程持有 VPN 文件描述符，无额外的代理转发层。
* **局限性**：
  - **下行能效比较低**：在下行仅 60 Mbps 左右的情况下，平均 CPU 占用仍达到 **65.4% ~ 96.1%**，峰值达到 **172% ~ 202%**，单位吞吐消耗的算力显著偏高。

---

## 6. 适用场景总结

| 协议栈后端 | 推荐应用场景 | 关键优势 | 权衡代价 |
| :--- | :--- | :--- | :--- |
| **`hev-socks5-tunnel`** | 日常轻量续航、低功耗保活、发热敏感环境 | CPU 功耗极低、发热小、内存稳定 | MTU 1500 多流下行并发受限、需写入临时配置 |
| **`SingTUN`** | 高速无线 Wi-Fi、大带宽流媒体、多流高并发下载 | 多流下行并发极强、UDP 稳定、无临时文件 | 高并发下 CPU 占用较高、瞬时堆内存扩容 |
| **`MipsTUN`** | USB 有线网络、局域网大吞吐传输、巨型帧环境 | 上行吞吐极高、Loopback 吞吐顶尖、内置 BBRv3 | 基础内存占用偏高、标准 MTU 下 CPU 负载较重 |
| **`Xray Native TUN`** | 海量微长连接、后台低频保活、无额外代理中转 | 1000 连接驻留内存极低 (<5K/conn)、架构原汁原味 | 单流及多流下行吞吐受限、下行能效比较低 |

---

## 7. 原始数据与脚本索引

- 结构化实测原始数据集: [benchmark_results.json](./benchmark_results.json)
- Markdown 明细排版总结: [benchmark_summary.md](./benchmark_summary.md)
- 自动化基准测试套件: [tools/benchmark.py](../../tools/benchmark.py)
- 高密度可视化绘图引擎: [tools/generate_charts.py](../../tools/generate_charts.py)
""")

    output_path = "docs/benchmark/android-tun-benchmark.md"
    with open(output_path, "w", encoding="utf-8") as f:
        f.write("".join(doc))

    print(f"Successfully updated: {output_path}")

if __name__ == "__main__":
    main()
