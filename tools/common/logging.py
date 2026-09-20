"""
Terminal styling and subprocess execution helpers.
"""

import subprocess
from typing import List, Optional, Tuple


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
