# SimpleXray TUN Backend Benchmark Report (Snapdragon 778G)

## 1. Test Environment

### 1.1 Hardware and OS Specifications

| Parameter | Client | Server |
| :--- | :--- | :--- |
| **Platform** | Snapdragon 778G / 4×Cortex-A78 + 4×Cortex-A55 | Ryzen 7 6800H / 8C16T |
| **OS** | Android 14 | Debian 13 (trixie) |
| **Kernel** | 5.4.254 | 6.12 |
| **Network** | 5 GHz Wi-Fi | 1 GbE |
| **Baseline RTT** | 2.1 ms to server | — |

### 1.2 Software Stack

All implementations were evaluated via SimpleXray (`assembleDebug`) with Android VpnService transparent routing to an in-process Xray-core instance via local SOCKS5 loopback (`127.0.0.1`, RFC 1928).

| Backend | Core Language | Underlying Stack / Runtime | Tracked Revision |
| :--- | :--- | :--- | :--- |
| **Hev** | C | lwIP (embedded TCP/IP) | v2.17.1 (`b514150`) |
| **SingTUN** | Go | sing-box userspace tun / Go 1.27.1 | Commit `aff4131a9e9e` |
| **Zeptun** | Zig | Custom user-space stack | v1.1.1 (`4d24203`) |
| **Xray-core** | Go | Upstream SOCKS5 endpoint | v26.9.9 |

---

## 2. Methodology

The benchmark was executed headlessly via Android `BenchmarkService`. Each configuration was run three times with a 3-second cooldown between cases. Reported figures are the arithmetic mean of successful runs. Failed or timed-out runs were excluded from the mean and are reported separately.

1. **Throughput (iPerf3)**: MTU 1500 bytes. Workloads: TCP $P=1$, TCP $P=8$, and UDP (10 s per direction).
2. **CPU Compute Cost**: Cumulative process CPU load divided by throughput in units of 100 Mbps ($\% \text{ CPU} / 100 \text{ Mbps}$).
3. **Loaded Latency**: 8-stream TCP download with concurrent 100 ms echo probing ($\Delta \text{ ms} = \text{RTT}_{\text{loaded}} - \text{RTT}_{\text{idle}}$).
4. **Sustained Stability**: 60-second continuous 8-stream TCP download; throughput sampled at 1 Hz.
5. **Short-Lived TCP Connections (CPS)**: 5,000 requests dispatched across 4 and 8 workers; metrics: conn/s, P50, and P95 latency (120 s timeout).
6. **Weak-Network Resilience**: Server-side `netem` bridge with fixed 50 ms base RTT; uniform packet loss at 3%, 5%, and 8% on 8-stream TCP.
7. **Isolated Memory Attribution**: Scheme 2 microbenchmark in a Linux network namespace (`unshare -r -n`); PSS sampled across 0 to 1,000 idle retained TCP/UDP connections.

---

## 3. Results

### 3.1 Throughput & Transport Capacity

![Wi-Fi Throughput](../charts/wifi_throughput.webp)

| Scenario | Direction | Hev (C / lwIP) | SingTUN (Go) | Zeptun (Zig) |
| :--- | :--- | :--- | :--- | :--- |
| **TCP $P=1$ (Single Stream)** | Download | 652.0 Mbps | 386.4 Mbps | 678.9 Mbps |
| | Upload | 596.2 Mbps | 448.1 Mbps | 639.2 Mbps |
| **TCP $P=8$ (Multi-Stream)** | Download | 259.8 Mbps | 766.1 Mbps | 774.2 Mbps |
| | Upload | 637.1 Mbps | 711.2 Mbps | 755.9 Mbps |
| **UDP (Single Stream)** | Download | 486.2 Mbps | 312.4 Mbps | 498.7 Mbps |
| | Upload | 461.3 Mbps | 340.5 Mbps | 472.0 Mbps |

- At $P=1$, Zeptun and Hev recorded 678.9 Mbps and 652.0 Mbps download throughput, whereas SingTUN reached 386.4 Mbps.
- At $P=8$, SingTUN and Zeptun saturated the Wi-Fi downlink at 766.1 Mbps and 774.2 Mbps; Hev download throughput decreased to 259.8 Mbps.

