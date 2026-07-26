from __future__ import annotations

import csv
import hashlib
import json
import random
from pathlib import Path
from typing import Any, Mapping


INTENT_DEFINITIONS = {
    "READ": "先阅读或重读允许证据，定位关键句。",
    "REVIEW": "复盘已有误区、概念边界或先修知识。",
    "TRACE": "追踪程序执行、变量或控制流变化。",
    "SCAFFOLD": "提供分步支架、骨架或渐进提示。",
    "PRACTICE": "安排最小练习或迁移任务。",
    "ASK": "追问学生的判断、证据或缺失上下文。",
    "CHECK": "检查提交、解释或达到停止条件。",
}


def export_paired_semantic_adjudication(
    model_responses: Mapping[str, list[dict[str, Any]]],
    output_dir: str | Path,
    *,
    seed: int = 20260712,
) -> dict[str, str]:
    if len(model_responses) != 2:
        raise ValueError("paired semantic adjudication requires exactly two models")
    names = list(model_responses)
    by_model = {
        name: {str(row["case"]["id"]): row for row in rows}
        for name, rows in model_responses.items()
    }
    case_ids = set(by_model[names[0]])
    if set(by_model[names[1]]) != case_ids:
        raise ValueError("paired response files must contain identical case IDs")

    rng = random.Random(seed)
    ordered_ids = sorted(case_ids)
    rng.shuffle(ordered_ids)
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    item_rows: list[dict[str, Any]] = []
    key_rows: list[dict[str, Any]] = []
    for index, case_id in enumerate(ordered_ids, start=1):
        left, right = names
        if rng.random() < 0.5:
            left, right = right, left
        case = by_model[left][case_id]["case"]
        item_id = f"semantic-{index:04d}"
        item_rows.append(
            {
                "review_item_id": item_id,
                "case_id": case_id,
                "chapter": case.get("chapter", ""),
                "kc": case.get("kc", ""),
                "learner_profile": case.get("learner_profile", ""),
                "learning_signal": case.get("learning_signal", ""),
                "misconception_signal": case.get("misconception_signal", ""),
                "task_focus": case.get("task_focus", ""),
                "allowed_evidence": json.dumps(case.get("evidence_ids", []), ensure_ascii=False),
                "response_a": by_model[left][case_id].get("response", ""),
                "response_b": by_model[right][case_id].get("response", ""),
                "acceptable_intents": "",
                "primary_intent": "",
                "preferred_response": "",
                "confidence_1_to_3": "",
                "review_notes": "",
            }
        )
        key_rows.append(
            {
                "review_item_id": item_id,
                "case_id": case_id,
                "model_a": left,
                "model_b": right,
                "legacy_rule_intent": case.get("expected_event_intent", ""),
            }
        )

    items_csv = target / "semantic_review_items.csv"
    items_jsonl = target / "semantic_review_items.jsonl"
    key_csv = target / "semantic_review_answer_key.csv"
    rubric = target / "semantic_review_rubric.md"
    manifest = target / "manifest.json"
    _write_csv(items_csv, item_rows)
    _write_csv(key_csv, key_rows)
    _write_jsonl(items_jsonl, item_rows)
    rubric.write_text(_rubric(), encoding="utf-8")
    manifest.write_text(
        json.dumps(
            {
                "purpose": "pre-promotion semantic label adjudication; not the final blind-review gate",
                "seed": seed,
                "case_count": len(item_rows),
                "models": sorted(names),
                "items_sha256": _sha256(items_jsonl),
                "answer_key_sha256": _sha256(key_csv),
                "label_policy": "annotate admissible intent set before revealing model identity",
            },
            ensure_ascii=False,
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    return {
        "items_csv": str(items_csv),
        "items_jsonl": str(items_jsonl),
        "answer_key": str(key_csv),
        "rubric": str(rubric),
        "manifest": str(manifest),
    }


def _rubric() -> str:
    definitions = "\n".join(f"- `{key}`: {value}" for key, value in INTENT_DEFINITIONS.items())
    return f"""# GuideCoach Semantic Adjudication Rubric

This is a pre-promotion label-quality audit, not the final full-evaluation blind review.
Do not open the answer key until every row is complete.

For each item:

1. Read only the learner context and task focus. Before comparing responses, fill
   `acceptable_intents` with every defensible intent, comma-separated.
2. Select one `primary_intent`; use the most direct next teaching action, not a downstream action.
3. Compare response A and B for intent fit, evidence boundary, safety, and action usefulness.
4. Fill `preferred_response` with `A`, `B`, `TIE`, or `NEITHER`.
5. Confidence: 1=ambiguous, 2=moderate, 3=clear. Explain confidence 1 or any NEITHER.

Intent definitions:

{definitions}

Invalid review rows: missing primary intent, primary not in acceptable set, or preferred response
outside A/B/TIE/NEITHER. At least two independent reviewers are required before labels can replace
the conservative strict rule in promotion scoring.
"""


def _write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    if not rows:
        raise ValueError("cannot export empty semantic adjudication pack")
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def _write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()
