#!/usr/bin/env python3
"""
SimpleXray Advanced Benchmark (Backward Compatibility Wrapper)
Redirects to the unified benchmark suite (tools/benchmark.py).
"""

import argparse
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)

from benchmark import main as benchmark_main


def main():
    parser = argparse.ArgumentParser(description="Advanced SimpleXray Benchmark (Bufferbloat & Stability Wrapper)")
    parser.add_argument("--wifi-server-ip", default="192.168.31.236", help="Host PC IP address in Wi-Fi subnet")
    parser.add_argument("--device", default=None, help="ADB device identifier")
    parser.add_argument("--test", default="all", choices=["all", "bufferbloat", "longrun"], help="Which test to run")
    parser.add_argument("--output-dir", default=None, help="Directory to save charts (unused wrapper)")
    parser.add_argument("--output-json", default=None, help="Custom output JSON path (optional)")
    args, unknown = parser.parse_known_args()

    mode_map = {
        "all": "bufferbloat,longrun",
        "bufferbloat": "bufferbloat",
        "longrun": "longrun"
    }
    mode = mode_map[args.test]

    forward_args = [
        "benchmark.py",
        "--mode", mode,
        "--wifi-server-ip", args.wifi_server_ip
    ]
    if args.device:
        forward_args.extend(["--device", args.device])
    if args.output_json:
        forward_args.extend(["--advanced-json", args.output_json])
    forward_args.extend(unknown)

    sys.argv = forward_args
    benchmark_main()


if __name__ == "__main__":
    main()
