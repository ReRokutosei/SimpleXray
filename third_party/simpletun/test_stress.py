#!/usr/bin/env python3
"""
SimpleTUN Phase 2 Stress & Boundary Verifier
Using standard Go socks5_sink for production-grade concurrency testing.
"""
import os
import sys
import time
import socket
import threading
import subprocess

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../.."))
SIMPLETUN_BIN = os.path.join(REPO_ROOT, "third_party/simpletun/zig-out/bin/simpletun")
SOCKS5_SINK_BIN = os.path.join(REPO_ROOT, "tools/microbench/socks5_sink")

def run_stress_test():
    cmd = [
        "unshare", "-r", "-n", "python3", "-c", """
import subprocess
import time
import socket
import threading
import os

subprocess.run(["ip", "link", "set", "lo", "up"], check=True)

# 1. Launch production-grade Go SOCKS5 sink on 127.0.0.1:10808
p_sink = subprocess.Popen([
    \"""" + SOCKS5_SINK_BIN + """\",
    "-listen", "127.0.0.1:10808"
])
time.sleep(0.3)

# 2. TUN interface setup
subprocess.run(["ip", "tuntap", "add", "dev", "tun0", "mode", "tun"], check=True)
subprocess.run(["ip", "addr", "add", "172.16.0.1/30", "dev", "tun0"], check=True)
subprocess.run(["ip", "link", "set", "tun0", "up"], check=True)
subprocess.run(["ip", "route", "add", "1.1.1.1/32", "dev", "tun0"], check=True)

# 3. Launch SimpleTUN
p_tun = subprocess.Popen([
    \"""" + SIMPLETUN_BIN + """\",
    "--tun", "tun0",
    "--socks5", "127.0.0.1:10808"
])
time.sleep(0.3)

# Test 1: 50 Sequential Rapid Requests
print("[*] Running 50 sequential rapid requests to test Tombstone churn & reuse...")
success_count = 0
for i in range(50):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(2.0)
        s.connect(("1.1.1.1", 80))
        s.sendall(b"PING50_SEQ")
        resp = s.recv(1024)
        s.close()
        if b"PING50_SEQ" in resp:
            success_count += 1
    except Exception as e:
        print(f"[-] Req {i} failed: {e}")

print(f"[+] Sequential Rapid Requests Success: {success_count}/50")
assert success_count == 50, "Not all sequential requests succeeded!"

# Test 2: 20 Concurrent Connections
print("[*] Running 20 concurrent connections...")
concurrent_success = 0
lock = threading.Lock()

def worker(idx):
    global concurrent_success
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(3.0)
        s.connect(("1.1.1.1", 80))
        payload = f"CONCURRENT_PING_{idx}".encode()
        s.sendall(payload)
        resp = s.recv(1024)
        s.close()
        if payload in resp:
            with lock:
                concurrent_success += 1
    except Exception as e:
        print(f"[-] Concurrent worker {idx} failed: {e}")

threads = [threading.Thread(target=worker, args=(i,)) for i in range(20)]
for t in threads: t.start()
for t in threads: t.join()

print(f"[+] Concurrent Connections Success: {concurrent_success}/20")
assert concurrent_success == 20, "Concurrent connections test failed!"

# Memory PSS Check after load
try:
    with open(f"/proc/{p_tun.pid}/smaps_rollup", "r") as f:
        for line in f:
            if "Pss:" in line:
                print(f"[+] SimpleTUN Post-Load {line.strip()}")
except Exception as e:
    pass

p_tun.terminate()
p_sink.terminate()
print("[SUCCESS] All Phase 2 Stress & Concurrency tests passed cleanly!")
"""
    ]
    res = subprocess.run(cmd)
    return res.returncode

if __name__ == "__main__":
    sys.exit(run_stress_test())
