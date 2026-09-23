"""
Standard physical throughput and latency test suites:
- Wi-Fi 5GHz (TCP & UDP, MTU 1500/9000, Single & Multi-Stream)
- USB 3.2 / 4.0 (TCP & UDP, MTU 1500/9000, Single & Multi-Stream)
- On-Device Loopback (Local 127.0.0.1 IPC stress testing)
"""

import time
from typing import Any, Dict, List, Optional

from common.adb import AdbRunner, CpuProfiler
from common.iperf import (
    start_host_server,
    kill_host_server,
    build_client_args,
    parse_iperf_json,
)
from common.logging import Colors, log_info, log_success, log_warn, log_error

DEFAULT_PORT = 5201
LOOPBACK_PORT = 5202


def run_throughput_case(
    adb: AdbRunner,
    app_uid: str,
    name: str,
    backend: str,
    mtu: int,
    server_ip: str,
    duration: int = 10,
    parallel: int = 1,
    medium: str = "5GHz Wi-Fi",
    network: str = "tcp",
    udp_bitrate: Optional[str] = None
) -> Dict[str, Any]:
    """
    Executes bidirectional (Upload + Download) throughput benchmark for a specific backend configuration.
    """
    adb.wake_device()

    par_desc = f" [P={parallel}]" if parallel > 1 else " [Single Stream]"
    net_desc = f" [{network.upper()}]" if network != "tcp" else ""
    full_name = f"{name}{net_desc}{par_desc}"

    print(f"\n{Colors.MAGENTA}======================================================={Colors.RESET}")
    log_info(f"Running: {full_name} ({medium}, Backend: {backend}, MTU: {mtu}, Target: {server_ip})")
    print(f"{Colors.MAGENTA}======================================================={Colors.RESET}")

    # Stop previous VPN instance
    adb.set_app_state(backend, mtu, cmd="stop")
    adb.wait_for_no_tun0()  # Wait for VPN fd to be fully released before starting next backend

    # Start VPN if not physical baseline
    if backend != "direct_none":
        adb.set_app_state(backend, mtu, cmd="start")
        adb.wait_for_tun0(backend)
    else:
        adb.ensure_no_tun0()

    bitrate = udp_bitrate or ("25M" if parallel > 1 else "200M")

    # 1. Upload Test (Android -> Host)
    log_info(f">>> [1/2] Testing {network.upper()} UPLOAD (Android -> Host, duration: {duration}s)...")
    server_proc = start_host_server(DEFAULT_PORT)
    profiler = CpuProfiler(adb, app_uid, duration)
    profiler.start()

    up_cmd = build_client_args(
        server_ip=server_ip,
        port=DEFAULT_PORT,
        network=network,
        parallel=parallel,
        duration=duration,
        reverse=False,
        bitrate=bitrate
    )
    rc, up_out, _ = adb.shell(up_cmd, timeout=duration + 15)
    up_avg_cpu, up_peak_cpu = profiler.stop()
    kill_host_server(server_proc)

    up_metrics = parse_iperf_json(up_out, network=network)
    up_mbps = up_metrics["mbps"]
    up_loss = up_metrics["loss_percent"]
    up_jitter = up_metrics["jitter_ms"]
    up_mem_mb = adb.get_app_memory_mb()

    loss_str = f" | Loss: {up_loss:.1f}% | Jitter: {up_jitter:.2f}ms" if network == "udp" else ""
    log_success(f"Upload: {up_mbps} Mbps{loss_str} | CPU: {up_avg_cpu}% (Peak: {up_peak_cpu}%) | MEM: {up_mem_mb} MB")

    time.sleep(1.5)

    # 2. Download Test (Host -> Android, reverse mode)
    log_info(f">>> [2/2] Testing {network.upper()} DOWNLOAD (Host -> Android, duration: {duration}s)...")
    server_proc = start_host_server(DEFAULT_PORT)
    profiler = CpuProfiler(adb, app_uid, duration)
    profiler.start()

    down_cmd = build_client_args(
        server_ip=server_ip,
        port=DEFAULT_PORT,
        network=network,
        parallel=parallel,
        duration=duration,
        reverse=True,
        bitrate=bitrate
    )
    rc, down_out, _ = adb.shell(down_cmd, timeout=duration + 15)
    down_avg_cpu, down_peak_cpu = profiler.stop()
    kill_host_server(server_proc)

    down_metrics = parse_iperf_json(down_out, network=network)
    down_mbps = down_metrics["mbps"]
    down_loss = down_metrics["loss_percent"]
    down_jitter = down_metrics["jitter_ms"]
    down_mem_mb = adb.get_app_memory_mb()

    loss_str = f" | Loss: {down_loss:.1f}% | Jitter: {down_jitter:.2f}ms" if network == "udp" else ""
    log_success(f"Download: {down_mbps} Mbps{loss_str} | CPU: {down_avg_cpu}% (Peak: {down_peak_cpu}%) | MEM: {down_mem_mb} MB")

    # Cleanup VPN
    if backend != "direct_none":
        adb.set_app_state(backend, mtu, cmd="stop")
        adb.wait_for_no_tun0()

    peak_cpu = max(up_peak_cpu, down_peak_cpu)
    peak_mem = max(up_mem_mb, down_mem_mb)

    res = {
        "name": full_name,
        "backend": backend,
        "mtu": mtu,
        "medium": medium,
        "network": network,
        "parallel": parallel,
        "upload_mbps": up_mbps,
        "download_mbps": down_mbps,
        "upload_cpu_avg": up_avg_cpu,
        "upload_cpu_peak": up_peak_cpu,
        "download_cpu_avg": down_avg_cpu,
        "download_cpu_peak": down_peak_cpu,
        "peak_cpu": peak_cpu,
        "peak_mem_mb": peak_mem
    }
    if network == "udp":
        res["upload_loss_percent"] = up_loss
        res["download_loss_percent"] = down_loss
        res["upload_jitter_ms"] = up_jitter
        res["download_jitter_ms"] = down_jitter

    return res


