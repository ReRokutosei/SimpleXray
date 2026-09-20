#!/usr/bin/env python3
"""
SimpleXray Super Mega Benchmark Dashboard Generator
Generates an ultra-wide, publication-grade, multi-archetype master infographic
synthesizing Scheme 1 (Android End-to-End), Scheme 2 (Linux Standalone Microbench),
and Advanced Benchmarks (60s Long-run Stability & UDP Jitter).

Design Language & Standards:
  - Clean, restrained, high-density scientific aesthetics (inspired by tailscale/mundotunnel).
  - Modern light theme (#f8fafc canvas, pure #ffffff chart cards, slate borders).
  - 8 Visualization Archetypes (3 Rows):
      Row 1:
        1. Physical Baseline Retention Radar Matrix
        2. Multi-Stream (P=1 -> P=8) Scaling Speedup Ratio
        3. Compute Cost (CPU% per 100 Mbps) Density Heatmap
      Row 2:
        4. Dual-Layer Memory Attribution (Microbench Stack vs Android App PSS)
        5. Step 0 -> 1000 Idle Flows Memory Growth Band
        6. Architecture Specs & Stripped arm64-v8a Shared Binary Footprint
      Row 3:
        7. 60s Sustained High-Throughput Stability & Attenuation Time-Series
        8. UDP Transmission Jitter Across Media (Single Stream & Multi-Stream)
  - Standardized font: JetBrains Mono (monospace precision).
  - Automatic version extraction from version.properties.
"""

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
from common.theme import (
    PALETTE,
    BACKEND_ORDER,
    setup_fonts,
    apply_global_theme,
)

OUTPUT_MEGA_IMAGE = os.path.join(PROJECT_ROOT, "docs", "images", "mega_benchmark_infographic.webp")

prop_regular, prop_bold, prop_medium = setup_fonts()
apply_global_theme()


