"""
Idle connection retention (0 -> 1000 flows) and userspace PSS memory slope suite.
"""

import json
import os
import subprocess
import time
from typing import Any, Dict, List

from common.adb import AdbRunner
from common.logging import Colors, log_info, log_success, log_warn

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TOOLS_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))


def run_idle_flow_case(
    adb: AdbRunner,
    backend: str,
    network: str,
    server_ip: str,
    port: int = 5301
) -> Dict[str, Any]:
    """
    Executes stepped (0 -> 250 -> 500 -> 750 -> 1000) idle connection retention test,
    sampling PSS memory at each steady step and computing the growth slope.
    """
    full_name = f"{backend.upper()} {network.upper()} Idle Flows (0-1000)"
    print(f"\n{Colors.MAGENTA}======================================================={Colors.RESET}")
    log_info(f"Running Idle Flow Benchmark: {full_name} on {backend} (Target: {server_ip}:{port})")
    print(f"{Colors.MAGENTA}======================================================={Colors.RESET}")

    server_bin = os.path.join(TOOLS_DIR, "idle_bench", "idle_bench_linux_amd64")
    if not os.path.exists(server_bin):
        raise RuntimeError(f"Host idle probe binary not found: {server_bin}")

    server_proc = subprocess.Popen(
        [server_bin, "server", "--port", str(port)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    )
    time.sleep(0.5)

    adb.set_app_state(backend, 1500, cmd="stop")
    time.sleep(2)
    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="start")
        adb.wait_for_tun0(backend)

    client_bin_host = os.path.join(TOOLS_DIR, "idle_bench", "idle_bench_linux_arm64")
    adb.exec(["push", client_bin_host, "/data/local/tmp/idle_bench"])
    adb.shell("chmod 755 /data/local/tmp/idle_bench")

    client_cmd = (
        f"/data/local/tmp/idle_bench client --server {server_ip}:{port} "
        f"--network {network} --steps 0,250,500,750,1000 --settle 1s --auto"
    )

    log_info(f"Executing idle probe on device: {client_cmd}")
    proc = subprocess.Popen(
        ["adb", "-s", adb.device, "shell", client_cmd],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
    )

    flow_records = []
    base_mem = 0.0

    for line in iter(proc.stdout.readline, ''):
        line = line.strip()
        if not line:
            continue
        try:
            data = json.loads(line)
            if data.get("status") == "settled":
                step = data.get("step", 0)
                mem_mb = adb.get_app_memory_mb()
                if step == 0:
                    base_mem = mem_mb
                delta = round(mem_mb - base_mem, 2)
                flow_records.append({"connections": step, "pss_mb": mem_mb, "delta_mb": delta})
                log_success(f"[Idle {network.upper()}] Conns: {step:4d} | PSS: {mem_mb:6.1f} MB (Delta: +{delta:5.2f} MB)")
        except Exception:
            pass

    proc.wait()
    server_proc.terminate()
    try:
        server_proc.wait(timeout=2)
    except subprocess.TimeoutExpired:
        server_proc.kill()

    if backend != "direct_none":
        adb.set_app_state(backend, 1500, cmd="stop")
        time.sleep(2)

    slope_kib = 0.0
    if len(flow_records) >= 2:
        first = flow_records[0]
        last = flow_records[-1]
        if last["connections"] > first["connections"]:
            slope_kib = round(
                (last["pss_mb"] - first["pss_mb"]) * 1024.0 / (last["connections"] - first["connections"]),
                3
            )

    log_info(f"Idle flows complete. Slope: {slope_kib:.2f} KiB / connection")

    return {
        "name": full_name,
        "backend": backend,
        "network": network,
        "type": "idle_memory",
        "idle_flows": flow_records,
        "slope_kib_per_conn": slope_kib
    }


def run_idle_suite(
    adb: AdbRunner,
    server_ip: str,
    backends: List[str],
    networks: List[str],
    port: int = 5301
) -> List[Dict[str, Any]]:
    """Runs idle flow memory test for all specified backends and protocols."""
    results = []
    for b in backends:
        for net in networks:
            results.append(run_idle_flow_case(adb, b, net, server_ip, port=port))
    return results
