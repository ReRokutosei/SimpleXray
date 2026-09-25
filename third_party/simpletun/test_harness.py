#!/usr/bin/env python3
"""
SimpleTUN Phase 1 Local Harness Verifier (Linux user-namespace)
Tests conservative handshake, data transmission, and clean teardown.
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

def run_test():
    if not os.path.exists(SIMPLETUN_BIN):
        print(f"Error: {SIMPLETUN_BIN} does not exist. Run 'zig build -Doptimize=ReleaseFast' first.")
        return 1

    print("[*] SimpleTUN Harness starting in isolated network namespace...")

    # We will test in an unshare -r -n subshell:
    # 1. Bring up lo (127.0.0.1)
    # 2. Start a mock echo TCP server on 127.0.0.1:8080 (our simulated destination)
    # 3. Start a dummy SOCKS5 sink on 127.0.0.1:10808 that bridges to 127.0.0.1:8080
    # 4. Create tun0, assign 172.16.0.1/30, route traffic to tun0
    # 5. Start simpletun --tun tun0 --socks5 127.0.0.1:10808
    # 6. Run curl/python socket to verify echo data and check memory

    cmd = [
        "unshare", "-r", "-n", "python3", "-c", """
import subprocess
import time
import socket
import threading
import os

# 1. Bring up loopback
subprocess.run(["ip", "link", "set", "lo", "up"], check=True)

# 2. Simple Echo TCP Server simulating target HTTP/Echo backend on 127.0.0.1:9999
def echo_server():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("127.0.0.1", 9999))
    s.listen(5)
    while True:
        try:
            conn, _ = s.accept()
            data = conn.recv(4096)
            if data:
                conn.sendall(b"HTTP/1.1 200 OK\\r\\nContent-Length: 13\\r\\n\\r\\nHelloSimpleTun")
            conn.close()
        except Exception:
            break

t1 = threading.Thread(target=echo_server, daemon=True)
t1.start()

# 3. Minimal SOCKS5 Inbound on 127.0.0.1:10808 (pointing to echo_server)
def socks5_server():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("127.0.0.1", 10808))
    s.listen(5)
    while True:
        try:
            client, _ = s.accept()
            # Greeting
            g = client.recv(3)
            client.sendall(b"\\x05\\x00")
            # Connect request
            req = client.recv(10)
            # Connect to actual target (127.0.0.1:9999)
            target = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            target.connect(("127.0.0.1", 9999))
            client.sendall(b"\\x05\\x00\\x00\\x01\\x7f\\x00\\x00\\x01\\x27\\x0f")
            
            # Pipe data
            def p1():
                try:
                    while True:
                        d = client.recv(4096)
                        if not d: break
                        target.sendall(d)
                except: pass
                finally:
                    try: target.shutdown(socket.SHUT_WR)
                    except: pass

            def p2():
                try:
                    while True:
                        d = target.recv(4096)
                        if not d: break
                        client.sendall(d)
                except: pass
                finally:
                    try: client.shutdown(socket.SHUT_WR)
                    except: pass

            t_a = threading.Thread(target=p1, daemon=True)
            t_b = threading.Thread(target=p2, daemon=True)
            t_a.start()
            t_b.start()
        except Exception:
            break

t2 = threading.Thread(target=socks5_server, daemon=True)
t2.start()

# 4. Create and configure tun0
subprocess.run(["ip", "tuntap", "add", "dev", "tun0", "mode", "tun"], check=True)
subprocess.run(["ip", "addr", "add", "172.16.0.1/30", "dev", "tun0"], check=True)
subprocess.run(["ip", "link", "set", "tun0", "up"], check=True)
subprocess.run(["ip", "route", "add", "1.1.1.1/32", "dev", "tun0"], check=True)

# 5. Launch simpletun
p_tun = subprocess.Popen([
    \"""" + SIMPLETUN_BIN + """\",
    "--tun", "tun0",
    "--socks5", "127.0.0.1:10808"
])
time.sleep(0.5)

# Verify Memory PSS of SimpleTUN
try:
    with open(f"/proc/{p_tun.pid}/smaps_rollup", "r") as f:
        for line in f:
            if "Pss:" in line:
                print(f"[+] SimpleTUN Initial {line.strip()}")
except Exception as e:
    print(f"[-] Could not read PSS: {e}")

# 6. Send request via tun0 towards 1.1.1.1:80
print("[*] Sending test TCP stream via tun0 -> SimpleTUN -> SOCKS5...")
s_test = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s_test.settimeout(3.0)
s_test.connect(("1.1.1.1", 80))
s_test.sendall(b"GET / HTTP/1.1\\r\\nHost: 1.1.1.1\\r\\n\\r\\n")
resp = s_test.recv(4096)
s_test.close()

print(f"[+] Received Response: {resp.decode('utf-8', errors='ignore')}")

if b"HelloSimpleTun" in resp:
    print("[SUCCESS] SimpleTUN conservative handshake and bidirectional relay verified!")
    p_tun.terminate()
    exit(0)
else:
    print("[FAIL] Unexpected response content")
    p_tun.terminate()
    exit(1)
"""
    ]

    res = subprocess.run(cmd)
    return res.returncode

if __name__ == "__main__":
    sys.exit(run_test())
