#!/usr/bin/env python3
import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DOCS_DIR = REPO_ROOT / "docs"
JSON_PATH = DOCS_DIR / "aboutlibraries.json"
OUTPUT_MD = DOCS_DIR / "third_party_licenses.md"

sys.path.insert(0, str(REPO_ROOT))
from tools.common.dataset import load_version_properties


def run_gradle_export():
    print("Running Gradle to export library definitions...")
    gradlew = REPO_ROOT / "gradlew"
    cmd = [str(gradlew), ":app:exportLibraryDefinitions", "-PexportLicenses"]
    result = subprocess.run(cmd, cwd=REPO_ROOT)
    if result.returncode != 0:
        print(f"Error: Gradle export failed with exit code {result.returncode}", file=sys.stderr)
        sys.exit(result.returncode)


def read_json_file(file_path):
    with open(file_path, "r", encoding="utf-8") as file:
        return json.load(file)


def format_license_text(licenses_raw, licenses_dict):
    license_names = []
    for item in licenses_raw:
        if isinstance(item, dict):
            name = item.get("name") or item.get("spdxId") or ""
            if name:
                license_names.append(name)
        elif isinstance(item, str):
            if item in licenses_dict:
                name = licenses_dict[item].get("name") or licenses_dict[item].get("spdxId") or item
                license_names.append(name)
            else:
                license_names.append(item)

    filtered = [n for n in license_names if n]
    return ", ".join(filtered) if filtered else "Unknown License"


def generate_markdown(libraries_data):
    libraries = libraries_data.get("libraries", [])
    licenses_dict = libraries_data.get("licenses", {})

    libraries.sort(key=lambda lib: (lib.get("name") or lib.get("uniqueId") or "").lower())

    lines = []
    lines.append("# Third-Party Open Source Licenses")
    lines.append("")
    lines.append("SimpleXray relies on the following open source libraries and components.")
    lines.append("We gratefully acknowledge the contributions of these projects.")
    lines.append("")

    lines.append("## Core & Native Components")
    lines.append("")
    version_props = load_version_properties()
    xray_ver = version_props.get("XRAY_CORE_VERSION", "").strip()
    hev_ver = version_props.get("HEV_TUN_VERSION", "").strip()

    xray_str = f" {xray_ver}" if xray_ver else ""
    hev_str = f" {hev_ver}" if hev_ver else ""

    lines.append(f"- [Xray-core](https://github.com/XTLS/Xray-core){xray_str} | Under Mozilla Public License 2.0")
    lines.append(f"- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel){hev_str} | Under MIT License")
    lines.append("")

    lines.append("## Android & Kotlin Libraries")
    lines.append("")

    for lib in libraries:
        name = lib.get("name", "").strip() or lib.get("uniqueId", "").strip()
        if not name:
            continue

        version = lib.get("artifactVersion", "").strip()
        website = lib.get("website", "").strip()
        licenses_raw = lib.get("licenses", [])
        license_text = format_license_text(licenses_raw, licenses_dict)

        version_str = f" {version}" if version else ""
        if website:
            line = f"- [{name}]({website}){version_str} | Under {license_text}"
        else:
            line = f"- {name}{version_str} | Under {license_text}"
        lines.append(line)

    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Generate third-party licenses documentation.")
    parser.add_argument("--refresh", action="store_true", help="Force refresh aboutlibraries.json via Gradle")
    args = parser.parse_args()

    if args.refresh or not JSON_PATH.exists():
        run_gradle_export()

    if not JSON_PATH.exists():
        print(f"Error: {JSON_PATH} not found.", file=sys.stderr)
        sys.exit(1)

    libraries_data = read_json_file(JSON_PATH)
    markdown_content = generate_markdown(libraries_data)

    with open(OUTPUT_MD, "w", encoding="utf-8") as f:
        f.write(markdown_content)

    print(f"Successfully generated {OUTPUT_MD}")


if __name__ == "__main__":
    main()
