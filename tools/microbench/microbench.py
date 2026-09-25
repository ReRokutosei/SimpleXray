#!/usr/bin/env python3
"""
SimpleXray Standalone TUN Micro-Benchmark Suite (Scheme 2)
Evaluates pure user-space TUN network stacks (Hev lwIP, SingTUN, Xray gVisor)
in an isolated Linux user namespace without Android ART/Framework overhead.
"""

import argparse
import json
import os
import re
import signal
import subprocess
import sys
import time
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../.."))

# Binary paths
HEV_BIN = os.path.join(PROJECT_ROOT, "third_party/hev-socks5-tunnel/bin/hev-socks5-tunnel")
SING_BIN = os.path.join(PROJECT_ROOT, "third_party/sing-tun/bin/sing-tun")
ZEPTUN_BIN = os.environ.get("ZEPTUN_BIN", os.path.join(PROJECT_ROOT, "third_party/zeptun/zig-out/bin/zeptun"))
SIMPLETUN_BIN = os.environ.get("SIMPLETUN_BIN", os.path.join(PROJECT_ROOT, "third_party/simpletun/zig-out/bin/simpletun"))
XRAY_BIN = "/home/vanitas/Downloads/Xray-linux-64/xray"
SOCKS5_SINK_BIN = os.path.join(SCRIPT_DIR, "socks5_sink")
IDLE_BENCH_BIN = os.path.join(PROJECT_ROOT, "tools/idle_bench/idle_bench_linux_amd64")

class Colors:
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    RED = "\033[91m"
    CYAN = "\033[96m"
    BOLD = "\033[1m"
    RESET = "\033[0m"

def log_info(msg: str):
    print(f"{Colors.CYAN}[INFO]{Colors.RESET} {msg}")

def log_success(msg: str):
    print(f"{Colors.GREEN}[SUCCESS]{Colors.RESET} {msg}")

def log_warn(msg: str):
    print(f"{Colors.YELLOW}[WARN]{Colors.RESET} {msg}")

def log_error(msg: str):
    print(f"{Colors.RED}[ERROR]{Colors.RESET} {msg}")

def get_proc_memory_mb(pid: int) -> Dict[str, float]:
    """Reads PSS, RSS, and Private_Dirty in MB for a given PID from /proc."""
    pss_kb = 0.0
    rss_kb = 0.0
    private_dirty_kb = 0.0
    threads = 1

    # Try smaps_rollup first
    rollup_path = f"/proc/{pid}/smaps_rollup"
    if os.path.exists(rollup_path):
        try:
            with open(rollup_path, "r", encoding="utf-8") as f:
                for line in f:
                    if line.startswith("Pss:"):
                        pss_kb = float(line.split()[1])
                    elif line.startswith("Rss:"):
                        rss_kb = float(line.split()[1])
                    elif line.startswith("Private_Dirty:"):
                        private_dirty_kb = float(line.split()[1])
        except Exception:
            pass

    # Status for threads and fallback RSS
    status_path = f"/proc/{pid}/status"
    if os.path.exists(status_path):
        try:
            with open(status_path, "r", encoding="utf-8") as f:
                for line in f:
                    if line.startswith("Threads:"):
                        threads = int(line.split()[1])
                    elif rss_kb == 0.0 and line.startswith("VmRSS:"):
                        rss_kb = float(line.split()[1])
        except Exception:
            pass

    # Fallback PSS to RSS if PSS not present
    if pss_kb == 0.0:
        pss_kb = rss_kb

    return {
        "pss_mb": round(pss_kb / 1024.0, 2),
        "rss_mb": round(rss_kb / 1024.0, 2),
        "private_dirty_mb": round(private_dirty_kb / 1024.0, 2),
        "threads": threads
    }

