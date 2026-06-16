from __future__ import annotations

import json
import sys
import unittest
from collections import Counter
from pathlib import Path


SECURITY_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SECURITY_DIR))

from attack_catalog import AttackCase, build_attack_catalog  # noqa: E402


class AttackCatalogTest(unittest.TestCase):
    def setUp(self) -> None:
        self.cases = build_attack_catalog()

    def test_catalog_contains_exactly_one_thousand_cases(self) -> None:
        self.assertEqual(1000, len(self.cases))

    def test_catalog_has_ten_balanced_domains(self) -> None:
        counts = Counter(case.domain for case in self.cases)
        self.assertEqual(10, len(counts))
        self.assertEqual({100}, set(counts.values()))

    def test_ids_and_semantic_fingerprints_are_unique(self) -> None:
        ids = [case.case_id for case in self.cases]
        fingerprints = [case.fingerprint for case in self.cases]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertEqual(len(fingerprints), len(set(fingerprints)))

    def test_cases_are_serializable_and_have_executable_invariants(self) -> None:
        for case in self.cases:
            self.assertIsInstance(case, AttackCase)
            self.assertIn(case.safety, {"local-active", "public-safe", "static"})
            self.assertTrue(case.assertion.kind)
            self.assertTrue(case.name)
            json.dumps(case.to_dict(), ensure_ascii=True, sort_keys=True)

    def test_public_safe_cases_are_read_only_or_bounded_auth_checks(self) -> None:
        for case in self.cases:
            if case.safety != "public-safe":
                continue
            self.assertIn(case.method, {"GET", "HEAD", "OPTIONS", "POST"})
            self.assertNotIn("resource-exhaustion", case.tags)
            self.assertNotIn("state-changing", case.tags)
            self.assertNotIn("sandbox-execution", case.tags)


if __name__ == "__main__":
    unittest.main()
