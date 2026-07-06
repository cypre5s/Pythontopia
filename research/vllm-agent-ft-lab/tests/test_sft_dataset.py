import json
from pathlib import Path

from pyt_agent_lab.sft_dataset import build_sft_examples, split_sft_examples, write_sft_dataset


def test_build_sft_examples_uses_original_prompt_and_clean_response() -> None:
    records = [
        {
            "case": {
                "id": "c1",
                "agent": "GuideCoachAgent",
                "task_type": "learning_decision",
                "system": "你是导学编排 Agent。输出 JSON。",
                "user": "围绕变量，不需要引用课件页码。",
                "evidence_ids": [],
            },
            "response": '{"recommended_action": "ask a probe", "evidence_ids": [], "grounding_note": "common_knowledge"}',
        }
    ]

    examples = build_sft_examples(records, source_run="run-a")

    assert len(examples) == 1
    example = examples[0]
    assert example["id"] == "sft-c1"
    assert example["messages"][0] == {"role": "system", "content": "你是导学编排 Agent。输出 JSON。"}
    assert example["messages"][1] == {"role": "user", "content": "围绕变量，不需要引用课件页码。"}
    assert example["messages"][2]["role"] == "assistant"
    assert "protocol_patch_v1" not in json.dumps(example, ensure_ascii=False)
    assert example["metadata"]["source_run"] == "run-a"
    assert example["metadata"]["agent"] == "GuideCoachAgent"


def test_build_sft_examples_filters_unclean_records() -> None:
    records = [
        {
            "case": {"id": "clean", "agent": "TutorAgent", "task_type": "hint", "system": "s", "user": "u", "evidence_ids": ["page-1-006"]},
            "response": '{"next_hint": "x", "evidence_ids": ["page-1-006"]}',
        },
        {
            "case": {"id": "bad", "agent": "TutorAgent", "task_type": "hint", "system": "s", "user": "u", "evidence_ids": ["page-1-006"]},
            "response": '{"next_hint": "x", "evidence_ids": []}',
        },
    ]

    examples = build_sft_examples(records, source_run="run-a")

    assert [example["id"] for example in examples] == ["sft-clean"]


def test_split_sft_examples_is_deterministic_and_disjoint() -> None:
    examples = [{"id": f"sft-{idx}", "metadata": {}} for idx in range(10)]

    split = split_sft_examples(examples, heldout_ratio=0.2, seed=7)

    assert len(split["train"]) == 8
    assert len(split["heldout"]) == 2
    assert {item["id"] for item in split["train"]}.isdisjoint({item["id"] for item in split["heldout"]})
    assert split == split_sft_examples(examples, heldout_ratio=0.2, seed=7)


def test_write_sft_dataset_outputs_manifest(tmp_path: Path) -> None:
    split = {
        "train": [{"id": "sft-a", "messages": [], "metadata": {"agent": "TutorAgent"}}],
        "heldout": [{"id": "sft-b", "messages": [], "metadata": {"agent": "SafetyAgent"}}],
    }

    manifest = write_sft_dataset(split, tmp_path)

    assert manifest["train_count"] == 1
    assert manifest["heldout_count"] == 1
    assert (tmp_path / "train.jsonl").read_text(encoding="utf-8").count("\n") == 1
    assert (tmp_path / "heldout.jsonl").read_text(encoding="utf-8").count("\n") == 1
    assert json.loads((tmp_path / "manifest.json").read_text(encoding="utf-8")) == manifest
