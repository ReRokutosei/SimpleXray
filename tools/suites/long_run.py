"""
60-second Sustained High-Throughput Stability & Attenuation Suite:
Samples per-second throughput across 60 seconds of 8-stream TCP download,
computing the coefficient of variation (CV%) and throughput decay rate (Decay%).
"""

import json
import os
import subprocess
import time
from typing import Any, Dict, List, Optional

import matplotlib.pyplot as plt
import numpy as np

from common.adb import AdbRunner
from common.iperf import DEFAULT_PORT
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


def run_long_run_case(
    adb: AdbRunner,
    server_ip: str,
    backend: str,
    duration: int = 60
) -> Dict[str, Any]:
    """
    Executes a 60-second continuous 8-stream TCP download.
    Extracts 1s interval throughput time series and computes CV% and Decay%.
    """
    adb.set_app_state(backend, 1500, cmd="stop")
    time.sleep(2)
    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="start")
        adb.wait_for_tun0(backend)
    else:
        adb.ensure_no_tun0()

    # Start host iperf3 server
    server_proc = subprocess.Popen(
        ["iperf3", "-s", "-p", str(DEFAULT_PORT)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    )
    time.sleep(0.5)

    client_cmd = f"/data/local/tmp/iperf3 -c {server_ip} -p {DEFAULT_PORT} -R -t {duration} -P 8 -l 64K -i 1 -J"
    log_info(f"[{backend}] Starting {duration}s sustained 8-stream TCP download...")
    rc, out, _ = adb.shell(client_cmd, timeout=duration + 20)

    server_proc.terminate()
    try:
        server_proc.wait(timeout=2)
    except subprocess.TimeoutExpired:
        server_proc.kill()

    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="stop")
        time.sleep(2)

    time_series = []
    try:
        data = json.loads(out)
        intervals = data.get("intervals", [])
        for interval in intervals:
            sum_data = interval.get("sum", {})
            bps = sum_data.get("bits_per_second", 0.0)
            mbps = round(bps / 1_000_000.0, 2)
            time_series.append(mbps)
    except Exception as e:
        log_warn(f"Failed to parse intervals from iperf3 JSON ({backend}): {e}")

    if not time_series:
        time_series = [0.0] * duration

    avg_tp = float(np.mean(time_series))
    std_tp = float(np.std(time_series))
    cv_pct = round((std_tp / avg_tp) * 100.0, 2) if avg_tp > 0 else 0.0

    # Calculate decay (First 5s average vs Last 5s average)
    first_5s = np.mean(time_series[:5]) if len(time_series) >= 5 else avg_tp
    last_5s = np.mean(time_series[-5:]) if len(time_series) >= 5 else avg_tp
    decay_pct = round(((last_5s - first_5s) / first_5s) * 100.0, 2) if first_5s > 0 else 0.0

    log_success(
        f"[{backend}] 60s Stability: Avg {avg_tp:.1f} Mbps | "
        f"CV: {cv_pct:.1f}% | Decay: {decay_pct:+.1f}% (First: {first_5s:.1f}M -> Last: {last_5s:.1f}M)"
    )

    return {
        "backend": backend,
        "duration": duration,
        "avg_throughput_mbps": round(avg_tp, 2),
        "cv_percent": cv_pct,
        "decay_percent": decay_pct,
        "first_5s_avg_mbps": round(first_5s, 2),
        "last_5s_avg_mbps": round(last_5s, 2),
        "time_series": time_series
    }


def run_long_run_suite(
    adb: AdbRunner,
    server_ip: str,
    backends: List[str] = TARGET_ORDER,
    duration: int = 60
) -> List[Dict[str, Any]]:
    """Runs 60s long run stability test for all backends."""
    results = []
    for b in backends:
        results.append(run_long_run_case(adb, server_ip, b, duration=duration))
    return results


def render_long_run_chart(results: List[Dict[str, Any]], output_path: str):
    """Renders 60s Long-Run Throughput Stability & Decay Curves."""
    apply_global_theme()
    prop_regular, prop_bold, prop_medium = setup_fonts()

    fig = plt.figure(figsize=(16, 9.5), dpi=200)
    ax = fig.add_axes([0.08, 0.10, 0.88, 0.69])

    add_dashboard_header(
        fig,
        "Long Run Throughput Stability and Decay (Higher is Better)",
        "60 seconds continuous 8 stream TCP download under 5GHz Wi Fi",
        prop_bold=prop_bold,
        prop_regular=prop_regular
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
        avg = float(np.mean(series)) if series else 0.0
        cv = round((float(np.std(series)) / avg) * 100.0, 2) if avg > 0 else 0.0
        first_5s = float(np.mean(series[:5])) if len(series) >= 5 else avg
        last_5s = float(np.mean(series[-5:])) if len(series) >= 5 else avg
        decay = round(((last_5s - first_5s) / first_5s) * 100.0, 2) if first_5s > 0 else 0.0
        lbl_y = label_y_positions[idx]

        tag = f"{b_info['name'].split()[0]}: {avg:.0f}M (CV: {cv:.1f}%, Decay: {decay:+.1f}%)"

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

    create_top_legend(fig, legend_lines, legend_labels, y_pos=0.895, prop_medium=prop_medium)
    save_dashboard(fig, output_path, dpi=200)
