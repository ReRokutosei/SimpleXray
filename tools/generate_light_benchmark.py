#!/usr/bin/env python3
"""
Render standard-compliant benchmark dashboards from light-run JSON files.

Adheres strictly to the SimpleXray unified design system:
- Palette: Hev (#555555), SingTUN (#00ADD8), Zeptun (#F7A41D)
- Backend order: Hev, SingTUN, Zeptun (alphabetical order)
- Font: JetBrains Mono via FontProperties
- Layout: Centered title, subtitle, and top legend
- Subtitle: Clean & concise, no "Light Benchmark" mention
- Higher/Lower is Better indicators on all titles
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

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, TOOLS_DIR)
from common.theme import (
    setup_fonts,
    apply_global_theme,
    add_dashboard_header,
    create_top_legend,
    save_dashboard,
)

BACKENDS = ["hev", "sing", "zeptun"]
NAMES = {
    "hev": "Hev",
    "sing": "SingTUN",
    "zeptun": "Zeptun",
}
COLORS = {
    "hev": "#555555",
    "sing": "#00ADD8",
    "zeptun": "#F7A41D",
}


def load(path: str) -> Any:
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def flat_rounds(payload: Dict[str, Any]) -> List[Dict[str, Any]]:
    return [item for rows in payload.get("rounds", {}).values() for item in rows]


def average(rows: List[Dict[str, Any]], key: str) -> Optional[float]:
    values = [float(row[key]) for row in rows if row.get(key) is not None]
    return statistics.mean(values) if values else None


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


def render_throughput(groups: Dict[Tuple[str, int], Dict[str, List[float]]], path: str) -> None:
    """Renders horizontal grouped bars for TCP and UDP Wi-Fi throughput."""
    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "Wi-Fi Throughput (Higher is Better)",
        "MTU 1500 bidirectional mean three rounds",
        figsize=(16, 9.5),
    )
    axes = fig.subplots(1, 2)
    fig.subplots_adjust(left=0.12, right=0.96, top=0.78, bottom=0.12, wspace=0.20)

    legend_handles = []
    legend_labels = [NAMES[b] for b in BACKENDS]

    for axis, network in zip(axes, ("TCP", "UDP")):
        keys = [(network, 1), (network, 8)]
        y = np.arange(len(keys))[::-1]
        height = 0.18
        num_backends = len(BACKENDS)

        all_vals = []
        for index, backend in enumerate(BACKENDS):
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
    save_dashboard(fig, path, dpi=200)


def render_bufferbloat(
    data: Dict[str, Optional[float]],
    tp_data: Dict[str, Optional[float]],
    path: str
) -> None:
    """Renders horizontal bars for loaded latency delta."""
    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "Loaded Latency Delta (Lower is Better)",
        "8-stream TCP download with concurrent TCP echo probe three rounds",
        figsize=(16, 9.5),
    )
    ax = fig.add_axes([0.18, 0.12, 0.72, 0.66])
    y_pos = np.arange(len(BACKENDS))[::-1]
    values = [data.get(b) for b in BACKENDS]
    plotted = [v if (v is not None and v >= 0) else 0.0 for v in values]
    max_val = max(plotted + [10.0])

    bars = ax.barh(
        y_pos, plotted, height=0.42,
        color=[COLORS[b] for b in BACKENDS],
        zorder=3
    )
    ax.set_yticks(y_pos)
    ax.set_yticklabels([NAMES[b] for b in BACKENDS], fontproperties=prop_medium, fontsize=10)
    ax.set_xlabel("Latency Inflation Delta (ms)", fontproperties=prop_regular, fontsize=10, color="#64748b")
    ax.set_xlim(0, max_val * 1.45)
    ax.grid(axis="x", color="#f1f5f9", zorder=0)
    ax.set_axisbelow(True)

    for bar, b, val in zip(bars, BACKENDS, values):
        tp = tp_data.get(b)
        tp_str = f"  ({tp:.0f} Mbps)" if tp is not None else ""
        if val is None:
            txt = f"N/A{tp_str}"
        else:
            txt = f"+{val:.1f} ms{tp_str}"
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

    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor=COLORS[b])
        for b in BACKENDS
    ]
    create_top_legend(fig, legend_handles, [NAMES[b] for b in BACKENDS], y_pos=0.895, prop_medium=prop_medium)
    save_dashboard(fig, path, dpi=200)


def render_longrun_chart(long_run_rows: List[Dict[str, Any]], path: str) -> None:
    """Renders 60s sustained TCP download throughput time series as a line chart."""
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
    for b in BACKENDS:
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

    # Sort matching rows by avg throughput descending for side label placement
    sorted_matches = []
    for b in BACKENDS:
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
    save_dashboard(fig, path, dpi=200)


def render_idle(idle_rows: List[Dict[str, Any]], path: str) -> None:
    """Renders PSS memory by retained TCP connections (0 -> 1000) as a line chart."""
    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "Idle Connection Memory Footprint (Lower is Better)",
        "PSS by retained TCP connections 0 → 1000 three rounds",
        figsize=(16, 9.5),
    )
    ax = fig.add_axes([0.10, 0.12, 0.86, 0.67])
    handles = []
    labels = []

    for backend in BACKENDS:
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
    save_dashboard(fig, path, dpi=200)


def render_cps(cps_rows: List[Dict[str, Any]], path: str) -> None:
    """Renders short-lived TCP connection rate (CPS) for worker counts 4 and 8."""
    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "Short-Lived TCP Connection Rate (CPS) (Higher is Better)",
        "5000 connections worker counts 4 and 8 three rounds",
        figsize=(16, 9.5),
    )
    axes = fig.subplots(1, 2)
    fig.subplots_adjust(left=0.14, right=0.96, top=0.78, bottom=0.12, wspace=0.22)

    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor=COLORS[b])
        for b in BACKENDS
    ]
    legend_labels = [NAMES[b] for b in BACKENDS]

    for axis, worker_count in zip(axes, (4, 8)):
        values = {}
        for backend in BACKENDS:
            valid = [
                row.get("cps")
                for row in cps_rows
                if row.get("backend") == backend
                and row.get("workers") == worker_count
                and row.get("rc") == 0
                and row.get("cps") is not None
            ]
            values[backend] = statistics.mean(valid) if valid else None

        y = np.arange(len(BACKENDS))[::-1]
        plotted = [values[b] if values[b] is not None else 0.0 for b in BACKENDS]
        bars = axis.barh(y, plotted, height=0.42, color=[COLORS[b] for b in BACKENDS], zorder=3)
        upper = max(plotted + [100.0])
        axis.set_xlim(0, upper * 1.30)

        for bar, backend in zip(bars, BACKENDS):
            val = values[backend]
            text = "N/A" if val is None else f"{val:.1f} conn/s"
            axis.text(
                (val or 0.0) + upper * 0.02,
                bar.get_y() + bar.get_height() / 2,
                text,
                va="center", ha="left",
                fontsize=8.5,
                color="#1e293b",
                fontproperties=prop_bold,
                bbox=dict(boxstyle="round,pad=0.15", facecolor="#ffffff", edgecolor="none", alpha=0.85),
                zorder=5,
            )

        axis.set_yticks(y)
        axis.set_yticklabels([NAMES[b] for b in BACKENDS], fontproperties=prop_medium, fontsize=10)
        axis.set_xlabel("Connections / Second", fontproperties=prop_regular, fontsize=10, color="#64748b")
        axis.set_title(f"{worker_count} Workers", fontproperties=prop_bold, fontsize=12, pad=12, color="#0f172a")
        axis.grid(axis="x", color="#f1f5f9", zorder=0)
        axis.set_axisbelow(True)

    create_top_legend(fig, legend_handles, legend_labels, y_pos=0.895, prop_medium=prop_medium)
    save_dashboard(fig, path, dpi=200)


def render_memory_attribution(microbench_path: str, path: str) -> None:
    """
    Renders the Fixed Baseline PSS vs Incremental Connection Slope Breakdown Dashboard.
    Left Subplot: Fixed Base Process Memory PSS (MB) at 0 connections.
    Right Subplot: Incremental Connection Slope (KiB / connection).
    """
    if not os.path.exists(microbench_path):
        return

    mb_data = load(microbench_path)
    rounds = mb_data.get("rounds", {})

    base_pss_tcp = {b: [] for b in BACKENDS}
    slope_tcp = {b: [] for b in BACKENDS}
    base_pss_udp = {b: [] for b in BACKENDS}
    slope_udp = {b: [] for b in BACKENDS}

    for r_cases in rounds.values():
        for case in r_cases:
            b = case.get("backend")
            if b not in BACKENDS:
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

    avg_base_tcp = {b: statistics.mean(base_pss_tcp[b]) if base_pss_tcp[b] else 0.0 for b in BACKENDS}
    avg_slope_tcp = {b: statistics.mean(slope_tcp[b]) if slope_tcp[b] else 0.0 for b in BACKENDS}
    avg_base_udp = {b: statistics.mean(base_pss_udp[b]) if base_pss_udp[b] else 0.0 for b in BACKENDS}
    avg_slope_udp = {b: statistics.mean(slope_udp[b]) if slope_udp[b] else 0.0 for b in BACKENDS}

    fig, prop_regular, prop_bold, prop_medium = setup_dashboard(
        "Memory Footprint: Fixed Base PSS vs Incremental Connection Slope (Lower is Better)",
        "Standalone user namespace microbenchmark (Scheme 2) three rounds mean",
        figsize=(16, 9.5),
    )
    axes = fig.subplots(1, 2)
    fig.subplots_adjust(left=0.14, right=0.96, top=0.78, bottom=0.12, wspace=0.22)

    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor=COLORS[b])
        for b in BACKENDS
    ]
    legend_labels = [NAMES[b] for b in BACKENDS]

    y = np.arange(len(BACKENDS))[::-1]

    # Subplot 1: Fixed Base PSS at 0 connections (Average across TCP/UDP)
    ax_base = axes[0]
    base_vals = [(avg_base_tcp[b] + avg_base_udp[b]) / 2.0 for b in BACKENDS]
    bars_base = ax_base.barh(y, base_vals, height=0.42, color=[COLORS[b] for b in BACKENDS], zorder=3)
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
    ax_base.set_yticklabels([NAMES[b] for b in BACKENDS], fontproperties=prop_medium, fontsize=10)
    ax_base.set_xlabel("Base Process Memory PSS (MB)", fontproperties=prop_regular, fontsize=10, color="#64748b")
    ax_base.set_title("Fixed Baseline Process Footprint (0 Conns)", fontproperties=prop_bold, fontsize=12, pad=12, color="#0f172a")
    ax_base.grid(axis="x", color="#f1f5f9", zorder=0)
    ax_base.set_axisbelow(True)

    # Subplot 2: Incremental Connection Slope (TCP & UDP paired bars)
    ax_slope = axes[1]
    bar_h = 0.22
    for idx_net, (net_label, slope_map, offset) in enumerate([
        ("TCP", avg_slope_tcp, bar_h / 2 + 0.02),
        ("UDP", avg_slope_udp, -(bar_h / 2 + 0.02)),
    ]):
        vals = [slope_map[b] for b in BACKENDS]
        max_slope = max(list(avg_slope_tcp.values()) + list(avg_slope_udp.values()) + [5.0])
        ax_slope.set_xlim(0, max_slope * 1.35)

        bars = ax_slope.barh(
            y + offset, vals, height=bar_h,
            color=[COLORS[b] for b in BACKENDS],
            alpha=0.95 if net_label == "TCP" else 0.55,
            edgecolor=[COLORS[b] for b in BACKENDS],
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
    ax_slope.set_yticklabels([NAMES[b] for b in BACKENDS], fontproperties=prop_medium, fontsize=10)
    ax_slope.set_xlabel("Incremental Footprint (KiB / connection)", fontproperties=prop_regular, fontsize=10, color="#64748b")
    ax_slope.set_title("Incremental Retention Slope (0 → 1000 Conns)", fontproperties=prop_bold, fontsize=12, pad=12, color="#0f172a")
    ax_slope.grid(axis="x", color="#f1f5f9", zorder=0)
    ax_slope.set_axisbelow(True)

    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor=COLORS[b])
        for b in BACKENDS
    ] + [
        plt.Rectangle((0, 0), 1, 1, facecolor="#64748b", alpha=0.95),
        plt.Rectangle((0, 0), 1, 1, facecolor="#64748b", alpha=0.55),
    ]
    legend_labels = [NAMES[b] for b in BACKENDS] + ["TCP Slope", "UDP Slope"]

    create_top_legend(fig, legend_handles, legend_labels, y_pos=0.895, prop_medium=prop_medium)
    save_dashboard(fig, path, dpi=200)


def main() -> None:
    parser = argparse.ArgumentParser(description="Render SimpleXray light benchmark dashboards.")
    parser.add_argument("--input-dir", required=True, help="Directory containing raw JSON result files")
    parser.add_argument("--output-dir", required=True, help="Directory to save generated charts and report")
    args = parser.parse_args()

    root = os.path.abspath(args.input_dir)
    output = os.path.abspath(args.output_dir)
    chart_dir = os.path.join(output, "charts")
    report_dir = os.path.join(output, "report")
    os.makedirs(chart_dir, exist_ok=True)
    os.makedirs(report_dir, exist_ok=True)

    standard = flat_rounds(load(os.path.join(root, "standard.json")))
    idle = flat_rounds(load(os.path.join(root, "idle.json")))

    latency = []
    for round_no in range(1, 4):
        p = os.path.join(root, f"latency_round_{round_no}.json")
        if os.path.exists(p):
            latency.extend(load(p).get("bufferbloat", []))

    longrun = load(os.path.join(root, "longrun.json")).get("long_run", [])

    cps = []
    for round_no in range(1, 4):
        p = os.path.join(root, f"cps_round_{round_no}.json")
        if os.path.exists(p):
            cps.extend(load(p).get("results", []))

    # 1. Wi-Fi Throughput
    groups = defaultdict(lambda: defaultdict(list))
    for row in standard:
        if row.get("backend") in BACKENDS and row.get("mtu") == 1500:
            net = row.get("network", "tcp").upper()
            par = row.get("parallel", 1)
            b = row.get("backend")
            mean_tp = (float(row.get("upload_mbps", 0.0)) + float(row.get("download_mbps", 0.0))) / 2.0
            groups[(net, par)][b].append(mean_tp)
    render_throughput(groups, os.path.join(chart_dir, "light_wifi_throughput.webp"))

    # 2. Bufferbloat (Loaded Latency Delta)
    bloat_delta = {b: average([r for r in latency if r.get("backend") == b], "delta_rtt_ms") for b in BACKENDS}
    bloat_tp = {b: average([r for r in latency if r.get("backend") == b], "throughput_mbps") for b in BACKENDS}
    render_bufferbloat(bloat_delta, bloat_tp, os.path.join(chart_dir, "light_bufferbloat.webp"))

    # 3. 60s Longrun Stability (Line Chart)
    render_longrun_chart(longrun, os.path.join(chart_dir, "light_longrun.webp"))

    # 4. Idle Memory (Line Chart)
    render_idle(idle, os.path.join(chart_dir, "light_idle_memory.webp"))

    # 5. CPS (Horizontal Bar Charts)
    render_cps(cps, os.path.join(chart_dir, "light_cps.webp"))

    # 6. Scheme 2 Fixed vs Incremental Memory Attribution (if available)
    microbench_file = os.path.join(root, "microbench_results.json")
    if os.path.exists(microbench_file):
        render_memory_attribution(microbench_file, os.path.join(chart_dir, "light_memory_fixed_vs_incremental.webp"))

    # 7. Report Markdown
    report_file = os.path.join(report_dir, "light-benchmark-report.md")
    with open(report_file, "w", encoding="utf-8") as handle:
        handle.write("# SimpleXray Benchmark Report\n\n")
        handle.write("Publication-grade benchmark evaluation across three primary TUN backends on Android:\n\n")
        handle.write("- **Backends**: Hev (`#555555`), SingTUN (`#00ADD8`), Zeptun (`#F7A41D`)\n")
        handle.write("- **Wi-Fi MTU 1500**: TCP/UDP, P=1/P=8, 3 rounds bidirectional mean\n")
        handle.write("- **Bufferbloat**: Concurrent TCP echo probe during 8-stream saturation\n")
        handle.write("- **60-Second Long-Run**: 1-second interval time-series throughput stability and decay\n")
        handle.write("- **Idle Connection Footprint**: 0 → 1000 stepped TCP connection retention PSS\n")
        handle.write("- **Connection Rate (CPS)**: Short-lived TCP handshakes across 4 and 8 workers\n")
        if os.path.exists(microbench_file):
            handle.write("- **Scheme 2 Microbenchmark**: Fixed base memory footprint vs incremental per-connection slope\n")

    print("[Light Benchmark] All charts and report successfully generated.")


if __name__ == "__main__":
    main()

