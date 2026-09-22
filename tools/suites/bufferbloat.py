"""
Bufferbloat & Loaded Latency Delta Suite:
Measures latency inflation (Bufferbloat) under single-stream and multi-stream TCP saturation
using concurrent TCP echo probing through the same data path.
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

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TOOLS_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
RTT_PORT = 5301


def measure_tcp_rtt(adb: AdbRunner, server_ip: str, count: int = 15) -> List[float]:
    """Measures TCP echo RTT through the selected Android/TUN path."""
    cmd = (
        f"/data/local/tmp/idle_bench rtt --server {server_ip}:{RTT_PORT} "
        f"--count {count} --interval 200ms --timeout 1s"
    )
    rc, out, err = adb.shell(cmd, timeout=count * 0.4 + 5)
    rtts = []
    for line in out.splitlines():
        m = re.search(r'"rtt_ms":([\d.]+)', line)
        if m:
            rtts.append(float(m.group(1)))
    if rc != 0 and not rtts:
        log_warn(f"TCP RTT probe failed: {err.strip()}")
    return rtts


def run_bufferbloat_case(
    adb: AdbRunner,
    server_ip: str,
    backend: str,
    parallel: int = 1,
    duration: int = 10
) -> Dict[str, Any]:
    """
    Runs an iPerf3 download while concurrently probing TCP echo RTT.
    Computes idle RTT, loaded RTT, and Delta RTT (Bufferbloat).
    """
    adb.set_app_state(backend, 1500, cmd="stop")
    time.sleep(2)
    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="start")
        adb.wait_for_tun0(backend)
    else:
        adb.ensure_no_tun0()

    # Start host TCP echo and iperf3 servers.
    echo_bin = os.path.join(TOOLS_DIR, "idle_bench", "idle_bench_linux_amd64")
    if not os.path.exists(echo_bin):
        raise RuntimeError(f"Host RTT probe binary not found: {echo_bin}")
    echo_proc = subprocess.Popen([echo_bin, "server", "--port", str(RTT_PORT)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    server_proc = subprocess.Popen(
        ["iperf3", "-s", "-p", str(DEFAULT_PORT)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    )
    time.sleep(0.5)

    client_bin = os.path.join(TOOLS_DIR, "idle_bench", "idle_bench_linux_arm64")
    if not os.path.exists(client_bin):
        raise RuntimeError(f"Device RTT probe binary not found: {client_bin}")
    adb.exec(["push", client_bin, "/data/local/tmp/idle_bench"])
    adb.shell("chmod 755 /data/local/tmp/idle_bench")

    idle_samples = measure_tcp_rtt(adb, server_ip, count=15)
    if len(idle_samples) < 3:
        raise RuntimeError(f"[{backend}] TCP RTT idle probe has too few samples: {len(idle_samples)}")
    idle_rtt = float(np.median(idle_samples))
    log_info(f"[{backend}] Idle TCP RTT: {idle_rtt:.2f} ms ({len(idle_samples)} samples)")

    # Start concurrent TCP RTT probe on device.
    rtt_samples: List[float] = []
    rtt_stop_event = threading.Event()

    def rtt_worker():
        cmd = ["adb", "-s", adb.device, "shell", f"/data/local/tmp/idle_bench rtt --server {server_ip}:{RTT_PORT} --count 1000 --interval 200ms --timeout 1s"]
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        while not rtt_stop_event.is_set():
            line = p.stdout.readline()
            if not line:
                break
            m = re.search(r'"rtt_ms":([\d.]+)', line)
            if m:
                rtt_samples.append(float(m.group(1)))
        p.terminate()
        p.wait()

    rtt_thread = threading.Thread(target=rtt_worker, daemon=True)
    rtt_thread.start()

    # Run iperf3 download from device (reverse mode)
    par_flag = f"-P {parallel} -l 64K" if parallel > 1 else ""
    client_cmd = f"/data/local/tmp/iperf3 -c {server_ip} -p {DEFAULT_PORT} -R -t {duration} {par_flag} -J"
    log_info(f"[{backend}] Starting {duration}s TCP download (P={parallel}) with concurrent TCP RTT...")
    rc, iperf_out, _ = adb.shell(client_cmd, timeout=duration + 15)

    # Stop pinging and server
    rtt_stop_event.set()
    rtt_thread.join(timeout=2)
    server_proc.terminate()
    try:
        server_proc.wait(timeout=2)
    except subprocess.TimeoutExpired:
        server_proc.kill()
    echo_proc.terminate()
    try:
        echo_proc.wait(timeout=2)
    except subprocess.TimeoutExpired:
        echo_proc.kill()

    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="stop")
        time.sleep(2)

    # Process loaded RTT
    if len(rtt_samples) < 3:
        raise RuntimeError(f"[{backend} P={parallel}] TCP RTT loaded probe has too few samples: {len(rtt_samples)}")
    loaded_rtt = float(np.median(rtt_samples))
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
        "probe": "tcp_echo",
        "probe_samples_idle": len(idle_samples),
        "probe_samples_loaded": len(rtt_samples)
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
