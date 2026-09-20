#!/usr/bin/env python3
"""
SimpleXray Advanced Benchmark Suite:
1. Bufferbloat & Loaded Latency Delta (TCP Download + Concurrent Ping)
2. 60s Long-Run Throughput Stability & Decay Curves
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
import numpy as np

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
APP_PKG = "com.simplexray.re.debug"
DEFAULT_PORT = 5201

# Register JetBrains Mono fonts if available
FONT_DIR = "/home/vanitas/.local/share/fonts/JetBrains"
FONT_REGULAR = os.path.join(FONT_DIR, "JetBrainsMonoNerdFont-Regular.ttf")
FONT_BOLD = os.path.join(FONT_DIR, "JetBrainsMonoNerdFont-Bold.ttf")
FONT_MEDIUM = os.path.join(FONT_DIR, "JetBrainsMonoNerdFont-Medium.ttf")

prop_regular = None
prop_bold = None
prop_medium = None

for p, target in [(FONT_REGULAR, 'regular'), (FONT_BOLD, 'bold'), (FONT_MEDIUM, 'medium')]:
    if os.path.exists(p):
        try:
            fm.fontManager.addfont(p)
            fprop = fm.FontProperties(fname=p)
            if target == 'regular':
                prop_regular = fprop
            elif target == 'bold':
                prop_bold = fprop
            elif target == 'medium':
                prop_medium = fprop
        except Exception:
            pass

if prop_regular:
    plt.rcParams['font.sans-serif'] = [prop_regular.get_name(), 'DejaVu Sans', 'Arial', 'sans-serif']
    plt.rcParams['font.family'] = 'sans-serif'

plt.rcParams['figure.facecolor'] = '#f8fafc'
plt.rcParams['axes.facecolor'] = '#ffffff'
plt.rcParams['axes.edgecolor'] = '#cbd5e1'
plt.rcParams['axes.labelcolor'] = '#334155'
plt.rcParams['xtick.color'] = '#475569'
plt.rcParams['ytick.color'] = '#0f172a'
plt.rcParams['grid.color'] = '#f1f5f9'
plt.rcParams['grid.alpha'] = 1.0
plt.rcParams['grid.linestyle'] = '-'

PALETTE = {
    'direct_none': {
        'name': 'No VPN (Physical Baseline)',
        'fill': '#94a3b8',
        'edge': '#64748b'
    },
    'hev': {
        'name': 'Hev (C/lwIP)',
        'fill': '#0d9488',
        'edge': '#0f766e'
    },
    'sing': {
        'name': 'SingTUN (Go/sing-box)',
        'fill': '#2563eb',
        'edge': '#1d4ed8'
    },
    'mips': {
        'name': 'MipsTUN (Go/BBRv3)',
        'fill': '#7c3aed',
        'edge': '#6d28d9'
    },
    'xray': {
        'name': 'Xray TUN (gVisor)',
        'fill': '#e11d48',
        'edge': '#be123c'
    }
}

TARGET_ORDER = ['direct_none', 'hev', 'sing', 'mips', 'xray']

def log_info(msg: str):
    print(f"\033[96m[INFO] {msg}\033[0m")

def log_success(msg: str):
    print(f"\033[92m[SUCCESS] {msg}\033[0m")

def log_warn(msg: str):
    print(f"\033[93m[WARN] {msg}\033[0m")

def log_error(msg: str):
    print(f"\033[91m[ERROR] {msg}\033[0m")

def run_cmd(cmd: List[str], timeout: Optional[float] = None) -> Tuple[int, str, str]:
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return proc.returncode, proc.stdout, proc.stderr
    except subprocess.TimeoutExpired:
        return -1, "", "Timeout"
    except Exception as e:
        return -1, "", str(e)


class AdbRunner:
    def __init__(self, device: Optional[str] = None):
        self.device = device
        self._ensure_device()

    def _ensure_device(self):
        rc, out, _ = run_cmd(["adb", "devices"])
        lines = [line.strip() for line in out.strip().splitlines() if line.strip() and not line.startswith("List of devices")]
        devices = [l.split()[0] for l in lines if "\tdevice" in l]
        if not devices:
            raise RuntimeError("No connected/authorized ADB device found.")
        if self.device:
            if self.device not in devices:
                raise RuntimeError(f"Specified device {self.device} not found in connected devices: {devices}")
        else:
            self.device = devices[0]
            log_info(f"Using ADB Device: {self.device}")

    def shell(self, cmd_str: str, timeout: Optional[float] = None) -> Tuple[int, str, str]:
        cmd = ["adb", "-s", self.device, "shell", cmd_str]
        return run_cmd(cmd, timeout=timeout)

    def set_app_state(self, backend: str, mtu: int, cmd: str = "start"):
        log_info(f"Controlling Headless Service: cmd={cmd}, backend={backend}, mtu={mtu}")
        self.shell(
            f"am start-foreground-service -n {APP_PKG}/com.simplexray.re.service.BenchmarkService "
            f"--es cmd {cmd} --es backend {backend} --ei mtu {mtu}",
            timeout=10.0
        )


def add_dashboard_header(fig, title: str, subtitle: str):
    fig.text(
        0.5, 0.970, title,
        fontsize=16, fontweight='bold', color='#0f172a',
        fontproperties=prop_bold if prop_bold else None,
        ha='center', va='top'
    )
    fig.text(
        0.5, 0.938, subtitle,
        fontsize=10, color='#64748b',
        fontproperties=prop_regular if prop_regular else None,
        ha='center', va='top'
    )


def create_top_legend(fig, handles, labels, y_pos=0.895):
    fig.legend(
        handles=handles,
        labels=labels,
        loc='upper center',
        bbox_to_anchor=(0.5, y_pos),
        ncol=len(labels),
        frameon=True,
        facecolor='#f8fafc',
        edgecolor='#cbd5e1',
        fontsize=9.5,
        prop=prop_medium if prop_medium else prop_regular
    )


def measure_idle_ping(adb: AdbRunner, server_ip: str, count: int = 15) -> float:
    """Measures baseline ping RTT (in ms) when no traffic is active."""
    cmd = f"ping -c {count} -i 0.2 -W 1 {server_ip}"
    rc, out, _ = adb.shell(cmd, timeout=count * 0.4 + 5)
    rtts = []
    for line in out.splitlines():
        m = re.search(r"time=([\d\.]+)\s*ms", line)
        if m:
            rtts.append(float(m.group(1)))
    if rtts:
        return float(np.median(rtts))
    return 0.0


def run_bufferbloat_case(
    adb: AdbRunner,
    server_ip: str,
    backend: str,
    parallel: int,
    duration: int = 10
) -> Dict[str, Any]:
    """
    Runs an iPerf3 download while concurrently pinging the host.
    Computes idle RTT, loaded RTT, and Delta RTT (Bufferbloat).
    """
    # 1. Start backend
    adb.set_app_state(backend, 1500, cmd="stop")
    time.sleep(2)
    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="start")
        time.sleep(4)

    # 2. Measure idle RTT
    idle_rtt = measure_idle_ping(adb, server_ip, count=15)
    log_info(f"[{backend}] Idle RTT: {idle_rtt:.2f} ms")

    # 3. Start host iperf3 server
    server_proc = subprocess.Popen(
        ["iperf3", "-s", "-p", str(DEFAULT_PORT)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    )
    time.sleep(0.5)

    # 4. Start concurrent ping thread on device
    ping_rtts: List[float] = []
    ping_stop_event = threading.Event()

    def ping_worker():
        # Ping continuously every 200ms (Android minimal user interval)
        cmd = ["adb", "-s", adb.device, "shell", f"ping -i 0.2 {server_ip}"]
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        while not ping_stop_event.is_set():
            line = p.stdout.readline()
            if not line:
                break
            m = re.search(r"time=([\d\.]+)\s*ms", line)
            if m:
                ping_rtts.append(float(m.group(1)))
        p.terminate()
        p.wait()

    ping_thread = threading.Thread(target=ping_worker, daemon=True)
    ping_thread.start()

    # 5. Run iperf3 download from device (reverse mode)
    par_flag = f"-P {parallel} -l 64K" if parallel > 1 else ""
    client_cmd = f"/data/local/tmp/iperf3 -c {server_ip} -p {DEFAULT_PORT} -R -t {duration} {par_flag} -J"
    log_info(f"[{backend}] Starting {duration}s TCP download (P={parallel}) with concurrent ping...")
    rc, iperf_out, _ = adb.shell(client_cmd, timeout=duration + 15)

    # 6. Stop pinging and server
    ping_stop_event.set()
    ping_thread.join(timeout=2)
    server_proc.terminate()
    server_proc.wait()

    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="stop")
        time.sleep(2)

    # 7. Parse results
    throughput_mbps = 0.0
    try:
        d = json.loads(iperf_out)
        sum_data = d.get("end", {}).get("sum_received", {})
        throughput_mbps = round(float(sum_data.get("bits_per_second", 0)) / 1e6, 2)
    except Exception:
        log_warn(f"Failed to parse iperf3 JSON for {backend}")

    loaded_median = float(np.median(ping_rtts)) if ping_rtts else idle_rtt
    loaded_p95 = float(np.percentile(ping_rtts, 95)) if ping_rtts else idle_rtt
    delta_rtt = max(0.0, round(loaded_median - idle_rtt, 2))

    log_success(
        f"[{backend}] P={parallel} | Speed: {throughput_mbps} Mbps | "
        f"Idle: {idle_rtt:.2f} ms | Loaded Median: {loaded_median:.2f} ms | Delta: +{delta_rtt:.2f} ms"
    )

    return {
        "backend": backend,
        "parallel": parallel,
        "throughput_mbps": throughput_mbps,
        "idle_rtt_ms": idle_rtt,
        "loaded_rtt_median_ms": loaded_median,
        "loaded_rtt_p95_ms": loaded_p95,
        "delta_rtt_ms": delta_rtt
    }


def run_long_run_case(
    adb: AdbRunner,
    server_ip: str,
    backend: str,
    duration: int = 60
) -> Dict[str, Any]:
    """
    Runs a 60-second continuous 8-stream TCP download, sampling every 1 second.
    Computes time series, average throughput, CV%, and decay%.
    """
    adb.set_app_state(backend, 1500, cmd="stop")
    time.sleep(2)
    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="start")
        time.sleep(4)

    server_proc = subprocess.Popen(
        ["iperf3", "-s", "-p", str(DEFAULT_PORT)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    )
    time.sleep(0.5)

    client_cmd = f"/data/local/tmp/iperf3 -c {server_ip} -p {DEFAULT_PORT} -R -P 8 -l 64K -t {duration} -i 1 -J"
    log_info(f"[{backend}] Running 60s long-run download test...")
    rc, out, _ = adb.shell(client_cmd, timeout=duration + 20)

    server_proc.terminate()
    server_proc.wait()

    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="stop")
        time.sleep(2)

    series: List[float] = []
    try:
        d = json.loads(out)
        intervals = d.get("intervals", [])
        for item in intervals:
            sum_info = item.get("sum", {})
            bps = float(sum_info.get("bits_per_second", 0))
            series.append(round(bps / 1e6, 2))
    except Exception as e:
        log_warn(f"Failed to parse intervals for {backend}: {e}")

    if not series:
        series = [0.0] * duration

    avg_tp = round(float(np.mean(series)), 2)
    std_tp = round(float(np.std(series)), 2)
    cv_percent = round((std_tp / avg_tp) * 100, 2) if avg_tp > 0 else 0.0

    first_10 = np.mean(series[:10]) if len(series) >= 10 else avg_tp
    last_10 = np.mean(series[-10:]) if len(series) >= 10 else avg_tp
    decay_percent = round(float((first_10 - last_10) / first_10 * 100), 2) if first_10 > 0 else 0.0

    log_success(
        f"[{backend}] 60s Average: {avg_tp} Mbps | Std: {std_tp} | CV: {cv_percent}% | Decay: {decay_percent}%"
    )

    return {
        "backend": backend,
        "duration": duration,
        "time_series": series,
        "avg_throughput_mbps": avg_tp,
        "std_throughput_mbps": std_tp,
        "cv_percent": cv_percent,
        "decay_percent": decay_percent
    }


def render_bufferbloat_chart(results: List[Dict[str, Any]], output_path: str):
    """
    Renders Bufferbloat Delta RTT Dashboard.
    Left: Single Stream (P=1) (Lower is Better)
    Right: Multi-Stream (P=8 Parallel) (Lower is Better)
    """
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 9.5), dpi=200)
    plt.subplots_adjust(left=0.18, right=0.96, top=0.80, bottom=0.08, wspace=0.10)

    add_dashboard_header(
        fig,
        "Bufferbloat and Loaded Latency Delta (Lower is Better)",
        "Ping latency inflation in milliseconds during active TCP download under 5GHz Wi Fi"
    )

    p1_cases = {r["backend"]: r for r in results if r["parallel"] == 1}
    p8_cases = {r["backend"]: r for r in results if r["parallel"] == 8}

    def draw_bar_subplot(ax, cases_map, sub_title, is_left: bool = True):
        ax.set_title(sub_title, fontsize=12, fontweight='bold', color='#1e293b',
                     fontproperties=prop_bold, pad=12)

        backends = [b for b in TARGET_ORDER if b in cases_map]
        y_pos = np.arange(len(backends))
        labels = [PALETTE[b]["name"] for b in backends]

        ax.set_yticks(y_pos)
        if is_left:
            ax.set_yticklabels(labels, fontsize=10, fontproperties=prop_medium, color='#334155')
        else:
            ax.set_yticklabels([])
            ax.tick_params(left=False)

        deltas = [cases_map[b]["delta_rtt_ms"] for b in backends]
        colors = [PALETTE[b]["fill"] for b in backends]
        edges = [PALETTE[b]["edge"] for b in backends]

        max_val = max(max(deltas, default=10.0), 10.0)
        ax.set_xlim(0, max_val * 1.35)
        ax.set_xlabel("Latency Inflation Delta (ms)", fontsize=10, color='#64748b', fontproperties=prop_regular)
        ax.grid(True, axis='x', zorder=0)

        rects = ax.barh(y_pos, deltas, 0.42, color=colors, edgecolor=edges, linewidth=0.8, zorder=3)

        for rect, b in zip(rects, backends):
            c = cases_map[b]
            d_val = c["delta_rtt_ms"]
            spd = c["throughput_mbps"]
            txt = f"+{d_val:.1f} ms  ({spd:.0f} Mbps)"
            ax.text(
                d_val + max_val * 0.02, rect.get_y() + rect.get_height() / 2,
                txt,
                ha='left', va='center',
                fontsize=8.5, fontweight='bold', color='#1e293b',
                fontproperties=prop_bold,
                bbox=dict(boxstyle='round,pad=0.15', facecolor='#ffffff', edgecolor='none', alpha=0.85),
                zorder=5
            )

    draw_bar_subplot(ax1, p1_cases, "Single Stream (P=1) (Lower is Better)", is_left=True)
    draw_bar_subplot(ax2, p8_cases, "Multi-Stream (P=8 Parallel) (Lower is Better)", is_left=False)

    legend_rects = [plt.Rectangle((0, 0), 1, 1, facecolor=PALETTE[b]['fill'], edgecolor=PALETTE[b]['edge']) for b in TARGET_ORDER]
    legend_labels = [PALETTE[b]['name'] for b in TARGET_ORDER]
    create_top_legend(fig, legend_rects, legend_labels)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=200, facecolor='#f8fafc', edgecolor='none')
    plt.close()
    print(f"[Dashboard] Saved: {output_path}")


def render_long_run_chart(results: List[Dict[str, Any]], output_path: str):
    """
    Renders 60s Long-Run Throughput Stability & Decay Curves.
    Single large plot with 5 high-contrast lines.
    """
    fig = plt.figure(figsize=(16, 9.5), dpi=200)
    ax = fig.add_axes([0.08, 0.12, 0.88, 0.73])

    add_dashboard_header(
        fig,
        "Long Run Throughput Stability and Decay (Higher is Better)",
        "60 seconds continuous 8 stream TCP download under 5GHz Wi Fi"
    )

    legend_lines = []
    legend_labels = []

    time_axis = np.arange(1, 61)

    for b in TARGET_ORDER:
        match = next((r for r in results if r["backend"] == b), None)
        if not match:
            continue
        b_info = PALETTE[b]
        series = match["time_series"][:60]
        if len(series) < 60:
            series.extend([series[-1]] * (60 - len(series)))

        line, = ax.plot(
            time_axis, series,
            label=b_info['name'],
            color=b_info['fill'],
            linewidth=2.2,
            zorder=3
        )
        legend_lines.append(line)
        legend_labels.append(b_info['name'])

    all_max = max([max(r.get("time_series", [500])) for r in results if r.get("time_series")], default=800)
    top_y = max(all_max * 1.16, 500)

    # Smart non-overlapping vertical label alignment on the right margin
    sorted_matches = sorted(
        [m for m in [next((r for r in results if r["backend"] == b), None) for b in TARGET_ORDER] if m],
        key=lambda x: x.get("avg_throughput_mbps", 0.0),
        reverse=True
    )

    label_y_positions = np.linspace(top_y * 0.88, top_y * 0.55, len(sorted_matches))

    for idx, match in enumerate(sorted_matches):
        b = match["backend"]
        b_info = PALETTE[b]
        series = match["time_series"]
        final_y = series[-1]
        cv = match["cv_percent"]
        decay = match["decay_percent"]
        avg = match["avg_throughput_mbps"]
        lbl_y = label_y_positions[idx]

        tag = f"{b_info['name'].split()[0]}: {avg:.0f}M (CV: {cv:.1f}%, Decay: {decay:+.1f}%)"

        # Subtle guide line connecting the curve end to the label
        ax.plot([60.2, 61.2], [final_y, lbl_y], color=b_info['fill'], linestyle=':', linewidth=1.2, alpha=0.85)

        ax.text(
            61.4, lbl_y,
            tag,
            fontsize=8.5, fontweight='bold', color=b_info['edge'],
            va='center', fontproperties=prop_bold,
            bbox=dict(boxstyle='round,pad=0.20', facecolor='#ffffff', edgecolor=b_info['edge'], alpha=0.92, linewidth=0.8)
        )
    ax.set_xlim(0, 78)
    ax.set_ylim(0, max(all_max * 1.18, 500))
    ax.set_xlabel("Elapsed Time (Seconds)", fontsize=10, color='#64748b', fontproperties=prop_regular)
    ax.set_ylabel("Throughput (Mbps)", fontsize=10, color='#64748b', fontproperties=prop_regular)
    ax.grid(True, zorder=0)

    create_top_legend(fig, legend_lines, legend_labels, y_pos=0.88)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=200, facecolor='#f8fafc', edgecolor='none')
    plt.close()
    print(f"[Dashboard] Saved: {output_path}")


def main():
    parser = argparse.ArgumentParser(description="Advanced SimpleXray Benchmark (Bufferbloat & Stability)")
    parser.add_argument("--wifi-server-ip", default="192.168.31.236", help="Host PC IP address in Wi-Fi subnet")
    parser.add_argument("--device", default=None, help="ADB device identifier")
    parser.add_argument("--test", default="all", choices=["all", "bufferbloat", "longrun"], help="Which test to run")
    parser.add_argument("--output-dir", default=os.path.join(PROJECT_ROOT, "docs/images"), help="Directory to save charts")
    parser.add_argument("--output-json", default=os.path.join(PROJECT_ROOT, "docs/benchmark/advanced_benchmark_results.json"))
    args = parser.parse_args()

    adb = AdbRunner(args.device)

    # Load existing JSON if present to prevent wiping previous tests
    all_data: Dict[str, Any] = {
        "timestamp": datetime.now().isoformat(),
        "wifi_server_ip": args.wifi_server_ip,
        "bufferbloat": [],
        "long_run": []
    }
    if os.path.exists(args.output_json):
        try:
            with open(args.output_json, "r", encoding="utf-8") as f:
                existing = json.load(f)
                if isinstance(existing, dict):
                    all_data.update(existing)
                    log_info(f"Loaded existing data from {args.output_json}")
        except Exception as e:
            log_warn(f"Could not load existing {args.output_json}: {e}")

    # 1. Bufferbloat Test Suite
    if args.test in ["all", "bufferbloat"]:
        log_info("\n=======================================================")
        log_info("      STARTING BUFFERBLOAT & LOADED LATENCY TEST       ")
        log_info("=======================================================")
        bloat_results = []
        for b in TARGET_ORDER:
            # Single stream
            r1 = run_bufferbloat_case(adb, args.wifi_server_ip, b, parallel=1, duration=10)
            bloat_results.append(r1)
            # 8-stream
            r8 = run_bufferbloat_case(adb, args.wifi_server_ip, b, parallel=8, duration=10)
            bloat_results.append(r8)

        all_data["bufferbloat"] = bloat_results
        chart_path = os.path.join(args.output_dir, "bufferbloat_dashboard.webp")
        render_bufferbloat_chart(bloat_results, chart_path)

    # 2. 60s Long-Run Stability Test Suite
    if args.test in ["all", "longrun"]:
        log_info("\n=======================================================")
        log_info("     STARTING 60-SECOND LONG-RUN STABILITY TEST        ")
        log_info("=======================================================")
        long_results = []
        for b in TARGET_ORDER:
            res = run_long_run_case(adb, args.wifi_server_ip, b, duration=60)
            long_results.append(res)

        all_data["long_run"] = long_results
        chart_path = os.path.join(args.output_dir, "long_run_stability_dashboard.webp")
        render_long_run_chart(long_results, chart_path)

    # Save results JSON
    os.makedirs(os.path.dirname(args.output_json), exist_ok=True)
    with open(args.output_json, "w", encoding="utf-8") as f:
        json.dump(all_data, f, indent=2, ensure_ascii=False)
    log_success(f"Advanced benchmark results saved to: {args.output_json}")


if __name__ == "__main__":
    main()
