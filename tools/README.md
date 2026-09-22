# SimpleXray Benchmark Automation Tool

This directory contains automated testing scripts and profiling tools for benchmarking TUN performance across Android devices and PC hosts.

## Files & Architecture

- `benchmark.py`: Unified master CLI runner orchestrating physical throughput (Wi-Fi, USB, Loopback), idle flow retention, and advanced suites (Bufferbloat, 60s stability).
- `advanced_bench.py`: Backward-compatibility wrapper delegating directly to `benchmark.py`.
- `common/`: Core shared libraries:
  - `adb.py`: ADB communication, device discovery, `BenchmarkService` headless control, `tun0` interface verification, CPU/PSS sampling.
  - `iperf.py`: Host/device iPerf3 server management, arguments builder, JSON parsing.
  - `dataset.py`: JSON dataset loading, multi-round arithmetic clean averaging, `version.properties` extraction.
  - `theme.py`: Unified `PALETTE`, JetBrains Mono typography, standard light theme styling.
  - `logging.py`: Terminal colors, logger utilities, and subprocess wrapper.
- `suites/`: High-cohesion benchmark test suites:
  - `standard.py`: Physical media throughput (Wi-Fi, USB, Loopback; MTU 1500/9000, P=1/P=8, TCP/UDP).
  - `idle.py`: Stepped 0 -> 1000 idle connection retention and memory slope probe.
  - `bufferbloat.py`: Saturated TCP download with concurrent ICMP ping probing.
  - `long_run.py`: 60s continuous 8-stream TCP download stability, decay rate, and CV%.
- `generate_charts.py`: Generates standardized 16:9 individual WebP publication dashboards.
- `generate_mega_dashboard.py`: Master 8-archetype 3-row mega infographic generator.
- `update_benchmark_doc.py`: Non-destructive, in-place synchronizer for Section 3.1 tables in `android-tun-benchmark.md`.
- `sync_versions.py`: Automated submodule and dependency commit hash synchronizer for `version.properties`.
- `idle_bench/`: High-performance, zero-external-dependency Go probe (cross-compiled for Linux `amd64` and Android `arm64`).
- `microbench/`: Standalone Linux user-namespace microbenchmark harness (Scheme 2).

## Prerequisites

1. **ADB**: Android Debug Bridge installed and accessible in `PATH`.
2. **Device**: An Android device connected via USB or Wi-Fi debugging with `SimpleXray` (debug build) installed.
3. **Python 3**: Python 3.8+ with `matplotlib` and `numpy` (for chart generation).
4. **iPerf3**: Installed on both host and Android device (`/data/local/tmp/iperf3`).
5. **iproute2**: Required for `weaknet` mode host-side `tc netem`.
6. **Go**: Go 1.22+ (for building `idle_bench` and `microbench/socks5_sink`).

## Usage

### 1. Android End-to-End Benchmark (Scheme 1)

```bash
# Run all benchmark suites (5GHz Wi-Fi, USB Tethering, On-Device Loopback, and Idle Flows)
python3 tools/benchmark.py --mode all --with-idle --rounds 3

# Run specific suite
python3 tools/benchmark.py --mode wifi --wifi-server-ip 10.189.231.200 --duration 10
python3 tools/benchmark.py --mode loopback --duration 10
python3 tools/benchmark.py --mode idle --network udp --backends hev,xray,sing,mips

# Weak-network TCP download through host netem (requires root/sudo on the host)
python3 tools/benchmark.py \
  --mode weaknet \
  --device <adb-serial> \
  --device-profile 8-elite-gen-5 \
  --wifi-server-ip 192.168.31.236 \
  --backends hev,xray,sing,mips \
  --netem-losses 1,3 \
  --netem-delay 50 \
  --weaknet-duration 15

# Short-lived TCP connection rate (CPS)
python3 tools/benchmark.py \
  --mode cps \
  --device <adb-serial> \
  --device-profile 8-elite-gen-5 \
  --wifi-server-ip 192.168.31.236 \
  --backends hev,xray,sing,mips \
  --cps-workers 1,4,8 \
  --cps-connections 5000
```

### 2. Standalone Pure Stack Microbenchmark (Scheme 2)

```bash
# Run 3-round standalone Linux unshare microbenchmark
python3 tools/microbench/microbench.py --rounds 3
```

### 3. Generate Charts & Dashboards

```bash
# Regenerate all publication dashboards for one device.
python3 tools/generate_charts.py --device 8-elite-gen-5

# Explicit paths and DUT label can still be supplied when needed.
python3 tools/generate_charts.py \
  --json docs/benchmark/qualcomm-snapdragon-778g/data/benchmark_results.json \
  --microbench docs/benchmark/qualcomm-snapdragon-778g/data/microbench_results.json \
  --output-dir docs/benchmark/qualcomm-snapdragon-778g/charts \
  --dut "Snapdragon 778G"
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
| `--device-profile` | `string` | `8-elite-gen-5` | Device dataset profile for default paths (`778g` or `8-elite-gen-5`). |
| `--output-json` | `string` | profile data dir | Path to save JSON results. |
| `--output-md` | `string` | profile data dir | Path to save Markdown tables. |
| `--advanced-json` | `string` | profile data dir | Path to save advanced benchmark results. |

## Outputs

The tools output real-time terminal progress, per-stream throughput (Mbps/Gbps), packet loss/jitter for UDP, CPU utilization (average and peak), PSS memory consumption, and connection retention slopes (KiB/conn). Generated results are structured into:
- Android End-to-End Dataset: `docs/benchmark/<device>/data/benchmark_results.json`
- Android End-to-End Markdown Summary: `docs/benchmark/<device>/data/benchmark_summary.md`
- Standalone Microbenchmark Dataset: `docs/benchmark/<device>/data/microbench_results.json`
- Standalone Microbenchmark Summary: `docs/benchmark/<device>/data/microbench_summary.md`
- Visual Dashboards: `docs/benchmark/<device>/charts/*_dashboard.webp`
