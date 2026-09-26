#!/usr/bin/env python3
"""
SimpleXray Unified Benchmark Dashboard Generator
Generates clean, publication-quality 16:9 dashboards from standardized JSON datasets.

Design Language:
- Fonts: JetBrains Mono via FontProperties.
- Palette: Hev (#555555), SingTUN (#00ADD8), Zeptun (#F7A41D).
- Backend Order: Alphabetical (Hev -> SingTUN -> Zeptun).
- Headers: Centered title and subtitle without ' · ' dots.
- Direction indicators: (Higher is Better) or (Lower is Better) on all titles.
- Output: Lossless WebP saved directly to docs/benchmark/<profile>/charts/.
"""

import argparse
from collections import defaultdict
import json
import os
import statistics
import sys
from typing import Any, Dict, List, Optional, Tuple

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)

from common.device import DEFAULT_DEVICE, DEVICE_PROFILES, resolve_device_paths
from common.theme import (
    PALETTE,
    setup_fonts,
    apply_global_theme,
    add_dashboard_header,
    create_top_legend,
    save_dashboard,
)

LIGHT_BACKENDS = ["hev", "sing", "zeptun"]
FULL_BACKENDS = ["hev", "sing", "xray", "zeptun"]

NAMES = {
    "direct_none": "Baseline",
    "hev": "Hev",
    "sing": "SingTUN",
    "xray": "Xray",
    "zeptun": "Zeptun",
    "simpletun": "SimpleTUN",
}
COLORS = {
    "direct_none": "#94a3b8",
    "hev": "#555555",
    "sing": "#00ADD8",
    "xray": "#e11d48",
    "zeptun": "#F7A41D",
    "simpletun": "#10B981",
}


def load_json(path: str) -> Optional[Any]:
    if not os.path.exists(path):
        return None
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:
        print(f"[Warning] Failed to load {path}: {e}")
        return None


def flat_rounds(payload: Optional[Dict[str, Any]]) -> List[Dict[str, Any]]:
    if not payload:
        return []
    rounds = payload.get("rounds", {})
    if isinstance(rounds, dict):
        return [item for rows in rounds.values() for item in rows]
    return []


def setup_dashboard(
    title: str,
    subtitle: str,
    figsize: Tuple[float, float] = (16, 9.5)
) -> Tuple[plt.Figure, Any, Any, Any]:
    apply_global_theme()
    prop_regular, prop_bold, prop_medium = setup_fonts()
    fig = plt.figure(figsize=figsize, dpi=200)
    add_dashboard_header(fig, title, subtitle, prop_bold=prop_bold, prop_regular=prop_regular)
    return fig, prop_regular, prop_bold, prop_medium


# ----------------------------------------------------------------------
# 1. Wi-Fi Throughput Dashboard
# ----------------------------------------------------------------------
def render_wifi_throughput(
    tp_rows: List[Dict[str, Any]],
    output_path: str,
    backends: List[str]
) -> None:
    groups = defaultdict(lambda: defaultdict(list))
    for row in tp_rows:
        b = row.get("backend")
        if b in backends and row.get("mtu") == 1500 and "Wi-Fi" in row.get("medium", ""):
            net = row.get("network", "tcp").upper()
            par = row.get("parallel", 1)
            mean_tp = (float(row.get("upload_mbps", 0.0)) + float(row.get("download_mbps", 0.0))) / 2.0
            groups[(net, par)][b].append(mean_tp)

    if not groups:
        return

    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "Wi-Fi Network Throughput (Higher is Better)",
        "MTU 1500 bidirectional mean across three rounds",
        figsize=(16, 9.5),
    )
    axes = fig.subplots(1, 2)
    fig.subplots_adjust(left=0.12, right=0.96, top=0.78, bottom=0.12, wspace=0.20)

    legend_handles = []
    legend_labels = [NAMES[b] for b in backends]

    for axis, network in zip(axes, ("TCP", "UDP")):
        keys = [(network, 1), (network, 8)]
        y = np.arange(len(keys))[::-1]
        height = 0.18
        num_backends = len(backends)

        all_vals = []
        for index, backend in enumerate(backends):
            values = [statistics.mean(groups[key].get(backend, [0.0])) for key in keys]
            all_vals.extend(values)
            positions = y + (1 - index) * height
            bars = axis.barh(
                positions, values, height,
                color=COLORS[backend],
                label=NAMES[backend],
                zorder=3
            )
            if len(legend_handles) < num_backends:
                legend_handles.append(bars)
            for bar, value in zip(bars, values):
                axis.text(
                    bar.get_width() + max(all_vals + [1.0]) * 0.015,
                    bar.get_y() + bar.get_height() / 2,
                    f"{value:.1f} Mbps",
                    va="center", ha="left",
                    fontsize=8.5,
                    color="#1e293b",
                    fontproperties=prop_bold,
                    zorder=5,
                )

        axis.set_yticks(y)
        axis.set_yticklabels(["P=1", "P=8"], fontproperties=prop_medium, fontsize=10)
        axis.set_xlabel("Throughput (Mbps)", fontproperties=prop_regular, fontsize=10, color="#64748b")
        axis.set_title(network, fontproperties=prop_bold, fontsize=12, pad=12, color="#0f172a")
        max_xlim = max(all_vals + [100.0]) * 1.25
        axis.set_xlim(0, max_xlim)
        axis.grid(axis="x", color="#f1f5f9", zorder=0)
        axis.set_axisbelow(True)

    create_top_legend(fig, legend_handles, legend_labels, y_pos=0.895, prop_medium=prop_medium)
    save_dashboard(fig, output_path, dpi=200)


