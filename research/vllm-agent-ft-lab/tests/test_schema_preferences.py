from __future__ import annotations

import json

import pytest

from pyt_agent_lab.schema_preferences import (
    build_schema_preference_dataset,
    split_and_write_schema_preferences,
)
from pyt_agent_lab.scoring import score_case_response


def _case(index: int) -> dict:
    return {
        "id": f"train-{index}",
        "agent": "TutorAgent",
        "chapter": index % 7 + 1,
        "system": "TutorAgent JSON",
        "user": f"question {index}",
        "evidence_ids": [],
        "expected_markers": [],
    }


def test_schema_preferences_are_targeted_and_exclude_protected_eval(tmp_path) -> None:
    source = [_case(index) for index in range(205)]
    protected = [source[0]]
    failures = [
        {
            "agent": "TutorAgent",
            "missing_schema_keys": ["safety_flags"],
            "schema_type_errors": ["evidence_ids"],
        }
    ]
    examples = build_schema_preference_dataset(
        source_cases=source,
        protected_cases=protected,
        failure_records=failures,
        target_count=200,
        seed=7,
    )

    assert len(examples) == 200
    assert all(example["metadata"]["source_case_id"] != "train-0" for example in examples)
    for example in examples:
        case_id = example["metadata"]["source_case_id"]
        case = next(case for case in source if case["id"] == case_id)
        assert score_case_response(case, example["chosen"]).contract_pass
        rejected = score_case_response(case, example["rejected"])
        assert rejected.json_valid and not rejected.schema_valid and not rejected.forbidden_leak

    protected_path = tmp_path / "golden.jsonl"
    protected_path.write_text(json.dumps(protected[0]) + "\n", encoding="utf-8")
    source_path = tmp_path / "source.jsonl"
    source_path.write_text("".join(json.dumps(case) + "\n" for case in source), encoding="utf-8")
    manifest = split_and_write_schema_preferences(
        examples,
        tmp_path / "output",
        protected_paths=[protected_path],
        source_path=source_path,
    )
    assert manifest["total_count"] == 200
    assert manifest["protected_overlap_count"] == 0
    assert manifest["train_count"] + manifest["heldout_count"] == 200


def test_schema_preference_count_is_bounded() -> None:
    with pytest.raises(ValueError, match="between 200 and 500"):
        build_schema_preference_dataset(
            source_cases=[], protected_cases=[], failure_records=[], target_count=20
        )
