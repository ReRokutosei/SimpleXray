#!/usr/bin/env python3
"""
SimpleXray Benchmark Dashboard Generator (v2)
Generates unified, publication-quality 16:9 dashboards from benchmark results.
Design Language:
  - Modern, clean, restrained technical aesthetics.
  - Side-by-side subplots (Single vs Multi-Stream) for comprehensive views.
  - Top-centered horizontal legend to prevent any visual obstruction.
  - Standardized font: JetBrains Mono Nerd Font.
"""

import argparse
import json
import os
import sys
from typing import Tuple, List, Dict, Any, Optional

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
import numpy as np

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

sys.path.insert(0, SCRIPT_DIR)
from common.device import (
    DEFAULT_DEVICE,
    DEVICE_PROFILES,
    resolve_device_paths,
)
from common.theme import (
    PALETTE,
    setup_fonts,
    apply_global_theme,
    add_dashboard_header,
    create_top_legend,
    save_dashboard,
)
from common.dataset import compute_clean_averages

prop_regular, prop_bold, prop_medium = setup_fonts()
apply_global_theme()

BACKEND_ORDER = ['xray', 'sing', 'zeptun', 'hev']


def infer_dut_label(json_path: str, output_dir: str) -> str:
    """Infers a human-readable DUT label from known benchmark dataset paths."""
    haystack = f"{json_path} {output_dir}".lower()
    if "8-elite-gen-5" in haystack or "8_elite_gen_5" in haystack:
        return "Snapdragon 8 Elite Gen 5"
    if "778g" in haystack or "778-g" in haystack:
        return "Snapdragon 778G"
    return "DUT"


def render_throughput_dashboard(
    output_path: str,
    title: str,
    subtitle: str,
    categories: List[Dict[str, Any]],
    single_data: Dict[str, Dict[str, float]],
    multi_data: Dict[str, Dict[str, float]],
    single_baselines: Dict[str, float],
    multi_baselines: Dict[str, float],
    max_val: float,
    unit: str = "Mbps",
    single_losses: Optional[Dict[str, Dict[str, float]]] = None,
    multi_losses: Optional[Dict[str, Dict[str, float]]] = None
):
    """
    Renders a unified 16:9 throughput dashboard with side-by-side subplots:
    Left: Single Stream (P=1)
    Right: Multi Stream (P=8)
    """
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 9.5), dpi=200)
    plt.subplots_adjust(left=0.18, right=0.96, top=0.80, bottom=0.08, wspace=0.10)

    add_dashboard_header(fig, title, subtitle)

    cat_labels = [c['label'] for c in categories]
    cat_keys = [c['key'] for c in categories]
    num_cats = len(categories)
    num_backends = len(BACKEND_ORDER)

    y_indices = np.arange(num_cats)
    # Reverse so top item is first in reading order
    y_indices_rev = y_indices[::-1]

    bar_height = 0.17
    offsets = np.linspace((num_backends - 1) * bar_height / 2, -(num_backends - 1) * bar_height / 2, num_backends)

    legend_rects = []
    legend_labels = []

    def draw_subplot(ax, data_map, baselines, losses_map, sub_title, is_left: bool = True):
        ax.set_title(sub_title, fontsize=12, fontweight='bold', color='#1e293b',
                     fontproperties=prop_bold, pad=12)
        ax.set_yticks(y_indices_rev)
        if is_left:
            ax.set_yticklabels(cat_labels, fontsize=10, fontproperties=prop_medium, color='#334155')
        else:
            ax.set_yticklabels([])
            ax.tick_params(left=False)

        ax.set_xlim(0, max_val * 1.20)
        ax.set_xlabel(f"Throughput ({unit})", fontsize=10, color='#64748b', fontproperties=prop_regular)
        ax.grid(True, axis='x', zorder=0)

        # Draw category divider lines
        for y in y_indices:
            if y < num_cats - 1:
                ax.axhline(y + 0.5, color='#e2e8f0', linestyle='--', linewidth=0.8, zorder=1)

        # Draw bars for each backend
        for b_idx, b_key in enumerate(BACKEND_ORDER):
            b_info = PALETTE[b_key]
            vals = [data_map.get(k, {}).get(b_key, 0.0) for k in cat_keys]
            y_positions = y_indices_rev + offsets[b_idx]

            rects = ax.barh(
                y_positions, vals, bar_height,
                color=b_info['fill'], edgecolor=b_info['edge'],
                linewidth=0.8, zorder=3,
                label=b_info['name']
            )

            if len(legend_rects) < num_backends:
                legend_rects.append(rects)
                legend_labels.append(b_info['name'])

            # Value labels with optional UDP loss annotation & translucent pad
            for cat_idx, (rect, val) in enumerate(zip(rects, vals)):
                if val > 0:
                    text_x = val + max_val * 0.015
                    txt = f"{val:.1f}" if val < 1000 else f"{int(val)}"
                    k = cat_keys[cat_idx]
                    loss = losses_map.get(k, {}).get(b_key, 0.0) if losses_map else 0.0
                    if "udp" in k and loss >= 0.1:
                        txt += f" ({loss:.1f}% loss)"

                    ax.text(
                        text_x, rect.get_y() + rect.get_height() / 2,
                        txt,
                        ha='left', va='center',
                        fontsize=8.2, fontweight='bold', color='#1e293b',
                        fontproperties=prop_bold,
                        bbox=dict(boxstyle='round,pad=0.15', facecolor='#ffffff', edgecolor='none', alpha=0.85),
                        zorder=5
                    )

        # Draw reference baseline lines for each category
        for idx, k in enumerate(cat_keys):
            base_val = baselines.get(k, 0.0)
            if base_val > 0:
                y_center = y_indices_rev[idx]
                y_min = y_center - (num_backends * bar_height / 2) - 0.04
                y_max = y_center + (num_backends * bar_height / 2) + 0.04
                ax.vlines(
                    x=base_val, ymin=y_min, ymax=y_max,
                    colors='#64748b', linestyles='--', linewidth=1.5, zorder=4
                )
                ax.text(
                    base_val, y_max + 0.03,
                    f"Baseline: {base_val:.1f}",
                    ha='center', va='bottom',
                    fontsize=7.5, color='#64748b',
                    fontproperties=prop_regular,
                    bbox=dict(boxstyle='round,pad=0.12', facecolor='#ffffff', edgecolor='none', alpha=0.85),
                    zorder=5
                )

    draw_subplot(ax1, single_data, single_baselines, single_losses, "Single Stream (P=1) (Higher is Better)", is_left=True)
    draw_subplot(ax2, multi_data, multi_baselines, multi_losses, "Multi-Stream (P=8 Parallel) (Higher is Better)", is_left=False)

    # Legend at top
    create_top_legend(fig, legend_rects, legend_labels)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=200, facecolor='#f8fafc', edgecolor='none')
    plt.close()
    print(f"[Dashboard] Saved: {output_path}")


