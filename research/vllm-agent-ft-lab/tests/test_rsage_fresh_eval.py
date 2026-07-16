from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

import pytest

from pyt_agent_lab.rsage_fresh_eval import (
    audit_fresh_eval_against_history,
    build_fresh_eval_pack,
    select_fresh_stratified_subset,
    write_fresh_eval_pack,
)
from pyt_agent_lab.rsage_eval_budget import validate_fresh_full5000_manifest


COURSE_DIR = Path(__file__).parents[3] / "data" / "course-pack-43"


def test_builds_independent_fresh_pack_with_target_and_retention_coverage() -> None:
    cases, manifest = build_fresh_eval_pack(COURSE_DIR, target_count=500, seed=20260716)

    assert len(cases) == 500
    assert len({row["id"] for row in cases}) == 500
    assert len({(row["system"], row["user"]) for row in cases}) == 500
    assert Counter(row["agent"] for row in cases) == {
        "TutorDecisionAgent": 50,
        "PracticeAgent": 50,
        "ResourceDesignerAgent": 50,
        "IntentRouterAgent": 50,
        "GuideCoachAgent": 50,
        "DebugAgent": 50,
        "GroundingCriticAgent": 50,
        "SafetyAgent": 50,
        "PathPlannerAgent": 50,
        "MindMapAgent": 50,
    }
    assert set(manifest["chapter_counts"]) == {str(index) for index in range(1, 8)}
    assert manifest["eval_pack_version"] == "fresh-full5000-v2"
    assert manifest["protected_status"] == "never_train"
    assert all(row["source_case_id"] == row["id"] for row in cases)
    assert all(row["prompt_family_id"].startswith("fresh-v2-surface-") for row in cases)


def test_freshness_audit_fails_closed_on_id_prompt_or_template_overlap() -> None:
    fresh, _ = build_fresh_eval_pack(COURSE_DIR, target_count=40, seed=9)
    historical = [
        {
            "id": fresh[0]["id"],
            "user": fresh[1]["user"],
            "system": fresh[1]["system"],
            "prompt_family_id": fresh[2]["prompt_family_id"],
        }
    ]

    with pytest.raises(ValueError, match="fresh 评测集与历史数据发生重叠") as raised:
        audit_fresh_eval_against_history(fresh, historical)

    assert raised.value.args


def test_stratified_500_is_ten_percent_and_deterministic() -> None:
    cases, _ = build_fresh_eval_pack(COURSE_DIR, target_count=5000, seed=20260716)

    first, first_manifest = select_fresh_stratified_subset(cases, target_count=500, seed=20260716)
    second, second_manifest = select_fresh_stratified_subset(cases, target_count=500, seed=20260716)

    assert first == second
    assert first_manifest == second_manifest
    assert Counter(row["agent"] for row in first) == {agent: 50 for agent in Counter(row["agent"] for row in cases)}
    assert first_manifest["source_count"] == 5000
    assert first_manifest["reduction_from_full"] == 0.90


def test_writer_freezes_hashes_and_history_audit(tmp_path: Path) -> None:
    cases, manifest = build_fresh_eval_pack(COURSE_DIR, target_count=40, seed=17)
    history_path = tmp_path / "history.jsonl"
    history_path.write_text(json.dumps({"id": "old", "system": "旧", "user": "旧"}, ensure_ascii=False) + "\n", encoding="utf-8")

    written = write_fresh_eval_pack(
        cases,
        manifest,
        cases_path=tmp_path / "fresh.jsonl",
        manifest_path=tmp_path / "manifest.json",
        historical_paths=[history_path],
    )

    assert written["freshness_audit"]["zero_historical_overlap"] is True
    assert written["jsonl_sha256"]
    assert written["historical_inputs"][0]["sha256"]


def test_full_manifest_satisfies_one_time_final_gate_contract(tmp_path: Path) -> None:
    cases, manifest = build_fresh_eval_pack(COURSE_DIR, target_count=5000, seed=20260716)
    history = tmp_path / "history.jsonl"
    history.write_text(json.dumps({"id": "old", "system": "旧", "user": "旧"}, ensure_ascii=False) + "\n", encoding="utf-8")

    written = write_fresh_eval_pack(
        cases,
        manifest,
        cases_path=tmp_path / "fresh5000.jsonl",
        manifest_path=tmp_path / "manifest.json",
        historical_paths=[history],
    )

    audit = validate_fresh_full5000_manifest(written)
    assert audit["eligible_for_final"] is True
