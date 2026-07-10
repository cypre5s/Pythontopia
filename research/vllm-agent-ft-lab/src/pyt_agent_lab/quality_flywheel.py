from __future__ import annotations

import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable, Mapping

from .fallback import build_fallback_response
from .scoring import AGENT_SCHEMA, CaseScore, score_case_response


def contract_pass(score: CaseScore | Mapping[str, Any]) -> bool:
    if isinstance(score, CaseScore):
        return score.contract_pass
    return (
        _truthy(score.get("json_valid"))
        and _truthy(score.get("schema_valid"))
        and _truthy(score.get("grounding_safe"))
        and not _truthy(score.get("forbidden_leak"))
    )


def build_layered_record(record: dict[str, Any]) -> dict[str, Any]:
    case = dict(record["case"])
    raw_response = str(record.get("response", ""))
    raw_score = score_case_response(case, raw_response)

    guard_response = raw_response
    guard_score = raw_score
    guard_repaired = False
    repair_actions: list[str] = []
    if not raw_score.contract_pass:
        repaired, repair_actions = repair_contract_response(case, raw_response)
        if repaired is not None:
            guard_response = repaired
            guard_score = score_case_response(case, repaired)
            guard_repaired = guard_score.contract_pass and repaired != raw_response

    fallback_used = not guard_score.contract_pass
    final_response = guard_response
    if fallback_used:
        final_response = build_fallback_response(
            case,
            str(case.get("agent", "")),
            _failure_categories(guard_score),
        )
    final_score = score_case_response(case, final_response)

    return {
        "case_id": str(case["id"]),
        "agent": str(case.get("agent", "")),
        "case": case,
        "raw_response": raw_response,
        "guard_response": guard_response,
        "final_response": final_response,
        "raw_score": raw_score.as_row(),
        "guard_score": guard_score.as_row(),
        "final_score": final_score.as_row(),
        "raw_failures": _failure_categories(raw_score),
        "guard_failures": _failure_categories(guard_score),
        "repair_actions": repair_actions,
        "guard_repaired": guard_repaired,
        "fallback_used": fallback_used,
    }


def repair_contract_response(case: dict[str, Any], response: str) -> tuple[str | None, list[str]]:
    """Deterministically repair parseable schema/grounding defects.

    Invalid JSON and unsafe content deliberately go to the separate fallback layer;
    counting those as a guard repair would hide raw-model failures.
    """
    try:
        parsed = json.loads(response)
    except json.JSONDecodeError:
        return None, ["unrepairable_invalid_json"]
    if not isinstance(parsed, dict):
        return None, ["unrepairable_non_object_json"]
    raw_score = score_case_response(case, response)
    if raw_score.forbidden_leak:
        return None, ["unrepairable_forbidden_leak"]

    repaired = dict(parsed)
    actions: list[str] = []
    required = AGENT_SCHEMA.get(str(case.get("agent", "")), {}).get("required", {})
    for key, expected_type in required.items():
        if key not in repaired:
            repaired[key] = _safe_default(case, key, expected_type)
            actions.append(f"add_missing:{key}")
            continue
        if not isinstance(repaired[key], expected_type):
            repaired[key] = _coerce(repaired[key], expected_type)
            actions.append(f"coerce_type:{key}")

    evidence_ids = [str(item) for item in case.get("evidence_ids", [])]
    if "evidence_ids" in required or evidence_ids:
        if repaired.get("evidence_ids") != evidence_ids:
            repaired["evidence_ids"] = evidence_ids
            actions.append("normalize:evidence_ids")
    if not evidence_ids:
        if repaired.get("grounding_note") != "common_knowledge":
            repaired["grounding_note"] = "common_knowledge"
            actions.append("normalize:common_knowledge")
    elif "grounding_note" not in repaired:
        repaired["grounding_note"] = "courseware_evidence"
        actions.append("add:grounding_note")

    repaired_response = json.dumps(repaired, ensure_ascii=False, sort_keys=True)
    if not score_case_response(case, repaired_response).contract_pass:
        return None, actions + ["repair_did_not_pass_contract"]
    return repaired_response, actions


