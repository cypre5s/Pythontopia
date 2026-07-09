from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Callable, Iterable


TokenStatsFn = Callable[[dict[str, Any]], dict[str, int]]


def should_keep_orpo_row(
    *,
    chosen_label_count: int,
    rejected_label_count: int,
    chosen_length: int,
    rejected_length: int,
    max_seq_length: int,
) -> bool:
    if chosen_label_count <= 0 or rejected_label_count <= 0:
        return False
    return max(chosen_length, rejected_length) <= max_seq_length


def filter_orpo_rows(
    rows: Iterable[dict[str, Any]],
    *,
    token_stats: TokenStatsFn,
    max_seq_length: int,
) -> tuple[list[dict[str, Any]], dict[str, int]]:
    kept: list[dict[str, Any]] = []
    manifest = {
        "total_count": 0,
        "kept_count": 0,
        "dropped_zero_label_count": 0,
        "dropped_overlength_count": 0,
    }
    for row in rows:
        manifest["total_count"] += 1
        stats = token_stats(row)
        has_zero_label = stats["chosen_label_count"] <= 0 or stats["rejected_label_count"] <= 0
        is_overlength = max(stats["chosen_length"], stats["rejected_length"]) > max_seq_length
        if should_keep_orpo_row(
            chosen_label_count=stats["chosen_label_count"],
            rejected_label_count=stats["rejected_label_count"],
            chosen_length=stats["chosen_length"],
            rejected_length=stats["rejected_length"],
            max_seq_length=max_seq_length,
        ):
            kept.append(row)
            manifest["kept_count"] += 1
            continue
        if has_zero_label:
            manifest["dropped_zero_label_count"] += 1
        if is_overlength:
            manifest["dropped_overlength_count"] += 1
    return kept, manifest


def read_jsonl(path: str | Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with Path(path).open(encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                rows.append(json.loads(line))
    return rows


def write_jsonl(rows: Iterable[dict[str, Any]], path: str | Path) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
