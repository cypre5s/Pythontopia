from __future__ import annotations

import pytest

from pyt_agent_lab.rsage_generalization_gate import (
    GeneralizationThresholds,
    LearningCurvePoint,
    SeedGateReport,
    action_distribution_metrics,
    aggregate_seed_gates,
    evaluate_checkpoint,
    evaluate_learning_curve,
)


UNIFORM_ACTION_COUNTS = {
    "READ": 10,
    "REVIEW": 10,
    "TRACE": 10,
    "SCAFFOLD": 10,
    "PRACTICE": 10,
    "ASK": 10,
    "CHECK": 10,
}


def test_action_distribution_metrics_detect_uniform_and_collapsed_actions() -> None:
    uniform = action_distribution_metrics(UNIFORM_ACTION_COUNTS)
    collapsed = action_distribution_metrics({"READ": 70})

    assert uniform["normalized_action_entropy"] == pytest.approx(1.0)
    assert uniform["max_action_share"] == pytest.approx(1 / 7)
    assert collapsed["normalized_action_entropy"] == pytest.approx(0.0)
    assert collapsed["max_action_share"] == pytest.approx(1.0)


def test_thresholds_are_explicitly_project_empirical_values() -> None:
    thresholds = GeneralizationThresholds()

    assert thresholds.source == "project_empirical"
    assert "项目经验值" in thresholds.source_zh
    assert "非论文通用阈值" in thresholds.source_zh


@pytest.mark.parametrize("value", [float("nan"), float("inf"), float("-inf")])
def test_checkpoint_hard_stops_on_non_finite_metrics(value: float) -> None:
    decision = evaluate_checkpoint(
        action_counts=UNIFORM_ACTION_COUNTS,
        numeric_metrics={"loss": value},
    )

    assert decision.status == "hard_stop"
    assert "NON_FINITE_METRIC" in decision.reason_codes
    assert "loss" in decision.metrics["non_finite_metric_names"]


def test_checkpoint_hard_stops_on_oom() -> None:
    decision = evaluate_checkpoint(
        action_counts=UNIFORM_ACTION_COUNTS,
        numeric_metrics={"loss": 0.4},
        oom=True,
    )

    assert decision.status == "hard_stop"
    assert "CUDA_OOM" in decision.reason_codes


def test_checkpoint_hard_stops_on_answer_or_evidence_leakage() -> None:
    decision = evaluate_checkpoint(
        action_counts=UNIFORM_ACTION_COUNTS,
        answer_leak_count=1,
        fabricated_evidence_count=2,
    )

    assert decision.status == "hard_stop"
    assert "ANSWER_LEAK" in decision.reason_codes
    assert "FABRICATED_EVIDENCE" in decision.reason_codes


def test_checkpoint_warns_on_concentration_and_stops_on_action_collapse() -> None:
    warning = evaluate_checkpoint(
        action_counts={
            "READ": 30,
            "REVIEW": 10,
            "TRACE": 6,
            "SCAFFOLD": 6,
            "PRACTICE": 6,
            "ASK": 6,
            "CHECK": 6,
        }
    )
    collapse = evaluate_checkpoint(action_counts={"READ": 70})

    assert warning.status == "warn"
    assert "ACTION_CONCENTRATION_WARNING" in warning.reason_codes
    assert collapse.status == "hard_stop"
    assert "ACTION_COLLAPSE" in collapse.reason_codes
    assert any("\u4e00" <= char <= "\u9fff" for char in collapse.message_zh)


def test_learning_curve_hard_stops_on_overfit_gap() -> None:
    decision = evaluate_learning_curve(
        [
            LearningCurvePoint(0.35, 0.80, 0.58, 0.02, 0.50, 0.48),
            LearningCurvePoint(0.70, 0.65, 0.62, 0.08, 0.70, 0.64),
            LearningCurvePoint(1.00, 0.55, 0.68, 0.12, 0.85, 0.60),
        ],
        final_gap_ci_lower=0.08,
    )

    assert decision.status == "hard_stop"
    assert "OVERFIT_GAP" in decision.reason_codes
    assert decision.metrics["train_dev_gap"] == pytest.approx(0.25)