def generate_mega_dashboard():
    print("[Mega Dashboard] Loading datasets and component versions...")
    bench_root, micro_root, advanced_root = load_datasets()
    version_props = load_version_properties()
    avg_records = compute_clean_averages(bench_root)

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
    fig.text(
        0.5, 0.984,
        "SimpleXray Cross-Stack TUN Architecture & Performance Infographic",
        fontsize=18, fontweight='bold', color='#0f172a',
        fontproperties=prop_bold, ha='center', va='top'
    )
    fig.text(
        0.5, 0.968,
        "Comparative Evaluation of C/lwIP, sing-box, Mihomo mipstack, and Xray gVisor Across Physical Media & Microbenchmarks",
        fontsize=10.5, color='#475569',
        fontproperties=prop_regular, ha='center', va='top'
    )

    # Master Top Legend
    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor=PALETTE[b]['fill'], edgecolor=PALETTE[b]['edge'])
        for b in BACKEND_ORDER
    ]
    legend_labels = [PALETTE[b]['name'] for b in BACKEND_ORDER]
    fig.legend(
        handles=legend_handles,
        labels=legend_labels,
        loc='upper center',
        bbox_to_anchor=(0.5, 0.954),
        ncol=4,
        frameon=True,
        facecolor='#ffffff',
        edgecolor='#cbd5e1',
        fontsize=10,
        prop=prop_medium
    )

    # Geometry Layout:
    # Row 1: y = [0.69, 0.92] (height = 0.23)
    # Row 2: y = [0.385, 0.615] (height = 0.23)
    # Row 3: y = [0.08, 0.31] (height = 0.23)
    # Footnote: y = 0.020

    # -------------------------------------------------------------
    # CHART 1: Physical Baseline Retention Radar Matrix (Top Left)
    # -------------------------------------------------------------
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

    for b in BACKEND_ORDER:
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

    # -------------------------------------------------------------
    # CHART 2: P=1 to P=8 Scaling Dynamics (Top Center)
    # -------------------------------------------------------------
    ax2 = fig.add_axes([0.38, 0.69, 0.26, 0.23])
    ax2.set_facecolor('#ffffff')

    scaling_cases = [
        ("Wi-Fi DL (1500)", "5GHz Wi-Fi", 1500, "download_mbps"),
        ("Wi-Fi UL (1500)", "5GHz Wi-Fi", 1500, "upload_mbps"),
        ("USB DL (1500)", "USB 3.2 / 4.0", 1500, "download_mbps"),
        ("USB UL (1500)", "USB 3.2 / 4.0", 1500, "upload_mbps"),
    ]
    num_cases = len(scaling_cases)
    y_pos = np.arange(num_cases)[::-1]
    bar_h = 0.17
    offsets = np.linspace((len(BACKEND_ORDER) - 1) * bar_h / 2, -(len(BACKEND_ORDER) - 1) * bar_h / 2, len(BACKEND_ORDER))

    ax2.set_title("P=1 to P=8 Multi-Stream Speedup Ratio (Higher is Better)", fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=14)
    ax2.set_yticks(y_pos)
    ax2.set_yticklabels([c[0] for c in scaling_cases], fontsize=9, fontproperties=prop_medium, color='#334155')
    ax2.set_xlabel("Speedup Factor (Throughput P=8 / Throughput P=1)", fontsize=9, color='#64748b', fontproperties=prop_regular)
    
    # Extended to 5.2 to comfortably house SingTUN 4.35x speedup without overflow
    ax2.set_xlim(0, 5.2)
    ax2.set_xticks([0, 1, 2, 3, 4, 5])
    ax2.set_xticklabels(["0x", "1x", "2x", "3x", "4x", "5x"], fontsize=8)
    ax2.axvline(1.0, color='#94a3b8', linestyle='--', linewidth=1.2, zorder=2)
    ax2.grid(True, axis='x', zorder=0)

    for b_idx, b in enumerate(BACKEND_ORDER):
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

    heat_scenarios = [
        ("Wi-Fi DL (1500)", "5GHz Wi-Fi", 1500, 1, "download_mbps", "download_cpu_avg"),
        ("Wi-Fi UL (1500)", "5GHz Wi-Fi", 1500, 1, "upload_mbps", "upload_cpu_avg"),
        ("Wi-Fi P=8 DL", "5GHz Wi-Fi", 1500, 8, "download_mbps", "download_cpu_avg"),
        ("USB DL (1500)", "USB 3.2 / 4.0", 1500, 1, "download_mbps", "download_cpu_avg"),
        ("USB UL (1500)", "USB 3.2 / 4.0", 1500, 1, "upload_mbps", "upload_cpu_avg"),
        ("USB P=8 DL", "USB 3.2 / 4.0", 1500, 8, "download_mbps", "download_cpu_avg"),
    ]
    matrix = np.zeros((len(BACKEND_ORDER), len(heat_scenarios)))

    for i, b in enumerate(BACKEND_ORDER):
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
    ax3.set_xticklabels([s[0] for s in heat_scenarios], rotation=35, ha='right', fontsize=7.8, fontproperties=prop_regular)
    ax3.set_yticks(np.arange(len(BACKEND_ORDER)))
    ax3.set_yticklabels([PALETTE[b]['name'].split()[0] for b in BACKEND_ORDER], fontsize=9, fontproperties=prop_medium)

    for i in range(len(BACKEND_ORDER)):
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
    pure_tcp_slopes = {b: [] for b in BACKEND_ORDER}
    for r_list in mb_rounds.values():
        for rec in r_list:
            b = rec.get("backend")
            if b in BACKEND_ORDER and rec.get("network") == "tcp":
                pure_tcp_slopes[b].append(rec.get("slope_kib_per_conn", 0.0))
    pure_tcp_avg = {b: round(sum(pure_tcp_slopes[b]) / len(pure_tcp_slopes[b]), 2) if pure_tcp_slopes[b] else 0.0 for b in BACKEND_ORDER}

    android_tcp_avg = {}
    for b in BACKEND_ORDER:
        for r in avg_records:
            if r.get("type") == "idle_memory" and r.get("backend") == b and r.get("network") == "tcp":
                flows = r.get("idle_flows", [])
                if len(flows) >= 2:
                    slope = (flows[-1]["pss_mb"] - flows[0]["pss_mb"]) * 1024.0 / (flows[-1]["connections"] - flows[0]["connections"])
                    android_tcp_avg[b] = round(slope, 2)
                break

    y_indices_4 = np.arange(len(BACKEND_ORDER))[::-1]
    bh = 0.25

    ax4.set_title("Full-Stack Memory Footprint (Lower is Better)", fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=14)
    ax4.set_yticks(y_indices_4)
    ax4.set_yticklabels([PALETTE[b]['name'].split()[0] for b in BACKEND_ORDER], fontsize=9.5, fontproperties=prop_medium, color='#334155')
    ax4.set_xlabel("TCP Connection Footprint (KiB/conn)", fontsize=9, color='#64748b', fontproperties=prop_regular)
    ax4.set_xlim(0, 100)
    ax4.grid(True, axis='x', zorder=0)

    c_pure = '#fdba74'       # Orange 300
    c_android = '#ea580c'    # Orange 600
    edge_pure = '#fb923c'    # Orange 400
    edge_android = '#c2410c' # Orange 700

    for idx, b in enumerate(BACKEND_ORDER):
        y = y_indices_4[idx]
        v_pure = pure_tcp_avg.get(b, 0.0)
        v_and = android_tcp_avg.get(b, 0.0)

        ax4.barh(y + bh / 2 + 0.02, v_pure, bh, color=c_pure, edgecolor=edge_pure, linewidth=0.8, zorder=3)
        ax4.barh(y - bh / 2 - 0.02, v_and, bh, color=c_android, edgecolor=edge_android, linewidth=0.8, zorder=3)

        ax4.text(v_pure + 1.5, y + bh / 2 + 0.02, f"{v_pure:.1f} KiB", ha='left', va='center', fontsize=7.8, fontweight='bold', color='#9a3412', fontproperties=prop_bold)

        note = " (IPC fd)" if b == 'xray' else ("*" if b == 'sing' else "")
        ax4.text(v_and + 1.5, y - bh / 2 - 0.02, f"{v_and:.1f} KiB{note}", ha='left', va='center', fontsize=7.8, fontweight='bold', color='#7c2d12' if b != 'xray' else '#b45309', fontproperties=prop_bold)

    ax4.legend(
        handles=[plt.Rectangle((0, 0), 1, 1, facecolor=c_pure, edgecolor=edge_pure), plt.Rectangle((0, 0), 1, 1, facecolor=c_android, edgecolor=edge_android)],
        labels=["Scheme 2: Pure Stack Microbench", "Scheme 1: Android Host App PSS"],
        loc='lower right', fontsize=7.5, frameon=True, facecolor='#ffffff'
    )
    # Footnote annotation for SingTUN Scheme 1 GC phenomenon
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
    ax5.set_ylabel("Memory Growth (MiB PSS above baseline)", fontsize=9, color='#64748b', fontproperties=prop_regular)
    ax5.grid(True, zorder=0)

    markers = {'hev': 'o', 'sing': 's', 'mips': '^', 'xray': 'D'}
    for b in BACKEND_ORDER:
        for r in avg_records:
            if r.get("type") == "idle_memory" and r.get("backend") == b and r.get("network") == "tcp":
                flows = r.get("idle_flows", [])
                if flows:
                    xs = [f["connections"] for f in flows]
                    base = flows[0]["pss_mb"]
                    ys = [max(0.0, round(f["pss_mb"] - base, 2)) for f in flows]
                    ax5.plot(xs, ys, color=PALETTE[b]['fill'], marker=markers[b], markersize=5.5, linewidth=1.8, label=PALETTE[b]['name'].split()[0], zorder=3)
                    slope = (ys[-1] - ys[0]) * 1024.0 / (xs[-1] - xs[0])
                    ax5.text(xs[-1] + 20, ys[-1], f"{slope:.2f} KiB", fontsize=7.5, fontweight='bold', color=PALETTE[b]['edge'], va='center', fontproperties=prop_bold)
                break

    ax5.set_xlim(-30, 1250)
    ax5.set_ylim(-1, 58)
    ax5.set_xticks([0, 250, 500, 750, 1000])

    # -------------------------------------------------------------
    # CHART 6: Binary Footprint & Architecture Feature Specs (Middle Right)
    # -------------------------------------------------------------
    ax6 = fig.add_axes([0.70, 0.385, 0.25, 0.23])
    ax6.set_facecolor('#ffffff')

    ax6.set_title("Stripped arm64-v8a Binary Size (Lower is Better)", fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=14)

    sizes = [PALETTE[b]['so_size_mb'] for b in BACKEND_ORDER]
    y_pos6 = np.arange(len(BACKEND_ORDER))[::-1]

    bars6 = ax6.barh(y_pos6, sizes, 0.42, color=[PALETTE[b]['fill'] for b in BACKEND_ORDER],
                     edgecolor=[PALETTE[b]['edge'] for b in BACKEND_ORDER], linewidth=0.8, zorder=3)
    ax6.set_yticks(y_pos6)
    ax6.set_yticklabels([PALETTE[b]['name'].split()[0] for b in BACKEND_ORDER], fontsize=9.5, fontproperties=prop_medium, color='#334155')
    ax6.set_xlabel("Stripped Shared Object Size (MiB)", fontsize=9, color='#64748b', fontproperties=prop_regular)
    ax6.set_xlim(0, 43)
    ax6.grid(True, axis='x', zorder=0)

    for bar, b in zip(bars6, BACKEND_ORDER):
        sz = PALETTE[b]['so_size_mb']
        txt = f"{sz:.2f} MiB ({int(sz*1024)} KB)" if sz < 1.0 else f"{sz:.2f} MiB"
        runtime_desc = f" [{PALETTE[b]['runtime'].split()[0]}]"
        ax6.text(
            sz + 0.8, bar.get_y() + bar.get_height() / 2,
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
            [r for r in long_runs if r.get("backend") in BACKEND_ORDER or r.get("backend") == "direct_none"],
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
    # CHART 8: UDP Transmission Jitter Across Media (Bottom Right)
    # -------------------------------------------------------------
    ax8 = fig.add_axes([0.70, 0.08, 0.25, 0.23])
    ax8.set_facecolor('#ffffff')
    ax8.set_title("UDP Transmission Jitter Across Media (Lower is Better)", fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=14)

    udp_scenarios = [
        ("Wi-Fi (P=1)", "5GHz Wi-Fi", 1),
        ("Wi-Fi (P=8)", "5GHz Wi-Fi", 8),
        ("USB (P=1)", "USB 3.2 / 4.0", 1),
        ("USB (P=8)", "USB 3.2 / 4.0", 8),
    ]
    num_scenarios = len(udp_scenarios)
    y_pos8 = np.arange(num_scenarios)[::-1]
    bh8 = 0.17
    offsets8 = np.linspace((len(BACKEND_ORDER) - 1) * bh8 / 2, -(len(BACKEND_ORDER) - 1) * bh8 / 2, len(BACKEND_ORDER))

    ax8.set_yticks(y_pos8)
    ax8.set_yticklabels([s[0] for s in udp_scenarios], fontsize=9, fontproperties=prop_medium, color='#334155')
    ax8.set_xlabel("Average Jitter (Milliseconds)", fontsize=9, color='#64748b', fontproperties=prop_regular)
    ax8.set_xlim(0, 1.00)
    ax8.grid(True, axis='x', zorder=0)

    for b_idx, b in enumerate(BACKEND_ORDER):
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
                j_val + 0.03, bar.get_y() + bar.get_height() / 2,
                txt,
                ha='left', va='center', fontsize=7.5, fontweight='bold',
                fontproperties=prop_bold, color='#1e293b'
            )

    # Add baseline markers for UDP jitter
    for idx, s in enumerate(udp_scenarios):
        lbl, med, par = s
        base_rec = get_rec(med, "direct_none", 0, par, "udp")
        b_up = base_rec.get("upload_jitter_ms", 0.0)
        b_down = base_rec.get("download_jitter_ms", 0.0)
        b_avg = (b_up + b_down) / 2.0 if (b_up > 0 and b_down > 0) else max(b_up, b_down)
        if b_avg > 0:
            y_center = y_pos8[idx]
            y_min = y_center - (len(BACKEND_ORDER) * bh8 / 2) - 0.03
            y_max = y_center + (len(BACKEND_ORDER) * bh8 / 2) + 0.03
            ax8.vlines(x=b_avg, ymin=y_min, ymax=y_max, colors='#64748b', linestyles='--', linewidth=1.2, zorder=4)
            b_txt = f"Base: {b_avg:.3f}ms" if b_avg < 0.1 else f"Base: {b_avg:.2f}ms"
            ax8.text(b_avg, y_max + 0.02, b_txt, ha='center', va='bottom', fontsize=7.2, color='#64748b', fontproperties=prop_regular)

    # -------------------------------------------------------------
    # 7. ACADEMIC & METHODOLOGY FOOTNOTE
    # -------------------------------------------------------------
    xray_ver = version_props.get("XRAY_CORE_VERSION", "v26.9.9")
    hev_ver = version_props.get("HEV_TUN_VERSION", "2.17.1 (b514150)")
    sing_ver = version_props.get("SING_TUN_VERSION", "a39eab51450b")
    mips_ver = version_props.get("MIPS_TUN_VERSION", "802d64336f8c")

    footnote_text = (
        "SPECIFICATIONS & METHODOLOGY\n"
        "Host (Server): AMD Ryzen 7 6800H @ 3.2GHz (8C/16T), Linux 6.12 | iPerf3 v3.18 | 1201 Mbps HE80 Wi-Fi 6 / 1Gbps USB 3.2 Gen1 Type-C RNDIS\n"
        "DUT (Client): Qualcomm Snapdragon 778G (1+3+4 Cores), Android 14, Linux 5.4 | iPerf3 v3.21 static arm64\n"
        f"Backends: hev-socks5-tunnel ({hev_ver}) | SingTUN ({sing_ver}) | MipsTUN ({mips_ver}) | Xray Native TUN ({xray_ver})\n"
        "Sampling & Metrics: 3-round arithmetic mean; CPU% represents multi-core cumulative load (800% max); Scheme 1 Android PSS; Scheme 2 Linux isolated user namespace"
    )
    fig.text(
        0.5, 0.016, footnote_text,
        fontsize=7.5, color='#64748b', fontproperties=prop_regular,
        ha='center', va='bottom', linespacing=1.45
    )

    # Save image
    os.makedirs(os.path.dirname(OUTPUT_MEGA_IMAGE), exist_ok=True)
    plt.savefig(
        OUTPUT_MEGA_IMAGE,
        dpi=400,
        facecolor='#f8fafc',
        edgecolor='none',
        pil_kwargs={'lossless': True}
    )
    plt.close()
    print(f"[Mega Dashboard] Successfully generated: {OUTPUT_MEGA_IMAGE}")


if __name__ == "__main__":
    generate_mega_dashboard()