# ----------------------------------------------------------------------
# 2. Bufferbloat & Loaded Latency Delta Dashboard
# ----------------------------------------------------------------------
def render_bufferbloat(
    bloat_rows: List[Dict[str, Any]],
    output_path: str,
    backends: List[str]
) -> None:
    deltas = {}
    throughputs = {}
    for b in backends:
        matched = [r for r in bloat_rows if r.get("backend") == b]
        valid_deltas = [float(r["delta_rtt_ms"]) for r in matched if r.get("delta_rtt_ms") is not None]
        tps = [float(r["throughput_mbps"]) for r in matched if r.get("throughput_mbps") is not None]
        deltas[b] = statistics.mean(valid_deltas) if valid_deltas else None
        throughputs[b] = statistics.mean(tps) if tps else None

    if not any(v is not None for v in deltas.values()):
        return

    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "Bufferbloat and Loaded Latency Delta (Lower is Better)",
        "8-stream TCP download with concurrent TCP echo probe across three rounds",
        figsize=(16, 9.5),
    )
    ax = fig.add_axes([0.18, 0.12, 0.72, 0.66])
    y_pos = np.arange(len(backends))[::-1]
    plotted = [deltas[b] if (deltas[b] is not None and deltas[b] >= 0) else 0.0 for b in backends]
    max_val = max(plotted + [10.0])

    bars = ax.barh(
        y_pos, plotted, height=0.42,
        color=[COLORS[b] for b in backends],
        zorder=3
    )
    ax.set_yticks(y_pos)
    ax.set_yticklabels([NAMES[b] for b in backends], fontproperties=prop_bold, fontsize=10)
    for tick_label, b in zip(ax.get_yticklabels(), backends):
        tick_label.set_color(COLORS[b])

    ax.set_xlabel("Latency Inflation Delta (ms)", fontproperties=prop_regular, fontsize=10, color="#64748b")
    ax.set_xlim(0, max_val * 1.45)
    ax.grid(axis="x", color="#f1f5f9", zorder=0)
    ax.set_axisbelow(True)

    for bar, b in zip(bars, backends):
        val = deltas[b]
        tp = throughputs.get(b)
        tp_str = f"  ({tp:.0f} Mbps)" if tp is not None else ""
        txt = f"+{val:.1f} ms{tp_str}" if val is not None else f"N/A{tp_str}"
        ax.text(
            bar.get_width() + max_val * 0.02,
            bar.get_y() + bar.get_height() / 2,
            txt,
            va="center", ha="left",
            fontsize=8.5,
            fontproperties=prop_bold,
            color="#1e293b",
            bbox=dict(boxstyle="round,pad=0.15", facecolor="#ffffff", edgecolor="none", alpha=0.85),
            zorder=5,
        )

    save_dashboard(fig, output_path, dpi=200)


