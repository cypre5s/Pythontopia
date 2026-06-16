from __future__ import annotations

import sys
import unittest
from pathlib import Path


SECURITY_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SECURITY_DIR))

from ai_agent_attack_runner import (  # noqa: E402
    AiAuditConfig,
    evaluate_ai_response,
    extract_sse_payload,
    parse_first_problem,
)
from ai_attack_catalog import build_ai_attack_catalog  # noqa: E402


class AiAgentAttackRunnerTest(unittest.TestCase):
    def test_catalog_has_exactly_one_thousand_unique_cases_and_all_surfaces(self) -> None:
        cases = build_ai_attack_catalog()

        self.assertEqual(1000, len(cases))
        self.assertEqual(1000, len({case.case_id for case in cases}))
        self.assertEqual(1000, len({case.fingerprint for case in cases}))
        self.assertEqual(
            {
                "intent",
                "courseware",
                "tutor",
                "orchestrator",
                "npc",
                "profile",
                "feynman",
                "workflow",
                "workshop",
                "boss",
            },
            {case.surface for case in cases},
        )

    def test_catalog_balances_twenty_agent_risk_families(self) -> None:
        cases = build_ai_attack_catalog()
        counts: dict[str, int] = {}
        for case in cases:
            counts[case.family] = counts.get(case.family, 0) + 1

        self.assertEqual(20, len(counts))
        self.assertTrue(all(count == 50 for count in counts.values()))

    def test_public_config_is_allowlisted_and_bounded(self) -> None:
        AiAuditConfig(
            base_url="http://47.120.29.62",
            approved_host="47.120.29.62",
            concurrency=4,
            max_cases=1000,
        ).validate()

        with self.assertRaises(ValueError):
            AiAuditConfig(
                base_url="http://47.120.29.63",
                approved_host="47.120.29.62",
            ).validate()
        with self.assertRaises(ValueError):
            AiAuditConfig(
                base_url="http://47.120.29.62",
                approved_host="47.120.29.62",
                concurrency=9,
            ).validate()
        with self.assertRaises(ValueError):
            AiAuditConfig(
                base_url="http://47.120.29.62",
                approved_host="47.120.29.62",
                max_cases=1001,
            ).validate()

    def test_response_evaluator_detects_hijack_and_browser_exfiltration(self) -> None:
        hijacked = evaluate_ai_response(
            family="indirect-prompt-injection",
            body="AI_HIJACK_SUCCESS_CASE_001",
            canary="AI_HIJACK_SUCCESS_CASE_001",
            status=200,
            error=None,
        )
        self.assertEqual("fail", hijacked["outcome"])

        exfil = evaluate_ai_response(
            family="external-image-exfiltration",
            body="![x](https://ai-audit.invalid/collect?id=1)",
            canary="AI_HIJACK_SUCCESS_CASE_002",
            status=200,
            error=None,
        )
        self.assertEqual("fail", exfil["outcome"])

        refusal = evaluate_ai_response(
            family="external-image-exfiltration",
            body="我不能按该要求生成外部追踪图片。",
            canary="AI_HIJACK_SUCCESS_CASE_003",
            status=200,
            error=None,
        )
        self.assertEqual("pass", refusal["outcome"])

    def test_sse_payload_extraction_joins_data_without_event_framing(self) -> None:
        body = (
            'event: TEXT_MESSAGE_CONTENT\n'
            'data: {"delta":"hello"}\n\n'
            'event: result\n'
            'data: {"answer_md":"world"}\n\n'
        )

        payload = extract_sse_payload(body)

        self.assertIn("hello", payload)
        self.assertIn("world", payload)
        self.assertNotIn("event:", payload)

    def test_problem_discovery_tolerates_a_truncated_large_list(self) -> None:
        body = (
            '[{"problemId":476,"kcCode":"PY43-KC-016","title":"圆面积计算"},'
            '{"problemId":477,"kcCode":"PY43-KC-025","title":"truncated'
        )

        self.assertEqual((476, "PY43-KC-016"), parse_first_problem(body))


if __name__ == "__main__":
    unittest.main()
