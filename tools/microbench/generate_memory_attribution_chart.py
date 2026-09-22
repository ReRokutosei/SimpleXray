#!/usr/bin/env python3
"""
Compare Scheme 2 pure-stack memory slopes with Android end-to-end idle memory.
"""

import argparse
import json
import os
import sys
from collections import defaultdict
from typing import Dict, List, Tuple

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TOOLS_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
sys.path.insert(0, TOOLS_DIR)

from common.device import DEFAULT_DEVICE, DEVICE_PROFILES, resolve_device_paths
from common.theme import (
    setup_fonts,
    apply_global_theme,
    add_dashboard_header,
    create_top_legend,
    save_dashboard,
)

BACKENDS = ["hev", "sing", "xray"]
LABELS = {"hev": "HEV", "sing": "SingTUN", "xray": "Xray"}
NETWORKS = ["tcp", "udp"]


def average_micro(micro_json: Dict) -> Dict[Tuple[str, str], float]:
    samples: Dict[Tuple[str, str], List[float]] = defaultdict(list)
    for round_items in micro_json.get("rounds", {}).values():
        for rec in round_items:
            b = str(rec.get("backend", "")).lower()
            net = str(rec.get("network", "")).lower()
            slope = rec.get("slope_kib_per_conn")
            if b in BACKENDS and net in NETWORKS and slope is not None:
                samples[(b, net)].append(float(slope))
    return {k: sum(v) / len(v) for k, v in samples.items() if v}


def android_slopes(idle_json: Dict) -> Dict[Tuple[str, str], float]:
    out = {}
    for rec in idle_json.get("results", []):
        b = str(rec.get("backend", "")).lower()
        net = str(rec.get("network", "")).lower()
        if b in BACKENDS and net in NETWORKS:
            out[(b, net)] = float(rec.get("slope_kib_per_conn", 0.0))
    return out


def main():
    parser = argparse.ArgumentParser(description="Generate Scheme 2 vs Android idle memory chart")
    parser.add_argument("--device", default=DEFAULT_DEVICE, choices=sorted(DEVICE_PROFILES))
    parser.add_argument("--output", default=None)
    args = parser.parse_args()

    profile = resolve_device_paths(args.device)
    output_path = args.output or os.path.join(profile["charts_dir"], "memory_attribution_scheme2_vs_android.webp")

    with open(profile["microbench_json"], "r", encoding="utf-8") as f:
        micro = json.load(f)
    with open(profile["idle_json"], "r", encoding="utf-8") as f:
        idle = json.load(f)

    micro_avg = average_micro(micro)
    android_avg = android_slopes(idle)

    apply_global_theme()
    prop_regular, prop_bold, prop_medium = setup_fonts()

    fig = plt.figure(figsize=(16, 9.5), dpi=200)
    add_dashboard_header(
        fig,
        "Memory Slope Attribution (Lower is Better)",
        "Scheme 2 pure stack vs Android end-to-end idle memory (KiB/conn)",
        prop_bold=prop_bold,
        prop_regular=prop_regular,
    )

    axes = [
        fig.add_axes([0.08, 0.12, 0.40, 0.68]),
        fig.add_axes([0.55, 0.12, 0.40, 0.68]),
    ]
    y = np.arange(len(BACKENDS))
    height = 0.34

    c_pure = "#fdba74"
    c_android = "#ea580c"
    edge_pure = "#fb923c"
    edge_android = "#c2410c"

    for ax, network in zip(axes, NETWORKS):
        micro_vals = [micro_avg.get((b, network), 0.0) for b in BACKENDS]
        and_vals = [android_avg.get((b, network), 0.0) for b in BACKENDS]
        ax.barh(y + height / 2, micro_vals, height, label="Scheme 2 pure stack",
                color=c_pure, edgecolor=edge_pure, linewidth=0.8, zorder=3)
        ax.barh(y - height / 2, and_vals, height, label="Android end-to-end",
                color=c_android, edgecolor=edge_android, linewidth=0.8, zorder=3)

        xmax = max(micro_vals + and_vals + [1.0]) * 1.55
        xmin = min(0.0, min(micro_vals + and_vals))
        for idx, b in enumerate(BACKENDS):
            v_pure = micro_vals[idx]
            v_and = and_vals[idx]
            ax.text(
                max(0.0, v_pure) + xmax * 0.015, y[idx] + height / 2,
                f"{v_pure:.2f}", ha="left", va="center",
                fontsize=7.8, color="#9a3412", fontproperties=prop_bold,
            )
            note = ""
            if b == "sing":
                note = " (Go GC steady state)"
            elif b == "xray":
                note = " (main proc only)"
            ax.text(
                max(0.0, v_and) + xmax * 0.015, y[idx] - height / 2,
                f"{v_and:.2f}{note}", ha="left", va="center",
                fontsize=7.8, color="#7c2d12", fontproperties=prop_bold,
            )

        ax.set_title(network.upper(), fontsize=12, fontproperties=prop_bold, color="#1e293b")
        ax.set_xlabel("Memory Slope (KiB/conn)", fontsize=10, color="#475569",
                      fontproperties=prop_regular)
        ax.set_yticks(y)
        ax.set_yticklabels([LABELS[b] for b in BACKENDS], fontsize=10,
                           color="#334155", fontproperties=prop_medium)
        ax.set_xlim(xmin - xmax * 0.03, xmax)
        ax.grid(True, axis="x", color="#f1f5f9", linewidth=0.8, zorder=0)

    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor=c_pure, edgecolor=edge_pure),
        plt.Rectangle((0, 0), 1, 1, facecolor=c_android, edgecolor=edge_android),
    ]
    create_top_legend(
        fig,
        legend_handles,
        ["Scheme 2: Pure Stack Microbench", "Scheme 1: Android Host App PSS"],
        y_pos=0.895,
        prop_medium=prop_medium,
    )

    fig.text(
        0.5, 0.035,
        "Xray Android end-to-end slope reflects the main process only; gVisor cost lives in the child process.",
        ha="center", va="bottom", fontsize=8, color="#64748b", fontproperties=prop_regular,
    )

    save_dashboard(fig, output_path, dpi=200)
    print(f"[Memory Attribution] Saved: {output_path}")


if __name__ == "__main__":
    main()