# ----------------------------------------------------------------------
# 3. Sustained Stability Dashboard (Line Chart)
# ----------------------------------------------------------------------
def render_sustained_stability(
    long_run_rows: List[Dict[str, Any]],
    output_path: str,
    backends: List[str]
) -> None:
    if not long_run_rows:
        return

    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "60-Second Sustained Throughput Stability (Higher is Better)",
        "60 seconds continuous 8-stream TCP download under 5GHz Wi-Fi",
        figsize=(16, 9.5),
    )
    ax = fig.add_axes([0.08, 0.12, 0.88, 0.67])

    legend_lines = []
    legend_labels = []
    time_axis = np.arange(1, 61)

    all_max = 500.0
    for b in backends:
        match = next((r for r in long_run_rows if r.get("backend") == b), None)
        if not match:
            continue
        series = match.get("time_series", [])[:60]
        if len(series) < 60:
            series = series + [series[-1] if series else 0.0] * (60 - len(series))
        if series:
            all_max = max(all_max, max(series))

        line, = ax.plot(
            time_axis, series,
            label=NAMES[b],
            color=COLORS[b],
            linewidth=2.2,
            zorder=3
        )
        legend_lines.append(line)
        legend_labels.append(NAMES[b])

    top_y = max(all_max * 1.18, 500.0)

    sorted_matches = []
    for b in backends:
        m = next((r for r in long_run_rows if r.get("backend") == b), None)
        if m:
            sorted_matches.append(m)
    sorted_matches.sort(key=lambda x: x.get("avg_throughput_mbps", 0.0), reverse=True)

    label_y_positions = np.linspace(top_y * 0.85, top_y * 0.45, max(len(sorted_matches), 1))

    for idx, match in enumerate(sorted_matches):
        b = match.get("backend")
        series = match.get("time_series", [])
        final_y = series[-1] if series else 0.0
        avg = float(match.get("avg_throughput_mbps", 0.0))
        cv = float(match.get("cv_percent", 0.0))
        decay = float(match.get("decay_percent", 0.0))
        lbl_y = label_y_positions[idx]

        tag = f"{NAMES[b]}: {avg:.0f} Mbps (CV: {cv:.1f}%, Decay: {decay:+.1f}%)"

        ax.plot([60.2, 61.2], [final_y, lbl_y], color=COLORS[b], linestyle=":", linewidth=1.2, alpha=0.85)
        ax.text(
            61.4, lbl_y,
            tag,
            fontsize=8.5,
            color=COLORS[b],
            va="center",
            fontproperties=prop_bold,
            bbox=dict(boxstyle="round,pad=0.20", facecolor="#ffffff", edgecolor=COLORS[b], alpha=0.92, linewidth=0.8)
        )

    ax.set_xlim(0, 78)
    ax.set_ylim(0, top_y)
    ax.set_xlabel("Elapsed Time (Seconds)", fontsize=10, color="#64748b", fontproperties=prop_regular)
    ax.set_ylabel("Throughput (Mbps)", fontsize=10, color="#64748b", fontproperties=prop_regular)
    ax.grid(True, color="#f1f5f9", zorder=0)

    create_top_legend(fig, legend_lines, legend_labels, y_pos=0.895, prop_medium=prop_medium)
    save_dashboard(fig, output_path, dpi=200)


# ----------------------------------------------------------------------
# 4. Idle Memory Retention Dashboard (Line Chart)
# ----------------------------------------------------------------------
def render_idle_memory(
    idle_rows: List[Dict[str, Any]],
    output_path: str,
    backends: List[str]
) -> None:
    if not idle_rows:
        return

    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "Idle Connection Memory Footprint (Lower is Better)",
        "Process memory PSS by retained TCP connections 0 to 1000 across three rounds",
        figsize=(16, 9.5),
    )
    ax = fig.add_axes([0.10, 0.12, 0.86, 0.67])
    handles = []
    labels = []

    for backend in backends:
        by_connections = defaultdict(list)
        for row in idle_rows:
            if row.get("backend") != backend or row.get("network") != "tcp":
                continue
            for sample in row.get("idle_flows", []):
                by_connections[int(sample["connections"])].append(float(sample["pss_mb"]))
        if not by_connections:
            continue
        xs = sorted(by_connections)
        ys = [statistics.mean(by_connections[x]) for x in xs]
        line, = ax.plot(
            xs, ys,
            marker="o", linewidth=2.2, markersize=5,
            color=COLORS[backend], label=NAMES[backend], zorder=3
        )
        handles.append(line)
        labels.append(NAMES[backend])
        for x, y in zip(xs, ys):
            ax.annotate(
                f"{y:.1f}", (x, y),
                xytext=(0, 7), textcoords="offset points",
                ha="center", fontsize=8.0,
                color=COLORS[backend],
                fontproperties=prop_bold
            )

    ax.set_xlabel("Retained TCP Connections", fontsize=10, color="#64748b", fontproperties=prop_regular)
    ax.set_ylabel("Process Memory PSS (MB)", fontsize=10, color="#64748b", fontproperties=prop_regular)
    ax.grid(True, color="#f1f5f9", zorder=0)
    ax.set_axisbelow(True)

    create_top_legend(fig, handles, labels, y_pos=0.895, prop_medium=prop_medium)
    save_dashboard(fig, output_path, dpi=200)


