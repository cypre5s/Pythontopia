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