def render_efficiency_dashboard(
    output_path: str,
    title: str,
    subtitle: str,
    categories: List[Dict[str, Any]],
    single_costs: Dict[str, Dict[str, float]],
    multi_costs: Dict[str, Dict[str, float]],
    max_cost: float
):
    """
    Renders CPU cost per 100 Mbps (CPU% / 100Mbps).
    Lower is better!
    """
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 9.5), dpi=200)
    plt.subplots_adjust(left=0.18, right=0.96, top=0.80, bottom=0.08, wspace=0.10)

    add_dashboard_header(fig, title, subtitle)

    cat_labels = [c['label'] for c in categories]
    cat_keys = [c['key'] for c in categories]
    num_cats = len(categories)
    num_backends = len(BACKEND_ORDER)

    y_indices = np.arange(num_cats)
    y_indices_rev = y_indices[::-1]

    bar_height = 0.17
    offsets = np.linspace((num_backends - 1) * bar_height / 2, -(num_backends - 1) * bar_height / 2, num_backends)

    legend_rects = []
    legend_labels = []

    def draw_cost_subplot(ax, data_map, sub_title, is_left: bool = True):
        ax.set_title(sub_title, fontsize=12, fontweight='bold', color='#1e293b',
                     fontproperties=prop_bold, pad=12)
        ax.set_yticks(y_indices_rev)
        if is_left:
            ax.set_yticklabels(cat_labels, fontsize=10, fontproperties=prop_medium, color='#334155')
        else:
            ax.set_yticklabels([])
            ax.tick_params(left=False)

        ax.set_xlim(0, max_cost * 1.20)
        ax.set_xlabel("CPU Cost (CPU % per 100 Mbps)", fontsize=10, color='#64748b', fontproperties=prop_regular)
        ax.grid(True, axis='x', zorder=0)

        for y in y_indices:
            if y < num_cats - 1:
                ax.axhline(y + 0.5, color='#e2e8f0', linestyle='--', linewidth=0.8, zorder=1)

        for b_idx, b_key in enumerate(BACKEND_ORDER):
            b_info = PALETTE[b_key]
            vals = [data_map.get(k, {}).get(b_key, 0.0) for k in cat_keys]
            y_positions = y_indices_rev + offsets[b_idx]

            rects = ax.barh(
                y_positions, vals, bar_height,
                color=b_info['fill'], edgecolor=b_info['edge'],
                linewidth=0.8, zorder=3,
                label=b_info['name']
            )

            if len(legend_rects) < num_backends:
                legend_rects.append(rects)
                legend_labels.append(b_info['name'])

            for rect, val in zip(rects, vals):
                if val > 0:
                    text_x = val + max_cost * 0.015
                    ax.text(
                        text_x, rect.get_y() + rect.get_height() / 2,
                        f"{val:.1f}%",
                        ha='left', va='center',
                        fontsize=8.5, fontweight='bold', color='#1e293b',
                        fontproperties=prop_bold,
                        bbox=dict(boxstyle='round,pad=0.15', facecolor='#ffffff', edgecolor='none', alpha=0.85),
                        zorder=5
                    )

    draw_cost_subplot(ax1, single_costs, "Single Stream (P=1) (Lower is Better)", is_left=True)
    draw_cost_subplot(ax2, multi_costs, "Multi-Stream (P=8 Parallel) (Lower is Better)", is_left=False)

    create_top_legend(fig, legend_rects, legend_labels)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=200, facecolor='#f8fafc', edgecolor='none')
    plt.close()
    print(f"[Dashboard] Saved: {output_path}")


