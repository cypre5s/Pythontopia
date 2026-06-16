from __future__ import annotations

import sys
import unittest
from pathlib import Path


SECURITY_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SECURITY_DIR))

from precision_attack_runner import (  # noqa: E402
    PrecisionConfig,
    analyse_timing_samples,
    parse_http_status,
    sanitise_result,
)


class PrecisionAttackRunnerTest(unittest.TestCase):
    def test_config_rejects_unapproved_or_excessive_public_runs(self) -> None:
        PrecisionConfig(
            base_url="http://47.120.29.62",
            approved_host="47.120.29.62",
            concurrency=12,
            timing_samples=20,
        ).validate()

        with self.assertRaises(ValueError):
            PrecisionConfig(
                base_url="http://47.120.29.63",
                approved_host="47.120.29.62",
            ).validate()
        with self.assertRaises(ValueError):
            PrecisionConfig(
                base_url="http://47.120.29.62",
                approved_host="47.120.29.62",
                concurrency=21,
            ).validate()
        with self.assertRaises(ValueError):
            PrecisionConfig(
                base_url="http://47.120.29.62",
                approved_host="47.120.29.62",
                timing_samples=41,
            ).validate()

    def test_timing_analysis_flags_repeatable_username_oracle(self) -> None:
        vulnerable = analyse_timing_samples(
            known_ms=[118, 121, 125, 130, 135, 140, 145],
            unknown_ms=[18, 20, 21, 22, 24, 25, 27],
        )
        self.assertTrue(vulnerable["oracle_detected"])

        noisy_but_close = analyse_timing_samples(
            known_ms=[92, 98, 101, 105, 109, 113, 118],
            unknown_ms=[88, 94, 99, 103, 107, 111, 116],
        )
        self.assertFalse(noisy_but_close["oracle_detected"])

    def test_raw_http_status_parser_handles_valid_and_invalid_responses(self) -> None:
        self.assertEqual(400, parse_http_status(b"HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n"))
        self.assertIsNone(parse_http_status(b"not-http"))
        self.assertIsNone(parse_http_status(b""))

    def test_result_sanitiser_redacts_secrets_and_caps_bodies(self) -> None:
        value = sanitise_result(
            {
                "token": "secret.jwt.value",
                "password": "AuditPass-123!",
                "authorization": "Bearer abc",
                "body": "A" * 9000,
                "nested": {"cookie": "SESSION=secret"},
            }
        )
        self.assertEqual("[REDACTED]", value["token"])
        self.assertEqual("[REDACTED]", value["password"])
        self.assertEqual("[REDACTED]", value["authorization"])
        self.assertEqual("[REDACTED]", value["nested"]["cookie"])
        self.assertLessEqual(len(value["body"]), 4096)


if __name__ == "__main__":
    unittest.main()
