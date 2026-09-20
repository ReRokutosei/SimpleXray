"""
Bufferbloat & Loaded Latency Delta Suite:
Measures latency inflation (Bufferbloat) under single-stream and multi-stream TCP saturation
using concurrent ICMP ping probing.
"""

import os
import re
import subprocess
import threading
import time
from typing import Any, Dict, List, Optional

import matplotlib.pyplot as plt
import numpy as np

from common.adb import AdbRunner
from common.iperf import DEFAULT_PORT, parse_iperf_json
from common.logging import log_info, log_success, log_warn
from common.theme import (
    PALETTE,
    TARGET_ORDER,
    setup_fonts,
    apply_global_theme,
    add_dashboard_header,
    create_top_legend,
    save_dashboard,
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
    parallel: int = 1,
    duration: int = 10
) -> Dict[str, Any]:
    """
    Runs an iPerf3 download while concurrently pinging the host.
    Computes idle RTT, loaded RTT, and Delta RTT (Bufferbloat).
    """
    adb.set_app_state(backend, 1500, cmd="stop")
    time.sleep(2)
    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="start")
        adb.wait_for_tun0(backend)
    else:
        adb.ensure_no_tun0()

    # Measure idle RTT
    idle_rtt = measure_idle_ping(adb, server_ip, count=15)
    log_info(f"[{backend}] Idle RTT: {idle_rtt:.2f} ms")

    # Start host iperf3 server
    server_proc = subprocess.Popen(
        ["iperf3", "-s", "-p", str(DEFAULT_PORT)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    )
    time.sleep(0.5)

    # Start concurrent ping thread on device
    ping_rtts: List[float] = []
    ping_stop_event = threading.Event()

    def ping_worker():
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

    # Run iperf3 download from device (reverse mode)
    par_flag = f"-P {parallel} -l 64K" if parallel > 1 else ""
    client_cmd = f"/data/local/tmp/iperf3 -c {server_ip} -p {DEFAULT_PORT} -R -t {duration} {par_flag} -J"
    log_info(f"[{backend}] Starting {duration}s TCP download (P={parallel}) with concurrent ping...")
    rc, iperf_out, _ = adb.shell(client_cmd, timeout=duration + 15)

    # Stop pinging and server
    ping_stop_event.set()
    ping_thread.join(timeout=2)
    server_proc.terminate()
    try:
        server_proc.wait(timeout=2)
    except subprocess.TimeoutExpired:
        server_proc.kill()

    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="stop")
        time.sleep(2)

    # Process loaded RTT
    loaded_rtt = float(np.median(ping_rtts)) if ping_rtts else idle_rtt
    delta_rtt = max(0.0, loaded_rtt - idle_rtt)

    # Process throughput
    metrics = parse_iperf_json(iperf_out, network="tcp")
    tp_mbps = metrics["mbps"]

    log_success(
        f"[{backend} P={parallel}] TP: {tp_mbps:.1f} Mbps | "
        f"Idle RTT: {idle_rtt:.1f} ms -> Loaded RTT: {loaded_rtt:.1f} ms (Delta: +{delta_rtt:.1f} ms)"
    )

    return {
        "backend": backend,
        "parallel": parallel,
        "duration": duration,
        "throughput_mbps": tp_mbps,
        "idle_rtt_ms": round(idle_rtt, 2),
        "loaded_rtt_ms": round(loaded_rtt, 2),
        "delta_rtt_ms": round(delta_rtt, 2),
        "pings_count": len(ping_rtts)
    }


def run_bufferbloat_suite(
    adb: AdbRunner,
    server_ip: str,
    backends: List[str] = TARGET_ORDER,
    duration: int = 10
) -> List[Dict[str, Any]]:
    """Runs bufferbloat test for all backends at P=1 and P=8."""
    results = []
    for b in backends:
        results.append(run_bufferbloat_case(adb, server_ip, b, parallel=1, duration=duration))
        results.append(run_bufferbloat_case(adb, server_ip, b, parallel=8, duration=duration))
    return results


def render_bufferbloat_chart(results: List[Dict[str, Any]], output_path: str):
    """Renders Bufferbloat & Loaded Latency Delta publication dashboard."""
    apply_global_theme()
    prop_regular, prop_bold, prop_medium = setup_fonts()

    fig = plt.figure(figsize=(16, 9.5), dpi=200)
    add_dashboard_header(
        fig,
        "Bufferbloat and Loaded Latency Delta",
        "Latency inflation under saturated TCP download across TUN backends (5GHz Wi-Fi)",
        prop_bold=prop_bold,
        prop_regular=prop_regular
    )

    ax1 = fig.add_axes([0.08, 0.10, 0.40, 0.68])
    ax2 = fig.add_axes([0.55, 0.10, 0.40, 0.68])

    p1_cases = [r for r in results if r["parallel"] == 1]
    p8_cases = [r for r in results if r["parallel"] == 8]

    def draw_bar_subplot(ax, cases, title, is_left=True):
        ax.set_title(title, fontsize=12, fontweight='bold', fontproperties=prop_bold, color='#1e293b', pad=12)
        backends = [b for b in TARGET_ORDER if any(c["backend"] == b for c in cases)]
        cases_map = {c["backend"]: c for c in cases}

        y_pos = np.arange(len(backends))[::-1]
        deltas = [cases_map[b]["delta_rtt_ms"] for b in backends]
        colors = [PALETTE[b]['fill'] for b in backends]
        edges = [PALETTE[b]['edge'] for b in backends]

        ax.set_yticks(y_pos)
        if is_left:
            ax.set_yticklabels([PALETTE[b]['name'].split()[0] for b in backends], fontsize=10, fontproperties=prop_medium)
        else:
            ax.set_yticklabels([])

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
    create_top_legend(fig, legend_rects, legend_labels, y_pos=0.895, prop_medium=prop_medium)

    save_dashboard(fig, output_path, dpi=200)
