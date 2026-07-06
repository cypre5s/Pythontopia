from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def load_sft_source_case_ids(path: str | Path) -> list[str]:
    ids: list[str] = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        source_case_id = row.get("metadata", {}).get("source_case_id")
        if source_case_id:
            ids.append(str(source_case_id))
    return ids


def select_cases_by_ids(cases: list[dict[str, Any]], case_ids: list[str]) -> list[dict[str, Any]]:
    by_id = {str(case["id"]): case for case in cases}
    missing = [case_id for case_id in case_ids if case_id not in by_id]
    if missing:
        raise ValueError(f"missing cases: {', '.join(missing[:5])}")
    return [by_id[case_id] for case_id in case_ids]


def generation_token_budget(case: dict[str, Any], default_max_new_tokens: int) -> int:
    try:
        value = int(case.get("max_tokens", default_max_new_tokens))
    except (TypeError, ValueError):
        return default_max_new_tokens
    return value if value > 0 else default_max_new_tokens


def normalize_generation_inputs(value: Any, device: Any) -> tuple[dict[str, Any], int]:
    if hasattr(value, "keys") and "input_ids" in value:
        kwargs = {key: _move_to_device(value[key], device) for key in value.keys()}
        return kwargs, int(kwargs["input_ids"].shape[-1])
    if hasattr(value, "shape"):
        input_ids = _move_to_device(value, device)
        return {"input_ids": input_ids}, int(input_ids.shape[-1])
    raise TypeError(f"unsupported generation input type: {type(value)!r}")


def _move_to_device(value: Any, device: Any) -> Any:
    return value.to(device) if hasattr(value, "to") else value
