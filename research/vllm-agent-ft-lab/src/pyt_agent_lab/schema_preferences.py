from __future__ import annotations

import hashlib
import json
import random
from collections import Counter, defaultdict, deque
from pathlib import Path
from typing import Any, Iterable

from .fallback import build_fallback_response
from .scoring import AGENT_SCHEMA, score_case_response


def build_schema_preference_dataset(
    *,
    source_cases: list[dict[str, Any]],
    protected_cases: list[dict[str, Any]],
    failure_records: list[dict[str, Any]],
    target_count: int = 300,
    seed: int = 20260710,
) -> list[dict[str, Any]]:
    if not 200 <= target_count <= 500:
        raise ValueError("target_count must be between 200 and 500")
    protected_ids = {str(case["id"]) for case in protected_cases}
    protected_fingerprints = {_case_fingerprint(case) for case in protected_cases}
    protected_source_ids = {
        str(case.get("source_case_id")) for case in protected_cases if case.get("source_case_id")
    }
    eligible = [
        case
        for case in source_cases
        if str(case["id"]) not in protected_ids
        and _case_fingerprint(case) not in protected_fingerprints
        and str(case.get("source_case_id") or "") not in protected_source_ids
        and str(case.get("agent", "")) in AGENT_SCHEMA
    ]
    eligible = _dedupe_cases(eligible)
    if len(eligible) < target_count:
        raise ValueError(f"only {len(eligible)} uncontaminated source cases for target_count={target_count}")

    field_targets = _schema_failure_targets(failure_records)
    selected = _balanced_select(eligible, target_count, seed)
    examples = []
    for index, case in enumerate(selected):
        agent = str(case["agent"])
        field, observed_failure_count = _next_target(field_targets, agent, index)
        chosen = build_fallback_response(case, agent, ["schema_preference_chosen"])
        chosen_score = score_case_response(case, chosen)
        if not chosen_score.contract_pass:
            raise ValueError(f"chosen response failed contract for {case['id']}: {chosen_score.as_row()}")
        rejected, mutation = _make_schema_rejected(case, chosen, field, index)
        rejected_score = score_case_response(case, rejected)
        if not rejected_score.json_valid or rejected_score.schema_valid:
            raise ValueError(f"rejected response is not a targeted schema failure for {case['id']}")
        if rejected_score.forbidden_leak:
            raise ValueError(f"rejected response introduced forbidden leak for {case['id']}")
        examples.append(
            {
                "id": f"schema-pref-{index + 1:04d}-{case['id']}",
                "prompt": [
                    {"role": "system", "content": str(case.get("system", ""))},
                    {"role": "user", "content": str(case.get("user", ""))},
                ],
                "chosen": chosen,
                "rejected": rejected,
                "metadata": {
                    "source_case_id": str(case["id"]),
                    "source_case_fingerprint": _case_fingerprint(case),
                    "agent": agent,
                    "chapter": case.get("chapter"),
                    "schema_target_field": field,
                    "schema_mutation": mutation,
                    "observed_failure_count": observed_failure_count,
                    "chosen_contract_pass": True,
                    "rejected_schema_valid": False,
                    "protected_eval_overlap": False,
                },
            }
        )
    _assert_unique_examples(examples)
    return examples


def split_and_write_schema_preferences(
    examples: list[dict[str, Any]],
    output_dir: str | Path,
    *,
    heldout_ratio: float = 0.1,
    seed: int = 20260710,
    protected_paths: Iterable[str | Path] = (),
    source_path: str | Path | None = None,
) -> dict[str, Any]:
    if not 0 < heldout_ratio < 1:
        raise ValueError("heldout_ratio must be between 0 and 1")
    shuffled = list(examples)
    random.Random(seed).shuffle(shuffled)
    heldout_count = max(1, round(len(shuffled) * heldout_ratio))
    split = {
        "train": sorted(shuffled[heldout_count:], key=lambda item: item["id"]),
        "heldout": sorted(shuffled[:heldout_count], key=lambda item: item["id"]),
    }
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    for name, rows in split.items():
        _write_jsonl(target / f"{name}.jsonl", rows)
    protected = [Path(path) for path in protected_paths]
    manifest = {
        "purpose": "schema_failure_targeted_preference_optimization",
        "total_count": len(examples),
        "train_count": len(split["train"]),
        "heldout_count": len(split["heldout"]),
        "seed": seed,
        "source_path": str(source_path) if source_path else None,
        "source_sha256": _sha256_file(Path(source_path)) if source_path else None,
        "protected_evaluation_paths": [str(path) for path in protected],
        "protected_evaluation_sha256": {str(path): _sha256_file(path) for path in protected},
        "protected_overlap_count": sum(
            bool(example["metadata"].get("protected_eval_overlap")) for example in examples
        ),
        "train_sha256": _sha256_file(target / "train.jsonl"),
        "heldout_sha256": _sha256_file(target / "heldout.jsonl"),
        "agents": dict(sorted(Counter(example["metadata"]["agent"] for example in examples).items())),
        "schema_targets": dict(
            sorted(Counter(example["metadata"]["schema_target_field"] for example in examples).items())
        ),
        "mutation_types": dict(
            sorted(Counter(example["metadata"]["schema_mutation"] for example in examples).items())
        ),
        "contamination_policy": (
            "Exclude protected exact IDs, prompt fingerprints, and source_case_id lineage before selection."
        ),
    }
    if manifest["protected_overlap_count"]:
        raise ValueError("protected evaluation contamination detected")
    (target / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8"
    )
    return manifest


