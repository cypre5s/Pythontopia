"""Eval-gated prompt optimization.

This module is the small but important bridge between offline prompt/program
optimization and production use: a candidate is accepted only when golden evals
show a measurable mean uplift and sample-level regressions stay within policy.
It is deterministic, dependency-light, and compatible with the existing
``optimize/loader.py`` frozen artifact contract.
"""

from __future__ import annotations

import math
from collections.abc import Callable, Mapping, Sequence
from pathlib import Path
from typing import Any

from pydantic import BaseModel, Field

from pythontopia_graph.eval.scorers import (
    DEFAULT_METRICS,
    EvalSample,
    METRIC_ANSWER_RELEVANCY,
    METRIC_CONTEXT_RECALL,
    METRIC_FAITHFULNESS,
)
from pythontopia_graph.optimize import compile as compile_mod
from pythontopia_graph.optimize import programs

DEFAULT_METRIC_WEIGHTS: dict[str, float] = {
    METRIC_FAITHFULNESS: 0.40,
    METRIC_ANSWER_RELEVANCY: 0.30,
    METRIC_CONTEXT_RECALL: 0.30,
}


class PromptVariant(BaseModel):
    """A prompt/program candidate that can be evaluated and exported."""

    name: str
    program_name: str
    instruction: str
    demos: list[dict[str, Any]] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)


class OptimizationPolicy(BaseModel):
    """Acceptance gates for eval-driven optimization."""

    metric_weights: dict[str, float] = Field(default_factory=lambda: dict(DEFAULT_METRIC_WEIGHTS))
    min_mean_uplift: float = 0.05
    min_sample_win_rate: float = 0.55
    max_regression_rate: float = 0.15
    regression_tolerance: float = 0.02
    severe_regression_delta: float = 0.20
    max_severe_regressions: int = 0
    min_candidate_mean: float = 0.0

    def normalized_weights(self) -> dict[str, float]:
        """Return positive metric weights normalized to sum to 1."""
        positive = {
            metric: float(weight)
            for metric, weight in self.metric_weights.items()
            if math.isfinite(float(weight)) and float(weight) > 0
        }
        if not positive:
            positive = dict(DEFAULT_METRIC_WEIGHTS)
        total = sum(positive.values())
        return {metric: weight / total for metric, weight in positive.items()}


class SampleOptimizationScore(BaseModel):
    """Per-sample comparison between a candidate and the baseline."""

    sample_id: str
    metrics: dict[str, float]
    composite_score: float
    baseline_composite_score: float
    delta: float
    regressed: bool
    severe_regression: bool


class CandidateOptimizationSummary(BaseModel):
    """Aggregate comparison for one candidate."""

    variant_name: str
    mean_score: float
    baseline_mean_score: float
    mean_uplift: float
    sample_win_rate: float
    regression_rate: float
    severe_regression_count: int
    accepted_by_gates: bool
    gate_reasons: list[str] = Field(default_factory=list)
    samples: list[SampleOptimizationScore] = Field(default_factory=list)


class OptimizationDecision(BaseModel):
    """Final optimization decision and audit trail."""

    baseline_name: str
    baseline_mean_score: float
    accepted: bool
    selected_variant_name: str | None
    selected_summary: CandidateOptimizationSummary | None
    candidate_summaries: dict[str, CandidateOptimizationSummary]
    policy: OptimizationPolicy


VariantScorer = Callable[[PromptVariant, EvalSample], Mapping[str, Any]]


