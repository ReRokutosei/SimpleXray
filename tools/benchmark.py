#!/usr/bin/env python3
"""
SimpleXray Unified Benchmark Master Runner
Supports standardized execution via '--preset light' or '--preset full',
as well as fine-grained manual arguments across all suites:
- throughput (Wi-Fi, USB, Loopback)
- idle_memory (0 -> 1000 stepped connection retention)
- bufferbloat (loaded latency delta probe)
- stability (60-second high-throughput stability & attenuation)
- cps (short-lived connection-per-second rate)
- weaknet (host netem loss & delay emulation)
"""

import argparse
import copy
import json
import os
import sys
from datetime import datetime
from typing import Any, Dict, List, Optional

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)

from common.adb import AdbRunner, APP_PKG
from common.device import (
    DEFAULT_DEVICE,
    DEVICE_PROFILES,
    resolve_device_paths,
)
from common.netem import NetemError, get_route_interface
from common.logging import (
    Colors,
    log_info,
    log_success,
    log_warn,
    log_error,
    run_cmd,
)
from common.theme import BACKEND_ORDER, TARGET_ORDER
from presets import PRESETS, get_preset
from suites import (
    run_media_suite,
    run_loopback_suite,
    run_idle_suite,
    run_bufferbloat_suite,
    run_stability_suite,
    run_weaknet_suite,
    run_cps_suite,
)

DEFAULT_DURATION = 10


def format_markdown_table(results: List[Dict[str, Any]], title: str = "Benchmark Results") -> str:
    standard_results = [r for r in results if r.get("type") != "idle_memory"]
    idle_results = [r for r in results if r.get("type") == "idle_memory"]

    lines = [
        f"### {title}\n",
        "| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |",
        "| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |"
    ]
    for r in standard_results:
        loss_up = f" ({r.get('upload_loss_percent', 0.0):.1f}% loss)" if r.get("network") == "udp" else ""
        loss_down = f" ({r.get('download_loss_percent', 0.0):.1f}% loss)" if r.get("network") == "udp" else ""
        lines.append(
            f"| {r['name']} | {r['backend'].upper()} | {r['mtu']} | {r['medium']} | "
            f"{r['upload_mbps']} Mbps{loss_up} | {r['download_mbps']} Mbps{loss_down} | "
            f"{r['upload_cpu_avg']:.1f}% | {r['download_cpu_avg']:.1f}% | {r['peak_cpu']:.1f}% | {r['peak_mem_mb']:.1f} MB |"
        )

    if idle_results:
        lines.append("\n#### Idle Memory Retention Results (0 -> 1000 Flows)\n")
        lines.append("| Backend | Network | Conns Range | Baseline PSS | 1000 Conns PSS | Memory Slope |")
        lines.append("| :--- | :---: | :---: | :---: | :---: | :---: |")
        for ir in idle_results:
            flows = ir.get("idle_flows", [])
            base = flows[0]["pss_mb"] if flows else 0.0
            last = flows[-1]["pss_mb"] if flows else 0.0
            slope = ir.get("slope_kib_per_conn", 0.0)
            lines.append(f"| {ir['backend'].upper()} | {ir['network'].upper()} | 0 -> 1000 | {base:.1f} MB | {last:.1f} MB | {slope:.2f} KiB/conn |")

    return "\n".join(lines) + "\n"


def auto_detect_usb_ip() -> str:
    rc, out, _ = run_cmd(["ip", "-brief", "address"])
    for line in out.splitlines():
        parts = line.split()
        if len(parts) >= 3 and any(parts[0].startswith(prefix) for prefix in ["enx", "rndis", "usb"]):
            return parts[2].split("/")[0]
    return "192.168.232.59"


