"""eval.scorers 覆盖测试（O2-eval-harness）。

测试矩阵（对齐主契约 §3.6 / §4「Stream O」第 3 条 + §0 红线）：

- 注入 judge/mock → 三个 metric（faithfulness / answer_relevancy / context_recall）出分；
  该路径**完全绕开** ragas/deepeval，故零外网、零真实 LLM、且不依赖重包安装。
- judge 返回越界值 → 裁剪到 [0,1]；返回非数值 / 抛异常 → status=error（fail-soft 不上抛）。
- 缺 ragas / deepeval 包（用 sys.modules 注 None 模拟）→ 清晰降级 status=skipped、
  backend=unavailable、value=None，绝不在导入期或调用期炸。
- score_sample 多 metric 调度 + uniform_judges 辅助。
- EvalSample / ScoreResult 模型行为。
"""

from __future__ import annotations

import sys
from collections.abc import Callable

import pytest

from pythontopia_graph.eval import scorers
from pythontopia_graph.eval.scorers import EvalSample, ScoreResult


# ── 辅助 ──────────────────────────────────────────────────────────────────────


def const_judge(value: object) -> Callable[[EvalSample], object]:
    """返回一个忽略样本、恒定产出 ``value`` 的 mock judge。"""

    def _judge(_sample: EvalSample) -> object:
        return value

    return _judge


def _sample(**over: object) -> EvalSample:
    base: dict[str, object] = {
        "question": "Python 里怎么读取用户输入？",
        "answer": "用内置函数 input()，返回字符串。",
        "contexts": ["input() 是 Python 内置函数，从标准输入读取一行并返回 str。"],
        "reference": "用 input() 读取，返回 str。",
    }
    base.update(over)
    return EvalSample(**base)


@pytest.fixture
def no_ragas(monkeypatch: pytest.MonkeyPatch) -> None:
    """模拟「缺 ragas 包」：sys.modules 置 None 使惰性 import 抛 ImportError。"""
    for name in ("ragas", "ragas.dataset_schema", "ragas.metrics", "ragas.llms"):
        monkeypatch.setitem(sys.modules, name, None)


@pytest.fixture
def no_deepeval(monkeypatch: pytest.MonkeyPatch) -> None:
    """模拟「缺 deepeval 包」。"""
    for name in ("deepeval", "deepeval.metrics", "deepeval.test_case"):
        monkeypatch.setitem(sys.modules, name, None)


# ── 1. 注入 judge → 出分（三个 metric） ──────────────────────────────────────


def test_faithfulness_injected_judge_scores() -> None:
    result = scorers.score_faithfulness(_sample(), judge=const_judge(0.8))
    assert result.metric == scorers.METRIC_FAITHFULNESS
    assert result.backend == "injected"
    assert result.status == "ok"
    assert result.value == pytest.approx(0.8)
    assert result.scored is True


def test_answer_relevancy_injected_judge_scores() -> None:
    result = scorers.score_answer_relevancy(_sample(), judge=const_judge(0.65))
    assert result.metric == scorers.METRIC_ANSWER_RELEVANCY
    assert result.backend == "injected"
    assert result.status == "ok"
    assert result.value == pytest.approx(0.65)


def test_context_recall_injected_judge_scores() -> None:
    result = scorers.score_context_recall(_sample(), judge=const_judge(0.5))
    assert result.metric == scorers.METRIC_CONTEXT_RECALL
    assert result.backend == "injected"
    assert result.value == pytest.approx(0.5)


# ── 2. 裁剪 / 非数值 / 异常（fail-soft） ─────────────────────────────────────


def test_injected_judge_clamps_above_one() -> None:
    result = scorers.score_faithfulness(_sample(), judge=const_judge(1.7))
    assert result.value == 1.0


def test_injected_judge_clamps_below_zero() -> None:
    result = scorers.score_answer_relevancy(_sample(), judge=const_judge(-2.0))
    assert result.value == 0.0


def test_injected_judge_non_numeric_is_error() -> None:
    result = scorers.score_faithfulness(_sample(), judge=const_judge("very faithful"))
    assert result.status == "error"
    assert result.value is None
    assert result.scored is False


def test_injected_judge_nan_is_error() -> None:
    result = scorers.score_context_recall(_sample(), judge=const_judge(float("nan")))
    assert result.status == "error"
    assert result.value is None


def test_injected_judge_exception_is_caught_not_raised() -> None:
    def boom(_sample: EvalSample) -> float:
        raise RuntimeError("judge exploded")

    result = scorers.score_answer_relevancy(_sample(), judge=boom)
    assert result.status == "error"
    assert result.value is None
    assert "judge exploded" in result.detail


def test_injected_judge_receives_sample() -> None:
    seen: list[EvalSample] = []

    def capture(sample: EvalSample) -> float:
        seen.append(sample)
        return 0.9

    sample = _sample(question="独特问题标记")
    scorers.score_faithfulness(sample, judge=capture)
    assert len(seen) == 1
    assert seen[0].question == "独特问题标记"


# ── 3. 缺 ragas / deepeval 包 → 清晰降级（skipped） ──────────────────────────


def test_faithfulness_skips_when_ragas_missing(no_ragas: None) -> None:
    result = scorers.score_faithfulness(_sample())  # 无 judge → 走真实后端
    assert result.status == "skipped"
    assert result.backend == "unavailable"
    assert result.value is None
    assert "ragas" in result.detail.lower()


