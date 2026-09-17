# SimpleXray Benchmark Automation Tool

This directory contains automated testing scripts and tools for benchmarking TUN performance across Android devices and PC hosts.

## Files

- `benchmark.py`: Cross-platform Python benchmark test runner supporting multi-mode throughput, CPU usage, and memory profiling across all TUN backends (Hev, Xray TUN, SingTUN).
- `generate_charts.py`: Generates SVG/WebP benchmark visualization charts from test result data.

## Prerequisites

1. **ADB**: Android Debug Bridge installed and accessible in `PATH`.
2. **Device**: An Android device connected via USB or Wi-Fi debugging with `SimpleXray` (debug build) installed.
3. **Python 3**: Python 3.8+ with `matplotlib` and `numpy` (for chart generation).
4. **iPerf3**: Installed on both host and Android device (`/data/local/tmp/iperf3`).

## Usage

Run the benchmark runner:

```bash
# Run all benchmark suites (5GHz Wi-Fi, USB Tethering, On-Device Loopback)
python3 tools/benchmark.py --mode all --rounds 3

# Run specific suite
python3 tools/benchmark.py --mode wifi --wifi-server-ip 10.189.231.200 --duration 10
python3 tools/benchmark.py --mode loopback --duration 10
```

## Parameters

| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `--mode` | `string` | `all` | Benchmark suite to run (`wifi`, `usb`, `loopback`, `all`). |
| `--wifi-server-ip` | `string` | `10.189.231.200` | Target host IPv4 address in the local Wi-Fi subnet. |
| `--usb-server-ip` | `string` | `192.168.232.59` | Target host IPv4 address in the USB tethering subnet. |
| `--duration` | `int` | `10` | Test duration in seconds per direction / stream. |
| `--rounds` | `int` | `3` | Number of test rounds to execute. |
| `--device` | `string` | `auto` | ADB device serial when multiple devices are connected. |
| `--output-json` | `string` | `docs/benchmark/benchmark_results.json` | Path to save JSON results. |
| `--output-md` | `string` | `docs/benchmark/benchmark_summary.md` | Path to save Markdown tables. |

## Outputs

The script outputs real-time progress, per-stream bandwidth, CPU utilization metrics (average and peak via `top`), memory consumption (TOTAL PSS via `dumpsys meminfo`), and saves both structured JSON results and formatted Markdown summary tables upon completion.
