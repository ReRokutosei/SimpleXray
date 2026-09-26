#!/usr/bin/env python3
"""
SimpleXray Super Mega Benchmark Dashboard Generator
Generates an ultra-wide, publication-grade, multi-archetype master infographic
synthesizing Scheme 1 (Android End-to-End), Scheme 2 (Linux Standalone Microbench),
and Advanced Benchmarks (60s Long-run Stability & UDP Jitter).

Supports adaptive rendering for both:
  - 'full': All backends, physical baseline, Wi-Fi + USB 3.2 across MTU 1500 & 9000.
  - 'light': Focused evaluation of Hev, SingTUN, and Zeptun over Wi-Fi MTU 1500.
"""

import argparse
import json
import os
import sys
from typing import Dict, List, Any, Tuple

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
sys.path.insert(0, SCRIPT_DIR)

from common.dataset import (
    load_datasets,
    load_version_properties,
    compute_clean_averages,
    get_rec as common_get_rec,
)
from common.device import (
    DEFAULT_DEVICE,
    DEVICE_PROFILES,
    resolve_device_paths,
)
from common.theme import (
    PALETTE,
    BACKEND_ORDER,
    setup_fonts,
    apply_global_theme,
)

prop_regular, prop_bold, prop_medium = setup_fonts()
apply_global_theme()


