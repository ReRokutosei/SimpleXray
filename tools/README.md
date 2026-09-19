# SimpleXray Benchmark Automation Tool

This directory contains automated testing scripts and profiling tools for benchmarking TUN performance across Android devices and PC hosts.

## Files & Architecture

- `benchmark.py`: Cross-platform Python benchmark test runner supporting multi-mode throughput (TCP/UDP, MTU 1500/9000, single/8-stream), CPU usage, memory profiling, and idle flow tracking across all TUN backends (`Hev`, `Xray Native TUN`, `SingTUN`, `MipsTUN`).
- `generate_charts.py`: Generates standardized 16:9 ultra-wide WebP publication dashboards (light theme) with JetBrains Mono typography and fullstack memory attribution.
- `idle_bench/`: High-performance, zero-external-dependency Go probe (cross-compiled for both Linux `amd64` and Android `arm64`) for stepped 0 $\to$ 1000 connection retention and userspace PSS memory growth profiling.
- `microbench/`: Standalone Linux user-namespace microbenchmark harness (Scheme 2) that benchmarks pure TUN user-space network stacks in isolation without Android OS overhead:
  - `microbench/socks5_sink.go`: Bidirectional TCP/UDP echo SOCKS5 sink server.
  - `microbench/microbench.py`: Unshare-based runner profiling pure stack memory slope (PSS/RSS/Dirty).
- `update_benchmark_doc.py`: Data-driven report synchronization script that reads benchmark JSON datasets and updates `docs/benchmark/android-tun-benchmark.md`.

## Prerequisites

1. **ADB**: Android Debug Bridge installed and accessible in `PATH`.
2. **Device**: An Android device connected via USB or Wi-Fi debugging with `SimpleXray` (debug build) installed.
3. **Python 3**: Python 3.8+ with `matplotlib` and `numpy` (for chart generation).
4. **iPerf3**: Installed on both host and Android device (`/data/local/tmp/iperf3`).
5. **Go**: Go 1.22+ (for building `idle_bench` and `microbench/socks5_sink`).

## Usage

### 1. Android End-to-End Benchmark (Scheme 1)

```bash
# Run all benchmark suites (5GHz Wi-Fi, USB Tethering, On-Device Loopback, and Idle Flows)
python3 tools/benchmark.py --mode all --with-idle --rounds 3

# Run specific suite
python3 tools/benchmark.py --mode wifi --wifi-server-ip 10.189.231.200 --duration 10
python3 tools/benchmark.py --mode loopback --duration 10
python3 tools/benchmark.py --mode idle --network udp --backends hev,xray,sing,mips
```

### 2. Standalone Pure Stack Microbenchmark (Scheme 2)

```bash
# Run 3-round standalone Linux unshare microbenchmark
python3 tools/microbench/microbench.py --rounds 3
```

### 3. Generate Charts & Dashboards

```bash
# Regenerate all publication dashboards (including fullstack attribution)
python3 tools/generate_charts.py
```

## Parameters (benchmark.py)

| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `--mode` | `string` | `wifi,loopback` | Benchmark suite(s) to run (`wifi`, `usb`, `loopback`, `idle`, `all`). |
| `--network` | `string` | `all` | Network protocols to benchmark (`tcp`, `udp`, `all`). |
| `--with-idle` | `flag` | `false` | Include 0-1000 retained connections vs memory growth benchmark. |
| `--backends` | `string` | `all` | TUN backends to test (`hev`, `xray`, `sing`, `mips`, or comma-separated list). |
| `--skip-baseline` | `flag` | `false` | Skip running physical baseline (No VPN) tests. |
| `--wifi-server-ip` | `string` | `10.189.231.200` | Target host IPv4 address in the local Wi-Fi subnet. |
| `--usb-server-ip` | `string` | `auto` | Target host IPv4 address in the USB tethering subnet. |
| `--duration` | `int` | `10` | Test duration in seconds per direction / stream. |
| `--rounds` | `int` | `3` | Number of test rounds to execute. |
| `--device` | `string` | `auto` | ADB device serial when multiple devices are connected. |
| `--output-json` | `string` | `docs/benchmark/benchmark_results.json` | Path to save JSON results. |
| `--output-md` | `string` | `docs/benchmark/benchmark_summary.md` | Path to save Markdown tables. |

## Outputs

The tools output real-time terminal progress, per-stream throughput (Mbps/Gbps), packet loss/jitter for UDP, CPU utilization (average and peak), PSS memory consumption, and connection retention slopes (KiB/conn). Generated results are structured into:
- Android End-to-End Dataset: `docs/benchmark/benchmark_results.json`
- Android End-to-End Markdown Summary: `docs/benchmark/benchmark_summary.md`
- Standalone Microbenchmark Dataset: `docs/benchmark/microbench_results.json`
- Standalone Microbenchmark Summary: `docs/benchmark/microbench_summary.md`
- Visual Dashboards: `docs/images/*_dashboard.webp` (including `fullstack_attribution_dashboard.webp`)