def load_cases(paths: Iterable[str | Path]) -> list[dict[str, Any]]:
    rows = []
    for path in paths:
        rows.extend(_read_jsonl(path))
    return rows


def load_failure_records(paths: Iterable[str | Path]) -> list[dict[str, Any]]:
    rows = []
    for path in paths:
        for record in _read_jsonl(path):
            case = record.get("case", {})
            response = str(record.get("response", ""))
            score = score_case_response(case, response)
            if score.json_valid and not score.schema_valid:
                rows.append(
                    {
                        "agent": str(case.get("agent", "")),
                        "missing_schema_keys": list(score.missing_schema_keys),
                        "schema_type_errors": list(score.schema_type_errors),
                    }
                )
    return rows


def _schema_failure_targets(records: list[dict[str, Any]]) -> dict[str, deque[tuple[str, int]]]:
    counts: dict[str, Counter[str]] = defaultdict(Counter)
    for record in records:
        agent = str(record.get("agent", ""))
        counts[agent].update(str(key) for key in record.get("missing_schema_keys", []))
        counts[agent].update(str(key) for key in record.get("schema_type_errors", []))
    targets: dict[str, deque[tuple[str, int]]] = {}
    for agent, schema in AGENT_SCHEMA.items():
        fields = list(schema["required"])
        ranked = sorted(
            ((field, counts[agent][field]) for field in fields),
            key=lambda item: (-item[1], item[0]),
        )
        targets[agent] = deque(ranked)
    return targets


def _next_target(targets: dict[str, deque[tuple[str, int]]], agent: str, index: int) -> tuple[str, int]:
    queue = targets[agent]
    field, count = queue[0]
    queue.rotate(-1)
    return field, count


def _make_schema_rejected(case: dict[str, Any], chosen: str, field: str, index: int) -> tuple[str, str]:
    parsed = json.loads(chosen)
    expected_type = AGENT_SCHEMA[str(case["agent"])]["required"][field]
    use_type_error = index % 2 == 1 or (field == "evidence_ids" and bool(case.get("evidence_ids")))
    if use_type_error:
        parsed[field] = _wrong_type_value(expected_type, case)
        mutation = "wrong_type"
    else:
        parsed.pop(field, None)
        mutation = "missing_field"
    return json.dumps(parsed, ensure_ascii=False, sort_keys=True), mutation


def _wrong_type_value(expected_type: Any, case: dict[str, Any]) -> Any:
    if expected_type is list:
        evidence = [str(item) for item in case.get("evidence_ids", [])]
        return " ".join(evidence) if evidence else "should_be_array"
    if expected_type is str:
        return ["should", "be", "string"]
    if expected_type is bool:
        return "false"
    if expected_type == (int, float):
        return "high"
    return None


def _balanced_select(cases: list[dict[str, Any]], target_count: int, seed: int) -> list[dict[str, Any]]:
    rng = random.Random(seed)
    by_agent: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for case in cases:
        by_agent[str(case["agent"])].append(case)
    for rows in by_agent.values():
        rng.shuffle(rows)
    agents = sorted(by_agent)
    selected = []
    cursors = Counter()
    while len(selected) < target_count:
        made_progress = False
        for agent in agents:
            cursor = cursors[agent]
            if cursor < len(by_agent[agent]) and len(selected) < target_count:
                selected.append(by_agent[agent][cursor])
                cursors[agent] += 1
                made_progress = True
        if not made_progress:
            break
    return selected


def _case_fingerprint(case: dict[str, Any]) -> str:
    identity = {
        "agent": case.get("agent"),
        "system": case.get("system"),
        "user": case.get("user"),
        "evidence_ids": case.get("evidence_ids", []),
    }
    return hashlib.sha256(
        json.dumps(identity, ensure_ascii=False, sort_keys=True).encode("utf-8")
    ).hexdigest()


def _dedupe_cases(cases: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen = set()
    unique = []
    for case in cases:
        fingerprint = _case_fingerprint(case)
        if fingerprint not in seen:
            seen.add(fingerprint)
            unique.append(case)
    return unique


def _assert_unique_examples(examples: list[dict[str, Any]]) -> None:
    ids = [example["id"] for example in examples]
    prompt_pairs = [
        json.dumps(example["prompt"], ensure_ascii=False, sort_keys=True) for example in examples
    ]
    if len(ids) != len(set(ids)) or len(prompt_pairs) != len(set(prompt_pairs)):
        raise ValueError("duplicate preference example detected")


def _read_jsonl(path: str | Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def _write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
