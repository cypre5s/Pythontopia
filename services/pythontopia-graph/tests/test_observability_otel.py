"""observability.otel（OpenTelemetry GenAI 埋点地基）覆盖测试。

两组场景（对齐并发构建主契约 §5.6 与本 agent 任务）：

1. **无依赖 / 禁用时全链路 no-op**：通过 monkeypatch ``_ENABLED=False`` 模拟「缺
   opentelemetry 包」，断言 init_tracing / traced_tool（上下文管理器 + 装饰器）/
   record_token_usage 均不抛错、不吞业务异常。该组**始终运行**（不依赖 otel 安装）。
2. **有依赖时进入 span 分支**：用真实 SDK + InMemorySpanExporter 抓取 span，断言遵循
   GenAI 语义约定（span 名、operation/tool 属性、token 用量、SpanKind、异常记录、
   敏感参数默认不记录）。otel 未安装时整组 importorskip 跳过。
"""

from __future__ import annotations

from typing import Any

import pytest

from pythontopia_graph.observability import otel


# ─────────────────────────────────────────────────────────────────────────────
# 1. 无依赖 / 禁用 → 全链路 no-op（始终运行）
# ─────────────────────────────────────────────────────────────────────────────


@pytest.fixture
def disabled_otel(monkeypatch: pytest.MonkeyPatch) -> None:
    """模拟「缺 opentelemetry 包」：强制 _ENABLED=False。monkeypatch 自动复原。"""
    monkeypatch.setattr(otel, "_ENABLED", False)
    monkeypatch.setattr(otel, "_otel_trace", None)
    monkeypatch.setattr(otel, "_initialized", False)


def test_is_enabled_false_when_disabled(disabled_otel: None) -> None:
    assert otel.is_enabled() is False


def test_init_tracing_noop_when_disabled(disabled_otel: None) -> None:
    # 不抛错；缺包时连初始化都不做（_initialized 不被置位），保持纯 no-op。
    otel.init_tracing()
    assert otel._initialized is False


def test_traced_tool_context_manager_noop_yields_none(disabled_otel: None) -> None:
    with otel.traced_tool("rag_search") as span:
        assert span is None


def test_traced_tool_decorator_noop_returns_value(disabled_otel: None) -> None:
    @otel.traced_tool("paper_designer")
    def do_work(x: int) -> int:
        return x * 2

    assert do_work(21) == 42
    # 装饰器可重复调用（contextmanager 装饰器 _recreate_cm 语义）。
    assert do_work(1) == 2


def test_record_token_usage_noop_when_disabled(disabled_otel: None) -> None:
    assert otel.record_token_usage(100, 50) is None


def test_traced_tool_noop_does_not_swallow_body_exception(disabled_otel: None) -> None:
    """no-op 路径也必须 fail fast：被包裹代码的异常照常上抛，不被吞。"""
    with pytest.raises(ValueError, match="boom"):
        with otel.traced_tool("flaky"):
            raise ValueError("boom")


def test_record_token_usage_with_active_span_does_not_throw(disabled_otel: None) -> None:
    """禁用态下即便在 traced_tool 内记录用量也安全 no-op。"""
    with otel.traced_tool("combo"):
        otel.record_token_usage(7, 3)


# ─────────────────────────────────────────────────────────────────────────────
# 2. 有依赖 → 进入 span 分支（otel 未装则整组跳过）
# ─────────────────────────────────────────────────────────────────────────────

# GenAI 语义约定属性键（与被测模块保持一致，独立硬编码以校验对外契约）。
GEN_AI_OPERATION_NAME = "gen_ai.operation.name"
GEN_AI_TOOL_NAME = "gen_ai.tool.name"
GEN_AI_USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens"
GEN_AI_USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens"