def render_loopback_dashboard(
    output_path: str,
    title: str,
    subtitle: str,
    loop_speeds: Dict[str, Dict[str, float]],
    loop_cpus: Dict[str, Dict[str, float]],
    max_speed: float,
    max_cpu: float
):
    """
    Renders on-device loopback speed and peak CPU usage.
    """
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 7.5), dpi=200)
    plt.subplots_adjust(left=0.15, right=0.96, top=0.75, bottom=0.12, wspace=0.12)

    add_dashboard_header(fig, title, subtitle)

    modes = [("Single Stream (P=1)", "single"), ("Multi-Stream (P=8)", "multi")]
    mode_labels = [m[0] for m in modes]
    mode_keys = [m[1] for m in modes]
    num_modes = len(modes)
    num_backends = len(BACKEND_ORDER)

    y_indices = np.arange(num_modes)[::-1]
    bar_height = 0.16
    offsets = np.linspace((num_backends - 1) * bar_height / 2, -(num_backends - 1) * bar_height / 2, num_backends)

    legend_rects = []
    legend_labels = []

    # Left: Speed (Gbps)
    ax1.set_title("Loopback Throughput (Gbps) (Higher is Better)", fontsize=12, fontweight='bold', color='#1e293b',
                  fontproperties=prop_bold, pad=12)
    ax1.set_yticks(y_indices)
    ax1.set_yticklabels(mode_labels, fontsize=10.5, fontproperties=prop_medium, color='#334155')
    ax1.set_xlim(0, max_speed * 1.2)
    ax1.set_xlabel("Throughput (Gbps)", fontsize=10, color='#64748b', fontproperties=prop_regular)
    ax1.grid(True, axis='x', zorder=0)

    for b_idx, b_key in enumerate(BACKEND_ORDER):
        b_info = PALETTE[b_key]
        vals = [loop_speeds.get(m, {}).get(b_key, 0.0) for m in mode_keys]
        y_pos = y_indices + offsets[b_idx]
        rects = ax1.barh(
            y_pos, vals, bar_height,
            color=b_info['fill'], edgecolor=b_info['edge'],
            linewidth=0.8, zorder=3, label=b_info['name']
        )
        legend_rects.append(rects)
        legend_labels.append(b_info['name'])

        for rect, val in zip(rects, vals):
            if val > 0:
                ax1.text(
                    val + max_speed * 0.015, rect.get_y() + rect.get_height() / 2,
                    f"{val:.2f} Gbps",
                    ha='left', va='center',
                    fontsize=8.5, fontweight='bold', color='#1e293b',
                    fontproperties=prop_bold,
                    bbox=dict(boxstyle='round,pad=0.15', facecolor='#ffffff', edgecolor='none', alpha=0.85),
                    zorder=5
                )

    # Right: Peak CPU (%)
    ax2.set_title("Loopback Peak CPU Usage (Lower is Better)", fontsize=12, fontweight='bold', color='#1e293b',
                  fontproperties=prop_bold, pad=12)
    ax2.set_yticks(y_indices)
    ax2.set_yticklabels([])
    ax2.tick_params(left=False)
    ax2.set_xlim(0, max(max_cpu * 1.25, 20.0))
    ax2.set_xlabel("Peak CPU (%)", fontsize=10, color='#64748b', fontproperties=prop_regular)
    ax2.grid(True, axis='x', zorder=0)

    for b_idx, b_key in enumerate(BACKEND_ORDER):
        b_info = PALETTE[b_key]
        vals = [loop_cpus.get(m, {}).get(b_key, 0.0) for m in mode_keys]
        y_pos = y_indices + offsets[b_idx]
        rects = ax2.barh(
            y_pos, vals, bar_height,
            color=b_info['fill'], edgecolor=b_info['edge'],
            linewidth=0.8, zorder=3
        )
        for rect, val in zip(rects, vals):
            ax2.text(
                val + max(max_cpu * 0.015, 0.3), rect.get_y() + rect.get_height() / 2,
                f"{val:.1f}%",
                ha='left', va='center',
                fontsize=8.5, fontweight='bold', color='#1e293b',
                fontproperties=prop_bold,
                bbox=dict(boxstyle='round,pad=0.15', facecolor='#ffffff', edgecolor='none', alpha=0.85),
                zorder=5
            )

    create_top_legend(fig, legend_rects, legend_labels, y_pos=0.88)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=200, facecolor='#f8fafc', edgecolor='none')
    plt.close()
    print(f"[Dashboard] Saved: {output_path}")


def render_idle_memory_dashboard(
    output_path: str,
    title: str,
    subtitle: str,
    tcp_flows_data: Dict[str, List[Tuple[int, float]]],
    udp_flows_data: Dict[str, List[Tuple[int, float]]]
):
    """
    Renders 0 -> 1000 Idle Flows vs Memory Growth (PSS MiB) line charts.
    Left: TCP Idle Flows
    Right: UDP Idle Flows
    """
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 8), dpi=200)
    plt.subplots_adjust(left=0.08, right=0.96, top=0.78, bottom=0.10, wspace=0.18)

    add_dashboard_header(fig, title, subtitle)

    markers = {'hev': 'o', 'sing': 's', 'xray': 'D', 'zeptun': 'P'}
    legend_lines = []
    legend_labels = []

    def draw_line_subplot(ax, data_map, sub_title):
        ax.set_title(sub_title, fontsize=12, fontweight='bold', color='#1e293b',
                     fontproperties=prop_bold, pad=12)
        ax.set_xlabel("Retained Idle Connections (Flows)", fontsize=10, color='#64748b', fontproperties=prop_regular)
        ax.set_ylabel("Memory Growth (MiB PSS above baseline)", fontsize=10, color='#64748b', fontproperties=prop_regular)
        ax.grid(True, zorder=0)

        min_y = 0.0
        max_y = 5.0
        for b_key in BACKEND_ORDER:
            b_info = PALETTE[b_key]
            points = data_map.get(b_key, [])
            if not points:
                continue
            x_vals = [p[0] for p in points]
            y_vals = [p[1] for p in points]
            min_y = min(min_y, min(y_vals) if y_vals else 0.0)
            max_y = max(max_y, max(y_vals) if y_vals else 5.0)

            line, = ax.plot(
                x_vals, y_vals,
                color=b_info['fill'], marker=markers.get(b_key, 'o'),
                markersize=6, linewidth=2.0, zorder=3,
                label=b_info['name']
            )
            if b_info['name'] not in legend_labels:
                legend_lines.append(line)
                legend_labels.append(b_info['name'])

            # Annotate final slope
            if len(x_vals) >= 2 and x_vals[-1] > 0:
                slope_kib = (y_vals[-1] - y_vals[0]) * 1024.0 / (x_vals[-1] - x_vals[0])
                label_text = f"{b_info['name'].split()[0]}: {slope_kib:.2f} KiB/conn"
                if slope_kib <= 0:
                    label_text = f"{b_info['name'].split()[0]}: ~0.00 KiB/conn"
                ax.text(
                    x_vals[-1] + 15, y_vals[-1],
                    label_text,
                    fontsize=8, fontweight='bold', color=b_info['edge'],
                    va='center', fontproperties=prop_bold
                )

        ax.axhline(0, color='#94a3b8', linestyle='--', linewidth=0.8, alpha=0.7, zorder=1)
        ax.set_xlim(-30, 1180)
        ax.set_ylim(min(-1.5, min_y - 0.5), max_y * 1.25)
        ax.set_xticks([0, 250, 500, 750, 1000])

    draw_line_subplot(ax1, tcp_flows_data, "TCP Idle Connections vs Memory Growth (Lower is Better)")
    draw_line_subplot(ax2, udp_flows_data, "UDP Idle Connections vs Memory Growth (Lower is Better)")

    create_top_legend(fig, legend_lines, legend_labels, y_pos=0.88)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=200, facecolor='#f8fafc', edgecolor='none')
    plt.close()
    print(f"[Dashboard] Saved: {output_path}")