def optimize_prompt_variants(
    samples: Sequence[EvalSample],
    baseline: PromptVariant,
    candidates: Sequence[PromptVariant],
    scorer: VariantScorer,
    *,
    policy: OptimizationPolicy | None = None,
) -> OptimizationDecision:
    """Evaluate candidates against a baseline and select the best safe uplift.

    The scorer is intentionally injectable. CI can use deterministic tables,
    while offline runs can wrap Ragas/DeepEval, human labels, or model judges.
    """
    resolved_policy = policy or OptimizationPolicy()
    sample_list = list(samples)
    weights = resolved_policy.normalized_weights()
    baseline_scores = [
        _variant_sample_score(baseline, sample, scorer, weights) for sample in sample_list
    ]
    baseline_mean = _mean([score.composite_score for score in baseline_scores])

    summaries: dict[str, CandidateOptimizationSummary] = {}
    for candidate in candidates:
        candidate_scores = [
            _variant_sample_score(candidate, sample, scorer, weights) for sample in sample_list
        ]
        comparisons = [
            _compare_sample(
                sample_id=sample.id or f"#{index}",
                candidate_score=candidate_score,
                baseline_score=baseline_score,
                policy=resolved_policy,
            )
            for index, (sample, candidate_score, baseline_score) in enumerate(
                zip(sample_list, candidate_scores, baseline_scores, strict=True)
            )
        ]
        summaries[candidate.name] = _candidate_summary(
            candidate.name,
            comparisons,
            baseline_mean=baseline_mean,
            policy=resolved_policy,
            sample_count=len(sample_list),
        )

    accepted = [summary for summary in summaries.values() if summary.accepted_by_gates]
    selected = _best_summary(accepted)
    return OptimizationDecision(
        baseline_name=baseline.name,
        baseline_mean_score=round(baseline_mean, 6),
        accepted=selected is not None,
        selected_variant_name=selected.variant_name if selected is not None else None,
        selected_summary=selected,
        candidate_summaries=summaries,
        policy=resolved_policy,
    )


def write_selected_artifact(
    decision: OptimizationDecision,
    baseline: PromptVariant,
    candidates: Sequence[PromptVariant],
    *,
    artifacts_dir: Path | str | None = None,
) -> Path | None:
    """Write the accepted candidate as a loader-compatible frozen artifact.

    Returns ``None`` when no candidate passed the eval gates.
    """
    if not decision.accepted or decision.selected_variant_name is None:
        return None
    variant = _find_variant(decision.selected_variant_name, baseline, candidates)
    if variant is None:
        return None
    selected = decision.selected_summary
    if selected is None:
        return None

    metadata = dict(variant.metadata)
    metadata.update(
        {
            "optimizer": "eval_gate",
            "baseline_name": decision.baseline_name,
            "baseline_mean_score": decision.baseline_mean_score,
            "mean_score": selected.mean_score,
            "mean_uplift": selected.mean_uplift,
            "sample_win_rate": selected.sample_win_rate,
            "regression_rate": selected.regression_rate,
            "severe_regression_count": selected.severe_regression_count,
            "metric_weights": decision.policy.normalized_weights(),
        }
    )
    artifact = programs.build_artifact(
        variant.program_name,
        instruction=variant.instruction,
        demos=variant.demos,
        metadata=metadata,
    )
    return compile_mod.write_artifact(
        artifact, compile_mod.artifact_path(variant.program_name, artifacts_dir)
    )


class _VariantSampleScore(BaseModel):
    metrics: dict[str, float]
    composite_score: float


def _variant_sample_score(
    variant: PromptVariant,
    sample: EvalSample,
    scorer: VariantScorer,
    weights: Mapping[str, float],
) -> _VariantSampleScore:
    metrics = _coerce_metrics(scorer(variant, sample))
    composite = sum(metrics.get(metric, 0.0) * weight for metric, weight in weights.items())
    return _VariantSampleScore(metrics=metrics, composite_score=round(composite, 6))


def _coerce_metrics(raw: Mapping[str, Any]) -> dict[str, float]:
    metrics: dict[str, float] = {}
    for metric in DEFAULT_METRICS:
        if metric in raw:
            metrics[metric] = _clamp01(raw[metric])
    for metric, value in raw.items():
        if metric not in metrics:
            metrics[metric] = _clamp01(value)
    return metrics


def _clamp01(raw: Any) -> float:
    value = getattr(raw, "value", raw)
    try:
        numeric = float(value)
    except (TypeError, ValueError):
        return 0.0
    if not math.isfinite(numeric):
        return 0.0
    return max(0.0, min(1.0, numeric))


def _compare_sample(
    *,
    sample_id: str,
    candidate_score: _VariantSampleScore,
    baseline_score: _VariantSampleScore,
    policy: OptimizationPolicy,
) -> SampleOptimizationScore:
    delta = round(candidate_score.composite_score - baseline_score.composite_score, 6)
    return SampleOptimizationScore(
        sample_id=sample_id,
        metrics=candidate_score.metrics,
        composite_score=candidate_score.composite_score,
        baseline_composite_score=baseline_score.composite_score,
        delta=delta,
        regressed=delta < -policy.regression_tolerance,
        severe_regression=delta <= -policy.severe_regression_delta,
    )


