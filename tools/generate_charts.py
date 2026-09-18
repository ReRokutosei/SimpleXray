#!/usr/bin/env python3
"""
SimpleXray Benchmark Chart Generator
Generates high-resolution visualization charts (WebP/PNG) from benchmark results.
Supports 4 TUN backends: Hev, Xray TUN, SingTUN, and MipsTUN.
"""

import argparse
import json
import os
import sys
from typing import Tuple, List, Dict, Any
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
import numpy as np

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DOCS_IMAGES = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "docs", "images"))
DEFAULT_JSON_PATH = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "docs", "benchmark", "benchmark_results.json"))

# Try to find a clean sans-serif font
prop = None
for candidate in [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/opentype/inter/Inter-Regular.otf",
    r"D:\文档\字体\Inter-4.1\InterVariable.ttf"
]:
    if os.path.exists(candidate):
        try:
            fm.fontManager.addfont(candidate)
            prop = fm.FontProperties(fname=candidate)
            plt.rcParams['font.sans-serif'] = [prop.get_name(), 'DejaVu Sans', 'Arial', 'sans-serif']
            plt.rcParams['font.family'] = 'sans-serif'
            break
        except Exception:
            pass

# Crisp, Professional Light Theme Settings
plt.rcParams['figure.facecolor'] = '#ffffff'
plt.rcParams['axes.facecolor'] = '#ffffff'
plt.rcParams['axes.edgecolor'] = '#cbd5e1'
plt.rcParams['axes.labelcolor'] = '#475569'
plt.rcParams['xtick.color'] = '#334155'
plt.rcParams['ytick.color'] = '#334155'
plt.rcParams['grid.color'] = '#e2e8f0'
plt.rcParams['grid.alpha'] = 0.8
plt.rcParams['grid.linestyle'] = '--'

COLOR_SINGLE = '#3b82f6'  # Blue (Single Stream) - Top bar
COLOR_MULTI = '#f97316'   # Orange (Multi Stream) - Bottom bar
EDGE_SINGLE = '#2563eb'
EDGE_MULTI = '#ea580c'

def render_horizontal_bar_chart(
    filename: str,
    title: str,
    xlabel: str,
    models: list,
    single_vals: list,
    multi_vals: list,
    max_x: float,
    min_x: float = 0.0,
    is_int: bool = True,
    unit: str = "",
    xticks: list = None,
    legend_loc: str = 'lower right',
    fig_width: float = 9.5
):
    models_reversed = list(reversed(models))
    single_reversed = list(reversed(single_vals))
    multi_reversed = list(reversed(multi_vals))
    y_pos = np.arange(len(models_reversed))
    bar_height = 0.35

    fig_height = max(4.0, 0.7 * len(models) + 0.8)
    fig, ax = plt.subplots(figsize=(fig_width, fig_height), facecolor='#ffffff')
    
    rects_single = ax.barh(y_pos + bar_height/2, single_reversed, bar_height, label='Single', color=COLOR_SINGLE, edgecolor=EDGE_SINGLE, linewidth=1.0, zorder=3)
    rects_multi = ax.barh(y_pos - bar_height/2, multi_reversed, bar_height, label='Multi', color=COLOR_MULTI, edgecolor=EDGE_MULTI, linewidth=1.0, zorder=3)
    
    ax.set_title(title, fontsize=13, fontweight='bold', color='#0f172a', fontproperties=prop, pad=12)
    ax.set_xlabel(xlabel, fontsize=10.5, fontproperties=prop)
    ax.set_yticks(y_pos)
    ax.set_yticklabels(models_reversed, fontsize=9.5, fontproperties=prop)
    ax.grid(True, zorder=0, axis='x')
    ax.set_xlim(min_x, max_x)
    if xticks is not None:
        ax.set_xticks(xticks)
    
    ax.legend(handles=[rects_single, rects_multi], labels=['Single', 'Multi'], loc=legend_loc, framealpha=0.95, facecolor='#f8fafc', edgecolor='#cbd5e1', prop=prop)
    
    def autolabel_h(rects):
        for rect in rects:
            width = rect.get_width()
            val_str = f'{int(width)}' if is_int else f'{width:.1f}'
            if unit:
                val_str += f' {unit}'
            ax.annotate(
                val_str,
                xy=(width, rect.get_y() + rect.get_height() / 2),
                xytext=(6, 0),
                textcoords="offset points",
                ha='left',
                va='center',
                fontsize=8.5,
                fontweight='bold',
                color='#1e293b',
                fontproperties=prop
            )
            
    autolabel_h(rects_single)
    autolabel_h(rects_multi)
    
    plt.tight_layout()
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    plt.savefig(filename, dpi=200, facecolor='#ffffff', edgecolor='none')
    plt.close()
    print(f"[Chart] Saved: {filename}")


