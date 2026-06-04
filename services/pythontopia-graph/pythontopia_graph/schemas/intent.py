"""Schemas for the IntentRouterAgent（百晓工坊 意图路由）.

The router picks ONE tool for a single composer message — either Q&A (``ASK``) or
one of the 6 resource generators — and grounds suggested KC codes to the course
catalog the frontend already loaded for its ``@`` mention menu.

It does NOT generate content: the frontend dispatches the chosen intent to the
existing tutor (``/api/tutor/ask``) or workshop (``/api/workshop/resources/stream``)
SSE pipelines. KC suggestions are advisory — the workshop generation endpoint
re-validates codes against the real course DB, so grounding integrity is enforced
at the (persisted) generation layer regardless of this advisory step.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

# ASK = 答疑问答（导师管线）；其余 6 个对应资源工坊的 6 类资源。
IntentKind = Literal[
    "ASK",
    "HANDOUT",
    "MINDMAP",
    "PRACTICE",
    "CODE_LAB",
    "READING",
    "STORYBOARD",
]


class CourseKc(BaseModel):
    """A single KC catalog entry (code + display name) for grounding suggestions."""

    code: str = Field(min_length=1, max_length=64)
    name: str = Field(default="", max_length=200)


class IntentRouteRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    # 学生手动 @ 指定的 KC（最高优先级，永远保留在结果里）。
    manual_kc_codes: list[str] = Field(default_factory=list)
    # 前端已加载的课程 KC 目录（code + name），用于把语句里的主题锚定到真实 KC。
    course_kcs: list[CourseKc] = Field(default_factory=list)
    current_chapter: int | None = Field(default=None, ge=1, le=7)
    recent_kc_codes: list[str] = Field(default_factory=list)


class IntentRouteResponse(BaseModel):
    intent: IntentKind
    kc_codes: list[str] = Field(default_factory=list)
    focus_hint: str = ""
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)
    reason: str = ""
