"""IntentRouterAgent（百晓工坊意图路由）的结构化输出 schema。

单条消息选定一种 intent：ASK（答疑）、VIDEO（B站检索，非生成）或 6 类资源生成器之一；
KC 建议锚定前端已加载的 course_kcs（@ 菜单同源）。不生成内容，前端再分发到
/api/tutor/ask 或 /api/workshop/resources/stream；KC 为建议值，持久化生成层会二次校验。
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

# ASK = 答疑问答（导师管线）；VIDEO = B站精选讲解视频（外部资源检索，非生成）；
# REVIEW = 记忆矿脉回声复习（引导到错题矿洞按 FSRS 到期 KC 复盘，非生成）；
# 其余 6 个对应资源工坊的 6 类资源。
IntentKind = Literal[
    "ASK",
    "VIDEO",
    "REVIEW",
    "HANDOUT",
    "MINDMAP",
    "PRACTICE",
    "CODE_LAB",
    "READING",
    "STORYBOARD",
]


class CourseKc(BaseModel):
    """课程 KC 目录项（code + 展示名），供意图锚定建议。"""

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
