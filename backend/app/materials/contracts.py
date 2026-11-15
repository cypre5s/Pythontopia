"""Material upload resource limits and shared input values."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, field_validator


class MaterialUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str | None = None
    chapter_ids: list[int] | None = None

    @field_validator("title")
    @classmethod
    def valid_title(cls, value):
        if value is None or not value.strip() or len(value) > 200:
            raise ValueError("资料标题必须为1至200个字符")
        return value.strip()

    @field_validator("chapter_ids", mode="before")
    @classmethod
    def valid_chapters(cls, value):
        from app.curriculum import validate_id_list

        return validate_id_list(value)
