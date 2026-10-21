from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class Turn(BaseModel):
    role: Literal["user", "assistant"]
    content: str


class Misconception(BaseModel):
    tag: str
    evidence_event_ids: list[int] = Field(default_factory=list)
    resolved: bool = False


class FeynmanLearnerContext(BaseModel):
    level: Literal["beginner", "intermediate", "advanced"] = "beginner"
    response_style: Literal["examples", "step_by_step", "practice"] = "examples"


class FeynmanTuteeInput(BaseModel):
    kc_code: str
    topic: str | None = None
    learner_context: FeynmanLearnerContext | None = None
    misconceptions: list[Misconception] = Field(default_factory=list)
    student_teaching: str | None = None
    history: list[Turn] = Field(default_factory=list)


class FeynmanTuteeOutput(BaseModel):
    reply_md: str
    updated_misconceptions: list[Misconception] = Field(default_factory=list)
    produced_code: str | None = None


class FeynmanBugExposureInput(BaseModel):
    """代码找茬：让学弟写出一段含 bug 的代码供学生限时找茬。"""

    kc_code: str
    # 人类可读的知识点/概念主题（课程 KC 传中文名；课件外概念直接传概念词）。
    # 用于让 LLM 生成「贴该 KC / 该概念」的 bug，而非只按章节泛化；缺省回退到章节主题/kc_code。
    topic: str | None = None
    learner_context: FeynmanLearnerContext | None = None
    misconceptions: list[Misconception] = Field(default_factory=list)


class FeynmanBugExposureOutput(BaseModel):
    """代码找茬产出：buggy 代码 + 真实 bug 行号（与正确版 diff 得出，零伪造）。

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
    difficulty: Literal["easy", "medium", "hard"] = "medium"
    generated_by_ai: bool = False
