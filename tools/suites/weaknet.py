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


def run_weaknet_case(
    adb,
    app_uid: str,
    server_ip: str,
    backend: str,
    duration: int = 10,
    parallel: int = 8,
    reverse: bool = True,
) -> Dict[str, Any]:
    """Runs one TCP upload or download through a selected backend."""
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

    direction = "download" if reverse else "upload"
    client_cmd = build_client_args(
        server_ip=server_ip,
        port=DEFAULT_PORT,
        network="tcp",
        parallel=parallel,
        duration=duration,
        reverse=reverse,
    )
    log_info(f"[weaknet] {backend} P={parallel} TCP {direction} {duration}s")
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
        "direction": direction,
        "throughput_mbps": metrics["mbps"],
        "download_mbps": metrics["mbps"] if reverse else 0.0,
        "upload_mbps": metrics["mbps"] if not reverse else 0.0,
        "download_cpu_avg": avg_cpu,
        "download_cpu_peak": peak_cpu,
        "peak_mem_mb": mem_mb,
        "rc": rc,
        "stderr": err.strip()[-300:],
    }


# Backward-compatible alias
run_weaknet_download_case = run_weaknet_case


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

            rec = run_weaknet_case(
                adb=adb,
                app_uid=app_uid,
                server_ip=server_ip,
                backend=backend,
                duration=duration,
                parallel=parallel,
                reverse=False,
            )
            upload_rec = rec
            rec = run_weaknet_case(
                adb=adb, app_uid=app_uid, server_ip=server_ip, backend=backend,
                duration=duration, parallel=parallel, reverse=True,
            )
            for item in (upload_rec, rec):
                item["loss_percent"] = loss_percent
                item["delay_ms"] = delay_ms
                item["netem_interface"] = interface
                results.append(item)
            log_success(
                f"[weaknet] {backend} loss={loss_percent:g}% delay={delay_ms:g}ms | "
                f"Upload: {upload_rec['upload_mbps']:.1f} Mbps | Download: {rec['download_mbps']:.1f} Mbps"
            )
    finally:
        if not skip_netem:
            netem.clear()

    return results


def render_weaknet_chart(results: List[Dict[str, Any]], output_path: str) -> None:
    """Renders grouped weak-network throughput charts for Upload and Download across loss rates."""
    apply_global_theme()
    prop_regular, prop_bold, prop_medium = setup_fonts()

    backend_order = ["direct_none", "hev", "sing", "xray", "zeptun"]
    backends = [b for b in backend_order if any(r.get("backend") == b for r in results)]
    losses = sorted({float(r.get("loss_percent", 0.0)) for r in results})
    if not backends or not losses:
        raise RuntimeError("No weak-network records to render")

    fig = plt.figure(figsize=(16, 9.5), dpi=200)
    axes = fig.subplots(1, 2)
    fig.subplots_adjust(left=0.08, right=0.96, top=0.78, bottom=0.12, wspace=0.18)

    add_dashboard_header(
        fig,
        "Weak-Network TCP Throughput (Higher is Better)",
        "Host netem delay 50 ms across loss rates (5GHz Wi-Fi P=8)",
        prop_bold=prop_bold,
        prop_regular=prop_regular,
    )

    num_losses = len(losses)
    width = 0.8 / max(num_losses, 1)

    # Dynamic color palette for loss rates using orange gradients
    cmap = plt.cm.Oranges
    loss_colors = {}
    loss_edges = {}
    for idx, loss in enumerate(losses):
        factor = 0.35 + 0.55 * (idx / max(num_losses - 1, 1))
        loss_colors[loss] = cmap(factor)
        loss_edges[loss] = cmap(min(factor + 0.15, 1.0))

    display_names = {
        "direct_none": "Baseline",
        "hev": "Hev",
        "sing": "SingTUN",
        "xray": "Xray",
        "zeptun": "Zeptun",
    }

    directions = [("upload", "TCP Upload (Android → Host)", "upload_mbps"),
                  ("download", "TCP Download (Host → Android)", "download_mbps")]

    x = np.arange(len(backends))

    for axis, (dir_key, dir_title, metric_key) in zip(axes, directions):
        all_vals = []
        for idx, loss in enumerate(losses):
            vals = []
            for b in backends:
                rec = next((
                    r for r in results
                    if r.get("backend") == b
                    and float(r.get("loss_percent", 0.0)) == loss
                    and (r.get("direction") == dir_key or (dir_key == "download" and "direction" not in r))
                ), None)
                if rec:
                    v = float(rec.get(metric_key, rec.get("throughput_mbps", 0.0)))
                else:
                    v = 0.0
                vals.append(v)
            all_vals.extend(vals)

            offset = (idx - (num_losses - 1) / 2.0) * width
            bars = axis.bar(
                x + offset, vals, width,
                color=loss_colors[loss],
                edgecolor=loss_edges[loss],
                linewidth=0.8,
                zorder=3
            )
            for bar, val in zip(bars, vals):
                if val > 0:
                    axis.text(
                        bar.get_x() + bar.get_width() / 2,
                        bar.get_height() + max(all_vals + [1.0]) * 0.02,
                        f"{val:.1f}",
                        ha="center", va="bottom",
                        fontsize=7.5,
                        color="#1e293b",
                        fontproperties=prop_bold,
                        zorder=5,
                    )

        axis.set_title(dir_title, fontproperties=prop_bold, fontsize=12, pad=12, color="#0f172a")
        axis.set_ylabel("Throughput (Mbps)", fontsize=10, color="#64748b", fontproperties=prop_regular)
        axis.set_xticks(x)
        axis.set_xticklabels([display_names.get(b, b) for b in backends], fontsize=10,
                             color="#334155", fontproperties=prop_medium)
        max_y = max(all_vals + [10.0]) * 1.25
        axis.set_ylim(0, max_y)
        axis.grid(True, axis="y", color="#f1f5f9", linewidth=0.8, zorder=0)
        axis.set_axisbelow(True)

    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor=loss_colors[loss], edgecolor=loss_edges[loss])
        for loss in losses
    ]
    create_top_legend(
        fig,
        legend_handles,
        [f"{loss:g}% Loss" for loss in losses],
        y_pos=0.895,
        prop_medium=prop_medium,
    )
    save_dashboard(fig, output_path, dpi=200)

