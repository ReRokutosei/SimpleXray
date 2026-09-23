"""
Dataset management, multi-round arithmetic averaging, and version property extraction.
"""

import json
import os
from typing import Any, Dict, List, Optional, Tuple

from .device import DEFAULT_DEVICE, resolve_device_paths

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))

_DEFAULT_DEVICE_PATHS = resolve_device_paths(DEFAULT_DEVICE)
DEFAULT_BENCH_JSON = _DEFAULT_DEVICE_PATHS["throughput_json"]
DEFAULT_MICRO_JSON = _DEFAULT_DEVICE_PATHS["microbench_json"]
DEFAULT_ADVANCED_JSON = _DEFAULT_DEVICE_PATHS["stability_json"]
VERSION_PROPS_PATH = os.path.join(PROJECT_ROOT, "version.properties")


def load_version_properties(props_path: Optional[str] = None) -> Dict[str, str]:
    """Reads key=value pairs from version.properties."""
    path = props_path or VERSION_PROPS_PATH
    props = {}
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    props[k.strip()] = v.strip()
    return props


def load_datasets(
    bench_path: Optional[str] = None,
    micro_path: Optional[str] = None,
    advanced_path: Optional[str] = None
) -> Tuple[Dict[str, Any], Dict[str, Any], Dict[str, Any]]:
    """Loads all 3 benchmark JSON datasets safely."""
    b_path = bench_path or DEFAULT_BENCH_JSON
    m_path = micro_path or DEFAULT_MICRO_JSON
    a_path = advanced_path or DEFAULT_ADVANCED_JSON

    bench_root = {}
    if os.path.exists(b_path):
        with open(b_path, "r", encoding="utf-8") as f:
            bench_root = json.load(f)

    micro_root = {}
    if os.path.exists(m_path):
        with open(m_path, "r", encoding="utf-8") as f:
            micro_root = json.load(f)

    advanced_root = {}
    if os.path.exists(a_path):
        try:
            with open(a_path, "r", encoding="utf-8") as f:
                advanced_root = json.load(f)
        except Exception:
            pass

    return bench_root, micro_root, advanced_root


def compute_clean_averages(bench_root: Dict[str, Any]) -> List[Dict[str, Any]]:
    """
    Computes multi-round arithmetic averages from benchmark_results.json rounds.
    Handles throughput, CPU, PSS, jitter, loss, and idle flow curves cleanly.
    """
    rounds = bench_root.get("rounds", {})
    if not rounds:
        return []

    first_round = list(rounds.values())[0]
    numeric_fields = [
        "upload_mbps", "download_mbps", "upload_cpu_avg", "upload_cpu_peak",
        "download_cpu_avg", "download_cpu_peak", "peak_cpu", "peak_mem_mb",
        "upload_loss_percent", "download_loss_percent", "upload_jitter_ms",
        "download_jitter_ms", "slope_kib_per_conn"
    ]

    avg_records = []
    for template in first_round:
        rec = dict(template)
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
                        val = m.get(fld)
                        if val is not None and isinstance(val, (int, float)) and val >= 0.0:
                            vals.append(float(val))
                        break
            if vals:
                rec[fld] = round(sum(vals) / len(vals), 3)

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


def get_rec(
    records: List[Dict[str, Any]],
    medium: str,
    backend: str,
    mtu: int,
    parallel: int,
    network: str = "tcp"
) -> Dict[str, Any]:
    """Finds matching record from records list."""
    for r in records:
        if (r.get("medium") == medium and
            r.get("backend") == backend and
            r.get("mtu") == mtu and
            r.get("parallel") == parallel and
            r.get("network", "tcp") == network):
            return r
    return {}
