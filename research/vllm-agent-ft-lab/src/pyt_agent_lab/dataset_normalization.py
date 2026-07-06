from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Iterable


def normalize_ed_record(record: dict[str, Any], split: str, index: int) -> dict[str, Any]:
    background = record.get("background_info", {}) or {}
    return {
        "id": f"ed-{split}-{index:06d}",
        "source_dataset": "google-education-dialogue-dataset",
        "split": split,
        "dialogue_context": [_normalize_turn(turn) for turn in record.get("conversation", [])],
        "student_state": {
            "topic": background.get("topic", ""),
            "preferences": background.get("student_prefrences") or background.get("student_preferences", ""),
            "reaction": background.get("student_reactions", ""),
        },
        "tutor_action": {
            "teacher_preferences": background.get("teacher_prefrences") or background.get("teacher_preferences", ""),
            "teacher_reactions": background.get("teacher_reactions", ""),
        },
        "target_json": {
            "pedagogy_signal": "teacher_student_dialogue",
            "topic": background.get("topic", ""),
        },
        "license_or_terms_note": "See google-research-datasets/Education-Dialogue-Dataset repository.",
    }


def load_json_records(path: str | Path) -> list[dict[str, Any]]:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if isinstance(data, list):
        return [record for record in data if isinstance(record, dict)]
    if isinstance(data, dict):
        for key in ("data", "records", "examples", "conversations"):
            value = data.get(key)
            if isinstance(value, list):
                return [record for record in value if isinstance(record, dict)]
    raise ValueError(f"unsupported JSON record structure: {path}")


def write_normalized_jsonl(rows: Iterable[dict[str, Any]], path: str | Path) -> int:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with target.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
            count += 1
    return count


def normalize_ed_file(input_path: str | Path, output_path: str | Path, split: str, limit: int | None = None) -> int:
    records = load_json_records(input_path)
    if limit is not None:
        records = records[:limit]
    rows = (normalize_ed_record(record, split=split, index=index) for index, record in enumerate(records))
    return write_normalized_jsonl(rows, output_path)


def _normalize_turn(turn: dict[str, Any]) -> dict[str, str]:
    return {
        "role": str(turn.get("role", "")).strip().lower(),
        "text": _clean_text(str(turn.get("text", ""))),
    }


def _clean_text(value: str) -> str:
    text = value.strip()
    while text.endswith(","):
        text = text[:-1].rstrip()
    while text.endswith('"'):
        text = text[:-1].rstrip()
    return text
