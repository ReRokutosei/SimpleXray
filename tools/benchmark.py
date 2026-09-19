#!/usr/bin/env python3
"""
SimpleXray Automated TUN Benchmark Runner (Python 3)
Replaces benchmark.ps1 with a cross-platform (Linux / macOS / Windows) test suite.
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

APP_PKG = "com.simplexray.re.debug"
DEFAULT_DURATION = 10
DEFAULT_PORT = 5201
LOOPBACK_PORT = 5202

# ANSI Terminal Colors
class Colors:
    CYAN = "\033[96m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    RED = "\033[91m"
    MAGENTA = "\033[95m"
    BOLD = "\033[1m"
    RESET = "\033[0m"

def log_info(msg: str):
    print(f"{Colors.CYAN}[INFO] {msg}{Colors.RESET}")

def log_success(msg: str):
    print(f"{Colors.GREEN}[SUCCESS] {msg}{Colors.RESET}")

def log_warn(msg: str):
    print(f"{Colors.YELLOW}[WARN] {msg}{Colors.RESET}")

def log_error(msg: str):
    print(f"{Colors.RED}[ERROR] {msg}{Colors.RESET}")

def run_cmd(cmd: List[str], timeout: Optional[float] = None) -> Tuple[int, str, str]:
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return proc.returncode, proc.stdout, proc.stderr
    except subprocess.TimeoutExpired:
        log_warn(f"Command timed out ({timeout}s): {' '.join(cmd)}")
        return -1, "", "Timeout"
    except Exception as e:
        log_error(f"Command failed: {' '.join(cmd)}: {e}")
        return -1, "", str(e)

class AdbRunner:
    def __init__(self, device: Optional[str] = None):
        self.device = device
        self._ensure_device()

    def _ensure_device(self):
        adb_path = shutil.which("adb")
        if not adb_path:
            raise RuntimeError("ADB binary not found in PATH! Please install Android Platform Tools.")
        
        rc, out, _ = run_cmd(["adb", "devices"])
        lines = [line.strip() for line in out.strip().splitlines() if line.strip() and not line.startswith("List of devices")]
        devices = [l.split()[0] for l in lines if "\tdevice" in l]
        
        if not devices:
            raise RuntimeError("No authorized ADB device found connected.")
        if self.device:
            if self.device not in devices:
                raise RuntimeError(f"Specified ADB device '{self.device}' not found. Connected: {devices}")
        else:
            self.device = devices[0]
            log_info(f"Auto-selected ADB device: {self.device}")

    def exec(self, args: List[str], timeout: Optional[float] = None) -> Tuple[int, str, str]:
        cmd = ["adb", "-s", self.device] + args
        return run_cmd(cmd, timeout=timeout)

    def shell(self, cmd_str: str, timeout: Optional[float] = None) -> Tuple[int, str, str]:
        return self.exec(["shell", cmd_str], timeout=timeout)

    def get_app_uid(self) -> str:
        _, out, _ = self.shell(f"pm list packages --user 0 -U | grep {APP_PKG}")
        match = re.search(r"uid:(\d+)", out)
        if match:
            return match.group(1)
        return ""

    def ensure_iperf3_on_device(self):
        rc, out, _ = self.shell("ls -la /data/local/tmp/iperf3")
        if rc != 0 or "/data/local/tmp/iperf3" not in out:
            raise RuntimeError("Device does not have /data/local/tmp/iperf3. Please push an arm64 iperf3 binary.")
        self.shell("chmod 755 /data/local/tmp/iperf3")

    def set_app_state(self, backend: str, mtu: int, cmd: str = "start"):
        log_info(f"Controlling Headless Benchmark Service: cmd={cmd}, backend={backend}, mtu={mtu}")
        self.shell(
            f"am start-foreground-service -n {APP_PKG}/com.simplexray.re.service.BenchmarkService "
            f"--es cmd {cmd} --es backend {backend} --ei mtu {mtu}",
            timeout=10.0
        )

    def get_app_memory_mb(self) -> float:
        for _ in range(3):
            rc, out, _ = self.shell(f"dumpsys meminfo {APP_PKG} | grep 'TOTAL PSS:'", timeout=5.0)
            match = re.search(r"TOTAL PSS:\s+(\d+)", out)
            if match:
                pss_kb = float(match.group(1))
                return round(pss_kb / 1024.0, 1)
            time.sleep(0.3)
        return 0.0


class CpuProfiler:
    def __init__(self, adb: AdbRunner, app_uid: str, sample_duration: int):
        self.adb = adb
        self.app_uid = app_uid
        self.sample_duration = max(2, sample_duration)
        self.cpu_samples: List[float] = []
        self._stop_event = threading.Event()
        self._thread: Optional[threading.Thread] = None

    def _worker(self):
        cmd = [
            "adb", "-s", self.adb.device, "shell",
            f"top -b -d 1 -n {self.sample_duration} -u {self.app_uid}"
        ]
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        try:
            while not self._stop_event.is_set():
                line = proc.stdout.readline()
                if not line:
                    break
                trimmed = line.strip()
                if any(k in trimmed for k in [APP_PKG, "libxray", "simplexray", "sing-box"]):
                    parts = trimmed.split()
                    if len(parts) >= 9:
                        try:
                            # In Android top -b: PID USER PR NI VIRT RES SHR S [%CPU] %MEM TIME+ ARGS
                            cpu_val = float(parts[8])
                            self.cpu_samples.append(cpu_val)
                        except ValueError:
                            pass
            proc.wait(timeout=2)
        except Exception:
            pass
        finally:
            if proc.poll() is None:
                proc.kill()

    def start(self):
        self._stop_event.clear()
        self._thread = threading.Thread(target=self._worker, daemon=True)
        self._thread.start()

    def stop(self) -> Tuple[float, float]:
        self._stop_event.set()
        if self._thread:
            self._thread.join(timeout=3)
        if self.cpu_samples:
            avg_cpu = round(sum(self.cpu_samples) / len(self.cpu_samples), 1)
            peak_cpu = round(max(self.cpu_samples), 1)
        else:
            avg_cpu = 0.0
            peak_cpu = 0.0
        return avg_cpu, peak_cpu


class BenchmarkRunner:
    def __init__(self, args: argparse.Namespace):
        self.args = args
        self.adb = AdbRunner(args.device)
        self.host_iperf3 = shutil.which("iperf3")
        if not self.host_iperf3:
            raise RuntimeError("iperf3 binary not found on host machine. Please install it.")
        
        self.adb.ensure_iperf3_on_device()
        self.app_uid = self.adb.get_app_uid()
        log_info(f"Detected App Package: {APP_PKG}, UID: {self.app_uid}")

        # Ensure deviceidle whitelist to allow background service launch
        self.adb.shell(f"cmd deviceidle whitelist +{APP_PKG}")

    def _start_host_server(self, port: int = DEFAULT_PORT) -> subprocess.Popen:
        # iperf3 -s -1: run once and exit upon client disconnect
        cmd = [self.host_iperf3, "-s", "-1", "-p", str(port)]
        proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(1.0)
        return proc

    def _kill_host_server(self, proc: Optional[subprocess.Popen]):
        if proc and proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=2)
            except subprocess.TimeoutExpired:
                proc.kill()

    def run_remote_case(
        self,
        name: str,
        backend: str,
        mtu: int,
        server_ip: str,
        parallel: int = 1,
        medium: str = "5GHz Wi-Fi",
        network: str = "tcp"
    ) -> Dict[str, Any]:
        par_desc = f" [P={parallel}]" if parallel > 1 else " [Single Stream]"
        net_desc = f" [{network.upper()}]" if network != "tcp" else ""
        full_name = f"{name}{net_desc}{par_desc}"
        
        print(f"\n{Colors.MAGENTA}======================================================={Colors.RESET}")
        log_info(f"Running: {full_name} ({medium}, Backend: {backend}, MTU: {mtu}, Target: {server_ip})")
        print(f"{Colors.MAGENTA}======================================================={Colors.RESET}")

        # Stop previous VPN instance
        self.adb.set_app_state(backend, mtu, cmd="stop")
        time.sleep(2)

        # Start VPN if not physical baseline
        if backend != "direct_none":
            self.adb.set_app_state(backend, mtu, cmd="start")
            log_info("Waiting 4s for VPN & proxy backend to initialize...")
            time.sleep(4)

        duration = self.args.duration
        par_flags = f"-P {parallel} -l 64K" if (parallel > 1 and network == "tcp") else (f"-P {parallel}" if parallel > 1 else "")
        if network == "udp":
            bitrate = getattr(self.args, "udp_parallel_bitrate", "25M") if parallel > 1 else getattr(self.args, "udp_bitrate", "200M")
            udp_flags = f"-u -b {bitrate} -l 1400"
        else:
            udp_flags = ""

        # 1. Upload Test (Android -> Host)
        log_info(f">>> [1/2] Testing {network.upper()} UPLOAD (Android -> Host, duration: {duration}s)...")
        server_proc = self._start_host_server()
        profiler = CpuProfiler(self.adb, self.app_uid, duration)
        profiler.start()

        up_cmd = f"/data/local/tmp/iperf3 -c {server_ip} -p {DEFAULT_PORT} {udp_flags} -t {duration} {par_flags} -J"
        rc, up_out, _ = self.adb.shell(up_cmd, timeout=duration + 15)
        up_avg_cpu, up_peak_cpu = profiler.stop()
        self._kill_host_server(server_proc)

        up_bps = 0.0
        up_loss = 0.0
        up_jitter = 0.0
        try:
            up_json = json.loads(up_out)
            end_data = up_json.get("end", {})
            if network == "udp":
                sum_data = end_data.get("sum_received") or end_data.get("sum") or {}
                up_bps = float(sum_data.get("bits_per_second", 0))
                up_loss = float(sum_data.get("lost_percent", 0.0))
                up_jitter = float(sum_data.get("jitter_ms", 0.0))
            else:
                up_bps = float(end_data.get("sum_received", {}).get("bits_per_second", 0))
        except Exception:
            log_warn(f"Failed to parse upload iperf3 JSON output ({network}).")
        up_mbps = round(up_bps / 1_000_000.0, 2)
        up_mem_mb = self.adb.get_app_memory_mb()
        loss_str = f" | Loss: {up_loss:.1f}% | Jitter: {up_jitter:.2f}ms" if network == "udp" else ""
        log_success(f"Upload: {up_mbps} Mbps{loss_str} | CPU: {up_avg_cpu}% (Peak: {up_peak_cpu}%) | MEM: {up_mem_mb} MB")

        time.sleep(1.5)

        # 2. Download Test (Host -> Android, reverse mode)
        log_info(f">>> [2/2] Testing {network.upper()} DOWNLOAD (Host -> Android, duration: {duration}s)...")
        server_proc = self._start_host_server()
        profiler = CpuProfiler(self.adb, self.app_uid, duration)
        profiler.start()

        down_cmd = f"/data/local/tmp/iperf3 -c {server_ip} -p {DEFAULT_PORT} {udp_flags} -R -t {duration} {par_flags} -J"
        rc, down_out, _ = self.adb.shell(down_cmd, timeout=duration + 15)
        down_avg_cpu, down_peak_cpu = profiler.stop()
        self._kill_host_server(server_proc)

        down_bps = 0.0
        down_loss = 0.0
        down_jitter = 0.0
        try:
            down_json = json.loads(down_out)
            end_data = down_json.get("end", {})
            if network == "udp":
                sum_data = end_data.get("sum_received") or end_data.get("sum") or {}
                down_bps = float(sum_data.get("bits_per_second", 0))
                down_loss = float(sum_data.get("lost_percent", 0.0))
                down_jitter = float(sum_data.get("jitter_ms", 0.0))
            else:
                down_bps = float(end_data.get("sum_received", {}).get("bits_per_second", 0))
        except Exception:
            log_warn(f"Failed to parse download iperf3 JSON output ({network}).")
        down_mbps = round(down_bps / 1_000_000.0, 2)
        down_mem_mb = self.adb.get_app_memory_mb()
        loss_str = f" | Loss: {down_loss:.1f}% | Jitter: {down_jitter:.2f}ms" if network == "udp" else ""
        log_success(f"Download: {down_mbps} Mbps{loss_str} | CPU: {down_avg_cpu}% (Peak: {down_peak_cpu}%) | MEM: {down_mem_mb} MB")

        # Cleanup VPN
        if backend != "direct_none":
            self.adb.set_app_state(backend, mtu, cmd="stop")
            time.sleep(2)

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

    def run_idle_flow_case(
        self,
        backend: str,
        network: str,
        server_ip: str,
        port: int = 5301
    ) -> Dict[str, Any]:
        """
        Tests retained connections (0 -> 1000) vs memory growth.
        """
        full_name = f"{backend.upper()} {network.upper()} Idle Flows (0-1000)"
        print(f"\n{Colors.MAGENTA}======================================================={Colors.RESET}")
        log_info(f"Running Idle Flow Benchmark: {full_name} on {backend} (Target: {server_ip}:{port})")
        print(f"{Colors.MAGENTA}======================================================={Colors.RESET}")

        # Ensure server probe is running on host
        server_bin = os.path.join(SCRIPT_DIR, "idle_bench", "idle_bench_linux_amd64")
        if not os.path.exists(server_bin):
            raise RuntimeError(f"Server binary not found: {server_bin}")

        server_proc = subprocess.Popen([server_bin, "server", "--port", str(port)],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(0.5)

        # Stop previous VPN and start new
        self.adb.set_app_state(backend, 1500, cmd="stop")
        time.sleep(3)
        if backend != "direct_none":
            self.adb.set_app_state(backend, 1500, cmd="start")
            log_info("Waiting 4s for VPN to initialize...")
            time.sleep(4)

        # Ensure client binary exists on phone
        client_bin_host = os.path.join(SCRIPT_DIR, "idle_bench", "idle_bench_linux_arm64")
        self.adb.exec(["push", client_bin_host, "/data/local/tmp/idle_bench"])
        self.adb.shell("chmod 755 /data/local/tmp/idle_bench")

        client_cmd = (f"/data/local/tmp/idle_bench client --server {server_ip}:{port} "
                      f"--network {network} --steps 0,250,500,750,1000 --settle 1s --auto")

        log_info(f"Executing idle probe on device: {client_cmd}")
        p = subprocess.Popen(["adb", "-s", self.adb.device, "shell", client_cmd],
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

        flow_records = []
        base_mem = 0.0

        for line in iter(p.stdout.readline, ''):
            line = line.strip()
            if not line:
                continue
            try:
                data = json.loads(line)
                if data.get("status") == "settled":
                    step = data.get("step", 0)
                    mem_mb = self.adb.get_app_memory_mb()
                    if step == 0:
                        base_mem = mem_mb
                    delta = round(mem_mb - base_mem, 2)
                    flow_records.append({"connections": step, "pss_mb": mem_mb, "delta_mb": delta})
                    log_success(f"[Idle {network.upper()}] Conns: {step:4d} | PSS: {mem_mb:6.1f} MB (Delta: +{delta:5.2f} MB)")
            except Exception:
                pass

        p.wait()
        server_proc.terminate()
        server_proc.wait()

        # Stop VPN
        if backend != "direct_none":
            self.adb.set_app_state(backend, 1500, cmd="stop")
            time.sleep(3)

        # Calculate slope
        slope_kib = 0.0
        if len(flow_records) >= 2:
            first = flow_records[0]
            last = flow_records[-1]
            if last["connections"] > first["connections"]:
                slope_kib = round((last["pss_mb"] - first["pss_mb"]) * 1024.0 / (last["connections"] - first["connections"]), 3)

        log_info(f"Idle flows complete. Slope: {slope_kib:.2f} KiB / connection")

        return {
            "name": full_name,
            "backend": backend,
            "network": network,
            "type": "idle_memory",
            "idle_flows": flow_records,
            "slope_kib_per_conn": slope_kib
        }

    def run_loopback_case(
        self,
        name: str,
        backend: str,
        mtu: int,
        parallel: int = 1
    ) -> Dict[str, Any]:
        par_desc = f" [P={parallel}]" if parallel > 1 else " [Single Stream]"
        full_name = f"{name}{par_desc}"

        print(f"\n{Colors.MAGENTA}======================================================={Colors.RESET}")
        log_info(f"Running: {full_name} (On-Device Loopback, Backend: {backend}, MTU: {mtu})")
        print(f"{Colors.MAGENTA}======================================================={Colors.RESET}")

        self.adb.set_app_state(backend, mtu, cmd="stop")
        time.sleep(2)

        if backend != "direct_none":
            self.adb.set_app_state(backend, mtu, cmd="start")
            log_info("Waiting 4s for VPN & proxy backend to initialize...")
            time.sleep(4)

        # Launch background iperf3 server on device port LOOPBACK_PORT
        self.adb.shell(f"pkill iperf3; (nohup /data/local/tmp/iperf3 -s -p {LOOPBACK_PORT} > /dev/null 2>&1 &)")
        time.sleep(1.0)

        duration = self.args.duration
        par_flags = f"-P {parallel} -l 64K" if parallel > 1 else ""

        log_info(f">>> Testing ON-DEVICE LOOPBACK (duration: {duration}s)...")
        profiler = CpuProfiler(self.adb, self.app_uid, duration)
        profiler.start()

        loop_cmd = f"/data/local/tmp/iperf3 -c 127.0.0.1 -p {LOOPBACK_PORT} -t {duration} {par_flags} -J"
        rc, loop_out, _ = self.adb.shell(loop_cmd, timeout=duration + 15)
        loop_avg_cpu, loop_peak_cpu = profiler.stop()

        self.adb.shell("pkill iperf3")

        loop_bps = 0.0
        try:
            loop_json = json.loads(loop_out)
            loop_bps = float(loop_json.get("end", {}).get("sum_received", {}).get("bits_per_second", 0))
        except Exception:
            log_warn("Failed to parse loopback iperf3 JSON output.")

        loop_gbps = round(loop_bps / 1_000_000_000.0, 2)
        loop_mbps = round(loop_bps / 1_000_000.0, 2)
        loop_mem_mb = self.adb.get_app_memory_mb()
        log_success(f"Loopback: {loop_gbps} Gbps ({loop_mbps} Mbps) | CPU: {loop_avg_cpu}% (Peak: {loop_peak_cpu}%) | MEM: {loop_mem_mb} MB")

        if backend != "direct_none":
            self.adb.set_app_state(backend, mtu, cmd="stop")
            time.sleep(2)

        return {
            "name": full_name,
            "backend": backend,
            "mtu": mtu,
            "medium": "On-Device Loopback",
            "parallel": parallel,
            "upload_mbps": loop_mbps,
            "download_mbps": loop_mbps,
            "speed_gbps": loop_gbps,
            "upload_cpu_avg": loop_avg_cpu,
            "upload_cpu_peak": loop_peak_cpu,
            "download_cpu_avg": loop_avg_cpu,
            "download_cpu_peak": loop_peak_cpu,
            "peak_cpu": loop_peak_cpu,
            "peak_mem_mb": loop_mem_mb
        }

    def run_suite(self, round_num: int) -> List[Dict[str, Any]]:
        modes = [m.strip() for m in self.args.mode.split(",")]
        wifi_ip = self.args.wifi_server_ip
        usb_ip = self.args.usb_server_ip
        networks = ["tcp", "udp"] if self.args.network == "all" else [self.args.network]
        results: List[Dict[str, Any]] = []

        # Standardized models (MTU 1500 and Standard Jumbo 9000)
        all_models = [
            ("Hev (MTU 1500)", "hev", 1500),
            ("Xray TUN (MTU 1500)", "xray", 1500),
            ("SingTUN (MTU 1500)", "sing", 1500),
            ("MipsTUN (MTU 1500)", "mips", 1500),
            ("Hev (MTU 9000)", "hev", 9000),
            ("Xray TUN (MTU 9000)", "xray", 9000),
            ("SingTUN (MTU 9000)", "sing", 9000),
            ("MipsTUN (MTU 9000)", "mips", 9000),
        ]

        if hasattr(self.args, "backends") and self.args.backends and self.args.backends != "all":
            selected = [b.strip().lower() for b in self.args.backends.split(",")]
            models = [m for m in all_models if m[1] in selected]
        else:
            models = all_models

        skip_baseline = getattr(self.args, "skip_baseline", False)

        # 1. 5GHz Wi-Fi Benchmark Suite
        if "wifi" in modes or "all" in modes:
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"       ROUND {round_num} - SUITE 1: 5GHz Wi-Fi BENCHMARK")
            print(f"======================================================={Colors.RESET}")
            for net in networks:
                # Baseline
                if not skip_baseline:
                    results.append(self.run_remote_case("Wi-Fi Baseline (No VPN)", "direct_none", 0, wifi_ip, parallel=1, medium="5GHz Wi-Fi", network=net))
                    results.append(self.run_remote_case("Wi-Fi Baseline (No VPN)", "direct_none", 0, wifi_ip, parallel=8, medium="5GHz Wi-Fi", network=net))
                # If UDP, only test MTU 1500 (standard for UDP)
                cur_models = [m for m in models if (net == "tcp" or m[2] == 1500)]
                # Single Stream
                for name, backend, mtu in cur_models:
                    results.append(self.run_remote_case(name, backend, mtu, wifi_ip, parallel=1, medium="5GHz Wi-Fi", network=net))
                # Multi Stream
                for name, backend, mtu in cur_models:
                    results.append(self.run_remote_case(name, backend, mtu, wifi_ip, parallel=8, medium="5GHz Wi-Fi", network=net))

        # 2. USB Tethering Suite
        if "usb" in modes or "all" in modes:
            rc, _, _ = run_cmd(["ping", "-c", "1", "-W", "1", usb_ip])
            if rc != 0:
                log_warn(f"USB tethering host IP ({usb_ip}) is unreachable. Skipping USB benchmark suite.")
            else:
                print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
                print(f"       ROUND {round_num} - SUITE 2: USB TETHERING BENCHMARK")
                print(f"======================================================={Colors.RESET}")
                for net in networks:
                    if not skip_baseline:
                        results.append(self.run_remote_case("USB Baseline (No VPN)", "direct_none", 0, usb_ip, parallel=1, medium="USB 3.2 / 4.0", network=net))
                        results.append(self.run_remote_case("USB Baseline (No VPN)", "direct_none", 0, usb_ip, parallel=8, medium="USB 3.2 / 4.0", network=net))
                    cur_models = [m for m in models if (net == "tcp" or m[2] == 1500)]
                    for name, backend, mtu in cur_models:
                        results.append(self.run_remote_case(name, backend, mtu, usb_ip, parallel=1, medium="USB 3.2 / 4.0", network=net))
                    for name, backend, mtu in cur_models:
                        results.append(self.run_remote_case(name, backend, mtu, usb_ip, parallel=8, medium="USB 3.2 / 4.0", network=net))

        # 3. On-Device Loopback Suite
        if "loopback" in modes or "all" in modes:
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"       ROUND {round_num} - SUITE 3: ON-DEVICE LOOPBACK BENCHMARK")
            print(f"======================================================={Colors.RESET}")
            if not skip_baseline:
                results.append(self.run_loopback_case("Loopback Baseline (No VPN)", "direct_none", 0, parallel=1))
                results.append(self.run_loopback_case("Loopback Baseline (No VPN)", "direct_none", 0, parallel=8))
            loop_models = [m for m in models if m[2] == 9000] if any(m[2] == 9000 for m in models) else models
            for name, backend, mtu in loop_models:
                results.append(self.run_loopback_case(name, backend, mtu, parallel=1))
            for name, backend, mtu in loop_models:
                results.append(self.run_loopback_case(name, backend, mtu, parallel=8))

        # 4. Idle Connections Suite
        if "idle" in modes or getattr(self.args, "with_idle", False):
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"       ROUND {round_num} - SUITE 4: IDLE FLOWS vs MEMORY GROWTH")
            print(f"======================================================={Colors.RESET}")
            active_backends = list(dict.fromkeys([m[1] for m in models if m[1] != "direct_none"]))
            idle_networks = ["tcp", "udp"] if self.args.network == "all" else [self.args.network]
            for b in active_backends:
                for net in idle_networks:
                    results.append(self.run_idle_flow_case(b, net, wifi_ip))

        return results


def format_markdown_table(results: List[Dict[str, Any]], title: str = "Benchmark Results") -> str:
    standard_results = [r for r in results if r.get("type") != "idle_memory"]
    idle_results = [r for r in results if r.get("type") == "idle_memory"]

    lines = [
        f"### {title}\n",
        "| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |",
        "| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |"
    ]
    for r in standard_results:
        if r["medium"] == "On-Device Loopback":
            up_str = f"{r.get('speed_gbps', 0)} Gbps"
            down_str = f"{r.get('speed_gbps', 0)} Gbps"
        else:
            up_loss = f" ({r.get('upload_loss_percent', 0.0):.1f}% loss)" if r.get("network") == "udp" and r.get("upload_loss_percent", 0.0) >= 0.1 else ""
            down_loss = f" ({r.get('download_loss_percent', 0.0):.1f}% loss)" if r.get("network") == "udp" and r.get("download_loss_percent", 0.0) >= 0.1 else ""
            up_str = f"{r['upload_mbps']} Mbps{up_loss}"
            down_str = f"{r['download_mbps']} Mbps{down_loss}"

        lines.append(
            f"| {r['name']} | {r['backend']} | {r['mtu']} | {r['medium']} | "
            f"{up_str} | {down_str} | "
            f"{r['upload_cpu_avg']}% | {r['download_cpu_avg']}% | "
            f"{r['peak_cpu']}% | {r['peak_mem_mb']} MB |"
        )

    if idle_results:
        lines.append("\n#### Retained Connections vs Memory Growth (Idle Flows)\n")
        lines.append("| Backend | Network | Conns Range | Baseline PSS | 1000 Conns PSS | Memory Slope |")
        lines.append("| :--- | :---: | :---: | :---: | :---: | :---: |")
        for ir in idle_results:
            flows = ir.get("idle_flows", [])
            base = flows[0]["pss_mb"] if flows else 0.0
            last = flows[-1]["pss_mb"] if flows else 0.0
            slope = ir.get("slope_kib_per_conn", 0.0)
            lines.append(f"| {ir['backend'].upper()} | {ir['network'].upper()} | 0 -> 1000 | {base:.1f} MB | {last:.1f} MB | {slope:.2f} KiB/conn |")

    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description="SimpleXray Android TUN Automated Benchmark")
    parser.add_argument("--network", default="all",
                        help="Network protocols to benchmark ('tcp', 'udp', or 'all', default: all)")
    parser.add_argument("--with-idle", action="store_true",
                        help="Include idle flows vs memory growth (0-1000 connections) benchmark suite")
    parser.add_argument("--backends", default="all",
                        help="TUN backends to benchmark (comma-separated: hev,xray,sing,mips, or 'all', default: all)")
    parser.add_argument("--skip-baseline", action="store_true",
                        help="Skip running physical baseline (No VPN) tests")
    parser.add_argument("--mode", default="wifi,loopback",
                        help="Benchmark suite(s) to run (comma-separated: wifi,loopback,usb, or 'all', default: wifi,loopback)")
    parser.add_argument("--wifi-server-ip", default="10.189.231.200",
                        help="Host IP address in Wi-Fi subnet (default: 10.189.231.200)")
    parser.add_argument("--usb-server-ip", default="auto",
                        help="Host IP address in USB tethering subnet (default: auto)")
    parser.add_argument("--duration", type=int, default=DEFAULT_DURATION,
                        help="Duration in seconds per test direction (default: 10)")
    parser.add_argument("--device", default=None,
                        help="ADB device serial if multiple connected")
    parser.add_argument("--device-name", default=None,
                        help="Sanitized device name to record in output metadata (default: auto-masked serial)")
    parser.add_argument("--rounds", type=int, default=3,
                        help="Number of test rounds to execute (default: 3)")
    parser.add_argument("--merge", action="store_true",
                        help="Merge new benchmark results into existing JSON/Markdown if files already exist")
    parser.add_argument("--output-json", default="docs/benchmark/benchmark_results.json",
                        help="File path to save JSON results")
    parser.add_argument("--output-md", default="docs/benchmark/benchmark_summary.md",
                        help="File path to save Markdown tables")
    parser.add_argument("--no-charts", action="store_true",
                        help="Skip generating visualization charts in docs/images/")
    parser.add_argument("--charts-dir", default=None,
                        help="Output directory for generated charts (default: docs/images)")
    args = parser.parse_args()

    # Auto-detect USB host IP if set to auto
    if args.usb_server_ip == "auto":
        rc, out, _ = run_cmd(["ip", "-brief", "address"])
        detected_ip = None
        for line in out.splitlines():
            parts = line.split()
            if len(parts) >= 3 and any(parts[0].startswith(prefix) for prefix in ["enx", "rndis", "usb"]):
                detected_ip = parts[2].split("/")[0]
                break
        if detected_ip:
            log_info(f"Auto-detected USB tethering host IP: {detected_ip}")
            args.usb_server_ip = detected_ip
        else:
            args.usb_server_ip = "192.168.232.59"

    try:
        runner = BenchmarkRunner(args)
    except Exception as e:
        log_error(str(e))
        sys.exit(1)

    def sanitize_device(raw_device: Optional[str], custom_name: Optional[str] = None) -> str:
        if custom_name:
            return custom_name
        if not raw_device:
            return "Android DUT (Snapdragon 778G)"
        if len(raw_device) >= 8:
            return f"{raw_device[:4]}****{raw_device[-3:]}"
        return "****"

    all_rounds_data: Dict[str, Any] = {
        "metadata": {
            "timestamp": datetime.now().isoformat(),
            "mode": args.mode,
            "duration": args.duration,
            "wifi_server_ip": args.wifi_server_ip,
            "usb_server_ip": args.usb_server_ip,
            "device": sanitize_device(runner.adb.device, args.device_name),
            "rounds": args.rounds
        },
        "rounds": {}
    }

    markdown_reports: List[str] = [f"# SimpleXray Benchmark Summary\nGenerated at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n"]

    try:
        for r in range(1, args.rounds + 1):
            round_key = f"round_{r}"
            log_info(f"\n====================== STARTING ROUND {r}/{args.rounds} ======================")
            round_results = runner.run_suite(round_num=r)
            all_rounds_data["rounds"][round_key] = round_results
            md_table = format_markdown_table(round_results, title=f"Round {r} Results")
            markdown_reports.append(md_table)
            print("\n" + md_table)
            
            # Short cooldown between rounds
            if r < args.rounds:
                log_info("Cooling down for 10 seconds before next round...")
                time.sleep(10)

    except KeyboardInterrupt:
        log_warn("Benchmark interrupted by user! Stopping active VPN and cleaning up...")
        runner.adb.set_app_state("direct_none", 0, cmd="stop")
        runner.adb.shell("pkill iperf3")
        sys.exit(130)

    # Save outputs
    json_path = os.path.abspath(args.output_json)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    if args.merge and os.path.exists(json_path):
        log_info(f"Merging new results into existing JSON: {json_path}")
        try:
            with open(json_path, "r", encoding="utf-8") as f:
                existing_data = json.load(f)
            for r_key, r_items in all_rounds_data["rounds"].items():
                if r_key not in existing_data.get("rounds", {}):
                    existing_data.setdefault("rounds", {})[r_key] = r_items
                else:
                    existing_list = existing_data["rounds"][r_key]
                    for new_item in r_items:
                        matched = False
                        for idx, old_item in enumerate(existing_list):
                            if (old_item.get("name") == new_item.get("name") and
                                old_item.get("medium") == new_item.get("medium")):
                                existing_list[idx] = new_item
                                matched = True
                                break
                        if not matched:
                            existing_list.append(new_item)
            all_rounds_data = existing_data
        except Exception as e:
            log_warn(f"Failed to merge with existing JSON: {e}")

    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(all_rounds_data, f, indent=2, ensure_ascii=False)
    log_success(f"JSON results saved to: {json_path}")

    md_path = os.path.abspath(args.output_md)
    os.makedirs(os.path.dirname(md_path), exist_ok=True)
    if args.merge and os.path.exists(json_path):
        full_md_reports = [f"# SimpleXray Benchmark Summary\nGenerated at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n"]
        for r_name in ["round_1", "round_2", "round_3"]:
            if r_name in all_rounds_data.get("rounds", {}):
                r_num = r_name.split("_")[-1]
                full_md_reports.append(format_markdown_table(all_rounds_data["rounds"][r_name], title=f"Round {r_num} Results"))
        with open(md_path, "w", encoding="utf-8") as f:
            f.write("\n".join(full_md_reports))
    else:
        with open(md_path, "w", encoding="utf-8") as f:
            f.write("\n".join(markdown_reports))
    log_success(f"Markdown report saved to: {md_path}")

    # Automatically generate modern dashboards
    chart_gen = os.path.join(SCRIPT_DIR, "generate_charts.py")
    default_json = os.path.abspath("docs/benchmark/benchmark_results.json")
    if os.path.exists(chart_gen) and not args.no_charts:
        if json_path != default_json and not args.charts_dir:
            log_info("Skipping chart generation for non-default JSON output (specify --charts-dir to force).")
        else:
            log_info("Automatically generating modern visualization dashboards...")
            chart_cmd = [sys.executable, chart_gen, "--json", json_path]
            if args.charts_dir:
                chart_cmd.extend(["--output-dir", args.charts_dir])
            rc, out, err = run_cmd(chart_cmd)
            if rc == 0:
                log_success("Visualization dashboards refreshed successfully.")
            else:
                log_warn(f"Failed to generate charts: {err}\n{out}")

    log_success("All benchmark rounds completed successfully!")

if __name__ == "__main__":
    main()
