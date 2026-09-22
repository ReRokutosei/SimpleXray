#!/usr/bin/env python3
"""Generate only the chart families used by the lightweight benchmark report.

Included:
  - 5 GHz Wi-Fi TCP throughput
  - Android idle-memory slope

USB, loopback, UDP jitter, CPU-efficiency, per-round dashboards, and the
full-stack attribution chart are intentionally outside this lightweight entry
point. Bufferbloat, long-run, CPS, and weak-network charts are maintained by
their respective benchmark/report data pipelines.
"""

import argparse
import json
import os

from common.dataset import compute_clean_averages
from common.device import DEFAULT_DEVICE, DEVICE_PROFILES, resolve_device_paths
from common.theme import PALETTE  # noqa: F401 (keeps theme initialization consistent)
from generate_charts import render_idle_memory_dashboard, render_throughput_dashboard


BACKEND_ORDER = ["xray", "sing", "zeptun", "hev"]


def _value(records, medium, backend, mtu, parallel, field, network="tcp"):
    for record in records:
        if (record.get("medium") == medium and record.get("backend") == backend
                and record.get("mtu") == mtu and record.get("parallel") == parallel
                and record.get("network", "tcp") == network):
            return float(record.get(field, 0.0) or 0.0)
    return 0.0


def render_wifi_tcp(records, output_dir, dut_label):
    categories = [
        {"key": "tcp_down_1500", "label": "TCP Download (MTU 1500)"},
        {"key": "tcp_up_1500", "label": "TCP Upload (MTU 1500)"},
        {"key": "tcp_down_9000", "label": "TCP Download (Jumbo 9000)"},
        {"key": "tcp_up_9000", "label": "TCP Upload (Jumbo 9000)"},
    ]
    single, multi, single_loss, multi_loss = {}, {}, {}, {}
    base_single, base_multi = {}, {}
    for category in categories:
        key = category["key"]
        is_upload = "up" in key
        field = "upload_mbps" if is_upload else "download_mbps"
        loss_field = "upload_loss_percent" if is_upload else "download_loss_percent"
        mtu = 9000 if "9000" in key else 1500
        single[key], multi[key] = {}, {}
        single_loss[key], multi_loss[key] = {}, {}
        for backend in BACKEND_ORDER:
            single[key][backend] = _value(records, "5GHz Wi-Fi", backend, mtu, 1, field)
            multi[key][backend] = _value(records, "5GHz Wi-Fi", backend, mtu, 8, field)
            single_loss[key][backend] = _value(records, "5GHz Wi-Fi", backend, mtu, 1, loss_field)
            multi_loss[key][backend] = _value(records, "5GHz Wi-Fi", backend, mtu, 8, loss_field)
        base_single[key] = _value(records, "5GHz Wi-Fi", "direct_none", 0, 1, field)
        base_multi[key] = _value(records, "5GHz Wi-Fi", "direct_none", 0, 8, field)

    max_value = max(
        [100.0]
        + [value for values in single.values() for value in values.values()]
        + [value for values in multi.values() for value in values.values()]
        + list(base_single.values()) + list(base_multi.values())
    )
    render_throughput_dashboard(
        os.path.join(output_dir, "wifi_throughput_dashboard.webp"),
        "5GHz Wi-Fi TCP Throughput (Mbps)",
        f"Lightweight benchmark (DUT: {dut_label})",
        categories, single, multi, base_single, base_multi, max_value,
        single_losses=single_loss, multi_losses=multi_loss,
    )
    render_throughput_dashboard(
        os.path.join(output_dir, "avg_wifi_throughput_dashboard.webp"),
        "5GHz Wi-Fi TCP Throughput (Mbps)",
        f"Lightweight benchmark average (DUT: {dut_label})",
        categories, single, multi, base_single, base_multi, max_value,
        single_losses=single_loss, multi_losses=multi_loss,
    )


def render_idle(records, output_dir):
    tcp_idle = {backend: [] for backend in BACKEND_ORDER}
    empty_udp = {backend: [] for backend in BACKEND_ORDER}
    for record in records:
        if record.get("type") != "idle_memory" or record.get("network", "tcp") != "tcp":
            continue
        backend = record.get("backend")
        flows = record.get("idle_flows", [])
        if backend not in tcp_idle or not flows:
            continue
        baseline = flows[0].get("pss_mb", 0.0)
        tcp_idle[backend] = [
            (flow.get("connections", 0), round(flow.get("pss_mb", 0.0) - baseline, 2))
            for flow in flows
        ]
    render_idle_memory_dashboard(
        os.path.join(output_dir, "idle_memory_dashboard.webp"),
        "Retained TCP Idle Connections vs Process Memory Growth",
        "SimpleXray process PSS growth above baseline (0 -> 1000 connections)",
        tcp_idle, empty_udp,
    )


def main():
    parser = argparse.ArgumentParser(description="Generate lightweight benchmark charts")
    parser.add_argument("--device", default=DEFAULT_DEVICE, choices=sorted(DEVICE_PROFILES))
    parser.add_argument("--json", default=None)
    parser.add_argument("--output-dir", default=None)
    parser.add_argument("--dut", default=None)
    args = parser.parse_args()
    profile = resolve_device_paths(args.device)
    json_path = args.json or profile["bench_json"]
    output_dir = args.output_dir or profile["charts_dir"]
    os.makedirs(output_dir, exist_ok=True)
    with open(json_path, "r", encoding="utf-8") as handle:
        root = json.load(handle)
    records = compute_clean_averages(root)
    dut_label = args.dut or profile["name"]
    render_wifi_tcp(records, output_dir, dut_label)
    if any(record.get("type") == "idle_memory" for record in records):
        render_idle(records, output_dir)
    print("Generated lightweight Wi-Fi TCP and idle-memory charts.")


if __name__ == "__main__":
    main()
