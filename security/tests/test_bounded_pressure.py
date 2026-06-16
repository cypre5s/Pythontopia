from __future__ import annotations

import sys
import unittest
from pathlib import Path


SECURITY_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SECURITY_DIR))

from bounded_pressure import PressureConfig  # noqa: E402


class BoundedPressureTest(unittest.TestCase):
    def test_accepts_approved_bounded_probe(self) -> None:
        config = PressureConfig(500, 25, "/api/course/pack")
        self.assertEqual(500, config.requests)

    def test_rejects_excessive_pressure(self) -> None:
        with self.assertRaises(ValueError):
            PressureConfig(1001, 25, "/healthz")
        with self.assertRaises(ValueError):
            PressureConfig(500, 41, "/healthz")

    def test_rejects_state_changing_or_unknown_paths(self) -> None:
        with self.assertRaises(ValueError):
            PressureConfig(100, 10, "/api/auth/register")


if __name__ == "__main__":
    unittest.main()
