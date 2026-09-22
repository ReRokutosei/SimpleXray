"""
Matplotlib themes, unified backend color palettes, JetBrains Mono font loading, and figure layout helpers.
"""

import os
from typing import Any, Dict, List, Optional, Tuple

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm

# Unified Backend Palette (Scientific Modern Light Theme)
PALETTE = {
    'direct_none': {
        'name': 'No VPN (Physical Baseline)',
        'fill': '#94a3b8',   # Slate 400
        'edge': '#64748b',   # Slate 500
        'light': '#f1f5f9',
        'so_size_mb': 0.0,
        'runtime': 'Linux Kernel Netstack',
        'ipc': 'Direct Socket'
    },
    'hev': {
        'name': 'Hev (C/lwIP)',
        'fill': '#0d9488',   # Teal 600
        'edge': '#0f766e',   # Teal 700
        'light': '#ccfbf1',  # Teal 100
        'so_size_mb': 0.34,  # 346 KB
        'runtime': 'C (Single-threaded lwIP)',
        'ipc': 'Local SOCKS5 Inbound'
    },
    'sing': {
        'name': 'SingTUN (Go/sing-box)',
        'fill': '#2563eb',   # Blue 600
        'edge': '#1d4ed8',   # Blue 700
        'light': '#dbeafe',  # Blue 100
        'so_size_mb': 6.30,  # 6.3 MB
        'runtime': 'Go 1.26 (pure user-space sing-tun)',
        'ipc': 'Local SOCKS5 Inbound'
    },
    'xray': {
        'name': 'Xray TUN (gVisor)',
        'fill': '#e11d48',   # Rose 600
        'edge': '#be123c',   # Rose 700
        'light': '#ffe4e6',  # Rose 100
        'so_size_mb': 34.00, # 34 MB
        'runtime': 'Go / gVisor Netstack',
        'ipc': 'JNI Fork Child Process (FD Injected)'
    },
    'zeptun': {
        'name': 'Zeptun (Zig/userspace)',
        'fill': '#d97706',
        'edge': '#b45309',
        'light': '#fef3c7',
        'so_size_mb': 0.26,
        'runtime': 'Zig userspace stack',
        'ipc': 'Local SOCKS5 Inbound'
    }
}

BACKEND_ORDER = ['hev', 'sing', 'xray', 'zeptun']
TARGET_ORDER = ['direct_none', 'hev', 'sing', 'xray', 'zeptun']

# Font Discovery
FONT_DIRS = [
    "/home/vanitas/.local/share/fonts/JetBrains",
    os.path.expanduser("~/.local/share/fonts/JetBrains"),
    os.path.expanduser("~/.fonts"),
]


_PROP_REGULAR: Optional[fm.FontProperties] = None
_PROP_BOLD: Optional[fm.FontProperties] = None
_PROP_MEDIUM: Optional[fm.FontProperties] = None


def setup_fonts() -> Tuple[Optional[fm.FontProperties], Optional[fm.FontProperties], Optional[fm.FontProperties]]:
    """Registers JetBrains Mono font files and returns (regular, bold, medium) font properties."""
    global _PROP_REGULAR, _PROP_BOLD, _PROP_MEDIUM
    prop_regular = None
    prop_bold = None
    prop_medium = None

    for d in FONT_DIRS:
        if not os.path.exists(d):
            continue
        p_reg = os.path.join(d, "JetBrainsMonoNerdFont-Regular.ttf")
        p_bld = os.path.join(d, "JetBrainsMonoNerdFont-Bold.ttf")
        p_med = os.path.join(d, "JetBrainsMonoNerdFont-Medium.ttf")

        for p, target in [(p_reg, 'regular'), (p_bld, 'bold'), (p_med, 'medium')]:
            if os.path.exists(p):
                try:
                    fm.fontManager.addfont(p)
                    fprop = fm.FontProperties(fname=p)
                    if target == 'regular' and not prop_regular:
                        prop_regular = fprop
                    elif target == 'bold' and not prop_bold:
                        prop_bold = fprop
                    elif target == 'medium' and not prop_medium:
                        prop_medium = fprop
                except Exception:
                    pass

    if prop_regular:
        plt.rcParams['font.sans-serif'] = [prop_regular.get_name(), 'DejaVu Sans', 'Arial', 'sans-serif']
        plt.rcParams['font.family'] = 'sans-serif'

    _PROP_REGULAR = prop_regular
    _PROP_BOLD = prop_bold
    _PROP_MEDIUM = prop_medium

    return prop_regular, prop_bold, prop_medium


