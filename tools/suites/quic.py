"""
QUIC / HTTP3 smoke suite.

Host runs a self-signed HTTP/3 server. A tiny quic-go client is cross-compiled
for Android arm64 and pushed to the device; it performs one small request and
one 10 MiB download through the active TUN backend.

This is a smoke/regression suite, not a precise UDP throughput benchmark.
"""

import json
import os
import shutil
import subprocess
import tempfile
import time
from typing import Any, Dict, List, Optional, Tuple

from common.logging import log_error, log_info, log_success, log_warn

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TOOLS_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
QUIC_SRC_DIR = os.path.join(TOOLS_DIR, "quic")
DEVICE_BIN = "/data/local/tmp/simplexray_quic"
DEFAULT_PORT = 4433
DEFAULT_TIMEOUT_SEC = 30
GO_BACKENDS = {"xray"}

_host_bin: Optional[str] = None
_android_bin: Optional[str] = None


def _build_binaries() -> Tuple[str, str]:
    global _host_bin, _android_bin
    if _host_bin and _android_bin and os.path.exists(_host_bin) and os.path.exists(_android_bin):
        return _host_bin, _android_bin

    go = shutil.which("go")
    if not go:
        raise RuntimeError("go binary not found in PATH (required by QUIC smoke suite)")

    build_dir = os.path.join(tempfile.gettempdir(), "simpletun_quic_build")
    os.makedirs(build_dir, exist_ok=True)
    host_bin = os.path.join(build_dir, "quic_smoke_host")
    android_bin = os.path.join(build_dir, "quic_smoke_android")

    env = os.environ.copy()
    env.setdefault("GOSUMDB", "off")
    env.setdefault("GOCACHE", os.path.join(build_dir, "go-build"))
    env.setdefault("GOMODCACHE", os.path.join(build_dir, "go-mod-cache"))

    log_info("[quic] building QUIC helper binaries...")
    subprocess.run([go, "build", "-o", host_bin, "."], cwd=QUIC_SRC_DIR, env=env, check=True)
    android_env = env.copy()
    android_env.update({"GOOS": "android", "GOARCH": "arm64", "CGO_ENABLED": "0"})
    subprocess.run([go, "build", "-o", android_bin, "."], cwd=QUIC_SRC_DIR, env=android_env, check=True)

    _host_bin = host_bin
    _android_bin = android_bin
    return host_bin, android_bin


def _push_client(adb) -> None:
    _, android_bin = _build_binaries()
    rc, _, err = adb.exec(["push", android_bin, DEVICE_BIN])
    if rc != 0:
        raise RuntimeError(f"failed to push QUIC client: {err.strip()}")
    rc, _, err = adb.shell(f"chmod 755 {DEVICE_BIN}")
    if rc != 0:
        raise RuntimeError(f"failed to chmod QUIC client: {err.strip()}")


def _parse_client_output(raw: str) -> Dict[str, Any]:
    for line in reversed(raw.strip().splitlines()):
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            return json.loads(line)
        except json.JSONDecodeError:
            continue
    return {"ok": False, "error": f"no JSON result: {raw[:300]}"}


def _run_quic_client(adb, url: str, timeout_sec: int) -> Dict[str, Any]:
    cmd = f"{DEVICE_BIN} -mode client -url {url} -timeout {timeout_sec}s"
    rc, out, err = adb.shell(cmd, timeout=timeout_sec + 15)
    result = _parse_client_output(out)
    if rc != 0:
        result["ok"] = False
        result.setdefault("error", f"client rc={rc}")
    if err.strip():
        result["stderr"] = err.strip()[-300:]
    return result


def run_quic_suite(
    adb,
    host_ip: str,
    backends: List[str],
    port: int = DEFAULT_PORT,
    timeout_sec: int = DEFAULT_TIMEOUT_SEC,
) -> List[Dict[str, Any]]:
    """Runs one QUIC smoke round across the requested backends."""
    host_bin, _ = _build_binaries()
    _push_client(adb)

    server_log_path = os.path.join(tempfile.gettempdir(), "simpletun_quic_server.log")
    server_log = open(server_log_path, "w", encoding="utf-8")
    server = subprocess.Popen(
        [host_bin, "-mode", "server", "-addr", f"0.0.0.0:{port}"],
        stdout=server_log,
        stderr=subprocess.STDOUT,
    )
    time.sleep(1.0)
    if server.poll() is not None:
        server_log.close()
        raise RuntimeError(f"QUIC server exited early; see {server_log_path}")

    results: List[Dict[str, Any]] = []
    prev_backend: Optional[str] = None
    try:
        for backend in backends:
            if prev_backend is not None and (
                backend in GO_BACKENDS or prev_backend in GO_BACKENDS
            ):
                adb.force_reset_app()
            prev_backend = backend

            adb.set_app_state(backend, 1500, cmd="stop")
            adb.wait_for_no_tun0()
            if backend != "direct_none":
                adb.set_app_state(backend, 1500, cmd="start")
                adb.wait_for_tun0(backend)
            else:
                adb.ensure_no_tun0()
            time.sleep(1.0)

            for case, path in (("hello", "/hello"), ("payload_10m", "/payload")):
                url = f"https://{host_ip}:{port}{path}"
                log_info(f"[quic] {backend} {case}")
                result = _run_quic_client(adb, url, timeout_sec)
                result.update({
                    "backend": backend,
                    "network": "http3",
                    "medium": "5GHz Wi-Fi",
                    "case": case,
                    "url": url,
                })
                results.append(result)
                if result.get("ok"):
                    if case == "hello":
                        log_success(
                            f"[quic] {backend} {case}: "
                            f"{float(result.get('seconds', 0.0)) * 1000:.1f} ms "
                            f"{result.get('proto')}"
                        )
                    else:
                        log_success(
                            f"[quic] {backend} {case}: "
                            f"{float(result.get('mbps', 0.0)):.1f} Mbps "
                            f"{result.get('proto')}"
                        )
                else:
                    log_warn(f"[quic] {backend} {case} failed: {result.get('error', 'unknown')}")

            if backend != "direct_none":
                adb.set_app_state(backend, 1500, cmd="stop")
                adb.wait_for_no_tun0()
    finally:
        server.terminate()
        try:
            server.wait(timeout=3)
        except subprocess.TimeoutExpired:
            server.kill()
        server_log.close()

    return results
