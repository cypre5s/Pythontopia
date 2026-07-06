from __future__ import annotations

import json
import random
from collections import Counter
from pathlib import Path
from typing import Any

from .response_audit import audit_response_records


def build_sft_examples(records: list[dict[str, Any]], source_run: str) -> list[dict[str, Any]]:
    audits = audit_response_records(records)
    clean_case_ids = {audit["case_id"] for audit in audits if not audit["flags"]}
    examples: list[dict[str, Any]] = []
    for record in records:
        case = record["case"]
        case_id = str(case["id"])
        if case_id not in clean_case_ids:
            continue
        examples.append(
            {
                "id": f"sft-{case_id}",
                "messages": [
                    {"role": "system", "content": str(case["system"])},
                    {"role": "user", "content": str(case["user"])},
                    {"role": "assistant", "content": str(record["response"])},
                ],
                "metadata": {
                    "source_run": source_run,
                    "source_case_id": case_id,
                    "agent": str(case["agent"]),
                    "task_type": str(case.get("task_type", "")),
                    "requires_evidence": bool(case.get("requires_evidence", False)),
                    "evidence_ids": list(case.get("evidence_ids", [])),
                },
            }
        )
    return examples


def split_sft_examples(examples: list[dict[str, Any]], heldout_ratio: float, seed: int) -> dict[str, list[dict[str, Any]]]:
    if not 0 < heldout_ratio < 1:
        raise ValueError("heldout_ratio must be between 0 and 1")
    shuffled = list(examples)
    random.Random(seed).shuffle(shuffled)
    heldout_count = max(1, round(len(shuffled) * heldout_ratio)) if shuffled else 0
    heldout = sorted(shuffled[:heldout_count], key=lambda item: item["id"])
    train = sorted(shuffled[heldout_count:], key=lambda item: item["id"])
    return {"train": train, "heldout": heldout}


def write_sft_dataset(split: dict[str, list[dict[str, Any]]], output_dir: str | Path) -> dict[str, Any]:
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    for split_name, examples in split.items():
        _write_jsonl(target / f"{split_name}.jsonl", examples)
    manifest = _manifest(split)
    (target / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    return manifest


def _write_jsonl(path: Path, examples: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for example in examples:
            handle.write(json.dumps(example, ensure_ascii=False, sort_keys=True) + "\n")


def _manifest(split: dict[str, list[dict[str, Any]]]) -> dict[str, Any]:
    all_examples = [example for examples in split.values() for example in examples]
    by_agent = Counter(str(example.get("metadata", {}).get("agent", "")) for example in all_examples)
    return {
        "train_count": len(split.get("train", [])),
        "heldout_count": len(split.get("heldout", [])),
        "total_count": len(all_examples),
        "agents": dict(sorted(by_agent.items())),
        "format": "chat_messages_jsonl",
        "input_policy": "original_case_system_user_without_protocol_patch",
    }
