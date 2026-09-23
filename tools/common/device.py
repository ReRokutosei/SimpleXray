"""
Device profile resolution for benchmark datasets, charts, and reports.
"""

import os
from typing import Dict

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))

DEFAULT_DEVICE = "778g"

DEVICE_PROFILES: Dict[str, Dict[str, str]] = {
    "778g": {
        "name": "Snapdragon 778G",
        "dir": os.path.join(PROJECT_ROOT, "docs", "benchmark", "778g"),
        "dut_footer": "Qualcomm Snapdragon 778G, Android 14, Wi-Fi 6",
    },
    "8-elite-gen-5": {
        "name": "Snapdragon 8 Elite Gen 5",
        "dir": os.path.join(PROJECT_ROOT, "docs", "benchmark", "8-elite-gen-5"),
        "dut_footer": "Qualcomm Snapdragon 8 Elite Gen 5 (2×Prime + 6×Performance, up to 4.6 GHz), Android 16, Wi-Fi 7",
    },
}


def resolve_device_paths(device: str = DEFAULT_DEVICE) -> Dict[str, str]:
    """Returns standard data, chart, and report paths for a device profile."""
    profile = DEVICE_PROFILES.get(device)
    if profile is None:
        valid = ", ".join(sorted(DEVICE_PROFILES))
        raise SystemExit(f"Unknown device profile '{device}'. Valid profiles: {valid}")

    base_dir = profile["dir"]
    data_dir = os.path.join(base_dir, "data")
    charts_dir = os.path.join(base_dir, "charts")
    report_dir = os.path.join(base_dir, "report")

    return {
        "key": device,
        "name": profile["name"],
        "dut_footer": profile["dut_footer"],
        "root_dir": base_dir,
        "data_dir": data_dir,
        "charts_dir": charts_dir,
        "report_dir": report_dir,
        # Standardized symmetric data files
        "throughput_json": os.path.join(data_dir, "throughput.json"),
        "idle_memory_json": os.path.join(data_dir, "idle_memory.json"),
        "bufferbloat_json": os.path.join(data_dir, "bufferbloat.json"),
        "stability_json": os.path.join(data_dir, "stability.json"),
        "cps_json": os.path.join(data_dir, "cps.json"),
        "weaknet_json": os.path.join(data_dir, "weaknet.json"),
        "microbench_json": os.path.join(data_dir, "microbench.json"),
    }
