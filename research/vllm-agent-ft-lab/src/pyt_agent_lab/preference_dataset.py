from __future__ import annotations

import json
import random
from collections import Counter
from pathlib import Path
from typing import Any

from .response_audit import audit_response_records


def build_preference_examples(
    *,
    chosen_records: list[dict[str, Any]],
    rejected_records: list[dict[str, Any]],
    chosen_source: str,
    rejected_source: str,
) -> list[dict[str, Any]]:
    chosen_by_case = _audited_by_case(chosen_records)
    rejected_by_case = _audited_by_case(rejected_records)
    examples: list[dict[str, Any]] = []
    for case_id in sorted(chosen_by_case.keys() & rejected_by_case.keys()):
        chosen_record, chosen_flags = chosen_by_case[case_id]
        rejected_record, rejected_flags = rejected_by_case[case_id]
        if chosen_flags or not rejected_flags:
            continue
        case = chosen_record["case"]
        examples.append(
            {
                "id": f"pref-{case_id}",
                "prompt": [
                    {"role": "system", "content": str(case.get("system", ""))},
                    {"role": "user", "content": str(case.get("user", ""))},
                ],
                "chosen": str(chosen_record.get("response", "")),
                "rejected": str(rejected_record.get("response", "")),
                "metadata": {
                    "source_case_id": case_id,
                    "agent": str(case.get("agent", "")),
                    "task_type": str(case.get("task_type", "")),
                    "requires_evidence": bool(case.get("requires_evidence", False)),
                    "evidence_ids": list(case.get("evidence_ids", [])),
                    "chosen_source": chosen_source,
                    "rejected_source": rejected_source,
                    "rejected_flags": rejected_flags,
                },
            }
        )
    return examples


def split_preference_examples(
    examples: list[dict[str, Any]], heldout_ratio: float, seed: int
) -> dict[str, list[dict[str, Any]]]:
    if not 0 < heldout_ratio < 1:
        raise ValueError("heldout_ratio must be between 0 and 1")
    shuffled = list(examples)
    random.Random(seed).shuffle(shuffled)
    heldout_count = max(1, round(len(shuffled) * heldout_ratio)) if shuffled else 0
    heldout = sorted(shuffled[:heldout_count], key=lambda item: item["id"])
    train = sorted(shuffled[heldout_count:], key=lambda item: item["id"])
    return {"train": train, "heldout": heldout}


def write_preference_dataset(
    split: dict[str, list[dict[str, Any]]], output_dir: str | Path
) -> dict[str, Any]:
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    for split_name, examples in split.items():
        _write_jsonl(target / f"{split_name}.jsonl", examples)
    manifest = _manifest(split)
    (target / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    return manifest


def _audited_by_case(
    records: list[dict[str, Any]]
) -> dict[str, tuple[dict[str, Any], list[str]]]:
    audits = audit_response_records(records)
    by_case: dict[str, tuple[dict[str, Any], list[str]]] = {}
    for record, audit in zip(records, audits, strict=True):
        by_case[str(record["case"]["id"])] = (record, list(audit["flags"]))
    return by_case


def _write_jsonl(path: Path, examples: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for example in examples:
            handle.write(json.dumps(example, ensure_ascii=False, sort_keys=True) + "\n")


def _manifest(split: dict[str, list[dict[str, Any]]]) -> dict[str, Any]:
    all_examples = [example for examples in split.values() for example in examples]
    agent_counts = Counter(
        str(example.get("metadata", {}).get("agent", "")) for example in all_examples
    )
    flag_counts: Counter[str] = Counter()
    for example in all_examples:
        flag_counts.update(example.get("metadata", {}).get("rejected_flags", []))
    return {
        "format": "chat_preference_jsonl",
        "train_count": len(split.get("train", [])),
        "heldout_count": len(split.get("heldout", [])),
        "total_count": len(all_examples),
        "agents": dict(sorted(agent_counts.items())),
        "flag_counts": dict(sorted(flag_counts.items())),
        "input_policy": "original_case_system_user",
        "chosen_policy": "clean_e1_or_guarded_response",
        "rejected_policy": "audited_raw_baseline_or_raw_adapter_failure",
    }