class StandaloneBackendRunner:
    def __init__(self, backend: str, socks_port: int = 10800, tun_name: str = "tun0", mtu: int = 1500):
        self.backend = backend.lower()
        self.socks_port = socks_port
        self.tun_name = tun_name
        self.mtu = mtu
        self.proc: Optional[subprocess.Popen] = None
        self.tmp_cfg: Optional[str] = None

    def start(self) -> int:
        if self.backend == "hev":
            self.tmp_cfg = f"/tmp/hev_micro_{os.getpid()}.yml"
            with open(self.tmp_cfg, "w", encoding="utf-8") as f:
                f.write(f"""
tunnel:
  name: {self.tun_name}
  mtu: {self.mtu}
  ipv4: 172.16.0.1
socks5:
  port: {self.socks_port}
  address: 127.0.0.1
  udp: 'udp'
misc:
  log-level: warn
""")
            cmd = [HEV_BIN, self.tmp_cfg]
            self.proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        elif self.backend == "sing":
            cmd = [
                SING_BIN,
                "-tun", self.tun_name,
                "-socks-host", "127.0.0.1",
                "-socks-port", str(self.socks_port),
                "-mtu", str(self.mtu)
            ]
            self.proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        elif self.backend == "zeptun":
            cmd = [
                ZEPTUN_BIN, "run", "--preset", "mobile",
                "--tun", self.tun_name,
                "--mtu", str(self.mtu), "--handler", "socks5",
                "--socks5", f"127.0.0.1:{self.socks_port}",
                "--address", "172.16.0.1/30",
            ]
            self.proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        elif self.backend == "simpletun":
            cmd = [
                SIMPLETUN_BIN,
                "--tun", self.tun_name,
                "--socks5", f"127.0.0.1:{self.socks_port}",
            ]
            self.proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        elif self.backend == "xray":
            self.tmp_cfg = f"/tmp/xray_micro_{os.getpid()}.json"
            cfg = {
                "log": {"loglevel": "none"},
                "inbounds": [
                    {
                        "tag": "tun-in",
                        "protocol": "tun",
                        "settings": {
                            "name": self.tun_name,
                            "mtu": self.mtu
                        }
                    }
                ],
                "outbounds": [
                    {
                        "protocol": "socks",
                        "tag": "socks-out",
                        "settings": {
                            "servers": [
                                {
                                    "address": "127.0.0.1",
                                    "port": self.socks_port
                                }
                            ]
                        }
                    }
                ]
            }
            with open(self.tmp_cfg, "w", encoding="utf-8") as f:
                json.dump(cfg, f)
            cmd = [XRAY_BIN, "run", "-c", self.tmp_cfg]
            self.proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        else:
            raise ValueError(f"Unknown backend: {self.backend}")

        return self.proc.pid

    def stop(self):
        if self.proc:
            try:
                self.proc.terminate()
                self.proc.wait(timeout=2)
            except Exception:
                try:
                    self.proc.kill()
                    self.proc.wait()
                except Exception:
                    pass
            self.proc = None
        if self.tmp_cfg and os.path.exists(self.tmp_cfg):
            try:
                os.unlink(self.tmp_cfg)
            except Exception:
                pass
            self.tmp_cfg = None