def main():
    parser = argparse.ArgumentParser(description="SimpleXray Unified TUN Benchmark Master Runner")
    parser.add_argument("--preset", choices=list(PRESETS.keys()), default=None,
                        help="Benchmark preset contract: 'light' (Hev/SingTUN/Zeptun, Wi-Fi 1500) or 'full' (all backends/media)")
    parser.add_argument(
        "--mode",
        default=None,
        help="Suites to run: throughput, idle_memory, bufferbloat, stability, weaknet, cps, all (comma-separated)"
    )
    parser.add_argument("--network", default=None, choices=["tcp", "udp", "all"],
                        help="Network protocols to benchmark ('tcp', 'udp', or 'all')")
    parser.add_argument("--backends", default=None,
                        help="TUN backends to benchmark (comma-separated: hev,sing,xray,zeptun, or 'all')")
    parser.add_argument("--skip-baseline", action="store_true", default=None,
                        help="Skip running physical baseline (No VPN) tests")
    parser.add_argument("--wifi-server-ip", default="192.168.31.236",
                        help="Host PC IP in Wi-Fi subnet (default: 192.168.31.236)")
    parser.add_argument("--usb-server-ip", default="auto",
                        help="Host PC IP in USB tethering subnet (default: auto)")
    parser.add_argument("--duration", type=int, default=None,
                        help="Duration in seconds per throughput test direction (default: 10)")
    parser.add_argument("--device", default=None,
                        help="ADB device serial if multiple devices are connected")
    parser.add_argument("--device-profile", default=DEFAULT_DEVICE, choices=sorted(DEVICE_PROFILES),
                        help=f"Device profile used for default output paths (default: {DEFAULT_DEVICE})")
    parser.add_argument("--rounds", type=int, default=None,
                        help="Number of test rounds to execute (default: 3)")
    parser.add_argument("--no-jumbo", action="store_true", default=None,
                        help="Skip MTU 9000 throughput cases")
    parser.add_argument("--netem-iface", default=None,
                        help="Host interface for netem; auto-resolved from --wifi-server-ip when omitted")
    parser.add_argument("--netem-losses", default=None,
                        help="Comma-separated loss percentages for weaknet mode (e.g. '3,5,8')")
    parser.add_argument("--netem-delay", type=float, default=None,
                        help="Netem delay in milliseconds (default: 50.0)")
    parser.add_argument("--weaknet-parallel", type=int, default=None,
                        help="Parallel streams for weaknet tests (default: 8)")
    parser.add_argument("--weaknet-duration", type=int, default=None,
                        help="Weaknet duration in seconds")
    parser.add_argument("--netem-dry-run", action="store_true",
                        help="Print netem commands without applying them")
    parser.add_argument("--skip-netem", action="store_true",
                        help="Run weaknet traffic without applying a netem qdisc")
    parser.add_argument("--cps-workers", default=None,
                        help="Comma-separated worker counts for CPS mode (e.g. '4,8')")
    parser.add_argument("--cps-connections", type=int, default=None,
                        help="Total short-lived connections per CPS case (default: 5000)")
    parser.add_argument("--cps-timeout", type=int, default=None,
                        help="Per-CPS-case timeout in seconds (default: 120)")
    args = parser.parse_args()

    preset = get_preset(args.preset)
    profile = resolve_device_paths(args.device_profile)

    # 1. Resolve configuration through Preset with CLI overrides
    if preset:
        log_info(f"Loaded Benchmark Preset: [{preset.name.upper()}] - {preset.description}")
        backends = args.backends.split(",") if args.backends else list(preset.backends)
        networks = ["tcp", "udp"] if (args.network == "all" or (not args.network and "tcp" in preset.networks and "udp" in preset.networks)) else ([args.network] if args.network else list(preset.networks))
        duration = args.duration or preset.duration
        rounds = args.rounds or preset.rounds
        skip_baseline = args.skip_baseline if args.skip_baseline is not None else preset.skip_baseline
        include_jumbo = (not args.no_jumbo) if args.no_jumbo else preset.include_jumbo
        
        # Weaknet
        netem_losses = [float(x) for x in args.netem_losses.split(",") if x.strip()] if args.netem_losses else list(preset.netem_losses)
        netem_delay = args.netem_delay if args.netem_delay is not None else preset.netem_delay
        weaknet_parallel = args.weaknet_parallel or preset.weaknet_parallel
        weaknet_duration = args.weaknet_duration or preset.weaknet_duration or duration
        
        # CPS
        cps_workers = [int(x) for x in args.cps_workers.split(",") if x.strip()] if args.cps_workers else list(preset.cps_workers)
        cps_connections = args.cps_connections or preset.cps_connections
        cps_timeout = args.cps_timeout or preset.cps_timeout
        
        # Modes
        if args.mode:
            raw_modes = [m.strip().lower() for m in args.mode.split(",") if m.strip()]
        else:
            raw_modes = list(preset.modes)
    else:
        # Default manual behavior
        backends = [b.strip() for b in args.backends.split(",") if b.strip()] if args.backends and args.backends != "all" else list(BACKEND_ORDER)
        networks = ["tcp", "udp"] if (args.network == "all" or not args.network) else [args.network]
        duration = args.duration or DEFAULT_DURATION
        rounds = args.rounds or 3
        skip_baseline = bool(args.skip_baseline)
        include_jumbo = not args.no_jumbo
        netem_losses = [float(x) for x in (args.netem_losses or "3,5,8").split(",") if x.strip()]
        netem_delay = args.netem_delay if args.netem_delay is not None else 50.0
        weaknet_parallel = args.weaknet_parallel or 8
        weaknet_duration = args.weaknet_duration or duration
        cps_workers = [int(x) for x in (args.cps_workers or "4,8").split(",") if x.strip()]
        cps_connections = args.cps_connections or 5000
        cps_timeout = args.cps_timeout or 120
        raw_modes = [m.strip().lower() for m in (args.mode or "throughput,idle_memory,bufferbloat,stability,cps,weaknet").split(",") if m.strip()]

    # Map legacy aliases to canonical suite names
    alias_map = {
        "wifi": "throughput",
        "usb": "throughput",
        "loopback": "throughput",
        "standard": "throughput",
        "idle": "idle_memory",
        "longrun": "stability",
        "long_run": "stability",
    }
    modes = set()
    for m in raw_modes:
        if m == "all":
            modes.update(["throughput", "idle_memory", "bufferbloat", "stability", "cps", "weaknet"])
        else:
            modes.add(alias_map.get(m, m))

    log_info(f"Target Device Profile: {profile['name']} ({profile['key']})")
    log_info(f"Active Suites: {sorted(modes)}")
    log_info(f"Backends: {backends} | Skip Baseline: {skip_baseline} | Networks: {networks} | Rounds: {rounds}")

    # Auto-detect USB host IP if needed
    usb_server_ip = args.usb_server_ip
    if usb_server_ip == "auto":
        usb_server_ip = auto_detect_usb_ip()

    # Initialize ADB
    try:
        adb = AdbRunner(args.device)
        adb.ensure_iperf3_on_device()
        app_uid = adb.get_app_uid()
        log_info(f"Connected to Device: {adb.device} | App UID: {app_uid}")
        adb.wake_device()
        adb.wakeup_app_from_stopped_state()
    except Exception as e:
        log_error(f"Device initialization failed: {e}")
        sys.exit(1)

    os.makedirs(profile["data_dir"], exist_ok=True)

    # -------------------------------------------------------------
    # 1. Throughput Suite (Wi-Fi, USB, Loopback)
    # -------------------------------------------------------------
    if "throughput" in modes:
        tp_data: Dict[str, Any] = {
            "metadata": {
                "timestamp": datetime.now().isoformat(),
                "duration": duration,
                "wifi_server_ip": args.wifi_server_ip,
                "usb_server_ip": usb_server_ip,
                "device": adb.device,
                "rounds": rounds,
                "backends": backends,
            },
            "rounds": {}
        }
        for round_idx in range(1, rounds + 1):
            round_key = f"round_{round_idx}"
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"            THROUGHPUT TEST: ROUND {round_idx} OF {rounds}")
            print(f"======================================================={Colors.RESET}")
            round_res: List[Dict[str, Any]] = []
            
            # Wi-Fi throughput
            round_res.extend(run_media_suite(
                adb, app_uid, "5GHz Wi-Fi", args.wifi_server_ip,
                backends=backends, networks=networks,
                skip_baseline=skip_baseline, duration=duration,
                include_jumbo=include_jumbo
            ))
            
            # If not in light preset, run USB and Loopback
            if preset and preset.name == "full":
                round_res.extend(run_media_suite(
                    adb, app_uid, "USB 3.2 / 4.0", usb_server_ip,
                    backends=backends, networks=networks,
                    skip_baseline=skip_baseline, duration=duration,
                    include_jumbo=include_jumbo
                ))
                round_res.extend(run_loopback_suite(
                    adb, app_uid, backends=backends,
                    skip_baseline=skip_baseline, duration=duration
                ))

            tp_data["rounds"][round_key] = round_res
            with open(profile["throughput_json"], "w", encoding="utf-8") as f:
                json.dump(tp_data, f, indent=2, ensure_ascii=False)
            log_success(f"Throughput round {round_idx} saved to: {profile['throughput_json']}")

    # -------------------------------------------------------------
    # 2. Idle Memory Retention Suite
    # -------------------------------------------------------------
    if "idle_memory" in modes:
        idle_data: Dict[str, Any] = {
            "metadata": {
                "timestamp": datetime.now().isoformat(),
                "device": adb.device,
                "wifi_server_ip": args.wifi_server_ip,
                "rounds": rounds,
                "backends": backends,
            },
            "rounds": {}
        }
        for round_idx in range(1, rounds + 1):
            round_key = f"round_{round_idx}"
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"          IDLE MEMORY TEST: ROUND {round_idx} OF {rounds}")
            print(f"======================================================={Colors.RESET}")
            idle_res = run_idle_suite(adb, args.wifi_server_ip, backends=backends, networks=networks)
            idle_data["rounds"][round_key] = idle_res
            with open(profile["idle_memory_json"], "w", encoding="utf-8") as f:
                json.dump(idle_data, f, indent=2, ensure_ascii=False)
            log_success(f"Idle memory round {round_idx} saved to: {profile['idle_memory_json']}")

    # -------------------------------------------------------------
    # 3. Bufferbloat & Loaded Latency Suite
    # -------------------------------------------------------------
    if "bufferbloat" in modes:
        bloat_data: Dict[str, Any] = {
            "metadata": {
                "timestamp": datetime.now().isoformat(),
                "device": adb.device,
                "wifi_server_ip": args.wifi_server_ip,
                "rounds": rounds,
                "backends": backends,
            },
            "rounds": {}
        }
        adv_backends = [b for b in TARGET_ORDER if b in backends or (b == "direct_none" and not skip_baseline)]
        for round_idx in range(1, rounds + 1):
            round_key = f"round_{round_idx}"
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"          BUFFERBLOAT TEST: ROUND {round_idx} OF {rounds}")
            print(f"======================================================={Colors.RESET}")
            bloat_res = run_bufferbloat_suite(adb, args.wifi_server_ip, backends=adv_backends, duration=duration)
            bloat_data["rounds"][round_key] = bloat_res
            with open(profile["bufferbloat_json"], "w", encoding="utf-8") as f:
                json.dump(bloat_data, f, indent=2, ensure_ascii=False)
            log_success(f"Bufferbloat round {round_idx} saved to: {profile['bufferbloat_json']}")

    # -------------------------------------------------------------
    # 4. 60-Second Sustained Stability Suite
    # -------------------------------------------------------------
    if "stability" in modes:
        print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
        print(f"          STARTING 60-SECOND SUSTAINED STABILITY TEST")
        print(f"======================================================={Colors.RESET}")
        adv_backends = [b for b in TARGET_ORDER if b in backends or (b == "direct_none" and not skip_baseline)]
        stab_res = run_stability_suite(adb, args.wifi_server_ip, backends=adv_backends, duration=60)
        stab_data = {
            "timestamp": datetime.now().isoformat(),
            "device": adb.device,
            "wifi_server_ip": args.wifi_server_ip,
            "long_run": stab_res,
        }
        with open(profile["stability_json"], "w", encoding="utf-8") as f:
            json.dump(stab_data, f, indent=2, ensure_ascii=False)
        log_success(f"Stability results saved to: {profile['stability_json']}")

    # -------------------------------------------------------------
    # 5. Weak-Network Suite (host netem)
    # -------------------------------------------------------------
    if "weaknet" in modes:
        netem_iface = args.netem_iface
        if args.skip_netem:
            netem_iface = netem_iface or "unused"
        elif not netem_iface:
            device_ip = adb.get_route_source_ip(args.wifi_server_ip)
            route_target = device_ip or args.wifi_server_ip
            try:
                netem_iface = get_route_interface(route_target)
                log_info(f"Auto-detected netem interface: {netem_iface} (route target: {route_target})")
            except NetemError as e:
                log_error(str(e))
                sys.exit(1)

        weaknet_backends = [b for b in TARGET_ORDER if b in backends or (b == "direct_none" and not skip_baseline)]
        weaknet_results: List[Dict[str, Any]] = []

        for loss in netem_losses:
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"      STARTING WEAK-NETWORK TEST loss={loss:g}% delay={netem_delay:g}ms")
            print(f"======================================================={Colors.RESET}")
            try:
                weaknet_results.extend(run_weaknet_suite(
                    adb=adb,
                    app_uid=app_uid,
                    server_ip=args.wifi_server_ip,
                    backends=weaknet_backends,
                    interface=netem_iface,
                    loss_percent=loss,
                    delay_ms=netem_delay,
                    duration=weaknet_duration,
                    parallel=weaknet_parallel,
                    dry_run=args.netem_dry_run,
                    skip_netem=args.skip_netem,
                ))
            except NetemError as e:
                log_error(str(e))
                sys.exit(1)

        weaknet_payload = {
            "timestamp": datetime.now().isoformat(),
            "device": adb.device,
            "device_profile": profile["key"],
            "server_ip": args.wifi_server_ip,
            "interface": netem_iface,
            "delay_ms": netem_delay,
            "loss_percent_list": netem_losses,
            "parallel": weaknet_parallel,
            "duration": weaknet_duration,
            "dry_run": args.netem_dry_run,
            "skip_netem": args.skip_netem,
            "results": weaknet_results,
        }
        with open(profile["weaknet_json"], "w", encoding="utf-8") as f:
            json.dump(weaknet_payload, f, indent=2, ensure_ascii=False)
        log_success(f"Weak-network results saved to: {profile['weaknet_json']}")

    # -------------------------------------------------------------
    # 6. CPS Suite (Short-lived TCP Connections)
    # -------------------------------------------------------------
    if "cps" in modes:
        cps_backends = [b for b in TARGET_ORDER if b in backends or (b == "direct_none" and not skip_baseline)]
        cps_data: Dict[str, Any] = {
            "metadata": {
                "timestamp": datetime.now().isoformat(),
                "device": adb.device,
                "device_profile": profile["key"],
                "server_ip": args.wifi_server_ip,
                "connections": cps_connections,
                "workers": cps_workers,
                "timeout_sec": cps_timeout,
                "rounds": rounds,
            },
            "rounds": {}
        }
        for round_idx in range(1, rounds + 1):
            round_key = f"round_{round_idx}"
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"       CPS TEST: ROUND {round_idx} OF {rounds} (conns={cps_connections})")
            print(f"======================================================={Colors.RESET}")
            try:
                cps_results = run_cps_suite(
                    adb=adb,
                    app_uid=app_uid,
                    server_ip=args.wifi_server_ip,
                    backends=cps_backends,
                    workers_list=cps_workers,
                    connections=cps_connections,
                    timeout_sec=cps_timeout,
                )
                cps_data["rounds"][round_key] = cps_results
            except RuntimeError as e:
                log_error(str(e))
                sys.exit(1)

            with open(profile["cps_json"], "w", encoding="utf-8") as f:
                json.dump(cps_data, f, indent=2, ensure_ascii=False)
            log_success(f"CPS round {round_idx} saved to: {profile['cps_json']}")

    log_success(f"All requested benchmark operations completed successfully! Data stored in {profile['data_dir']}")


if __name__ == "__main__":
    main()