def summarize_layers(records: list[dict[str, Any]]) -> dict[str, Any]:
    total = len(records)
    safety_sensitive = [record for record in records if _is_safety_sensitive(record["case"])]
    return {
        "case_count": total,
        "primary_metric": "contract_pass_rate",
        "layer_semantics": {
            "raw": "observed model response",
            "guard": "offline deterministic contract-repair replay; not a claim about historical production guard execution",
            "final": "guard result or deterministic fallback replay; final 100% must not be attributed to the raw model",
        },
        "raw": _score_summary(record["raw_score"] for record in records),
        "guard": _score_summary(record["guard_score"] for record in records),
        "final": _score_summary(record["final_score"] for record in records),
        "guard_repair_count": sum(bool(record["guard_repaired"]) for record in records),
        "guard_repair_rate": _ratio(sum(bool(record["guard_repaired"]) for record in records), total),
        "guard_repair_rate_among_raw_failures": _ratio(
            sum(bool(record["guard_repaired"]) for record in records),
            sum(not contract_pass(record["raw_score"]) for record in records),
        ),
        "fallback_count": sum(bool(record["fallback_used"]) for record in records),
        "fallback_rate": _ratio(sum(bool(record["fallback_used"]) for record in records), total),
        "critical_slices": _critical_slice_contract_rates(records),
        "safety_sensitive_forbidden_leak_rate": _ratio(
            sum(_truthy(record["raw_score"].get("forbidden_leak")) for record in safety_sensitive),
            len(safety_sensitive),
        ),
    }


def _critical_slice_contract_rates(records: list[dict[str, Any]]) -> dict[str, float]:
    slices: dict[str, list[dict[str, Any]]] = {
        "evidence_required": [record for record in records if record["case"].get("evidence_ids")],
        "safety_sensitive": [record for record in records if _is_safety_sensitive(record["case"])],
        "beginner_noise": [
            record
            for record in records
            if record["case"].get("beginner_noise_profile") not in {None, "", "clean"}
        ],
        "web_input": [record for record in records if record["case"].get("web_input_type")],
    }
    for agent in ("GuideCoachAgent", "PracticeAgent", "SafetyAgent"):
        slices[f"agent:{agent}"] = [
            record for record in records if record["case"].get("agent") == agent
        ]
    return {
        name: _ratio(sum(contract_pass(record["raw_score"]) for record in items), len(items))
        for name, items in slices.items()
        if items
    }


def _is_safety_sensitive(case: Mapping[str, Any]) -> bool:
    return bool(case.get("forbidden_answer_risk") or case.get("agent") == "SafetyAgent")


def build_failure_matrix(records: list[dict[str, Any]]) -> dict[str, Any]:
    dimensions = {
        "agent": lambda case: str(case.get("agent") or "unknown"),
        "chapter": lambda case: str(case.get("chapter") or "unknown"),
        "beginner_noise": lambda case: str(case.get("beginner_noise_profile") or "clean"),
        "web_input": lambda case: str(case.get("web_input_type") or "non_web"),
        "evidence": lambda case: "required" if case.get("evidence_ids") else "none",
        "safety": lambda case: "sensitive" if _is_safety_sensitive(case) else "standard",
    }
    matrix: dict[str, Any] = {}
    for dimension, classifier in dimensions.items():
        buckets: dict[str, list[dict[str, Any]]] = defaultdict(list)
        for record in records:
            buckets[classifier(record["case"])].append(record)
        matrix[dimension] = {
            key: _failure_bucket(items)
            for key, items in sorted(buckets.items())
        }

    missing: Counter[str] = Counter()
    type_errors: Counter[str] = Counter()
    categories: Counter[str] = Counter()
    for record in records:
        raw_score = record["raw_score"]
        missing.update(str(key) for key in raw_score.get("missing_schema_keys", []))
        type_errors.update(str(key) for key in raw_score.get("schema_type_errors", []))
        categories.update(record["raw_failures"])
    matrix["missing_schema_field"] = dict(sorted(missing.items()))
    matrix["schema_type_error"] = dict(sorted(type_errors.items()))
    matrix["failure_category"] = dict(sorted(categories.items()))
    return matrix


def build_paired_comparison(models: Mapping[str, list[dict[str, Any]]]) -> dict[str, Any]:
    if len(models) < 2:
        raise ValueError("at least two models are required for paired comparison")
    by_model = {
        name: {str(record["case_id"]): record for record in records}
        for name, records in models.items()
    }
    case_sets = {name: set(records) for name, records in by_model.items()}
    expected = next(iter(case_sets.values()))
    mismatch = {
        name: {"missing": sorted(expected - ids), "extra": sorted(ids - expected)}
        for name, ids in case_sets.items()
        if ids != expected
    }
    if mismatch:
        raise ValueError(f"paired evaluation case mismatch: {mismatch}")

    names = list(models)
    pairwise: dict[str, Any] = {}
    for left_index, left in enumerate(names):
        for right in names[left_index + 1 :]:
            wins = losses = contract_ties = score_ties = 0
            for case_id in expected:
                left_score = by_model[left][case_id]["raw_score"]
                right_score = by_model[right][case_id]["raw_score"]
                left_contract = contract_pass(left_score)
                right_contract = contract_pass(right_score)
                if left_contract != right_contract:
                    if left_contract:
                        wins += 1
                    else:
                        losses += 1
                    continue
                contract_ties += 1
                left_aux = float(left_score.get("score", 0.0))
                right_aux = float(right_score.get("score", 0.0))
                if left_aux > right_aux:
                    wins += 1
                elif left_aux < right_aux:
                    losses += 1
                else:
                    score_ties += 1
            pairwise[f"{left}_vs_{right}"] = {
                "left": left,
                "right": right,
                "left_wins": wins,
                "right_wins": losses,
                "contract_ties": contract_ties,
                "full_ties": score_ties,
                "ordering": "contract_pass_then_auxiliary_score",
            }

    return {"case_count": len(expected), "models": names, "pairwise": pairwise}


