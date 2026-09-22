#!/usr/bin/env python3
"""
SimpleXray Version Synchronizer
Extracts and synchronizes component versions and commit hashes
from submodules and go.mod files into version.properties.
"""

import os
import re
import subprocess
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VERSION_PROPS_PATH = os.path.join(REPO_ROOT, "version.properties")


def read_current_properties() -> dict:
    props = {}
    if os.path.exists(VERSION_PROPS_PATH):
        with open(VERSION_PROPS_PATH, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    props[k.strip()] = v.strip()
    return props


def get_hev_version() -> str:
    hev_dir = os.path.join(REPO_ROOT, "third_party", "hev-socks5-tunnel")
    if not os.path.exists(hev_dir):
        return "unknown"
    try:
        desc = subprocess.check_output(
            ["git", "-C", hev_dir, "describe", "--tags", "--always"],
            stderr=subprocess.DEVNULL,
            text=True
        ).strip()
        commit = subprocess.check_output(
            ["git", "-C", hev_dir, "rev-parse", "--short", "HEAD"],
            stderr=subprocess.DEVNULL,
            text=True
        ).strip()
        # If tag has -g<commit>, extract base tag
        tag_match = re.match(r"^([0-9\.]+)", desc)
        tag = tag_match.group(1) if tag_match else desc
        return f"{tag} ({commit})"
    except Exception:
        return "2.17.1 (b514150)"


def get_zeptun_version() -> str:
    """Return the checked-out Zeptun tag and short commit."""
    zeptun_dir = os.path.join(REPO_ROOT, "third_party", "zeptun")
    if not os.path.exists(zeptun_dir):
        return "unknown"
    try:
        desc = subprocess.check_output(
            ["git", "-C", zeptun_dir, "describe", "--tags", "--always"],
            stderr=subprocess.DEVNULL,
            text=True,
        ).strip()
        commit = subprocess.check_output(
            ["git", "-C", zeptun_dir, "rev-parse", "--short", "HEAD"],
            stderr=subprocess.DEVNULL,
            text=True,
        ).strip()
        return f"{desc} ({commit})"
    except Exception:
        return "unknown"


def get_sing_tun_version() -> str:
    go_mod = os.path.join(REPO_ROOT, "third_party", "sing-tun", "go.mod")
    if not os.path.exists(go_mod):
        return "unknown"
    try:
        with open(go_mod, "r", encoding="utf-8") as f:
            content = f.read()
        # Find github.com/sagernet/sing-tun v...-<hash>
        m = re.search(r"github\.com/sagernet/sing-tun\s+v[0-9\.\-]+-([0-9a-fA-F]+)", content)
        if m:
            return m.group(1)[:12]
        # Or standard tag v0.9.x
        m_tag = re.search(r"github\.com/sagernet/sing-tun\s+(v[0-9\.]+)", content)
        if m_tag:
            return m_tag.group(1)
    except Exception:
        pass
    return "unknown"


def get_mips_tun_version() -> str:
    go_mod = os.path.join(REPO_ROOT, "third_party", "mips-tun", "go.mod")
    if not os.path.exists(go_mod):
        return "unknown"
    try:
        with open(go_mod, "r", encoding="utf-8") as f:
            content = f.read()
        # Find github.com/metacubex/mipstack v...-<hash>
        m = re.search(r"github\.com/metacubex/mipstack\s+v[0-9\.\-]+-([0-9a-fA-F]+)", content)
        if m:
            return m.group(1)[:12]
        m_tag = re.search(r"github\.com/metacubex/mipstack\s+(v[0-9\.]+)", content)
        if m_tag:
            return m_tag.group(1)
    except Exception:
        pass
    return "unknown"


def sync_versions(check_only: bool = False) -> bool:
    current = read_current_properties()
    
    expected = {
        "XRAY_CORE_VERSION": current.get("XRAY_CORE_VERSION", "v26.9.9"),
        "HEV_TUN_VERSION": get_hev_version(),
        "ZEPTUN_VERSION": get_zeptun_version(),
        "SING_TUN_VERSION": get_sing_tun_version(),
        "MIPS_TUN_VERSION": get_mips_tun_version(),
        "GO_VERSION": current.get("GO_VERSION", "1.27.1"),
        "NDK_VERSION": current.get("NDK_VERSION", "28.2.13676358"),
    }
    
    is_synced = True
    diffs = []
    for k, v in expected.items():
        curr_v = current.get(k)
        if curr_v != v:
            is_synced = False
            diffs.append(f"  {k}: '{curr_v}' -> '{v}'")

    if check_only:
        if not is_synced:
            print("version.properties is out of sync:")
            for d in diffs:
                print(d)
            return False
        print("version.properties is up to date.")
        return True

    if not is_synced or set(current.keys()) != set(expected.keys()):
        print("Updating version.properties with live component versions:")
        for d in diffs:
            print(d)
        with open(VERSION_PROPS_PATH, "w", encoding="utf-8") as f:
            for k, v in expected.items():
                f.write(f"{k}={v}\n")
        print(f"Successfully synchronized {VERSION_PROPS_PATH}")
    else:
        print("version.properties is already up to date.")
    return True


if __name__ == "__main__":
    check_mode = "--check" in sys.argv
    success = sync_versions(check_only=check_mode)
    sys.exit(0 if success else 1)