def render_udp_jitter_dashboard(
    output_path: str,
    title: str,
    subtitle: str,
    categories: List[Dict[str, Any]],
    wifi_jitters: Dict[str, Dict[str, float]],
    usb_jitters: Dict[str, Dict[str, float]],
    wifi_baselines: Dict[str, float],
    usb_baselines: Dict[str, float]
):
    """
    Renders a unified 16:9 UDP Jitter dashboard with side-by-side subplots split by physical medium:
    Left: 5GHz Wi-Fi (Lower is Better)
    Right: USB 3.2 Tethering (Lower is Better)
    """
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 9.5), dpi=200)
    plt.subplots_adjust(left=0.18, right=0.96, top=0.80, bottom=0.08, wspace=0.10)

    add_dashboard_header(fig, title, subtitle)

    cat_labels = [c['label'] for c in categories]
    cat_keys = [c['key'] for c in categories]
    num_cats = len(categories)
    num_backends = len(BACKEND_ORDER)

    y_indices = np.arange(num_cats)
    y_indices_rev = y_indices[::-1]

    bar_height = 0.17
    offsets = np.linspace((num_backends - 1) * bar_height / 2, -(num_backends - 1) * bar_height / 2, num_backends)

    legend_rects = []
    legend_labels = []

    def draw_subplot(ax, data_map, baselines, sub_title, is_left: bool = True):
        ax.set_title(sub_title, fontsize=12, fontweight='bold', color='#1e293b',
                     fontproperties=prop_bold, pad=12)
        ax.set_yticks(y_indices_rev)
        if is_left:
            ax.set_yticklabels(cat_labels, fontsize=10, fontproperties=prop_medium, color='#334155')
        else:
            ax.set_yticklabels([])
            ax.tick_params(left=False)

        max_val = 0.05
        for k in cat_keys:
            base_v = baselines.get(k, 0.0)
            max_val = max(max_val, base_v)
            for b_key in BACKEND_ORDER:
                max_val = max(max_val, data_map.get(k, {}).get(b_key, 0.0))

        ax.set_xlim(0, max_val * 1.30)
        ax.set_xlabel("Jitter (ms)", fontsize=10, color='#64748b', fontproperties=prop_regular)
        ax.grid(True, axis='x', zorder=0)

        for y in y_indices:
            if y < num_cats - 1:
                ax.axhline(y + 0.5, color='#e2e8f0', linestyle='--', linewidth=0.8, zorder=1)

        for b_idx, b_key in enumerate(BACKEND_ORDER):
            b_info = PALETTE[b_key]
            vals = [data_map.get(k, {}).get(b_key, 0.0) for k in cat_keys]
            y_positions = y_indices_rev + offsets[b_idx]

            rects = ax.barh(
                y_positions, vals, bar_height,
                color=b_info['fill'], edgecolor=b_info['edge'],
                linewidth=0.8, zorder=3,
                label=b_info['name']
            )

            if len(legend_rects) < num_backends:
                legend_rects.append(rects)
                legend_labels.append(b_info['name'])

            for cat_idx, (rect, val) in enumerate(zip(rects, vals)):
                if val >= 0:
                    text_x = val + max_val * 0.015
                    txt = f"{val:.3f} ms" if val < 0.1 else f"{val:.2f} ms"

                    ax.text(
                        text_x, rect.get_y() + rect.get_height() / 2,
                        txt,
                        ha='left', va='center',
                        fontsize=8.2, fontweight='bold', color='#1e293b',
                        fontproperties=prop_bold,
                        bbox=dict(boxstyle='round,pad=0.15', facecolor='#ffffff', edgecolor='none', alpha=0.85),
                        zorder=5
                    )

        for idx, k in enumerate(cat_keys):
            base_val = baselines.get(k, 0.0)
            if base_val > 0:
                y_center = y_indices_rev[idx]
                y_min = y_center - (num_backends * bar_height / 2) - 0.04
                y_max = y_center + (num_backends * bar_height / 2) + 0.04
                ax.vlines(
                    x=base_val, ymin=y_min, ymax=y_max,
                    colors='#64748b', linestyles='--', linewidth=1.5, zorder=4
                )
                txt_base = f"Baseline: {base_val:.3f} ms" if base_val < 0.1 else f"Baseline: {base_val:.2f} ms"
                ax.text(
                    base_val, y_max + 0.03,
                    txt_base,
                    ha='center', va='bottom',
                    fontsize=7.5, color='#64748b',
                    fontproperties=prop_regular,
                    bbox=dict(boxstyle='round,pad=0.12', facecolor='#ffffff', edgecolor='none', alpha=0.85),
                    zorder=5
                )

    draw_subplot(ax1, wifi_jitters, wifi_baselines, "5GHz Wi-Fi Transmission Jitter (Lower is Better)", is_left=True)
    draw_subplot(ax2, usb_jitters, usb_baselines, "USB 3.2 Tethering Jitter (Lower is Better)", is_left=False)

    create_top_legend(fig, legend_rects, legend_labels)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=200, facecolor='#f8fafc', edgecolor='none')
    plt.close()
    print(f"[Dashboard] Saved: {output_path}")


