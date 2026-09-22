"""
TCP connection-per-second (CPS) suite.
"""

import json
import os
import subprocess
import time
from typing import Any, Dict, List

from common.adb import CpuProfiler
from common.logging import log_info, log_success, log_warn

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TOOLS_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
HOST_CPS_SERVER = os.path.join(TOOLS_DIR, "idle_bench", "idle_bench_linux_amd64")
DEVICE_CPS_CLIENT = os.path.join(TOOLS_DIR, "idle_bench", "idle_bench_linux_arm64")
CPS_PORT = 5302

GO_BACKENDS = {"sing", "mips", "xray"}


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
                f"cps={rec.get('cps', 0):.1f} success={rec.get('success', 0)}"
            )

    return results
