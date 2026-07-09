import json
from pathlib import Path

from collections import Counter

from pyt_agent_lab.eval_pack_5000 import (
    build_eval_pack_5000,
    select_stratified_eval_subset,
    write_eval_pack_jsonl,
    write_manifest,
)


def test_build_eval_pack_5000_covers_required_slices() -> None:
    cases, manifest = build_eval_pack_5000(120, seed=20260706)

    assert len(cases) == 120
    assert manifest["actual_count"] == 120
    assert manifest["unique_case_ids"] == 120
    assert manifest["unique_user_prompts"] == 120
    assert set(manifest["slice_counts"]) == {
        "new_question",
        "new_chapter_slice",
        "new_student_expression",
        "web_workshop_input",
    }
    assert set(manifest["chapter_counts"]) == {str(chapter) for chapter in range(1, 8)}
    assert "不新增虚构章节" in manifest["official_course_boundary"]
    assert manifest["web_input_type_counts"]
    assert manifest["noise_profile_counts"]


def test_eval_pack_cases_preserve_scoring_contracts() -> None:
    cases, _ = build_eval_pack_5000(80, seed=7)

    for case in cases:
        assert case["expected_markers"]
        assert case["system"]
        assert case["user"]
        assert case["eval_pack_version"] == "5000-v1"
        assert case["source_case_id"]
        assert case["eval_slice"]
        if not case["evidence_ids"]:
            assert "common_knowledge" in case["user"] or "通识" in case["user"]


def test_write_eval_pack_outputs_jsonl_and_manifest(tmp_path: Path) -> None:
    cases, manifest = build_eval_pack_5000(40, seed=11)
    cases_path = tmp_path / "cases.jsonl"
    manifest_path = tmp_path / "manifest.json"

    count = write_eval_pack_jsonl(cases, cases_path)
    write_manifest(manifest, manifest_path)

    assert count == 40
    assert len(cases_path.read_text(encoding="utf-8").splitlines()) == 40
    loaded_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert loaded_manifest["actual_count"] == 40


def test_select_stratified_eval_subset_preserves_core_5000_axes() -> None:
    cases, _ = build_eval_pack_5000(5000, seed=20260706)

    subset, manifest = select_stratified_eval_subset(cases, 500, seed=20260709)

    assert len(subset) == 500
    assert manifest["source_count"] == 5000
    assert manifest["actual_count"] == 500
    assert manifest["unique_case_ids"] == 500
    assert Counter(case["agent"] for case in subset) == {
        "DebugAgent": 50,
        "GroundingCriticAgent": 50,
        "GuideCoachAgent": 50,
        "IntentRouterAgent": 50,
        "MindMapAgent": 50,
        "PathPlannerAgent": 50,
        "PracticeAgent": 50,
        "ResourceDesignerAgent": 50,
        "SafetyAgent": 50,
        "TutorAgent": 50,
    }
    assert Counter(case["eval_slice"] for case in subset) == {
        "new_question": 200,
        "new_chapter_slice": 100,
        "new_student_expression": 150,
        "web_workshop_input": 50,
    }
    assert set(Counter(str(case["chapter"]) for case in subset)) == {str(chapter) for chapter in range(1, 8)}
    assert set(case["beginner_noise_profile"] for case in subset if case.get("beginner_noise_profile")) == {
        "anxious_answer_request",
        "copy_paste_code_fragment",
        "fragmented_sentence",
        "incomplete_error_paste",
        "low_confidence_help",
        "mixed_terminology",
        "mobile_short_message",
        "reflection_after_failure",
        "teacher_assignment_context",
        "typo_pinyin_mix",
        "vague_reference",
        "wrong_kc_guess",
    }
    assert set(case["web_input_type"] for case in subset if case.get("web_input_type")) == {
        "ASK",
        "DEBUG",
        "HANDOUT",
        "MINDMAP",
        "PATH",
        "PRACTICE",
    }
    assert any(case.get("evidence_ids") for case in subset)
    assert any(not case.get("evidence_ids") for case in subset)
