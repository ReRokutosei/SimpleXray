#!/usr/bin/env python3
"""
SimpleXray Super Mega Benchmark Dashboard Generator
Generates an ultra-wide, publication-grade, multi-archetype master infographic
synthesizing Scheme 1 (Android End-to-End) and Scheme 2 (Linux Standalone Microbench).

Design Language & Standards:
  - Clean, restrained, high-density scientific aesthetics (inspired by tailscale/mundotunnel).
  - Modern light theme (#f8fafc canvas, pure #ffffff chart cards, slate borders).
  - Novel visualization archetypes:
      1. Physical Baseline Retention Radar Matrix
      2. Multi-Stream (P=1 -> P=8) Scaling Speedup Ratio
      3. Compute Cost (CPU% per 100 Mbps) Density Heatmap
      4. Dual-Layer Memory Attribution (Microbench Stack vs Android App PSS)
      5. Step 0 -> 1000 Idle Flows Memory Growth Band
      6. Architecture Specs & Stripped arm64-v8a Shared Binary Footprint
  - Standardized font: JetBrains Mono (monospace precision).
  - Pure English in charts; directional indicators "(Higher is Better)" / "(Lower is Better)" spaced without middle-dots.
  - Academic methodology & hardware specifications footnote directly synthesized from report documentation.
"""

import json
import os
import sys
from typing import Dict, List, Any, Tuple

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
import numpy as np

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_BENCH_JSON = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "docs", "benchmark", "benchmark_results.json"))
DEFAULT_MICRO_JSON = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "docs", "benchmark", "microbench_results.json"))
OUTPUT_MEGA_IMAGE = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "docs", "images", "mega_benchmark_infographic.webp"))

# Register JetBrains Mono fonts
FONT_DIR = "/home/vanitas/.local/share/fonts/JetBrains"
FONT_REGULAR = os.path.join(FONT_DIR, "JetBrainsMonoNerdFont-Regular.ttf")
FONT_BOLD = os.path.join(FONT_DIR, "JetBrainsMonoNerdFont-Bold.ttf")
FONT_MEDIUM = os.path.join(FONT_DIR, "JetBrainsMonoNerdFont-Medium.ttf")

prop_regular = None
prop_bold = None
prop_medium = None

for p, target in [(FONT_REGULAR, 'regular'), (FONT_BOLD, 'bold'), (FONT_MEDIUM, 'medium')]:
    if os.path.exists(p):
        try:
            fm.fontManager.addfont(p)
            fprop = fm.FontProperties(fname=p)
            if target == 'regular':
                prop_regular = fprop
            elif target == 'bold':
                prop_bold = fprop
            elif target == 'medium':
                prop_medium = fprop
        except Exception:
            pass

if prop_regular:
    plt.rcParams['font.sans-serif'] = [prop_regular.get_name(), 'DejaVu Sans', 'Arial', 'sans-serif']
    plt.rcParams['font.family'] = 'sans-serif'

# Global Theme Styling
plt.rcParams['figure.facecolor'] = '#f8fafc'
plt.rcParams['axes.facecolor'] = '#ffffff'
plt.rcParams['axes.edgecolor'] = '#cbd5e1'
plt.rcParams['axes.labelcolor'] = '#334155'
plt.rcParams['xtick.color'] = '#475569'
plt.rcParams['ytick.color'] = '#0f172a'
plt.rcParams['grid.color'] = '#f1f5f9'
plt.rcParams['grid.alpha'] = 1.0
plt.rcParams['grid.linestyle'] = '-'

# Unified Backend Palette
PALETTE = {
    'hev': {
        'name': 'Hev (C/lwIP)',
        'fill': '#0d9488',   # Teal 600
        'edge': '#0f766e',   # Teal 700
        'light': '#ccfbf1',  # Teal 100
        'so_size_mb': 0.34,  # 346 KB
        'runtime': 'C (Single-threaded lwIP)',
        'ipc': 'Local SOCKS5 Inbound'
    },
    'sing': {
        'name': 'SingTUN (Go/sing-box)',
        'fill': '#2563eb',   # Blue 600
        'edge': '#1d4ed8',   # Blue 700
        'light': '#dbeafe',  # Blue 100
        'so_size_mb': 6.30,  # 6.3 MB
        'runtime': 'Go 1.25 (sing-tun 0.9.4 mod)',
        'ipc': 'Local SOCKS5 Inbound'
    },
    'mips': {
        'name': 'MipsTUN (Go/BBRv3)',
        'fill': '#7c3aed',   # Violet 600
        'edge': '#6d28d9',   # Violet 700
        'light': '#ede9fe',  # Violet 100
        'so_size_mb': 4.80,  # 4.8 MB
        'runtime': 'Go 1.27 (mipstack BBRv3)',
        'ipc': 'Local SOCKS5 Inbound'
    },
    'xray': {
        'name': 'Xray TUN (gVisor)',
        'fill': '#e11d48',   # Rose 600
        'edge': '#be123c',   # Rose 700
        'light': '#ffe4e6',  # Rose 100
        'so_size_mb': 34.00, # 34 MB
        'runtime': 'Go / gVisor Netstack',
        'ipc': 'JNI Fork Child Process (FD Injected)'
    }
}

