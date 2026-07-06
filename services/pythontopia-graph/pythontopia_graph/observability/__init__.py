"""可观测性地基：OpenTelemetry GenAI 语义约定埋点（缺包时全链路 no-op）。

公开接口（并发构建主契约 §5.6）：

- :func:`init_tracing`：幂等初始化；无 opentelemetry 依赖时 no-op。
- :func:`traced_tool`：上下文管理器 / 装饰器，产出 ``execute_tool`` span。
- :func:`record_token_usage`：在当前 span 上记录 GenAI token 用量。

A-harness 等调用方可选地 ``from pythontopia_graph.observability import traced_tool``，
无需关心 opentelemetry 是否安装——未安装时全部静默降级。
"""

from pythontopia_graph.observability.otel import (
    init_tracing,
    is_enabled,
    record_token_usage,
    traced_tool,
)

__all__ = [
    "init_tracing",
    "is_enabled",
    "record_token_usage",
    "traced_tool",
]
