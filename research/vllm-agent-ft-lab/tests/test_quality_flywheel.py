from __future__ import annotations

import json

import pytest

from pyt_agent_lab.quality_flywheel import (
    build_failure_matrix,
    build_layered_record,
    build_paired_comparison,
    summarize_layers,
)


def _case(case_id: str = "c1") -> dict:
    return {
        "id": case_id,
        "agent": "PracticeAgent",
        "chapter": 2,
        "system": "return json",
        "user": "help with range",
        "expected_markers": ["range"],
        "evidence_ids": ["page-2-014"],
        "forbidden_answer_risk": True,
    }


def test_layered_metrics_keep_raw_guard_and_final_separate() -> None:
    case = _case()
    raw = json.dumps(
        {
            "misconception": "range boundary",
            "hint_steps": ["read page-2-014"],
            "blocked_content": "complete answer",
            "evidence_ids": ["page-2-014"],
        }
    )
    layered = build_layered_record({"case": case, "response": raw})

    assert layered["raw_score"]["schema_valid"] is False
    assert layered["raw_score"]["contract_pass"] is False
    assert layered["guard_score"]["contract_pass"] is True
    assert layered["guard_repaired"] is True
    assert layered["fallback_used"] is False
    assert layered["final_score"]["contract_pass"] is True


def test_invalid_json_is_counted_as_fallback_not_guard_repair() -> None:
    layered = build_layered_record({"case": _case(), "response": "not json"})

    assert layered["guard_repaired"] is False
    assert layered["fallback_used"] is True
    assert layered["final_score"]["contract_pass"] is True
    summary = summarize_layers([layered])
    assert summary["raw"]["contract_pass_rate"] == 0.0
    assert summary["guard_repair_rate"] == 0.0
    assert summary["fallback_rate"] == 1.0
    assert summary["critical_slices"]["safety_sensitive"] == 0.0
    assert summary["critical_slices"]["evidence_required"] == 0.0
    assert summary["safety_sensitive_forbidden_leak_rate"] == 0.0


def test_failure_matrix_has_requested_slices_and_schema_fields() -> None:
    layered = build_layered_record({"case": _case(), "response": "not json"})
    matrix = build_failure_matrix([layered])

    assert matrix["agent"]["PracticeAgent"]["failure_count"] == 1
    assert matrix["chapter"]["2"]["case_count"] == 1
    assert matrix["beginner_noise"]["clean"]["case_count"] == 1
    assert matrix["web_input"]["non_web"]["case_count"] == 1
    assert matrix["evidence"]["required"]["case_count"] == 1
    assert matrix["safety"]["sensitive"]["case_count"] == 1


def test_paired_comparison_rejects_case_set_mismatch() -> None:
    left = [build_layered_record({"case": _case("c1"), "response": "not json"})]
    right = [build_layered_record({"case": _case("c2"), "response": "not json"})]

    with pytest.raises(ValueError, match="case mismatch"):
        build_paired_comparison({"sft": left, "dpo": right})
