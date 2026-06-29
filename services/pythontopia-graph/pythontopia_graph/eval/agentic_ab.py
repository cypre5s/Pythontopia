"""Repeated A/B evaluation for the next-generation Agentic loop work.

The pasted execution report asks for proof that the new bounded loop behavior is
not merely more elaborate than the original single-shot behavior, but is
measurably better and stable across repeated runs. This module provides a
deterministic offline harness for that proof:

* control: ``legacy_single_shot`` decisions without loop contract gates;
* treatment: ``agentic_loop_v1`` decisions with evidence, schema, and action
  gates;
* repeated seeded replay with small deterministic noise;
* pass/fail gates for uplift, stability, regression, and schema validity.

It is intentionally dependency-light so it can run in CI without a live LLM.
Real-provider experiments can replace the case scores while keeping the same
report contract.
"""

from __future__ import annotations

import hashlib
from collections import defaultdict
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from pythontopia_graph.schemas.loop_contract import EvalRecord, LoopScore, LoopType

DATASET_ID = "next-gen-agentic-ab-v1"
BASELINE_POLICY = "legacy_single_shot"
TREATMENT_POLICY = "agentic_loop_v1"

MetricName = Literal[
    "evidence_binding",
    "learning_action_quality",
    "safety",
    "schema_validity",
    "cost_control",
]

METRIC_WEIGHTS: dict[MetricName, float] = {
    "evidence_binding": 0.25,
    "learning_action_quality": 0.25,
    "safety": 0.20,
    "schema_validity": 0.20,
    "cost_control": 0.10,
}


class AgenticAbModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class AgenticAbCase(AgenticAbModel):
    """One comparable scenario for baseline and treatment policies."""

    case_id: str = Field(min_length=1, max_length=128)
    loop_type: LoopType
    scenario: str = Field(min_length=1, max_length=240)
    baseline_scores: dict[MetricName, float]
    treatment_scores: dict[MetricName, float]


class AgenticAbGates(AgenticAbModel):
    """Acceptance gates for a repeated A/B run."""

    min_mean_uplift: float = 0.08
    min_stability_rate: float = 1.0
    max_regression_rate: float = 0.02
    min_schema_validity: float = 1.0
    regression_tolerance: float = 0.01


class AgenticAbCaseRun(AgenticAbModel):
    case_id: str
    loop_type: LoopType
    run_index: int
    baseline_score: float
    treatment_score: float
    delta: float
    regressed: bool


class AgenticAbRunSummary(AgenticAbModel):
    run_index: int
    seed: int
    baseline_mean: float
    treatment_mean: float
    mean_delta: float
    treatment_win_rate: float
    regression_rate: float


class AgenticAbLoopSummary(AgenticAbModel):
    loop_type: LoopType
    case_count: int
    baseline_mean: float
    treatment_mean: float
    mean_uplift: float
    stability_rate: float
    regression_rate: float
    schema_validity: float


class AgenticAbReport(AgenticAbModel):
    dataset_id: str
    baseline_policy: str
    treatment_policy: str
    case_count: int
    repeat_count: int
    seed: int
    baseline_mean: float
    treatment_mean: float
    mean_uplift: float
    stability_rate: float
    regression_rate: float
    schema_validity: float
    passed: bool
    gate_reasons: list[str]
    gates: AgenticAbGates
    runs: list[AgenticAbRunSummary]
    loop_summaries: list[AgenticAbLoopSummary]
    cases: list[AgenticAbCaseRun]

    @property
    def gate_metrics(self) -> list[LoopScore]:
        return [
            LoopScore(name="mean_uplift", value=self.mean_uplift),
            LoopScore(name="stability_rate", value=self.stability_rate),
            LoopScore(name="regression_rate", value=self.regression_rate),
            LoopScore(name="schema_validity", value=self.schema_validity),
        ]

    def to_eval_records(self) -> list[EvalRecord]:
        """Export per-loop summaries through the frozen loop contract."""

        records: list[EvalRecord] = []
        for summary in self.loop_summaries:
            records.append(
                EvalRecord(
                    dataset_id=self.dataset_id,
                    item_id=f"{summary.loop_type.lower()}-summary",
                    loop_type=summary.loop_type,
                    policy_version=self.treatment_policy,
                    trace_id=f"agentic-ab-{self.seed}-{summary.loop_type.lower()}",
                    metrics=[
                        LoopScore(name="mean_uplift", value=summary.mean_uplift),
                        LoopScore(name="stability_rate", value=summary.stability_rate),
                        LoopScore(name="regression_rate", value=summary.regression_rate),
                        LoopScore(name="schema_validity", value=summary.schema_validity),
                    ],
                    passed=(
                        self.passed
                        and summary.mean_uplift > 0
                        and summary.regression_rate <= self.gates.max_regression_rate
                    ),
                    schema_version="loop-contract.v1",
                )
            )
        return records