def _candidate_summary(
    variant_name: str,
    comparisons: list[SampleOptimizationScore],
    *,
    baseline_mean: float,
    policy: OptimizationPolicy,
    sample_count: int,
) -> CandidateOptimizationSummary:
    mean_score = _mean([sample.composite_score for sample in comparisons])
    mean_uplift = mean_score - baseline_mean
    sample_win_count = sum(
        1 for sample in comparisons if sample.delta > policy.regression_tolerance
    )
    sample_win_rate = sample_win_count / sample_count if sample_count else 0.0
    regression_count = sum(1 for sample in comparisons if sample.regressed)
    severe_count = sum(1 for sample in comparisons if sample.severe_regression)
    regression_rate = regression_count / sample_count if sample_count else 0.0
    gate_reasons = _gate_reasons(
        mean_score=mean_score,
        mean_uplift=mean_uplift,
        sample_win_rate=sample_win_rate,
        regression_rate=regression_rate,
        severe_count=severe_count,
        sample_count=sample_count,
        policy=policy,
    )
    return CandidateOptimizationSummary(
        variant_name=variant_name,
        mean_score=round(mean_score, 6),
        baseline_mean_score=round(baseline_mean, 6),
        mean_uplift=round(mean_uplift, 6),
        sample_win_rate=round(sample_win_rate, 6),
        regression_rate=round(regression_rate, 6),
        severe_regression_count=severe_count,
        accepted_by_gates=not gate_reasons,
        gate_reasons=gate_reasons,
        samples=comparisons,
    )


def _gate_reasons(
    *,
    mean_score: float,
    mean_uplift: float,
    sample_win_rate: float,
    regression_rate: float,
    severe_count: int,
    sample_count: int,
    policy: OptimizationPolicy,
) -> list[str]:
    reasons: list[str] = []
    if sample_count <= 0:
        reasons.append("no_eval_samples")
    if mean_uplift < policy.min_mean_uplift:
        reasons.append(
            f"mean_uplift {mean_uplift:.3f} < min_mean_uplift {policy.min_mean_uplift:.3f}"
        )
    if mean_score < policy.min_candidate_mean:
        reasons.append(
            f"mean_score {mean_score:.3f} < min_candidate_mean {policy.min_candidate_mean:.3f}"
        )
    if sample_win_rate < policy.min_sample_win_rate:
        reasons.append(
            f"sample_win_rate {sample_win_rate:.3f} < min_sample_win_rate "
            f"{policy.min_sample_win_rate:.3f}"
        )
    if regression_rate > policy.max_regression_rate:
        reasons.append(
            f"regression_rate {regression_rate:.3f} > max_regression_rate "
            f"{policy.max_regression_rate:.3f}"
        )
    if severe_count > policy.max_severe_regressions:
        reasons.append(
            f"severe_regression_count {severe_count} > max_severe_regressions "
            f"{policy.max_severe_regressions}"
        )
    return reasons


def _best_summary(
    summaries: Sequence[CandidateOptimizationSummary],
) -> CandidateOptimizationSummary | None:
    if not summaries:
        return None
    return max(
        summaries,
        key=lambda summary: (
            summary.mean_uplift,
            summary.sample_win_rate,
            summary.mean_score,
            -summary.regression_rate,
        ),
    )


def _find_variant(
    name: str, baseline: PromptVariant, candidates: Sequence[PromptVariant]
) -> PromptVariant | None:
    if baseline.name == name:
        return baseline
    for candidate in candidates:
        if candidate.name == name:
            return candidate
    return None


def _mean(values: Sequence[float]) -> float:
    return sum(values) / len(values) if values else 0.0


__all__ = [
    "CandidateOptimizationSummary",
    "OptimizationDecision",
    "OptimizationPolicy",
    "PromptVariant",
    "SampleOptimizationScore",
    "optimize_prompt_variants",
    "write_selected_artifact",
]
