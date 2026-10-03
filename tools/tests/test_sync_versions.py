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

    def test_go_pin_must_cover_go_mod_directives(self):
        errors = sync_versions.validate_go_minimum({"GO_VERSION": "1.25.0"})
        self.assertTrue(any("GO_VERSION" in error for error in errors))
        self.assertEqual([], sync_versions.validate_go_minimum({"GO_VERSION": "1.27.1"}))

    def test_check_mode_fails_on_invalid_properties(self):
        invalid = sync_versions.read_current_properties()
        invalid["XRAY_CORE_COMMIT"] = "invalid"
        with patch.object(sync_versions, "read_current_properties", return_value=invalid), \
             patch("builtins.print"):
            self.assertFalse(sync_versions.sync_versions(check_only=True))


if __name__ == "__main__":
    unittest.main()