# ----------------------------------------------------------------------
# 5. CPS Performance Dashboard (Rate & Latency 1x2 Dual Panels)
# ----------------------------------------------------------------------
def render_cps(
    cps_rows: List[Dict[str, Any]],
    output_path: str,
    backends: List[str]
) -> None:
    """
    Renders human-centric 1x2 dashboard:
    - Omit Hev from main plot rows and provide clear bottom footnote.
    - Hierarchical Y-axis with explicit Backend headers and 8W / 4W rows.
    - Zero legend overhead: self-describing rows and colors.
    - Left: Connection Rate (integer conn/s).
    - Right: Handshake Latency (P50 bar + P95 whisker with 'P50 -> P95 ms' label).
    """
    if not cps_rows:
        return

    # Filter out backends with no valid CPS data (e.g. Hev on 5000 conns)
    active_backends = [
        b for b in backends
        if any(r.get("backend") == b and r.get("rc") == 0 and r.get("cps") is not None for r in cps_rows)
    ]
    if not active_backends:
        return

    # Order from top to bottom: 4W first, then 8W
    # In matplotlib Y-axis: higher Y is top, lower Y is bottom.
    # So 4W gets the higher Y, 8W gets the lower Y.
    workers_display_order = [4, 8]  # from top to bottom

    row_configs = []
    current_y = 0.0
    backend_centers = {}

    for b in reversed(active_backends):
        b_y_list = []
        for w in reversed(workers_display_order):
            row_configs.append({
                "backend": b,
                "worker": w,
                "y": current_y,
            })
            b_y_list.append(current_y)
            current_y += 0.8
        backend_centers[b] = statistics.mean(b_y_list)
        current_y += 0.6  # Separation gap between backends

    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "Short-Lived TCP Performance (5,000 Connections)",
        "Connection establishment rate and handshake latency distribution across 4W and 8W",
        figsize=(16, 9.5),
    )
    axes = fig.subplots(1, 2)
    fig.subplots_adjust(left=0.18, right=0.95, top=0.84, bottom=0.14, wspace=0.18)

    bar_h = 0.52

    # ------------------ Left: Connection Rate ------------------
    ax_rate = axes[0]
    rate_map = {}
    for r in row_configs:
        b = r["backend"]
        w = r["worker"]
        matched = [
            float(row["cps"])
            for row in cps_rows
            if row.get("backend") == b
            and row.get("workers") == w
            and row.get("rc") == 0
            and row.get("cps") is not None
        ]
        rate_map[(b, w)] = statistics.mean(matched) if matched else 0.0

    max_rate = max(list(rate_map.values()) + [100.0]) * 1.32
    ax_rate.set_xlim(0, max_rate)

    for r in row_configs:
        b = r["backend"]
        w = r["worker"]
        val = rate_map[(b, w)]
        y_pos = r["y"]
        color = COLORS[b]
        alpha = 0.95 if w == 8 else 0.65

        ax_rate.barh(
            y_pos, val, height=bar_h,
            color=color, alpha=alpha,
            edgecolor=color, linewidth=1.0,
            zorder=3
        )
        if val > 0:
            ax_rate.text(
                val + max_rate * 0.02, y_pos,
                f"{round(val)} conn/s",
                va="center", ha="left",
                fontsize=8.5,
                color="#1e293b",
                fontproperties=prop_bold,
                bbox=dict(boxstyle="round,pad=0.12", facecolor="#ffffff", edgecolor="none", alpha=0.85),
                zorder=5,
            )

    ax_rate.set_title("Connection Rate (Higher is Better)", fontproperties=prop_bold, fontsize=12, pad=12, color="#0f172a")
    ax_rate.set_xlabel("Connections / Second", fontproperties=prop_regular, fontsize=10, color="#64748b")
    ax_rate.grid(axis="x", color="#f1f5f9", zorder=0)
    ax_rate.set_axisbelow(True)

    # ------------------ Right: Handshake Latency (P50 -> P95) ------------------
    ax_lat = axes[1]
    p50_map = {}
    p95_map = {}
    for r in row_configs:
        b = r["backend"]
        w = r["worker"]
        matched = [
            row for row in cps_rows
            if row.get("backend") == b
            and row.get("workers") == w
            and row.get("rc") == 0
            and row.get("p50_ms") is not None
        ]
        p50_map[(b, w)] = statistics.mean([float(x["p50_ms"]) for x in matched]) if matched else None
        p95_map[(b, w)] = statistics.mean([float(x["p95_ms"]) for x in matched]) if matched else None

    all_p95 = [v for v in p95_map.values() if v is not None]
    max_lat = max(all_p95 + [25.0]) * 1.35
    ax_lat.set_xlim(0, max_lat)

    for r in row_configs:
        b = r["backend"]
        w = r["worker"]
        p50 = p50_map.get((b, w))
        p95 = p95_map.get((b, w))
        y_pos = r["y"]
        color = COLORS[b]
        alpha = 0.95 if w == 8 else 0.65

        if p50 is not None:
            # Main bar reaches P50
            ax_lat.barh(
                y_pos, p50, height=bar_h,
                color=color, alpha=alpha,
                edgecolor=color, linewidth=1.0,
                zorder=3
            )
            if p95 is not None:
                # Whisker line to P95
                ax_lat.plot([p50, p95], [y_pos, y_pos], color=color, linewidth=2.0, alpha=0.9, zorder=4)
                # End cap on P95
                ax_lat.plot([p95, p95], [y_pos - bar_h * 0.35, y_pos + bar_h * 0.35], color=color, linewidth=2.2, alpha=0.9, zorder=4)

                txt = f"{p50:.1f} → {p95:.1f} ms"
                ax_lat.text(
                    p95 + max_lat * 0.02, y_pos,
                    txt,
                    va="center", ha="left",
                    fontsize=8.5,
                    color="#1e293b",
                    fontproperties=prop_bold,
                    bbox=dict(boxstyle="round,pad=0.12", facecolor="#ffffff", edgecolor="none", alpha=0.85),
                    zorder=5,
                )

    ax_lat.set_title("Handshake Latency (Lower is Better)", fontproperties=prop_bold, fontsize=12, pad=12, color="#0f172a")
    ax_lat.set_xlabel("Latency (P50 Median → P95 Tail, ms)", fontproperties=prop_regular, fontsize=10, color="#64748b")
    ax_lat.grid(axis="x", color="#f1f5f9", zorder=0)
    ax_lat.set_axisbelow(True)

    # ------------------ Y-Axis Formatting ------------------
    # Align vertical limits on both panels
    for ax in [ax_rate, ax_lat]:
        ax.set_ylim(-0.6, current_y - 0.2)

    # Left subplot: show 4W / 8W tick labels and centered Backend group labels
    ax_rate.set_yticks([r["y"] for r in row_configs])
    ax_rate.set_yticklabels([f"{r['worker']}W" for r in row_configs], fontproperties=prop_bold, fontsize=9.5, color="#475569")

    for b, center_y in backend_centers.items():
        ax_rate.text(
            -max_rate * 0.09,
            center_y,
            NAMES[b],
            va="center", ha="right",
            fontsize=11.5,
            color=COLORS[b],
            fontproperties=prop_bold,
        )

    # Right subplot: clean, omit redundant Y-axis ticks and labels completely
    ax_lat.set_yticks([r["y"] for r in row_configs])
    ax_lat.set_yticklabels([])
    ax_lat.tick_params(left=False)

    # Footnote at bottom explaining Hev omission
    fig.text(
        0.50, 0.045,
        "Note: Hev omitted from 5,000-connection test (exceeds lwIP PCB limits; historical: ~16 CPS @ 4W, ~34 CPS @ 8W).",
        ha="center", va="center",
        fontsize=9.0,
        color="#64748b",
        fontproperties=prop_regular,
    )

    save_dashboard(fig, output_path, dpi=200)