def paired_case_rows(models: Mapping[str, list[dict[str, Any]]]) -> list[dict[str, Any]]:
    by_model = {
        name: {str(record["case_id"]): record for record in records}
        for name, records in models.items()
    }
    case_ids = sorted(set.intersection(*(set(rows) for rows in by_model.values())))
    rows = []
    for case_id in case_ids:
        model_scores = {name: records[case_id]["raw_score"] for name, records in by_model.items()}
        rows.append(
            {
                "case_id": case_id,
                "agent": next(iter(by_model.values()))[case_id]["agent"],
                "models": model_scores,
                "contract_pass_models": sorted(
                    name for name, score in model_scores.items() if contract_pass(score)
                ),
            }
        )
    return rows


def write_quality_flywheel_report(
    model_response_paths: Mapping[str, str | Path],
    output_dir: str | Path,
    *,
    cases_path: str | Path | None = None,
    prompt_variant: str = "baseline",
    temperature: float = 0.0,
    seed: int = 20260708,
) -> dict[str, Any]:
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    layered_by_model: dict[str, list[dict[str, Any]]] = {}
    summaries: dict[str, Any] = {}
    failure_matrices: dict[str, Any] = {}
    response_hashes: dict[str, str] = {}

    expected_ids = None
    if cases_path is not None:
        expected_cases = _load_jsonl(cases_path)
        expected_ids = [str(case["id"]) for case in expected_cases]

    for model, path in model_response_paths.items():
        source = Path(path)
        response_hashes[model] = _sha256_file(source)
        raw_records = _load_jsonl(source)
        layered = [build_layered_record(record) for record in raw_records]
        if expected_ids is not None and sorted(record["case_id"] for record in layered) != sorted(expected_ids):
            raise ValueError(f"{model} does not contain exactly the expected evaluation cases")
        layered_by_model[model] = layered
        summaries[model] = summarize_layers(layered)
        failure_matrices[model] = build_failure_matrix(layered)
        _write_jsonl(target / f"{model}_layered.jsonl", layered)
        _write_jsonl(
            target / f"{model}_failures.jsonl",
            (record for record in layered if not contract_pass(record["raw_score"])),
        )

    comparison = build_paired_comparison(layered_by_model)
    _write_jsonl(target / "paired_cases.jsonl", paired_case_rows(layered_by_model))
    report = {
        "primary_metric": "contract_pass_rate",
        "auxiliary_metric": "score_mean",
        "claims_policy": (
            "Smoke, small heldout, and guard/fallback-repaired results are not evidence that a model reached 100%."
        ),
        "models": summaries,
        "paired_comparison": comparison,
        "failure_matrices": failure_matrices,
        "protocol": {
            "cases_path": str(cases_path) if cases_path is not None else None,
            "cases_sha256": _sha256_file(Path(cases_path)) if cases_path is not None else None,
            "prompt_variant": prompt_variant,
            "temperature": temperature,
            "seed": seed,
            "max_tokens": "per_case.max_tokens",
            "response_sha256": response_hashes,
            "warning": (
                "This manifest proves case identity and rescoring settings. It does not prove historical "
                "serve commands unless each source run saved its own immutable run manifest."
            ),
        },
    }
    (target / "quality_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8"
    )
    charts = target / "charts"
    charts.mkdir(exist_ok=True)
    _write_contract_chart(charts / "contract_pass_by_model.svg", summaries)
    _write_failure_chart(charts / "raw_failure_categories.svg", failure_matrices)
    return report


def _failure_categories(score: CaseScore) -> list[str]:
    failures = []
    if not score.json_valid:
        failures.append("json_invalid")
    if score.json_valid and not score.schema_valid:
        failures.append("schema_invalid")
    failures.extend(f"missing_schema_field:{key}" for key in score.missing_schema_keys)
    failures.extend(f"schema_type_error:{key}" for key in score.schema_type_errors)
    if not score.grounding_safe:
        failures.append("grounding_unsafe")
    if score.forbidden_leak:
        failures.append("forbidden_leak")
    return failures


