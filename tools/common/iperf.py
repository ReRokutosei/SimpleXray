"""
iPerf3 process lifecycle control, argument construction, and JSON output parsing.
"""

import json
import shutil
import subprocess
import time
from typing import Any, Dict, Optional

from .logging import log_warn

DEFAULT_PORT = 5201


def get_host_iperf3_path() -> str:
    path = shutil.which("iperf3")
    if not path:
        raise RuntimeError("iperf3 binary not found on host machine. Please install it.")
    return path


def start_host_server(port: int = 5201, host_iperf_path: Optional[str] = None) -> subprocess.Popen:
    """
    Starts an ephemeral iperf3 server on the host:
    -s -1 exits cleanly after one client session finishes.
    """
    iperf_bin = host_iperf_path or get_host_iperf3_path()
    cmd = [iperf_bin, "-s", "-1", "-p", str(port)]
    proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.0)
    return proc


def kill_host_server(proc: Optional[subprocess.Popen]):
    """Cleanly terminates the host iperf3 server if still running."""
    if proc and proc.poll() is None:
        proc.terminate()
        try:
            proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            proc.kill()


def build_client_args(
    server_ip: str,
    port: int = 5201,
    network: str = "tcp",
    parallel: int = 1,
    duration: int = 10,
    reverse: bool = False,
    bitrate: Optional[str] = None,
    iperf_path: str = "/data/local/tmp/iperf3"
) -> str:
    """Builds the shell command string for running iperf3 client on Android device."""
    flags = [iperf_path, "-c", server_ip, "-p", str(port)]
    if reverse:
        flags.append("-R")
    flags.extend(["-t", str(duration)])

    if network.lower() == "udp":
        b_rate = bitrate or ("25M" if parallel > 1 else "200M")
        flags.extend(["-u", "-b", b_rate, "-l", "1400"])
    else:
        if parallel > 1:
            flags.extend(["-P", str(parallel), "-l", "64K"])

    flags.append("-J")
    return " ".join(flags)


def parse_iperf_json(raw_json_str: str, network: str = "tcp") -> Dict[str, Any]:
    """
    Safely parses iperf3 JSON output, extracting throughput bps/mbps, loss %, and jitter ms.
    """
    res = {
        "bps": 0.0,
        "mbps": 0.0,
        "loss_percent": 0.0,
        "jitter_ms": 0.0,
        "raw": None
    }
    if not raw_json_str or not raw_json_str.strip():
        return res

    try:
        data = json.loads(raw_json_str)
        res["raw"] = data
        end_data = data.get("end", {})

        if network.lower() == "udp":
            sum_data = end_data.get("sum_received") or end_data.get("sum") or {}
            res["bps"] = float(sum_data.get("bits_per_second", 0.0))
            res["loss_percent"] = float(sum_data.get("lost_percent", 0.0))
            res["jitter_ms"] = float(sum_data.get("jitter_ms", 0.0))
        else:
            sum_received = end_data.get("sum_received") or end_data.get("sum_sent") or {}
            res["bps"] = float(sum_received.get("bits_per_second", 0.0))

        res["mbps"] = round(res["bps"] / 1_000_000.0, 2)
    except Exception as e:
        log_warn(f"Failed to parse iperf3 JSON output ({network}): {e}")

    return res