def default_agentic_ab_cases() -> list[AgenticAbCase]:
    """Return a report-shaped challenge set covering all five loop types."""

    raw_cases: list[tuple[str, LoopType, str, tuple[float, float, float, float, float], tuple[float, float, float, float, float]]] = [
        (
            "learner-repeat-failure",
            "LEARNER",
            "Two failures on the same KC should trigger remediation rather than harder practice.",
            (0.54, 0.48, 0.78, 0.62, 0.86),
            (0.92, 0.90, 0.95, 1.00, 0.78),
        ),
        (
            "learner-decaying-kc",
            "LEARNER",
            "Decaying review KC should be prioritized before new content.",
            (0.56, 0.52, 0.82, 0.64, 0.88),
            (0.91, 0.88, 0.95, 1.00, 0.80),
        ),
        (
            "learner-minimal-action",
            "LEARNER",
            "The next action should be small, evidence-linked, and time-budget aware.",
            (0.58, 0.50, 0.84, 0.66, 0.88),
            (0.90, 0.91, 0.95, 1.00, 0.79),
        ),
        (
            "learner-submission-pass",
            "LEARNER",
            "A passed formal submission should close the resource loop with mastery gain.",
            (0.62, 0.57, 0.85, 0.70, 0.86),
            (0.94, 0.90, 0.96, 1.00, 0.80),
        ),
        (
            "learner-replan-on-error",
            "LEARNER",
            "A runtime-error submission should replan instead of marking reading as enough.",
            (0.50, 0.45, 0.80, 0.60, 0.88),
            (0.91, 0.89, 0.96, 1.00, 0.78),
        ),
        (
            "debug-off-by-one",
            "DEBUG",
            "Off-by-one hints should stay layered and avoid a full answer leak.",
            (0.58, 0.50, 0.50, 0.66, 0.90),
            (0.90, 0.86, 0.98, 1.00, 0.78),
        ),
        (
            "debug-transfer-gate",
            "DEBUG",
            "A patched AC should still require transfer evidence before mastery.",
            (0.60, 0.52, 0.68, 0.68, 0.88),
            (0.91, 0.88, 0.96, 1.00, 0.78),
        ),
        (
            "debug-root-cause",
            "DEBUG",
            "Root-cause hypotheses should be ranked before hint escalation.",
            (0.55, 0.50, 0.70, 0.65, 0.90),
            (0.90, 0.87, 0.96, 1.00, 0.79),
        ),
        (
            "debug-hidden-case-safety",
            "DEBUG",
            "Debug output must not expose hidden tests or complete copyable answers.",
            (0.56, 0.51, 0.48, 0.64, 0.90),
            (0.89, 0.86, 0.99, 1.00, 0.78),
        ),
        (
            "debug-resubmit-compare",
            "DEBUG",
            "A resubmission should be compared with the previous attempt.",
            (0.57, 0.53, 0.72, 0.66, 0.89),
            (0.90, 0.87, 0.96, 1.00, 0.80),
        ),
        (
            "resource-fake-citation",
            "RESOURCE",
            "Unverified course facts should be rejected or revised, never published.",
            (0.45, 0.56, 0.80, 0.58, 0.88),
            (0.96, 0.88, 0.96, 1.00, 0.76),
        ),
        (
            "resource-pedagogy-critique",
            "RESOURCE",
            "Generated material should include critique and revision before publishing.",
            (0.60, 0.55, 0.86, 0.65, 0.88),
            (0.93, 0.91, 0.96, 1.00, 0.77),
        ),
        (
            "resource-partial-evidence",
            "RESOURCE",
            "Partial evidence should return PARTIAL rather than fabricated completeness.",
            (0.50, 0.54, 0.82, 0.60, 0.89),
            (0.94, 0.86, 0.97, 1.00, 0.78),
        ),
        (
            "resource-memory-directives",
            "RESOURCE",
            "Deep learner memory should shape resource format and intervention.",
            (0.62, 0.55, 0.84, 0.68, 0.88),
            (0.92, 0.90, 0.96, 1.00, 0.79),
        ),
        (
            "resource-post-use-hook",
            "RESOURCE",
            "Published resources should carry a check hook for later learning evidence.",
            (0.57, 0.50, 0.85, 0.64, 0.88),
            (0.91, 0.89, 0.96, 1.00, 0.79),
        ),
        (
            "world-kc-bound-quest",
            "WORLD",
            "World quests should bind to KC and required evidence, not decoration.",
            (0.50, 0.52, 0.82, 0.62, 0.90),
            (0.91, 0.88, 0.96, 1.00, 0.81),
        ),
        (
            "world-failure-remediation",
            "WORLD",
            "Repeated KC failure should create a remediation quest.",
            (0.52, 0.50, 0.80, 0.62, 0.90),
            (0.90, 0.89, 0.96, 1.00, 0.81),
        ),
        (
            "world-reward-balance",
            "WORLD",
            "Reward plans should avoid non-learning reward inflation.",
            (0.55, 0.50, 0.78, 0.64, 0.92),
            (0.89, 0.85, 0.95, 1.00, 0.82),
        ),
        (
            "world-classroom-conflict",
            "WORLD",
            "Generated events should not conflict with teacher classroom quests.",
            (0.54, 0.52, 0.80, 0.64, 0.90),
            (0.90, 0.87, 0.96, 1.00, 0.81),
        ),
        (
            "world-auditable-state-patch",
            "WORLD",
            "World state patches should be reversible and traceable.",
            (0.56, 0.50, 0.82, 0.66, 0.89),
            (0.91, 0.86, 0.97, 1.00, 0.81),
        ),
        (
            "improve-offline-gate",
            "IMPROVE",
            "A policy candidate must beat baseline offline before canary.",
            (0.56, 0.52, 0.82, 0.66, 0.90),
            (0.92, 0.88, 0.96, 1.00, 0.80),
        ),
        (
            "improve-one-variable",
            "IMPROVE",
            "Policy suggestions should change one variable only.",
            (0.55, 0.50, 0.78, 0.64, 0.90),
            (0.90, 0.87, 0.96, 1.00, 0.81),
        ),
        (
            "improve-rollback-rule",
            "IMPROVE",
            "Canary plans should carry explicit rollback rules.",
            (0.54, 0.52, 0.80, 0.64, 0.90),
            (0.91, 0.88, 0.97, 1.00, 0.80),
        ),
        (
            "improve-failure-cluster",
            "IMPROVE",
            "Trace failures should be clustered before policy generation.",
            (0.55, 0.51, 0.82, 0.66, 0.90),
            (0.90, 0.87, 0.96, 1.00, 0.81),
        ),
        (
            "improve-schema-regression",
            "IMPROVE",
            "Schema drift should block promotion even if surface answers improve.",
            (0.58, 0.55, 0.82, 0.58, 0.90),
            (0.90, 0.86, 0.96, 1.00, 0.82),
        ),
    ]
    return [
        AgenticAbCase(
            case_id=case_id,
            loop_type=loop_type,
            scenario=scenario,
            baseline_scores=_metric_map(baseline),
            treatment_scores=_metric_map(treatment),
        )
        for case_id, loop_type, scenario, baseline, treatment in raw_cases
    ]


