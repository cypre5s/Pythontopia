"""OrchestratorAgent（学习总管）对话编排端点的结构化输出 schema。

位于专家 Agent 之上，拆解看→学→练→测计划并调度 ResourceDesigner/Practice/PathPlanner/Tutor/Debug 等；
失败时动态重规划，对话中提取画像。GraphTraceStep/GroundingResult/SafetyResult 复用 resource schema。
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field

from pythontopia_graph.schemas.resource import (
    GraphTraceStep,
    GroundingResult,
    SafetyResult,
)

Phase = Literal["watch", "learn", "practice", "assess"]
StepStatus = Literal["pending", "active", "done", "skipped"]


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


class OrchestratorResponse(BaseModel):
    reply_md: str
    clarifying_question: str | None = None
    needs_user_input: bool = False
    plan: list[PlanStep] = Field(default_factory=list)
    dispatch_results: list[DispatchResult] = Field(default_factory=list)
    profile_updates: list[ProfileSignal] = Field(default_factory=list)
    trace: list[GraphTraceStep] = Field(default_factory=list)
    done: bool = False
    safety_result: SafetyResult = Field(default_factory=lambda: SafetyResult(passed=True))
    grounding_result: GroundingResult = Field(
        default_factory=lambda: GroundingResult(passed=True)
    )
