"""离线评测打分器：Ragas faithfulness / context_recall + DeepEval answer_relevancy。

落地主契约 §3.6 第 2 条 / §4「Stream O」第 3 条。设计铁律（§0 红线）：

- **惰性导入**：``ragas`` / ``deepeval`` 一律在函数内 import；缺包不在模块导入期炸，
  调用时优雅降级为 ``ScoreResult(status="skipped", backend="unavailable")``，绝不抛、
  绝不拖垮主链路（观测/评测 fail-soft）。
- **judge 可注入**：每个 scorer 接受 ``judge: Judge | None``。注入时直接用 judge 产出
  确定分（``backend="injected"``），**完全绕开** ragas/deepeval —— 因此单元测试零外网、
  零真实 LLM、且不依赖重包安装。
- **真实后端的 LLM 评审亦可注入**：未注入 judge 时走真实 ragas/deepeval；其底层 evaluator
  LLM 经 ``llm`` / ``model`` 注入（离线脚本配置），缺评审器时优雅 skipped。

本模块**不**触碰 ``observability/**``（兄弟单元 O1 负责）；在线 span 评分（record_eval_score）
不在本评测 harness 职责内。
"""

from __future__ import annotations

import asyncio
import concurrent.futures
import math
from collections.abc import Callable, Mapping, Sequence
from typing import Any, Literal

from pydantic import BaseModel, Field

# ── metric 名常量（与 Langfuse score name / span 属性键命名空间对齐）─────────────
METRIC_FAITHFULNESS = "faithfulness"
METRIC_ANSWER_RELEVANCY = "answer_relevancy"
METRIC_CONTEXT_RECALL = "context_recall"

#: 默认评测的 metric 集合（顺序稳定，便于汇总/展示）。
DEFAULT_METRICS: tuple[str, ...] = (
    METRIC_FAITHFULNESS,
    METRIC_ANSWER_RELEVANCY,
    METRIC_CONTEXT_RECALL,
)

ScoreStatus = Literal["ok", "skipped", "error"]
ScoreBackend = Literal["injected", "ragas", "deepeval", "unavailable"]


class EvalSample(BaseModel):
    """单条 golden 评测样本（数据集 jsonl 的一行）。"""

    id: str = ""
    question: str  # 学生问题 / RAG query（ragas user_input、deepeval input）
    answer: str  # 系统回答（ragas response、deepeval actual_output）
    contexts: list[str] = Field(default_factory=list)  # 召回上下文（retrieved_contexts）
    reference: str = ""  # 参考答案 / ground truth（context_recall 用；空=无参考）
    tags: list[str] = Field(default_factory=list)  # 主题标签，仅审计可读，不充当课件引用


class ScoreResult(BaseModel):
    """单个 metric 对单条样本的打分结果。"""

    metric: str
    value: float | None = None  # None = 未出分（skipped / error）
    backend: ScoreBackend = "injected"
    status: ScoreStatus = "ok"
    detail: str = ""  # 降级 / 失败原因（可读）

    @property
    def scored(self) -> bool:
        """是否产出了有效分（status=ok 且 value 非 None）。"""
        return self.status == "ok" and self.value is not None


#: 注入式打分器：给一条样本直接产出 [0,1] 分（测试用 mock / 自定义启发式）。
Judge = Callable[[EvalSample], Any]


# ── 公开 scorer：三个 metric ───────────────────────────────────────────────────


def score_faithfulness(
    sample: EvalSample, *, judge: Judge | None = None, llm: Any | None = None
) -> ScoreResult:
    """忠实度（回答是否被 contexts 支撑）。Ragas ``Faithfulness``；judge 可注入。"""
    if judge is not None:
        return _score_with_injected(METRIC_FAITHFULNESS, sample, judge)
    return _score_with_ragas(METRIC_FAITHFULNESS, sample, llm=llm)


def score_context_recall(
    sample: EvalSample, *, judge: Judge | None = None, llm: Any | None = None
) -> ScoreResult:
    """上下文召回（参考答案是否被 contexts 覆盖）。Ragas ``LLMContextRecall``；judge 可注入。"""
    if judge is not None:
        return _score_with_injected(METRIC_CONTEXT_RECALL, sample, judge)
    return _score_with_ragas(METRIC_CONTEXT_RECALL, sample, llm=llm)


