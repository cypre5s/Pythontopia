"""TutorAgent 答疑端点的 Pydantic schema。"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class TutorTurn(BaseModel):
    role: str
    content: str


class TutorVisualAid(BaseModel):
    """答疑图解：kind=mermaid 时 ``mermaid`` 可渲染结构图；kind=image 时 ``image_prompt`` 供 /api/media/image 文生图。"""

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
    # 当前学习上下文（视频标题/PPT 页/题面摘要），增强证据检索；空串时与无上下文行为一致。
    context: str = Field(default="", max_length=2000)


class EvidenceCitation(BaseModel):
    """单条课件证据，供前端「课件证据」面板展示（仿 Alethicode 引用卡）。

    page_no：课本页码；page_id：回链 /library?pageId=；snippet：RAG 命中片段（折叠空白+截断）；score：相关度 0~1。
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
    # 课件证据明细，与 evidence_page_ids 同源同序；evidence 供面板，page_ids 供仅读 id 的消费方。
    evidence: list[EvidenceCitation] = Field(default_factory=list)
    grounding_result: GroundingResult
    safety_result: SafetyResult
    based_on_general_knowledge: bool = False
    # 随答案附带的 mermaid 结构图或文生图插画。
    visual_aids: list[TutorVisualAid] = Field(default_factory=list)
