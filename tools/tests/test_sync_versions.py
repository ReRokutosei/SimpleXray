import os
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import sync_versions


class SyncVersionsValidationTest(unittest.TestCase):
    def test_current_properties_are_valid(self):
        properties = sync_versions.read_current_properties()
        self.assertEqual([], sync_versions.validate_properties(properties))

    def test_rejects_malformed_pins(self):
        properties = sync_versions.read_current_properties()
        properties["XRAY_CORE_COMMIT"] = "not-a-commit"
        properties["XRAY_CORE_ZIP_SHA256"] = "abc"
        properties["GO_VERSION"] = "go1.27"
        errors = sync_versions.validate_properties(properties)
        self.assertTrue(any("XRAY_CORE_COMMIT" in error for error in errors))
        self.assertTrue(any("XRAY_CORE_ZIP_SHA256" in error for error in errors))
        self.assertTrue(any("GO_VERSION" in error for error in errors))

    def test_rejects_unknown_or_missing_keys(self):
        properties = sync_versions.read_current_properties()
        del properties["NDK_VERSION"]
        properties["EXTRA"] = "value"
        errors = sync_versions.validate_properties(properties)
        self.assertTrue(any("NDK_VERSION" in error for error in errors))
        self.assertTrue(any("EXTRA" in error for error in errors))

    def test_geo_assets_are_not_hash_pinned(self):
        properties = sync_versions.read_current_properties()
        for key in ("GEOIP_SHA256", "GEOSITE_SHA256"):
            self.assertNotIn(key, properties)

        properties["GEOIP_SHA256"] = "a" * 64
        errors = sync_versions.validate_properties(properties)
        self.assertTrue(any("GEOIP_SHA256" in error for error in errors))

    def test_go_pin_must_cover_go_mod_directives(self):
        errors = sync_versions.validate_go_minimum({"GO_VERSION": "1.24.0"})
        self.assertTrue(any("GO_VERSION" in error for error in errors))
        self.assertEqual([], sync_versions.validate_go_minimum({"GO_VERSION": "1.27.1"}))

    @unittest.skipUnless(
        os.path.isdir(os.path.join(sync_versions.REPO_ROOT, "third_party", "hev-socks5-tunnel")),
        "hev submodule is not checked out",
    )
    def test_hev_version_falls_back_to_source_constants(self):
        self.assertEqual("2.18.0", sync_versions.get_hev_config_version())

        def fake_check_output(args, **kwargs):
            if "describe" in args:
                return "d9dca26\n"  # shallow CI checkout without tags
            if "rev-parse" in args:
                return "d9dca26\n"
            raise AssertionError(f"unexpected subprocess call: {args}")

        with patch.object(sync_versions.subprocess, "check_output", side_effect=fake_check_output):
            self.assertEqual("2.18.0 (d9dca26)", sync_versions.get_hev_version())

    def test_check_mode_fails_on_invalid_properties(self):
        invalid = sync_versions.read_current_properties()
        invalid["XRAY_CORE_COMMIT"] = "invalid"
        with patch.object(sync_versions, "read_current_properties", return_value=invalid), \
             patch("builtins.print"):
            self.assertFalse(sync_versions.sync_versions(check_only=True))


if __name__ == "__main__":
    unittest.main()
