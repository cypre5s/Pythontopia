from __future__ import annotations

import hashlib
import json
from pathlib import Path

import pytest

from pyt_agent_lab.rsage_training_plan import (
    build_rsage_training_plans,
    build_training_command,
    filter_rows_for_training_plan,
    validate_training_plan_hashes,
    write_rsage_training_plans,
)


def _curated_dir(tmp_path: Path) -> Path:
    root = tmp_path / "curated"
    root.mkdir()
    rows = {
        "sft_train.jsonl": [{"id": "sft-1", "messages": []}],
        "sft_dev.jsonl": [{"id": "sft-dev", "messages": []}],
        "sft_heldout.jsonl": [{"id": "sft-heldout", "messages": []}],
        "preference_train.jsonl": [
            {"id": "pref-1", "prompt": [], "chosen": "{}", "rejected": "{}", "preference_q": 1.0}
        ],
        "preference_dev.jsonl": [
            {"id": "pref-dev", "prompt": [], "chosen": "{}", "rejected": "{}", "preference_q": 1.0}
        ],
        "preference_heldout.jsonl": [
            {"id": "pref-heldout", "prompt": [], "chosen": "{}", "rejected": "{}", "preference_q": 1.0}
        ],
    }
    for name, values in rows.items():
        (root / name).write_text(
            "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in values),
            encoding="utf-8",
        )
    return root


def _curated_budget_dir(tmp_path: Path) -> Path:
    root = tmp_path / "curated-budget"
    root.mkdir()
    target = [
        {"id": f"target-{index:03d}", "messages": [], "metadata": {"source_kind": "teacher_verified_target"}}
        for index in range(294)
    ]
    retention = [
        {"id": f"retention-{index:03d}", "messages": [], "metadata": {"source_kind": "champion_retention_replay"}}
        for index in range(252)
    ]
    preference = [
        {
            "id": f"pref-{index:03d}",
            "prompt": [],
            "chosen": "{}",
            "rejected": "{}",
            "preference_q": 1.0,
        }
        for index in range(208)
    ]
    rows = {
        "sft_train.jsonl": [*target, *retention],
        "sft_dev.jsonl": [{"id": "sft-dev", "messages": []}],
        "sft_heldout.jsonl": [{"id": "sft-heldout", "messages": []}],
        "preference_train.jsonl": preference,
        "preference_dev.jsonl": [{"id": "pref-dev", "prompt": [], "chosen": "{}", "rejected": "{}", "preference_q": 1.0}],
        "preference_heldout.jsonl": [{"id": "pref-heldout", "prompt": [], "chosen": "{}", "rejected": "{}", "preference_q": 1.0}],
    }
    for name, values in rows.items():
        (root / name).write_text(
            "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in values),
            encoding="utf-8",
        )
    return root


def test_builds_nf4_specialist_sft_and_gdpo_rpo_plans(tmp_path: Path) -> None:
    curated = _curated_dir(tmp_path)

    plans = build_rsage_training_plans(
        curated_dir=curated,
        model_path="/models/Qwen3.5-4B",
        adapter_root=tmp_path / "adapters",
        seed=42,
        max_seq_length=384,
        beta=0.1,
        lambda_sft=0.2,
    )

    sft = plans["sft"]
    gdpo = plans["gdpo_rpo"]
    assert sft["method"] == "qlora_4bit"
    assert sft["quantization"] == {
        "load_in_4bit": True,
        "quant_type": "nf4",
        "compute_dtype": "bfloat16",
        "double_quant": True,
    }
    assert sft["specialist"]["agent_name"] == "TutorDecisionAgent"
    assert sft["specialist"]["route_is_deterministic"] is True
    assert sft["seed"] == gdpo["seed"] == 42
    assert sft["max_seq_length"] == gdpo["max_seq_length"] == 384
    assert gdpo["method"] == "gdpo_rpo_lora"
    assert gdpo["gdpo_rpo"]["beta"] == 0.1
    assert gdpo["gdpo_rpo"]["lambda_sft"] == 0.2
    assert gdpo["gdpo_rpo"]["disable_dropout"] is True
    assert sft["initialization"]["mode"] == "continue_from_frozen_champion_adapter"
    assert sft["initial_adapter_path"].endswith("e1-protocol-patch-v1-1000-v1-dpo-v2-len512")
    assert gdpo["sft_adapter_path"] == sft["output_dir"]
    assert gdpo["output_dir"] != sft["output_dir"]
    assert "seed42" in gdpo["output_dir"]
    assert sft["training"]["weight_decay"] == 0.01
    assert sft["training"]["max_grad_norm"] == 1.0
    assert gdpo["training"]["max_grad_norm"] == 0.3
    assert gdpo["training"]["load_best_model_at_end"] is True
    assert gdpo["fit_safeguards"]["early_stopping_patience"] == 2
    assert gdpo["fit_safeguards"]["learning_curve_fractions"] == [0.35, 0.7, 1.0]
    assert gdpo["fit_safeguards"]["three_seed_promotion"]["seeds"] == [42, 43, 44]


