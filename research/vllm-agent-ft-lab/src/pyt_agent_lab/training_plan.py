from __future__ import annotations

import importlib.metadata
import importlib.util
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class TrainingDependency:
    name: str
    required: bool
    available: bool
    version: str | None


def build_training_plan(
    model_path: str,
    train_file: str,
    heldout_file: str,
    output_dir: str,
    method: str = "qlora_4bit",
    loss_masking_mode: str = "assistant_only",
) -> dict[str, Any]:
    return {
        "method": method,
        "model_path": model_path,
        "train_file": train_file,
        "heldout_file": heldout_file,
        "output_dir": output_dir,
        "max_seq_length": 768,
        "lora": {
            "r": 8,
            "alpha": 16,
            "dropout": 0.05,
            "target_modules": ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        },
        "training": {
            "num_train_epochs": 2,
            "per_device_train_batch_size": 1,
            "per_device_eval_batch_size": 1,
            "gradient_accumulation_steps": 16,
            "learning_rate": 2e-4,
            "warmup_ratio": 0.03,
            "logging_steps": 1,
            "save_strategy": "epoch",
            "eval_strategy": "epoch",
            "gradient_checkpointing": True,
            "bf16": True,
        },
        "loss_masking": {
            "mode": loss_masking_mode,
        },
        "notes": [
            "Designed for an 8GB laptop GPU; use 4-bit QLoRA rather than full fine-tuning.",
            "Training inputs use original case prompts; E1 protocol text is not included in the user/system prompt.",
            "Loss masking defaults to assistant_only so gradients focus on the final structured JSON response.",
            "Held-out evaluation must run through the same scoring/audit pipeline before story claims.",
        ],
    }


def detect_training_dependencies(method: str = "qlora_4bit") -> list[TrainingDependency]:
    required = ["torch", "transformers", "peft", "trl", "datasets", "accelerate"]
    if method == "qlora_4bit":
        required.append("bitsandbytes")
    return [_dependency(name, required=True) for name in required]


def dependency_status(deps: list[TrainingDependency]) -> dict[str, Any]:
    missing_required = [dep.name for dep in deps if dep.required and not dep.available]
    return {
        "ready": not missing_required,
        "missing_required": missing_required,
        "available": {dep.name: dep.version or "unknown" for dep in deps if dep.available},
    }


def write_training_plan(plan: dict[str, Any], path: str | Path) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(plan, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")


def write_preflight_report(plan: dict[str, Any], deps: list[TrainingDependency], output_dir: str | Path) -> dict[str, Any]:
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    status = dependency_status(deps)
    report = {
        "plan": plan,
        "dependency_status": status,
        "dependencies": [dep.__dict__ for dep in deps],
    }
    (target / "training_preflight.json").write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    return report


def _dependency(name: str, required: bool) -> TrainingDependency:
    available = importlib.util.find_spec(name) is not None
    version: str | None = None
    if available:
        try:
            version = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            version = None
    return TrainingDependency(name=name, required=required, available=available, version=version)
