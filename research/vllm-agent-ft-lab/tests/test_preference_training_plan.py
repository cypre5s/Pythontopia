from __future__ import annotations

from pyt_agent_lab.preference_training_plan import build_dpo_training_plan, build_orpo_training_plan


def test_build_dpo_training_plan_records_adapter_and_preference_files() -> None:
    plan = build_dpo_training_plan(
        model_path="/models/qwen",
        sft_adapter_path="outputs/adapters/sft-lora",
        train_file="outputs/preferences/dpo/train.jsonl",
        heldout_file="outputs/preferences/dpo/heldout.jsonl",
        output_dir="outputs/adapters/dpo-v2",
    )

    assert plan["method"] == "dpo_lora"
    assert plan["model_path"] == "/models/qwen"
    assert plan["sft_adapter_path"] == "outputs/adapters/sft-lora"
    assert plan["train_file"] == "outputs/preferences/dpo/train.jsonl"
    assert plan["heldout_file"] == "outputs/preferences/dpo/heldout.jsonl"
    assert plan["dpo"]["beta"] == 0.1
    assert plan["lora"]["r"] == 8
    assert plan["training"]["learning_rate"] == 5e-5
    assert plan["training"]["seed"] == 20260710
    assert plan["training"]["data_seed"] == 20260710
    assert "Do not merge guard behavior into labels" in plan["notes"][0]


def test_build_dpo_training_plan_accepts_shorter_sequence_length() -> None:
    plan = build_dpo_training_plan(
        model_path="/models/qwen",
        sft_adapter_path="outputs/adapters/sft-lora",
        train_file="outputs/preferences/dpo/train.jsonl",
        heldout_file="outputs/preferences/dpo/heldout.jsonl",
        output_dir="outputs/adapters/dpo-v2-len512",
        max_seq_length=512,
    )

    assert plan["max_seq_length"] == 512


def test_build_orpo_training_plan_records_reference_free_method() -> None:
    plan = build_orpo_training_plan(
        model_path="/models/qwen",
        sft_adapter_path="outputs/adapters/sft-lora",
        train_file="outputs/preferences/dpo/train.jsonl",
        heldout_file="outputs/preferences/dpo/heldout.jsonl",
        output_dir="outputs/adapters/orpo-v1-len512",
        max_seq_length=512,
    )

    assert plan["method"] == "orpo_lora"
    assert plan["orpo"]["beta"] == 0.1
    assert plan["max_seq_length"] == 512
    assert "reference-model-free" in plan["notes"][0]


def test_build_orpo_training_plan_accepts_stability_overrides() -> None:
    plan = build_orpo_training_plan(
        model_path="/models/qwen",
        sft_adapter_path="outputs/adapters/sft-lora",
        train_file="outputs/preferences/dpo/train.jsonl",
        heldout_file="outputs/preferences/dpo/heldout.jsonl",
        output_dir="outputs/adapters/orpo-v1-len384-stable",
        max_seq_length=384,
        beta=0.05,
        learning_rate=1e-6,
        max_grad_norm=0.3,
    )

    assert plan["orpo"]["beta"] == 0.05
    assert plan["training"]["learning_rate"] == 1e-6
    assert plan["training"]["max_grad_norm"] == 0.3
