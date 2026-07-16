from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any, Mapping


def build_rsage_training_plans(
    *,
    curated_dir: str | Path,
    model_path: str | Path,
    adapter_root: str | Path,
    seed: int,
    max_seq_length: int = 384,
    beta: float = 0.1,
    lambda_sft: float = 0.2,
    data_fraction: float = 1.0,
    sft_output_dir: str | Path | None = None,
    gdpo_output_dir: str | Path | None = None,
) -> dict[str, dict[str, Any]]:
    if seed < 0:
        raise ValueError("训练种子不能为负数")
    if max_seq_length <= 0:
        raise ValueError("最大序列长度必须是正整数")
    if beta <= 0 or lambda_sft < 0:
        raise ValueError("beta 必须大于零，chosen NLL 系数不能为负")
    if data_fraction not in {0.35, 0.70, 1.0}:
        raise ValueError("学习曲线数据比例只能是 0.35、0.70 或 1.0")

    curated = Path(curated_dir)
    files = {
        "sft_train": curated / "sft_train.jsonl",
        "sft_dev": curated / "sft_dev.jsonl",
        "sft_heldout": curated / "sft_heldout.jsonl",
        "preference_train": curated / "preference_train.jsonl",
        "preference_dev": curated / "preference_dev.jsonl",
        "preference_heldout": curated / "preference_heldout.jsonl",
    }
    missing = [str(path) for path in files.values() if not path.exists()]
    if missing:
        raise FileNotFoundError("缺少整理后的训练数据：" + "、".join(missing))

    fraction_label = {0.35: "f035", 0.70: "f070", 1.0: "f100"}[data_fraction]
    adapter_base = Path(adapter_root)
    sft_output = Path(sft_output_dir) if sft_output_dir else adapter_base / f"rsage-sft-{fraction_label}-seed{seed}"
    gdpo_output = (
        Path(gdpo_output_dir)
        if gdpo_output_dir
        else adapter_base / f"rsage-gdpo-rpo-{fraction_label}-seed{seed}"
    )
    if sft_output.resolve() == gdpo_output.resolve():
        raise ValueError("SFT 与 GDPO+RPO 输出目录必须隔离")

    specialist = {
        "agent_name": "TutorDecisionAgent",
        "schema_version": "rsage-ir-v1",
        "route_key": "TutorDecisionAgent::rsage-ir-v1",
        "route_is_deterministic": True,
        "non_target_policy": "继续使用冻结 champion，不加载本 specialist",
        "message_zh": "仅训练下一教学动作专属 Adapter，非目标 Agent 保持冻结",
    }
    quantization = {
        "load_in_4bit": True,
        "quant_type": "nf4",
        "compute_dtype": "bfloat16",
        "double_quant": True,
    }
    fit_safeguards = {
        "early_stopping_patience": 2,
        "early_stopping_threshold": 0.005,
        "checkpoint_selection_metric": "eval_loss",
        "checkpoint_selection_boundary_zh": "eval_loss 只用于优化早停，不能替代语义晋级评测",
        "hard_stop_on_nan_or_oom": True,
        "train_dev_gap_warning": 0.15,
        "action_entropy_warning": 0.80,
        "action_entropy_hard_stop": 0.65,
        "max_action_share_warning": 0.35,
        "max_action_share_hard_stop": 0.50,
        "learning_curve_fractions": [0.35, 0.70, 1.0],
        "learning_curve_plateau_min_gain": 0.02,
        "underfit_min_loss_reduction": 0.05,
        "underfit_max_preference_accuracy": 0.55,
        "three_seed_promotion": {
            "seeds": [42, 43, 44],
            "minimum_positive_seeds": 2,
            "median_gain_min": 0.03,
            "worst_seed_floor": -0.02,
            "maximum_seed_range": 0.10,
        },
        "threshold_basis": "项目经验阈值，不是论文通用定律",
    }
    sft_rows = _load_jsonl(files["sft_train"])
    preference_rows = _load_jsonl(files["preference_train"])
    selected_sft_ids, sft_subset_counts = _select_sft_subset(
        sft_rows,
        data_fraction,
        data_seed=20260716,
    )
    selected_preference_ids = _select_nested_ids(
        preference_rows,
        data_fraction,
        data_seed=20260716,
        namespace="软偏好",
    )
    preference_subset_counts = {
        "total": len(preference_rows),
        "selected": len(selected_preference_ids),
    }
    common = {
        "model_path": str(model_path),
        "max_seq_length": max_seq_length,
        "seed": seed,
        "data_seed": 20260716,
        "data_fraction": data_fraction,
        "specialist": specialist,
        "quantization": quantization,
        "fit_safeguards": fit_safeguards,
        "evaluation_boundary": {
            "legacy_full5000_is_final_protected": False,
            "message_zh": "旧 full5000 与历史训练源有重叠，只能作历史回归；正式最终评测必须使用新冻结且零重叠的 5000 集",
        },
    }
    sft = {
        **common,
        "method": "qlora_4bit",
        "stage": "rsage_specialist_sft",
        "train_file": str(files["sft_train"]),
        "heldout_file": str(files["sft_dev"]),
        "protected_heldout_file": str(files["sft_heldout"]),
        "output_dir": str(sft_output),
        "data_hashes": {
            "train_file": _file_hash(files["sft_train"]),
            "heldout_file": _file_hash(files["sft_dev"]),
            "protected_heldout_file": _file_hash(files["sft_heldout"]),
        },
        "selected_train_ids": selected_sft_ids,
        "subset_counts": sft_subset_counts,
        "subset_sha256": _value_hash(selected_sft_ids),
        "loss_masking": {"mode": "assistant_only"},
        "lora": {
            "r": 8,
            "alpha": 16,
            "dropout": 0.05,
            "target_modules": [
                "q_proj",
                "k_proj",
                "v_proj",
                "o_proj",
                "gate_proj",
                "up_proj",
                "down_proj",
            ],
        },
        "training": {
            "num_train_epochs": 4,
            "per_device_train_batch_size": 1,
            "per_device_eval_batch_size": 1,
            "gradient_accumulation_steps": 16,
            "learning_rate": 5e-5,
            "weight_decay": 0.01,
            "max_grad_norm": 1.0,
            "warmup_ratio": 0.05,
            "logging_steps": 1,
            "save_strategy": "epoch",
            "eval_strategy": "epoch",
            "save_total_limit": 2,
            "load_best_model_at_end": True,
            "metric_for_best_model": "eval_loss",
            "greater_is_better": False,
            "gradient_checkpointing": True,
            "bf16": True,
            "disable_tqdm": True,
            "seed": seed,
            "data_seed": 20260716,
        },
        "notes_zh": [
            "SFT 训练集包含教师核验目标样本与分层 retention 回放。",
            "开发集只用于早停；独立留出集和正式 protected 集均不得训练。",
        ],
    }
    gdpo = {
        **common,
        "method": "gdpo_rpo_lora",
        "stage": "rsage_specialist_gdpo_rpo",
        "sft_adapter_path": str(sft_output),
        "train_file": str(files["preference_train"]),
        "heldout_file": str(files["preference_dev"]),
        "protected_heldout_file": str(files["preference_heldout"]),
        "output_dir": str(gdpo_output),
        "data_hashes": {
            "train_file": _file_hash(files["preference_train"]),
            "heldout_file": _file_hash(files["preference_dev"]),
            "protected_heldout_file": _file_hash(files["preference_heldout"]),
        },
        "selected_train_ids": selected_preference_ids,
        "subset_counts": preference_subset_counts,
        "subset_sha256": _value_hash(selected_preference_ids),
        "gdpo_rpo": {
            "formula_version": "neurips2024-geometric-soft-preference-plus-rpo-chosen-nll-v1",
            "soft_preference_field": "preference_q",
            "beta": beta,
            "lambda_sft": lambda_sft,
            "loss_types": ["sigmoid", "sft"],
            "loss_weights": [1.0, lambda_sft],
            "precompute_ref_log_probs": True,
            "disable_dropout": True,
        },
        "training": {
            "num_train_epochs": 4,
            "per_device_train_batch_size": 1,
            "per_device_eval_batch_size": 1,
            "gradient_accumulation_steps": 16,
            "learning_rate": 5e-6,
            "weight_decay": 0.01,
            "max_grad_norm": 0.3,
            "warmup_ratio": 0.05,
            "logging_steps": 1,
            "save_strategy": "epoch",
            "eval_strategy": "epoch",
            "save_total_limit": 2,
            "load_best_model_at_end": True,
            "metric_for_best_model": "eval_loss",
            "greater_is_better": False,
            "gradient_checkpointing": True,
            "bf16": True,
            "disable_tqdm": True,
            "seed": seed,
            "data_seed": 20260716,
        },
        "notes_zh": [
            "GDPO 使用教师成对软偏好概率；RPO chosen NLL 是独立正则项。",
            "训练与开发 microbatch 均固定为 1，保证动态 beta 与逐样本 GDPO 公式严格等价。",
        ],
    }
    return {"sft": sft, "gdpo_rpo": gdpo}


