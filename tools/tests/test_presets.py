import os
import sys
import unittest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from presets import resolve_backends


class ResolveBackendsTest(unittest.TestCase):
    ALL = ["hev", "xray", "simpletun"]

    def test_none_uses_fallback(self):
        self.assertEqual(resolve_backends(None, ["hev"], self.ALL), ["hev"])

    def test_empty_uses_fallback(self):
        self.assertEqual(resolve_backends("  ", ["hev"], self.ALL), ["hev"])

    def test_all_alias_expands(self):
        self.assertEqual(resolve_backends("all", ["hev"], self.ALL), self.ALL)
        self.assertEqual(resolve_backends(" ALL ", ["hev"], self.ALL), self.ALL)

    def test_csv_list_is_trimmed(self):
        self.assertEqual(resolve_backends("hev, simpletun", [], self.ALL), ["hev", "simpletun"])


if __name__ == "__main__":
    unittest.main()
