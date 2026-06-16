from __future__ import annotations

import sys
import tempfile
import unittest
from io import BytesIO
from pathlib import Path


SECURITY_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SECURITY_DIR))

from attack_catalog import Assertion  # noqa: E402
from attack_runner import (  # noqa: E402
    evaluate_assertion,
    load_existing_results,
    normalize_request_url,
    read_response_body,
    select_cases_for_mode,
    redact_evidence,
    validate_target,
)
from attack_catalog import build_attack_catalog  # noqa: E402


class AttackRunnerSafetyTest(unittest.TestCase):
    def test_local_mode_allows_only_loopback_targets(self) -> None:
        validate_target("http://127.0.0.1:18080", "local")
        validate_target("http://localhost:5173", "local")
        with self.assertRaises(ValueError):
            validate_target("http://example.com", "local")

    def test_public_mode_allows_only_explicit_approved_host(self) -> None:
        validate_target("http://47.120.29.62", "public", {"47.120.29.62"})
        with self.assertRaises(ValueError):
            validate_target("http://127.0.0.1:18080", "public", {"47.120.29.62"})
        with self.assertRaises(ValueError):
            validate_target("http://47.120.29.63", "public", {"47.120.29.62"})

    def test_evidence_redacts_credentials_and_caps_body(self) -> None:
        evidence = redact_evidence(
            {
                "Authorization": "Bearer abc.def.ghi",
                "X-Internal-Token": "top-secret",
                "body": "A" * 10000,
                "password": "AuditPass-123!",
            },
            max_body=128,
        )
        self.assertEqual("[REDACTED]", evidence["Authorization"])
        self.assertEqual("[REDACTED]", evidence["X-Internal-Token"])
        self.assertEqual("[REDACTED]", evidence["password"])
        self.assertLessEqual(len(evidence["body"]), 128)

    def test_core_assertions_detect_security_failures(self) -> None:
        self.assertTrue(evaluate_assertion(Assertion("unauthorized"), 401, {}, ""))
        self.assertFalse(evaluate_assertion(Assertion("unauthorized"), 200, {}, "{}"))
        self.assertFalse(evaluate_assertion(Assertion("no_5xx"), 500, {}, "error"))
        self.assertFalse(
            evaluate_assertion(
                Assertion("cors_restricted"),
                204,
                {"access-control-allow-origin": "https://evil.invalid"},
                "",
            )
        )
        self.assertTrue(
            evaluate_assertion(
                Assertion("header_nosniff"),
                200,
                {"x-content-type-options": "nosniff"},
                "",
            )
        )

    def test_internal_service_assertion_accepts_spa_or_rejection_only(self) -> None:
        self.assertTrue(
            evaluate_assertion(
                Assertion("internal_not_exposed"),
                200,
                {"content-type": "text/html"},
                "<!doctype html><title>Pythontopia</title>",
                mode="public",
            )
        )

    def test_request_url_encodes_spaces_and_html_payload_characters(self) -> None:
        url = normalize_request_url(
            'http://47.120.29.62/?audit="><img src=x onerror=security-canary>'
        )
        self.assertNotIn(" ", url)
        self.assertIn("%3Cimg%20src=x%20onerror=security-canary%3E", url)

    def test_private_cache_policy_accepts_no_store_empty_success(self) -> None:
        self.assertTrue(
            evaluate_assertion(
                Assertion("private_cache_policy"),
                204,
                {"cache-control": "no-cache, no-store"},
                "",
            )
        )

    def test_resume_loader_returns_completed_ids_and_counts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "results.jsonl"
            path.write_text(
                '{"case_id":"SEC-01","outcome":"pass"}\n'
                '{"case_id":"SEC-02","outcome":"fail"}\n',
                encoding="utf-8",
            )
            completed, counts = load_existing_results(path)
        self.assertEqual({"SEC-01", "SEC-02"}, completed)
        self.assertEqual({"pass": 1, "fail": 1}, counts)
        self.assertTrue(
            evaluate_assertion(
                Assertion("internal_not_exposed"),
                405,
                {"content-type": "text/html"},
                "",
                mode="public",
            )
        )
        self.assertFalse(
            evaluate_assertion(
                Assertion("internal_not_exposed"),
                200,
                {"content-type": "application/json"},
                '{"service":"pythontopia-rag"}',
                mode="public",
            )
        )

    def test_authorized_public_active_mode_executes_all_catalog_cases(self) -> None:
        selected = select_cases_for_mode(build_attack_catalog(), "public-active")
        self.assertEqual(1000, len(selected))

    def test_public_safe_mode_keeps_only_non_destructive_subset(self) -> None:
        selected = select_cases_for_mode(build_attack_catalog(), "public-safe")
        self.assertTrue(selected)
        self.assertTrue(all(case.safety == "public-safe" for case in selected))
        self.assertLess(len(selected), 1000)

    def test_streaming_response_read_is_capped_without_waiting_for_eof(self) -> None:
        class StreamingResponse:
            def __init__(self) -> None:
                self.buffer = BytesIO(b"A" * 4096)

            def read1(self, size: int) -> bytes:
                return self.buffer.read(size)

        body = read_response_body(StreamingResponse(), 128)
        self.assertEqual("A" * 128, body)


if __name__ == "__main__":
    unittest.main()
