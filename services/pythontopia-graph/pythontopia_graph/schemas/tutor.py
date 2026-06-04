"""Schemas for the TutorAgent conversational Q&A endpoint."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class TutorTurn(BaseModel):
    role: str
    content: str


class TutorVisualAid(BaseModel):
    """答疑配套的多模态图解。

    - kind="mermaid": ``mermaid`` 字段是可渲染的结构图（前端用 mermaid 渲染 SVG）。
    - kind="image": ``image_prompt`` 字段是文生图提示词，前端调 /api/media/image 渲染插画。
    """

    kind: Literal["mermaid", "image"]
    caption: str = ""
    mermaid: str | None = None
    image_prompt: str | None = None
    kc_codes: list[str] = Field(default_factory=list)


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
    # 多模态答疑：随文字答案附带的图解（mermaid 结构图 + 文生图插画）。
    visual_aids: list[TutorVisualAid] = Field(default_factory=list)
