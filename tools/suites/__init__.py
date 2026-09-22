"""
SimpleXray Benchmark Suites
"""

from .standard import (
    run_throughput_case,
    run_loopback_case,
    run_media_suite,
    run_loopback_suite,
)
from .idle import (
    run_idle_flow_case,
    run_idle_suite,
)
from .bufferbloat import (
    run_bufferbloat_case,
    run_bufferbloat_suite,
    render_bufferbloat_chart,
)
from .long_run import (
    run_long_run_case,
    run_long_run_suite,
    render_long_run_chart,
)
from .weaknet import (
    run_weaknet_download_case,
    run_weaknet_suite,
    render_weaknet_chart,
)
from .cps import (
    run_cps_case,
    run_cps_suite,
    render_cps_chart,
)

__all__ = [
    "run_throughput_case",
    "run_loopback_case",
    "run_media_suite",
    "run_loopback_suite",
    "run_idle_flow_case",
    "run_idle_suite",
    "run_bufferbloat_case",
    "run_bufferbloat_suite",
    "render_bufferbloat_chart",
    "run_long_run_case",
    "run_long_run_suite",
    "render_long_run_chart",
    "run_weaknet_download_case",
    "run_weaknet_suite",
    "render_weaknet_chart",
    "run_cps_case",
    "run_cps_suite",
    "render_cps_chart",
]
