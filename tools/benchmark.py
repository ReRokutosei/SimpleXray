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

APP_PKG = "com.simplexray.re.debug"
ACTION_BENCHMARK = "com.simplexray.re.action.BENCHMARK"
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
        log_info(f"Sending Benchmark broadcast: cmd={cmd}, backend={backend}, mtu={mtu}")
        self.shell(
            f"am broadcast --user 0 -a {ACTION_BENCHMARK} -p {APP_PKG} "
            f"--es cmd {cmd} --es backend {backend} --ei mtu {mtu}"
        )

    def get_app_memory_mb(self) -> float:
        rc, out, _ = self.shell(f"dumpsys meminfo {APP_PKG} | grep 'TOTAL PSS:'")
        match = re.search(r"TOTAL PSS:\s+(\d+)", out)
        if match:
            pss_kb = float(match.group(1))
            return round(pss_kb / 1024.0, 1)
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
        medium: str = "Wi-Fi"
    ) -> Dict[str, Any]:
        par_desc = f" [P={parallel}]" if parallel > 1 else " [Single Stream]"
        full_name = f"{name}{par_desc}"
        
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
        par_flags = f"-P {parallel} -l 64K" if parallel > 1 else ""

        # 1. Upload Test (Android -> Host)
        log_info(f">>> [1/2] Testing UPLOAD (Android -> Host, duration: {duration}s)...")
        server_proc = self._start_host_server()
        profiler = CpuProfiler(self.adb, self.app_uid, duration)
        profiler.start()

        up_cmd = f"/data/local/tmp/iperf3 -c {server_ip} -p {DEFAULT_PORT} -t {duration} {par_flags} -J"
        rc, up_out, _ = self.adb.shell(up_cmd, timeout=duration + 15)
        up_avg_cpu, up_peak_cpu = profiler.stop()
        self._kill_host_server(server_proc)

        up_bps = 0.0
        try:
            up_json = json.loads(up_out)
            up_bps = float(up_json.get("end", {}).get("sum_received", {}).get("bits_per_second", 0))
        except Exception:
            log_warn("Failed to parse upload iperf3 JSON output.")
        up_mbps = round(up_bps / 1_000_000.0, 2)
        up_mem_mb = self.adb.get_app_memory_mb()
        log_success(f"Upload: {up_mbps} Mbps | CPU: {up_avg_cpu}% (Peak: {up_peak_cpu}%) | MEM: {up_mem_mb} MB")

        time.sleep(1.5)

        # 2. Download Test (Host -> Android, reverse mode)
        log_info(f">>> [2/2] Testing DOWNLOAD (Host -> Android, duration: {duration}s)...")
        server_proc = self._start_host_server()
        profiler = CpuProfiler(self.adb, self.app_uid, duration)
        profiler.start()

        down_cmd = f"/data/local/tmp/iperf3 -c {server_ip} -p {DEFAULT_PORT} -R -t {duration} {par_flags} -J"
        rc, down_out, _ = self.adb.shell(down_cmd, timeout=duration + 15)
        down_avg_cpu, down_peak_cpu = profiler.stop()
        self._kill_host_server(server_proc)

        down_bps = 0.0
        try:
            down_json = json.loads(down_out)
            down_bps = float(down_json.get("end", {}).get("sum_received", {}).get("bits_per_second", 0))
        except Exception:
            log_warn("Failed to parse download iperf3 JSON output.")
        down_mbps = round(down_bps / 1_000_000.0, 2)
        down_mem_mb = self.adb.get_app_memory_mb()
        log_success(f"Download: {down_mbps} Mbps | CPU: {down_avg_cpu}% (Peak: {down_peak_cpu}%) | MEM: {down_mem_mb} MB")

        # Cleanup VPN
        if backend != "direct_none":
            self.adb.set_app_state(backend, mtu, cmd="stop")
            time.sleep(2)

        peak_cpu = max(up_peak_cpu, down_peak_cpu)
        peak_mem = max(up_mem_mb, down_mem_mb)

        return {
            "name": full_name,
            "backend": backend,
            "mtu": mtu,
            "medium": medium,
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
        results: List[Dict[str, Any]] = []

        # List of models to test: (Display Name, Backend, MTU)
        models = [
            ("Hev (MTU 1500)", "hev", 1500),
            ("Xray TUN (MTU 1500)", "xray", 1500),
            ("SingTUN (MTU 1500)", "sing", 1500),
            ("Hev (MTU 8500)", "hev", 8500),
            ("Xray TUN (MTU 8500)", "xray", 8500),
            ("SingTUN (MTU 8500)", "sing", 8500),
        ]

        # 1. 5GHz Wi-Fi Benchmark Suite
        if "wifi" in modes or "all" in modes:
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"       ROUND {round_num} - SUITE 1: 5GHz Wi-Fi BENCHMARK")
            print(f"======================================================={Colors.RESET}")
            # Baseline
            results.append(self.run_remote_case("Wi-Fi Baseline (No VPN)", "direct_none", 0, wifi_ip, parallel=1, medium="5GHz Wi-Fi"))
            results.append(self.run_remote_case("Wi-Fi Baseline (No VPN)", "direct_none", 0, wifi_ip, parallel=8, medium="5GHz Wi-Fi"))
            # Single Stream
            for name, backend, mtu in models:
                results.append(self.run_remote_case(name, backend, mtu, wifi_ip, parallel=1, medium="5GHz Wi-Fi"))
            # Multi Stream
            for name, backend, mtu in models:
                results.append(self.run_remote_case(name, backend, mtu, wifi_ip, parallel=8, medium="5GHz Wi-Fi"))

        # 2. USB Tethering Suite
        if "usb" in modes or "all" in modes:
            # Check if USB subnet is reachable
            rc, _, _ = run_cmd(["ping", "-c", "1", "-W", "1", usb_ip])
            if rc != 0:
                log_warn(f"USB tethering host IP ({usb_ip}) is unreachable. Skipping USB benchmark suite.")
            else:
                print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
                print(f"       ROUND {round_num} - SUITE 2: USB TETHERING BENCHMARK")
                print(f"======================================================={Colors.RESET}")
                # Baseline
                results.append(self.run_remote_case("USB Baseline (No VPN)", "direct_none", 0, usb_ip, parallel=1, medium="USB 3.2 / 4.0"))
                results.append(self.run_remote_case("USB Baseline (No VPN)", "direct_none", 0, usb_ip, parallel=8, medium="USB 3.2 / 4.0"))
                # Single Stream
                for name, backend, mtu in models:
                    results.append(self.run_remote_case(name, backend, mtu, usb_ip, parallel=1, medium="USB 3.2 / 4.0"))
                # Multi Stream
                for name, backend, mtu in models:
                    results.append(self.run_remote_case(name, backend, mtu, usb_ip, parallel=8, medium="USB 3.2 / 4.0"))

        # 3. On-Device Loopback Suite
        if "loopback" in modes or "all" in modes:
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"       ROUND {round_num} - SUITE 3: ON-DEVICE LOOPBACK BENCHMARK")
            print(f"======================================================={Colors.RESET}")
            # Baseline
            results.append(self.run_loopback_case("Loopback Baseline (No VPN)", "direct_none", 0, parallel=1))
            results.append(self.run_loopback_case("Loopback Baseline (No VPN)", "direct_none", 0, parallel=8))
            # Single Stream
            for name, backend, mtu in models:
                results.append(self.run_loopback_case(name, backend, mtu, parallel=1))
            # Multi Stream
            for name, backend, mtu in models:
                results.append(self.run_loopback_case(name, backend, mtu, parallel=8))

        return results