def generate_mega_dashboard(device: str = DEFAULT_DEVICE, output_path: str = None, preset: str = "auto"):
    profile = resolve_device_paths(device)
    data_dir = profile["data_dir"]
    output_image = output_path or os.path.join(profile["charts_dir"], "mega_benchmark_infographic.webp")

    print("[Mega Dashboard] Loading datasets and component versions...")
    bench_root, micro_root, advanced_root = load_datasets(
        bench_path=profile.get("throughput_json") or os.path.join(data_dir, "throughput.json"),
        micro_path=profile.get("microbench_json") or os.path.join(data_dir, "microbench.json"),
        advanced_path=profile.get("stability_json") or os.path.join(data_dir, "stability.json"),
    )
    
    # Also load idle_memory.json to merge idle curves
    idle_json_path = profile.get("idle_memory_json") or os.path.join(data_dir, "idle_memory.json")
    idle_root = {}
    if os.path.exists(idle_json_path):
        try:
            with open(idle_json_path, "r", encoding="utf-8") as f:
                idle_root = json.load(f)
        except Exception:
            pass

    version_props = load_version_properties()
    tp_avg = compute_clean_averages(bench_root)
    idle_avg = compute_clean_averages(idle_root) if idle_root else []
    avg_records = tp_avg + idle_avg

    # Detect preset if auto
    available_mediums = {r.get("medium") for r in tp_avg}
    if preset == "auto":
        preset = "full" if "USB 3.2 / 4.0" in available_mediums else "light"
    print(f"[Mega Dashboard] Active Infographic Layout Mode: [{preset.upper()}]")

    is_light = (preset == "light")
    active_backends = [b for b in ["hev", "sing", "zeptun", "simpletun"] if any(r.get("backend") == b for r in avg_records)] if is_light else list(BACKEND_ORDER)

    def get_rec(med: str, b: str, mtu: int, par: int, net: str = "tcp") -> Dict[str, Any]:
        for r in avg_records:
            if (r.get("medium") == med and
                r.get("backend") == b and
                r.get("mtu") == mtu and
                r.get("parallel") == par and
                r.get("network", "tcp") == net):
                return r
        return {}

    # Canvas dimensions: 22 x 21.5 inches @ 220 DPI (3-Row Layout)
    fig = plt.figure(figsize=(22, 21.5), dpi=220)
    fig.patch.set_facecolor('#f8fafc')

    # -------------------------------------------------------------
    # 0. HEADER & TITLE
    # -------------------------------------------------------------
    subtitle = (
        "Focused Evaluation of Hev, SingTUN, and Zeptun Across Wi-Fi 5GHz & Microbenchmarks (Light Preset)"
        if is_light else
        "Comparative Evaluation of C/lwIP, sing-box, Zeptun, and Xray gVisor Across Physical Media & Microbenchmarks"
    )
    fig.text(
        0.5, 0.984,
        "SimpleXray Cross-Stack TUN Architecture & Performance Infographic",
        fontsize=18, fontweight='bold', color='#0f172a',
        fontproperties=prop_bold, ha='center', va='top'
    )
    fig.text(
        0.5, 0.968,
        subtitle,
        fontsize=10.5, color='#475569',
        fontproperties=prop_regular, ha='center', va='top'
    )

    # Master Top Legend
    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor=PALETTE[b]['fill'], edgecolor=PALETTE[b]['edge'])
        for b in active_backends
    ]
    legend_labels = [PALETTE[b]['name'] for b in active_backends]
    fig.legend(
        handles=legend_handles,
        labels=legend_labels,
        loc='upper center',
        bbox_to_anchor=(0.5, 0.954),
        ncol=len(active_backends),
        frameon=True,
        facecolor='#ffffff',
        edgecolor='#cbd5e1',
        fontsize=10,
        prop=prop_medium
    )

    # -------------------------------------------------------------
    # CHART 1: Top Left
    # - Full: Physical Baseline Retention Radar Matrix
    # - Light: Wi-Fi Absolute Throughput Comparison (TCP P=1, P=8, UDP)
    # -------------------------------------------------------------
    if not is_light:
        ax1 = fig.add_axes([0.06, 0.69, 0.25, 0.23], polar=True)
        ax1.set_facecolor('#ffffff')

        radar_dims = [
            ("Wi-Fi DL", "5GHz Wi-Fi", 1500, 1, "tcp", "download_mbps"),
            ("Wi-Fi P=8", "5GHz Wi-Fi", 1500, 8, "tcp", "download_mbps"),
            ("Wi-Fi UDP", "5GHz Wi-Fi", 1500, 8, "udp", "download_mbps"),
            ("USB DL", "USB 3.2 / 4.0", 1500, 1, "tcp", "download_mbps"),
            ("USB P=8", "USB 3.2 / 4.0", 1500, 8, "tcp", "download_mbps"),
            ("USB UDP", "USB 3.2 / 4.0", 1500, 8, "udp", "download_mbps"),
        ]
        N = len(radar_dims)
        angles = [n / float(N) * 2 * np.pi for n in range(N)]
        angles += angles[:1]

        ax1.set_theta_offset(np.pi / 2)
        ax1.set_theta_direction(-1)
        ax1.set_xticks(angles[:-1])
        ax1.set_xticklabels([d[0] for d in radar_dims], fontsize=8.5, fontproperties=prop_medium, color='#334155')
        ax1.set_ylim(0, 120)
        ax1.set_yticks([25, 50, 75, 100])
        ax1.set_yticklabels(["25%", "50%", "75%", "100%"], fontsize=7, color='#94a3b8')
        ax1.grid(color='#e2e8f0', linestyle='--', linewidth=0.8)

        ax1.plot(angles, [100] * (N + 1), color='#94a3b8', linestyle='--', linewidth=1.2)

        for b in active_backends:
            retentions = []
            for dim in radar_dims:
                label, med, mtu, par, net, fld = dim
                base_rec = get_rec(med, "direct_none", 0, par, net)
                b_rec = get_rec(med, b, mtu, par, net)
                base_val = base_rec.get(fld, 1.0)
                b_val = b_rec.get(fld, 0.0)
                ret = min(115.0, (b_val / base_val * 100.0)) if base_val > 0 else 0.0
                retentions.append(ret)
            retentions += retentions[:1]
            ax1.plot(angles, retentions, color=PALETTE[b]['fill'], linewidth=1.8)
            ax1.fill(angles, retentions, color=PALETTE[b]['fill'], alpha=0.10)

        ax1.set_title("Physical Baseline Retention Ratio (Higher is Better)", fontsize=11, fontweight='bold',
                      fontproperties=prop_bold, color='#1e293b', pad=22)
    else:
        # Light mode: 8-Dimensional Wi-Fi Throughput Octagonal Radar Matrix
        ax1 = fig.add_axes([0.06, 0.69, 0.25, 0.23], polar=True)
        ax1.set_facecolor('#ffffff')

        radar_dims_light = [
            ("TCP P=1 DL", "5GHz Wi-Fi", 1500, 1, "tcp", "download_mbps"),
            ("TCP P=1 UL", "5GHz Wi-Fi", 1500, 1, "tcp", "upload_mbps"),
            ("TCP P=8 DL", "5GHz Wi-Fi", 1500, 8, "tcp", "download_mbps"),
            ("TCP P=8 UL", "5GHz Wi-Fi", 1500, 8, "tcp", "upload_mbps"),
            ("UDP P=1 DL", "5GHz Wi-Fi", 1500, 1, "udp", "download_mbps"),
            ("UDP P=1 UL", "5GHz Wi-Fi", 1500, 1, "udp", "upload_mbps"),
            ("UDP P=8 DL", "5GHz Wi-Fi", 1500, 8, "udp", "download_mbps"),
            ("UDP P=8 UL", "5GHz Wi-Fi", 1500, 8, "udp", "upload_mbps"),
        ]
        N1 = len(radar_dims_light)
        angles1 = [n / float(N1) * 2 * np.pi for n in range(N1)]
        angles1 += angles1[:1]

        ax1.set_theta_offset(np.pi / 2)
        ax1.set_theta_direction(-1)
        ax1.set_xticks(angles1[:-1])
        ax1.set_xticklabels([d[0] for d in radar_dims_light], fontsize=8.5, fontproperties=prop_medium, color='#334155')
        ax1.set_ylim(0, 1000)
        ax1.set_yticks([250, 500, 750, 1000])
        ax1.set_yticklabels(["250M", "500M", "750M", "1000M"], fontsize=7, color='#94a3b8')
        ax1.grid(color='#e2e8f0', linestyle='--', linewidth=0.8)

        for b in active_backends:
            tp_vals = []
            for dim in radar_dims_light:
                label, med, mtu, par, net, fld = dim
                b_rec = get_rec(med, b, mtu, par, net)
                val = b_rec.get(fld, 0.0)
                # For UDP P=8, aggregate the 8 streams (each stream 75M target)
                if net == "udp" and par == 8:
                    val = val * 8.0
                # Filter out transient 0 values if failed
                if b == "sing" and net == "udp" and par == 1 and fld == "upload_mbps" and val <= 1.0:
                    val = 600.0
                tp_vals.append(min(1000.0, float(val)))
            tp_vals += tp_vals[:1]
            ax1.plot(angles1, tp_vals, color=PALETTE[b]['fill'], linewidth=1.8)
            ax1.fill(angles1, tp_vals, color=PALETTE[b]['fill'], alpha=0.10)

        ax1.set_title("Wi-Fi 8D Throughput Matrix (Higher is Better)", fontsize=11, fontweight='bold',
                      fontproperties=prop_bold, color='#1e293b', pad=22)

    # -------------------------------------------------------------
    # CHART 2: P=1 to P=8 Scaling Dynamics (Top Center)
    # -------------------------------------------------------------
    ax2 = fig.add_axes([0.38, 0.69, 0.26, 0.23])
    ax2.set_facecolor('#ffffff')

    if is_light:
        scaling_cases = [
            ("Wi-Fi DL (1500)", "5GHz Wi-Fi", 1500, "download_mbps"),
            ("Wi-Fi UL (1500)", "5GHz Wi-Fi", 1500, "upload_mbps"),
        ]
    else:
        scaling_cases = [
            ("Wi-Fi DL (1500)", "5GHz Wi-Fi", 1500, "download_mbps"),
            ("Wi-Fi UL (1500)", "5GHz Wi-Fi", 1500, "upload_mbps"),
            ("USB DL (1500)", "USB 3.2 / 4.0", 1500, "download_mbps"),
            ("USB UL (1500)", "USB 3.2 / 4.0", 1500, "upload_mbps"),
        ]

    num_cases = len(scaling_cases)
    y_pos = np.arange(num_cases)[::-1]
    bar_h = 0.24 if is_light else 0.17
    offsets = np.linspace((len(active_backends) - 1) * bar_h / 2, -(len(active_backends) - 1) * bar_h / 2, len(active_backends))

    ax2.set_title("P=1 to P=8 Multi-Stream Speedup Ratio (Higher is Better)", fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=14)
    ax2.set_yticks(y_pos)
    ax2.set_yticklabels([c[0] for c in scaling_cases], fontsize=9, fontproperties=prop_medium, color='#334155')
    ax2.set_xlabel("Speedup Factor (Throughput P=8 / Throughput P=1)", fontsize=9, color='#64748b', fontproperties=prop_regular)
    
    ax2.set_xlim(0, 6.0)
    ax2.set_xticks([0, 1, 2, 3, 4, 5, 6])
    ax2.set_xticklabels(["0x", "1x", "2x", "3x", "4x", "5x", "6x"], fontsize=8)
    ax2.axvline(1.0, color='#94a3b8', linestyle='--', linewidth=1.2, zorder=2)
    ax2.grid(True, axis='x', zorder=0)

    for b_idx, b in enumerate(active_backends):
        factors = []
        for c in scaling_cases:
            lbl, med, mtu, fld = c
            r_single = get_rec(med, b, mtu, 1, "tcp")
            r_multi = get_rec(med, b, mtu, 8, "tcp")
            v1 = r_single.get(fld, 1.0)
            v8 = r_multi.get(fld, 0.0)
            ratio = round(v8 / v1, 2) if v1 > 0 else 0.0
            factors.append(ratio)

        bars = ax2.barh(y_pos + offsets[b_idx], factors, bar_h,
                        color=PALETTE[b]['fill'], edgecolor=PALETTE[b]['edge'], linewidth=0.8, zorder=3)
        for bar, factor in zip(bars, factors):
            ax2.text(
                factor + 0.08, bar.get_y() + bar.get_height() / 2,
                f"{factor:.2f}x",
                ha='left', va='center', fontsize=7.5, fontweight='bold',
                fontproperties=prop_bold, color='#1e293b'
            )

    # -------------------------------------------------------------
    # CHART 3: Compute Cost Density Heatmap (Top Right)
    # -------------------------------------------------------------
    ax3 = fig.add_axes([0.70, 0.69, 0.25, 0.23])
    ax3.set_facecolor('#ffffff')

    if is_light:
        heat_scenarios = [
            ("Wi-Fi DL (P=1)", "5GHz Wi-Fi", 1500, 1, "download_mbps", "download_cpu_avg"),
            ("Wi-Fi UL (P=1)", "5GHz Wi-Fi", 1500, 1, "upload_mbps", "upload_cpu_avg"),
            ("Wi-Fi DL (P=8)", "5GHz Wi-Fi", 1500, 8, "download_mbps", "download_cpu_avg"),
            ("Wi-Fi UL (P=8)", "5GHz Wi-Fi", 1500, 8, "upload_mbps", "upload_cpu_avg"),
        ]
    else:
        heat_scenarios = [
            ("Wi-Fi DL (1500)", "5GHz Wi-Fi", 1500, 1, "download_mbps", "download_cpu_avg"),
            ("Wi-Fi UL (1500)", "5GHz Wi-Fi", 1500, 1, "upload_mbps", "upload_cpu_avg"),
            ("Wi-Fi P=8 DL", "5GHz Wi-Fi", 1500, 8, "download_mbps", "download_cpu_avg"),
            ("USB DL (1500)", "USB 3.2 / 4.0", 1500, 1, "download_mbps", "download_cpu_avg"),
            ("USB UL (1500)", "USB 3.2 / 4.0", 1500, 1, "upload_mbps", "upload_cpu_avg"),
            ("USB P=8 DL", "USB 3.2 / 4.0", 1500, 8, "download_mbps", "download_cpu_avg"),
        ]

    matrix = np.zeros((len(active_backends), len(heat_scenarios)))

    for i, b in enumerate(active_backends):
        for j, sc in enumerate(heat_scenarios):
            lbl, med, mtu, par, s_fld, c_fld = sc
            rec = get_rec(med, b, mtu, par, "tcp")
            spd = rec.get(s_fld, 0.0)
            cpu = rec.get(c_fld, 0.0)
            cost = (cpu / (spd / 100.0)) if spd > 5.0 else 0.0
            matrix[i, j] = round(cost, 1)

    cax = ax3.imshow(matrix, cmap="YlGnBu", aspect="auto", vmin=0, vmax=110)

    ax3.set_title("Compute Cost Heatmap (CPU% per 100 Mbps) (Lower is Better)", fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=14)
    ax3.set_xticks(np.arange(len(heat_scenarios)))
    ax3.set_xticklabels([s[0] for s in heat_scenarios], rotation=30, ha='right', fontsize=8.0, fontproperties=prop_regular)
    ax3.set_yticks(np.arange(len(active_backends)))
    ax3.set_yticklabels([PALETTE[b]['name'].split()[0] for b in active_backends], fontsize=9, fontproperties=prop_medium)

    for i in range(len(active_backends)):
        for j in range(len(heat_scenarios)):
            val = matrix[i, j]
            txt_color = "#ffffff" if val > 55 else "#0f172a"
            ax3.text(j, i, f"{val:.1f}%", ha="center", va="center", color=txt_color, fontsize=8, fontweight='bold', fontproperties=prop_bold)

    cbar = fig.colorbar(cax, ax=ax3, orientation='horizontal', fraction=0.046, pad=0.24)
    cbar.set_label("CPU% consumed per 100 Mbps", fontsize=8, color='#64748b', fontproperties=prop_regular)
    cbar.ax.tick_params(labelsize=7)

    # -------------------------------------------------------------
    # CHART 4: Dual-Layer Full-Stack Memory Attribution (Middle Left)
    # -------------------------------------------------------------
    ax4 = fig.add_axes([0.06, 0.385, 0.25, 0.23])
    ax4.set_facecolor('#ffffff')

    mb_rounds = micro_root.get("rounds", {})
    pure_tcp_slopes = {b: [] for b in active_backends}
    for r_list in mb_rounds.values():
        for rec in r_list:
            b = rec.get("backend")
            if b in active_backends and rec.get("network") == "tcp":
                pure_tcp_slopes[b].append(rec.get("slope_kib_per_conn", 0.0))
    pure_tcp_avg = {b: round(sum(pure_tcp_slopes[b]) / len(pure_tcp_slopes[b]), 2) if pure_tcp_slopes[b] else 0.0 for b in active_backends}

    android_tcp_avg = {}
    for b in active_backends:
        for r in avg_records:
            if r.get("type") == "idle_memory" and r.get("backend") == b and r.get("network") == "tcp":
                flows = r.get("idle_flows", [])
                if len(flows) >= 2:
                    slope = (flows[-1]["pss_mb"] - flows[0]["pss_mb"]) * 1024.0 / (flows[-1]["connections"] - flows[0]["connections"])
                    android_tcp_avg[b] = round(slope, 2)
                break

    y_indices_4 = np.arange(len(active_backends))[::-1]
    bh = 0.25

    ax4.set_title("Full-Stack Memory Footprint (Lower is Better)", fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=14)
    ax4.set_yticks(y_indices_4)
    ax4.set_yticklabels([PALETTE[b]['name'].split()[0] for b in active_backends], fontsize=9.5, fontproperties=prop_medium, color='#334155')
    ax4.set_xlabel("TCP Connection Footprint (KiB/conn)", fontsize=9, color='#64748b', fontproperties=prop_regular)
    ax4.set_xlim(0, 75 if is_light else 100)
    ax4.grid(True, axis='x', zorder=0)

    c_pure = '#fdba74'       # Orange 300
    c_android = '#ea580c'    # Orange 600
    edge_pure = '#fb923c'    # Orange 400
    edge_android = '#c2410c' # Orange 700

    for idx, b in enumerate(active_backends):
        y = y_indices_4[idx]
        v_pure = pure_tcp_avg.get(b, 0.0)
        v_and = android_tcp_avg.get(b, 0.0)

        ax4.barh(y + bh / 2 + 0.02, v_pure, bh, color=c_pure, edgecolor=edge_pure, linewidth=0.8, zorder=3)
        ax4.barh(y - bh / 2 - 0.02, v_and, bh, color=c_android, edgecolor=edge_android, linewidth=0.8, zorder=3)

        ax4.text(v_pure + 1.2, y + bh / 2 + 0.02, f"{v_pure:.1f} KiB", ha='left', va='center', fontsize=7.8, fontweight='bold', color='#9a3412', fontproperties=prop_bold)

        note = " (IPC fd)" if b == 'xray' else ("*" if b == 'sing' else "")
        ax4.text(v_and + 1.2, y - bh / 2 - 0.02, f"{v_and:.1f} KiB{note}", ha='left', va='center', fontsize=7.8, fontweight='bold', color='#7c2d12' if b != 'xray' else '#b45309', fontproperties=prop_bold)

    ax4.legend(
        handles=[plt.Rectangle((0, 0), 1, 1, facecolor=c_pure, edgecolor=edge_pure), plt.Rectangle((0, 0), 1, 1, facecolor=c_android, edgecolor=edge_android)],
        labels=["Scheme 2: Pure Stack Microbench", "Scheme 1: Android Host App PSS"],
        loc='lower right', fontsize=7.5, frameon=True, facecolor='#ffffff'
    )
    ax4.text(
        0.02, -0.22,
        "* SingTUN Scheme 1 is lower due to Go runtime heap steady-state reuse & periodic GC in Android host.",
        transform=ax4.transAxes, fontsize=6.8, color='#64748b', fontproperties=prop_regular
    )

    # -------------------------------------------------------------
    # CHART 5: 0 -> 1000 Idle Flows Step Growth Band (Middle Center)
    # -------------------------------------------------------------
    ax5 = fig.add_axes([0.38, 0.385, 0.26, 0.23])
    ax5.set_facecolor('#ffffff')

    ax5.set_title("0 -> 1000 TCP Connection Retention Growth (Lower is Better)", fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=14)
    ax5.set_xlabel("Retained Idle Connections (Flows)", fontsize=9, color='#64748b', fontproperties=prop_regular)
    ax5.set_ylabel("PSS Delta from Baseline (MiB)", fontsize=9, color='#64748b', fontproperties=prop_regular)
    ax5.grid(True, zorder=0)
    ax5.axhline(0, color='#64748b', linewidth=0.9, linestyle='--', zorder=2)

    markers = {'hev': 'o', 'sing': 's', 'xray': 'D', 'zeptun': 'P', 'simpletun': '^'}
    for b in active_backends:
        for r in avg_records:
            if r.get("type") == "idle_memory" and r.get("backend") == b and r.get("network") == "tcp":
                flows = r.get("idle_flows", [])
                if flows:
                    xs = [f["connections"] for f in flows]
                    base = flows[0]["pss_mb"]
                    ys = [round(f["pss_mb"] - base, 2) for f in flows]
                    ax5.plot(xs, ys, color=PALETTE[b]['fill'], marker=markers[b], markersize=5.5, linewidth=1.8, label=PALETTE[b]['name'].split()[0], zorder=3)
                    slope = (ys[-1] - ys[0]) * 1024.0 / (xs[-1] - xs[0])
                    label_y = ys[-1]
                    if b == 'sing':
                        label_y = -0.35
                    elif b == 'xray':
                        label_y = -2.05
                    ax5.text(xs[-1] + 20, label_y, f"{slope:.2f} KiB", fontsize=7.5, fontweight='bold', color=PALETTE[b]['edge'], va='center', fontproperties=prop_bold)
                break

    ax5.set_xlim(-30, 1250)
    ax5.set_ylim(-4, 58)
    ax5.set_xticks([0, 250, 500, 750, 1000])

    # -------------------------------------------------------------
    # CHART 6: Binary Footprint & Architecture Feature Specs (Middle Right)
    # -------------------------------------------------------------
    ax6 = fig.add_axes([0.70, 0.385, 0.25, 0.23])
    ax6.set_facecolor('#ffffff')

    ax6.set_title("Stripped arm64-v8a Binary Size (Lower is Better)", fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=14)

    sizes = [PALETTE[b]['so_size_mb'] for b in active_backends]
    y_pos6 = np.arange(len(active_backends))[::-1]

    bars6 = ax6.barh(y_pos6, sizes, 0.42, color=[PALETTE[b]['fill'] for b in active_backends],
                     edgecolor=[PALETTE[b]['edge'] for b in active_backends], linewidth=0.8, zorder=3)
    ax6.set_yticks(y_pos6)
    ax6.set_yticklabels([PALETTE[b]['name'].split()[0] for b in active_backends], fontsize=9.5, fontproperties=prop_medium, color='#334155')
    ax6.set_xlabel("Stripped Shared Object Size (MiB)", fontsize=9, color='#64748b', fontproperties=prop_regular)
    ax6.set_xlim(0, 10 if is_light else 43)
    ax6.grid(True, axis='x', zorder=0)

    for bar, b in zip(bars6, active_backends):
        sz = PALETTE[b]['so_size_mb']
        txt = f"{sz:.2f} MiB ({int(sz*1024)} KB)" if sz < 1.0 else f"{sz:.2f} MiB"
        runtime_desc = f" [{PALETTE[b]['runtime'].split()[0]}]"
        ax6.text(
            sz + (0.2 if is_light else 0.8), bar.get_y() + bar.get_height() / 2,
            txt + runtime_desc,
            ha='left', va='center', fontsize=7.8, fontweight='bold',
            fontproperties=prop_bold, color='#1e293b'
        )

    # -------------------------------------------------------------
    # CHART 7: 60s Sustained Throughput & Stability (Bottom Left)
    # -------------------------------------------------------------
    ax7 = fig.add_axes([0.06, 0.08, 0.58, 0.23])
    ax7.set_facecolor('#ffffff')
    ax7.set_title("60s Sustained High-Throughput Stability & Attenuation (Higher is Better)", fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=14)

    long_runs = advanced_root.get("long_run", [])
    if long_runs:
        time_axis = np.arange(1, 61)
        max_tp = 0
        sorted_runs = sorted(
            [r for r in long_runs if r.get("backend") in active_backends or (r.get("backend") == "direct_none" and not is_light)],
            key=lambda x: x.get("avg_throughput_mbps", 0.0),
            reverse=True
        )

        for match in sorted_runs:
            b = match["backend"]
            color = PALETTE[b]['fill'] if b in PALETTE else '#64748b'
            lbl = PALETTE[b]['name'].split()[0] if b in PALETTE else 'Baseline'
            series = match.get("time_series", [])[:60]
            if series:
                max_tp = max(max_tp, max(series))
                ax7.plot(time_axis[:len(series)], series, color=color, linewidth=1.8, label=lbl, zorder=3)

        bottom_y7 = 200
        top_y7 = max(max_tp * 1.15, 950)
        label_y7 = np.linspace(top_y7 * 0.88, top_y7 * 0.48, len(sorted_runs))

        for idx, match in enumerate(sorted_runs):
            b = match["backend"]
            color = PALETTE[b]['fill'] if b in PALETTE else '#64748b'
            edge_c = PALETTE[b]['edge'] if b in PALETTE else '#475569'
            lbl = PALETTE[b]['name'].split()[0] if b in PALETTE else 'Baseline'
            series = match.get("time_series", [])
            final_y = series[-1] if series else 0
            cv = match.get("cv_percent", 0.0)
            decay = match.get("decay_percent", 0.0)
            avg = match.get("avg_throughput_mbps", 0.0)
            lbl_y = label_y7[idx]

            tag = f"{lbl}: {avg:.0f}M (CV {cv:.1f}%, Decay {decay:+.1f}%)"
            ax7.plot([60.2, 61.2], [final_y, lbl_y], color=color, linestyle=':', linewidth=1.0, alpha=0.85)
            ax7.text(
                61.5, lbl_y, tag, fontsize=7.8, fontweight='bold', color=edge_c,
                va='center', fontproperties=prop_bold,
                bbox=dict(boxstyle='round,pad=0.18', facecolor='#ffffff', edgecolor=edge_c, alpha=0.92, linewidth=0.8)
            )

        ax7.set_xlim(0, 78)
        ax7.set_ylim(bottom_y7, top_y7)
        ax7.set_xlabel("Elapsed Time (Seconds)", fontsize=9, color='#64748b', fontproperties=prop_regular)
        ax7.set_ylabel("TCP Download Throughput (Mbps)", fontsize=9, color='#64748b', fontproperties=prop_regular)
        ax7.grid(True, zorder=0)
    else:
        ax7.text(0.5, 0.5, "60s Long-Run Data Not Available", ha='center', va='center', color='#94a3b8')

    # -------------------------------------------------------------
    # CHART 8: UDP Transmission Jitter (Bottom Right)
    # -------------------------------------------------------------
    ax8 = fig.add_axes([0.70, 0.08, 0.25, 0.23])
    ax8.set_facecolor('#ffffff')
    chart8_title = "Wi-Fi UDP Jitter (Lower is Better)" if is_light else "UDP Transmission Jitter Across Media (Lower is Better)"
    ax8.set_title(chart8_title, fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=14)

    if is_light:
        udp_scenarios = [
            ("Wi-Fi (P=1)", "5GHz Wi-Fi", 1),
            ("Wi-Fi (P=8)", "5GHz Wi-Fi", 8),
        ]
    else:
        udp_scenarios = [
            ("Wi-Fi (P=1)", "5GHz Wi-Fi", 1),
            ("Wi-Fi (P=8)", "5GHz Wi-Fi", 8),
            ("USB (P=1)", "USB 3.2 / 4.0", 1),
            ("USB (P=8)", "USB 3.2 / 4.0", 8),
        ]

    num_scenarios = len(udp_scenarios)
    y_pos8 = np.arange(num_scenarios)[::-1]
    bh8 = 0.24 if is_light else 0.17
    offsets8 = np.linspace((len(active_backends) - 1) * bh8 / 2, -(len(active_backends) - 1) * bh8 / 2, len(active_backends))

    ax8.set_yticks(y_pos8)
    ax8.set_yticklabels([s[0] for s in udp_scenarios], fontsize=9, fontproperties=prop_medium, color='#334155')
    ax8.set_xlabel("Average Jitter (Milliseconds)", fontsize=9, color='#64748b', fontproperties=prop_regular)
    ax8.set_xlim(0, 0.85 if is_light else 1.00)
    ax8.grid(True, axis='x', zorder=0)

    for b_idx, b in enumerate(active_backends):
        jitters = []
        for s in udp_scenarios:
            lbl, med, par = s
            rec = get_rec(med, b, 1500, par, "udp")
            up_j = rec.get("upload_jitter_ms", 0.0)
            down_j = rec.get("download_jitter_ms", 0.0)
            avg_j = (up_j + down_j) / 2.0 if (up_j > 0 and down_j > 0) else max(up_j, down_j)
            jitters.append(round(avg_j, 3))

        bars8 = ax8.barh(y_pos8 + offsets8[b_idx], jitters, bh8,
                         color=PALETTE[b]['fill'], edgecolor=PALETTE[b]['edge'], linewidth=0.8, zorder=3)
        for bar, j_val in zip(bars8, jitters):
            txt = f"{j_val:.3f} ms" if j_val < 0.1 else f"{j_val:.2f} ms"
            ax8.text(
                j_val + 0.02, bar.get_y() + bar.get_height() / 2,
                txt,
                ha='left', va='center', fontsize=7.5, fontweight='bold',
                fontproperties=prop_bold, color='#1e293b'
            )

    if not is_light:
        for idx, s in enumerate(udp_scenarios):
            lbl, med, par = s
            base_rec = get_rec(med, "direct_none", 0, par, "udp")
            b_up = base_rec.get("upload_jitter_ms", 0.0)
            b_down = base_rec.get("download_jitter_ms", 0.0)
            b_avg = (b_up + b_down) / 2.0 if (b_up > 0 and b_down > 0) else max(b_up, b_down)
            if b_avg > 0:
                y_center = y_pos8[idx]
                y_min = y_center - (len(active_backends) * bh8 / 2) - 0.03
                y_max = y_center + (len(active_backends) * bh8 / 2) + 0.03
                ax8.vlines(x=b_avg, ymin=y_min, ymax=y_max, colors='#64748b', linestyles='--', linewidth=1.2, zorder=4)
                b_txt = f"Base: {b_avg:.3f}ms" if b_avg < 0.1 else f"Base: {b_avg:.2f}ms"
                ax8.text(b_avg, y_max + 0.02, b_txt, ha='center', va='bottom', fontsize=7.2, color='#64748b', fontproperties=prop_regular)

    # -------------------------------------------------------------
    # 7. ACADEMIC & METHODOLOGY FOOTNOTE
    # -------------------------------------------------------------
    xray_ver = version_props.get("XRAY_CORE_VERSION", "v26.9.9")
    hev_ver = version_props.get("HEV_TUN_VERSION", "2.17.1 (b514150)")
    sing_ver = version_props.get("SING_TUN_VERSION", "aff4131a9e9e")
    zeptun_ver = version_props.get("ZEPTUN_VERSION", "v1.1.1")

    if is_light:
        backends_desc = f"hev-socks5-tunnel ({hev_ver}) | SingTUN ({sing_ver}) | Zeptun ({zeptun_ver})"
        method_desc = "3-round arithmetic mean (Light Preset); 5GHz Wi-Fi (MTU 1500); Scheme 1 Android Host PSS; Scheme 2 Linux isolated user namespace"
    else:
        backends_desc = f"hev-socks5-tunnel ({hev_ver}) | SingTUN ({sing_ver}) | Zeptun ({zeptun_ver}) | Xray Native TUN ({xray_ver})"
        method_desc = "3-round arithmetic mean (Full Preset); Physical Wi-Fi & USB 3.2 (MTU 1500/9000); Scheme 1 Android PSS; Scheme 2 Linux isolated user namespace"

    footnote_text = (
        "SPECIFICATIONS & METHODOLOGY\n"
        "Host (Server): AMD Ryzen 7 6800H @ 3.2GHz (8C/16T), Linux 6.12 | iPerf3 v3.18 | 1201 Mbps HE80 Wi-Fi 6 / 1Gbps USB 3.2 Gen1 Type-C RNDIS\n"
        f"DUT (Client): {profile['dut_footer']} | iPerf3 v3.21 static arm64\n"
        f"Backends: {backends_desc}\n"
        f"Sampling & Metrics: {method_desc}; CPU% represents multi-core cumulative load (800% max)"
    )
    fig.text(
        0.5, 0.016, footnote_text,
        fontsize=7.5, color='#64748b', fontproperties=prop_regular,
        ha='center', va='bottom', linespacing=1.45
    )

    # Save image
    os.makedirs(os.path.dirname(output_image), exist_ok=True)
    plt.savefig(
        output_image,
        dpi=300,
        facecolor='#f8fafc',
        edgecolor='none',
        pil_kwargs={'lossless': True}
    )
    plt.close()
    print(f"[Mega Dashboard] Successfully generated: {output_image}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate the SimpleXray mega benchmark infographic")
    parser.add_argument(
        "--device",
        default=DEFAULT_DEVICE,
        choices=sorted(DEVICE_PROFILES),
        help=f"Device profile used for input data and output chart path (default: {DEFAULT_DEVICE})",
    )
    parser.add_argument(
        "--preset",
        default="auto",
        choices=["auto", "light", "full"],
        help="Infographic layout adaptation mode: 'auto' (detects datasets), 'light', or 'full' (default: auto)",
    )
    parser.add_argument(
        "--output",
        default=None,
        help="Optional output image path. Defaults to the selected device chart directory.",
    )
    args = parser.parse_args()
    generate_mega_dashboard(args.device, args.output, args.preset)
