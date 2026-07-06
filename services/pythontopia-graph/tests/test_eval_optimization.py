from __future__ import annotations

from collections.abc import Mapping
from pathlib import Path

import pytest

from pythontopia_graph.eval.optimization import (
    OptimizationPolicy,
    PromptVariant,
    optimize_prompt_variants,
    write_selected_artifact,
)
from pythontopia_graph.eval.scorers import (
    EvalSample,
    METRIC_ANSWER_RELEVANCY,
    METRIC_CONTEXT_RECALL,
    METRIC_FAITHFULNESS,
)
from pythontopia_graph.optimize.loader import load_compiled


MetricMap = Mapping[str, float]


def _judge(table: Mapping[str, Mapping[str, MetricMap]]):
    def score(variant: PromptVariant, sample: EvalSample) -> MetricMap:
        return table[variant.name][sample.id]

    return score


def _metrics(faithfulness: float, answer_relevancy: float, context_recall: float) -> MetricMap:
    return {
        METRIC_FAITHFULNESS: faithfulness,
        METRIC_ANSWER_RELEVANCY: answer_relevancy,
        METRIC_CONTEXT_RECALL: context_recall,
    }


def test_optimizer_accepts_candidate_with_quantified_uplift_and_exports_artifact(
    tmp_path: Path,
) -> None:
    samples = [
        EvalSample(id="memory", question="What should I review?", answer="Use evidence memory"),
        EvalSample(id="grounding", question="Explain input()", answer="Cite page evidence"),
        EvalSample(id="practice", question="Give practice", answer="No leaked answer"),
        EvalSample(id="neutral", question="Plan my week", answer="Balanced plan"),
    ]
    baseline = PromptVariant(
        name="current",
        program_name="guide_coach_decision",
        instruction="Current hand-written prompt.",
    )
    candidate = PromptVariant(
        name="evidence_memory_v2",
        program_name="guide_coach_decision",
        instruction="Prefer learning evidence graph signals before raw recent memory.",
        demos=[{"role": "user", "content": "weak kc"}, {"role": "assistant", "content": "trace"}],
        metadata={"route": "evidence-memory-2.0"},
    )
    table = {
        "current": {
            "memory": _metrics(0.55, 0.55, 0.50),
            "grounding": _metrics(0.50, 0.55, 0.45),
            "practice": _metrics(0.70, 0.65, 0.70),
            "neutral": _metrics(0.60, 0.60, 0.60),
        },
        "evidence_memory_v2": {
            "memory": _metrics(0.88, 0.82, 0.84),
            "grounding": _metrics(0.83, 0.80, 0.79),
            "practice": _metrics(0.78, 0.74, 0.76),
            "neutral": _metrics(0.72, 0.72, 0.72),
        },
    }

    decision = optimize_prompt_variants(
        samples,
        baseline,
        [candidate],
        _judge(table),
        policy=OptimizationPolicy(min_mean_uplift=0.18, max_regression_rate=0.0),
    )

    assert decision.accepted is True
    assert decision.selected_variant_name == "evidence_memory_v2"
    assert decision.selected_summary is not None
    assert decision.selected_summary.mean_uplift >= 0.18
    assert decision.selected_summary.regression_rate == 0.0

    path = write_selected_artifact(decision, baseline, [candidate], artifacts_dir=tmp_path)
    assert path == tmp_path / "guide_coach_decision.json"
    compiled = load_compiled("guide_coach_decision", artifacts_dir=tmp_path)
    assert compiled is not None
    assert compiled.instruction == candidate.instruction
    assert compiled.demos == candidate.demos
    assert compiled.metadata["optimizer"] == "eval_gate"
    assert compiled.metadata["mean_uplift"] == pytest.approx(decision.selected_summary.mean_uplift)


