"""
Weak-network TUN benchmark suite.

Applies host-side netem delay/loss and measures a reverse-mode TCP download
through the selected Android/TUN backend.
"""

import time
from typing import Any, Dict, List, Optional

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

GO_BACKENDS = {"sing", "mips", "xray"}


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
