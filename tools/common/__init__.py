"""
SimpleXray Benchmark Common Library
"""

from .logging import Colors, log_info, log_success, log_warn, log_error, run_cmd
from .adb import AdbRunner, CpuProfiler, APP_PKG
from .iperf import start_host_server, kill_host_server, parse_iperf_json, build_client_args
from .dataset import (
    load_datasets,
    compute_clean_averages,
    get_rec,
    load_version_properties,
    DEFAULT_BENCH_JSON,
    DEFAULT_MICRO_JSON,
    DEFAULT_ADVANCED_JSON,
    VERSION_PROPS_PATH,
)
from .theme import (
    PALETTE,
    TARGET_ORDER,
    BACKEND_ORDER,
    setup_fonts,
    apply_global_theme,
    add_dashboard_header,
    create_top_legend,
)

__all__ = [
    "Colors",
    "log_info",
    "log_success",
    "log_warn",
    "log_error",
    "run_cmd",
    "AdbRunner",
    "CpuProfiler",
    "APP_PKG",
    "start_host_server",
    "kill_host_server",
    "parse_iperf_json",
    "build_client_args",
    "load_datasets",
    "compute_clean_averages",
    "get_rec",
    "load_version_properties",
    "DEFAULT_BENCH_JSON",
    "DEFAULT_MICRO_JSON",
    "DEFAULT_ADVANCED_JSON",
    "VERSION_PROPS_PATH",
    "PALETTE",
    "TARGET_ORDER",
    "BACKEND_ORDER",
    "setup_fonts",
    "apply_global_theme",
    "add_dashboard_header",
    "create_top_legend",
]
