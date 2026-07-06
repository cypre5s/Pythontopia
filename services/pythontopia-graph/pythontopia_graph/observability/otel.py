"""OpenTelemetry GenAI 可观测性地基（2026 OTel GenAI 语义约定）。

本模块是 graph 微服务工具调用的「执行 tool span」埋点适配层，遵循 OpenTelemetry
GenAI Semantic Conventions 的 ``execute_tool`` 操作约定：

- span 名 ``execute_tool {gen_ai.tool.name}``；
- ``gen_ai.operation.name = execute_tool``、``gen_ai.tool.name = <工具名>``；
- token 用量记在当前 span 上：``gen_ai.usage.input_tokens`` / ``gen_ai.usage.output_tokens``；
- SpanKind 取 ``INTERNAL``（工具在 agent 进程内执行，非对外 client 调用）。

核心约束（与并发构建主契约 §5.6 对齐）：

- **缺 ``opentelemetry`` 包时整模块优雅降级为 no-op**：顶层 try/except import + ``_ENABLED``
  标志；导入与任何调用绝不抛错、绝不影响主链路（harness/agent 主流程是第一公民）。
- **敏感内容默认不记录**：只记录稳定的工具名与 token 计数；GenAI 约定中属于 Opt-In 的
  ``gen_ai.tool.call.arguments`` / ``gen_ai.tool.call.result``（prompt / 工具参数 / 结果，
  可能含用户数据）一律不采集。

参考：https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/
"""

from __future__ import annotations

import base64
import contextlib
import os
from collections.abc import Iterator
from typing import Any

# ── 顶层导入守卫：缺 opentelemetry（API 层）时全模块降级为 no-op ──
# 只依赖 opentelemetry-api 即可产出 span；opentelemetry-sdk 仅 init_tracing 惰性使用。
try:
    from opentelemetry import trace as _otel_trace

    _ENABLED = True
except Exception:  # noqa: BLE001 - 任意导入异常（缺包/损坏）都降级，绝不拖垮主链路
    _otel_trace = None  # type: ignore[assignment]
    _ENABLED = False


# ── GenAI 语义约定属性键（硬编码字符串：比 incubating 常量更稳，不随次版本漂移）──
_GEN_AI_OPERATION_NAME = "gen_ai.operation.name"
_GEN_AI_TOOL_NAME = "gen_ai.tool.name"
_GEN_AI_USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens"
_GEN_AI_USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens"
_OP_EXECUTE_TOOL = "execute_tool"

_TRACER_NAME = "pythontopia_graph.observability"
_DEFAULT_SERVICE_NAME = "pythontopia-graph"

# OTLP/HTTP traces 信号子路径；base endpoint（OTEL_EXPORTER_OTLP_ENDPOINT 或 Langfuse host）
# 需要补到此路径才是真正的 traces 投递地址（signal-specific endpoint 则原样使用）。
_OTLP_TRACES_SIGNAL_PATH = "/v1/traces"
# Langfuse 的 OTLP 基础路径（自托管/云端一致）：{LANGFUSE_HOST}/api/public/otel。
# 参考 Langfuse OpenTelemetry 文档：traces 实际投递到 {host}/api/public/otel/v1/traces。
_LANGFUSE_OTEL_BASE_PATH = "/api/public/otel"

# 幂等位：init_tracing 只真正执行一次。
_initialized = False


def is_enabled() -> bool:
    """opentelemetry（API 层）是否可用。供调用方/测试探测降级状态，不影响行为。"""
    return _ENABLED


def init_tracing() -> None:
    """幂等初始化 GenAI tracing。无 ``opentelemetry`` 依赖时 no-op。

    行为：

    - 未安装 opentelemetry → 直接返回（no-op）。
    - 已初始化过 → 直接返回（幂等）。
    - 宿主应用已装好真正的 SDK ``TracerProvider`` → 不覆盖，仅标记完成。
    - 否则安装一个带 ``service.name`` 资源的 SDK ``TracerProvider``；若配置了
      ``OTEL_EXPORTER_OTLP_ENDPOINT`` 且 OTLP exporter 包可用，则挂 batch OTLP 导出，
      否则只建 provider（span 在进程内记录，导出去向交由部署/宿主决定）。

    任何步骤失败都被吞掉并保持 no-op，**绝不**让初始化异常影响主链路。
    """
    global _initialized
    if not _ENABLED or _initialized:
        return
    # 先置位再尝试：保证幂等，且失败后不反复重试拖慢主链路。
    _initialized = True
    try:
        _install_sdk_provider_if_absent()
    except Exception:  # noqa: BLE001 - SDK 缺失/初始化失败都保持 no-op
        return


