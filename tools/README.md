# SimpleXray Benchmark Automation Tool

This directory contains automated testing scripts and profiling tools for benchmarking TUN performance across Android devices and PC hosts.

## Files & Architecture

- `benchmark.py`: Cross-platform Python benchmark test runner supporting multi-mode throughput (TCP/UDP, MTU 1500/9000, single/8-stream), CPU usage, memory profiling, and idle flow tracking across all TUN backends (`Hev`, `Xray Native TUN`, `SingTUN`, `MipsTUN`).
- `generate_charts.py`: Generates standardized 16:9 ultra-wide WebP/SVG benchmark visualization dashboards with JetBrains Mono typography.
- `idle_bench/`: High-performance, zero-external-dependency Go probe (cross-compiled for both Linux `amd64` and Android `arm64`) for stepped 0 $\to$ 1000 connection retention and userspace PSS memory growth profiling.
- `update_benchmark_doc.py`: Data-driven report synchronization script that reads benchmark JSON datasets and updates `docs/benchmark/android-tun-benchmark.md`.

## Prerequisites

1. **ADB**: Android Debug Bridge installed and accessible in `PATH`.
2. **Device**: An Android device connected via USB or Wi-Fi debugging with `SimpleXray` (debug build) installed.
3. **Python 3**: Python 3.8+ with `matplotlib` and `numpy` (for chart generation).
4. **iPerf3**: Installed on both host and Android device (`/data/local/tmp/iperf3`).

## Usage

Run the benchmark runner:

```bash
# Run all benchmark suites (5GHz Wi-Fi, USB Tethering, On-Device Loopback, and Idle Flows)
python3 tools/benchmark.py --mode all --with-idle --rounds 3

# Run specific suite
python3 tools/benchmark.py --mode wifi --wifi-server-ip 10.189.231.200 --duration 10
python3 tools/benchmark.py --mode loopback --duration 10
python3 tools/benchmark.py --mode idle --network udp --backends hev,xray,sing,mips

# Regenerate all visualization dashboards
python3 tools/generate_charts.py --json docs/benchmark/benchmark_results.json

# Update the main Markdown report
python3 tools/update_benchmark_doc.py
```

## Parameters

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

The tool outputs real-time terminal progress, per-stream throughput (Mbps/Gbps), packet loss/jitter for UDP, CPU utilization (average and peak via `top`), PSS memory consumption via `dumpsys meminfo`, and connection retention slopes (KiB/conn). Generated results are structured into:
- JSON Dataset: `docs/benchmark/benchmark_results.json`
- Markdown Summary: `docs/benchmark/benchmark_summary.md`
- Visual Dashboards: `docs/images/*_dashboard.webp`
