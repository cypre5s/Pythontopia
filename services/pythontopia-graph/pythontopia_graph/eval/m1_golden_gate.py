"""M1 offline golden gate for learner/debug loops and route safety cases."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

from pythontopia_graph.schemas.loop_contract import LoopType

DEFAULT_M1_GOLDEN_DATASET = (
    Path(__file__).resolve().parent / "golden" / "agentic_m1_golden.jsonl"
)
DATASET_ID = "agentic-m1-golden-v1"
POLICY_VERSION = "agentic_loop_v1"
CANARY_TRAFFIC_FRACTION = 0.05

M1Category = Literal["learner", "debug", "route", "safety", "leakage"]


class M1Model(BaseModel):
    model_config = ConfigDict(extra="forbid")


class GoldenExpected(M1Model):
    required_evidence_refs: list[str] = Field(default_factory=list)
    required_action_keywords: list[str] = Field(default_factory=list)
    route: str = Field(min_length=1, max_length=64)
    forbidden_keywords: list[str] = Field(default_factory=list)


class GoldenCandidate(M1Model):
    evidence_refs: list[str] = Field(default_factory=list)
    next_action: str = Field(min_length=1, max_length=512)
    route: str = Field(min_length=1, max_length=64)
    safety_pass: bool = True
    schema_valid: bool = True
    leakage_flags: list[str] = Field(default_factory=list)
    content: str = Field(default="", max_length=2000)
    bounded: bool = True
    replayable_state: bool = True


class GoldenCase(M1Model):
    id: str = Field(min_length=1, max_length=128)
    category: M1Category
    loop_type: LoopType
    policy_version: str = Field(min_length=1, max_length=128)
    trace_id: str = Field(min_length=1, max_length=128)
    input: dict[str, Any] = Field(default_factory=dict)
    expected: GoldenExpected
    candidate: GoldenCandidate


class CaseFailure(M1Model):
    case_id: str
    category: M1Category
    loop_type: LoopType
    reasons: list[str]


class CaseResult(M1Model):
    case_id: str
    category: M1Category
    loop_type: LoopType
    passed: bool
    score: float
    reasons: list[str] = Field(default_factory=list)


class CanaryMetadata(M1Model):
    policy_version: str
    rollout_stage: Literal["CANARY_METADATA_ONLY"]
    traffic_fraction: float
    metadata_only: bool
    real_traffic_enabled: bool
    one_variable_only: bool
    cohort: str
    rollback_rules: list[str]


class M1GoldenGateReport(M1Model):
    dataset_id: str
    policy_version: str
    total_cases: int
    category_counts: dict[str, int]
    passed: bool
    pass_rate: float
    case_results: list[CaseResult]
    case_failures: list[CaseFailure]
    canary: CanaryMetadata


def load_m1_golden_cases(path: str | Path = DEFAULT_M1_GOLDEN_DATASET) -> list[GoldenCase]:
    file_path = Path(path)
    cases: list[GoldenCase] = []
    for lineno, raw_line in enumerate(
        file_path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        line = raw_line.strip()
        if not line:
            continue
        try:
            cases.append(GoldenCase.model_validate(json.loads(line)))
        except Exception as exc:  # noqa: BLE001 - offline data errors need file/line context
            raise ValueError(f"{file_path}:{lineno}: invalid M1 golden case: {exc}") from exc
    return cases


def run_m1_golden_gate(path: str | Path = DEFAULT_M1_GOLDEN_DATASET) -> M1GoldenGateReport:
    cases = load_m1_golden_cases(path)
    results = [_evaluate_case(case) for case in cases]
    failures = [
        CaseFailure(
            case_id=result.case_id,
            category=result.category,
            loop_type=result.loop_type,
            reasons=result.reasons,
        )
        for result in results
        if not result.passed
    ]
    counts = Counter(case.category for case in cases)
    passed = bool(cases) and not failures and _coverage_ok(counts)
    pass_rate = (
        round(sum(1 for result in results if result.passed) / len(results), 6)
        if results
        else 0.0
    )
    return M1GoldenGateReport(
        dataset_id=DATASET_ID,
        policy_version=POLICY_VERSION,
        total_cases=len(cases),
        category_counts={category: counts.get(category, 0) for category in _required_categories()},
        passed=passed,
        pass_rate=pass_rate,
        case_results=results,
        case_failures=failures,
        canary=canary_5_metadata(POLICY_VERSION),
    )


def canary_5_metadata(policy_version: str = POLICY_VERSION) -> CanaryMetadata:
    return CanaryMetadata(
        policy_version=policy_version,
        rollout_stage="CANARY_METADATA_ONLY",
        traffic_fraction=CANARY_TRAFFIC_FRACTION,
        metadata_only=True,
        real_traffic_enabled=False,
        one_variable_only=True,
        cohort="eligible-active-students-placeholder",
        rollback_rules=[
            "rollback if offline pass_rate < 1.0",
            "rollback if schema_validity < 1.0",
            "rollback if leakage_flags are observed",
            "rollback if cost_ratio exceeds 1.20 in a future live canary",
        ],
    )


def _evaluate_case(case: GoldenCase) -> CaseResult:
    reasons: list[str] = []
    candidate = case.candidate
    expected = case.expected

    forbidden_hits = _forbidden_hits(candidate.content, expected.forbidden_keywords)
    if candidate.leakage_flags or forbidden_hits:
        reasons.append(
            "leakage blocked: "
            + ", ".join([*candidate.leakage_flags, *[f"keyword:{hit}" for hit in forbidden_hits]])
        )
    if not candidate.safety_pass:
        reasons.append("safety_pass must be true")
    if not candidate.schema_valid:
        reasons.append("schema_valid must be true")
    if candidate.route != expected.route:
        reasons.append(f"route mismatch: expected {expected.route}, got {candidate.route}")
    missing_evidence = sorted(set(expected.required_evidence_refs) - set(candidate.evidence_refs))
    if missing_evidence:
        reasons.append(f"missing evidence refs: {', '.join(missing_evidence)}")
    missing_keywords = _missing_keywords(candidate.next_action, expected.required_action_keywords)
    if missing_keywords:
        reasons.append(f"missing action keywords: {', '.join(missing_keywords)}")
    if not candidate.bounded:
        reasons.append("bounded loop flag must be true")
    if not candidate.replayable_state:
        reasons.append("replayable_state must be true")
    if case.policy_version != POLICY_VERSION:
        reasons.append(f"policy_version must be {POLICY_VERSION}")

    score_parts = [
        1.0 if not candidate.leakage_flags and not forbidden_hits else 0.0,
        1.0 if candidate.safety_pass else 0.0,
        1.0 if candidate.schema_valid else 0.0,
        _coverage(expected.required_evidence_refs, candidate.evidence_refs),
        _coverage(
            expected.required_action_keywords,
            _matched_keywords(candidate.next_action, expected.required_action_keywords),
        ),
        1.0 if candidate.route == expected.route else 0.0,
        1.0 if candidate.bounded and candidate.replayable_state else 0.0,
    ]
    return CaseResult(
        case_id=case.id,
        category=case.category,
        loop_type=case.loop_type,
        passed=not reasons,
        score=round(sum(score_parts) / len(score_parts), 6),
        reasons=reasons,
    )


def _required_categories() -> tuple[M1Category, ...]:
    return ("learner", "debug", "route", "safety", "leakage")


def _coverage_ok(counts: Counter[str]) -> bool:
    return (
        counts.get("learner", 0) >= 3
        and counts.get("debug", 0) >= 3
        and counts.get("route", 0) >= 2
        and counts.get("safety", 0) >= 2
        and counts.get("leakage", 0) >= 2
    )


def _coverage(required: list[str], observed: list[str]) -> float:
    if not required:
        return 1.0
    observed_set = set(observed)
    return round(
        sum(1 for item in set(required) if item in observed_set) / len(set(required)), 6
    )


def _matched_keywords(text: str, keywords: list[str]) -> list[str]:
    lowered = text.lower()
    return [keyword for keyword in keywords if keyword.lower() in lowered]


def _missing_keywords(text: str, keywords: list[str]) -> list[str]:
    matched = set(_matched_keywords(text, keywords))
    return [keyword for keyword in keywords if keyword not in matched]


def _forbidden_hits(text: str, forbidden_keywords: list[str]) -> list[str]:
    lowered = text.lower()
    return [keyword for keyword in forbidden_keywords if keyword.lower() in lowered]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run the M1 agentic golden gate")
    parser.add_argument("dataset", nargs="?", default=str(DEFAULT_M1_GOLDEN_DATASET))
    args = parser.parse_args(argv)

    report = run_m1_golden_gate(args.dataset)
    print(json.dumps(report.model_dump(), ensure_ascii=False, indent=2))
    return 0 if report.passed else 1


if __name__ == "__main__":  # pragma: no cover - CLI entry
    raise SystemExit(main())