def render_fullstack_attribution_dashboard(
    output_path: str,
    android_avg_records: List[Dict[str, Any]],
    microbench_path: str
):
    """
    Renders the Full-Stack Memory Attribution Dashboard (16:9).
    Compares Standalone Pure Stack (Isolated Linux Kernel TUN Harness)
    vs Android End-to-End (SimpleXray Main Process) connection slopes (KiB/conn).
    Left Subplot: TCP Idle Connections
    Right Subplot: UDP Idle Connections
    Bottom: Layer Cost Decomposition & Architectural Insight Callout.
    """
    if not os.path.exists(microbench_path):
        print(f"[Dashboard] Warning: {microbench_path} not found, skipping attribution dashboard.")
        return

    with open(microbench_path, "r", encoding="utf-8") as f:
        mb_data = json.load(f)

    # 1. Standalone slopes (3-round average)
    mb_rounds = mb_data.get("rounds", {})
    pure_tcp_slopes = {b: [] for b in BACKEND_ORDER}
    pure_udp_slopes = {b: [] for b in BACKEND_ORDER}
    for r_name, r_list in mb_rounds.items():
        for rec in r_list:
            b = rec.get("backend")
            net = rec.get("network")
            slope = rec.get("slope_kib_per_conn", 0.0)
            if b in BACKEND_ORDER:
                if net == "tcp":
                    pure_tcp_slopes[b].append(slope)
                elif net == "udp":
                    pure_udp_slopes[b].append(slope)

    pure_tcp_avg = {b: round(sum(pure_tcp_slopes[b]) / len(pure_tcp_slopes[b]), 2) if pure_tcp_slopes[b] else 0.0 for b in BACKEND_ORDER}
    pure_udp_avg = {b: round(sum(pure_udp_slopes[b]) / len(pure_udp_slopes[b]), 2) if pure_udp_slopes[b] else 0.0 for b in BACKEND_ORDER}

    # 2. Android slopes (from android_avg_records idle_flows)
    android_tcp_avg = {b: 0.0 for b in BACKEND_ORDER}
    android_udp_avg = {b: 0.0 for b in BACKEND_ORDER}
    for rec in android_avg_records:
        if rec.get("type") == "idle_memory":
            b = rec.get("backend")
            net = rec.get("network", "tcp").lower()
            flows = rec.get("idle_flows", [])
            if b in BACKEND_ORDER and len(flows) >= 2:
                c0 = flows[0].get("connections", 0)
                c1 = flows[-1].get("connections", 1000)
                m0 = flows[0].get("pss_mb", 0.0)
                m1 = flows[-1].get("pss_mb", 0.0)
                slope = round((m1 - m0) * 1024.0 / (c1 - c0), 2) if c1 > c0 else 0.0
                if net == "udp":
                    android_udp_avg[b] = slope
                else:
                    android_tcp_avg[b] = slope

    fig = plt.figure(figsize=(16, 9.5), dpi=200)
    fig.patch.set_facecolor('#f8fafc')

    add_dashboard_header(
        fig,
        "Full-Stack Memory Attribution: Pure Core Stack vs Android End-to-End",
        "Connection Footprint (KiB/conn) & Layer Cost Attribution Across 4 TUN Backends (3-Round Mean)"
    )

    ax1 = fig.add_axes([0.16, 0.12, 0.37, 0.70])
    ax2 = fig.add_axes([0.57, 0.12, 0.37, 0.70])

    num_backends = len(BACKEND_ORDER)
    y_indices = np.arange(num_backends)[::-1]
    backend_labels = [PALETTE[b]['name'] for b in BACKEND_ORDER]

    bar_height = 0.28
    pure_offset = bar_height / 2 + 0.02
    android_offset = -(bar_height / 2 + 0.02)

    c_pure = '#4f46e5'    # Indigo 600
    c_pure_edge = '#3730a3'
    c_android = '#0d9488' # Teal 600
    c_android_edge = '#0f766e'

    max_x = max(
        max(pure_tcp_avg.values()), max(android_tcp_avg.values()),
        max(pure_udp_avg.values()), max(android_udp_avg.values())
    ) * 1.35

    def draw_attr_subplot(ax, pure_map, android_map, sub_title, is_left: bool = True):
        ax.set_title(sub_title, fontsize=12, fontweight='bold', color='#1e293b',
                     fontproperties=prop_bold, pad=12)
        ax.set_yticks(y_indices)
        if is_left:
            ax.set_yticklabels(backend_labels, fontsize=10.5, fontproperties=prop_medium, color='#334155')
        else:
            ax.set_yticklabels([])
            ax.tick_params(left=False)

        ax.set_xlim(0, max_x)
        ax.set_xlabel("Memory Cost per Connection (KiB/conn)", fontsize=10, color='#64748b', fontproperties=prop_regular)
        ax.grid(True, axis='x', zorder=0)

        for y in np.arange(num_backends):
            if y < num_backends - 1:
                ax.axhline(y + 0.5, color='#e2e8f0', linestyle='--', linewidth=0.8, zorder=1)

        for idx, b in enumerate(BACKEND_ORDER):
            y_pos = y_indices[idx]
            v_pure = pure_map.get(b, 0.0)
            v_android = android_map.get(b, 0.0)

            # Bar 1: Pure Standalone
            ax.barh(
                y_pos + pure_offset, v_pure, bar_height,
                color=c_pure, edgecolor=c_pure_edge, linewidth=0.8, zorder=3
            )
            # Bar 2: Android End-to-End
            v_android_bar = max(0.0, v_android)
            ax.barh(
                y_pos + android_offset, v_android_bar, bar_height,
                color=c_android, edgecolor=c_android_edge, linewidth=0.8, zorder=3
            )

            # Pure label
            ax.text(
                v_pure + max_x * 0.015, y_pos + pure_offset,
                f"{v_pure:.2f} KiB",
                ha='left', va='center',
                fontsize=8.5, fontweight='bold', color='#1e293b',
                fontproperties=prop_bold,
                bbox=dict(boxstyle='round,pad=0.15', facecolor='#ffffff', edgecolor='none', alpha=0.85),
                zorder=5
            )

            # Android label
            txt_a = f"{v_android:.2f} KiB"
            if v_android <= 0:
                txt_a = "~0.00 KiB (GC steady)"
            if b == 'xray':
                txt_a += " (Main Process Only *)"
            ax.text(
                v_android_bar + max_x * 0.015, y_pos + android_offset,
                txt_a,
                ha='left', va='center',
                fontsize=8.5, fontweight='bold', color='#0f766e' if b != 'xray' else '#d97706',
                fontproperties=prop_bold,
                bbox=dict(boxstyle='round,pad=0.15', facecolor='#ffffff', edgecolor='none', alpha=0.85),
                zorder=5
            )

    draw_attr_subplot(ax1, pure_tcp_avg, android_tcp_avg, "TCP Connection Footprint (KiB/conn) (Lower is Better)", is_left=True)
    draw_attr_subplot(ax2, pure_udp_avg, android_udp_avg, "UDP Connection Footprint (KiB/conn) (Lower is Better)", is_left=False)

    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor=c_pure, edgecolor=c_pure_edge),
        plt.Rectangle((0, 0), 1, 1, facecolor=c_android, edgecolor=c_android_edge),
    ]
    legend_labels = [
        "Standalone Pure Stack (Isolated Linux Kernel TUN Harness)",
        "Android End-to-End (SimpleXray Main Process Memory Footprint)"
    ]
    create_top_legend(fig, legend_handles, legend_labels, y_pos=0.895)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=200, facecolor='#f8fafc', edgecolor='none')
    plt.close()
    print(f"[Dashboard] Saved: {output_path}")


