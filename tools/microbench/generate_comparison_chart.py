#!/usr/bin/env python3
"""
SimpleXray Microbenchmark Comparison Chart Generator
Generates high-density visual comparison between Android Integrated Data (Option B)
and Standalone Pure Network Stack Data (Scheme 2).
"""

import argparse
import json
import os
import matplotlib.pyplot as plt
import numpy as np

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../.."))

plt.rcParams["font.sans-serif"] = ["DejaVu Sans", "Arial", "Helvetica"]
plt.rcParams["axes.unicode_minus"] = False

def main():
    android_json = os.path.join(PROJECT_ROOT, "docs/benchmark/benchmark_results.json")
    micro_json = os.path.join(PROJECT_ROOT, "docs/benchmark/microbench_results.json")
    output_img = os.path.join(PROJECT_ROOT, "docs/images/microbench_attribution_comparison.webp")

    if not os.path.exists(android_json) or not os.path.exists(micro_json):
        print(f"Error: Required JSON files missing: {android_json} or {micro_json}")
        return

    with open(android_json, "r", encoding="utf-8") as f:
        android_data = json.load(f)
    with open(micro_json, "r", encoding="utf-8") as f:
        micro_data = json.load(f)

    # Extract averages for idle memory slopes
    def get_avg_idle_slopes(data_dict, key="rounds"):
        slopes = {}
        for r_name, r_items in data_dict.get(key, {}).items():
            for item in r_items:
                b = item.get("backend", "").lower()
                net = item.get("network", "").lower()
                if not b or not net:
                    continue
                slope = item.get("slope_kib_per_conn")
                if slope is None:
                    continue
                k = (b, net)
                slopes.setdefault(k, []).append(slope)
        return {k: round(float(np.mean(v)), 2) for k, v in slopes.items()}

    android_slopes = get_avg_idle_slopes(android_data)
    micro_slopes = get_avg_idle_slopes(micro_data)

    cases = [
        ("hev", "tcp", "HEV (TCP)"),
        ("hev", "udp", "HEV (UDP)"),
        ("sing", "tcp", "SingTUN (TCP)"),
        ("sing", "udp", "SingTUN (UDP)"),
        ("xray", "tcp", "Xray TUN (TCP)"),
        ("xray", "udp", "Xray TUN (UDP)")
    ]

    labels = [c[2] for c in cases]
    and_vals = [android_slopes.get((c[0], c[1]), 0.0) for c in cases]
    mic_vals = [micro_slopes.get((c[0], c[1]), 0.0) for c in cases]

    # Plot
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(18, 7), dpi=200)
    fig.patch.set_facecolor("#0F141C")
    for ax in (ax1, ax2):
        ax.set_facecolor("#161D2A")
        ax.grid(True, linestyle="--", alpha=0.25, color="#8F9CAE")
        for spine in ax.spines.values():
            spine.set_color("#2D3748")
        ax.tick_params(colors="#CBD5E1", labelsize=10)

    x = np.arange(len(labels))
    width = 0.36

    # Bar comparison
    rects1 = ax1.bar(x - width/2, and_vals, width, label="Android Integrated App (Snapdragon 778G)", color="#38BDF8", edgecolor="#0284C7", linewidth=1.2, zorder=3)
    rects2 = ax1.bar(x + width/2, mic_vals, width, label="Pure Standalone Stack (Scheme 2 Microbench)", color="#A855F7", edgecolor="#7E22CE", linewidth=1.2, zorder=3)

    ax1.set_ylabel("Memory Growth Slope (KiB / Connection)", color="#F1F5F9", fontsize=11, fontweight="bold")
    ax1.set_title("Memory Slope Comparison: Android App vs Standalone Stack", color="#F8FAFC", fontsize=13, fontweight="bold", pad=12)
    ax1.set_xticks(x)
    ax1.set_xticklabels(labels, rotation=35, ha="right", color="#E2E8F0", fontweight="bold")
    leg1 = ax1.legend(facecolor="#1E293B", edgecolor="#334155", labelcolor="#F1F5F9", fontsize=10)

    for rect in rects1:
        h = rect.get_height()
        ax1.annotate(f"{h:.1f}",
                    xy=(rect.get_x() + rect.get_width() / 2, h),
                    xytext=(0, 4), textcoords="offset points",
                    ha='center', va='bottom', color="#38BDF8", fontsize=9, fontweight="bold")
    for rect in rects2:
        h = rect.get_height()
        ax1.annotate(f"{h:.1f}",
                    xy=(rect.get_x() + rect.get_width() / 2, h),
                    xytext=(0, 4), textcoords="offset points",
                    ha='center', va='bottom', color="#C084FC", fontsize=9, fontweight="bold")

    # Stacked attribution analysis
    pure_stack_costs = mic_vals
    overhead_diffs = []
    for a, m in zip(and_vals, mic_vals):
        diff = a - m
        overhead_diffs.append(max(0.0, diff))

    ax2.bar(x, pure_stack_costs, width * 1.3, label="Pure Protocol Stack Core Cost", color="#8B5CF6", edgecolor="#6D28D9", zorder=3)
    ax2.bar(x, overhead_diffs, width * 1.3, bottom=pure_stack_costs, label="Android Platform & JNI Ingestion Delta", color="#F59E0B", edgecolor="#D97706", zorder=3)

    ax2.set_ylabel("Attributed Memory Footprint (KiB / Connection)", color="#F1F5F9", fontsize=11, fontweight="bold")
    ax2.set_title("Layered Cost Attribution (Protocol Core vs Android Overhead)", color="#F8FAFC", fontsize=13, fontweight="bold", pad=12)
    ax2.set_xticks(x)
    ax2.set_xticklabels(labels, rotation=35, ha="right", color="#E2E8F0", fontweight="bold")
    leg2 = ax2.legend(facecolor="#1E293B", edgecolor="#334155", labelcolor="#F1F5F9", fontsize=10)

    plt.suptitle("SimpleXray Full-System vs Standalone Micro-Benchmark Attribution Analysis",
                 color="#F8FAFC", fontsize=15, fontweight="bold", y=0.98)
    plt.tight_layout()

    os.makedirs(os.path.dirname(output_img), exist_ok=True)
    plt.savefig(output_img, facecolor=fig.get_facecolor(), bbox_inches="tight")
    plt.close()
    print(f"Successfully generated comparison dashboard: {output_img}")

if __name__ == "__main__":
    main()