BACKEND_ORDER = ['hev', 'sing', 'mips', 'xray']


def load_datasets():
    with open(DEFAULT_BENCH_JSON, 'r', encoding='utf-8') as f:
        bench_root = json.load(f)
    with open(DEFAULT_MICRO_JSON, 'r', encoding='utf-8') as f:
        micro_root = json.load(f)
    return bench_root, micro_root


def compute_clean_averages(bench_root: Dict[str, Any]) -> List[Dict[str, Any]]:
    rounds = bench_root.get("rounds", {})
    first_round = list(rounds.values())[0]
    numeric_fields = [
        "upload_mbps", "download_mbps", "speed_gbps",
        "upload_cpu_avg", "upload_cpu_peak",
        "download_cpu_avg", "download_cpu_peak",
        "peak_cpu", "peak_mem_mb",
        "upload_loss_percent", "download_loss_percent"
    ]
    avg_records = []
    for rec_template in first_round:
        rec = dict(rec_template)
        med = rec.get("medium")
        b = rec.get("backend")
        mtu = rec.get("mtu")
        par = rec.get("parallel")
        net = rec.get("network", "tcp")

        for fld in numeric_fields:
            vals = []
            for r_list in rounds.values():
                for m in r_list:
                    if (m.get("medium") == med and
                        m.get("backend") == b and
                        m.get("mtu") == mtu and
                        m.get("parallel") == par and
                        m.get("network", "tcp") == net):
                        if fld in m and m[fld] > 0.0:
                            vals.append(m[fld])
                        break
            if vals:
                rec[fld] = round(sum(vals) / len(vals), 2)

        if rec.get("type") == "idle_memory":
            conns_map = {}
            for r_list in rounds.values():
                for m in r_list:
                    if (m.get("type") == "idle_memory" and
                        m.get("backend") == b and
                        m.get("network", "tcp") == net):
                        for flow in m.get("idle_flows", []):
                            c = flow.get("connections", 0)
                            conns_map.setdefault(c, []).append(flow.get("pss_mb", 0.0))
                        break
            new_flows = []
            for c in sorted(conns_map.keys()):
                p_list = conns_map[c]
                avg_pss = round(sum(p_list) / len(p_list), 2) if p_list else 0.0
                new_flows.append({"connections": c, "pss_mb": avg_pss})
            rec["idle_flows"] = new_flows

        avg_records.append(rec)
    return avg_records


