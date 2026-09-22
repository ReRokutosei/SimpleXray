"""
Unified ADB interaction, device state control, tun0 verification, and hardware performance probes.
"""

import re
import shutil
import subprocess
import threading
import time
from typing import List, Optional, Tuple

from .logging import log_info, log_success, log_warn, log_error, run_cmd

APP_PKG = "com.simplexray.re.debug"


class AdbRunner:
    """
    Manages ADB connection, shell commands, Android service control, and device probes.
    """

    def __init__(self, device: Optional[str] = None):
        self.device = device
        # A force-stop already tears down the previous service/process. The next
        # stop intent would start BenchmarkService again and can re-enter the
        # native cleanup path before the next Go backend is started.
        self._just_force_reset = False
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

    def wake_device(self):
        """Wakes up screen and disables device idle / sleep during benchmark runs."""
        self.shell(f"cmd deviceidle whitelist +{APP_PKG}")
        self.shell("dumpsys deviceidle disable")
        self.shell("svc power stayon true")
        self.shell("input keyevent KEYCODE_WAKEUP")

    def set_app_state(self, backend: str, mtu: int, cmd: str = "start"):
        if cmd == "stop" and self._just_force_reset:
            log_info("Skipping stop intent after force_reset (process already clean).")
            self._just_force_reset = False
            return
        self._just_force_reset = False
        log_info(f"Controlling Headless Benchmark Service: cmd={cmd}, backend={backend}, mtu={mtu}")
        self.shell(
            f"am start-foreground-service --user 0 -n {APP_PKG}/com.simplexray.re.service.BenchmarkService "
            f"--es cmd {cmd} --es backend {backend} --ei mtu {mtu}",
            timeout=10.0
        )

    def wakeup_app_from_stopped_state(self):
        """Ensures the app Activity is in the foreground before issuing service intents.

        On HyperOS/MIUI (and Android 14+ in general), am start-foreground-service is blocked
        when the app is in the background or in stopped state. Launching the Activity brings
        the app to the foreground, allowing BenchmarkService intents to be received.

        The Activity is intentionally LEFT in the foreground (no HOME key) so that subsequent
        set_app_state() calls succeed under HyperOS background execution restrictions.
        Safe to call even when the app is already in the foreground.
        """
        rc, out, _ = self.shell(f"dumpsys package {APP_PKG} | grep 'stopped='")
        if "stopped=true" in out:
            log_info(f"App is in Android stopped state. Launching Activity to exit stopped state...")
        else:
            log_info(f"Ensuring {APP_PKG} Activity is in foreground for service intent delivery...")
        self.shell(
            f"am start --user 0 -n {APP_PKG}/com.simplexray.re.MainActivityLineal "
            f"--activity-no-animation -f 0x10000000",
            timeout=10.0
        )
        # Wait for Activity + Application to fully initialize before service intents are sent.
        # Do NOT send KEYCODE_HOME: keeping the Activity in foreground is required for
        # HyperOS/MIUI to allow am start-foreground-service to succeed.
        time.sleep(2.0)
        log_info("App Activity is in foreground. Ready to receive service intents.")

    def force_reset_app(self):
        """Force-stops the app to reset Go runtime state between different Go-based TUN backends.

        libsingtun.so and libmipstun.so are each compiled as independent Go c-shared libraries.
        Loading both sequentially in the same process causes fatal Go runtime conflicts
        (fatal error: unknown caller pc via cgocallbackg). Calling this between backends ensures
        the next backend starts in a fresh process with no Go runtime residue.
        """
        log_info(f"Force-stopping {APP_PKG} to isolate Go runtime between backends...")
        self.shell(f"am force-stop {APP_PKG}", timeout=10.0)
        time.sleep(1.0)
        # After force-stop, the app enters Android's stopped state. Wake it via Activity
        # so the next am start-foreground-service intent can be received on Android 14+.
        self.wakeup_app_from_stopped_state()
        self._just_force_reset = True
        log_info("App process reset complete. Ready for next backend cold-start.")

    def wait_for_tun0(self, backend: str, timeout_sec: int = 15):
        """Strictly verifies that Android kernel has established tun0 interface."""
        log_info(f"Waiting up to {timeout_sec}s for VPN & proxy backend '{backend}' to establish tun0...")
        tun_ready = False
        for _ in range(timeout_sec):
            time.sleep(1)
            rc, out, _ = self.shell("ip addr show dev tun0")
            if rc == 0 and "tun0" in out:
                tun_ready = True
                break

        if not tun_ready:
            log_error(f"Failed to establish VPN interface 'tun0' for backend '{backend}'!")
            raise RuntimeError(
                f"FATAL: 'tun0' interface does not exist after starting backend '{backend}'. "
                f"Ensure VpnService is properly authorized on device and not returning null on establish()."
            )
        log_success(f"Verified VPN interface 'tun0' is UP for backend '{backend}'.")

    def wait_for_no_tun0(self, timeout_sec: int = 10):
        """Waits until tun0 interface is fully torn down after a backend stop.

        After sending ACTION_DISCONNECT to TProxyService, the VPN fd is released
        asynchronously. Starting the next backend before tun0 disappears may cause
        establish() to return null (VPN already held). This method polls until tun0
        is gone or the timeout elapses.
        """
        for i in range(timeout_sec):
            rc, out, _ = self.shell("ip addr show dev tun0")
            if rc != 0 or "tun0" not in out:
                log_info(f"tun0 is down after {i}s — VPN teardown complete.")
                return
            time.sleep(1)
        log_warn(f"tun0 still present after {timeout_sec}s stop wait; proceeding anyway.")

    def ensure_no_tun0(self):
        """Ensures tun0 interface is completely torn down (for physical baseline tests)."""
        rc, out, _ = self.shell("ip addr show dev tun0")
        if rc == 0 and "tun0" in out:
            log_warn("Warning: 'tun0' interface is still active during baseline test! Stopping...")
            self.set_app_state("hev", 1500, cmd="stop")
            time.sleep(2)

    def get_route_source_ip(self, target_ip: str) -> Optional[str]:
        """Returns the device source IP used to reach target_ip, if resolvable."""
        rc, out, _ = self.shell(f"ip route get {target_ip}", timeout=5.0)
        match = re.search(r"\bsrc\s+(\d{1,3}(?:\.\d{1,3}){3})", out)
        if match:
            return match.group(1)
        return None

    def get_app_memory_mb(self) -> float:
        """Samples current PSS memory in MB with retry."""
        for _ in range(3):
            rc, out, _ = self.shell(f"dumpsys meminfo {APP_PKG} | grep 'TOTAL PSS:'", timeout=5.0)
            match = re.search(r"TOTAL PSS:\s+(\d+)", out)
            if match:
                pss_kb = float(match.group(1))
                return round(pss_kb / 1024.0, 1)
            time.sleep(0.3)
        return 0.0

    def ping_rtt(self, target_ip: str, count: int = 5, timeout_sec: float = 6.0) -> Optional[float]:
        """Executes ICMP ping from device to target_ip, returns average RTT in ms."""
        rc, out, _ = self.shell(f"ping -c {count} -W 1 {target_ip}", timeout=timeout_sec)
        match = re.search(r"rtt min/avg/max/mdev = [\d\.]+/([\d\.]+)/", out)
        if match:
            try:
                return float(match.group(1))
            except ValueError:
                pass
        return None


class CpuProfiler:
    """
    Samples cumulative multi-core CPU utilization percentage for the application UID.
    """

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
        current_frame_cpu = 0.0
        in_frame = False
        try:
            while not self._stop_event.is_set():
                line = proc.stdout.readline()
                if not line:
                    break
                trimmed = line.strip()
                if trimmed.startswith("Tasks:"):
                    if in_frame:
                        self.cpu_samples.append(round(current_frame_cpu, 1))
                        current_frame_cpu = 0.0
                    in_frame = True
                    continue
                if any(k in trimmed for k in [APP_PKG, "libxray", "simplexray", "sing-box"]):
                    parts = trimmed.split()
                    if len(parts) >= 9:
                        try:
                            # In Android top -b: PID USER PR NI VIRT RES SHR S [%CPU] %MEM TIME+ ARGS
                            cpu_val = float(parts[8])
                            current_frame_cpu += cpu_val
                        except ValueError:
                            pass
            if in_frame:
                self.cpu_samples.append(round(current_frame_cpu, 1))
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