@contextlib.contextmanager
def traced_tool(name: str) -> Iterator[Any]:
    """产出一个 GenAI ``execute_tool`` span 的上下文管理器（亦可作装饰器）。

    用法（两者等价，均不会因观测层失败而抛错）::

        with traced_tool("rag_search"):
            ...

        @traced_tool("rag_search")
        def run(): ...

    span 名为 ``execute_tool {name}``，并打上 ``gen_ai.operation.name=execute_tool`` 与
    ``gen_ai.tool.name=name``。**不**记录工具参数/结果等敏感内容。

    被包裹代码体抛出的异常会被 OTel span 自动记录（exception 事件 + ERROR 状态）后
    **原样向上抛**（fail fast，不静默吞掉业务异常）；而观测层自身的任何失败都降级为
    no-op，不影响被包裹逻辑。
    """
    span_cm = _start_tool_span(name)
    if span_cm is None:
        # 降级路径：无 otel / 创建失败时，仍是个合法的 no-op 上下文管理器/装饰器。
        yield None
        return
    with span_cm as span:
        yield span


def record_token_usage(input_tokens: int, output_tokens: int) -> None:
    """在**当前 span** 上记录 GenAI token 用量。无 otel / 无活动 span 时 no-op。

    写入 ``gen_ai.usage.input_tokens`` 与 ``gen_ai.usage.output_tokens``。当前无活动
    span 时 OTel 返回 INVALID_SPAN，``set_attribute`` 为安全 no-op。任何异常都被吞掉。
    """
    if not _ENABLED:
        return
    try:
        span = _otel_trace.get_current_span()
        span.set_attribute(_GEN_AI_USAGE_INPUT_TOKENS, int(input_tokens))
        span.set_attribute(_GEN_AI_USAGE_OUTPUT_TOKENS, int(output_tokens))
    except Exception:  # noqa: BLE001 - 观测失败绝不影响主链路
        return


# ── 内部辅助（全部 fail-soft，绝不向调用方抛错）──


def _start_tool_span(name: str) -> Any | None:
    """构造（但不进入）一个 ``execute_tool`` span 上下文管理器；失败/禁用时返回 None。

    仅构造、不进入：进入/退出由 :func:`traced_tool` 的 ``with`` 负责，从而让被包裹代码
    体的异常能被 span 正常记录并原样上抛，互不串扰。
    """
    if not _ENABLED:
        return None
    try:
        tracer = _otel_trace.get_tracer(_TRACER_NAME)
        return tracer.start_as_current_span(
            f"{_OP_EXECUTE_TOOL} {name}",
            kind=_otel_trace.SpanKind.INTERNAL,
            attributes={
                _GEN_AI_OPERATION_NAME: _OP_EXECUTE_TOOL,
                _GEN_AI_TOOL_NAME: name,
            },
        )
    except Exception:  # noqa: BLE001 - tracer/span 创建失败则降级为 no-op
        return None


def _install_sdk_provider_if_absent() -> None:
    """若全局还没有真正的 SDK ``TracerProvider``，安装一个带 service.name 的 provider。"""
    from opentelemetry.sdk.resources import SERVICE_NAME, Resource
    from opentelemetry.sdk.trace import TracerProvider

    current = _otel_trace.get_tracer_provider()
    if isinstance(current, TracerProvider):
        # 宿主应用已配置真正的 provider，尊重之，不覆盖。
        return

    service_name = os.getenv("OTEL_SERVICE_NAME", _DEFAULT_SERVICE_NAME)
    provider = TracerProvider(resource=Resource.create({SERVICE_NAME: service_name}))
    _maybe_attach_otlp_exporter(provider)
    _otel_trace.set_tracer_provider(provider)


def _maybe_attach_otlp_exporter(provider: Any) -> None:
    """仅当配置了 OTLP traces endpoint 且 exporter 包可用时，挂一个 batch OTLP span exporter。

    在既有 OTLP 导出基础上扩展（并发构建主契约 §3.6 第 1 条）：

    - endpoint 解析支持 ``OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`` / ``OTEL_EXPORTER_OTLP_ENDPOINT``，
      并新增由 ``LANGFUSE_HOST`` 推导出指向 Langfuse ``/api/public/otel`` 的 traces 地址；
    - headers 解析支持 ``OTEL_EXPORTER_OTLP_HEADERS``（``k1=v1,k2=v2``），并支持由
      ``LANGFUSE_PUBLIC_KEY`` / ``LANGFUSE_SECRET_KEY`` 拼出 ``Authorization: Basic <b64(pk:sk)>``。

    OTLP exporter 是独立可选包（不在硬依赖内）；缺 endpoint / 缺包 / 任何异常都跳过导出且
    **绝不抛错**——span 仍在进程内记录，主链路零影响（fail-soft 观测）。
    """
    try:
        endpoint = _resolve_otlp_traces_endpoint()
        if not endpoint:
            return
        headers = _resolve_otlp_headers()
        exporter = _build_otlp_span_exporter(endpoint, headers)
        if exporter is None:
            return
        from opentelemetry.sdk.trace.export import BatchSpanProcessor

        provider.add_span_processor(BatchSpanProcessor(exporter))
    except Exception:  # noqa: BLE001 - 导出挂载的任何失败都降级 no-op，绝不拖垮主链路
        return


