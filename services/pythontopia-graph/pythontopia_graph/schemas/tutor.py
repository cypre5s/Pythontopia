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


class EvidenceCitation(BaseModel):
    """单条「系统命中的课件证据」，供前端「课件证据」面板展示给学生（仿 Alethicode 引用卡）。

    - ``page_no``：课本页码（学生可读，用于「第 X 页」标签）；
    - ``page_id``：课件页主键（用于回链 ``/library?pageId=``）；
    - ``snippet``：RAG 命中的原文片段（已折叠空白 + 截断），让学生看见答案到底依据了哪段课件；
    - ``score``：检索相关度（0~1），仅作排序/参考。
    """

    page_id: int
    page_no: int
    snippet: str = ""
    score: float = 0.0


class GroundingResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)


class SafetyResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)


class TutorResponse(BaseModel):
    answer_md: str
    evidence_page_ids: list[int] = Field(default_factory=list)
    # 命中的课件证据明细（页码 + 原文片段），前端据此渲染「课件证据」面板。
    # 与 evidence_page_ids 同源、同序（page_ids 由 evidence 去重后投影），供旧消费方继续用。
    evidence: list[EvidenceCitation] = Field(default_factory=list)
    grounding_result: GroundingResult
    safety_result: SafetyResult
    based_on_general_knowledge: bool = False
    # 多模态答疑：随文字答案附带的图解（mermaid 结构图 + 文生图插画）。
    visual_aids: list[TutorVisualAid] = Field(default_factory=list)
