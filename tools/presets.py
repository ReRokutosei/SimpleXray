"""
SimpleXray Benchmark Presets Contract.

Defines standardized configurations for:
- 'light': Lightweight fast suite focusing on Hev, SingTUN, Zeptun over Wi-Fi MTU 1500.
- 'full': Comprehensive hardware suite covering all physical media, MTU 9000, and all backends.
"""

from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class BenchmarkPreset:
    name: str
    description: str
    backends: List[str]
    modes: List[str]
    networks: List[str]
    duration: int
    rounds: int
    skip_baseline: bool
    include_jumbo: bool
    # Weaknet specifics
    netem_losses: List[float] = field(default_factory=lambda: [3.0, 5.0, 8.0])
    netem_delay: float = 50.0
    weaknet_parallel: int = 8
    weaknet_duration: int = 10
    # CPS specifics
    cps_workers: List[int] = field(default_factory=lambda: [4, 8])
    cps_connections: int = 5000
    cps_timeout: int = 120


PRESETS = {
    "light": BenchmarkPreset(
        name="light",
        description="Focused evaluation: Hev, SingTUN, Zeptun on Wi-Fi MTU 1500 (3 rounds, no baseline)",
        backends=["hev", "sing", "zeptun"],
        modes=["throughput", "idle_memory", "bufferbloat", "stability", "cps", "weaknet"],
        networks=["tcp", "udp"],
        duration=10,
        rounds=3,
        skip_baseline=True,
        include_jumbo=False,
        netem_losses=[3.0, 5.0, 8.0],
        netem_delay=50.0,
        weaknet_parallel=8,
        weaknet_duration=10,
        cps_workers=[4, 8],
        cps_connections=5000,
        cps_timeout=120,
    ),
    "full": BenchmarkPreset(
        name="full",
        description="Comprehensive evaluation: All backends, physical baseline, MTU 1500/9000 across Wi-Fi, USB, Loopback",
        backends=["hev", "sing", "xray", "zeptun"],
        modes=["throughput", "idle_memory", "bufferbloat", "stability", "cps", "weaknet"],
        networks=["tcp", "udp"],
        duration=10,
        rounds=3,
        skip_baseline=False,
        include_jumbo=True,
        netem_losses=[1.0, 3.0, 5.0, 8.0],
        netem_delay=50.0,
        weaknet_parallel=8,
        weaknet_duration=10,
        cps_workers=[1, 4, 8],
        cps_connections=5000,
        cps_timeout=120,
    ),
}


def get_preset(name: Optional[str]) -> Optional[BenchmarkPreset]:
    if not name:
        return None
    key = name.strip().lower()
    if key not in PRESETS:
        valid = ", ".join(PRESETS.keys())
        raise ValueError(f"Unknown preset '{name}'. Valid presets: {valid}")
    return PRESETS[key]