def validate_training_plan_hashes(plan: Mapping[str, Any]) -> None:
    hashes = plan.get("data_hashes")
    if not isinstance(hashes, Mapping):
        raise ValueError("训练计划缺少数据哈希")
    paths = {
        "train_file": plan.get("train_file"),
        "heldout_file": plan.get("heldout_file"),
        "protected_heldout_file": plan.get("protected_heldout_file"),
    }
    for key, raw_path in paths.items():
        if not isinstance(raw_path, str) or not raw_path:
            raise ValueError(f"训练计划缺少 {key}")
        actual = _file_hash(Path(raw_path))
        if actual != hashes.get(key):
            raise ValueError(f"训练数据哈希发生变化：{key}")


def filter_rows_for_training_plan(
    rows: list[dict[str, Any]],
    plan: Mapping[str, Any],
) -> list[dict[str, Any]]:
    selected = plan.get("selected_train_ids")
    if not isinstance(selected, list) or not selected or any(not isinstance(value, str) for value in selected):
        raise ValueError("训练计划缺少有效的训练子集编号")
    if len(selected) != len(set(selected)):
        raise ValueError("训练计划包含重复的训练子集编号")
    by_id = {str(row.get("id") or ""): row for row in rows}
    missing = [identifier for identifier in selected if identifier not in by_id]
    if missing:
        raise ValueError(f"训练子集编号不存在：{missing[0]}")
    if _value_hash(selected) != plan.get("subset_sha256"):
        raise ValueError("训练子集编号哈希发生变化")
    return [by_id[identifier] for identifier in selected]