def run_case_in_namespace(backend: str, network: str, steps: List[int], settle_sec: float) -> Dict[str, Any]:
    """
    Executes a single test case inside a dedicated unshare user/network namespace.
    """
    if backend == "zeptun" and not os.path.isfile(ZEPTUN_BIN):
        raise RuntimeError(
            f"Zeptun CLI not found: {ZEPTUN_BIN}. Build it with "
            "(cd third_party/zeptun && zig build), or set ZEPTUN_BIN."
        )

    # Orchestrator script run inside namespace
    steps_str = ",".join(str(s) for s in steps)
    socks_port = 10800
    target_ip = "10.0.0.2:10801"

    # We use a python child script inside unshare to coordinate
    inner_py = f"""
import sys, os, time, subprocess, json, signal

# 1. Bring up lo
subprocess.run(["ip", "link", "set", "lo", "up"], check=True)

# 2. Start SOCKS5 sink
sink_proc = subprocess.Popen(["{SOCKS5_SINK_BIN}", "--listen", "127.0.0.1:{socks_port}"],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(0.1)

# 3. Start TUN Backend
runner = None
backend = "{backend}"
if backend == "hev":
    cfg_file = "/tmp/hev_inner_{os.getpid()}.yml"
    with open(cfg_file, "w") as f:
        f.write(\"\"\"
tunnel:
  name: tun0
  mtu: 1500
  ipv4: 172.16.0.1
socks5:
  port: {socks_port}
  address: 127.0.0.1
  udp: 'udp'
misc:
  log-level: warn
\"\"\")
    tun_proc = subprocess.Popen(["{HEV_BIN}", cfg_file], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
elif backend == "sing":
    tun_proc = subprocess.Popen(["{SING_BIN}", "-tun", "tun0", "-socks-host", "127.0.0.1", "-socks-port", "{socks_port}", "-mtu", "1500"],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
elif backend == "zeptun":
    tun_proc = subprocess.Popen(["{ZEPTUN_BIN}", "run", "--preset", "mobile", "--tun", "tun0", "--mtu", "1500", "--handler", "socks5", "--socks5", "127.0.0.1:{socks_port}", "--address", "172.16.0.1/30"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
elif backend == "simpletun":
    tun_proc = subprocess.Popen(["{SIMPLETUN_BIN}", "--tun", "tun0", "--socks5", "127.0.0.1:{socks_port}"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
elif backend == "xray":
    cfg_file = "/tmp/xray_inner_{os.getpid()}.json"
    with open(cfg_file, "w") as f:
        json.dump({{
            "log": {{"loglevel": "none"}},
            "inbounds": [{{"tag": "tun-in", "protocol": "tun", "settings": {{"name": "tun0", "mtu": 1500}}}}],
            "outbounds": [{{"protocol": "socks", "tag": "socks-out", "settings": {{"servers": [{{"address": "127.0.0.1", "port": {socks_port}}}]}}}}]
        }}, f)
    tun_proc = subprocess.Popen(["{XRAY_BIN}", "run", "-c", cfg_file], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

time.sleep(0.6)

# 4. Bring up tun0 interface and configure routing
subprocess.run(["ip", "link", "set", "tun0", "up"], check=True)
subprocess.run(["ip", "addr", "add", "172.16.0.1/24", "dev", "tun0"], check=True)
subprocess.run(["ip", "route", "add", "10.0.0.0/8", "dev", "tun0"], check=True)

tun_pid = tun_proc.pid

def get_mem():
    pss = 0.0
    rss = 0.0
    pdirty = 0.0
    threads = 1
    try:
        with open(f"/proc/{{tun_pid}}/smaps_rollup", "r") as f:
            for l in f:
                if l.startswith("Pss:"): pss = float(l.split()[1])
                elif l.startswith("Rss:"): rss = float(l.split()[1])
                elif l.startswith("Private_Dirty:"): pdirty = float(l.split()[1])
    except Exception:
        pass
    try:
        with open(f"/proc/{{tun_pid}}/status", "r") as f:
            for l in f:
                if l.startswith("Threads:"): threads = int(l.split()[1])
                elif rss == 0.0 and l.startswith("VmRSS:"): rss = float(l.split()[1])
    except Exception:
        pass
    if pss == 0.0: pss = rss
    return round(pss/1024.0, 2), round(rss/1024.0, 2), round(pdirty/1024.0, 2), threads

# 5. Launch idle_bench client with pipe communication
bench_cmd = [
    "{IDLE_BENCH_BIN}", "client",
    "--server", "{target_ip}",
    "--network", "{network}",
    "--steps", "{steps_str}",
    "--settle", "{settle_sec}s"
]
bench_proc = subprocess.Popen(bench_cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)

measurements = []
for line in bench_proc.stdout:
    line = line.strip()
    if not line: continue
    try:
        data = json.loads(line)
        if data.get("status") == "settled":
            step = data.get("step")
            pss, rss, pdirty, threads = get_mem()
            measurements.append({{
                "conns": step,
                "pss_mb": pss,
                "rss_mb": rss,
                "private_dirty_mb": pdirty,
                "threads": threads
            }})
            sys.stderr.write(f"  [+] Step {{step}}/{steps[-1]} settled: PSS={{pss}} MB, RSS={{rss}} MB\\n")
            sys.stderr.flush()
            # Advance to next step
            if step != {steps[-1]}:
                bench_proc.stdin.write("\\n")
                bench_proc.stdin.flush()
        elif data.get("status") == "completed":
            break
    except Exception as e:
        pass

bench_proc.wait()

# Teardown
tun_proc.terminate()
sink_proc.terminate()
try: tun_proc.wait(timeout=1)
except Exception: tun_proc.kill()
try: sink_proc.wait(timeout=1)
except Exception: sink_proc.kill()

print(json.dumps(measurements))
"""

    unshare_cmd = ["unshare", "-r", "-n", sys.executable, "-c", inner_py]
    proc = subprocess.Popen(unshare_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    
    # Read stderr in a background thread or poll
    import threading
    def forward_stderr():
        for line in proc.stderr:
            sys.stdout.write(line)
            sys.stdout.flush()
    err_thread = threading.Thread(target=forward_stderr, daemon=True)
    err_thread.start()

    stdout, _ = proc.communicate()
    err_thread.join()

    if proc.returncode != 0:
        log_error(f"Unshare run failed: rc={proc.returncode}\n{stdout}")
        return {"backend": backend, "network": network, "flows": [], "slope": 0.0}

    try:
        flows = json.loads(stdout.strip().splitlines()[-1])
    except Exception as e:
        log_error(f"Failed to parse flow measurements: {e}\nRaw output: {res.stdout}")
        flows = []

    # Calculate slope
    slope_kib = 0.0
    if len(flows) >= 2:
        base_pss = flows[0]["pss_mb"]
        last_pss = flows[-1]["pss_mb"]
        conns_delta = flows[-1]["conns"] - flows[0]["conns"]
        if conns_delta > 0:
            slope_kib = round(((last_pss - base_pss) * 1024.0) / conns_delta, 2)

    return {
        "backend": backend,
        "network": network,
        "flows": flows,
        "slope_kib_per_conn": slope_kib
    }

def run_suite(backends: List[str], networks: List[str], rounds: int = 3) -> Dict[str, Any]:
    steps = [0, 250, 500, 750, 1000]
    all_data: Dict[str, Any] = {
        "metadata": {
            "timestamp": datetime.now().isoformat(),
            "target": "Linux x86_64 User Namespace (Pure Stack Microbenchmark)",
            "rounds": rounds,
            "steps": steps,
            "backends": backends,
            "networks": networks
        },
        "rounds": {}
    }

    for r in range(1, rounds + 1):
        round_key = f"round_{r}"
        log_info(f"\n================ STARTING STANDALONE ROUND {r}/{rounds} ================")
        round_cases = []
        for b in backends:
            for net in networks:
                log_info(f"Running Microbench: {b.upper()} ({net.upper()}) 0 -> 1000 flows...")
                result = run_case_in_namespace(b, net, steps, settle_sec=0.5)
                round_cases.append(result)
                slope = result.get("slope_kib_per_conn", 0.0)
                flows = result.get("flows", [])
                base_pss = flows[0]["pss_mb"] if flows else 0.0
                last_pss = flows[-1]["pss_mb"] if flows else 0.0
                log_success(f"{b.upper()} {net.upper()}: Base={base_pss} MB -> 1000={last_pss} MB | Slope: {slope:.2f} KiB/conn")
        all_data["rounds"][round_key] = round_cases

    return all_data

def format_markdown(all_data: Dict[str, Any]) -> str:
    lines = [
        "# SimpleXray Standalone TUN Micro-Benchmark Summary (Scheme 2)\n",
        f"Generated at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n",
        "Environment: Linux x86_64 Isolated User Namespace (Zero Android ART / Zero Framework Overhead)\n",
        "### Standalone Pure Stack Memory Slope (3 Rounds Summary)\n",
        "| Backend | Network | Base PSS (MB) | 1000 Conns PSS (MB) | R1 Slope | R2 Slope | R3 Slope | **Average Slope** |",
        "| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |"
    ]

    # Aggregate rounds
    rounds = all_data.get("rounds", {})
    r1 = rounds.get("round_1", [])
    r2 = rounds.get("round_2", [])
    r3 = rounds.get("round_3", [])

    for idx, item in enumerate(r1):
        b = item["backend"].upper()
        net = item["network"].upper()
        flows = item.get("flows", [])
        base_pss = flows[0]["pss_mb"] if flows else 0.0
        last_pss = flows[-1]["pss_mb"] if flows else 0.0

        s1 = item.get("slope_kib_per_conn", 0.0)
        s2 = r2[idx].get("slope_kib_per_conn", 0.0) if idx < len(r2) else s1
        s3 = r3[idx].get("slope_kib_per_conn", 0.0) if idx < len(r3) else s1
        avg_s = round((s1 + s2 + s3) / 3.0, 2)

        lines.append(f"| **{b}** | {net} | {base_pss:.1f} MB | {last_pss:.1f} MB | {s1:.2f} KiB | {s2:.2f} KiB | {s3:.2f} KiB | **{avg_s:.2f} KiB/conn** |")

    lines.append("\n---\n")
    return "\n".join(lines)

def main():
    parser = argparse.ArgumentParser(description="SimpleXray Standalone TUN Microbenchmark (Scheme 2)")
    parser.add_argument("--backends", default="hev,simpletun,sing", help="Comma-separated backends: hev,simpletun,sing,zeptun,xray")
    parser.add_argument("--network", default="all", help="'tcp', 'udp', or 'all'")
    parser.add_argument("--rounds", type=int, default=3, help="Number of test rounds")
    parser.add_argument("--output-json", default="docs/benchmark/microbench_results.json", help="JSON output file")
    parser.add_argument("--output-md", default="docs/benchmark/microbench_summary.md", help="Markdown output file")
    args = parser.parse_args()

    backends = [b.strip() for b in args.backends.split(",") if b.strip()]
    networks = ["tcp", "udp"] if args.network == "all" else [args.network.strip()]

    log_info(f"Starting Scheme 2 Microbenchmark: Backends={backends}, Networks={networks}, Rounds={args.rounds}")

    results = run_suite(backends, networks, rounds=args.rounds)

    json_path = os.path.abspath(args.output_json)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    log_success(f"Saved results to: {json_path}")

    md_report = format_markdown(results)
    md_path = os.path.abspath(args.output_md)
    with open(md_path, "w", encoding="utf-8") as f:
        f.write(md_report)
    log_success(f"Saved markdown summary to: {md_path}")
    print("\n" + md_report)

if __name__ == "__main__":
    main()
