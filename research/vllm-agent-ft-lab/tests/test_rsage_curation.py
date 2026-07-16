from __future__ import annotations

import json
from pathlib import Path

import pytest

from pyt_agent_lab.rsage_case_builder import build_rsage_cases
from pyt_agent_lab.rsage_curation import (
    curate_rsage_dataset,
    filter_retention_rows_by_length,
    write_rsage_curation,
)
from pyt_agent_lab.rsage_data import EVENT_INTENTS


ROOT = Path(__file__).parents[3]
COURSE_DIR = ROOT / "data" / "course-pack-43"
RETENTION_PATH = (
    ROOT
    / "research"
    / "vllm-agent-ft-lab"
    / "outputs"
    / "sft"
    / "e1-protocol-patch-v1-1000-v1"
    / "train.jsonl"
)


def _load_retention() -> list[dict[str, object]]:
    return [json.loads(line) for line in RETENTION_PATH.read_text(encoding="utf-8").splitlines()]


def _labels(cases: list[dict[str, object]]) -> list[dict[str, object]]:
    labels = []
    for index, case in enumerate(cases):
        preferred = str(case["event_intent"])
        fallback = next(intent for intent in EVENT_INTENTS if intent != preferred)
        labels.append(
            {
                "case_id": case["case_id"],
                "status": "accepted",
                "misconception_type": f"{case['failure_axis']} 对应误区",
                "action_goal": "选择一个最小且可验证的下一教学动作",
                "admissible_intents": [preferred],
                "preferred_intent": preferred,
                "forbidden_intents": [fallback],
                "preference_q": 2 / 3 if index % 3 else 1.0,
                "hint_level": index % 4,
                "evidence_refs": [item["evidence_id"] for item in case["evidence_refs"]],
                "safety_flags": [],
                "valid_vote_count": 3,
                "vote_counts": (
                    {preferred: 3}
                    if index % 3 == 0
                    else {preferred: 2, fallback: 1}
                ),
                "model": "测试教师",
            }
        )
    return labels


def test_curates_exact_reduced_budget_with_family_isolation() -> None:
    cases, _ = build_rsage_cases(COURSE_DIR, count=420, seed=20260716)

    result = curate_rsage_dataset(cases, _labels(cases), _load_retention(), seed=20260716)

    assert len(result.target_sft_train) == 294
    assert len(result.retention_sft_train) == 252
    assert len(result.sft_train) == 546
    assert len(result.preference_train) == 208
    assert len(result.target_dev) == 84
    assert len(result.target_heldout) == 42
    assert result.manifest["message_zh"] == "已整理 546 条 SFT 与 208 条软偏好训练数据"
    assert {
        row["metadata"]["agent"] for row in result.retention_sft_train
    }.isdisjoint({"GuideCoachAgent", "PracticeAgent"})
    assert result.manifest["retention_excluded_target_agent_count"] == 163

    train_families = set(result.manifest["split_template_families"]["train"])
    dev_families = set(result.manifest["split_template_families"]["dev"])
    heldout_families = set(result.manifest["split_template_families"]["heldout"])
    assert train_families.isdisjoint(dev_families | heldout_families)
    assert dev_families.isdisjoint(heldout_families)

    split_ids = [
        {row["metadata"]["source_case_id"] for row in rows}
        for rows in (result.target_sft_train, result.target_dev, result.target_heldout)
    ]
    assert split_ids[0].isdisjoint(split_ids[1] | split_ids[2])
    assert split_ids[1].isdisjoint(split_ids[2])


def test_soft_preference_q_is_bound_to_the_actual_dissenting_pair() -> None:
    cases, _ = build_rsage_cases(COURSE_DIR, count=420, seed=20260716)
    labels = _labels(cases)

    result = curate_rsage_dataset(cases, labels, _load_retention(), seed=20260716)

    labels_by_id = {str(row["case_id"]): row for row in labels}
    soft_rows = [row for row in result.preference_train if row["preference_q"] < 1.0]
    assert soft_rows
    for row in soft_rows:
        source_id = row["metadata"]["source_case_id"]
        label = labels_by_id[source_id]
        dissenting = next(
            intent
            for intent, count in label["vote_counts"].items()
            if intent != label["preferred_intent"] and count > 0
        )
        assert json.loads(row["rejected"])["event_intent"] == dissenting


def test_training_prompt_is_compact_but_keeps_course_identity_and_evidence_titles() -> None:
    cases, _ = build_rsage_cases(COURSE_DIR, count=420, seed=20260716)

    result = curate_rsage_dataset(cases, _labels(cases), _load_retention(), seed=20260716)

    content = json.loads(result.target_sft_train[0]["messages"][1]["content"])
    assert len(content["kc"]["summary"]) <= 40
    assert content["kc"]["code"]
    assert content["kc"]["name"]
    assert content["evidence_allowlist"]
    assert all(set(item) == {"evidence_id", "title"} for item in content["evidence_allowlist"])