def write_rsage_training_plans(
    plans: Mapping[str, Mapping[str, Any]],
    output_dir: str | Path,
) -> dict[str, Path]:
    if set(plans) != {"sft", "gdpo_rpo"}:
        raise ValueError("必须同时写入 SFT 与 GDPO+RPO 两份训练计划")
    root = Path(output_dir)
    root.mkdir(parents=True, exist_ok=True)
    paths = {
        "sft": root / "sft-plan.json",
        "gdpo_rpo": root / "gdpo-rpo-plan.json",
    }
    plan_hashes: dict[str, str] = {}
    for key, path in paths.items():
        payload = json.dumps(dict(plans[key]), ensure_ascii=False, indent=2, sort_keys=True) + "\n"
        _atomic_write_text(path, payload)
        plan_hashes[path.name] = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    manifest_path = root / "manifest.json"
    manifest = {
        "format": "rsage_training_plans_manifest_v1",
        "seed": int(plans["sft"]["seed"]),
        "plan_sha256": plan_hashes,
        "message_zh": "R-SAGE SFT 与 GDPO+RPO 训练计划已冻结",
    }
    _atomic_write_text(
        manifest_path,
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
    )
    return {**paths, "manifest": manifest_path}


def build_training_command(
    stage: str,
    plan_path: str | Path,
    *,
    max_steps: int | None = None,
    resume_from_checkpoint: str | Path | None = None,
) -> list[str]:
    scripts = {
        "sft": "research/vllm-agent-ft-lab/scripts/train_lora.py",
        "gdpo_rpo": "research/vllm-agent-ft-lab/scripts/train_gdpo_rpo.py",
    }
    if stage not in scripts:
        raise ValueError(f"未知训练阶段：{stage}")
    command = [
        "research/vllm-agent-ft-lab/.venv/bin/python",
        scripts[stage],
        "--plan",
        str(plan_path),
    ]
    if max_steps is not None:
        if max_steps <= 0:
            raise ValueError("训练步数上限必须是正整数")
        command.extend(["--max-steps", str(max_steps)])
    if resume_from_checkpoint is not None:
        if stage == "sft":
            raise ValueError("当前 SFT 启动器尚不支持检查点恢复")
        command.extend(["--resume-from-checkpoint", str(resume_from_checkpoint)])
    return command