# ----------------------------------------------------------------------
# 5c. CPU Efficiency / Compute Cost Dashboard (CPU% per 100 Mbps)
# ----------------------------------------------------------------------
def render_cpu_efficiency(
    tp_rows: List[Dict[str, Any]],
    output_path: str,
    backends: List[str]
) -> None:
    """
    Renders CPU Compute Cost per 100 Mbps throughput (Lower is Better).
    Left Subplot: Single Stream (P=1) TCP & UDP Upload / Download.
    Right Subplot: Multi-Stream (P=8) TCP & UDP Upload / Download.
    """
    if not tp_rows:
        return

    # Compute average CPU cost per 100 Mbps for (direction, network, parallel, backend)
    # cost = (cpu_avg / (mbps / 100))
    scenarios = [
        ("P=1 (Single Stream)", 1),
        ("P=8 (Multi-Stream)", 8),
    ]

    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "CPU Cost per 100 Mbps Throughput (Lower is Better)",
        "Normalized processor compute cost across 5GHz Wi-Fi transmission scenarios",
        figsize=(16, 9.5),
    )
    axes = fig.subplots(1, 2)
    fig.subplots_adjust(left=0.14, right=0.96, top=0.78, bottom=0.12, wspace=0.22)

    y = np.arange(len(backends))[::-1]
    bar_h = 0.20

    for ax_idx, (axis, (scen_title, parallel_val)) in enumerate(zip(axes, scenarios)):
        # We evaluate: TCP Download & TCP Upload
        tcp_down_costs = {}
        tcp_up_costs = {}
        for b in backends:
            matched = [
                r for r in tp_rows
                if r.get("backend") == b
                and r.get("parallel") == parallel_val
                and r.get("network") == "tcp"
                and r.get("mtu") == 1500
            ]
            if matched:
                d_costs = [
                    (float(r["download_cpu_avg"]) / (float(r["download_mbps"]) / 100.0))
                    for r in matched
                    if float(r.get("download_mbps", 0.0)) > 1.0 and r.get("download_cpu_avg") is not None
                ]
                u_costs = [
                    (float(r["upload_cpu_avg"]) / (float(r["upload_mbps"]) / 100.0))
                    for r in matched
                    if float(r.get("upload_mbps", 0.0)) > 1.0 and r.get("upload_cpu_avg") is not None
                ]
                tcp_down_costs[b] = statistics.mean(d_costs) if d_costs else None
                tcp_up_costs[b] = statistics.mean(u_costs) if u_costs else None
            else:
                tcp_down_costs[b] = None
                tcp_up_costs[b] = None

        all_vals = [v for v in list(tcp_down_costs.values()) + list(tcp_up_costs.values()) if v is not None]
        max_x = max(all_vals + [30.0]) * 1.35
        axis.set_xlim(0, max_x)

        current_flow_configs = [
            ("TCP Upload", tcp_up_costs, bar_h / 2 + 0.02, 0.60),
            ("TCP Download", tcp_down_costs, -(bar_h / 2 + 0.02), 0.95),
        ]

        for flow_label, flow_map, offset, alpha in current_flow_configs:
            vals = [flow_map[b] if flow_map[b] is not None else 0.0 for b in backends]
            bars = axis.barh(
                y + offset, vals, height=bar_h,
                color=[COLORS[b] for b in backends],
                alpha=alpha,
                edgecolor=[COLORS[b] for b in backends],
                linewidth=1.0,
                zorder=3
            )
            for bar, b in zip(bars, backends):
                val = flow_map[b]
                if val is not None:
                    txt = f"{val:.1f}% ({flow_label.split()[-1]})"
                    axis.text(
                        bar.get_width() + max_x * 0.02,
                        bar.get_y() + bar.get_height() / 2,
                        txt,
                        va="center", ha="left",
                        fontsize=7.8,
                        color="#1e293b",
                        fontproperties=prop_bold,
                        bbox=dict(boxstyle="round,pad=0.12", facecolor="#ffffff", edgecolor="none", alpha=0.85),
                        zorder=5,
                    )

        axis.set_yticks(y)
        if ax_idx == 0:
            axis.set_yticklabels([NAMES[b] for b in backends], fontproperties=prop_bold, fontsize=10)
            for tick_label, b in zip(axis.get_yticklabels(), backends):
                tick_label.set_color(COLORS[b])
        else:
            axis.set_yticklabels([])
            axis.tick_params(left=False)

        axis.set_xlabel("CPU Load (%) per 100 Mbps", fontproperties=prop_regular, fontsize=10, color="#64748b")
        axis.set_title(scen_title, fontproperties=prop_bold, fontsize=12, pad=12, color="#0f172a")
        axis.grid(axis="x", color="#f1f5f9", zorder=0)
        axis.set_axisbelow(True)

    save_dashboard(fig, output_path, dpi=200)


