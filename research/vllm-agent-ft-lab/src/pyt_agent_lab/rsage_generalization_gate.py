"""Pure generalization gates for R-SAGE training experiments."""

from __future__ import annotations

import math
import statistics
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from typing import Literal


ACTION_SPACE_SIZE = 7
GateStatus = Literal["pass", "warn", "hard_stop"]


@dataclass(frozen=True)
class GeneralizationThresholds:
    """R-SAGE gate thresholds; these are project heuristics, not paper constants."""

    action_entropy_warn: float = 0.80
    action_entropy_hard_stop: float = 0.65
    max_action_share_warn: float = 0.35
    max_action_share_hard_stop: float = 0.50
    overfit_gap_hard_stop: float = 0.15
    overfit_gap_ci_lower_hard_stop: float = 0.05
    underfit_min_loss_reduction: float = 0.05
    underfit_max_preference_accuracy: float = 0.55
    underfit_max_reward_margin: float = 0.0
    plateau_min_dev_gain: float = 0.02
    seed_min_positive_count: int = 2
    seed_median_target_delta: float = 0.03
    seed_worst_target_delta: float = -0.02
    seed_max_spread: float = 0.10
    source: str = "project_empirical"
    source_zh: str = "项目经验值，非论文通用阈值"


DEFAULT_THRESHOLDS = GeneralizationThresholds()


@dataclass(frozen=True)
class GateDecision:
    """A deterministic, JSON-ready gate result."""

    status: GateStatus
    reason_codes: tuple[str, ...]
    metrics: dict[str, object]
    message_zh: str


@dataclass(frozen=True)
class LearningCurvePoint:
    """Metrics observed at one permanent 35%/70%/100% data fraction."""

    data_fraction: float
    train_loss: float
    preference_accuracy: float
    reward_margin: float
    train_score: float
    dev_score: float


@dataclass(frozen=True)
class SeedGateReport:
    """One registered seed's target delta and its checkpoint gate."""

    seed: int
    target_delta: float
    gate: GateDecision


_REASON_MESSAGES_ZH = {
    "NON_FINITE_METRIC": "训练指标出现 NaN 或 Inf",
    "CUDA_OOM": "训练发生显存溢出 OOM",
    "ANSWER_LEAK": "检测到可直接提交的答案泄漏",
    "FABRICATED_EVIDENCE": "检测到伪造或越界证据",
    "ACTION_COLLAPSE": "动作分布已塌缩",
    "ACTION_CONCENTRATION_WARNING": "动作分布过度集中",
    "OVERFIT_GAP": "训练集与开发集差距超过上限",
    "NO_OPTIMIZATION_SIGNAL": "未观察到足够的优化信号",
    "LEARNING_CURVE_PLATEAU": "35%、70% 与 100% 数据增量均进入平台期",
    "INSUFFICIENT_SEEDS": "未完成已登记的三个随机种子",
    "SEED_PROMOTION_FAILED": "三种子晋级收益不足",
    "SEED_INSTABILITY": "种子间结果波动超过上限",
}


def action_distribution_metrics(
    action_counts: Mapping[str, int],
    *,
    action_space_size: int = ACTION_SPACE_SIZE,
) -> dict[str, float]:
    """Return normalized Shannon entropy and the largest action share."""

    if action_space_size <= 1:
        raise ValueError("action_space_size must be greater than one")
    if not action_counts or any(count < 0 for count in action_counts.values()):
        raise ValueError("action_counts must contain non-negative counts")
    if len(action_counts) > action_space_size:
        raise ValueError("action_counts contains more actions than action_space_size")

    total = sum(action_counts.values())
    if total <= 0:
        raise ValueError("action_counts total must be positive")

    probabilities = [count / total for count in action_counts.values() if count]
    entropy = -sum(probability * math.log(probability) for probability in probabilities)
    return {
        "normalized_action_entropy": entropy / math.log(action_space_size),
        "max_action_share": max(probabilities),
    }


