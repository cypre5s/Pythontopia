from __future__ import annotations

import math

import pytest
import torch
import torch.nn.functional as F

from pyt_agent_lab.gdpo_rpo import gdpo_loss, gdpo_rpo_loss, summarize_preference_q


def test_q_one_reduces_to_ordinary_dpo() -> None:
    delta = torch.tensor([-1.2, 0.0, 2.5])
    q = torch.ones_like(delta)
    beta = 0.1

    actual = gdpo_loss(delta, q, beta=beta, reduction="none")
    expected = -F.logsigmoid(beta * delta)

    assert torch.allclose(actual, expected)


def test_q_half_has_zero_preference_gradient_scaling() -> None:
    delta = torch.tensor([3.0, -4.0], requires_grad=True)
    q = torch.full_like(delta, 0.5)

    loss = gdpo_loss(delta, q, beta=0.2, reduction="mean")
    loss.backward()

    assert loss.item() == pytest.approx(math.log(2), rel=1e-6)
    assert torch.equal(delta.grad, torch.zeros_like(delta))


def test_chosen_nll_changes_total_independently_from_soft_preference() -> None:
    delta = torch.tensor([0.3, -0.2])
    q = torch.tensor([2 / 3, 1.0])
    chosen_nll = torch.tensor([1.2, 0.8])

    without_rpo = gdpo_rpo_loss(
        delta,
        q,
        chosen_nll,
        beta=0.1,
        lambda_sft=0.0,
    )
    with_rpo = gdpo_rpo_loss(
        delta,
        q,
        chosen_nll,
        beta=0.1,
        lambda_sft=0.2,
    )

    assert with_rpo.gdpo.item() == pytest.approx(without_rpo.gdpo.item())
    assert with_rpo.chosen_nll.item() == pytest.approx(1.0)
    assert with_rpo.total.item() - without_rpo.total.item() == pytest.approx(0.2)


@pytest.mark.parametrize("q", [0.49, 1.01, float("nan")])
def test_soft_probability_outside_closed_interval_fails(q: float) -> None:
    with pytest.raises(ValueError, match="软偏好概率"):
        gdpo_loss(torch.tensor([0.0]), torch.tensor([q]), beta=0.1)


def test_negative_regularizer_weight_fails() -> None:
    with pytest.raises(ValueError, match="chosen NLL 正则系数"):
        gdpo_rpo_loss(
            torch.tensor([0.0]),
            torch.tensor([1.0]),
            torch.tensor([1.0]),
            beta=0.1,
            lambda_sft=-0.1,
        )


def test_preference_q_preflight_reports_distribution() -> None:
    summary = summarize_preference_q(
        [
            {"preference_q": 0.5},
            {"preference_q": 2 / 3},
            {"preference_q": 1.0},
        ]
    )

    assert summary["count"] == 3
    assert summary["minimum"] == 0.5
    assert summary["maximum"] == 1.0
    assert summary["half_count"] == 1
    assert summary["unanimous_count"] == 1
    assert summary["message_zh"] == "已核验 3 条软偏好概率，全部位于 0.5 到 1.0"


def test_preference_q_preflight_rejects_missing_values() -> None:
    with pytest.raises(ValueError, match="缺少 preference_q"):
        summarize_preference_q([{"id": "pref-1"}])
