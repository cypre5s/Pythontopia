from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class Turn(BaseModel):
    role: Literal["system", "user", "assistant"]
    content: str


class Misconception(BaseModel):
    tag: str
    evidence_event_ids: list[int] = Field(default_factory=list)
    resolved: bool = False


class FeynmanTuteeInput(BaseModel):
    kc_code: str
    misconceptions: list[Misconception] = Field(default_factory=list)
    student_teaching: str | None = None
    history: list[Turn] = Field(default_factory=list)


class FeynmanTuteeOutput(BaseModel):
    reply_md: str
    updated_misconceptions: list[Misconception] = Field(default_factory=list)
    produced_code: str | None = None


class FeynmanBugExposureInput(BaseModel):
    """错误猎人：让学弟写出一段含 bug 的代码供学生限时找茬。"""

    kc_code: str
    misconceptions: list[Misconception] = Field(default_factory=list)


class FeynmanBugExposureOutput(BaseModel):
    """错误猎人产出：buggy 代码 + 真实 bug 行号（与正确版 diff 得出，零伪造）。

    * ``source=real_misconception``：复现学生真实误解（misconception_tag 带 evidence）。
    * ``source=generic_beginner_bug``：无真实误解史时退化为常见初学者陷阱（诚实标注，不冒充"你的错"）。
    * ``source=none``：无可用样本（buggy_code 为 None，前端提示先做题）。
    """

    source: Literal["real_misconception", "generic_beginner_bug", "none"] = "none"
    misconception_tag: str | None = None
    buggy_code: str | None = None
    fixed_code: str | None = None
    # buggy_code 中确属 bug 的 0-based 行号（与 fixed_code 逐行 diff 得出）。
    bug_line_indices: list[int] = Field(default_factory=list)
    hint_md: str = ""
