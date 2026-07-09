from __future__ import annotations

import json

from pyt_agent_lab.preference_dataset import (
    build_preference_examples,
    split_preference_examples,
    write_preference_dataset,
)


def test_build_preference_examples_pairs_clean_chosen_with_bad_rejected() -> None:
    case = {
        "id": "case-1",
        "agent": "DebugAgent",
        "system": "你是调试 Agent。输出 JSON。",
        "user": "引用 page-6-018 给一个调试提示。",
        "evidence_ids": ["page-6-018"],
    }
    chosen_records = [
        {
            "case": case,
            "response": json.dumps(
                {
                    "minimal_repro_hint": "先观察变量定义位置。",
                    "evidence_ids": ["page-6-018"],
                },
                ensure_ascii=False,
            ),
        }
    ]
    rejected_records = [
        {
            "case": case,
            "response": '{"minimal_repro_hint": "缺少证据字段"}',
        }
    ]

    examples = build_preference_examples(
        chosen_records=chosen_records,
        rejected_records=rejected_records,
        chosen_source="guarded-v1",
        rejected_source="raw-adapter",
    )

    assert len(examples) == 1
    assert examples[0]["prompt"] == [
        {"role": "system", "content": case["system"]},
        {"role": "user", "content": case["user"]},
    ]
    assert examples[0]["chosen"].startswith('{"minimal_repro_hint"')
    assert examples[0]["rejected"] == '{"minimal_repro_hint": "缺少证据字段"}'
    assert examples[0]["metadata"]["rejected_flags"] == ["missing_required_evidence_id"]


def test_build_preference_examples_skips_clean_rejected_records() -> None:
    case = {
        "id": "case-clean",
        "agent": "TutorAgent",
        "system": "你是导师 Agent。",
        "user": "解释变量。",
        "evidence_ids": [],
    }
    clean_response = '{"answer": "先说明变量和值的关系", "grounding_note": "common_knowledge"}'

    examples = build_preference_examples(
        chosen_records=[{"case": case, "response": clean_response}],
        rejected_records=[{"case": case, "response": clean_response}],
        chosen_source="e1-clean",
        rejected_source="raw-baseline",
    )

    assert examples == []


def test_write_preference_dataset_writes_splits_and_manifest(tmp_path) -> None:
    examples = [
        {
            "id": f"pref-case-{index}",
            "prompt": [{"role": "user", "content": f"question {index}"}],
            "chosen": f'{{"ok": {index}}}',
            "rejected": "not-json",
            "metadata": {
                "agent": "SafetyAgent",
                "chosen_source": "guarded-v1",
                "rejected_source": "raw",
                "rejected_flags": ["invalid_json"],
            },
        }
        for index in range(5)
    ]
    split = split_preference_examples(examples, heldout_ratio=0.4, seed=20260707)

    manifest = write_preference_dataset(split, tmp_path)

    assert manifest["train_count"] == 3
    assert manifest["heldout_count"] == 2
    assert manifest["total_count"] == 5
    assert manifest["flag_counts"] == {"invalid_json": 5}
    assert (tmp_path / "train.jsonl").exists()
    assert (tmp_path / "heldout.jsonl").exists()
    assert (tmp_path / "manifest.json").exists()