# ----------------------------------------------------------------------
# 6. Weak-Network Throughput Dashboard
# ----------------------------------------------------------------------
def render_weaknet(
    weaknet_data: Dict[str, Any],
    output_path: str,
    backends: List[str]
) -> None:
    results = weaknet_data.get("results", [])
    if not results:
        return

    losses = sorted({float(r.get("loss_percent", 0.0)) for r in results})
    if not losses:
        return

    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "Weak-Network TCP Throughput (Higher is Better)",
        "Host netem delay 50 ms across loss rates (5GHz Wi-Fi P=8)",
        figsize=(16, 9.5),
    )
    axes = fig.subplots(1, 2)
    fig.subplots_adjust(left=0.08, right=0.96, top=0.78, bottom=0.12, wspace=0.18)

    num_losses = len(losses)
    width = 0.8 / max(num_losses, 1)

    cmap = plt.cm.Oranges
    loss_colors = {}
    loss_edges = {}
    for idx, loss in enumerate(losses):
        factor = 0.35 + 0.55 * (idx / max(num_losses - 1, 1))
        loss_colors[loss] = cmap(factor)
        loss_edges[loss] = cmap(min(factor + 0.15, 1.0))

    directions = [("upload", "TCP Upload (Android → Host)", "upload_mbps"),
                  ("download", "TCP Download (Host → Android)", "download_mbps")]

    x = np.arange(len(backends))

    for axis, (dir_key, dir_title, metric_key) in zip(axes, directions):
        all_vals = []
        for idx, loss in enumerate(losses):
            vals = []
            for b in backends:
                rec = next((
                    r for r in results
                    if r.get("backend") == b
                    and float(r.get("loss_percent", 0.0)) == loss
                    and (r.get("direction") == dir_key or (dir_key == "download" and "direction" not in r))
                ), None)
                v = float(rec.get(metric_key, rec.get("throughput_mbps", 0.0))) if rec else 0.0
                vals.append(v)
            all_vals.extend(vals)

            offset = (idx - (num_losses - 1) / 2.0) * width
            bars = axis.bar(
                x + offset, vals, width,
                color=loss_colors[loss],
                edgecolor=loss_edges[loss],
                linewidth=0.8,
                zorder=3
            )
            for bar, val in zip(bars, vals):
                if val > 0:
                    axis.text(
                        bar.get_x() + bar.get_width() / 2,
                        bar.get_height() + max(all_vals + [1.0]) * 0.02,
                        f"{val:.1f}",
                        ha="center", va="bottom",
                        fontsize=7.5,
                        color="#1e293b",
                        fontproperties=prop_bold,
                        zorder=5,
                    )

        axis.set_title(dir_title, fontproperties=prop_bold, fontsize=12, pad=12, color="#0f172a")
        axis.set_ylabel("Throughput (Mbps)", fontsize=10, color="#64748b", fontproperties=prop_regular)
        axis.set_xticks(x)
        axis.set_xticklabels([NAMES.get(b, b) for b in backends], fontsize=10,
                             color="#334155", fontproperties=prop_medium)
        max_y = max(all_vals + [10.0]) * 1.25
        axis.set_ylim(0, max_y)
        axis.grid(True, axis="y", color="#f1f5f9", linewidth=0.8, zorder=0)
        axis.set_axisbelow(True)

    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor=loss_colors[loss], edgecolor=loss_edges[loss])
        for loss in losses
    ]
    create_top_legend(
        fig,
        legend_handles,
        [f"{loss:g}% Loss" for loss in losses],
        y_pos=0.895,
        prop_medium=prop_medium,
    )
    save_dashboard(fig, output_path, dpi=200)