---

### 3.2 CPU Cost per 100 Mbps

![CPU Efficiency](../charts/cpu_efficiency.webp)

| Scenario | Direction | Hev (C / lwIP) | SingTUN (Go) | Zeptun (Zig) |
| :--- | :--- | :--- | :--- | :--- |
| **$P=1$ (Single Stream)** | Upload | 10.8% | 15.2% | 8.4% |
| | Download | 12.8% | 105.1% | 14.2% |
| **$P=8$ (Multi-Stream)** | Upload | 9.7% | 18.1% | 9.1% |
| | Download | 28.1% | 52.8% | 15.4% |

- In $P=8$ download, Zeptun consumed 15.4% CPU per 100 Mbps (109.2% process CPU at 774.2 Mbps), compared to 52.8% CPU per 100 Mbps for SingTUN (406.4% process CPU at 766.1 Mbps).
- In $P=1$ download, SingTUN required 105.1% CPU per 100 Mbps.

---

### 3.3 Loaded Latency

![Bufferbloat](../charts/bufferbloat.webp)

| Backend | Background Download Throughput | Baseline Latency | Loaded Latency Delta ($\Delta \text{ ms}$) |
| :--- | :--- | :--- | :--- |
| **Hev** | 313 Mbps (Constrained) | 2.1 ms | +12.6 ms |
| **SingTUN** | 786 Mbps (Saturated) | 2.1 ms | +76.1 ms |
| **Zeptun** | 794 Mbps (Saturated) | 2.1 ms | +79.0 ms |

- Under saturated downlink load (786–794 Mbps), latency inflation was +76.1 ms for SingTUN and +79.0 ms for Zeptun.
- Hev recorded +12.6 ms latency inflation with background throughput at 313 Mbps.

---

### 3.4 60-Second Sustained Throughput Stability

![Sustained Stability](../charts/sustained_stability.webp)

- **Zeptun**: Throughput remained within 760–775 Mbps throughout the 60-second window.
- **SingTUN**: Throughput remained within 740–765 Mbps during the second half.
- **Hev**: Throughput plateaued within 250–310 Mbps.

---

### 3.5 Short-Lived TCP Connections (CPS)

![Short-Lived TCP Performance](../charts/cps.webp)

| Backend | Workers | Connection Rate | Median Latency (P50) | Tail Latency (P95) | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Hev** | 4W / 8W | N/A | N/A | N/A | Timed out (> 120 s) |
| **SingTUN** | 4 Workers | 473 conn/s | 7.8 ms | 12.2 ms | Completed |
| | 8 Workers | 786 conn/s | 9.2 ms | 15.0 ms | Completed |
| **Zeptun** | 4 Workers | 248 conn/s | 16.0 ms | 29.5 ms | Completed |
| | 8 Workers | 337 conn/s | 16.3 ms | 29.8 ms | Completed |

- **Hev** did not complete the 5,000-connection workload within the 120-second timeout. (historical lower-scale runs: ~16 conn/s at 4W, ~34 conn/s at 8W).
  > **Note:** Earlier lower-scale runs are included for context only and
  >
  > are not directly comparable with the 5,000-connection workload.
- **SingTUN** completed at 473 conn/s (4W) and 786 conn/s (8W), with P50 latencies of 7.8 ms and 9.2 ms.
- **Zeptun** completed at 248 conn/s (4W) and 337 conn/s (8W), with P50 latencies of 16.0 ms and 16.3 ms.

---

### 3.6 Weak-Network Packet Loss Resilience

![Weak-Network Throughput](../charts/weaknet_throughput.webp)

| Loss Rate (%) | Flow Direction | Hev (C / lwIP) | SingTUN (Go) | Zeptun (Zig) |
| :--- | :--- | :--- | :--- | :--- |
| **3% Loss** | Upload | 86.4 Mbps | 168.2 Mbps | 192.5 Mbps |
| | Download | 91.2 Mbps | 184.6 Mbps | 206.8 Mbps |
| **5% Loss** | Upload | 42.1 Mbps | 124.5 Mbps | 153.2 Mbps |
| | Download | 48.6 Mbps | 136.0 Mbps | 162.7 Mbps |
| **8% Loss** | Upload | 14.8 Mbps | 92.4 Mbps | 118.6 Mbps |
| | Download | 16.2 Mbps | 101.4 Mbps | 129.5 Mbps |

