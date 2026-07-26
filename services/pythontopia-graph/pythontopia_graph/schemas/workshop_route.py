"""百晓工坊统一路由决策合同。

该合同只描述确定性编译后的执行计划，不承载模型思维过程。工具、Agent、预算与
fallback 均可审计；Phase 1 只允许 DIRECT / SINGLE_AGENT_TOOL。
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import AliasGenerator, BaseModel, ConfigDict, Field, StringConstraints
from pydantic.alias_generators import to_camel

TaskFamily = Literal[
    "CONCEPT_QA",
    "COURSEWARE_QA",
    "DEBUG",
    "GUIDE",
    "RESOURCE",
    "PRACTICE",
    "MINDMAP",
    "PATH",
    "MULTI_TASK",
    "CHAT",
    "UNKNOWN",
]
Complexity = Literal["C0", "C1", "C2", "C3"]
Topology = Literal[
    "DIRECT",
    "SINGLE_AGENT_TOOL",
    "SEQUENTIAL",
    "PARALLEL",
    "PLANNER_EXECUTOR",
]
RoutePathIdentifier = Annotated[str, StringConstraints(min_length=1, max_length=64)]


class RouteContractModel(BaseModel):
    """Accept both graph-native snake_case and backend/frontend camelCase payloads."""

    model_config = ConfigDict(
        alias_generator=AliasGenerator(validation_alias=to_camel),
        populate_by_name=True,
    )


class RouteTask(RouteContractModel):
    family: TaskFamily
    subtype: str = Field(default="", max_length=64)
    sub_intents: list[str] = Field(default_factory=list, max_length=4)
    complexity: Complexity
    confidence: float = Field(ge=0.0, le=1.0)
    needs_clarification: bool = False
    clarification_question: str = Field(default="", max_length=300)


class RouteOrchestration(RouteContractModel):
    topology: Topology
    primary_agent: str | None = Field(default=None, max_length=64)
    supporting_agents: list[str] = Field(default_factory=list, max_length=3)


class RouteTools(RouteContractModel):
    required: list[str] = Field(default_factory=list, max_length=8)
    optional: list[str] = Field(default_factory=list, max_length=8)
    forbidden: list[str] = Field(default_factory=list)


class RoutePolicies(RouteContractModel):
    max_tool_calls: int = Field(default=8, ge=0, le=8)
    max_calls_per_tool: int = Field(default=2, ge=0, le=2)
    max_agent_calls: int = Field(default=4, ge=0, le=4)
    max_planning_depth: int = Field(default=3, ge=0, le=3)
    max_parallel_agents: int = Field(default=3, ge=0, le=3)
    max_replans: int = Field(default=1, ge=0, le=1)
    require_grounding: bool = True
    require_safety: bool = True
    allow_common_knowledge: bool = False
    read_before_write: bool = True


class RouteOutput(RouteContractModel):
    schema_name: str = Field(min_length=1, max_length=80)
    render_mode: str = Field(min_length=1, max_length=40)
    require_evidence: bool = True


class RouteFallback(RouteContractModel):
    on_low_confidence: str = "ASK_CLARIFICATION"
    on_missing_context: str = "ASK_CLARIFICATION"
    on_no_evidence: str = "DISCLOSE_COMMON_KNOWLEDGE"
    on_tool_failure: str = "RETURN_PARTIAL_WITH_ERROR"
    on_model_failure: str = "USE_DETERMINISTIC_FALLBACK"
    on_guard_failure: str = "SAFE_TEMPLATE"


class RoutePersonalization(RouteContractModel):
    """Explainable, deterministic strategy derived from authoritative learner facts."""

    strategy: Literal[
        "STANDARD", "SCAFFOLD_FIRST", "REMEDIATION_FIRST", "VISUAL_FIRST"
    ] = "STANDARD"
    signals_used: list[str] = Field(default_factory=list, max_length=12)
    explanations: list[str] = Field(default_factory=list, max_length=4)
    writeback_mode: Literal["DOMAIN_EVENTS_ONLY"] = "DOMAIN_EVENTS_ONLY"


class RouteSearchCandidate(RouteContractModel):
    """C3 的公开候选路径与数值成本，不包含模型隐藏思维过程。"""

    candidate_id: str = Field(
        pattern=r"^candidate_[a-z0-9][a-z0-9_-]{0,63}$",
    )
    label: str = Field(min_length=1, max_length=80)
    agent_path: list[RoutePathIdentifier] = Field(min_length=1, max_length=3)
    tool_path: list[RoutePathIdentifier] = Field(min_length=1, max_length=3)
    depth: int = Field(ge=1, le=3)
    g_cost: float = Field(ge=0.0, allow_inf_nan=False)
    h_cost: float = Field(ge=0.0, allow_inf_nan=False)
    penalty: float = Field(ge=0.0, allow_inf_nan=False)
    total_cost: float = Field(ge=0.0, allow_inf_nan=False)


class RouteSearchReplanPolicy(RouteContractModel):
    """有界恢复只允许切换一次候选路径。"""

    max_candidate_switches: Literal[1] = 1


class RouteSearch(RouteContractModel):
    """仅供 C3 使用的有界 ToT 候选与 A* 公开评分合同。"""

    strategy: Literal["BOUNDED_TOT_ASTAR"] = "BOUNDED_TOT_ASTAR"
    candidates: list[RouteSearchCandidate] = Field(min_length=3, max_length=3)
    selected_candidate_id: str = Field(
        pattern=r"^candidate_[a-z0-9][a-z0-9_-]{0,63}$",
    )
    max_depth: Literal[3] = 3
    score_formula: Literal[
        "total_cost=g_cost+h_cost+penalty"
    ] = "total_cost=g_cost+h_cost+penalty"
    replan_policy: RouteSearchReplanPolicy = Field(
        default_factory=RouteSearchReplanPolicy
    )


class RouteDecision(RouteContractModel):
    route_id: str = Field(pattern=r"^route_[0-9a-f]{16}$")
    version: int = Field(default=1, ge=1)
    task: RouteTask
    orchestration: RouteOrchestration
    tools: RouteTools
    policies: RoutePolicies = Field(default_factory=RoutePolicies)
    output: RouteOutput
    fallback: RouteFallback = Field(default_factory=RouteFallback)
    personalization: RoutePersonalization = Field(default_factory=RoutePersonalization)
    search: RouteSearch | None = None
