#!/usr/bin/env python3
"""
Generate a standalone Scheme 2 microbenchmark memory-slope chart.
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
    PALETTE,
    setup_fonts,
    apply_global_theme,
    add_dashboard_header,
    save_dashboard,
)

BACKENDS = ["hev", "sing", "mips", "xray"]
NETWORKS = ["tcp", "udp"]
LABELS = {"hev": "HEV", "sing": "SingTUN", "mips": "MipsTUN", "xray": "Xray TUN"}


def average_slopes(micro_json: Dict) -> Dict[Tuple[str, str], float]:
    samples: Dict[Tuple[str, str], List[float]] = defaultdict(list)
    for round_items in micro_json.get("rounds", {}).values():
        for item in round_items:
            backend = str(item.get("backend", "")).lower()
            network = str(item.get("network", "")).lower()
            slope = item.get("slope_kib_per_conn")
            if backend in BACKENDS and network in NETWORKS and slope is not None:
                samples[(backend, network)].append(float(slope))
    return {k: round(sum(v) / len(v), 2) for k, v in samples.items() if v}


def main():
    parser = argparse.ArgumentParser(description="Generate Scheme 2 microbenchmark slope chart")
    parser.add_argument("--device", default=DEFAULT_DEVICE, choices=sorted(DEVICE_PROFILES))
    parser.add_argument("--output", default=None, help="Output WebP path")
    args = parser.parse_args()

    profile = resolve_device_paths(args.device)
    micro_json_path = profile["microbench_json"]
    output_path = args.output or os.path.join(profile["charts_dir"], "microbench_memory_slope.webp")

    if not os.path.exists(micro_json_path):
        raise SystemExit(f"Microbenchmark JSON not found: {micro_json_path}")

    with open(micro_json_path, "r", encoding="utf-8") as f:
        micro_json = json.load(f)

    slopes = average_slopes(micro_json)
    x = np.arange(len(BACKENDS))
    width = 0.34

    apply_global_theme()
    prop_regular, prop_bold, prop_medium = setup_fonts()

    fig = plt.figure(figsize=(16, 9.5), dpi=200)
    ax = fig.add_axes([0.10, 0.12, 0.86, 0.68])
    add_dashboard_header(
        fig,
        "Scheme 2 Standalone Stack Memory Slope",
        "Per-connection memory growth across pure user-space TUN stacks",
        prop_bold=prop_bold,
        prop_regular=prop_regular,
    )

    colors = {"tcp": PALETTE["hev"]["fill"], "udp": PALETTE["xray"]["fill"]}
    edges = {"tcp": PALETTE["hev"]["edge"], "udp": PALETTE["xray"]["edge"]}
    labels = {"tcp": "TCP", "udp": "UDP"}

    for idx, network in enumerate(NETWORKS):
        vals = [slopes.get((b, network), 0.0) for b in BACKENDS]
        offset = (idx - 0.5) * width
        bars = ax.bar(x + offset, vals, width, label=labels[network],
                      color=colors[network], edgecolor=edges[network], linewidth=0.8, zorder=3)
        for bar, val in zip(bars, vals):
            ax.text(
                bar.get_x() + bar.get_width() / 2,
                bar.get_height() + max(vals + [1.0]) * 0.02,
                f"{val:.2f}",
                ha="center",
                va="bottom",
                fontsize=8.5,
                color="#1e293b",
                fontproperties=prop_bold,
            )

    ax.set_ylabel("Memory Slope (KiB/conn)", fontsize=10, color="#475569",
                  fontproperties=prop_regular)
    ax.set_xticks(x)
    ax.set_xticklabels([LABELS[b] for b in BACKENDS], fontsize=10,
                       color="#334155", fontproperties=prop_medium)
    ax.tick_params(axis="y", labelsize=9, colors="#475569")
    # Use the themed matplotlib font for tick labels; explicit FontProperties can
    # be applied to axis labels above.
    ax.grid(True, axis="y", color="#f1f5f9", linestyle="-", linewidth=0.8, zorder=0)
    ax.legend(frameon=True, facecolor="#ffffff", edgecolor="#cbd5e1", fontsize=9,
              prop=prop_medium)

    save_dashboard(fig, output_path, dpi=200)
    print(f"[Microbench] Saved: {output_path}")


if __name__ == "__main__":
    main()