# ----------------------------------------------------------------------
# 7. Memory Attribution: Fixed Base vs Incremental Connection Slope
# ----------------------------------------------------------------------
def render_memory_attribution(
    mb_data: Dict[str, Any],
    output_path: str,
    backends: List[str]
) -> None:
    rounds = mb_data.get("rounds", {})
    if not rounds:
        return

    base_pss_tcp = {b: [] for b in backends}
    slope_tcp = {b: [] for b in backends}
    base_pss_udp = {b: [] for b in backends}
    slope_udp = {b: [] for b in backends}

    for r_cases in rounds.values():
        for case in r_cases:
            b = case.get("backend")
            if b not in backends:
                continue
            net = case.get("network")
            flows = case.get("flows", [])
            base = flows[0]["pss_mb"] if flows else 0.0
            slope = float(case.get("slope_kib_per_conn", 0.0))
            if net == "tcp":
                base_pss_tcp[b].append(base)
                slope_tcp[b].append(slope)
            elif net == "udp":
                base_pss_udp[b].append(base)
                slope_udp[b].append(slope)

    avg_base_tcp = {b: statistics.mean(base_pss_tcp[b]) if base_pss_tcp[b] else 0.0 for b in backends}
    avg_slope_tcp = {b: statistics.mean(slope_tcp[b]) if slope_tcp[b] else 0.0 for b in backends}
    avg_base_udp = {b: statistics.mean(base_pss_udp[b]) if base_pss_udp[b] else 0.0 for b in backends}
    avg_slope_udp = {b: statistics.mean(slope_udp[b]) if slope_udp[b] else 0.0 for b in backends}

    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "Memory Footprint: Fixed Base PSS vs Incremental Connection Slope (Lower is Better)",
        "Standalone user namespace microbenchmark (Scheme 2) three rounds mean",
        figsize=(16, 9.5),
    )
    axes = fig.subplots(1, 2)
    fig.subplots_adjust(left=0.14, right=0.96, top=0.78, bottom=0.12, wspace=0.22)

    y = np.arange(len(backends))[::-1]

    # Subplot 1: Fixed Base PSS at 0 connections
    ax_base = axes[0]
    base_vals = [(avg_base_tcp[b] + avg_base_udp[b]) / 2.0 for b in backends]
    bars_base = ax_base.barh(y, base_vals, height=0.42, color=[COLORS[b] for b in backends], zorder=3)
    max_base = max(base_vals + [10.0])
    ax_base.set_xlim(0, max_base * 1.30)
    for bar, val in zip(bars_base, base_vals):
        ax_base.text(
            bar.get_width() + max_base * 0.02,
            bar.get_y() + bar.get_height() / 2,
            f"{val:.1f} MB",
            va="center", ha="left",
            fontsize=8.5,
            color="#1e293b",
            fontproperties=prop_bold,
            bbox=dict(boxstyle="round,pad=0.15", facecolor="#ffffff", edgecolor="none", alpha=0.85),
            zorder=5,
        )
    ax_base.set_yticks(y)
    ax_base.set_yticklabels([NAMES[b] for b in backends], fontproperties=prop_bold, fontsize=10)
    for tick_label, b in zip(ax_base.get_yticklabels(), backends):
        tick_label.set_color(COLORS[b])

    ax_base.set_xlabel("Base Process Memory PSS (MB)", fontproperties=prop_regular, fontsize=10, color="#64748b")
    ax_base.set_title("Fixed Baseline Process Footprint (0 Conns)", fontproperties=prop_bold, fontsize=12, pad=12, color="#0f172a")
    ax_base.grid(axis="x", color="#f1f5f9", zorder=0)
    ax_base.set_axisbelow(True)

    # Subplot 2: Incremental Connection Slope
    ax_slope = axes[1]
    bar_h = 0.22
    for idx_net, (net_label, slope_map, offset) in enumerate([
        ("TCP", avg_slope_tcp, bar_h / 2 + 0.02),
        ("UDP", avg_slope_udp, -(bar_h / 2 + 0.02)),
    ]):
        vals = [slope_map[b] for b in backends]
        max_slope = max(list(avg_slope_tcp.values()) + list(avg_slope_udp.values()) + [5.0])
        ax_slope.set_xlim(0, max_slope * 1.35)

        bars = ax_slope.barh(
            y + offset, vals, height=bar_h,
            color=[COLORS[b] for b in backends],
            alpha=0.95 if net_label == "TCP" else 0.55,
            edgecolor=[COLORS[b] for b in backends],
            linewidth=1.0,
            zorder=3
        )
        for bar, val in zip(bars, vals):
            ax_slope.text(
                bar.get_width() + max_slope * 0.02,
                bar.get_y() + bar.get_height() / 2,
                f"{val:.2f} KiB ({net_label})",
                va="center", ha="left",
                fontsize=7.8,
                color="#1e293b",
                fontproperties=prop_bold,
                bbox=dict(boxstyle="round,pad=0.12", facecolor="#ffffff", edgecolor="none", alpha=0.85),
                zorder=5,
            )

    ax_slope.set_yticks(y)
    ax_slope.set_yticklabels([])
    ax_slope.tick_params(left=False)
    ax_slope.set_xlabel("Incremental Footprint (KiB / connection)", fontproperties=prop_regular, fontsize=10, color="#64748b")
    ax_slope.set_title("Incremental Retention Slope (0 to 1000 Conns)", fontproperties=prop_bold, fontsize=12, pad=12, color="#0f172a")
    ax_slope.grid(axis="x", color="#f1f5f9", zorder=0)
    ax_slope.set_axisbelow(True)

    save_dashboard(fig, output_path, dpi=200)


