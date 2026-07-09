from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from .scoring import CaseScore, score_case_response


@dataclass(frozen=True)
class GuardDecision:
    passed: bool
    failures: list[str] = field(default_factory=list)
    score: CaseScore | None = None

    @property
    def failure_count(self) -> int:
        return len(self.failures)


def apply_guard(case: dict[str, Any], response: str) -> GuardDecision:
    """Check a response against all four scoring dimensions.

    Returns GuardDecision.passed=True only when every dimension is clean.
    """
    score = score_case_response(case, response)
    failures: list[str] = []

    if not score.json_valid:
        failures.append("json_invalid")
    if score.json_valid and not score.schema_valid:
        failures.append("schema_invalid")
    if not score.grounding_safe:
        failures.append("grounding_failed")
    if score.forbidden_leak:
        failures.append("forbidden_leak")
    if score.matched_expectations < score.expectation_count:
        failures.append("incomplete_markers")

    return GuardDecision(
        passed=len(failures) == 0,
        failures=failures,
        score=score,
    )


def guard_failure_summary(decision: GuardDecision) -> str:
    """Human-readable summary of guard failures."""
    if decision.passed:
        return "OK"
    return "; ".join(decision.failures)