def test_optimizer_rejects_candidate_with_high_regression_despite_mean_uplift() -> None:
    samples = [
        EvalSample(id="s1", question="q1", answer="a1"),
        EvalSample(id="s2", question="q2", answer="a2"),
        EvalSample(id="s3", question="q3", answer="a3"),
        EvalSample(id="s4", question="q4", answer="a4"),
    ]
    baseline = PromptVariant(name="current", program_name="practice_generate", instruction="base")
    risky = PromptVariant(
        name="risky",
        program_name="practice_generate",
        instruction="Aggressively optimize only easy cases.",
    )
    table = {
        "current": {sample.id: _metrics(0.65, 0.65, 0.65) for sample in samples},
        "risky": {
            "s1": _metrics(1.00, 1.00, 1.00),
            "s2": _metrics(0.92, 0.92, 0.92),
            "s3": _metrics(0.92, 0.92, 0.92),
            "s4": _metrics(0.10, 0.10, 0.10),
        },
    }

    decision = optimize_prompt_variants(
        samples,
        baseline,
        [risky],
        _judge(table),
        policy=OptimizationPolicy(
            min_mean_uplift=0.05,
            max_regression_rate=0.20,
            severe_regression_delta=0.20,
            max_severe_regressions=0,
        ),
    )

    assert decision.accepted is False
    assert decision.selected_variant_name is None
    summary = decision.candidate_summaries["risky"]
    assert summary.mean_uplift > 0.05
    assert summary.regression_rate == 0.25
    assert summary.severe_regression_count == 1
    assert any("regression_rate" in reason for reason in summary.gate_reasons)


def test_optimizer_rejects_candidate_without_stable_sample_win_rate() -> None:
    samples = [
        EvalSample(id="memory", question="q1", answer="a1"),
        EvalSample(id="grounding", question="q2", answer="a2"),
        EvalSample(id="practice", question="q3", answer="a3"),
        EvalSample(id="neutral", question="q4", answer="a4"),
        EvalSample(id="review", question="q5", answer="a5"),
    ]
    baseline = PromptVariant(
        name="current",
        program_name="guide_coach_decision",
        instruction="base",
    )
    brittle = PromptVariant(
        name="brittle_mean_winner",
        program_name="guide_coach_decision",
        instruction="Huge gains on two cases, noise on the rest.",
    )
    table = {
        "current": {sample.id: _metrics(0.60, 0.60, 0.60) for sample in samples},
        "brittle_mean_winner": {
            "memory": _metrics(1.00, 1.00, 1.00),
            "grounding": _metrics(1.00, 1.00, 1.00),
            "practice": _metrics(0.61, 0.61, 0.61),
            "neutral": _metrics(0.61, 0.61, 0.61),
            "review": _metrics(0.61, 0.61, 0.61),
        },
    }

    decision = optimize_prompt_variants(
        samples,
        baseline,
        [brittle],
        _judge(table),
        policy=OptimizationPolicy(
            min_mean_uplift=0.10,
            max_regression_rate=0.0,
            regression_tolerance=0.02,
            min_sample_win_rate=0.80,
        ),
    )

    assert decision.accepted is False
    summary = decision.candidate_summaries["brittle_mean_winner"]
    assert summary.mean_uplift > 0.10
    assert summary.regression_rate == 0.0
    assert summary.sample_win_rate == pytest.approx(0.40)
    assert any("sample_win_rate" in reason for reason in summary.gate_reasons)


def test_optimizer_uses_weighted_metric_composite_when_selecting_candidate() -> None:
    samples = [
        EvalSample(id="kc-graph", question="q1", answer="a1"),
        EvalSample(id="evidence", question="q2", answer="a2"),
    ]
    baseline = PromptVariant(name="current", program_name="guide_coach_decision", instruction="base")
    flashy = PromptVariant(
        name="flashy",
        program_name="guide_coach_decision",
        instruction="Maximize surface faithfulness only.",
    )
    balanced = PromptVariant(
        name="balanced",
        program_name="guide_coach_decision",
        instruction="Balance faithfulness, relevancy, and context recall.",
    )
    table = {
        "current": {sample.id: _metrics(0.60, 0.60, 0.60) for sample in samples},
        "flashy": {sample.id: _metrics(0.98, 0.62, 0.22) for sample in samples},
        "balanced": {sample.id: _metrics(0.76, 0.75, 0.78) for sample in samples},
    }

    decision = optimize_prompt_variants(
        samples,
        baseline,
        [flashy, balanced],
        _judge(table),
        policy=OptimizationPolicy(
            metric_weights={
                METRIC_FAITHFULNESS: 0.20,
                METRIC_ANSWER_RELEVANCY: 0.20,
                METRIC_CONTEXT_RECALL: 0.60,
            },
            min_mean_uplift=0.10,
        ),
    )

    assert decision.accepted is True
    assert decision.selected_variant_name == "balanced"
    assert decision.candidate_summaries["balanced"].mean_uplift > 0.10
    assert decision.candidate_summaries["flashy"].mean_uplift < 0