def format_markdown_table(results: List[Dict[str, Any]], title: str = "Benchmark Results") -> str:
    lines = [
        f"### {title}\n",
        "| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |",
        "| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |"
    ]
    for r in results:
        if r["medium"] == "On-Device Loopback":
            up_str = f"{r.get('speed_gbps', 0)} Gbps"
            down_str = f"{r.get('speed_gbps', 0)} Gbps"
        else:
            up_str = f"{r['upload_mbps']} Mbps"
            down_str = f"{r['download_mbps']} Mbps"

        lines.append(
            f"| {r['name']} | {r['backend']} | {r['mtu']} | {r['medium']} | "
            f"{up_str} | {down_str} | "
            f"{r['upload_cpu_avg']}% | {r['download_cpu_avg']}% | "
            f"{r['peak_cpu']}% | {r['peak_mem_mb']} MB |"
        )
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description="SimpleXray Android TUN Automated Benchmark")
    parser.add_argument("--mode", default="wifi,loopback",
                        help="Benchmark suite(s) to run (comma-separated: wifi,loopback,usb, or 'all', default: wifi,loopback)")
    parser.add_argument("--wifi-server-ip", default="10.189.231.200",
                        help="Host IP address in Wi-Fi subnet (default: 10.189.231.200)")
    parser.add_argument("--usb-server-ip", default="192.168.232.59",
                        help="Host IP address in USB tethering subnet (default: 192.168.232.59)")
    parser.add_argument("--duration", type=int, default=DEFAULT_DURATION,
                        help="Duration in seconds per test direction (default: 10)")
    parser.add_argument("--device", default=None,
                        help="ADB device serial if multiple connected")
    parser.add_argument("--rounds", type=int, default=3,
                        help="Number of test rounds to execute (default: 3)")
    parser.add_argument("--output-json", default="docs/benchmark/benchmark_results.json",
                        help="File path to save JSON results")
    parser.add_argument("--output-md", default="docs/benchmark/benchmark_summary.md",
                        help="File path to save Markdown tables")
    args = parser.parse_args()

    try:
        runner = BenchmarkRunner(args)
    except Exception as e:
        log_error(str(e))
        sys.exit(1)

    all_rounds_data: Dict[str, Any] = {
        "metadata": {
            "timestamp": datetime.now().isoformat(),
            "mode": args.mode,
            "duration": args.duration,
            "wifi_server_ip": args.wifi_server_ip,
            "usb_server_ip": args.usb_server_ip,
            "device": runner.adb.device,
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
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(all_rounds_data, f, indent=2, ensure_ascii=False)
    log_success(f"JSON results saved to: {json_path}")

    md_path = os.path.abspath(args.output_md)
    os.makedirs(os.path.dirname(md_path), exist_ok=True)
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("\n".join(markdown_reports))
    log_success(f"Markdown report saved to: {md_path}")

    log_success("All benchmark rounds completed successfully!")

if __name__ == "__main__":
    main()
