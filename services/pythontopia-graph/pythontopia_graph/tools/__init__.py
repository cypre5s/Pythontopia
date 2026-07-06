"""百晓工坊工具治理层（R-16 深度优化）。

- ``registry``：17 工具单一事实源（ToolSpec：风险分级 / 超时 / 重试 / 降级 / 作用域）。
- ``harness``：执行治理包装（超时、退避重试、熔断、预算、结构化 ToolObservation、审计日志）。

意图路由的工具清单由 registry 自动生成；任何 LLM 子调用经 harness 包装后
永远返回结构化观测（ok / timeout / error / fallback / budget_skipped），绝无悬空。
"""

from pythontopia_graph.tools.harness import (
    BudgetGuard,
    CircuitBreaker,
    ToolObservation,
    breaker_for,
    harness_stats,
    run_with_governance,
)
from pythontopia_graph.tools.registry import (
    ALL_TOOL_NAMES,
    TOOLS,
    ToolSpec,
    intent_catalog_lines,
    to_tool_schema,
    tool_parameters,
    tool_schemas_for_scope,
    tools_for_scope,
)

__all__ = [
    "ALL_TOOL_NAMES",
    "TOOLS",
    "BudgetGuard",
    "CircuitBreaker",
    "ToolObservation",
    "ToolSpec",
    "breaker_for",
    "harness_stats",
    "intent_catalog_lines",
    "run_with_governance",
    "to_tool_schema",
    "tool_parameters",
    "tool_schemas_for_scope",
    "tools_for_scope",
]