def _safe_default(case: dict[str, Any], key: str, expected_type: Any) -> Any:
    if key == "evidence_ids":
        return [str(item) for item in case.get("evidence_ids", [])]
    if expected_type is str:
        return f"guard_repair:{key}"
    if expected_type is list:
        return []
    if expected_type is bool:
        return False
    if expected_type in {(int, float), int, float}:
        return 0.0
    return None


def _coerce(value: Any, expected_type: Any) -> Any:
    if expected_type is list:
        return value if isinstance(value, list) else ([] if value is None or value == "" else [value])
    if expected_type is str:
        if isinstance(value, list):
            return "；".join(str(item) for item in value)
        return str(value)
    if expected_type is bool:
        if isinstance(value, str):
            return value.strip().lower() in {"1", "true", "yes", "是"}
        return bool(value)
    if expected_type == (int, float):
        try:
            return float(value)
        except (TypeError, ValueError):
            return 0.0
    return value


def _score_summary(scores: Iterable[Mapping[str, Any]]) -> dict[str, Any]:
    rows = list(scores)
    count = len(rows)
    return {
        "case_count": count,
        "contract_pass_rate": _ratio(sum(contract_pass(row) for row in rows), count),
        "score_mean": _ratio(sum(float(row.get("score", 0.0)) for row in rows), count),
        "json_valid_rate": _ratio(sum(_truthy(row.get("json_valid")) for row in rows), count),
        "schema_valid_rate": _ratio(sum(_truthy(row.get("schema_valid")) for row in rows), count),
        "grounding_safe_rate": _ratio(sum(_truthy(row.get("grounding_safe")) for row in rows), count),
        "forbidden_leak_rate": _ratio(sum(_truthy(row.get("forbidden_leak")) for row in rows), count),
    }


def _failure_bucket(records: list[dict[str, Any]]) -> dict[str, Any]:
    count = len(records)
    failures = Counter(category for record in records for category in record["raw_failures"])
    return {
        "case_count": count,
        "contract_pass_rate": _ratio(sum(contract_pass(record["raw_score"]) for record in records), count),
        "failure_count": sum(not contract_pass(record["raw_score"]) for record in records),
        "failure_categories": dict(sorted(failures.items())),
    }


def _ratio(numerator: float, denominator: int) -> float:
    return round(numerator / denominator, 6) if denominator else 0.0


def _truthy(value: Any) -> bool:
    if isinstance(value, str):
        return value.lower() in {"1", "true", "yes"}
    return bool(value)


def _load_jsonl(path: str | Path) -> list[dict[str, Any]]:
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


def _write_contract_chart(path: Path, summaries: Mapping[str, Any]) -> None:
    labels = list(summaries)
    values = [float(summaries[label]["raw"]["contract_pass_rate"]) for label in labels]
    _write_simple_bar(path, "Raw contract pass rate (primary)", labels, values)


def _write_failure_chart(path: Path, matrices: Mapping[str, Any]) -> None:
    labels = list(matrices)
    values = [sum(matrix["failure_category"].values()) for matrix in matrices.values()]
    _write_simple_bar(path, "Raw contract failure signals", labels, values)


def _write_simple_bar(path: Path, title: str, labels: list[str], values: list[float]) -> None:
    width = max(640, 140 * max(1, len(labels)))
    height = 360
    baseline = 300
    max_value = max([1.0, *values])
    slot = (width - 120) / max(1, len(labels))
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">',
        '<rect width="100%" height="100%" fill="#fff"/>',
        f'<text x="60" y="32" font-family="Arial" font-size="20">{title}</text>',
        f'<line x1="60" y1="{baseline}" x2="{width - 40}" y2="{baseline}" stroke="#6b7280"/>',
    ]
    for index, (label, value) in enumerate(zip(labels, values, strict=True)):
        bar_height = (value / max_value) * 220
        x = 80 + index * slot
        y = baseline - bar_height
        parts.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{slot * 0.55:.1f}" height="{bar_height:.1f}" fill="#b45309"/>')
        parts.append(f'<text x="{x + slot * 0.275:.1f}" y="{max(y - 6, 48):.1f}" text-anchor="middle" font-family="Arial" font-size="12">{value:.3f}</text>')
        parts.append(f'<text x="{x + slot * 0.275:.1f}" y="326" text-anchor="middle" font-family="Arial" font-size="12">{label}</text>')
    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")
