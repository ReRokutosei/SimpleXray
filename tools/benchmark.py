#!/usr/bin/env python3
"""
SimpleXray Unified Benchmark Master Runner
Integrates Standard Throughput Suites (Wi-Fi, USB, Loopback, Idle Flows)
and Advanced Suites (Bufferbloat, 60s Long-Run Stability).
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
from suites import (
    run_media_suite,
    run_loopback_suite,
    run_idle_suite,
    run_bufferbloat_suite,
    render_bufferbloat_chart,
    run_long_run_suite,
    render_long_run_chart,
    run_weaknet_suite,
    render_weaknet_chart,
    run_cps_suite,
    render_cps_chart,
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


def auto_detect_usb_ip() -> str:
    rc, out, _ = run_cmd(["ip", "-brief", "address"])
    for line in out.splitlines():
        parts = line.split()
        if len(parts) >= 3 and any(parts[0].startswith(prefix) for prefix in ["enx", "rndis", "usb"]):
            return parts[2].split("/")[0]
    return "192.168.232.59"


def main():
    parser = argparse.ArgumentParser(description="SimpleXray Unified TUN Benchmark Suite")
    parser.add_argument(
        "--mode",
        default="wifi,loopback",
        help="Suites to run: wifi, usb, loopback, idle, bufferbloat, longrun, weaknet, cps, standard, advanced, all (comma-separated)"
    )
    parser.add_argument("--network", default="all", choices=["tcp", "udp", "all"],
                        help="Network protocols to benchmark ('tcp', 'udp', or 'all', default: all)")
    parser.add_argument("--with-idle", action="store_true",
                        help="Include 0-1000 connection retention idle flows memory test")
    parser.add_argument("--backends", default="all",
                        help="TUN backends to benchmark (comma-separated: hev,xray,sing,zeptun, or 'all')")
    parser.add_argument("--skip-baseline", action="store_true",
                        help="Skip running physical baseline (No VPN) tests")
    parser.add_argument("--wifi-server-ip", default="192.168.31.236",
                        help="Host PC IP in Wi-Fi subnet (default: 192.168.31.236)")
    parser.add_argument("--usb-server-ip", default="auto",
                        help="Host PC IP in USB tethering subnet (default: auto)")
    parser.add_argument("--duration", type=int, default=DEFAULT_DURATION,
                        help="Duration in seconds per throughput test direction (default: 10)")
    parser.add_argument("--device", default=None,
                        help="ADB device serial if multiple devices are connected")
    parser.add_argument("--device-profile", default=DEFAULT_DEVICE, choices=sorted(DEVICE_PROFILES),
                        help=f"Device profile used for default output paths (default: {DEFAULT_DEVICE})")
    parser.add_argument("--rounds", type=int, default=3,
                        help="Number of test rounds to execute (default: 3)")
    parser.add_argument("--no-jumbo", action="store_true",
                        help="Skip MTU 9000 throughput cases")
    parser.add_argument("--merge", action="store_true",
                        help="Merge new benchmark results into existing JSON file")
    parser.add_argument("--output-json", default=None,
                        help="File path to save standard benchmark JSON results (defaults to profile data dir)")
    parser.add_argument("--output-md", default=None,
                        help="File path to save standard Markdown summary tables (defaults to profile data dir)")
    parser.add_argument("--advanced-json", default=None,
                        help="File path to save advanced benchmark results (defaults to profile data dir)")
    parser.add_argument("--netem-iface", default=None,
                        help="Host interface for netem; auto-resolved from --wifi-server-ip when omitted")
    parser.add_argument("--netem-losses", default="3,5,8",
                        help="Comma-separated loss percentages for weaknet mode (default: 1,3)")
    parser.add_argument("--netem-delay", type=float, default=50.0,
                        help="Netem delay in milliseconds (default: 50)")
    parser.add_argument("--weaknet-parallel", type=int, default=8,
                        help="Parallel streams for weaknet download tests (default: 8)")
    parser.add_argument("--weaknet-duration", type=int, default=None,
                        help="Weaknet download duration in seconds (defaults to --duration)")
    parser.add_argument("--netem-dry-run", action="store_true",
                        help="Print netem commands without applying them")
    parser.add_argument("--skip-netem", action="store_true",
                        help="Run weaknet traffic without applying a netem qdisc")
    parser.add_argument("--cps-workers", default="1,4,8",
                        help="Comma-separated worker counts for CPS mode (default: 1,4,8)")
    parser.add_argument("--cps-connections", type=int, default=5000,
                        help="Total short-lived connections per CPS case (default: 5000)")
    parser.add_argument("--cps-timeout", type=int, default=120,
                        help="Per-CPS-case timeout in seconds (default: 120)")
    parser.add_argument("--cps-json", default=None,
                        help="Optional CPS JSON output path (defaults to profile cps_results.json)")
    parser.add_argument("--no-charts", action="store_true",
                        help="Skip generating visualization charts in the profile chart directory")
    args = parser.parse_args()

    profile = resolve_device_paths(args.device_profile)
    args.output_json = args.output_json or profile["bench_json"]
    args.output_md = args.output_md or profile["bench_summary"]
    args.advanced_json = args.advanced_json or profile["advanced_json"]
    weaknet_json = profile["weaknet_json"]
    cps_json = args.cps_json or profile["cps_json"]
    chart_dir = profile["charts_dir"]

    # Parse modes
    raw_modes = [m.strip().lower() for m in args.mode.split(",") if m.strip()]
    modes = set()
    for m in raw_modes:
        if m == "all":
            modes.update(["wifi", "usb", "loopback", "idle", "bufferbloat", "longrun"])
        elif m == "standard":
            modes.update(["wifi", "usb", "loopback"])
        elif m == "advanced":
            modes.update(["bufferbloat", "longrun"])
        else:
            modes.add(m)
    if args.with_idle:
        modes.add("idle")

    # Parse backends
    if args.backends == "all":
        backends = list(BACKEND_ORDER)
    else:
        backends = [b.strip() for b in args.backends.split(",") if b.strip()]

    # Parse protocols
    networks = ["tcp", "udp"] if args.network == "all" else [args.network]

    # Auto-detect USB host IP if needed
    usb_server_ip = args.usb_server_ip
    if usb_server_ip == "auto":
        usb_server_ip = auto_detect_usb_ip()
        log_info(f"Auto-detected USB host IP: {usb_server_ip}")

    # Initialize ADB
    try:
        adb = AdbRunner(args.device)
        adb.ensure_iperf3_on_device()
        app_uid = adb.get_app_uid()
        log_info(f"Connected to Device: {adb.device} | App UID: {app_uid}")
        adb.wake_device()
        adb.wakeup_app_from_stopped_state()  # Ensure app is not in stopped state before any test
    except Exception as e:
        log_error(f"Device initialization failed: {e}")
        sys.exit(1)

    has_standard = any(m in modes for m in ["wifi", "usb", "loopback", "idle"])
    has_advanced = any(m in modes for m in ["bufferbloat", "longrun"])
    has_weaknet = "weaknet" in modes
    has_cps = "cps" in modes

    # -------------------------------------------------------------
    # 1. Standard Benchmark Suites
    # -------------------------------------------------------------
    if has_standard:
        all_rounds_data: Dict[str, Any] = {
            "metadata": {
                "timestamp": datetime.now().isoformat(),
                "mode": list(modes),
                "duration": args.duration,
                "wifi_server_ip": args.wifi_server_ip,
                "usb_server_ip": usb_server_ip,
                "device": adb.device,
                "rounds": args.rounds
            },
            "rounds": {}
        }
        all_md_reports: List[str] = []

        for round_idx in range(1, args.rounds + 1):
            round_key = f"round_{round_idx}"
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"               STARTING TEST ROUND {round_idx} OF {args.rounds}")
            print(f"======================================================={Colors.RESET}")

            round_results: List[Dict[str, Any]] = []

            if "wifi" in modes:
                round_results.extend(run_media_suite(
                    adb, app_uid, "5GHz Wi-Fi", args.wifi_server_ip,
                    backends=backends, networks=networks,
                    skip_baseline=args.skip_baseline, duration=args.duration,
                    include_jumbo=not args.no_jumbo
                ))

            if "usb" in modes:
                round_results.extend(run_media_suite(
                    adb, app_uid, "USB 3.2 / 4.0", usb_server_ip,
                    backends=backends, networks=networks,
                    skip_baseline=args.skip_baseline, duration=args.duration,
                    include_jumbo=not args.no_jumbo
                ))

            if "loopback" in modes:
                round_results.extend(run_loopback_suite(
                    adb, app_uid, backends=backends,
                    skip_baseline=args.skip_baseline, duration=args.duration
                ))

            if "idle" in modes:
                round_results.extend(run_idle_suite(
                    adb, args.wifi_server_ip, backends=backends, networks=networks
                ))

            all_rounds_data["rounds"][round_key] = round_results
            all_md_reports.append(format_markdown_table(round_results, title=f"Round {round_idx} Results"))

            # Save incrementally after each round
            json_path = os.path.abspath(args.output_json)
            os.makedirs(os.path.dirname(json_path), exist_ok=True)
            merged_data = copy.deepcopy(all_rounds_data)
            if args.merge and os.path.exists(json_path):
                try:
                    with open(json_path, "r", encoding="utf-8") as f:
                        existing = json.load(f)
                    for r_k, r_items in merged_data["rounds"].items():
                        existing.setdefault("rounds", {})[r_k] = r_items
                    merged_data = existing
                except Exception as e:
                    log_warn(f"Failed to merge incremental JSON: {e}")

            with open(json_path, "w", encoding="utf-8") as f:
                json.dump(merged_data, f, indent=2, ensure_ascii=False)

            with open(os.path.abspath(args.output_md), "w", encoding="utf-8") as f:
                f.write("\n\n---\n\n".join(all_md_reports))

            log_success(f"Round {round_idx} results saved to {json_path}")

    # -------------------------------------------------------------
    # 2. Advanced Benchmark Suites (Bufferbloat & 60s Stability)
    # -------------------------------------------------------------
    if has_advanced:
        adv_json_path = os.path.abspath(args.advanced_json)
        adv_data: Dict[str, Any] = {
            "timestamp": datetime.now().isoformat(),
            "wifi_server_ip": args.wifi_server_ip,
            "bufferbloat": [],
            "long_run": []
        }
        if os.path.exists(adv_json_path):
            try:
                with open(adv_json_path, "r", encoding="utf-8") as f:
                    existing_adv = json.load(f)
                    if isinstance(existing_adv, dict):
                        adv_data.update(existing_adv)
            except Exception:
                pass

        adv_backends = [b for b in TARGET_ORDER if b in backends or (b == "direct_none" and not args.skip_baseline)]

        if "bufferbloat" in modes:
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"      STARTING BUFFERBLOAT & LOADED LATENCY TEST       ")
            print(f"======================================================={Colors.RESET}")
            bloat_res = run_bufferbloat_suite(adb, args.wifi_server_ip, backends=adv_backends, duration=10)
            adv_data["bufferbloat"] = bloat_res

            if not args.no_charts:
                chart_path = os.path.join(chart_dir, "bufferbloat_dashboard.webp")
                render_bufferbloat_chart(bloat_res, chart_path)

        if "longrun" in modes:
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"     STARTING 60-SECOND LONG-RUN STABILITY TEST        ")
            print(f"======================================================={Colors.RESET}")
            long_res = run_long_run_suite(adb, args.wifi_server_ip, backends=adv_backends, duration=60)
            adv_data["long_run"] = long_res

            if not args.no_charts:
                chart_path = os.path.join(chart_dir, "long_run_stability_dashboard.webp")
                render_long_run_chart(long_res, chart_path)

        os.makedirs(os.path.dirname(adv_json_path), exist_ok=True)
        with open(adv_json_path, "w", encoding="utf-8") as f:
            json.dump(adv_data, f, indent=2, ensure_ascii=False)
        log_success(f"Advanced benchmark results saved to: {adv_json_path}")

    # -------------------------------------------------------------
    # 3. Weak-Network Suite (host netem)
    # -------------------------------------------------------------
    if has_weaknet:
        try:
            losses = [float(x) for x in args.netem_losses.split(",") if x.strip()]
        except ValueError:
            log_error(f"Invalid --netem-losses value: {args.netem_losses}")
            sys.exit(1)

        netem_iface = args.netem_iface
        if args.skip_netem:
            netem_iface = netem_iface or "unused"
        elif not netem_iface:
            device_ip = adb.get_route_source_ip(args.wifi_server_ip)
            route_target = device_ip or args.wifi_server_ip
            try:
                netem_iface = get_route_interface(route_target)
                log_info(
                    f"Auto-detected netem interface: {netem_iface} "
                    f"(route target: {route_target})"
                )
                if netem_iface == "lo":
                    log_warn(
                        "Resolved netem interface is loopback; "
                        "pass --netem-iface explicitly if this is not intended."
                    )
            except NetemError as e:
                log_error(str(e))
                sys.exit(1)

        weaknet_duration = args.weaknet_duration or args.duration
        weaknet_backends = [b for b in TARGET_ORDER if b in backends or (b == "direct_none" and not args.skip_baseline)]
        weaknet_results: List[Dict[str, Any]] = []

        for loss in losses:
            print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
            print(f"      STARTING WEAK-NETWORK TEST loss={loss:g}% delay={args.netem_delay:g}ms")
            print(f"======================================================={Colors.RESET}")
            try:
                weaknet_results.extend(run_weaknet_suite(
                    adb=adb,
                    app_uid=app_uid,
                    server_ip=args.wifi_server_ip,
                    backends=weaknet_backends,
                    interface=netem_iface,
                    loss_percent=loss,
                    delay_ms=args.netem_delay,
                    duration=weaknet_duration,
                    parallel=args.weaknet_parallel,
                    dry_run=args.netem_dry_run,
                    skip_netem=args.skip_netem,
                ))
            except NetemError as e:
                log_error(str(e))
                sys.exit(1)

        weaknet_payload = {
            "timestamp": datetime.now().isoformat(),
            "device": adb.device,
            "device_profile": args.device_profile,
            "server_ip": args.wifi_server_ip,
            "interface": netem_iface,
            "delay_ms": args.netem_delay,
            "loss_percent_list": losses,
            "parallel": args.weaknet_parallel,
            "duration": weaknet_duration,
            "dry_run": args.netem_dry_run,
            "skip_netem": args.skip_netem,
            "results": weaknet_results,
        }
        os.makedirs(os.path.dirname(os.path.abspath(weaknet_json)), exist_ok=True)
        with open(os.path.abspath(weaknet_json), "w", encoding="utf-8") as f:
            json.dump(weaknet_payload, f, indent=2, ensure_ascii=False)
        if not args.no_charts:
            render_weaknet_chart(weaknet_results, os.path.join(chart_dir, "weaknet_throughput.webp"))
        log_success(f"Weak-network results saved to: {weaknet_json}")

    # -------------------------------------------------------------
    # 4. CPS Suite (short-lived TCP connections)
    # -------------------------------------------------------------
    if has_cps:
        try:
            cps_workers = [int(x) for x in args.cps_workers.split(",") if x.strip()]
        except ValueError:
            log_error(f"Invalid --cps-workers value: {args.cps_workers}")
            sys.exit(1)

        cps_backends = [b for b in TARGET_ORDER if b in backends or (b == "direct_none" and not args.skip_baseline)]
        print(f"\n{Colors.CYAN}{Colors.BOLD}=======================================================")
        print(f"      STARTING CPS TEST connections={args.cps_connections} workers={cps_workers}")
        print(f"======================================================={Colors.RESET}")
        try:
            cps_results = run_cps_suite(
                adb=adb,
                app_uid=app_uid,
                server_ip=args.wifi_server_ip,
                backends=cps_backends,
                workers_list=cps_workers,
                connections=args.cps_connections,
                timeout_sec=args.cps_timeout,
            )
        except RuntimeError as e:
            log_error(str(e))
            sys.exit(1)

        cps_payload = {
            "timestamp": datetime.now().isoformat(),
            "device": adb.device,
            "device_profile": args.device_profile,
            "server_ip": args.wifi_server_ip,
            "connections": args.cps_connections,
            "workers": cps_workers,
            "timeout_sec": args.cps_timeout,
            "results": cps_results,
        }
        os.makedirs(os.path.dirname(os.path.abspath(cps_json)), exist_ok=True)
        with open(os.path.abspath(cps_json), "w", encoding="utf-8") as f:
            json.dump(cps_payload, f, indent=2, ensure_ascii=False)
        if not args.no_charts and args.cps_json is None:
            render_cps_chart(cps_results, os.path.join(chart_dir, "cps_connection_rate.webp"))
        log_success(f"CPS results saved to: {cps_json}")

    log_success("All requested benchmark operations completed successfully!")


if __name__ == "__main__":
    main()
