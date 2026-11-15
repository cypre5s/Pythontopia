"""Narration editing and explicit synthesis request contracts."""

from typing import Literal

from pydantic import BaseModel, ConfigDict


class NarrationInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    text: str
    target_seconds: int | None = None


class GenerateInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    first_page: int
    last_page: int
    target_seconds: int | None = None
    # serial：把已生成的页作为上下文，过渡句才有依据；independent：每页独立（旧行为）。
    mode: Literal["independent", "serial"] = "independent"


class RangeInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    first_page: int | None = None
    last_page: int | None = None


class PageGenerateInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    target_seconds: int | None = None