def score_answer_relevancy(
    sample: EvalSample, *, judge: Judge | None = None, model: Any | None = None
) -> ScoreResult:
    """回答相关性（回答是否切题）。DeepEval ``AnswerRelevancyMetric``；judge 可注入。"""
    if judge is not None:
        return _score_with_injected(METRIC_ANSWER_RELEVANCY, sample, judge)
    return _score_with_deepeval(sample, model=model)


# ── 多 metric 调度 ─────────────────────────────────────────────────────────────


def score_sample(
    sample: EvalSample,
    *,
    metrics: Sequence[str] = DEFAULT_METRICS,
    judges: Mapping[str, Judge] | None = None,
    llm: Any | None = None,
    model: Any | None = None,
) -> list[ScoreResult]:
    """对一条样本跑 ``metrics`` 列出的全部 metric，返回结果列表（顺序与 metrics 一致）。

    ``judges`` 为 ``{metric: Judge}`` 映射：某 metric 有 judge 则用注入分，否则走真实后端。
    未知 metric → ``status="error"``（不抛）。
    """
    results: list[ScoreResult] = []
    for metric in metrics:
        judge = judges.get(metric) if judges is not None else None
        if metric == METRIC_FAITHFULNESS:
            results.append(score_faithfulness(sample, judge=judge, llm=llm))
        elif metric == METRIC_CONTEXT_RECALL:
            results.append(score_context_recall(sample, judge=judge, llm=llm))
        elif metric == METRIC_ANSWER_RELEVANCY:
            results.append(score_answer_relevancy(sample, judge=judge, model=model))
        else:
            results.append(
                ScoreResult(
                    metric=metric,
                    value=None,
                    backend="unavailable",
                    status="error",
                    detail=f"unknown metric: {metric!r}",
                )
            )
    return results


def uniform_judges(judge: Judge, metrics: Sequence[str] = DEFAULT_METRICS) -> dict[str, Judge]:
    """把单个 judge 复用到所有 metric，返回 ``{metric: judge}``（测试 / 统一启发式便利）。"""
    return {metric: judge for metric in metrics}


# ── 内部：注入路径 ─────────────────────────────────────────────────────────────


def _score_with_injected(metric: str, sample: EvalSample, judge: Judge) -> ScoreResult:
    """用注入 judge 出分；judge 抛异常 / 返回非数值都降级为 error（不上抛）。"""
    try:
        raw = judge(sample)
    except Exception as exc:  # noqa: BLE001 - 评测层 fail-soft：mock/启发式异常不拖垮 runner
        return ScoreResult(
            metric=metric,
            value=None,
            backend="injected",
            status="error",
            detail=f"judge raised: {exc!r}",
        )
    value = _clamp01(raw)
    if value is None:
        return ScoreResult(
            metric=metric,
            value=None,
            backend="injected",
            status="error",
            detail=f"judge returned non-numeric score: {raw!r}",
        )
    return ScoreResult(metric=metric, value=value, backend="injected", status="ok")


# ── 内部：Ragas 真实路径（惰性导入）────────────────────────────────────────────