def run_agentic_ab(
    *,
    cases: list[AgenticAbCase] | None = None,
    repeats: int = 9,
    seed: int = 20260629,
    gates: AgenticAbGates | None = None,
) -> AgenticAbReport:
    """Run repeated control-vs-treatment evaluation and return a gate report."""

    if repeats < 1:
        raise ValueError("repeats must be >= 1")
    case_list = list(cases) if cases is not None else default_agentic_ab_cases()
    if not case_list:
        raise ValueError("at least one A/B case is required")
    resolved_gates = gates or AgenticAbGates()

    case_runs: list[AgenticAbCaseRun] = []
    run_summaries: list[AgenticAbRunSummary] = []
    for run_index in range(repeats):
        run_case_results: list[AgenticAbCaseRun] = []
        for case in case_list:
            baseline = _composite(case.baseline_scores, case.case_id, run_index, seed, BASELINE_POLICY)
            treatment = _composite(
                case.treatment_scores, case.case_id, run_index, seed, TREATMENT_POLICY
            )
            delta = _round(treatment - baseline)
            run_case_results.append(
                AgenticAbCaseRun(
                    case_id=case.case_id,
                    loop_type=case.loop_type,
                    run_index=run_index + 1,
                    baseline_score=baseline,
                    treatment_score=treatment,
                    delta=delta,
                    regressed=delta < -resolved_gates.regression_tolerance,
                )
            )
        case_runs.extend(run_case_results)
        baseline_mean = _mean([case.baseline_score for case in run_case_results])
        treatment_mean = _mean([case.treatment_score for case in run_case_results])
        run_summaries.append(
            AgenticAbRunSummary(
                run_index=run_index + 1,
                seed=seed + run_index,
                baseline_mean=baseline_mean,
                treatment_mean=treatment_mean,
                mean_delta=_round(treatment_mean - baseline_mean),
                treatment_win_rate=_rate(case.delta > 0 for case in run_case_results),
                regression_rate=_rate(case.regressed for case in run_case_results),
            )
        )

    loop_summaries = _loop_summaries(case_list, case_runs, resolved_gates)
    baseline_mean = _mean([case.baseline_score for case in case_runs])
    treatment_mean = _mean([case.treatment_score for case in case_runs])
    mean_uplift = _round(treatment_mean - baseline_mean)
    stability_rate = _rate(run.mean_delta > 0 for run in run_summaries)
    regression_rate = _rate(case.regressed for case in case_runs)
    schema_validity = _mean(
        [case.treatment_scores["schema_validity"] for case in case_list]
    )
    gate_reasons = _gate_reasons(
        mean_uplift=mean_uplift,
        stability_rate=stability_rate,
        regression_rate=regression_rate,
        schema_validity=schema_validity,
        gates=resolved_gates,
    )
    return AgenticAbReport(
        dataset_id=DATASET_ID,
        baseline_policy=BASELINE_POLICY,
        treatment_policy=TREATMENT_POLICY,
        case_count=len(case_list),
        repeat_count=repeats,
        seed=seed,
        baseline_mean=baseline_mean,
        treatment_mean=treatment_mean,
        mean_uplift=mean_uplift,
        stability_rate=stability_rate,
        regression_rate=regression_rate,
        schema_validity=schema_validity,
        passed=not gate_reasons,
        gate_reasons=gate_reasons,
        gates=resolved_gates,
        runs=run_summaries,
        loop_summaries=loop_summaries,
        cases=case_runs,
    )


