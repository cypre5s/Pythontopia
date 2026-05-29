"""Schemas for the TutorAgent conversational Q&A endpoint."""

from __future__ import annotations

from pydantic import BaseModel, Field


class TutorTurn(BaseModel):
    role: str
    content: str


class TutorAskRequest(BaseModel):
    user_id: int = Field(ge=1)
    question: str = Field(min_length=1, max_length=2000)
    profile_brief: str = Field(default="", max_length=1000)
    conversation_history: list[TutorTurn] = Field(default_factory=list)
    kc_codes: list[str] = Field(default_factory=list, max_length=10)
    # R-12 主线 Phase 3/4：当前学习上下文（视频标题 / PPT 页 / 题面摘要），
    # 用于增强证据检索与答疑。空字符串时行为与旧版一致。
    context: str = Field(default="", max_length=2000)


class GroundingResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)


class SafetyResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)


class TutorResponse(BaseModel):
    answer_md: str
    evidence_page_ids: list[int] = Field(default_factory=list)
    grounding_result: GroundingResult
    safety_result: SafetyResult
    based_on_general_knowledge: bool = False
