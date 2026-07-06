from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def load_training_plan(path: str | Path) -> dict[str, Any]:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def example_to_training_text(example: dict[str, Any], tokenizer: Any) -> str:
    return str(tokenizer.apply_chat_template(example["messages"], tokenize=False, add_generation_prompt=False))


def require_dependency(name: str) -> None:
    try:
        __import__(name)
    except ModuleNotFoundError as exc:
        raise RuntimeError(f"missing required training dependency: {name}") from exc