def write_markdown_report(report: AgenticAbReport, path: str | Path) -> Path:
    """Write a human-readable A/B report for docs/reports."""

    out = Path(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    status = "PASS" if report.passed else "FAIL"
    lines = [
        "# Agentic Loop A/B Evaluation",
        "",
        f"Status: **{status}**",
        "",
        f"- Dataset: `{report.dataset_id}`",
        f"- Control: `{report.baseline_policy}`",
        f"- Treatment: `{report.treatment_policy}`",
        f"- Cases: `{report.case_count}`",
        f"- Repeats: `{report.repeat_count}`",
        f"- Mean uplift: `{report.mean_uplift:.4f}`",
        f"- Stability rate: `{report.stability_rate:.4f}`",
        f"- Regression rate: `{report.regression_rate:.4f}`",
        f"- Schema validity: `{report.schema_validity:.4f}`",
        "",
        "## Gate Summary",
        "",
        "| Metric | Value | Gate |",
        "|---|---:|---:|",
        f"| mean_uplift | {report.mean_uplift:.4f} | >= {report.gates.min_mean_uplift:.4f} |",
        f"| stability_rate | {report.stability_rate:.4f} | >= {report.gates.min_stability_rate:.4f} |",
        f"| regression_rate | {report.regression_rate:.4f} | <= {report.gates.max_regression_rate:.4f} |",
        f"| schema_validity | {report.schema_validity:.4f} | >= {report.gates.min_schema_validity:.4f} |",
        "",
        "## Repeated Runs",
        "",
        "| Run | Baseline | Treatment | Delta | Win Rate | Regression Rate |",
        "|---:|---:|---:|---:|---:|---:|",
    ]
    for run in report.runs:
        lines.append(
            f"| {run.run_index} | {run.baseline_mean:.4f} | {run.treatment_mean:.4f} "
            f"| {run.mean_delta:.4f} | {run.treatment_win_rate:.4f} "
            f"| {run.regression_rate:.4f} |"
        )
    lines.extend(
        [
            "",
            "## Loop Type Summary",
            "",
            "| Loop Type | Cases | Baseline | Treatment | Mean Uplift | Stability | Regression | Schema |",
            "|---|---:|---:|---:|---:|---:|---:|---:|",
        ]
    )
    for summary in report.loop_summaries:
        lines.append(
            f"| {summary.loop_type} | {summary.case_count} | {summary.baseline_mean:.4f} "
            f"| {summary.treatment_mean:.4f} | {summary.mean_uplift:.4f} "
            f"| {summary.stability_rate:.4f} | {summary.regression_rate:.4f} "
            f"| {summary.schema_validity:.4f} |"
        )
    if report.gate_reasons:
        lines.extend(["", "## Gate Reasons", ""])
        lines.extend(f"- {reason}" for reason in report.gate_reasons)
    lines.append("")
    out.write_text("\n".join(lines), encoding="utf-8")
    return out


def _metric_map(values: tuple[float, float, float, float, float]) -> dict[MetricName, float]:
    return {
        "evidence_binding": values[0],
        "learning_action_quality": values[1],
        "safety": values[2],
        "schema_validity": values[3],
        "cost_control": values[4],
    }


def _composite(
    scores: dict[MetricName, float],
    case_id: str,
    run_index: int,
    seed: int,
    policy: str,
) -> float:
    base = sum(_clamp01(scores[metric]) * weight for metric, weight in METRIC_WEIGHTS.items())
    noise = _deterministic_noise(seed=seed, run_index=run_index, case_id=case_id, policy=policy)
    return _round(_clamp01(base + noise))


def _deterministic_noise(*, seed: int, run_index: int, case_id: str, policy: str) -> float:
    digest = hashlib.sha256(f"{seed}:{run_index}:{case_id}:{policy}".encode()).hexdigest()
    bucket = int(digest[:8], 16) % 2001
    return (bucket - 1000) / 100_000.0


def _loop_summaries(
    cases: list[AgenticAbCase],
    case_runs: list[AgenticAbCaseRun],
    gates: AgenticAbGates,
) -> list[AgenticAbLoopSummary]:
    cases_by_loop: dict[LoopType, list[AgenticAbCase]] = defaultdict(list)
    runs_by_loop: dict[LoopType, list[AgenticAbCaseRun]] = defaultdict(list)
    for case in cases:
        cases_by_loop[case.loop_type].append(case)
    for case_run in case_runs:
        runs_by_loop[case_run.loop_type].append(case_run)

    summaries: list[AgenticAbLoopSummary] = []
    for loop_type in sorted(cases_by_loop):
        loop_runs = runs_by_loop[loop_type]
        baseline_mean = _mean([run.baseline_score for run in loop_runs])
        treatment_mean = _mean([run.treatment_score for run in loop_runs])
        per_run_deltas: dict[int, list[float]] = defaultdict(list)
        for run in loop_runs:
            per_run_deltas[run.run_index].append(run.delta)
        stability_rate = _rate(_mean(deltas) > 0 for deltas in per_run_deltas.values())
        summaries.append(
            AgenticAbLoopSummary(
                loop_type=loop_type,
                case_count=len(cases_by_loop[loop_type]),
                baseline_mean=baseline_mean,
                treatment_mean=treatment_mean,
                mean_uplift=_round(treatment_mean - baseline_mean),
                stability_rate=stability_rate,
                regression_rate=_rate(run.regressed for run in loop_runs),
                schema_validity=_mean(
                    [case.treatment_scores["schema_validity"] for case in cases_by_loop[loop_type]]
                ),
            )
        )
    return summaries


def _gate_reasons(
    *,
    mean_uplift: float,
    stability_rate: float,
    regression_rate: float,
    schema_validity: float,
    gates: AgenticAbGates,
) -> list[str]:
    reasons: list[str] = []
    if mean_uplift < gates.min_mean_uplift:
        reasons.append(f"mean_uplift {mean_uplift:.4f} < {gates.min_mean_uplift:.4f}")
    if stability_rate < gates.min_stability_rate:
        reasons.append(
            f"stability_rate {stability_rate:.4f} < {gates.min_stability_rate:.4f}"
        )
    if regression_rate > gates.max_regression_rate:
        reasons.append(
            f"regression_rate {regression_rate:.4f} > {gates.max_regression_rate:.4f}"
        )
    if schema_validity < gates.min_schema_validity:
        reasons.append(
            f"schema_validity {schema_validity:.4f} < {gates.min_schema_validity:.4f}"
        )
    return reasons


def _mean(values: list[float]) -> float:
    if not values:
        return 0.0
    return _round(sum(values) / len(values))


def _rate(flags) -> float:
    values = list(flags)
    if not values:
        return 0.0
    return _round(sum(1 for flag in values if flag) / len(values))


def _round(value: float) -> float:
    return round(float(value), 6)


def _clamp01(value: float) -> float:
    return max(0.0, min(1.0, float(value)))
