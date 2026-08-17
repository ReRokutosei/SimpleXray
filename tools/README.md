# SimpleXray Benchmark Automation Tool

This directory contains automated testing scripts and tools for benchmarking TUN performance across Android devices and PC hosts.

## Files

- `benchmark.ps1`: Automated PowerShell test runner supporting multi-mode throughput, CPU usage, and memory profiling.
- `bin/`: (Optional / Auto-downloaded) Directory containing host `iperf3.exe` and Android `iperf3-arm64` static binary.

## Prerequisites

1. **ADB**: Android Debug Bridge installed and accessible in `PATH`.
2. **Device**: An Android device connected via USB or Wi-Fi debugging with `SimpleXray` (debug build) installed.
3. **PowerShell**: PowerShell 5.1+ or PowerShell 7+.

## Usage

Run the benchmark script from PowerShell:

```powershell
# Run all benchmark suites (USB, Wi-Fi, and On-Device Loopback)
powershell -ExecutionPolicy Bypass -File tools/benchmark.ps1 -Mode all

# Run specific mode
powershell -ExecutionPolicy Bypass -File tools/benchmark.ps1 -Mode wifi -WifiServerIp 10.35.20.200 -Duration 10
powershell -ExecutionPolicy Bypass -File tools/benchmark.ps1 -Mode usb -UsbServerIp 192.168.232.59 -Duration 10
powershell -ExecutionPolicy Bypass -File tools/benchmark.ps1 -Mode loopback -Duration 10
```

## Parameters

| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `-Mode` | `string` | `all` | Benchmark suite to run (`wifi`, `usb`, `loopback`, `all`). |
| `-WifiServerIp` | `string` | `10.35.20.200` | Target host IPv4 address in the local Wi-Fi subnet. |
| `-UsbServerIp` | `string` | `192.168.232.59` | Target host IPv4 address in the USB tethering subnet. |
| `-Duration` | `int` | `10` | Test duration in seconds per direction / stream. |
| `-AdbDevice` | `string` | `""` | ADB device serial when multiple devices are connected. |

## Outputs

The script outputs real-time progress, per-stream bandwidth, CPU utilization metrics (average and peak via `top`), memory consumption (TOTAL PSS via `dumpsys meminfo`), and formats a complete summary table in Markdown upon completion.
