#!/usr/bin/env python3
"""
SimpleXray Benchmark Document In-Place Table Synchronizer
Safely updates Section 3.1 tables in docs/benchmark/android-tun-benchmark.md
without overwriting or modifying other sections, analyses, or conclusions.
"""

import os
import re
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
sys.path.insert(0, SCRIPT_DIR)

from common.dataset import (
    DEFAULT_BENCH_JSON,
    load_datasets,
    compute_clean_averages,
)

DOC_PATH = os.path.join(PROJECT_ROOT, "docs", "benchmark", "android-tun-benchmark.md")


def render_table(cases, is_loopback=False) -> str:
    lines = []
    if is_loopback:
        lines.append("| 测试用例 / 配置 | 后端协议栈 | MTU | 流模式 | 单流/多流吞吐 (Gbps) | 峰值 CPU (%) | 内存占用 (PSS) |")
        lines.append("| :--- | :--- | :---: | :---: | :---: | :---: | :---: |")
        for c in cases:
            speed_val = c.get("upload_mbps", 0.0)
            speed_gbps = round(speed_val / 1000.0, 2)
            par = "单流" if c.get("parallel") == 1 else "P=8"
            name = c.get("name")
            backend = c.get("backend")
            mtu = c.get("mtu")
            peak_cpu = c.get("peak_cpu", 0.0)
            peak_mem = c.get("peak_mem", c.get("peak_mem_mb", 0.0))
            mem_str = f"{peak_mem:.1f} MB" if peak_mem > 0.0 else "—"
            lines.append(f"| **{name}** | `{backend}` | {mtu} | {par} | {speed_gbps:.2f} Gbps | {peak_cpu:.1f}% | {mem_str} |")
    else:
        lines.append("| 测试用例 / 配置 | 后端协议栈 | MTU | 协议 | 流模式 | 上传吞吐 (Mbps) | 下载吞吐 (Mbps) | 平均/峰值 CPU (%) | 内存占用 (PSS) |")
        lines.append("| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")
        for c in cases:
            par = "单流" if c.get("parallel") == 1 else "P=8"
            net = c.get("network", "tcp").upper()
            up_loss_val = c.get("upload_loss_percent", 0.0)
            down_loss_val = c.get("download_loss_percent", 0.0)
            up_loss = f" ({up_loss_val:.1f}% 丢包)" if net == "UDP" and up_loss_val >= 0.1 else ""
            down_loss = f" ({down_loss_val:.1f}% 丢包)" if net == "UDP" and down_loss_val >= 0.1 else ""
            up_str = f"{c.get('upload_mbps', 0.0):.2f} Mbps{up_loss}"
            down_str = f"{c.get('download_mbps', 0.0):.2f} Mbps{down_loss}"
            up_cpu = c.get("upload_cpu_avg", 0.0)
            peak_cpu = c.get("peak_cpu", 0.0)
            peak_mem = c.get("peak_mem_mb", 0.0)
            mem_str = f"{peak_mem:.1f} MB" if peak_mem > 0.0 else "—"
            cpu_str = f"{up_cpu:.1f}% / {peak_cpu:.1f}%"
            name = c.get("name")
            backend = c.get("backend")
            mtu = c.get("mtu")
            lines.append(f"| **{name}** | `{backend}` | {mtu} | {net} | {par} | {up_str} | {down_str} | {cpu_str} | {mem_str} |")
    return "\n".join(lines) + "\n"


def render_idle_table(cases) -> str:
    lines = [
        "| 后端协议栈 | 传输协议 | 连接范围 | 基准 PSS (3轮均值) | 1000 连接 PSS (3轮均值) | 内存增长斜率 (3轮均值) |",
        "| :--- | :---: | :---: | :---: | :---: | :---: |"
    ]
    for c in cases:
        backend_str = c.get("backend", "").upper()
        network_str = c.get("network", "").upper()
        flows = c.get("idle_flows", [])
        base = flows[0].get("pss_mb", 0.0) if flows else 0.0
        end = flows[-1].get("pss_mb", 0.0) if flows else 0.0
        slope = c.get("slope_kib_per_conn", 0.0)
        slope_str = f"{slope:.2f} KiB/conn"
        if slope <= 0:
            slope_str = "~0.00 KiB/conn (GC 稳态)"
        lines.append(f"| **{backend_str}** | {network_str} | 0 -> 1000 | {base:.1f} MB | {end:.1f} MB | {slope_str} |")
    return "\n".join(lines) + "\n"


def build_section_3_1_content(avg_cases) -> str:
    usb_avg = [c for c in avg_cases if c.get("medium") == "USB 3.2 / 4.0"]
    wifi_avg = [c for c in avg_cases if c.get("medium") == "5GHz Wi-Fi"]
    loop_avg = [c for c in avg_cases if c.get("medium") == "On-Device Loopback"]
    idle_avg = [c for c in avg_cases if c.get("type") == "idle_memory"]

    parts = [
        "### 3.1 综合平均测试数据 (3 轮平均)\n",
        "注：本节各表格数据均为 3 轮完整实测的算术平均值。\n",
        "#### 3.1.1 USB 3.2 Gen1 / 4.0 有线以太网测试 (TCP & UDP)\n",
        render_table(usb_avg),
        "\n#### 3.1.2 5GHz Wi-Fi 无线网络测试 (TCP & UDP)\n",
        render_table(wifi_avg),
        "\n#### 3.1.3 设备内部纯回环压力测试\n",
        render_table(loop_avg, is_loopback=True),
        "\n#### 3.1.4 空闲连接驻留与内存增长 (TCP & UDP)\n",
        "注：本表数据为 3 轮独立实测的算术平均值，基准 PSS 为建立连接前的空闲内存，1000 连接 PSS 为阶梯压测达到 1000 连接并稳定 1.5 秒后采样的物理内存。\n",
        render_idle_table(idle_avg),
        "---\n\n"
    ]
    return "\n".join(parts)


def main():
    bench_root, _, _ = load_datasets()
    if not bench_root.get("rounds"):
        print(f"[Error] No rounds found in {DEFAULT_BENCH_JSON}")
        sys.exit(1)

    avg_cases = compute_clean_averages(bench_root)
    new_section_3_1 = build_section_3_1_content(avg_cases)

    if not os.path.exists(DOC_PATH):
        print(f"[Error] Markdown doc not found: {DOC_PATH}")
        sys.exit(1)

    with open(DOC_PATH, "r", encoding="utf-8") as f:
        doc_text = f.read()

    # In-place regex substitution of Section 3.1
    pattern = re.compile(
        r"(### 3\.1 综合平均测试数据 \(3 轮平均\)\n.*?\n)(?=### 3\.2 分轮实测数据明细)",
        re.DOTALL
    )

    if not pattern.search(doc_text):
        print("[Error] Could not locate Section 3.1 anchor in markdown document!")
        sys.exit(1)

    updated_doc = pattern.sub(new_section_3_1, doc_text)

    with open(DOC_PATH, "w", encoding="utf-8") as f:
        f.write(updated_doc)

    print(f"[Success] In-place updated Section 3.1 tables in: {DOC_PATH}")


if __name__ == "__main__":
    main()