def evaluate_checkpoint(
    *,
    action_counts: Mapping[str, int],
    numeric_metrics: Mapping[str, float] | None = None,
    oom: bool = False,
    answer_leak_count: int = 0,
    fabricated_evidence_count: int = 0,
    thresholds: GeneralizationThresholds = DEFAULT_THRESHOLDS,
) -> GateDecision:
    """Evaluate checkpoint integrity, leakage, and action-distribution gates."""

    action_metrics = action_distribution_metrics(action_counts)
    metric_values = dict(numeric_metrics or {})
    non_finite_names = sorted(
        name for name, value in metric_values.items() if not math.isfinite(float(value))
    )

    hard_reasons: list[str] = []
    warning_reasons: list[str] = []
    if non_finite_names:
        hard_reasons.append("NON_FINITE_METRIC")
    if oom:
        hard_reasons.append("CUDA_OOM")
    if answer_leak_count > 0:
        hard_reasons.append("ANSWER_LEAK")
    if fabricated_evidence_count > 0:
        hard_reasons.append("FABRICATED_EVIDENCE")

    entropy = action_metrics["normalized_action_entropy"]
    max_share = action_metrics["max_action_share"]
    if (
        entropy < thresholds.action_entropy_hard_stop
        or max_share > thresholds.max_action_share_hard_stop
    ):
        hard_reasons.append("ACTION_COLLAPSE")
    elif (
        entropy < thresholds.action_entropy_warn
        or max_share > thresholds.max_action_share_warn
    ):
        warning_reasons.append("ACTION_CONCENTRATION_WARNING")

    reasons = tuple(hard_reasons or warning_reasons)
    status: GateStatus = "hard_stop" if hard_reasons else "warn" if warning_reasons else "pass"
    metrics: dict[str, object] = {
        **action_metrics,
        **metric_values,
        "non_finite_metric_names": non_finite_names,
        "oom": oom,
        "answer_leak_count": answer_leak_count,
        "fabricated_evidence_count": fabricated_evidence_count,
        "threshold_source": thresholds.source,
        "threshold_source_zh": thresholds.source_zh,
    }
    return GateDecision(
        status=status,
        reason_codes=reasons,
        metrics=metrics,
        message_zh=_message_zh(status, reasons),
    )


def evaluate_learning_curve(
    points: Sequence[LearningCurvePoint],
    *,
    final_gap_ci_lower: float | None = None,
    thresholds: GeneralizationThresholds = DEFAULT_THRESHOLDS,
) -> GateDecision:
    """Gate the permanent 35%/70%/100% learning curve."""

    ordered = sorted(points, key=lambda point: point.data_fraction)
    expected_fractions = (0.35, 0.70, 1.00)
    if len(ordered) != len(expected_fractions) or any(
        not math.isclose(point.data_fraction, expected, abs_tol=1e-9)
        for point, expected in zip(ordered, expected_fractions)
    ):
        raise ValueError("learning curve must contain exactly 35%, 70%, and 100% points")

    point_35, point_70, point_100 = ordered
    if point_35.train_loss == 0:
        raise ValueError("35% train_loss must be non-zero")

    loss_reduction = (point_35.train_loss - point_100.train_loss) / abs(
        point_35.train_loss
    )
    train_dev_gap = point_100.train_score - point_100.dev_score
    gain_35_70 = point_70.dev_score - point_35.dev_score
    gain_70_100 = point_100.dev_score - point_70.dev_score

    overfit = train_dev_gap > thresholds.overfit_gap_hard_stop and (
        final_gap_ci_lower is None
        or final_gap_ci_lower > thresholds.overfit_gap_ci_lower_hard_stop
    )
    underfit = (
        loss_reduction < thresholds.underfit_min_loss_reduction
        and point_100.preference_accuracy <= thresholds.underfit_max_preference_accuracy
        and point_100.reward_margin <= thresholds.underfit_max_reward_margin
    )
    plateau = (
        gain_35_70 < thresholds.plateau_min_dev_gain
        and gain_70_100 < thresholds.plateau_min_dev_gain
    )

    hard_reasons: list[str] = []
    warning_reasons: list[str] = []
    if overfit:
        hard_reasons.append("OVERFIT_GAP")
    if underfit:
        hard_reasons.append("NO_OPTIMIZATION_SIGNAL")
    if plateau:
        warning_reasons.append("LEARNING_CURVE_PLATEAU")

    reasons = tuple(hard_reasons or warning_reasons)
    status: GateStatus = "hard_stop" if hard_reasons else "warn" if warning_reasons else "pass"
    metrics: dict[str, object] = {
        "train_loss_relative_reduction": loss_reduction,
        "final_preference_accuracy": point_100.preference_accuracy,
        "final_reward_margin": point_100.reward_margin,
        "train_dev_gap": train_dev_gap,
        "final_gap_ci_lower": final_gap_ci_lower,
        "dev_gain_35_70": gain_35_70,
        "dev_gain_70_100": gain_70_100,
        "threshold_source": thresholds.source,
        "threshold_source_zh": thresholds.source_zh,
    }
    return GateDecision(
        status=status,
        reason_codes=reasons,
        metrics=metrics,
        message_zh=_message_zh(status, reasons),
    )