def generate_mega_dashboard():
    print("[Mega Dashboard] Loading datasets...")
    bench_root, micro_root = load_datasets()
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

    # Canvas dimensions: 22 x 15 inches @ 220 DPI
    fig = plt.figure(figsize=(22, 15.0), dpi=220)
    fig.patch.set_facecolor('#f8fafc')

    # -------------------------------------------------------------
    # 0. HEADER & TITLE
    # -------------------------------------------------------------
    fig.text(
        0.5, 0.976,
        "SimpleXray Cross-Stack TUN Architecture & Performance Infographic",
        fontsize=18, fontweight='bold', color='#0f172a',
        fontproperties=prop_bold, ha='center', va='top'
    )
    fig.text(
        0.5, 0.954,
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
        bbox_to_anchor=(0.5, 0.935),
        ncol=4,
        frameon=True,
        facecolor='#ffffff',
        edgecolor='#cbd5e1',
        fontsize=10,
        prop=prop_medium
    )

    # Geometry layout bounds
    # Row 1: y = [0.55, 0.88] (height = 0.33)
    # Row 2: y = [0.14, 0.47] (height = 0.33)
    # Footnote: y = [0.02, 0.09]

    # -------------------------------------------------------------
    # CHART 1: Physical Baseline Retention Radar Matrix (Top Left)
    # -------------------------------------------------------------
    ax1 = fig.add_axes([0.06, 0.55, 0.25, 0.33], polar=True)
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
    ax1.set_ylim(0, 115)
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
            ret = min(110.0, (b_val / base_val * 100.0)) if base_val > 0 else 0.0
            retentions.append(ret)
        retentions += retentions[:1]
        ax1.plot(angles, retentions, color=PALETTE[b]['fill'], linewidth=1.8)
        ax1.fill(angles, retentions, color=PALETTE[b]['fill'], alpha=0.10)

    ax1.set_title("Physical Baseline Retention Ratio (Higher is Better)", fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=22)

    # -------------------------------------------------------------
    # CHART 2: P=1 to P=8 Scaling Dynamics (Top Center)
    # -------------------------------------------------------------
    ax2 = fig.add_axes([0.38, 0.55, 0.26, 0.33])
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
    ax2.set_xlim(0, 3.0)
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
                factor + 0.05, bar.get_y() + bar.get_height() / 2,
                f"{factor:.2f}x",
                ha='left', va='center', fontsize=7.5, fontweight='bold',
                fontproperties=prop_bold, color='#1e293b'
            )

    # -------------------------------------------------------------
    # CHART 3: Compute Cost Density Heatmap (Top Right)
    # -------------------------------------------------------------
    ax3 = fig.add_axes([0.70, 0.55, 0.25, 0.33])
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

    cax = ax3.imshow(matrix, cmap="YlGnBu", aspect="auto", vmin=0, vmax=60)

    ax3.set_title("Compute Cost Heatmap (CPU% per 100 Mbps) (Lower is Better)", fontsize=11, fontweight='bold',
                  fontproperties=prop_bold, color='#1e293b', pad=14)
    ax3.set_xticks(np.arange(len(heat_scenarios)))
    ax3.set_xticklabels([s[0] for s in heat_scenarios], rotation=35, ha='right', fontsize=7.8, fontproperties=prop_regular)
    ax3.set_yticks(np.arange(len(BACKEND_ORDER)))
    ax3.set_yticklabels([PALETTE[b]['name'].split()[0] for b in BACKEND_ORDER], fontsize=9, fontproperties=prop_medium)

    for i in range(len(BACKEND_ORDER)):
        for j in range(len(heat_scenarios)):
            val = matrix[i, j]
            txt_color = "#ffffff" if val > 35 else "#0f172a"
            ax3.text(j, i, f"{val:.1f}%", ha="center", va="center", color=txt_color, fontsize=8, fontweight='bold', fontproperties=prop_bold)

    cbar = fig.colorbar(cax, ax=ax3, orientation='horizontal', fraction=0.046, pad=0.24)
    cbar.set_label("CPU% consumed per 100 Mbps", fontsize=8, color='#64748b', fontproperties=prop_regular)
    cbar.ax.tick_params(labelsize=7)

    # -------------------------------------------------------------
    # CHART 4: Dual-Layer Full-Stack Memory Attribution (Bottom Left)
    # -------------------------------------------------------------
    ax4 = fig.add_axes([0.06, 0.15, 0.25, 0.32])
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

        note = " (IPC fd)" if b == 'xray' else ""
        ax4.text(v_and + 1.5, y - bh / 2 - 0.02, f"{v_and:.1f} KiB{note}", ha='left', va='center', fontsize=7.8, fontweight='bold', color='#7c2d12' if b != 'xray' else '#b45309', fontproperties=prop_bold)

    ax4.legend(
        handles=[plt.Rectangle((0, 0), 1, 1, facecolor=c_pure, edgecolor=edge_pure), plt.Rectangle((0, 0), 1, 1, facecolor=c_android, edgecolor=edge_android)],
        labels=["Scheme 2: Pure Stack Microbench", "Scheme 1: Android Host App PSS"],
        loc='lower right', fontsize=7.5, frameon=True, facecolor='#ffffff'
    )

    # -------------------------------------------------------------
    # CHART 5: 0 -> 1000 Idle Flows Step Growth Band (Bottom Center)
    # -------------------------------------------------------------
    ax5 = fig.add_axes([0.38, 0.15, 0.26, 0.32])
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
    # CHART 6: Binary Footprint & Architecture Feature Specs (Bottom Right)
    # -------------------------------------------------------------
    ax6 = fig.add_axes([0.70, 0.15, 0.25, 0.32])
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
    # 7. ACADEMIC & METHODOLOGY FOOTNOTE
    # -------------------------------------------------------------
    footnote_text = (
        "SPECIFICATIONS & METHODOLOGY\n"
        "Host (Server): AMD Ryzen 7 6800H @ 3.2GHz (8C/16T), Linux 6.12.107+deb13-amd64 | iPerf3 v3.18 (cJSON 1.7.15) | 1201 Mbps HE80 Wi-Fi 6E AX210 / USB 3.2 Gen1 Type-C RNDIS\n"
        "DUT (Client): Qualcomm Snapdragon 778G SM7325 (1x2.4GHz + 3x2.2GHz Cortex-A78 + 4x1.9GHz Cortex-A55), Android 14, Linux 5.4 | iPerf3 v3.21 static arm64\n"
        "Backends: hev-socks5-tunnel (git b514150 | SingTUN (git a39eab51450b) | MipsTUN (git 802d64336f8c) | Xray Native TUN (core v26.9.9)\n"
        "Sampling & Metrics: 3-round arithmetic mean; CPU% represents multi-core cumulative load (800% system ceiling); Scheme 1 Android PSS via dumpsys meminfo; Scheme 2 Linux unshare -r -n isolated user namespace"
    )
    fig.text(
        0.5, 0.024, footnote_text,
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