def generate_round_charts(output_dir: str, prefix: str, models: list, data: dict):
    # 1. Wi-Fi Speeds
    if "wifi_up_s" in data and "wifi_up_m" in data:
        max_wifi = max(max(data["wifi_up_s"] + data["wifi_up_m"] + data["wifi_down_s"] + data["wifi_down_m"]), 100) * 1.15
        render_horizontal_bar_chart(
            os.path.join(output_dir, f"{prefix}wifi_speed_upload.webp"),
            "5GHz Wi-Fi: Upload speed - Mbps", "Speed (Mbps)",
            models, data["wifi_up_s"], data["wifi_up_m"], max_wifi, legend_loc='lower right'
        )
        render_horizontal_bar_chart(
            os.path.join(output_dir, f"{prefix}wifi_speed_download.webp"),
            "5GHz Wi-Fi: Download speed - Mbps", "Speed (Mbps)",
            models, data["wifi_down_s"], data["wifi_down_m"], max_wifi, legend_loc='lower right'
        )

    # 2. USB Speeds
    if "usb_up_s" in data and "usb_up_m" in data:
        max_usb_up = max(max(data["usb_up_s"] + data["usb_up_m"]), 500) * 1.28
        max_usb_down = max(max(data["usb_down_s"] + data["usb_down_m"]), 200) * 1.15
        render_horizontal_bar_chart(
            os.path.join(output_dir, f"{prefix}usb_speed_upload.webp"),
            "USB 3.2 / 4.0: Upload speed - Mbps", "Speed (Mbps)",
            models, data["usb_up_s"], data["usb_up_m"], max_usb_up,
            legend_loc='lower right', fig_width=10.5
        )
        render_horizontal_bar_chart(
            os.path.join(output_dir, f"{prefix}usb_speed_download.webp"),
            "USB 3.2 / 4.0: Download speed - Mbps", "Speed (Mbps)",
            models, data["usb_down_s"], data["usb_down_m"], max_usb_down, legend_loc='lower right'
        )

    # 3. Loopback
    if "loop_s" in data and "loop_m" in data:
        max_loop = max(max(data["loop_s"] + data["loop_m"]), 10.0) * 1.2
        render_horizontal_bar_chart(
            os.path.join(output_dir, f"{prefix}loopback_speed.webp"),
            "On-Device Loopback: Core Processing Speed - Gbps", "Speed (Gbps)",
            models, data["loop_s"], data["loop_m"], max_loop, is_int=False, unit="G", legend_loc='lower right'
        )

    # 4. CPU Usage
    if "cpu_up_s" in data:
        max_cpu = max(max(data["cpu_up_s"] + data["cpu_up_m"] + data["cpu_down_s"] + data["cpu_down_m"]), 1.0)
        cpu_max_x = max(1.0, np.ceil(max_cpu * 1.2 * 10) / 10)
        render_horizontal_bar_chart(
            os.path.join(output_dir, f"{prefix}cpu_usage_upload.webp"),
            "Upload CPU usage - %", "CPU (%)",
            models, data["cpu_up_s"], data["cpu_up_m"], cpu_max_x, min_x=0.0, is_int=False, unit="%", legend_loc='lower right'
        )
        render_horizontal_bar_chart(
            os.path.join(output_dir, f"{prefix}cpu_usage_download.webp"),
            "Download CPU usage - %", "CPU (%)",
            models, data["cpu_down_s"], data["cpu_down_m"], cpu_max_x, min_x=0.0, is_int=False, unit="%", legend_loc='lower right'
        )

    # 5. Memory Usage
    if "mem_up_s" in data:
        all_mem = data["mem_up_s"] + data["mem_up_m"] + data["mem_down_s"] + data["mem_down_m"]
        mem_min = max(0.0, np.floor(min(all_mem) - 5.0))
        mem_max = np.ceil(max(all_mem) + (max(all_mem) - mem_min) * 0.22 + 4.0)
        render_horizontal_bar_chart(
            os.path.join(output_dir, f"{prefix}memory_usage_upload.webp"),
            "Upload MEM usage - MB", "Memory (MB)",
            models, data["mem_up_s"], data["mem_up_m"], mem_max, min_x=mem_min, is_int=False, unit="MB", legend_loc='lower right'
        )
        render_horizontal_bar_chart(
            os.path.join(output_dir, f"{prefix}memory_usage_download.webp"),
            "Download MEM usage - MB", "Memory (MB)",
            models, data["mem_down_s"], data["mem_down_m"], mem_max, min_x=mem_min, is_int=False, unit="MB", legend_loc='lower right'
        )


