"""
Host-side Linux netem control for weak-network benchmark scenarios.
"""

import argparse
import os
import shutil
import subprocess
from typing import List, Optional


class NetemError(RuntimeError):
    """Raised when a netem operation cannot be completed."""


def _require_tc() -> str:
    tc_path = shutil.which("tc")
    if not tc_path:
        raise NetemError(
            "tc not found. Install iproute2 on the host before running weak-network tests."
        )
    return tc_path


def get_route_interface(target_ip: str) -> str:
    """Returns the host interface used to reach target_ip."""
    ip_path = shutil.which("ip")
    if not ip_path:
        raise NetemError("ip not found. Install iproute2 on the host.")

    try:
        out = subprocess.check_output(
            [ip_path, "route", "get", target_ip],
            text=True,
            stderr=subprocess.STDOUT,
        )
    except subprocess.CalledProcessError as exc:
        raise NetemError(f"Failed to resolve route to {target_ip}: {exc.output.strip()}") from exc

    parts = out.split()
    if "dev" not in parts:
        raise NetemError(f"Could not find 'dev' in route output: {out.strip()}")
    return parts[parts.index("dev") + 1]


def _format_loss(loss_percent: float) -> str:
    return f"{loss_percent:g}%"


class NetemController:
    """Applies and clears a single root netem qdisc on a Linux host interface."""

    def __init__(
        self,
        interface: str,
        delay_ms: Optional[float] = None,
        jitter_ms: Optional[float] = None,
        loss_percent: Optional[float] = None,
        dry_run: bool = False,
        use_sudo: bool = True,
    ):
        self.interface = interface
        self.delay_ms = delay_ms
        self.jitter_ms = jitter_ms
        self.loss_percent = loss_percent
        self.dry_run = dry_run
        self.use_sudo = use_sudo and os.geteuid() != 0

    def _base_cmd(self) -> List[str]:
        tc_path = "tc" if self.dry_run else _require_tc()
        return (["sudo", "-n"] if self.use_sudo else []) + [tc_path]

    def _run(self, args: List[str], check: bool = True) -> subprocess.CompletedProcess:
        cmd = self._base_cmd() + args
        if self.dry_run:
            print(f"[netem dry-run] {' '.join(cmd)}")
            return subprocess.CompletedProcess(cmd, 0, "", "")
        try:
            return subprocess.run(cmd, text=True, capture_output=True, check=check)
        except FileNotFoundError as exc:
            raise NetemError(f"Command not found: {cmd[0]}") from exc
        except subprocess.CalledProcessError as exc:
            detail = (exc.stderr or exc.stdout or "").strip()
            raise NetemError(f"Command failed: {' '.join(cmd)}\n{detail}") from exc

    def _netem_args(self) -> List[str]:
        args = ["netem"]
        if self.delay_ms is not None:
            args += ["delay", f"{self.delay_ms:g}ms"]
            if self.jitter_ms is not None:
                args.append(f"{self.jitter_ms:g}ms")
        if self.loss_percent is not None:
            args += ["loss", _format_loss(self.loss_percent)]
        return args

    def apply(
        self,
        delay_ms: Optional[float] = None,
        jitter_ms: Optional[float] = None,
        loss_percent: Optional[float] = None,
    ) -> None:
        if delay_ms is not None:
            self.delay_ms = delay_ms
        if jitter_ms is not None:
            self.jitter_ms = jitter_ms
        if loss_percent is not None:
            self.loss_percent = loss_percent

        args = ["qdisc", "replace", "dev", self.interface, "root"] + self._netem_args()
        self._run(args)
        print(
            f"[netem] applied on {self.interface}: "
            f"delay={self.delay_ms}ms jitter={self.jitter_ms}ms loss={self.loss_percent}%"
        )

    def clear(self) -> None:
        self._run(["qdisc", "del", "dev", self.interface, "root"], check=False)
        print(f"[netem] cleared on {self.interface}")

    def show(self) -> str:
        result = self._run(["qdisc", "show", "dev", self.interface], check=False)
        return result.stdout

    def __enter__(self) -> "NetemController":
        self.apply()
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.clear()


def main() -> None:
    parser = argparse.ArgumentParser(description="Host-side netem controller")
    parser.add_argument("--action", choices=["apply", "clear", "show"], required=True)
    parser.add_argument("--iface", default=None, help="Host interface. If omitted, resolve via --target")
    parser.add_argument("--target", default=None, help="Target IP used for automatic interface resolution")
    parser.add_argument("--delay", type=float, default=50.0, help="Delay in milliseconds")
    parser.add_argument("--jitter", type=float, default=None, help="Delay jitter in milliseconds")
    parser.add_argument("--loss", type=float, default=0.0, help="Loss percentage")
    parser.add_argument("--dry-run", action="store_true", help="Print tc commands without applying them")
    args = parser.parse_args()

    interface = args.iface
    if not interface:
        if not args.target:
            raise SystemExit("Either --iface or --target is required")
        interface = get_route_interface(args.target)

    controller = NetemController(
        interface=interface,
        delay_ms=args.delay,
        jitter_ms=args.jitter,
        loss_percent=args.loss,
        dry_run=args.dry_run,
    )

    if args.action == "apply":
        controller.apply()
    elif args.action == "clear":
        controller.clear()
    elif args.action == "show":
        print(controller.show())


if __name__ == "__main__":
    main()