@pytest.fixture
def span_exporter() -> Any:
    """安装/复用一个真正的 SDK TracerProvider，并挂一枚 InMemorySpanExporter。

    otel 未安装时本 fixture 触发 importorskip → 依赖它的测试整体跳过。
    """
    pytest.importorskip("opentelemetry.sdk.trace")
    from opentelemetry import trace
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import SimpleSpanProcessor
    from opentelemetry.sdk.trace.export.in_memory_span_exporter import (
        InMemorySpanExporter,
    )

    # 让被测模块走真实初始化（幂等）；随后把 exporter 挂到当前活动 provider 上，
    # 从而无视全局 set-once 限制，稳定抓到本测试新建的 span。
    otel.init_tracing()
    provider = trace.get_tracer_provider()
    if not isinstance(provider, TracerProvider):
        provider = TracerProvider()
        trace.set_tracer_provider(provider)
    exporter = InMemorySpanExporter()
    provider.add_span_processor(SimpleSpanProcessor(exporter))
    return exporter


def test_traced_tool_emits_execute_tool_span(span_exporter: Any) -> None:
    from opentelemetry.trace import SpanKind

    with otel.traced_tool("rag_search") as span:
        assert span is not None  # 有依赖时拿到真实 span 对象

    spans = span_exporter.get_finished_spans()
    assert len(spans) == 1
    recorded = spans[0]
    # GenAI 约定：span 名 `execute_tool {tool_name}`、SpanKind.INTERNAL。
    assert recorded.name == "execute_tool rag_search"
    assert recorded.kind == SpanKind.INTERNAL
    assert recorded.attributes[GEN_AI_OPERATION_NAME] == "execute_tool"
    assert recorded.attributes[GEN_AI_TOOL_NAME] == "rag_search"


def test_record_token_usage_sets_genai_usage_attributes(span_exporter: Any) -> None:
    with otel.traced_tool("paper_designer"):
        otel.record_token_usage(120, 48)

    spans = span_exporter.get_finished_spans()
    assert len(spans) == 1
    attrs = spans[0].attributes
    assert attrs[GEN_AI_USAGE_INPUT_TOKENS] == 120
    assert attrs[GEN_AI_USAGE_OUTPUT_TOKENS] == 48


def test_sensitive_tool_args_and_results_not_recorded(span_exporter: Any) -> None:
    """默认隐私安全：只记录工具名/operation/token，绝不记录参数/结果等敏感内容。"""
    with otel.traced_tool("code_lab"):
        otel.record_token_usage(10, 5)

    spans = span_exporter.get_finished_spans()
    attrs = dict(spans[0].attributes)
    # GenAI 约定中属于 Opt-In 的敏感键不应出现。
    assert "gen_ai.tool.call.arguments" not in attrs
    assert "gen_ai.tool.call.result" not in attrs
    assert "gen_ai.prompt" not in attrs
    # 仅有约定内的非敏感键。
    assert set(attrs) == {
        GEN_AI_OPERATION_NAME,
        GEN_AI_TOOL_NAME,
        GEN_AI_USAGE_INPUT_TOKENS,
        GEN_AI_USAGE_OUTPUT_TOKENS,
    }


def test_traced_tool_records_exception_and_reraises(span_exporter: Any) -> None:
    from opentelemetry.trace.status import StatusCode

    with pytest.raises(RuntimeError, match="kaboom"):
        with otel.traced_tool("debug_agent"):
            raise RuntimeError("kaboom")

    spans = span_exporter.get_finished_spans()
    assert len(spans) == 1
    recorded = spans[0]
    # span 自动记录异常事件并置 ERROR 状态（fail fast，异常已向上抛）。
    assert recorded.status.status_code == StatusCode.ERROR
    assert any(event.name == "exception" for event in recorded.events)


def test_traced_tool_as_decorator_emits_span_per_call(span_exporter: Any) -> None:
    @otel.traced_tool("mind_map")
    def build(n: int) -> int:
        return n + 1

    assert build(1) == 2
    assert build(10) == 11

    spans = span_exporter.get_finished_spans()
    assert len(spans) == 2
    assert all(s.name == "execute_tool mind_map" for s in spans)


def test_init_tracing_is_idempotent(span_exporter: Any) -> None:
    # 重复调用不抛错、不重复安装 provider（已在 fixture 调用过一次）。
    otel.init_tracing()
    otel.init_tracing()
    assert otel.is_enabled() is True