def parse_round_data(records: list, models_spec: list) -> Tuple[list, dict]:
    # models_spec: [('Native Baseline', 'direct_none', 0), ('Hev (MTU 1500)', 'hev', 1500), ...]
    labels = [m[0] for m in models_spec]
    
    def find_val(medium: str, backend: str, mtu: int, parallel: int, field: str) -> float:
        for r in records:
            if r.get("medium") == medium and r.get("backend") == backend and r.get("mtu") == mtu and r.get("parallel") == parallel:
                return float(r.get(field, 0.0))
        return 0.0

    data = {
        "wifi_up_s": [find_val("5GHz Wi-Fi", b, m, 1, "upload_mbps") for _, b, m in models_spec],
        "wifi_up_m": [find_val("5GHz Wi-Fi", b, m, 8, "upload_mbps") for _, b, m in models_spec],
        "wifi_down_s": [find_val("5GHz Wi-Fi", b, m, 1, "download_mbps") for _, b, m in models_spec],
        "wifi_down_m": [find_val("5GHz Wi-Fi", b, m, 8, "download_mbps") for _, b, m in models_spec],

        "usb_up_s": [find_val("USB 3.2 / 4.0", b, m, 1, "upload_mbps") for _, b, m in models_spec],
        "usb_up_m": [find_val("USB 3.2 / 4.0", b, m, 8, "upload_mbps") for _, b, m in models_spec],
        "usb_down_s": [find_val("USB 3.2 / 4.0", b, m, 1, "download_mbps") for _, b, m in models_spec],
        "usb_down_m": [find_val("USB 3.2 / 4.0", b, m, 8, "download_mbps") for _, b, m in models_spec],

        "loop_s": [find_val("On-Device Loopback", b, m, 1, "speed_gbps") for _, b, m in models_spec],
        "loop_m": [find_val("On-Device Loopback", b, m, 8, "speed_gbps") for _, b, m in models_spec],

        "cpu_up_s": [find_val("5GHz Wi-Fi", b, m, 1, "upload_cpu_avg") for _, b, m in models_spec],
        "cpu_up_m": [find_val("5GHz Wi-Fi", b, m, 8, "upload_cpu_avg") for _, b, m in models_spec],
        "cpu_down_s": [find_val("5GHz Wi-Fi", b, m, 1, "download_cpu_avg") for _, b, m in models_spec],
        "cpu_down_m": [find_val("5GHz Wi-Fi", b, m, 8, "download_cpu_avg") for _, b, m in models_spec],

        "mem_up_s": [find_val("5GHz Wi-Fi", b, m, 1, "peak_mem_mb") for _, b, m in models_spec],
        "mem_up_m": [find_val("5GHz Wi-Fi", b, m, 8, "peak_mem_mb") for _, b, m in models_spec],
        "mem_down_s": [find_val("5GHz Wi-Fi", b, m, 1, "peak_mem_mb") for _, b, m in models_spec],
        "mem_down_m": [find_val("5GHz Wi-Fi", b, m, 8, "peak_mem_mb") for _, b, m in models_spec],
    }
    return labels, data


def main():
    parser = argparse.ArgumentParser(description="Generate benchmark charts")
    parser.add_argument("--json", default=DEFAULT_JSON_PATH, help="Path to benchmark_results.json")
    parser.add_argument("--output-dir", default=DEFAULT_DOCS_IMAGES, help="Path to output charts directory")
    args = parser.parse_args()

    models_spec = [
        ('Native Baseline', 'direct_none', 0),
        ('Hev (MTU 1500)', 'hev', 1500),
        ('Xray TUN (MTU 1500)', 'xray', 1500),
        ('SingTUN (MTU 1500)', 'sing', 1500),
        ('MipsTUN (MTU 1500)', 'mips', 1500),
        ('Hev (MTU 8500)', 'hev', 8500),
        ('Xray TUN (MTU 8500)', 'xray', 8500),
        ('SingTUN (MTU 8500)', 'sing', 8500),
        ('MipsTUN (MTU 8500)', 'mips', 8500),
    ]

    if os.path.exists(args.json):
        print(f"Loading results from {args.json}...")
        with open(args.json, "r", encoding="utf-8") as f:
            root = json.load(f)

        rounds = root.get("rounds", {})
        for r_name, records in rounds.items():
            r_num = r_name.replace("round_", "")
            prefix = f"r{r_num}_"
            labels, data = parse_round_data(records, models_spec)
            print(f"Generating charts for {r_name} (prefix='{prefix}')...")
            generate_round_charts(args.output_dir, prefix, labels, data)

        if rounds:
            first_round = list(rounds.values())[0]
            numeric_fields = [
                "upload_mbps", "download_mbps", "speed_gbps",
                "upload_cpu_avg", "upload_cpu_peak",
                "download_cpu_avg", "download_cpu_peak",
                "peak_cpu", "peak_mem_mb"
            ]
            avg_records = []
            for rec_template in first_round:
                rec = dict(rec_template)
                medium = rec.get("medium")
                backend = rec.get("backend")
                mtu = rec.get("mtu")
                parallel = rec.get("parallel")
                for field in numeric_fields:
                    vals = []
                    for r_list in rounds.values():
                        for match in r_list:
                            if (match.get("medium") == medium and
                                match.get("backend") == backend and
                                match.get("mtu") == mtu and
                                match.get("parallel") == parallel):
                                if field in match:
                                    vals.append(match[field])
                                break
                    if vals:
                        rec[field] = round(sum(vals) / len(vals), 2)
                avg_records.append(rec)

            labels, avg_data = parse_round_data(avg_records, models_spec)
            print("Generating charts for average across all rounds (prefix='avg_')...")
            generate_round_charts(args.output_dir, "avg_", labels, avg_data)
            generate_round_charts(args.output_dir, "", labels, avg_data)

        print("All charts generated successfully from JSON.")
    else:
        print(f"JSON file not found at {args.json}. Please run benchmark.py first.")

if __name__ == "__main__":
    main()