def apply_global_theme():
    """Applies standardized light theme defaults to matplotlib rcParams."""
    plt.rcParams['figure.facecolor'] = '#f8fafc'
    plt.rcParams['axes.facecolor'] = '#ffffff'
    plt.rcParams['axes.edgecolor'] = '#cbd5e1'
    plt.rcParams['axes.labelcolor'] = '#334155'
    plt.rcParams['xtick.color'] = '#475569'
    plt.rcParams['ytick.color'] = '#0f172a'
    plt.rcParams['grid.color'] = '#f1f5f9'
    plt.rcParams['grid.alpha'] = 1.0
    plt.rcParams['grid.linestyle'] = '-'


def add_dashboard_header(
    fig: plt.Figure,
    title: str,
    subtitle: str,
    prop_bold: Optional[fm.FontProperties] = None,
    prop_regular: Optional[fm.FontProperties] = None,
    y_title: Optional[float] = None,
    y_subtitle: Optional[float] = None
):
    """
    Draws standardized dashboard title and subtitle on figure.
    Uses physical-dimension adaptive placement (inches from top) to prevent overlap on any canvas height.
    """
    fig_h = fig.get_figheight()
    if y_title is None:
        y_title = (fig_h - 0.28) / fig_h
    if y_subtitle is None:
        y_subtitle = (fig_h - 0.58) / fig_h

    p_bld = prop_bold or _PROP_BOLD
    p_reg = prop_regular or _PROP_REGULAR

    title_fontsize = 18 if fig_h > 15 else 16
    sub_fontsize = 10.5 if fig_h > 15 else 9.5

    fig.text(
        0.5, y_title, title,
        fontsize=title_fontsize, fontweight='bold', color='#0f172a',
        fontproperties=p_bld, ha='center', va='top'
    )
    fig.text(
        0.5, y_subtitle, subtitle,
        fontsize=sub_fontsize, color='#475569',
        fontproperties=p_reg, ha='center', va='top'
    )


def create_top_legend(
    fig: plt.Figure,
    handles: List[Any],
    labels: List[str],
    y_pos: Optional[float] = None,
    ncol: Optional[int] = None,
    prop_medium: Optional[fm.FontProperties] = None
):
    """
    Draws standardized top-centered legend card.
    Uses physical-dimension adaptive placement (inches from top) to sit cleanly between subtitle and subplots.
    """
    fig_h = fig.get_figheight()
    if y_pos is None:
        y_pos = (fig_h - 0.98) / fig_h

    if ncol is None:
        ncol = len(labels)

    p_med = prop_medium or _PROP_MEDIUM

    fig.legend(
        handles=handles,
        labels=labels,
        loc='upper center',
        bbox_to_anchor=(0.5, y_pos),
        ncol=ncol,
        frameon=True,
        facecolor='#ffffff',
        edgecolor='#cbd5e1',
        fontsize=9.2,
        prop=p_med
    )


def save_dashboard(fig: plt.Figure, output_path: str, dpi: int = 200):
    """Saves figure cleanly with appropriate lossless options and closes figure."""
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    kwargs = {}
    if output_path.lower().endswith(".webp"):
        kwargs["pil_kwargs"] = {'lossless': True}

    fig.savefig(
        output_path,
        dpi=dpi,
        facecolor='#f8fafc',
        edgecolor='none',
        **kwargs
    )
    plt.close(fig)
    print(f"[Dashboard] Saved: {output_path}")