def main() -> None:
    parser = argparse.ArgumentParser(description="SimpleXray Benchmark Dashboard Generator")
    parser.add_argument("--device-profile", default=DEFAULT_DEVICE, choices=sorted(DEVICE_PROFILES),
                        help=f"Device profile to load data from and save charts to (default: {DEFAULT_DEVICE})")
    parser.add_argument("--preset", choices=["light", "full"], default="light",
                        help="Backend selection preset: 'light' (Hev/SingTUN/Zeptun) or 'full' (all)")
    args = parser.parse_args()

    profile = resolve_device_paths(args.device_profile)
    data_dir = profile["data_dir"]
    charts_dir = profile["charts_dir"]
    os.makedirs(charts_dir, exist_ok=True)

    backends = LIGHT_BACKENDS if args.preset == "light" else FULL_BACKENDS
    print(f"[Generator] Device Profile: {profile['name']} | Preset: {args.preset.upper()} | Backends: {backends}")

    # 1. Throughput
    tp_payload = load_json(profile["throughput_json"])
    if tp_payload:
        render_wifi_throughput(flat_rounds(tp_payload), os.path.join(charts_dir, "wifi_throughput.webp"), backends)

    # 2. Bufferbloat
    bloat_payload = load_json(profile["bufferbloat_json"])
    if bloat_payload:
        render_bufferbloat(flat_rounds(bloat_payload), os.path.join(charts_dir, "bufferbloat.webp"), backends)

    # 3. Sustained Stability
    stab_payload = load_json(profile["stability_json"])
    if stab_payload:
        render_sustained_stability(stab_payload.get("long_run", []), os.path.join(charts_dir, "sustained_stability.webp"), backends)

    # 4. Idle Memory
    idle_payload = load_json(profile["idle_memory_json"])
    if idle_payload:
        render_idle_memory(flat_rounds(idle_payload), os.path.join(charts_dir, "idle_memory.webp"), backends)

    # 5. CPS
    cps_payload = load_json(profile["cps_json"])
    if cps_payload:
        render_cps(flat_rounds(cps_payload), os.path.join(charts_dir, "cps.webp"), backends)

    # 6. Weaknet
    weaknet_payload = load_json(profile["weaknet_json"])
    if weaknet_payload:
        render_weaknet(weaknet_payload, os.path.join(charts_dir, "weaknet_throughput.webp"), backends)

    # 7. Memory Attribution (Scheme 2 Microbenchmark)
    mb_payload = load_json(profile["microbench_json"])
    if mb_payload:
        render_memory_attribution(mb_payload, os.path.join(charts_dir, "memory_attribution.webp"), backends)

    # 8. CPU Efficiency
    if tp_payload:
        render_cpu_efficiency(flat_rounds(tp_payload), os.path.join(charts_dir, "cpu_efficiency.webp"), backends)

    print(f"[Generator] All charts successfully generated in: {charts_dir}")


if __name__ == "__main__":
    main()