def _score_with_ragas(metric: str, sample: EvalSample, *, llm: Any | None) -> ScoreResult:
    """Ragas faithfulness / context_recall。缺包 → skipped；缺 evaluator LLM → skipped。"""
    try:
        from ragas.dataset_schema import (  # type: ignore[import-not-found]
            SingleTurnSample,
        )

        if metric == METRIC_FAITHFULNESS:
            from ragas.metrics import (  # type: ignore[import-not-found]
                Faithfulness as _RagasMetric,
            )
        else:
            from ragas.metrics import LLMContextRecall as _RagasMetric
    except Exception as exc:  # noqa: BLE001 - 缺包/导入失败优雅降级（fail-soft）
        return ScoreResult(
            metric=metric,
            value=None,
            backend="unavailable",
            status="skipped",
            detail=f"ragas unavailable: {exc!r}",
        )

    if llm is None:
        return ScoreResult(
            metric=metric,
            value=None,
            backend="ragas",
            status="skipped",
            detail="no evaluator llm injected; offline ragas path needs a ragas-compatible llm",
        )

    try:
        single_turn = SingleTurnSample(
            user_input=sample.question,
            response=sample.answer,
            retrieved_contexts=list(sample.contexts),
            reference=sample.reference or None,
        )
        scorer = _RagasMetric(llm=llm)
        raw = _run_coroutine_blocking(scorer.single_turn_ascore(single_turn))
    except Exception as exc:  # noqa: BLE001 - 评测失败 fail-soft，不拖垮 runner
        return ScoreResult(
            metric=metric,
            value=None,
            backend="ragas",
            status="error",
            detail=f"ragas scoring failed: {exc!r}",
        )

    value = _clamp01(raw)
    if value is None:
        return ScoreResult(
            metric=metric,
            value=None,
            backend="ragas",
            status="error",
            detail=f"ragas returned non-numeric score: {raw!r}",
        )
    return ScoreResult(metric=metric, value=value, backend="ragas", status="ok")


# ── 内部：DeepEval 真实路径（惰性导入）─────────────────────────────────────────


def _score_with_deepeval(sample: EvalSample, *, model: Any | None) -> ScoreResult:
    """DeepEval answer_relevancy。缺包 → skipped；评测异常（如缺评审 LLM）→ error。"""
    metric = METRIC_ANSWER_RELEVANCY
    try:
        from deepeval.metrics import (  # type: ignore[import-not-found]
            AnswerRelevancyMetric,
        )
        from deepeval.test_case import LLMTestCase  # type: ignore[import-not-found]
    except Exception as exc:  # noqa: BLE001 - 缺包/导入失败优雅降级（fail-soft）
        return ScoreResult(
            metric=metric,
            value=None,
            backend="unavailable",
            status="skipped",
            detail=f"deepeval unavailable: {exc!r}",
        )

    try:
        kwargs: dict[str, Any] = {
            "threshold": 0.5,
            "async_mode": False,  # 评测 harness 同步跑，避免事件循环重入
            "include_reason": False,
        }
        if model is not None:
            kwargs["model"] = model
        metric_impl = AnswerRelevancyMetric(**kwargs)
        test_case = LLMTestCase(
            input=sample.question,
            actual_output=sample.answer,
            retrieval_context=list(sample.contexts) or None,
        )
        metric_impl.measure(test_case)
        raw = metric_impl.score
    except Exception as exc:  # noqa: BLE001 - 评测失败 fail-soft（如缺评审 LLM/网络）
        return ScoreResult(
            metric=metric,
            value=None,
            backend="deepeval",
            status="error",
            detail=f"deepeval scoring failed: {exc!r}",
        )

    value = _clamp01(raw)
    if value is None:
        return ScoreResult(
            metric=metric,
            value=None,
            backend="deepeval",
            status="error",
            detail=f"deepeval returned non-numeric score: {raw!r}",
        )
    return ScoreResult(metric=metric, value=value, backend="deepeval", status="ok")


# ── 内部工具 ───────────────────────────────────────────────────────────────────


def _clamp01(raw: Any) -> float | None:
    """把任意值转 float 并裁剪到 [0,1]；非数值 / NaN / inf → None。"""
    try:
        value = float(raw)
    except (TypeError, ValueError):
        return None
    if not math.isfinite(value):
        return None
    return max(0.0, min(1.0, value))


def _run_coroutine_blocking(coro: Any) -> Any:
    """同步阻塞跑一个协程（Ragas ``single_turn_ascore`` 为 async）。

    评测 harness 离线运行；若当前线程已有运行中的事件循环（被异步上下文调用），则把协程
    丢到独立线程的新循环里跑，避免 ``asyncio.run`` 重入报错。
    """
    try:
        asyncio.get_running_loop()
    except RuntimeError:
        return asyncio.run(coro)
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
        return executor.submit(asyncio.run, coro).result()
