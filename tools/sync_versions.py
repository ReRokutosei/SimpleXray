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

REQUIRED_KEYS = (
    "XRAY_CORE_VERSION",
    "XRAY_CORE_COMMIT",
    "XRAY_CORE_ZIP_SHA256",
    "HEV_TUN_VERSION",
    "GO_VERSION",
    "NDK_VERSION",
)

PROPERTY_PATTERNS = {
    "XRAY_CORE_VERSION": re.compile(r"^v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$"),
    "XRAY_CORE_COMMIT": re.compile(r"^[0-9a-f]{40}$"),
    "XRAY_CORE_ZIP_SHA256": re.compile(r"^[0-9a-f]{64}$"),
    "HEV_TUN_VERSION": re.compile(r"^\d+\.\d+\.\d+(?: \([0-9a-f]{7,40}\))?$"),
    "GO_VERSION": re.compile(r"^\d+\.\d+\.\d+$"),
    "NDK_VERSION": re.compile(r"^\d+\.\d+\.\d+$"),
}


def version_tuple(value: str) -> tuple:
    return tuple(int(part) for part in value.split("."))


def parse_go_directive(go_mod_path: str) -> str | None:
    if not os.path.exists(go_mod_path):
        return None
    with open(go_mod_path, "r", encoding="utf-8") as f:
        for line in f:
            m = re.match(r"^go\s+(\d+\.\d+(?:\.\d+)?)\s*$", line.strip())
            if m:
                return m.group(1)
    return None


def validate_go_minimum(properties: dict) -> list:
    """Ensure GO_VERSION is not older than the go directives in the repo."""
    errors = []
    pin = properties.get("GO_VERSION", "")
    if not PROPERTY_PATTERNS["GO_VERSION"].match(pin):
        return errors  # format error is reported separately

    for rel_path in ("tools/quic/go.mod",):
        go_mod = os.path.join(REPO_ROOT, rel_path)
        directive = parse_go_directive(go_mod)
        if directive is None:
            continue
        if version_tuple(pin) < version_tuple(directive):
            errors.append(
                f"  GO_VERSION {pin} is older than {rel_path} directive 'go {directive}'."
            )
    return errors


def validate_properties(properties: dict) -> list:
    errors = []

    missing = [key for key in REQUIRED_KEYS if key not in properties or not properties[key]]
    if missing:
        errors.append(f"  Missing or empty keys: {', '.join(missing)}")

    extra = sorted(set(properties.keys()) - set(REQUIRED_KEYS))
    if extra:
        errors.append(f"  Unknown keys: {', '.join(extra)}")

    for key, pattern in PROPERTY_PATTERNS.items():
        value = properties.get(key)
        if value and not pattern.match(value):
            errors.append(f"  Invalid {key} format: '{value}'")

    errors.extend(validate_go_minimum(properties))
    return errors


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


HEV_CONFIG_CONST_PATH = os.path.join(
    REPO_ROOT, "third_party", "hev-socks5-tunnel", "src", "hev-config-const.h"
)


def get_hev_config_version() -> str | None:
    """Read the version constants used by the Hev source tree.

    CI checks out submodules without tags, so ``git describe`` can return a bare
    commit hash.  The checked-out source always contains the canonical
    MAJOR/MINOR/MICRO values, which makes this deterministic for both local and
    shallow CI checkouts.
    """
    if not os.path.exists(HEV_CONFIG_CONST_PATH):
        return None

    values = {}
    with open(HEV_CONFIG_CONST_PATH, "r", encoding="utf-8") as f:
        for line in f:
            m = re.match(
                r"^\s*#define\s+(MAJOR_VERSION|MINOR_VERSION|MICRO_VERSION)\s+\((\d+)\)",
                line,
            )
            if m:
                values[m.group(1)] = m.group(2)

    if {"MAJOR_VERSION", "MINOR_VERSION", "MICRO_VERSION"} <= values.keys():
        return "{}.{}.{}".format(
            values["MAJOR_VERSION"], values["MINOR_VERSION"], values["MICRO_VERSION"]
        )
    return None


def get_hev_version() -> str:
    hev_dir = os.path.join(REPO_ROOT, "third_party", "hev-socks5-tunnel")
    if not os.path.exists(hev_dir):
        return "unknown"

    tag = None
    try:
        desc = subprocess.check_output(
            ["git", "-C", hev_dir, "describe", "--tags", "--always"],
            stderr=subprocess.DEVNULL,
            text=True
        ).strip()
        # If tag has -g<commit>, extract base tag.
        tag_match = re.match(r"^([0-9]+\.[0-9]+\.[0-9]+)", desc)
        if tag_match:
            tag = tag_match.group(1)
    except Exception:
        pass

    if tag is None:
        tag = get_hev_config_version()
    if tag is None:
        return "unknown"

    try:
        commit = subprocess.check_output(
            ["git", "-C", hev_dir, "rev-parse", "--short", "HEAD"],
            stderr=subprocess.DEVNULL,
            text=True
        ).strip()
        if commit:
            return f"{tag} ({commit})"
    except Exception:
        pass
    return tag


def sync_versions(check_only: bool = False) -> bool:
    current = read_current_properties()

    validation_errors = validate_properties(current)
    if validation_errors:
        print("version.properties is invalid:")
        for error in validation_errors:
            print(error)
        return False

    expected = {
        "XRAY_CORE_VERSION": current.get("XRAY_CORE_VERSION", "v26.9.30"),
        "XRAY_CORE_COMMIT": current.get("XRAY_CORE_COMMIT", ""),
        "XRAY_CORE_ZIP_SHA256": current.get("XRAY_CORE_ZIP_SHA256", ""),
        "HEV_TUN_VERSION": get_hev_version(),
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