def _file_hash(path: Path) -> str:
    if not path.exists():
        raise FileNotFoundError(f"训练数据文件不存在：{path}")
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows = [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    identifiers = [str(row.get("id") or "") for row in rows]
    if any(not value for value in identifiers) or len(identifiers) != len(set(identifiers)):
        raise ValueError(f"训练文件存在空编号或重复编号：{path}")
    return rows


def _select_sft_subset(
    rows: list[dict[str, Any]],
    fraction: float,
    *,
    data_seed: int,
) -> tuple[list[str], dict[str, Any]]:
    target = [
        row
        for row in rows
        if str((row.get("metadata") or {}).get("source_kind")) == "teacher_verified_target"
    ]
    retention = [
        row
        for row in rows
        if str((row.get("metadata") or {}).get("source_kind")) == "champion_retention_replay"
    ]
    if target and retention and len(target) + len(retention) == len(rows):
        target_ids = _select_nested_ids(target, fraction, data_seed=data_seed, namespace="目标SFT")
        retention_ids = _select_nested_ids(
            retention,
            fraction,
            data_seed=data_seed,
            namespace="保留回放",
        )
        selected = sorted(
            [*target_ids, *retention_ids],
            key=lambda value: _seeded_id_key(data_seed, "混合SFT", value),
        )
        return selected, {
            "total": len(rows),
            "selected": len(selected),
            "target_total": len(target),
            "target_selected": len(target_ids),
            "retention_total": len(retention),
            "retention_selected": len(retention_ids),
        }
    selected = _select_nested_ids(rows, fraction, data_seed=data_seed, namespace="SFT")
    return selected, {"total": len(rows), "selected": len(selected)}


def _select_nested_ids(
    rows: list[dict[str, Any]],
    fraction: float,
    *,
    data_seed: int,
    namespace: str,
) -> list[str]:
    ordered = sorted(
        (str(row["id"]) for row in rows),
        key=lambda value: _seeded_id_key(data_seed, namespace, value),
    )
    if fraction == 1.0:
        return ordered
    selected_count = max(1, round(len(ordered) * fraction))
    return ordered[:selected_count]


def _seeded_id_key(seed: int, namespace: str, value: str) -> str:
    return _value_hash({"seed": seed, "namespace": namespace, "value": value})


def _value_hash(value: Any) -> str:
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _atomic_write_text(path: Path, content: str) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8")
    temporary.replace(path)