def test_learning_curve_subsets_are_exact_nested_and_keep_dev_frozen(tmp_path: Path) -> None:
    curated = _curated_budget_dir(tmp_path)
    plans_by_fraction = {
        fraction: build_rsage_training_plans(
            curated_dir=curated,
            model_path="/models/Qwen3.5-4B",
            adapter_root=tmp_path / "adapters",
            seed=42,
            data_fraction=fraction,
        )
        for fraction in (0.35, 0.70, 1.0)
    }

    assert [
        plans_by_fraction[fraction]["sft"]["subset_counts"]["selected"]
        for fraction in (0.35, 0.70, 1.0)
    ] == [191, 382, 546]
    assert [
        plans_by_fraction[fraction]["gdpo_rpo"]["subset_counts"]["selected"]
        for fraction in (0.35, 0.70, 1.0)
    ] == [73, 146, 208]
    sft_sets = [
        set(plans_by_fraction[fraction]["sft"]["selected_train_ids"])
        for fraction in (0.35, 0.70, 1.0)
    ]
    preference_sets = [
        set(plans_by_fraction[fraction]["gdpo_rpo"]["selected_train_ids"])
        for fraction in (0.35, 0.70, 1.0)
    ]
    assert sft_sets[0] < sft_sets[1] < sft_sets[2]
    assert preference_sets[0] < preference_sets[1] < preference_sets[2]
    assert len({plans_by_fraction[value]["sft"]["heldout_file"] for value in (0.35, 0.70, 1.0)}) == 1


def test_plan_records_exact_data_hashes_and_detects_drift(tmp_path: Path) -> None:
    curated = _curated_dir(tmp_path)
    plans = build_rsage_training_plans(
        curated_dir=curated,
        model_path="/models/Qwen3.5-4B",
        adapter_root=tmp_path / "adapters",
        seed=43,
    )
    train_path = curated / "sft_train.jsonl"
    expected = hashlib.sha256(train_path.read_bytes()).hexdigest()

    assert plans["sft"]["data_hashes"]["train_file"] == expected
    validate_training_plan_hashes(plans["sft"])
    train_path.write_text("{}\n", encoding="utf-8")

    with pytest.raises(ValueError, match="训练数据哈希发生变化"):
        validate_training_plan_hashes(plans["sft"])


def test_plan_rejects_non_isolated_output_directories(tmp_path: Path) -> None:
    curated = _curated_dir(tmp_path)

    with pytest.raises(ValueError, match="输出目录必须隔离"):
        build_rsage_training_plans(
            curated_dir=curated,
            model_path="/models/Qwen3.5-4B",
            adapter_root=tmp_path / "adapters",
            seed=42,
            sft_output_dir=tmp_path / "same",
            gdpo_output_dir=tmp_path / "same",
        )


def test_atomic_writer_and_launcher_commands(tmp_path: Path) -> None:
    curated = _curated_dir(tmp_path)
    plans = build_rsage_training_plans(
        curated_dir=curated,
        model_path="/models/Qwen3.5-4B",
        adapter_root=tmp_path / "adapters",
        seed=44,
    )
    paths = write_rsage_training_plans(plans, tmp_path / "plans")

    assert set(paths) == {"sft", "gdpo_rpo", "manifest"}
    assert json.loads(paths["sft"].read_text(encoding="utf-8"))["seed"] == 44
    sft_command = build_training_command("sft", paths["sft"], max_steps=1)
    gdpo_command = build_training_command("gdpo_rpo", paths["gdpo_rpo"], resume_from_checkpoint="checkpoint-1")
    assert sft_command[-2:] == ["--max-steps", "1"]
    assert "train_lora.py" in sft_command[1]
    assert gdpo_command[-2:] == ["--resume-from-checkpoint", "checkpoint-1"]
    assert "train_gdpo_rpo.py" in gdpo_command[1]


def test_plan_subset_filter_fails_closed_on_missing_identifier(tmp_path: Path) -> None:
    curated = _curated_budget_dir(tmp_path)
    plan = build_rsage_training_plans(
        curated_dir=curated,
        model_path="/models/Qwen3.5-4B",
        adapter_root=tmp_path / "adapters",
        seed=42,
        data_fraction=0.35,
    )["sft"]
    rows = [json.loads(line) for line in (curated / "sft_train.jsonl").read_text(encoding="utf-8").splitlines()]

    selected = filter_rows_for_training_plan(rows, plan)
    assert len(selected) == 191
    assert {row["id"] for row in selected} == set(plan["selected_train_ids"])

    incomplete = [row for row in rows if row["id"] != plan["selected_train_ids"][0]]
    with pytest.raises(ValueError, match="训练子集编号不存在"):
        filter_rows_for_training_plan(incomplete, plan)