- At 3% loss, download throughput fell to 206.8 Mbps (Zeptun), 184.6 Mbps (SingTUN), and 91.2 Mbps (Hev).
- At 8% loss, download throughput was 129.5 Mbps (Zeptun), 101.4 Mbps (SingTUN), and 16.2 Mbps (Hev).

---

### 3.7 Isolated Memory Attribution (Scheme 2 Microbenchmark)

![Memory Footprint Attribution](../charts/memory_attribution.webp)

| Backend | Base Footprint ($\text{PSS}_0$) | Slope (TCP) | Slope (UDP) | PSS @ 1,000 Connections |
| :--- | :--- | :--- | :--- | :--- |
| **Hev** | 2.1 MB | 12.58 KiB / conn | 16.59 KiB / conn | 14.4 MB (TCP) / 18.3 MB (UDP) |
| **SingTUN** | 9.7 MB | 53.22 KiB / conn | 50.56 KiB / conn | 62.7 MB (TCP) / 61.1 MB (UDP) |
| **Zeptun** | 326.3 MB | 1.11 KiB / conn | 0.60 KiB / conn | 326.9 MB (TCP) / 326.9 MB (UDP) |

- **Base Footprint**: Hev initialized at 2.1 MB PSS, SingTUN at 9.7 MB PSS, and Zeptun at 326.3 MB PSS.
- **Incremental Growth**: Across 1,000 connections, Zeptun increased by 1.11 KiB/conn (TCP), Hev by 12.58 KiB/conn (TCP), and SingTUN by 53.22 KiB/conn (TCP).

---

## 4. Discussion

- **Multi-Stream Downlink Scaling**: Hev's throughput drop on $P=8$ download (259.8 Mbps) aligns with lwIP's single-threaded event loop design, where packet ingress, PCB lookup, and SOCKS5 forwarding share a single thread context. Zeptun and SingTUN utilize multi-threaded or runtime-scheduled dispatch, scaling across available cores.
- **Compute Cost Differences**: SingTUN's higher processor load may be associated with Go runtime overhead, including allocation, garbage collection, and goroutine scheduling. This benchmark did not profile these components individually. Zeptun operates without runtime GC.
- **Connection Handling Limits**: Hev relies on fixed-size lwIP memory pools. Rapid successive connection attempts exhaust available PCBs before reclamation, triggering timeouts. Zeptun's high base PSS (~326 MB) is consistent with substantial upfront allocation of buffers and queues, yielding a low per-connection incremental footprint (1.11 KiB/conn).

---

## 5. Limitations

1. **Hardware Context**: Results were measured on Qualcomm Snapdragon 778G silicon. Core topology and thermal scaling will differ across alternative SoC architectures.
2. **Network Scope**: Benchmarks were conducted over a clean 5 GHz Wi-Fi link. Variable wireless conditions and cellular radio scheduling are not captured.
3. **Software Versions**: Measurements apply to the tracked software revisions in Section 1.2.
4. **Proxy Link**: Evaluations utilized a local SOCKS5 loopback to Xray-core; WAN transit delay and remote encryption overhead were not part of the testbed.

---

## 6. Summary

Under the evaluated test conditions on Snapdragon 778G:

- Hev recorded the lowest base PSS at 2.1 MB and low CPU cost in single-stream workloads, but reached only 259.8 Mbps in P=8 download and did not complete the 5,000-connection workload.

- SingTUN reached 766.1 Mbps in P=8 download and 786 conn/s with 8 workers, with P50 latency of 9.2 ms. It also recorded the highest CPU cost among the tested backends in several workloads.

- Zeptun reached 678.9 Mbps in P=1 download and 774.2 Mbps in P=8 download, with 15.4% CPU / 100 Mbps in P=8 download. Its base PSS was 326.3 MB, while incremental TCP memory growth was 1.11 KiB/connection.
