"""
TCP connection-per-second (CPS) suite.
"""

import json
import os
import subprocess
import time
from typing import Any, Dict, List

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

from common.adb import CpuProfiler
from common.logging import log_info, log_success, log_warn
from common.theme import (
    setup_fonts,
    apply_global_theme,
    add_dashboard_header,
    create_top_legend,
    save_dashboard,
)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TOOLS_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
HOST_CPS_SERVER = os.path.join(TOOLS_DIR, "idle_bench", "idle_bench_linux_amd64")
DEVICE_CPS_CLIENT = os.path.join(TOOLS_DIR, "idle_bench", "idle_bench_linux_arm64")
CPS_PORT = 5302

GO_BACKENDS = {"sing", "xray"}


def _push_cps_client(adb) -> None:
    if not os.path.exists(DEVICE_CPS_CLIENT):
        raise RuntimeError(f"CPS client not found: {DEVICE_CPS_CLIENT}")
    adb.exec(["push", DEVICE_CPS_CLIENT, "/data/local/tmp/idle_bench"])
    adb.shell("chmod 755 /data/local/tmp/idle_bench")


def run_cps_case(
    adb,
    app_uid: str,
    server_ip: str,
    backend: str,
    workers: int = 8,
    connections: int = 5000,
    timeout_ms: int = 3000,
    timeout_sec: int = 120,
) -> Dict[str, Any]:
    if not os.path.exists(HOST_CPS_SERVER):
        raise RuntimeError(f"CPS server not found: {HOST_CPS_SERVER}")

    adb.wake_device()
    adb.set_app_state(backend, 1500, cmd="stop")
    adb.wait_for_no_tun0()

    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="start")
        adb.wait_for_tun0(backend)
    else:
        adb.ensure_no_tun0()

    server_proc = subprocess.Popen(
        [HOST_CPS_SERVER, "server", "--port", str(CPS_PORT)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    time.sleep(0.5)

    profiler = CpuProfiler(adb, app_uid, max(5, connections // max(workers, 1) // 100))
    profiler.start()
    client_cmd = (
        f"/data/local/tmp/idle_bench cps --server {server_ip}:{CPS_PORT} "
        f"--connections {connections} --workers {workers} --timeout {timeout_ms}ms"
    )
    log_info(f"[cps] {backend} workers={workers} connections={connections}")
    rc, out, err = adb.shell(client_cmd, timeout=timeout_sec + 10)
    avg_cpu, peak_cpu = profiler.stop()

    server_proc.terminate()
    try:
        server_proc.wait(timeout=2)
    except subprocess.TimeoutExpired:
        server_proc.kill()

    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="stop")
        adb.wait_for_no_tun0()

    parsed: Dict[str, Any] = {}
    for line in reversed(out.splitlines()):
        line = line.strip()
        if line.startswith("{"):
            try:
                parsed = json.loads(line)
                break
            except json.JSONDecodeError:
                continue

    if rc != 0 and not parsed:
        log_warn(f"[cps] {backend} failed rc={rc}: {err.strip()[-200:]}")

    parsed.update({
        "backend": backend,
        "workers": workers,
        "requested_connections": connections,
        "cpu_avg": avg_cpu,
        "cpu_peak": peak_cpu,
        "rc": rc,
        "stderr": err.strip()[-300:],
    })
    return parsed


def run_cps_suite(
    adb,
    app_uid: str,
    server_ip: str,
    backends: List[str],
    workers_list: List[int],
    connections: int = 5000,
    timeout_sec: int = 120,
) -> List[Dict[str, Any]]:
    _push_cps_client(adb)
    results: List[Dict[str, Any]] = []
    prev_backend = None

    for backend in backends:
        if (
            prev_backend is not None
            and prev_backend != "direct_none"
            and (backend in GO_BACKENDS or prev_backend in GO_BACKENDS)
        ):
            adb.force_reset_app()
        prev_backend = backend

        for workers in workers_list:
            if backend == "hev" and connections >= 5000:
                log_info(f"[cps] {backend} workers={workers} connections={connections}: Skipping known lwIP PCB limit")
                rec = {
                    "backend": "hev",
                    "workers": workers,
                    "requested_connections": connections,
                    "cpu_avg": None,
                    "cpu_peak": None,
                    "rc": -1,
                    "stderr": "Skipped: Exceeds lwIP PCB capacity (Known limitation)",
                }
            else:
                rec = run_cps_case(
                    adb=adb,
                    app_uid=app_uid,
                    server_ip=server_ip,
                    backend=backend,
                    workers=workers,
                    connections=connections,
                    timeout_sec=timeout_sec,
                )
            results.append(rec)
            log_success(
                f"[cps] {backend} workers={workers} "
                f"cps={rec.get('cps') or 0:.1f} success={rec.get('success', 0)}"
            )

    return results


def render_cps_chart(results: List[Dict[str, Any]], output_path: str) -> None:
    """Renders a grouped CPS bar chart for 4 and 8 worker cases."""
    apply_global_theme()
    prop_regular, prop_bold, prop_medium = setup_fonts()

    workers_list = [4, 8]
    backend_order = ["direct_none", "hev", "sing", "xray", "zeptun", "simpletun"]
    valid_backends = {
        r.get("backend")
        for r in results
        if r.get("rc") == 0 and r.get("cps") is not None
    }
    backends = [b for b in backend_order if b in valid_backends]
    if not backends:
        raise RuntimeError("No valid CPS records to render")

    fig = plt.figure(figsize=(16, 9.5), dpi=200)
    ax = fig.add_axes([0.10, 0.13, 0.86, 0.67])
    add_dashboard_header(
        fig,
        "TCP Connection Rate (CPS) (Higher is Better)",
        "Short-lived TCP connections through Android/TUN backends (5GHz Wi-Fi)",
        prop_bold=prop_bold,
        prop_regular=prop_regular,
    )

    x = np.arange(len(backends))
    width = 0.36
    colors = {4: "#F08080", 8: "#CD5C5C"}
    edges = {4: "#D96C6C", 8: "#A94442"}

    for idx, workers in enumerate(workers_list):
        vals = []
        labels = []
        for b in backends:
            rec = next((r for r in results if r.get("backend") == b and r.get("workers") == workers), None)
            if rec is None or rec.get("rc") != 0 or rec.get("cps") is None:
                vals.append(0.0)
                labels.append("N/A")
            else:
                vals.append(float(rec.get("cps") or 0.0))
                labels.append(f"{float(rec.get('cps') or 0.0):.1f}")

        bars = ax.bar(x + (idx - 0.5) * width, vals, width,
                      label=f"{workers} worker", color=colors[workers],
                      edgecolor=edges[workers], linewidth=0.8, zorder=3)
        for bar, label, val in zip(bars, labels, vals):
            ax.text(
                bar.get_x() + bar.get_width() / 2,
                bar.get_height() + max(vals + [1.0]) * 0.02,
                label,
                ha="center",
                va="bottom",
                fontsize=8.5,
                color="#1e293b",
                fontproperties=prop_bold,
            )

    ax.set_ylabel("Connections per second", fontsize=10, color="#475569",
                  fontproperties=prop_regular)
    ax.set_xticks(x)
    display_names = {
        "direct_none": "Baseline",
        "hev": "HEV",
        "sing": "SingTUN",
        "xray": "Xray",
        "zeptun": "Zeptun",
        "simpletun": "SimpleTUN",
    }
    ax.set_xticklabels(
        [display_names[b] for b in backends],
        fontsize=10,
        color="#334155",
        fontproperties=prop_medium,
    )
    ax.grid(True, axis="y", color="#f1f5f9", linewidth=0.8, zorder=0)
    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor=colors[w], edgecolor=edges[w])
        for w in workers_list
    ]
    create_top_legend(
        fig,
        legend_handles,
        [f"{w} worker" for w in workers_list],
        y_pos=0.895,
        prop_medium=prop_medium,
    )
    save_dashboard(fig, output_path, dpi=200)
