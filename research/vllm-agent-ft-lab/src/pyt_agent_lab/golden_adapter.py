from __future__ import annotations

import json
from collections.abc import Iterable
from pathlib import Path
from typing import Any

from .fallback import build_fallback_response
from .guard import apply_guard
from .scoring import AGENT_SCHEMA, score_case_response


AGENT_ORDER = tuple(AGENT_SCHEMA.keys())


def load_jsonl(path: str | Path) -> list[dict[str, Any]]:
    source = Path(path)
    return [json.loads(line) for line in source.read_text(encoding="utf-8").splitlines() if line.strip()]


def write_jsonl(rows: Iterable[dict[str, Any]], path: str | Path) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def select_golden_cases(cases: list[dict[str, Any]], per_agent: int = 5) -> list[dict[str, Any]]:
    selected: list[dict[str, Any]] = []
    for agent in AGENT_ORDER:
        agent_cases = sorted((case for case in cases if case.get("agent") == agent), key=lambda item: str(item["id"]))
        picks = _pick_agent_cases(agent, agent_cases, per_agent)
        for index, case in enumerate(picks, start=1):
            golden_case = dict(case)
            golden_case["golden_index"] = f"{agent}-{index}"
            golden_case["golden_scenario"] = _scenario(golden_case)
            selected.append(golden_case)
    return selected


def build_expected_baseline(cases: list[dict[str, Any]]) -> list[dict[str, Any]]:
    records = []
    for case in cases:
        response = build_fallback_response(case, str(case["agent"]), ["golden_adapter_baseline"])
        score = score_case_response(case, response)
        guard = apply_guard(case, response)
        if not guard.passed:
            raise ValueError(f"golden baseline failed guard for {case['id']}: {guard.failures}")
        records.append(
            {
                "baseline_source": "deterministic_guarded_adapter_v1",
                "case": case,
                "latency_ms": 0,
                "response": response,
                "score": score.as_row(),
            }
        )
    return records


def _pick_agent_cases(agent: str, cases: list[dict[str, Any]], per_agent: int) -> list[dict[str, Any]]:
    picked: list[dict[str, Any]] = []
    if agent in {"SafetyAgent", "PracticeAgent"}:
        _extend_picks(picked, cases, per_agent, lambda case: bool(case.get("forbidden_answer_risk")))
    _extend_picks(picked, cases, per_agent, lambda case: bool(case.get("evidence_ids")))
    _extend_picks(picked, cases, per_agent, lambda case: not case.get("evidence_ids"))
    _extend_picks(picked, cases, per_agent, lambda case: True)
    if len(picked) != per_agent:
        raise ValueError(f"expected {per_agent} golden cases for {agent}, got {len(picked)}")
    return picked


def _extend_picks(
    picked: list[dict[str, Any]],
    cases: list[dict[str, Any]],
    per_agent: int,
    predicate: Any,
) -> None:
    used_ids = {str(case["id"]) for case in picked}
    for case in cases:
        if len(picked) == per_agent:
            return
        case_id = str(case["id"])
        if case_id in used_ids or not predicate(case):
            continue
        picked.append(case)
        used_ids.add(case_id)


def _scenario(case: dict[str, Any]) -> str:
    if case.get("forbidden_answer_risk") or case.get("agent") == "SafetyAgent":
        return "safety_sensitive"
    if case.get("evidence_ids"):
        return "with_evidence"
    return "without_evidence"