def test_retention_length_filter_replaces_overlong_and_target_agent_rows() -> None:
    class _Tokenizer:
        eos_token = "<eos>"

        def apply_chat_template(self, messages, *, tokenize=False, add_generation_prompt=False):  # noqa: ANN001
            text = "".join(str(item["content"]) for item in messages)
            return text + ("<assistant>" if add_generation_prompt else "")

        def __call__(self, *, text):  # noqa: ANN001
            return {"input_ids": list(range(len(text)))}

    rows = [
        {"id": "safe", "messages": [{"role": "user", "content": "问"}, {"role": "assistant", "content": "答"}], "metadata": {"agent": "DebugAgent"}},
        {"id": "long", "messages": [{"role": "user", "content": "问"}, {"role": "assistant", "content": "很" * 80}], "metadata": {"agent": "MindMapAgent"}},
        {"id": "target", "messages": [{"role": "user", "content": "问"}, {"role": "assistant", "content": "答"}], "metadata": {"agent": "GuideCoachAgent"}},
    ]

    eligible, report = filter_retention_rows_by_length(
        rows,
        tokenizer=_Tokenizer(),
        max_seq_length=32,
    )

    assert [row["id"] for row in eligible] == ["safe"]
    assert report["target_agent_excluded_count"] == 1
    assert report["overlength_count"] == 1
    assert report["eligible_count"] == 1


def test_curation_is_deterministic_for_same_seed() -> None:
    cases, _ = build_rsage_cases(COURSE_DIR, count=420, seed=77)
    labels = _labels(cases)
    retention = _load_retention()

    first = curate_rsage_dataset(cases, labels, retention, seed=77)
    second = curate_rsage_dataset(cases, labels, retention, seed=77)

    assert first.sft_train == second.sft_train
    assert first.preference_train == second.preference_train
    assert first.manifest["dataset_sha256"] == second.manifest["dataset_sha256"]


def test_protected_prompt_excludes_its_entire_template_family() -> None:
    cases, _ = build_rsage_cases(COURSE_DIR, count=420, seed=55)
    protected = {str(cases[0]["prompt_fingerprint"])}
    protected_family = str(cases[0]["source_lineage"]["template_family"])

    result = curate_rsage_dataset(
        cases,
        _labels(cases),
        _load_retention(),
        seed=55,
        protected_fingerprints=protected,
    )

    all_split_families = {
        family
        for values in result.manifest["split_template_families"].values()
        for family in values
    }
    assert protected_family not in all_split_families
    assert result.manifest["protected_family_exclusion_count"] == 42
    assert len(result.target_sft_train) == 294


def test_duplicate_prompt_fingerprints_fail_closed() -> None:
    cases, _ = build_rsage_cases(COURSE_DIR, count=420, seed=44)
    cases[1]["prompt_fingerprint"] = cases[0]["prompt_fingerprint"]

    with pytest.raises(ValueError, match="重复的候选提示指纹"):
        curate_rsage_dataset(cases, _labels(cases), _load_retention(), seed=44)


def test_curation_recovers_exact_training_budget_when_each_family_has_one_exclusion() -> None:
    cases, _ = build_rsage_cases(COURSE_DIR, count=420, seed=44)
    labels = _labels(cases)
    excluded_families: set[str] = set()
    for label, case in zip(labels, cases):
        family = str(case["source_lineage"]["template_family"])
        if family not in excluded_families:
            label["status"] = "excluded"
            excluded_families.add(family)

    result = curate_rsage_dataset(cases, labels, _load_retention(), seed=44)

    assert len(result.target_sft_train) == 294
    assert len(result.preference_train) == 208
    assert len(result.manifest["split_template_families"]["train"]) == 8
    assert result.manifest["unused_train_family_accepted_pairs"] == 34
    assert len(result.target_dev) == 41
    assert len(result.target_heldout) == 41


def test_writer_emits_all_splits_and_hash_manifest(tmp_path: Path) -> None:
    cases, _ = build_rsage_cases(COURSE_DIR, count=420, seed=22)
    result = curate_rsage_dataset(cases, _labels(cases), _load_retention(), seed=22)

    manifest = write_rsage_curation(result, tmp_path)

    expected = {
        "sft_train.jsonl",
        "sft_dev.jsonl",
        "sft_heldout.jsonl",
        "preference_train.jsonl",
        "preference_dev.jsonl",
        "preference_heldout.jsonl",
        "target_sft_train.jsonl",
        "retention_sft_train.jsonl",
        "manifest.json",
    }
    assert expected == {path.name for path in tmp_path.iterdir()}
    assert manifest["file_sha256"]["sft_train.jsonl"]
    assert len((tmp_path / "sft_train.jsonl").read_text(encoding="utf-8").splitlines()) == 546