def _resolve_otlp_traces_endpoint() -> str | None:
    """解析 OTLP **traces 信号** 投递地址；都没配则返回 None（→ 不挂导出）。

    优先级（与 OTel 约定一致，外加 Langfuse 便捷推导）：

    1. ``OTEL_EXPORTER_OTLP_TRACES_ENDPOINT``：signal-specific，原样使用（已含 ``/v1/traces``）。
    2. ``OTEL_EXPORTER_OTLP_ENDPOINT``：base endpoint，补 ``/v1/traces``。
    3. ``LANGFUSE_HOST``：推导 ``{host}/api/public/otel/v1/traces``。
    """
    traces_endpoint = os.getenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT")
    if traces_endpoint and traces_endpoint.strip():
        return traces_endpoint.strip()

    base_endpoint = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
    if base_endpoint and base_endpoint.strip():
        return base_endpoint.strip().rstrip("/") + _OTLP_TRACES_SIGNAL_PATH

    langfuse_host = os.getenv("LANGFUSE_HOST")
    if langfuse_host and langfuse_host.strip():
        return (
            langfuse_host.strip().rstrip("/")
            + _LANGFUSE_OTEL_BASE_PATH
            + _OTLP_TRACES_SIGNAL_PATH
        )

    return None


def _resolve_otlp_headers() -> dict[str, str] | None:
    """解析 OTLP exporter 的请求头；都没配则返回 None（exporter 用默认/无鉴权头）。

    优先级：

    1. ``OTEL_EXPORTER_OTLP_HEADERS``（``k1=v1,k2=v2``）；解析到至少一对才采用。
    2. 否则由 ``LANGFUSE_PUBLIC_KEY`` + ``LANGFUSE_SECRET_KEY`` 拼 HTTP Basic auth
       （``Authorization: Basic <base64(pk:sk)>``，Langfuse OTLP 鉴权方式）。
    """
    raw_headers = os.getenv("OTEL_EXPORTER_OTLP_HEADERS")
    if raw_headers and raw_headers.strip():
        parsed = _parse_otlp_headers(raw_headers)
        if parsed:
            return parsed

    public_key = os.getenv("LANGFUSE_PUBLIC_KEY")
    secret_key = os.getenv("LANGFUSE_SECRET_KEY")
    if public_key and secret_key:
        token = base64.b64encode(f"{public_key}:{secret_key}".encode()).decode("ascii")
        return {"Authorization": f"Basic {token}"}

    return None


def _parse_otlp_headers(raw: str) -> dict[str, str]:
    """把 ``k1=v1,k2=v2`` 形式的 header 串解析成 dict。

    仅在**首个** ``=`` 处切分键值，从而保留 base64 值里的 ``=`` padding（如 Basic auth）。
    空白、缺 ``=`` 或键为空的片段一律跳过。
    """
    headers: dict[str, str] = {}
    for pair in raw.split(","):
        if "=" not in pair:
            continue
        key, _, value = pair.partition("=")
        key = key.strip()
        if key:
            headers[key] = value.strip()
    return headers


def _build_otlp_span_exporter(
    endpoint: str, headers: dict[str, str] | None
) -> Any | None:
    """惰性导入并构造 OTLP/HTTP span exporter；缺包或构造失败一律返回 None（fail-soft）。

    OTLP exporter（``opentelemetry-exporter-otlp-proto-http``）是独立可选包、不在硬依赖内，
    故惰性导入并吞掉 ImportError；构造异常同样吞掉，绝不拖垮主链路。
    """
    try:
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import (
            OTLPSpanExporter,
        )
    except Exception:  # noqa: BLE001 - 未装 OTLP exporter 包就跳过导出
        return None
    try:
        return OTLPSpanExporter(endpoint=endpoint, headers=headers)
    except Exception:  # noqa: BLE001 - exporter 构造失败也降级 no-op
        return None