def run_loopback_case(
    adb: AdbRunner,
    app_uid: str,
    name: str,
    backend: str,
    mtu: int,
    duration: int = 10,
    parallel: int = 1
) -> Dict[str, Any]:
    """Executes on-device loopback IPC stress test against local 127.0.0.1 port."""
    par_desc = f" [P={parallel}]" if parallel > 1 else " [Single Stream]"
    full_name = f"{name}{par_desc}"

    print(f"\n{Colors.MAGENTA}======================================================={Colors.RESET}")
    log_info(f"Running: {full_name} (On-Device Loopback, Backend: {backend}, MTU: {mtu})")
    print(f"{Colors.MAGENTA}======================================================={Colors.RESET}")

    adb.set_app_state(backend, mtu, cmd="stop")
    adb.wait_for_no_tun0()  # Wait for VPN fd to be fully released before starting next backend

    if backend != "direct_none":
        adb.set_app_state(backend, mtu, cmd="start")
        adb.wait_for_tun0(backend)

    # Launch background iperf3 server on device loopback port
    adb.shell(f"pkill iperf3; (nohup /data/local/tmp/iperf3 -s -p {LOOPBACK_PORT} > /dev/null 2>&1 &)")
    time.sleep(1.0)

    par_flags = f"-P {parallel} -l 64K" if parallel > 1 else ""
    log_info(f">>> Testing ON-DEVICE LOOPBACK (duration: {duration}s)...")
    profiler = CpuProfiler(adb, app_uid, duration)
    profiler.start()

    loop_cmd = f"/data/local/tmp/iperf3 -c 127.0.0.1 -p {LOOPBACK_PORT} -t {duration} {par_flags} -J"
    rc, loop_out, _ = adb.shell(loop_cmd, timeout=duration + 15)
    loop_avg_cpu, loop_peak_cpu = profiler.stop()

    adb.shell("pkill iperf3")

    metrics = parse_iperf_json(loop_out, network="tcp")
    loop_mbps = metrics["mbps"]
    loop_gbps = round(loop_mbps / 1000.0, 2)
    loop_mem_mb = adb.get_app_memory_mb()

    log_success(f"Loopback: {loop_gbps} Gbps ({loop_mbps} Mbps) | CPU: {loop_avg_cpu}% (Peak: {loop_peak_cpu}%) | MEM: {loop_mem_mb} MB")

    if backend != "direct_none":
        adb.set_app_state(backend, mtu, cmd="stop")
        time.sleep(2)

    return {
        "name": full_name,
        "backend": backend,
        "mtu": mtu,
        "medium": "On-Device Loopback",
        "network": "tcp",
        "parallel": parallel,
        "speed_gbps": loop_gbps,
        "upload_mbps": loop_mbps,
        "download_mbps": loop_mbps,
        "upload_cpu_avg": loop_avg_cpu,
        "upload_cpu_peak": loop_peak_cpu,
        "download_cpu_avg": loop_avg_cpu,
        "download_cpu_peak": loop_peak_cpu,
        "peak_cpu": loop_peak_cpu,
        "peak_mem_mb": loop_mem_mb
    }


