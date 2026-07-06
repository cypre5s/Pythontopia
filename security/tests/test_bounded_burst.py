from __future__ import annotations

import sys
import unittest
from pathlib import Path


SECURITY_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SECURITY_DIR))

from bounded_burst import BurstConfig  # noqa: E402


class BoundedBurstTest(unittest.TestCase):
    def test_rejects_unbounded_request_counts(self) -> None:
        with self.assertRaises(ValueError):
            BurstConfig(requests=501, concurrency=10)

    def test_rejects_dangerous_concurrency(self) -> None:
        with self.assertRaises(ValueError):
            BurstConfig(requests=100, concurrency=26)

    def test_accepts_bounded_public_probe(self) -> None:
        config = BurstConfig(requests=200, concurrency=20)
        self.assertEqual(200, config.requests)
        self.assertEqual(20, config.concurrency)


if __name__ == "__main__":
    unittest.main()
