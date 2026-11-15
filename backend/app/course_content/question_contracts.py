"""Reusable question input contracts and pure payload validation."""

import json
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class QuestionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: str = Field(min_length=1, max_length=100)
    title: str = Field(min_length=1, max_length=200)
    kind: Literal["single_choice", "boolean", "fill", "python"]
    prompt: str = Field(min_length=1, max_length=30000)
    chapter_ids: list[int] = Field(default_factory=list, max_length=65)
    knowledge_point_ids: list[int] = Field(default_factory=list, max_length=65)
    source_type: Literal["ai_generated", "imported"] = "imported"
    status: Literal["draft", "ready", "disabled"] = "draft"
    options: list = Field(default_factory=list, max_length=20)
    samples: list = Field(default_factory=list, max_length=20)
    hints: list[str] = Field(default_factory=list, max_length=10)
    starter_code: str = Field(default="", max_length=20000)
    answer: dict | list | str | bool | None = None
    hidden_cases: list = Field(default_factory=list, max_length=20)
    source: dict = Field(default_factory=dict)

    @field_validator("chapter_ids", "knowledge_point_ids", mode="before")
    @classmethod
    def valid_ids(cls, value):
        from app.curriculum import validate_id_list

        return validate_id_list(value)

    @field_validator("id", "title", "prompt")
    @classmethod
    def nonblank(cls, value):
        if not value.strip():
            raise ValueError("题目标识、标题与题干不能为空白")
        return value.strip()

    @field_validator("source", "options")
    @classmethod
    def bounded_json(cls, value):
        if len(json.dumps(value, ensure_ascii=False)) > 30000:
            raise ValueError("题目选项或来源超过30000字符")
        return value

    @field_validator("hints")
    @classmethod
    def validate_hints(cls, hints):
        if any(not hint.strip() or len(hint) > 2000 for hint in hints):
            raise ValueError("每条提示须为1至2000字符")
        return hints

    @field_validator("samples", "hidden_cases")
    @classmethod
    def validate_cases(cls, cases):
        for case in cases:
            if not isinstance(case, dict) or set(case) not in ({"input", "output"}, {"input", "expected_output"}):
                raise ValueError("用例必须包含 input 和 output 或 expected_output，不能附加其他字段")
            if any(not isinstance(value, str) or len(value) > 10000 for value in case.values()):
                raise ValueError("用例输入和输出必须为不超过10000字符的字符串")
        return cases

    @model_validator(mode="after")
    def validate_judge_budget(self):
        if self.kind == "python" and len(self.samples) + len(self.hidden_cases) > 20:
            raise ValueError("编程题公开样例与隐藏用例合计不能超过20个")
        if self.status == "ready":
            if not self.chapter_ids:
                raise ValueError("就绪题目必须关联章节")
            if self.kind == "python" and not self.hidden_cases:
                raise ValueError("就绪编程题必须设置私有用例")
            expected = self.answer.get("value") if isinstance(self.answer, dict) and "value" in self.answer else self.answer
            if self.kind == "boolean" and not isinstance(expected, bool):
                raise ValueError("判断题答案必须是布尔值")
            if self.kind == "single_choice" and (not self.options or expected not in self.options):
                raise ValueError("单选答案必须属于选项")
            if self.kind == "fill" and (not isinstance(expected, (str, list)) or not expected):
                raise ValueError("填空题必须设置答案")
        return self


class QuestionUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str | None = None
    kind: Literal["single_choice", "boolean", "fill", "python"] | None = None
    prompt: str | None = None
    chapter_ids: list[int] | None = None
    knowledge_point_ids: list[int] | None = None
    source_type: Literal["ai_generated", "imported"] | None = None
    status: Literal["draft", "ready", "disabled"] | None = None
    options: list | None = None
    samples: list | None = None
    hints: list[str] | None = None
    starter_code: str | None = None
    answer: dict | list | str | bool | None = None
    hidden_cases: list | None = None
    source: dict | None = None