def test_learning_curve_hard_stops_when_optimization_signal_is_absent() -> None:
    decision = evaluate_learning_curve(
        [
            LearningCurvePoint(0.35, 1.00, 0.51, -0.02, 0.50, 0.50),
            LearningCurvePoint(0.70, 0.99, 0.52, -0.01, 0.51, 0.505),
            LearningCurvePoint(1.00, 0.97, 0.54, 0.00, 0.52, 0.51),
        ]
    )

    assert decision.status == "hard_stop"
    assert "NO_OPTIMIZATION_SIGNAL" in decision.reason_codes
    assert decision.metrics["train_loss_relative_reduction"] == pytest.approx(0.03)


def test_learning_curve_warns_when_both_data_increments_plateau() -> None:
    decision = evaluate_learning_curve(
        [
            LearningCurvePoint(0.35, 1.00, 0.60, 0.04, 0.61, 0.60),
            LearningCurvePoint(0.70, 0.80, 0.63, 0.08, 0.63, 0.61),
            LearningCurvePoint(1.00, 0.70, 0.65, 0.10, 0.64, 0.615),
        ]
    )

    assert decision.status == "warn"
    assert decision.reason_codes == ("LEARNING_CURVE_PLATEAU",)
    assert decision.metrics["dev_gain_35_70"] == pytest.approx(0.01)
    assert decision.metrics["dev_gain_70_100"] == pytest.approx(0.005)


def test_three_seed_gate_promotes_stable_target_improvement() -> None:
    passing_gate = evaluate_checkpoint(action_counts=UNIFORM_ACTION_COUNTS)

    decision = aggregate_seed_gates(
        [
            SeedGateReport(seed=42, target_delta=0.05, gate=passing_gate),
            SeedGateReport(seed=43, target_delta=0.04, gate=passing_gate),
            SeedGateReport(seed=44, target_delta=-0.01, gate=passing_gate),
        ]
    )

    assert decision.status == "pass"
    assert decision.reason_codes == ()
    assert decision.metrics["positive_seed_count"] == 2
    assert decision.metrics["median_target_delta"] == pytest.approx(0.04)
    assert decision.metrics["worst_target_delta"] == pytest.approx(-0.01)
    assert decision.metrics["seed_spread"] == pytest.approx(0.06)


def test_three_seed_gate_rejects_unstable_or_insufficient_improvement() -> None:
    passing_gate = evaluate_checkpoint(action_counts=UNIFORM_ACTION_COUNTS)

    decision = aggregate_seed_gates(
        [
            SeedGateReport(seed=42, target_delta=0.08, gate=passing_gate),
            SeedGateReport(seed=43, target_delta=0.00, gate=passing_gate),
            SeedGateReport(seed=44, target_delta=-0.03, gate=passing_gate),
        ]
    )

    assert decision.status == "hard_stop"
    assert "SEED_PROMOTION_FAILED" in decision.reason_codes
    assert "SEED_INSTABILITY" in decision.reason_codes


def test_three_seed_gate_requires_the_registered_seed_set() -> None:
    passing_gate = evaluate_checkpoint(action_counts=UNIFORM_ACTION_COUNTS)

    decision = aggregate_seed_gates(
        [
            SeedGateReport(seed=42, target_delta=0.05, gate=passing_gate),
            SeedGateReport(seed=43, target_delta=0.04, gate=passing_gate),
        ]
    )

    assert decision.status == "hard_stop"
    assert decision.reason_codes == ("INSUFFICIENT_SEEDS",)


def test_any_seed_leak_forces_global_hard_stop() -> None:
    passing_gate = evaluate_checkpoint(action_counts=UNIFORM_ACTION_COUNTS)
    leaking_gate = evaluate_checkpoint(
        action_counts=UNIFORM_ACTION_COUNTS,
        answer_leak_count=1,
    )

    decision = aggregate_seed_gates(
        [
            SeedGateReport(seed=42, target_delta=0.06, gate=passing_gate),
            SeedGateReport(seed=43, target_delta=0.05, gate=leaking_gate),
            SeedGateReport(seed=44, target_delta=0.04, gate=passing_gate),
        ]
    )

    assert decision.status == "hard_stop"
    assert "ANSWER_LEAK" in decision.reason_codes
    assert "泄漏" in decision.message_zh
