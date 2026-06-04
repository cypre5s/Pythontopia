"""Schemas for the OrchestratorAgent（学习总管）conversational orchestration endpoint.

The 学习总管 sits above the specialist agents (ResourceDesigner / Practice /
PathPlanner / Tutor / Debug …). It understands the learner's goal, decomposes it
into an ordered 看→学→练→测 (watch→learn→practice→assess) plan, dispatches each
step to a named specialist, dynamically replans on recent failures, and keeps a
running profile from the dialogue.

`GraphTraceStep`, `GroundingResult` and `SafetyResult` are reused from
``schemas.resource`` so the trace / guardrail shapes stay identical across graphs.
"""

from __future__ import annotations

from typing import Literal

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
    """One ordered step of the 看/学/练/测 plan, routed to a single specialist."""

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


class ProfileSignal(BaseModel):
    """A single profile feature extracted from the dialogue（对话即画像）.

    ``dimension`` is restricted to the 8 backend ``learner_profile.dimensions``
    JSONB keys.
    """

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
