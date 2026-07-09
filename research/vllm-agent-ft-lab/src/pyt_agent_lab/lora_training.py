from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def load_training_plan(path: str | Path) -> dict[str, Any]:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def example_to_training_text(example: dict[str, Any], tokenizer: Any) -> str:
    return str(tokenizer.apply_chat_template(example["messages"], tokenize=False, add_generation_prompt=False))


def example_to_prompt_completion(example: dict[str, Any], tokenizer: Any) -> dict[str, str]:
    messages = list(example["messages"])
    if not messages or messages[-1].get("role") != "assistant":
        raise ValueError("SFT example must end with an assistant message")
    prompt_messages = messages[:-1]
    completion = str(messages[-1]["content"])
    prompt = str(tokenizer.apply_chat_template(prompt_messages, tokenize=False, add_generation_prompt=True))
    return {
        "prompt": prompt,
        "completion": completion,
    }


def sft_loss_masking_config_kwargs(plan: dict[str, Any]) -> dict[str, bool]:
    mode = _loss_masking_mode(plan)
    if mode in {"", "full_text"}:
        return {}
    if mode == "assistant_only":
        return {
            "completion_only_loss": True,
            "assistant_only_loss": False,
        }
    raise ValueError(f"unsupported SFT loss masking mode: {mode}")


def should_use_prompt_completion_sft_dataset(plan: dict[str, Any]) -> bool:
    return _loss_masking_mode(plan) == "assistant_only"


def require_dependency(name: str) -> None:
    try:
        __import__(name)
    except ModuleNotFoundError as exc:
        raise RuntimeError(f"missing required training dependency: {name}") from exc


def _loss_masking_mode(plan: dict[str, Any]) -> str:
    config = plan.get("loss_masking") or plan.get("training", {}).get("loss_masking") or {}
    if isinstance(config, str):
        return config
    return str(config.get("mode", "") or "")
