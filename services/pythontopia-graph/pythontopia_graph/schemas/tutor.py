"""TutorAgent 答疑端点的 Pydantic schema。"""

from __future__ import annotations

import base64
import binascii
import re
from typing import Annotated, Literal

from pydantic import BaseModel, Field, field_validator


def _image_mime_type(data: bytes) -> str | None:
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if data.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if data.startswith((b"GIF87a", b"GIF89a")):
        return "image/gif"
    if data.startswith(b"RIFF") and data[8:12] == b"WEBP":
        return "image/webp"
    return None


class TutorTurn(BaseModel):
    role: str
    content: str


class TutorVisualAid(BaseModel):
    """答疑图解（按问题选型，常见图形式全覆盖）：

    - kind=mermaid：``mermaid`` 为 flowchart 源码（流程/结构/对比）；
    - kind=ascii：``ascii_art`` 为等宽字符画（内存布局/索引下标/逐格演示）；
    - kind=svg：``svg`` 为自包含、已服务端消毒的 ``<svg>`` 源码（几何/坐标/图形示意）；
    - kind=image：``image_prompt`` 供 /api/media/image 文生图（保留兼容，当前不主动产出）。
    """

    kind: Literal["mermaid", "image", "ascii", "svg"]
    caption: str = ""
    mermaid: str | None = None
    image_prompt: str | None = None
    ascii_art: str | None = None
    svg: str | None = None
    kc_codes: list[str] = Field(default_factory=list)


class TutorAskRequest(BaseModel):
    user_id: int = Field(ge=1)
    question: str = Field(min_length=1, max_length=2000)
    profile_brief: str = Field(default="", max_length=1000)
    conversation_history: list[TutorTurn] = Field(default_factory=list)
    kc_codes: list[str] = Field(default_factory=list, max_length=10)
    # 当前学习上下文（视频标题/PPT 页/题面摘要），增强证据检索；空串时与无上下文行为一致。
    context: str = Field(default="", max_length=2000)
    images: list[Annotated[str, Field(max_length=8_000_000)]] = Field(
        default_factory=list, max_length=3
    )

    @field_validator("images")
    @classmethod
    def normalize_images(cls, images: list[str]) -> list[str]:
        return normalize_image_data(images)


def normalize_image_data(images: list[str]) -> list[str]:
    """Validate and normalize inline JPEG, PNG, GIF, or WebP images."""
    if len(images) > 3:
        raise ValueError("at most three images are supported")
    normalized: list[str] = []
    for image in images:
        if not isinstance(image, str):
            raise ValueError("images must be strings")
        if len(image) > 8_000_000:
            raise ValueError("image data is too large")
        encoded = image
        declared_mime: str | None = None
        if image.startswith("data:"):
            header, separator, encoded = image.partition(",")
            match = re.fullmatch(
                r"data:(image/(?:png|jpeg|gif|webp));base64", header
            )
            if not separator or match is None:
                raise ValueError("images must be supported base64 image data")
            declared_mime = match.group(1)
        try:
            data = base64.b64decode(encoded, validate=True)
        except (binascii.Error, ValueError) as exc:
            raise ValueError("images must contain valid base64 data") from exc
        if len(data) > 5 * 1024 * 1024:
            raise ValueError("each image must be at most 5 MiB")
        detected_mime = _image_mime_type(data)
        if detected_mime is None or (
            declared_mime and declared_mime != detected_mime
        ):
            raise ValueError("images must be JPEG, PNG, GIF, or WebP")
        normalized.append(f"data:{detected_mime};base64,{encoded}")
    return normalized


class EvidenceCitation(BaseModel):
    """单条课件证据，供前端「课件证据」面板展示（仿 Alethicode 引用卡）。

    page_no：课本页码；page_id：回链 /library?pageId=；snippet：RAG 命中片段（折叠空白+截断）；score：相关度 0~1。
    """

    page_id: int
    page_no: int
    snippet: str = ""
    score: float = 0.0
    # KC 图谱感知扩展（R-16）：该证据页来自前置 KC（前端可标「前置知识」）。
    via_prerequisite: bool = False
    # 图谱命中的关系名；None 表示主题 KC 直接命中，非 None 可供前端显示「相关知识」等标签。
    via_relation: str | None = None


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