def aggregate_seed_gates(
    reports: Sequence[SeedGateReport],
    *,
    expected_seeds: Sequence[int] = (42, 43, 44),
    thresholds: GeneralizationThresholds = DEFAULT_THRESHOLDS,
) -> GateDecision:
    """Promote only a stable three-seed result with no seed-level hard stop."""

    report_list = list(reports)
    expected = tuple(expected_seeds)
    actual = tuple(report.seed for report in report_list)
    base_metrics: dict[str, object] = {
        "expected_seeds": list(expected),
        "actual_seeds": list(actual),
        "threshold_source": thresholds.source,
        "threshold_source_zh": thresholds.source_zh,
    }
    if len(actual) != len(expected) or set(actual) != set(expected):
        reasons = ("INSUFFICIENT_SEEDS",)
        return GateDecision(
            status="hard_stop",
            reason_codes=reasons,
            metrics=base_metrics,
            message_zh=_message_zh("hard_stop", reasons),
        )

    deltas = [float(report.target_delta) for report in report_list]
    non_finite_seeds = [
        report.seed
        for report, delta in zip(report_list, deltas)
        if not math.isfinite(delta)
    ]
    hard_reasons = [
        reason
        for report in report_list
        if report.gate.status == "hard_stop"
        for reason in report.gate.reason_codes
    ]
    if non_finite_seeds:
        hard_reasons.append("NON_FINITE_METRIC")

    if non_finite_seeds:
        reasons = _unique_reasons(hard_reasons)
        metrics = {**base_metrics, "non_finite_target_delta_seeds": non_finite_seeds}
        return GateDecision(
            status="hard_stop",
            reason_codes=reasons,
            metrics=metrics,
            message_zh=_message_zh("hard_stop", reasons),
        )

    positive_count = sum(delta > 0 for delta in deltas)
    median_delta = statistics.median(deltas)
    worst_delta = min(deltas)
    spread = max(deltas) - min(deltas)
    promotion_passes = (
        positive_count >= thresholds.seed_min_positive_count
        and median_delta >= thresholds.seed_median_target_delta
        and worst_delta >= thresholds.seed_worst_target_delta
        and spread <= thresholds.seed_max_spread
    )
    if not promotion_passes:
        hard_reasons.append("SEED_PROMOTION_FAILED")
    if spread > thresholds.seed_max_spread:
        hard_reasons.append("SEED_INSTABILITY")

    reasons = _unique_reasons(hard_reasons)
    status: GateStatus = "hard_stop" if reasons else "pass"
    metrics = {
        **base_metrics,
        "target_deltas": {str(report.seed): delta for report, delta in zip(report_list, deltas)},
        "positive_seed_count": positive_count,
        "median_target_delta": median_delta,
        "worst_target_delta": worst_delta,
        "seed_spread": spread,
    }
    return GateDecision(
        status=status,
        reason_codes=reasons,
        metrics=metrics,
        message_zh=_message_zh(status, reasons),
    )


def _unique_reasons(reasons: Sequence[str]) -> tuple[str, ...]:
    return tuple(dict.fromkeys(reasons))


def _message_zh(status: GateStatus, reasons: tuple[str, ...]) -> str:
    if status == "pass":
        return "通过：未触发泛化门禁。"
    prefix = "硬停止" if status == "hard_stop" else "警告"
    details = "；".join(_REASON_MESSAGES_ZH[reason] for reason in reasons)
    return f"{prefix}：{details}。"
