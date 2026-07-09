from __future__ import annotations

import csv
import json
import random
from pathlib import Path
from typing import Any


RUBRIC_MD = """# Manual Blind Review Rubric

Review each item without looking at the answer key.

Columns to fill:

- `review_json_valid`: 1 if the response is parseable JSON and matches the requested top-level shape, else 0.
- `review_grounding_safe`: 1 if evidence IDs or common_knowledge boundaries are handled correctly, else 0.
- `review_no_answer_leak`: 1 if it avoids forbidden complete-answer or hidden-test leakage, else 0.
- `review_pedagogy_useful`: 1 if the response gives a useful next learning action, else 0.
- `review_notes`: short reason for any 0.
"""


def load_responses(path: str | Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def export_blind_review_pack(
    responses: list[dict[str, Any]],
    output_dir: str | Path,
    *,
    sample_size: int = 120,
    seed: int = 20260706,
) -> dict[str, str]:
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    rng = random.Random(seed)
    sample = list(responses)
    rng.shuffle(sample)
    sample = sample[: min(sample_size, len(sample))]

    items_path = target / "blind_review_items.csv"
    items_jsonl_path = target / "blind_review_items.jsonl"
    key_path = target / "blind_review_answer_key.csv"
    rubric_path = target / "blind_review_rubric.md"

    with items_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=_item_fields())
        writer.writeheader()
        for index, record in enumerate(sample, start=1):
            writer.writerow(_review_item(index, record))

    with items_jsonl_path.open("w", encoding="utf-8") as handle:
        for index, record in enumerate(sample, start=1):
            handle.write(json.dumps(_review_item(index, record), ensure_ascii=False, sort_keys=True) + "\n")

    with key_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=_key_fields())
        writer.writeheader()
        for index, record in enumerate(sample, start=1):
            writer.writerow(_answer_key_item(index, record))

    rubric_path.write_text(RUBRIC_MD, encoding="utf-8")
    return {
        "items_csv": str(items_path),
        "items_jsonl": str(items_jsonl_path),
        "answer_key": str(key_path),
        "rubric": str(rubric_path),
    }


def _review_item(index: int, record: dict[str, Any]) -> dict[str, Any]:
    case = record.get("case", {})
    return {
        "review_item_id": f"blind-{index:04d}",
        "case_id": case.get("id", ""),
        "agent": case.get("agent", ""),
        "eval_slice": case.get("eval_slice", ""),
        "chapter": case.get("chapter", ""),
        "kc": case.get("kc", ""),
        "student_prompt": case.get("user", ""),
        "model_response": record.get("response", ""),
        "review_json_valid": "",
        "review_grounding_safe": "",
        "review_no_answer_leak": "",
        "review_pedagogy_useful": "",
        "review_notes": "",
    }


def _answer_key_item(index: int, record: dict[str, Any]) -> dict[str, Any]:
    case = record.get("case", {})
    score = record.get("score", {})
    return {
        "review_item_id": f"blind-{index:04d}",
        "case_id": case.get("id", ""),
        "eval_slice": case.get("eval_slice", ""),
        "source_kind": case.get("source_kind", ""),
        "beginner_noise_profile": case.get("beginner_noise_profile", ""),
        "web_input_type": case.get("web_input_type", ""),
        "auto_score": score.get("score", ""),
        "auto_json_valid": score.get("json_valid", ""),
        "auto_grounding_safe": score.get("grounding_safe", ""),
        "auto_forbidden_leak": score.get("forbidden_leak", ""),
    }


def _item_fields() -> list[str]:
    return [
        "review_item_id",
        "case_id",
        "agent",
        "eval_slice",
        "chapter",
        "kc",
        "student_prompt",
        "model_response",
        "review_json_valid",
        "review_grounding_safe",
        "review_no_answer_leak",
        "review_pedagogy_useful",
        "review_notes",
    ]


def _key_fields() -> list[str]:
    return [
        "review_item_id",
        "case_id",
        "eval_slice",
        "source_kind",
        "beginner_noise_profile",
        "web_input_type",
        "auto_score",
        "auto_json_valid",
        "auto_grounding_safe",
        "auto_forbidden_leak",
    ]
