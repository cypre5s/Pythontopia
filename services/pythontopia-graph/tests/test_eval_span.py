"""observability.eval_span.record_eval_score 覆盖测试（O1-otel-langfuse）。

并发构建主契约 §3.6 第 3 条：``record_eval_score(name, value)`` 把在线轻量评分写到
当前活动 span 的 ``pythontopia.eval.{name}`` 属性。与 otel.py 同范式：缺 ``opentelemetry`` /
无活动 span / 任何异常 → no-op，绝不影响主链路（§0 fail-soft 观测）。

测试矩阵：
- 有活动 span → 写入 ``pythontopia.eval.{name}`` 属性（真实 SDK + InMemorySpanExporter）
- 多个评分各写各的属性
- 无活动 span → no-op（OTel INVALID_SPAN，set_attribute 安全 no-op，且不串进后续 span）
- _ENABLED=False（缺 opentelemetry 包）→ no-op（不抛）
- get_current_span 抛异常 → 被吞（no-op）
- value 不可转 float → 被吞（no-op，不污染 span）
"""

from __future__ import annotations

from typing import Any

import pytest

from pythontopia_graph.observability import eval_span, otel

_EVAL_PREFIX = "pythontopia.eval."


# ─────────────────────────────────────────────────────────────────────────────
# 降级 / 异常路径（始终运行，不依赖真实 span 后端）
# ─────────────────────────────────────────────────────────────────────────────


@pytest.fixture
def disabled_eval_span(monkeypatch: pytest.MonkeyPatch) -> None:
    """模拟「缺 opentelemetry 包」：强制 _ENABLED=False。monkeypatch 自动复原。"""
    monkeypatch.setattr(eval_span, "_ENABLED", False)
    monkeypatch.setattr(eval_span, "_otel_trace", None)


def test_record_eval_score_noop_when_disabled(disabled_eval_span: None) -> None:
    assert eval_span.record_eval_score("faithfulness", 0.91) is None


def test_record_eval_score_no_active_span_is_noop() -> None:
    # otel 安装但当前无活动 span：get_current_span 返回 INVALID_SPAN，set_attribute 安全 no-op。
    assert eval_span.record_eval_score("grounding_pass", 1.0) is None


def test_record_eval_score_swallows_exception(monkeypatch: pytest.MonkeyPatch) -> None:
    class _Boom:
        def get_current_span(self) -> Any:
            raise RuntimeError("span lookup failed")

    monkeypatch.setattr(eval_span, "_ENABLED", True)
    monkeypatch.setattr(eval_span, "_otel_trace", _Boom())
    assert eval_span.record_eval_score("safety_pass", 0.0) is None


def test_record_eval_score_swallows_non_numeric_value(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class _Span:
        def set_attribute(self, *_: Any) -> None:  # pragma: no cover - 不应被触达
            raise AssertionError("value 不可转 float 时不应调用 set_attribute")

    class _Trace:
        def get_current_span(self) -> Any:
            return _Span()

    monkeypatch.setattr(eval_span, "_ENABLED", True)
    monkeypatch.setattr(eval_span, "_otel_trace", _Trace())
    assert eval_span.record_eval_score("weird", object()) is None  # type: ignore[arg-type]


def test_is_enabled_returns_bool() -> None:
    assert isinstance(eval_span.is_enabled(), bool)


# ─────────────────────────────────────────────────────────────────────────────
# 有依赖 → 写当前 span 属性（otel 未装则整组 importorskip 跳过）
# ─────────────────────────────────────────────────────────────────────────────


@pytest.fixture
def span_exporter() -> Any:
    """安装/复用真正的 SDK TracerProvider，挂一枚 InMemorySpanExporter 抓取 span。"""
    pytest.importorskip("opentelemetry.sdk.trace")
    from opentelemetry import trace
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import SimpleSpanProcessor
    from opentelemetry.sdk.trace.export.in_memory_span_exporter import (
        InMemorySpanExporter,
    )

    otel.init_tracing()
    provider = trace.get_tracer_provider()
    if not isinstance(provider, TracerProvider):
        provider = TracerProvider()
        trace.set_tracer_provider(provider)
    exporter = InMemorySpanExporter()
    provider.add_span_processor(SimpleSpanProcessor(exporter))
    return exporter


def test_record_eval_score_sets_attribute_on_current_span(span_exporter: Any) -> None:
    with otel.traced_tool("grounding_critic"):
        eval_span.record_eval_score("faithfulness", 0.875)

    spans = span_exporter.get_finished_spans()
    assert len(spans) == 1
    assert spans[0].attributes[f"{_EVAL_PREFIX}faithfulness"] == pytest.approx(0.875)


def test_record_eval_score_multiple_scores(span_exporter: Any) -> None:
    with otel.traced_tool("critic"):
        eval_span.record_eval_score("grounding_pass", 1.0)
        eval_span.record_eval_score("safety_pass", 0.0)

    attrs = dict(span_exporter.get_finished_spans()[0].attributes)
    assert attrs[f"{_EVAL_PREFIX}grounding_pass"] == pytest.approx(1.0)
    assert attrs[f"{_EVAL_PREFIX}safety_pass"] == pytest.approx(0.0)


def test_record_eval_score_outside_span_records_nothing(span_exporter: Any) -> None:
    # 无活动 span 时调用 → 不产生 span、不抛，且不串进后续 span。
    eval_span.record_eval_score("orphan", 0.5)
    with otel.traced_tool("later"):
        pass
    span = span_exporter.get_finished_spans()[0]
    assert f"{_EVAL_PREFIX}orphan" not in dict(span.attributes)
