from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

from pythontopia_graph.eval.m1_golden_gate import (
    CANARY_TRAFFIC_FRACTION,
    DEFAULT_M1_GOLDEN_DATASET,
    run_m1_golden_gate,
)


def test_m1_golden_dataset_covers_learner_debug_route_safety_and_leakage() -> None:
    report = run_m1_golden_gate(DEFAULT_M1_GOLDEN_DATASET)

    assert report.passed is True
    assert report.dataset_id == "agentic-m1-golden-v1"
    assert report.total_cases >= 12
    assert report.category_counts["learner"] >= 3
    assert report.category_counts["debug"] >= 3
    assert report.category_counts["route"] >= 2
    assert report.category_counts["safety"] >= 2
    assert report.category_counts["leakage"] >= 2
    assert report.case_failures == []
    assert report.canary.policy_version == "agentic_loop_v1"
    assert report.canary.traffic_fraction == CANARY_TRAFFIC_FRACTION
    assert report.canary.real_traffic_enabled is False
    assert report.canary.metadata_only is True
    assert report.canary.one_variable_only is True
    assert report.canary.rollback_rules


def test_m1_golden_gate_rejects_leaking_debug_candidate(tmp_path: Path) -> None:
    dataset = tmp_path / "leak.jsonl"
    dataset.write_text(
        json.dumps(
            {
                "id": "debug-leak",
                "category": "leakage",
                "loop_type": "DEBUG",
                "policy_version": "agentic_loop_v1",
                "trace_id": "trace-debug-leak",
                "input": {"submission_id": 91, "kc_codes": ["PY43-KC-016"]},
                "expected": {
                    "required_evidence_refs": ["submission:91"],
                    "required_action_keywords": ["hint", "transfer"],
                    "route": "debug",
                    "forbidden_keywords": ["hidden test", "copy this answer"],
                },
                "candidate": {
                    "evidence_refs": ["submission:91"],
                    "next_action": "hint then transfer check",
                    "route": "debug",
                    "safety_pass": True,
                    "schema_valid": True,
                    "leakage_flags": ["hidden_test_exposed"],
                    "content": "hidden test says copy this answer",
                },
            },
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )

    report = run_m1_golden_gate(dataset)

    assert report.passed is False
    assert report.case_failures
    assert report.case_failures[0].case_id == "debug-leak"
    assert "leakage" in report.case_failures[0].reasons[0]


def test_m1_golden_gate_cli_outputs_json_report() -> None:
    completed = subprocess.run(
        [
            sys.executable,
            "-m",
            "pythontopia_graph.eval.m1_golden_gate",
            str(DEFAULT_M1_GOLDEN_DATASET),
        ],
        check=True,
        capture_output=True,
        text=True,
    )

    payload = json.loads(completed.stdout)
    assert payload["passed"] is True
    assert payload["canary"]["traffic_fraction"] == CANARY_TRAFFIC_FRACTION
