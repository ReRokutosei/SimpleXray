# SimpleXray Benchmark Automation Tool

This directory contains automated testing scripts and profiling tools for benchmarking TUN performance across Android devices and PC hosts.

## Files & Architecture

- `benchmark.py`: Unified master CLI runner orchestrating physical throughput (Wi-Fi, USB, Loopback), idle flow retention, and advanced suites (Bufferbloat, 60s stability, CPS, Weaknet). Supports `--preset light` and `--preset full`.
- `presets.py`: Formal benchmark contract definitions (`light` vs `full`).
- `generate_charts.py`: Unified publication-grade 16:9 dashboard generator (Wi-Fi, Bufferbloat, Stability, Idle Memory, CPS, Weaknet, Scheme 2 Memory Attribution).
- `generate_mega_dashboard.py`: Master 8-archetype 3-row mega infographic generator.
- `common/`: Core shared libraries:
  - `adb.py`: ADB communication, device discovery, `BenchmarkService` headless control, `tun0` interface verification, CPU/PSS sampling.
  - `device.py`: Symmetric profile path resolution (`778g`, `8-elite-gen-5`) mapping data, chart, and report locations.
  - `iperf.py`: Host/device iPerf3 server management, arguments builder, JSON parsing.
  - `dataset.py`: JSON dataset loading, multi-round arithmetic clean averaging, `version.properties` extraction.
  - `theme.py`: Unified `PALETTE`, JetBrains Mono typography, standard light theme styling.
  - `netem.py`: Linux host `tc netem` delay/loss network condition emulator.
  - `logging.py`: Terminal colors, logger utilities, and subprocess wrapper.
- `suites/`: High-cohesion benchmark test suites:
  - `throughput.py`: Physical media throughput (Wi-Fi, USB, Loopback; MTU 1500/9000, P=1/P=8, TCP/UDP).
  - `idle_memory.py`: Stepped 0 -> 1000 idle connection retention and memory slope probe.
  - `bufferbloat.py`: Saturated TCP download with concurrent TCP echo probe.
  - `stability.py`: 60s continuous 8-stream TCP download stability, decay rate, and CV%.
  - `weaknet.py`: Bidirectional TCP throughput under host netem delay and packet loss.
  - `cps.py`: Short-lived TCP connection-per-second handshake stress suite.
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

### 1. Preset-Driven Benchmark (Recommended)

#### Light Preset (Hev, SingTUN, Zeptun on Wi-Fi MTU 1500, no baseline)
```bash
python3 tools/benchmark.py \
  --preset light \
  --device <adb-serial> \
  --device-profile 778g \
  --wifi-server-ip 192.168.31.236
```

#### Full Preset (All backends, physical baseline, Wi-Fi / USB / Loopback, MTU 1500 & 9000)
```bash
python3 tools/benchmark.py \
  --preset full \
  --device <adb-serial> \
  --device-profile 8-elite-gen-5 \
  --wifi-server-ip 192.168.31.236
```

### 2. Manual Suite Invocation

```bash
# Run specific suite
python3 tools/benchmark.py --mode throughput --wifi-server-ip 192.168.31.236 --duration 10
python3 tools/benchmark.py --mode idle_memory --network tcp --backends hev,sing,zeptun

# Weak-network TCP download through host netem (requires root/sudo on the host)
python3 tools/benchmark.py \
  --mode weaknet \
  --device <adb-serial> \
  --device-profile 778g \
  --wifi-server-ip 192.168.31.236 \
  --backends hev,sing,zeptun \
  --netem-losses 3,5,8 \
  --netem-delay 50 \
  --weaknet-duration 10

# Short-lived TCP connection rate (CPS)
python3 tools/benchmark.py \
  --mode cps \
  --device <adb-serial> \
  --device-profile 778g \
  --wifi-server-ip 192.168.31.236 \
  --backends hev,sing,zeptun \
  --cps-workers 4,8 \
  --cps-connections 5000
```

### 3. Publication Dashboard Generation

```bash
# Generate all standardized 16:9 WebP charts directly into docs/benchmark/<profile>/charts/
python3 tools/generate_charts.py --device-profile 778g --preset light
```
