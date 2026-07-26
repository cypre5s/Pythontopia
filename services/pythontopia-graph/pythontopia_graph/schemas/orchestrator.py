"""OrchestratorAgent（学习总管）对话编排端点的结构化输出 schema。

位于专家 Agent 之上，拆解看→学→练→测计划并调度 ResourceDesigner/Practice/PathPlanner/Tutor/Debug 等；
失败时动态重规划，对话中提取画像。GraphTraceStep/GroundingResult/SafetyResult 复用 resource schema。
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator

from pythontopia_graph.schemas.resource import (
    GraphTraceStep,
    GroundingResult,
    SafetyResult,
)
from pythontopia_graph.schemas.intent import LearnerRoutingContext, WorkshopInteractionContextRef
from pythontopia_graph.schemas.workshop_route import RouteDecision, Topology

Phase = Literal["watch", "learn", "practice", "assess"]
StepStatus = Literal["pending", "active", "done", "failed", "skipped"]


class OrchestratorTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str


class PlanStep(BaseModel):
    """看/学/练/测计划中的单步，路由到一个专家 Agent。"""

    id: str
    phase: Phase
    title: str
    detail: str
    agent: str
    kc_codes: list[str] = Field(default_factory=list)
    panel: str | None = None
    status: StepStatus = "pending"
    resource_type: str | None = None
    tool_name: str | None = Field(default=None, max_length=64)
    depends_on: list[str] = Field(default_factory=list, max_length=3)
    input_refs: list[str] = Field(default_factory=list, max_length=4)
    output_schema: str | None = Field(default=None, max_length=80)
    parallel_group: str | None = Field(default=None, max_length=64)
    idempotency_key: str | None = Field(default=None, max_length=160)
    on_failure: Literal["RETURN_PARTIAL", "BLOCK_DEPENDENTS", "REPLAN_ONCE"] = "BLOCK_DEPENDENTS"


class TaskPlan(BaseModel):
    """Supervisor 编译后的唯一执行计划；steps 复用既有 PlanStep，避免协议分叉。"""

    plan_id: str = Field(pattern=r"^plan_[0-9a-f]{16}$")
    route_id: str = Field(pattern=r"^route_[0-9a-f]{16}$")
    initial_candidate_id: str | None = Field(
        default=None,
        pattern=r"^candidate_[a-z0-9][a-z0-9_-]{0,63}$",
    )
    active_candidate_id: str | None = Field(
        default=None,
        pattern=r"^candidate_[a-z0-9][a-z0-9_-]{0,63}$",
    )
    replanned_candidate_id: str | None = Field(
        default=None,
        pattern=r"^candidate_[a-z0-9][a-z0-9_-]{0,63}$",
    )
    topology: Topology
    steps: list[PlanStep] = Field(min_length=1, max_length=4)
    max_tool_calls: int = Field(default=8, ge=1, le=8)
    max_calls_per_tool: int = Field(default=2, ge=1, le=2)
    max_parallel_agents: int = Field(default=3, ge=1, le=3)
    max_planning_depth: int = Field(default=3, ge=1, le=3)
    max_replans: int = Field(default=1, ge=0, le=1)
    replans_used: int = Field(default=0, ge=0, le=1)
    status: Literal["PENDING", "RUNNING", "PARTIAL", "COMPLETED", "FAILED"] = "PENDING"

    @model_validator(mode="after")
    def _unique_step_ids(self) -> "TaskPlan":
        ids = [step.id for step in self.steps]
        if len(ids) != len(set(ids)):
            raise ValueError("TaskPlan step ids must be unique")
        step_statuses = [step.status for step in self.steps]
        if self.status == "PENDING" and any(
            step_status != "pending" for step_status in step_statuses
        ):
            raise ValueError("PENDING TaskPlan requires every step to be pending")
        if self.status == "COMPLETED" and any(
            step_status != "done" for step_status in step_statuses
        ):
            raise ValueError("COMPLETED TaskPlan requires every step to be done")
        if self.status == "PARTIAL" and (
            "done" not in step_statuses
            or not any(step_status in {"failed", "skipped"} for step_status in step_statuses)
        ):
            raise ValueError("PARTIAL TaskPlan requires done and failed or skipped steps")
        if self.status == "FAILED" and (
            "done" in step_statuses
            or not any(step_status in {"failed", "skipped"} for step_status in step_statuses)
        ):
            raise ValueError("FAILED TaskPlan cannot contain done steps")
        candidate_ids = (
            self.initial_candidate_id,
            self.active_candidate_id,
            self.replanned_candidate_id,
        )
        if self.initial_candidate_id is None:
            if any(candidate_id is not None for candidate_id in candidate_ids):
                raise ValueError("candidate ids require initial_candidate_id")
            return self
        if self.active_candidate_id is None:
            raise ValueError("C3 TaskPlan requires active_candidate_id")
        if self.replans_used == 0:
            if (
                self.active_candidate_id != self.initial_candidate_id
                or self.replanned_candidate_id is not None
            ):
                raise ValueError("unreplanned C3 TaskPlan must keep the initial candidate active")
        elif (
            self.replanned_candidate_id != self.active_candidate_id
            or self.active_candidate_id == self.initial_candidate_id
        ):
            raise ValueError("replanned C3 TaskPlan must identify the switched candidate")
        return self


class DispatchResult(BaseModel):
    step_id: str
    agent: str
    ok: bool
    summary: str
    note: str | None = None
    elapsed_ms: int = 0
    artifact: dict[str, Any] | None = None


class ProfileSignal(BaseModel):
    """从对话提取的单条画像信号（对话即画像）；dimension 限定为 learner_profile.dimensions 的 8 个 snake_case key。"""

    dimension: str
    value: str
    confidence: float = Field(ge=0.0, le=1.0)


class OrchestratorRequest(BaseModel):
    user_id: int = Field(ge=1)
    session_id: str = Field(min_length=1, max_length=128)
    user_message: str = Field(min_length=1, max_length=4000)
    history: list[OrchestratorTurn] = Field(default_factory=list)
    profile_brief: str | None = Field(default=None, max_length=2000)
    current_chapter: int | None = Field(default=None, ge=1, le=7)
    recent_kc_codes: list[str] = Field(default_factory=list)
    recent_failures: list[str] = Field(default_factory=list)
    # FSRS 引擎判定「快遗忘 / 已到期」的 KC（记忆矿脉回声），由后端从 ReviewSignal 透传；
    # 学习总管据此把看/学/练/测计划优先锚定到这些待复习知识点。
    decaying_kcs: list[str] = Field(default_factory=list)
    course_kc_index: list[str] = Field(default_factory=list)
    route_decision: RouteDecision | None = None
    interaction: WorkshopInteractionContextRef | None = None
    learner_context: LearnerRoutingContext | None = None


class OrchestratorResponse(BaseModel):
    reply_md: str
    clarifying_question: str | None = None
    needs_user_input: bool = False
    plan: list[PlanStep] = Field(default_factory=list)
    route_decision: RouteDecision | None = None
    task_plan: TaskPlan | None = None
    dispatch_results: list[DispatchResult] = Field(default_factory=list)
    profile_updates: list[ProfileSignal] = Field(default_factory=list)
    trace: list[GraphTraceStep] = Field(default_factory=list)
    done: bool = False
    safety_result: SafetyResult = Field(default_factory=lambda: SafetyResult(passed=True))
    grounding_result: GroundingResult = Field(default_factory=lambda: GroundingResult(passed=True))