def process_dataset_and_generate_dashboards(
    records: list,
    output_dir: str,
    prefix: str = "",
    dut_label: str = "DUT",
):
    """
    Processes records and produces the 5 publication dashboards.

    dut_label is printed in chart subtitles so the same generator can be reused
    for different Android devices without hardcoding a SoC name.
    """
    def get_val(medium: str, backend: str, mtu: int, parallel: int, field: str, network: str = "tcp") -> float:
        for r in records:
            if (r.get("medium") == medium and
                r.get("backend") == backend and
                r.get("mtu") == mtu and
                r.get("parallel") == parallel and
                r.get("network", "tcp") == network):
                return float(r.get(field, 0.0))
        return 0.0

    mtus = {r.get("mtu", 0) for r in records}
    jumbo_mtu = 9000 if 9000 in mtus else (8500 if 8500 in mtus else 9000)
    jumbo_label = f"Jumbo {jumbo_mtu}"

    categories = [
        {"key": "tcp_down_1500", "label": "TCP Download (MTU 1500)"},
        {"key": "tcp_up_1500",   "label": "TCP Upload (MTU 1500)"},
        {"key": f"tcp_down_{jumbo_mtu}", "label": f"TCP Download ({jumbo_label})"},
        {"key": f"tcp_up_{jumbo_mtu}",   "label": f"TCP Upload ({jumbo_label})"},
    ]

    has_udp = any(r.get("network") == "udp" or r.get("protocol") == "udp" for r in records)
    if has_udp:
        categories.insert(0, {"key": "udp_down_1500", "label": "UDP Download (MTU 1500)"})
        categories.insert(0, {"key": "udp_up_1500",   "label": "UDP Upload (MTU 1500)"})

    # 1. 5GHz Wi-Fi Dashboard
    wifi_single = {}
    wifi_multi = {}
    wifi_loss_single = {}
    wifi_loss_multi = {}
    wifi_base_single = {}
    wifi_base_multi = {}

    for c in categories:
        k = c["key"]
        wifi_single[k] = {}
        wifi_multi[k] = {}
        wifi_loss_single[k] = {}
        wifi_loss_multi[k] = {}

        if "up" in k:
            field = "upload_mbps"
            loss_field = "upload_loss_percent"
        else:
            field = "download_mbps"
            loss_field = "download_loss_percent"

        net = "udp" if "udp" in k else "tcp"
        mtu_val = jumbo_mtu if str(jumbo_mtu) in k else 1500
        for b in BACKEND_ORDER:
            wifi_single[k][b] = get_val("5GHz Wi-Fi", b, mtu_val, 1, field, network=net)
            wifi_multi[k][b] = get_val("5GHz Wi-Fi", b, mtu_val, 8, field, network=net)
            wifi_loss_single[k][b] = get_val("5GHz Wi-Fi", b, mtu_val, 1, loss_field, network=net)
            wifi_loss_multi[k][b] = get_val("5GHz Wi-Fi", b, mtu_val, 8, loss_field, network=net)

        wifi_base_single[k] = get_val("5GHz Wi-Fi", "direct_none", 0, 1, field, network=net)
        wifi_base_multi[k] = get_val("5GHz Wi-Fi", "direct_none", 0, 8, field, network=net)

    max_wifi = max(
        max(max(v.values()) for v in wifi_single.values() if v.values()),
        max(max(v.values()) for v in wifi_multi.values() if v.values()),
        max(wifi_base_single.values() or [0]),
        max(wifi_base_multi.values() or [0]),
        100.0
    )

    render_throughput_dashboard(
        os.path.join(output_dir, f"{prefix}wifi_throughput_dashboard.webp"),
        "5GHz Wi-Fi Network Throughput (Mbps)",
        f"iPerf3 Client (DUT: {dut_label}) -> 5GHz Wi-Fi -> Linux 6.12 Server (iPerf3 UDP Target Limit: 200 Mbps)",
        categories, wifi_single, wifi_multi, wifi_base_single, wifi_base_multi, max_wifi,
        single_losses=wifi_loss_single, multi_losses=wifi_loss_multi
    )

    # 2. USB 3.2 Gen1 Dashboard
    usb_single = {}
    usb_multi = {}
    usb_loss_single = {}
    usb_loss_multi = {}
    usb_base_single = {}
    usb_base_multi = {}

    for c in categories:
        k = c["key"]
        usb_single[k] = {}
        usb_multi[k] = {}
        usb_loss_single[k] = {}
        usb_loss_multi[k] = {}
        field = "upload_mbps" if "up" in k else "download_mbps"
        loss_field = "upload_loss_percent" if "up" in k else "download_loss_percent"
        net = "udp" if "udp" in k else "tcp"
        mtu_val = jumbo_mtu if str(jumbo_mtu) in k else 1500

        for b in BACKEND_ORDER:
            usb_single[k][b] = get_val("USB 3.2 / 4.0", b, mtu_val, 1, field, network=net)
            usb_multi[k][b] = get_val("USB 3.2 / 4.0", b, mtu_val, 8, field, network=net)
            usb_loss_single[k][b] = get_val("USB 3.2 / 4.0", b, mtu_val, 1, loss_field, network=net)
            usb_loss_multi[k][b] = get_val("USB 3.2 / 4.0", b, mtu_val, 8, loss_field, network=net)

        usb_base_single[k] = get_val("USB 3.2 / 4.0", "direct_none", 0, 1, field, network=net)
        usb_base_multi[k] = get_val("USB 3.2 / 4.0", "direct_none", 0, 8, field, network=net)

    max_usb = max(
        max(max(v.values()) for v in usb_single.values() if v.values()),
        max(max(v.values()) for v in usb_multi.values() if v.values()),
        max(usb_base_single.values() or [0]),
        max(usb_base_multi.values() or [0]),
        100.0
    )

    render_throughput_dashboard(
        os.path.join(output_dir, f"{prefix}usb_throughput_dashboard.webp"),
        "USB 3.2 Gen1 / 4.0 Wired Throughput (Mbps)",
        f"iPerf3 Client (DUT: {dut_label}) -> USB 3.2 RNDIS (1 Gbps) -> Linux 6.12 Server (UDP Target Limit: 200 Mbps)",
        categories, usb_single, usb_multi, usb_base_single, usb_base_multi, max_usb,
        single_losses=usb_loss_single, multi_losses=usb_loss_multi
    )

    # 3. Compute Efficiency (CPU% / 100Mbps) Dashboard
    eff_cats = [
        {"key": "wifi_down_1500", "label": "Wi-Fi Download (1500)"},
        {"key": "wifi_up_1500",   "label": "Wi-Fi Upload (1500)"},
        {"key": f"wifi_down_{jumbo_mtu}", "label": f"Wi-Fi Download ({jumbo_mtu})"},
        {"key": f"wifi_up_{jumbo_mtu}",   "label": f"Wi-Fi Upload ({jumbo_mtu})"},
        {"key": "usb_down_1500",  "label": "USB Download (1500)"},
        {"key": "usb_up_1500",    "label": "USB Upload (1500)"},
        {"key": f"usb_down_{jumbo_mtu}",  "label": f"USB Download ({jumbo_mtu})"},
        {"key": f"usb_up_{jumbo_mtu}",    "label": f"USB Upload ({jumbo_mtu})"},
    ]

    single_costs = {}
    multi_costs = {}
    max_c = 10.0

    for c in eff_cats:
        k = c["key"]
        single_costs[k] = {}
        multi_costs[k] = {}

        medium = "5GHz Wi-Fi" if "wifi" in k else "USB 3.2 / 4.0"
        is_up = "up" in k
        speed_field = "upload_mbps" if is_up else "download_mbps"
        cpu_field = "upload_cpu_avg" if is_up else "download_cpu_avg"
        mtu_val = jumbo_mtu if str(jumbo_mtu) in k else 1500

        for b in BACKEND_ORDER:
            s_speed = get_val(medium, b, mtu_val, 1, speed_field)
            s_cpu = get_val(medium, b, mtu_val, 1, cpu_field)
            s_cost = (s_cpu / (s_speed / 100.0)) if s_speed > 5.0 else 0.0
            single_costs[k][b] = round(s_cost, 1)

            m_speed = get_val(medium, b, mtu_val, 8, speed_field)
            m_cpu = get_val(medium, b, mtu_val, 8, cpu_field)
            m_cost = (m_cpu / (m_speed / 100.0)) if m_speed > 5.0 else 0.0
            multi_costs[k][b] = round(m_cost, 1)

            max_c = max(max_c, s_cost, m_cost)

    render_efficiency_dashboard(
        os.path.join(output_dir, f"{prefix}cpu_efficiency_dashboard.webp"),
        "Processor Efficiency & Compute Cost",
        f"Standardized Metric: CPU % consumed per 100 Mbps (Multi-Core Cumulative: {dut_label} 8-Core Max: 800%)",
        eff_cats, single_costs, multi_costs, max_c
    )

    # 4. Loopback Dashboard
    loop_speeds = {"single": {}, "multi": {}}
    loop_cpus = {"single": {}, "multi": {}}
    max_l_speed = 10.0
    max_l_cpu = 10.0

    for b in BACKEND_ORDER:
        s_spd = get_val("On-Device Loopback", b, jumbo_mtu, 1, "speed_gbps")
        m_spd = get_val("On-Device Loopback", b, jumbo_mtu, 8, "speed_gbps")
        s_cpu = get_val("On-Device Loopback", b, jumbo_mtu, 1, "peak_cpu")
        m_cpu = get_val("On-Device Loopback", b, jumbo_mtu, 8, "peak_cpu")

        loop_speeds["single"][b] = s_spd
        loop_speeds["multi"][b] = m_spd
        loop_cpus["single"][b] = s_cpu
        loop_cpus["multi"][b] = m_cpu

        max_l_speed = max(max_l_speed, s_spd, m_spd)
        max_l_cpu = max(max_l_cpu, s_cpu, m_cpu)

    render_loopback_dashboard(
        os.path.join(output_dir, f"{prefix}loopback_dashboard.webp"),
        "On-Device Linux Loopback Processing Performance",
        "Zero Physical PHY Bottleneck: Testing Pure Userspace Stack & Event-Loop Architecture (CPU Max: 800%)",
        loop_speeds, loop_cpus, max_l_speed, max_l_cpu
    )

    # 5. Idle Flows Memory Dashboard (If available in records)
    has_idle = any("idle_flows" in r for r in records)
    if has_idle:
        tcp_idle = {b: [] for b in BACKEND_ORDER}
        udp_idle = {b: [] for b in BACKEND_ORDER}
        for r in records:
            b = r.get("backend")
            net = r.get("network", "tcp").lower()
            flows = r.get("idle_flows", [])
            if b in BACKEND_ORDER and flows:
                base_mem = flows[0].get("pss_mb", 0.0)
                pts = [(f.get("connections", 0), round(f.get("pss_mb", 0.0) - base_mem, 2)) for f in flows]
                if net == "udp":
                    udp_idle[b] = pts
                else:
                    tcp_idle[b] = pts

        render_idle_memory_dashboard(
            os.path.join(output_dir, f"{prefix}idle_memory_dashboard.webp"),
            "Retained Idle Connections vs Process Memory Growth",
            "SimpleXray Process Memory Growth (PSS MiB above baseline) across 0 -> 1000 Idle Connections",
            tcp_idle, udp_idle
        )

    # 6. UDP Packet Jitter Dashboard
    has_udp_jitter = any(r.get("network") == "udp" and ("upload_jitter_ms" in r or "download_jitter_ms" in r) for r in records)
    if has_udp_jitter:
        jitter_cats = [
            {"key": "p8_up", "label": "Multi Stream (P=8) Upload"},
            {"key": "p8_down", "label": "Multi Stream (P=8) Download"},
            {"key": "p1_up", "label": "Single Stream (P=1) Upload"},
            {"key": "p1_down", "label": "Single Stream (P=1) Download"},
        ]
        wifi_jitters = {k["key"]: {} for k in jitter_cats}
        usb_jitters = {k["key"]: {} for k in jitter_cats}
        wifi_base_jit = {}
        usb_base_jit = {}

        for r in records:
            if r.get("network") != "udp" or r.get("type") == "idle_memory":
                continue
            medium = r.get("medium", "")
            if "Wi-Fi" not in medium and "USB" not in medium:
                continue
            is_wifi = "Wi-Fi" in medium
            par_key = "p8" if r.get("parallel", 1) == 8 else "p1"
            b = r.get("backend")
            up_j = r.get("upload_jitter_ms", 0.0)
            down_j = r.get("download_jitter_ms", 0.0)

            target_jit = wifi_jitters if is_wifi else usb_jitters
            target_base = wifi_base_jit if is_wifi else usb_base_jit

            if b == "direct_none":
                target_base[f"{par_key}_up"] = up_j
                target_base[f"{par_key}_down"] = down_j
            elif b in BACKEND_ORDER:
                target_jit[f"{par_key}_up"][b] = up_j
                target_jit[f"{par_key}_down"][b] = down_j

        render_udp_jitter_dashboard(
            os.path.join(output_dir, f"{prefix}udp_jitter_dashboard.webp"),
            "UDP Packet Transmission Jitter (Lower is Better)",
            "Delay variation in milliseconds comparing 5GHz Wi Fi and USB 3.2 tethering",
            jitter_cats,
            wifi_jitters, usb_jitters,
            wifi_base_jit, usb_base_jit
        )


