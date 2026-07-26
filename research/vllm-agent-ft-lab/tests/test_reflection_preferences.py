from __future__ import annotations

import json

from pyt_agent_lab.reflection_preferences import (
    DIMENSIONS,
    FAILURE_AXES,
    build_reflection_eval_cases,
    build_reflection_preferences,
    split_and_write_reflection_preferences,
)
from pyt_agent_lab.scoring import score_case_response


def _source(index: int) -> dict:
    return {
        "id": f"source-{index}",
        "source_case_id": f"lineage-{index}",
        "agent": "PracticeAgent",
        "chapter": index % 7 + 1,
        "misconception_signal": f"误区-{index}",
        "learning_signal": f"学习信号-{index}",
        "learner_profile": f"画像-{index}",
        "instruction_style": "不给完整答案",
        "system": "legacy",
        "user": f"user-{index}",
    }


def test_reflection_eval_cases_are_balanced_and_semantically_scorable() -> None:
    cases = build_reflection_eval_cases([_source(index) for index in range(50)])
    assert len(cases) == 50
    assert {case["expected_reflection_dimension"] for case in cases} == set(DIMENSIONS)
    for case in cases:
        response = {
            "observations": [
                {
                    "claim": "多条记忆显示学生存在"
                    + case["expected_reflection_claim_markers"][0]
                    + "信号。",
                    "dimension_intent": case["expected_reflection_dimension"],
                    "tags": [],
                    "evidence_refs": case["expected_reflection_evidence_refs"],
                    "confidence": 0.8,
                }
            ]
        }
        score = score_case_response(case, json.dumps(response, ensure_ascii=False))
        assert score.promotion_pass


def test_reflection_preferences_exclude_protected_and_cover_failure_axes(tmp_path) -> None:
    source = [_source(index) for index in range(300)]
    protected = build_reflection_eval_cases(source[:50])
    rows = build_reflection_preferences(
        source_cases=source,
        protected_cases=protected,
        target_count=240,
        seed=42,
    )
    assert len(rows) == 240
    assert {row["metadata"]["preference_axis"] for row in rows} == set(FAILURE_AXES)
    assert {row["metadata"]["semantic_label"] for row in rows} == set(DIMENSIONS)
    assert not ({row["metadata"]["source_case_id"] for row in rows} & {f"source-{i}" for i in range(50)})
    assert all(row["metadata"]["chosen_promotion_pass"] for row in rows)
    assert all(not row["metadata"]["rejected_promotion_pass"] for row in rows)

    source_path = tmp_path / "source.jsonl"
    protected_path = tmp_path / "protected.jsonl"
    source_path.write_text("{}\n")
    protected_path.write_text("{}\n")
    manifest = split_and_write_reflection_preferences(
        rows,
        tmp_path / "out",
        heldout_ratio=0.1,
        seed=42,
        source_path=source_path,
        protected_paths=[protected_path],
    )
    assert manifest["train_count"] == 216
    assert manifest["heldout_count"] == 24
    assert manifest["protected_overlap_count"] == 0
