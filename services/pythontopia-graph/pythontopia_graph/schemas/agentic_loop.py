"""Agentic Loop 冻结契约（Phase 0 预置）。

S1 落地有界闭环编排与审计。后端 loop/** DTO 与前端 loopTypes.ts 按本 schema **镜像**
（跨服务为 HTTP/镜像契约，非直接 import）。运行时严格**有界、非自由 ReAct**：
step/replan 有上限 + 停止条件 + 白名单工具；达上界诚实降级，不编造。
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

LoopPhase = Literal["plan", "recall", "retrieve", "act", "critique", "compose"]
LoopStepStatus = Literal["pending", "active", "done", "failed"]
CitationKind = Literal["courseware", "external"]


class LoopCitation(BaseModel):
    kind: CitationKind
    ref: str  # 课件 = page_no/page_id；外部 = url
    label: str = ""


class LoopStep(BaseModel):
    id: str
    phase: LoopPhase
    label: str  # 中文可读：来自 registry zh_label / 阶段名
    tool_name: str | None = None
    status: LoopStepStatus = "pending"
    duration_ms: int = 0
    citations: list[LoopCitation] = Field(default_factory=list)
    reason_summary: str = ""  # 仅一句可审计摘要；禁原始 CoT
    error: str = ""


class LoopPlan(BaseModel):
    steps: list[LoopStep] = Field(default_factory=list)
    bounded: bool = True
    max_steps: int = Field(default=4, ge=1, le=8)


class AgenticLoopRunRecord(BaseModel):
    """落库审计（镜像 V171 loop_run 关键列）。"""

    user_id: int = Field(ge=1)
    session_id: str
    turn: int = Field(ge=0)
    max_steps: int = Field(default=4, ge=1, le=8)
    steps_used: int = 0
    replan_count: int = 0
    tools_used: list[str] = Field(default_factory=list)
    grounding_pass: bool = True
    safety_pass: bool = True
    input_tokens: int = 0
    output_tokens: int = 0
    trace_id: str = ""