def test_context_recall_skips_when_ragas_missing(no_ragas: None) -> None:
    result = scorers.score_context_recall(_sample())
    assert result.status == "skipped"
    assert result.backend == "unavailable"
    assert result.value is None


def test_answer_relevancy_skips_when_deepeval_missing(no_deepeval: None) -> None:
    result = scorers.score_answer_relevancy(_sample())
    assert result.status == "skipped"
    assert result.backend == "unavailable"
    assert result.value is None
    assert "deepeval" in result.detail.lower()


def test_answer_relevancy_without_explicit_model_does_not_use_deepeval_default(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class UnexpectedDefaultMetric:
        def __init__(self, **_kwargs: object) -> None:
            raise AssertionError("DeepEval default provider must not be selected")

    import types

    metrics = types.ModuleType("deepeval.metrics")
    metrics.AnswerRelevancyMetric = UnexpectedDefaultMetric  # type: ignore[attr-defined]
    test_case = types.ModuleType("deepeval.test_case")
    test_case.LLMTestCase = object  # type: ignore[attr-defined]
    monkeypatch.setitem(sys.modules, "deepeval.metrics", metrics)
    monkeypatch.setitem(sys.modules, "deepeval.test_case", test_case)

    result = scorers.score_answer_relevancy(_sample())

    assert result.status == "skipped"
    assert result.backend == "unavailable"
    assert "explicit" in result.detail.lower()


def test_missing_package_never_raises_on_import(no_ragas: None, no_deepeval: None) -> None:
    # 三个 scorer 在缺包时都返回结果对象、绝不抛。
    for fn in (scorers.score_faithfulness, scorers.score_context_recall):
        assert fn(_sample()).status == "skipped"
    assert scorers.score_answer_relevancy(_sample()).status == "skipped"


# ── 4. 真实后端存在但缺 evaluator LLM → 优雅 skipped（按需跳过） ─────────────


def test_ragas_present_without_llm_skips() -> None:
    pytest.importorskip("ragas")
    result = scorers.score_faithfulness(_sample())  # 无 judge、无 llm
    assert result.status == "skipped"
    assert "llm" in result.detail.lower()


# ── 5. score_sample 多 metric 调度 + uniform_judges ─────────────────────────


def test_score_sample_runs_all_default_metrics() -> None:
    judges = {
        scorers.METRIC_FAITHFULNESS: const_judge(0.9),
        scorers.METRIC_ANSWER_RELEVANCY: const_judge(0.7),
        scorers.METRIC_CONTEXT_RECALL: const_judge(0.5),
    }
    results = scorers.score_sample(_sample(), judges=judges)
    by_metric = {r.metric: r for r in results}
    assert set(by_metric) == set(scorers.DEFAULT_METRICS)
    assert by_metric[scorers.METRIC_FAITHFULNESS].value == pytest.approx(0.9)
    assert by_metric[scorers.METRIC_ANSWER_RELEVANCY].value == pytest.approx(0.7)
    assert by_metric[scorers.METRIC_CONTEXT_RECALL].value == pytest.approx(0.5)


def test_score_sample_respects_metric_subset() -> None:
    results = scorers.score_sample(
        _sample(),
        metrics=[scorers.METRIC_FAITHFULNESS],
        judges=scorers.uniform_judges(const_judge(0.42)),
    )
    assert len(results) == 1
    assert results[0].metric == scorers.METRIC_FAITHFULNESS
    assert results[0].value == pytest.approx(0.42)


def test_score_sample_unknown_metric_is_error() -> None:
    results = scorers.score_sample(_sample(), metrics=["nonsense_metric"])
    assert len(results) == 1
    assert results[0].status == "error"
    assert results[0].value is None


def test_uniform_judges_covers_default_metrics() -> None:
    judges = scorers.uniform_judges(const_judge(0.6))
    assert set(judges) == set(scorers.DEFAULT_METRICS)
    for fn in judges.values():
        assert fn(_sample()) == 0.6


def test_score_sample_missing_judge_for_metric_falls_back_to_backend(
    no_ragas: None, no_deepeval: None
) -> None:
    # judges 只给了 faithfulness；其余 metric 无 judge → 走真实后端 → 缺包 skipped。
    judges = {scorers.METRIC_FAITHFULNESS: const_judge(0.88)}
    results = scorers.score_sample(_sample(), judges=judges)
    by_metric = {r.metric: r for r in results}
    assert by_metric[scorers.METRIC_FAITHFULNESS].value == pytest.approx(0.88)
    assert by_metric[scorers.METRIC_ANSWER_RELEVANCY].status == "skipped"
    assert by_metric[scorers.METRIC_CONTEXT_RECALL].status == "skipped"


# ── 6. 模型对象行为 ─────────────────────────────────────────────────────────


def test_score_result_scored_property() -> None:
    assert ScoreResult(metric="m", value=0.5, status="ok").scored is True
    assert ScoreResult(metric="m", value=None, status="skipped").scored is False
    assert ScoreResult(metric="m", value=0.0, status="ok").scored is True
    assert ScoreResult(metric="m", value=0.5, status="error").scored is False


def test_eval_sample_defaults() -> None:
    sample = EvalSample(question="q", answer="a")
    assert sample.contexts == []
    assert sample.reference == ""
    assert sample.tags == []
    assert sample.id == ""