def main():
    parser = argparse.ArgumentParser(description="Generate benchmark dashboards (v2)")
    parser.add_argument(
        "--device",
        default=DEFAULT_DEVICE,
        choices=sorted(DEVICE_PROFILES),
        help=f"Device profile used for default JSON, microbench, and chart paths (default: {DEFAULT_DEVICE})",
    )
    parser.add_argument("--json", default=None, help="Path to benchmark_results.json (defaults to profile data dir)")
    parser.add_argument("--microbench", default=None, help="Path to microbench_results.json (defaults to profile data dir)")
    parser.add_argument("--output-dir", default=None, help="Path to output directory (defaults to profile charts dir)")
    parser.add_argument(
        "--dut",
        default=None,
        help="DUT/SoC label printed in chart subtitles (defaults to device profile name)",
    )
    args = parser.parse_args()

    profile = resolve_device_paths(args.device)
    args.json = args.json or profile["bench_json"]
    args.microbench = args.microbench or profile["microbench_json"]
    args.output_dir = args.output_dir or profile["charts_dir"]

    if not os.path.exists(args.json):
        print(f"Error: {args.json} not found.")
        sys.exit(1)

    dut_label = args.dut or infer_dut_label(args.json, args.output_dir)
    if dut_label == "DUT":
        dut_label = profile["name"]

    print(f"Loading results from {args.json}...")
    with open(args.json, "r", encoding="utf-8") as f:
        root = json.load(f)

    rounds = root.get("rounds", {})
    for r_name, records in rounds.items():
        r_num = r_name.replace("round_", "")
        prefix = f"r{r_num}_"
        print(f"Generating unified dashboards for {r_name} (prefix='{prefix}')...")
        process_dataset_and_generate_dashboards(
            records, args.output_dir, prefix=prefix, dut_label=dut_label
        )

    # Average dataset
    if rounds:
        avg_records = compute_clean_averages(root)

        print("Generating unified dashboards for average across all rounds...")
        process_dataset_and_generate_dashboards(
            avg_records, args.output_dir, prefix="avg_", dut_label=dut_label
        )
        process_dataset_and_generate_dashboards(
            avg_records, args.output_dir, prefix="", dut_label=dut_label
        )

        print("Generating Full-Stack Attribution Dashboard...")
        render_fullstack_attribution_dashboard(
            os.path.join(args.output_dir, "fullstack_attribution_dashboard.webp"),
            avg_records,
            args.microbench
        )

    print("All dashboards generated successfully.")


if __name__ == "__main__":
    main()