def run_media_suite(
    adb: AdbRunner,
    app_uid: str,
    medium: str,
    server_ip: str,
    backends: List[str],
    networks: List[str],
    skip_baseline: bool,
    duration: int,
    include_jumbo: bool = True
) -> List[Dict[str, Any]]:
    """Runs standard media throughput matrix for Wi-Fi or USB."""
    results = []
    prefix = "Wi-Fi" if "Wi-Fi" in medium else "USB"

    # 1. Baseline
    if not skip_baseline:
        if "tcp" in networks:
            results.append(run_throughput_case(
                adb, app_uid, f"{prefix} Baseline (No VPN)", "direct_none", 0, server_ip,
                duration=duration, parallel=1, medium=medium, network="tcp"
            ))
            results.append(run_throughput_case(
                adb, app_uid, f"{prefix} Baseline (No VPN)", "direct_none", 0, server_ip,
                duration=duration, parallel=8, medium=medium, network="tcp"
            ))
        if "udp" in networks:
            results.append(run_throughput_case(
                adb, app_uid, f"{prefix} Baseline (No VPN)", "direct_none", 0, server_ip,
                duration=duration, parallel=1, medium=medium, network="udp"
            ))
            results.append(run_throughput_case(
                adb, app_uid, f"{prefix} Baseline (No VPN)", "direct_none", 0, server_ip,
                duration=duration, parallel=8, medium=medium, network="udp"
            ))

    # Backend name mapping
    name_map = {
        "hev": "Hev",
        "xray": "Xray TUN",
        "sing": "SingTUN",
        "zeptun": "Zeptun"
    }

    # Go-based TUN backends that carry an independent Go runtime in their .so
    GO_BACKENDS = {"sing", "xray"}
    prev_backend: Optional[str] = None

    # 2. MTU 1500 & MTU 9000
    for b in backends:
        b_name = name_map.get(b, b.upper())

        # Force-reset the app process when transitioning between Go-based TUN backends to
        # prevent fatal Go runtime conflicts between independent Go runtimes;
        # sequential load in the same process triggers
        # "fatal error: unknown caller pc" via cgocallbackg).
        if prev_backend is not None and (b in GO_BACKENDS or prev_backend in GO_BACKENDS):
            adb.force_reset_app()
        prev_backend = b

        # TCP MTU 1500
        if "tcp" in networks:
            results.append(run_throughput_case(
                adb, app_uid, f"{b_name} (MTU 1500)", b, 1500, server_ip,
                duration=duration, parallel=1, medium=medium, network="tcp"
            ))
            if include_jumbo:
                results.append(run_throughput_case(
                    adb, app_uid, f"{b_name} (MTU 9000)", b, 9000, server_ip,
                    duration=duration, parallel=1, medium=medium, network="tcp"
                ))
            # Multi-stream P=8
            results.append(run_throughput_case(
                adb, app_uid, f"{b_name} (MTU 1500)", b, 1500, server_ip,
                duration=duration, parallel=8, medium=medium, network="tcp"
            ))
            if include_jumbo:
                results.append(run_throughput_case(
                    adb, app_uid, f"{b_name} (MTU 9000)", b, 9000, server_ip,
                    duration=duration, parallel=8, medium=medium, network="tcp"
                ))

        # UDP MTU 1500
        if "udp" in networks:
            results.append(run_throughput_case(
                adb, app_uid, f"{b_name} (MTU 1500)", b, 1500, server_ip,
                duration=duration, parallel=1, medium=medium, network="udp"
            ))
            results.append(run_throughput_case(
                adb, app_uid, f"{b_name} (MTU 1500)", b, 1500, server_ip,
                duration=duration, parallel=8, medium=medium, network="udp"
            ))

    return results



def run_loopback_suite(
    adb: AdbRunner,
    app_uid: str,
    backends: List[str],
    skip_baseline: bool,
    duration: int
) -> List[Dict[str, Any]]:
    """Runs standard loopback stress matrix."""
    results = []
    if not skip_baseline:
        results.append(run_loopback_case(adb, app_uid, "Loopback Baseline (No VPN)", "direct_none", 0, duration=duration, parallel=1))
        results.append(run_loopback_case(adb, app_uid, "Loopback Baseline (No VPN)", "direct_none", 0, duration=duration, parallel=8))

    GO_BACKENDS = {"sing", "xray"}
    name_map = {
        "hev": "Hev",
        "xray": "Xray TUN",
        "sing": "SingTUN",
        "zeptun": "Zeptun",
    }

    prev_backend: Optional[str] = None
    for b in backends:
        b_name = name_map.get(b, b.upper())
        # Force-reset between Go-based backends to prevent Go runtime cgo conflicts.
        if prev_backend is not None and (b in GO_BACKENDS or prev_backend in GO_BACKENDS):
            adb.force_reset_app()
        prev_backend = b
        results.append(run_loopback_case(adb, app_uid, f"{b_name} (MTU 9000)", b, 9000, duration=duration, parallel=1))

    # The previous loop leaves the last Go backend stopped in this process. Start
    # the parallel loop from a clean process as well, otherwise its first backend
    # can inherit the previous Go runtime's native state.
    if backends and any(b in GO_BACKENDS for b in backends):
        adb.force_reset_app()

    prev_backend = None
    for b in backends:
        b_name = name_map.get(b, b.upper())
        if prev_backend is not None and (b in GO_BACKENDS or prev_backend in GO_BACKENDS):
            adb.force_reset_app()
        prev_backend = b
        results.append(run_loopback_case(adb, app_uid, f"{b_name} (MTU 9000)", b, 9000, duration=duration, parallel=8))

    return results
