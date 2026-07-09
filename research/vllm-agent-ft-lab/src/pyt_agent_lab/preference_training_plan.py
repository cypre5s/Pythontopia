from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def build_dpo_training_plan(
    *,
    model_path: str,
    sft_adapter_path: str,
    train_file: str,
    heldout_file: str,
    output_dir: str,
    max_seq_length: int = 768,
) -> dict[str, Any]:
    return {
        "method": "dpo_lora",
        "model_path": model_path,
        "sft_adapter_path": sft_adapter_path,
        "train_file": train_file,
        "heldout_file": heldout_file,
        "output_dir": output_dir,
        "max_seq_length": max_seq_length,
        "dpo": {
            "beta": 0.1,
            "loss_type": "sigmoid",
        },
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
            "num_train_epochs": 1,
            "per_device_train_batch_size": 1,
            "per_device_eval_batch_size": 1,
            "gradient_accumulation_steps": 16,
            "learning_rate": 5e-5,
            "warmup_ratio": 0.03,
            "logging_steps": 1,
            "save_strategy": "epoch",
            "eval_strategy": "epoch",
            "gradient_checkpointing": True,
            "bf16": True,
        },
        "notes": [
            "Do not merge guard behavior into labels without preserving the raw rejected response and audit flags.",
            "DPO starts from the existing SFT QLoRA adapter and optimizes preference between chosen/rejected Agent JSON.",
            "Evaluate against SFT adapter, not only heldout preference loss.",
        ],
    }


def build_orpo_training_plan(
    *,
    model_path: str,
    sft_adapter_path: str,
    train_file: str,
    heldout_file: str,
    output_dir: str,
    max_seq_length: int = 512,
    beta: float = 0.1,
    learning_rate: float = 5e-5,
    max_grad_norm: float | None = None,
) -> dict[str, Any]:
    plan = build_dpo_training_plan(
        model_path=model_path,
        sft_adapter_path=sft_adapter_path,
        train_file=train_file,
        heldout_file=heldout_file,
        output_dir=output_dir,
        max_seq_length=max_seq_length,
    )
    plan["method"] = "orpo_lora"
    plan.pop("dpo", None)
    plan["orpo"] = {
        "beta": beta,
    }
    plan["training"]["learning_rate"] = learning_rate
    if max_grad_norm is not None:
        plan["training"]["max_grad_norm"] = max_grad_norm
    plan["notes"] = [
        "ORPO is reference-model-free preference optimization; this run uses TRL experimental ORPOTrainer instead of a custom loss.",
        "It starts from the existing SFT QLoRA adapter and uses the same chosen/rejected preference data as DPO.",
        "Evaluate against SFT adapter and SFT + DPO on identical heldout slices.",
    ]
    return plan


def write_preference_training_plan(plan: dict[str, Any], path: str | Path) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        json.dumps(plan, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )
