"""
Weak-network TUN benchmark suite.

Applies host-side netem delay/loss and measures a reverse-mode TCP download
through the selected Android/TUN backend.
"""

import time
from typing import Any, Dict, List, Optional

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

from common.adb import CpuProfiler
from common.iperf import (
    DEFAULT_PORT,
    build_client_args,
    kill_host_server,
    parse_iperf_json,
    start_host_server,
)
from common.logging import log_info, log_success, log_warn
from common.netem import NetemController
from common.theme import (
    PALETTE,
    setup_fonts,
    apply_global_theme,
    add_dashboard_header,
    create_top_legend,
    save_dashboard,
)

GO_BACKENDS = {"sing", "xray"}


def run_weaknet_download_case(
    adb,
    app_uid: str,
    server_ip: str,
    backend: str,
    duration: int = 10,
    parallel: int = 8,
) -> Dict[str, Any]:
    """Runs one reverse-mode TCP download through a selected backend."""
    adb.wake_device()
    adb.set_app_state(backend, 1500, cmd="stop")
    adb.wait_for_no_tun0()

    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="start")
        adb.wait_for_tun0(backend)
    else:
        adb.ensure_no_tun0()

    server_proc = start_host_server(DEFAULT_PORT)
    profiler = CpuProfiler(adb, app_uid, duration)
    profiler.start()

    client_cmd = build_client_args(
        server_ip=server_ip,
        port=DEFAULT_PORT,
        network="tcp",
        parallel=parallel,
        duration=duration,
        reverse=True,
    )
    log_info(f"[weaknet] {backend} P={parallel} TCP download {duration}s")
    rc, out, err = adb.shell(client_cmd, timeout=duration + 20)

    avg_cpu, peak_cpu = profiler.stop()
    kill_host_server(server_proc)

    metrics = parse_iperf_json(out, network="tcp")
    mem_mb = adb.get_app_memory_mb()

    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="stop")
        adb.wait_for_no_tun0()

    if rc != 0:
        log_warn(f"[weaknet] {backend} exited with rc={rc}: {err.strip()[-200:]}")

    return {
        "backend": backend,
        "parallel": parallel,
        "duration": duration,
        "download_mbps": metrics["mbps"],
        "download_cpu_avg": avg_cpu,
        "download_cpu_peak": peak_cpu,
        "peak_mem_mb": mem_mb,
        "rc": rc,
        "stderr": err.strip()[-300:],
    }


def run_weaknet_suite(
    adb,
    app_uid: str,
    server_ip: str,
    backends: List[str],
    interface: str,
    loss_percent: float,
    delay_ms: float = 50.0,
    duration: int = 10,
    parallel: int = 8,
    dry_run: bool = False,
    skip_netem: bool = False,
) -> List[Dict[str, Any]]:
    """Runs weak-network downloads for all selected backends under one netem condition."""
    results: List[Dict[str, Any]] = []
    netem = NetemController(
        interface=interface,
        delay_ms=delay_ms,
        loss_percent=loss_percent,
        dry_run=dry_run,
    )

    if not skip_netem:
        netem.apply()
    try:
        prev_backend: Optional[str] = None
        for backend in backends:
            if (
                prev_backend is not None
                and prev_backend != "direct_none"
                and (backend in GO_BACKENDS or prev_backend in GO_BACKENDS)
            ):
                adb.force_reset_app()
            prev_backend = backend

            rec = run_weaknet_download_case(
                adb=adb,
                app_uid=app_uid,
                server_ip=server_ip,
                backend=backend,
                duration=duration,
                parallel=parallel,
            )
            rec["loss_percent"] = loss_percent
            rec["delay_ms"] = delay_ms
            rec["netem_interface"] = interface
            results.append(rec)
            log_success(
                f"[weaknet] {backend} loss={loss_percent:g}% delay={delay_ms:g}ms "
                f"download={rec['download_mbps']:.1f} Mbps"
            )
    finally:
        if not skip_netem:
            netem.clear()

    return results


def render_weaknet_chart(results: List[Dict[str, Any]], output_path: str) -> None:
    """Renders a grouped weak-network throughput chart for the available loss rates."""
    apply_global_theme()
    prop_regular, prop_bold, prop_medium = setup_fonts()

    backend_order = ["direct_none", "hev", "sing", "xray", "zeptun"]
    backends = [b for b in backend_order if any(r.get("backend") == b for r in results)]
    losses = sorted({float(r.get("loss_percent", 0.0)) for r in results})
    if not backends or not losses:
        raise RuntimeError("No weak-network records to render")

    fig = plt.figure(figsize=(16, 9.5), dpi=200)
    ax = fig.add_axes([0.10, 0.13, 0.86, 0.67])
    add_dashboard_header(
        fig,
        "Weak-Network TCP Download Throughput (Higher is Better)",
        "Host netem delay 50 ms with 1% and 3% random loss (5GHz Wi-Fi)",
        prop_bold=prop_bold,
        prop_regular=prop_regular,
    )

    x = np.arange(len(backends))
    width = 0.36
    loss_colors = {
        losses[0]: "#fdba74",
        losses[-1]: "#ea580c",
    }
    loss_edges = {
        losses[0]: "#fb923c",
        losses[-1]: "#c2410c",
    }

    for idx, loss in enumerate(losses):
        vals = []
        for b in backends:
            rec = next((r for r in results if r.get("backend") == b and float(r.get("loss_percent", 0.0)) == loss), None)
            vals.append(float(rec.get("download_mbps", 0.0)) if rec else 0.0)
        bars = ax.bar(x + (idx - 0.5) * width, vals, width,
                      label=f"{loss:g}% loss", color=loss_colors.get(loss, "#64748b"),
                      edgecolor=loss_edges.get(loss, "#475569"), linewidth=0.8, zorder=3)
        for bar, val in zip(bars, vals):
            ax.text(
                bar.get_x() + bar.get_width() / 2,
                bar.get_height() + max(vals + [1.0]) * 0.02,
                f"{val:.1f}",
                ha="center",
                va="bottom",
                fontsize=8.5,
                color="#1e293b",
                fontproperties=prop_bold,
            )

    display_names = {
        "direct_none": "Baseline",
        "hev": "HEV",
        "sing": "SingTUN",
        "xray": "Xray",
        "zeptun": "Zeptun",
    }
    ax.set_ylabel("Download Throughput (Mbps)", fontsize=10, color="#475569",
                  fontproperties=prop_regular)
    ax.set_xticks(x)
    ax.set_xticklabels([display_names[b] for b in backends], fontsize=10,
                       color="#334155", fontproperties=prop_medium)
    ax.grid(True, axis="y", color="#f1f5f9", linewidth=0.8, zorder=0)
    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor=loss_colors[loss], edgecolor=loss_edges[loss])
        for loss in losses
    ]
    create_top_legend(
        fig,
        legend_handles,
        [f"{loss:g}% loss" for loss in losses],
        y_pos=0.895,
        prop_medium=prop_medium,
    )
    save_dashboard(fig, output_path, dpi=200)
