from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class MapperKc(BaseModel):
    kc_code: str
    kc_name: str
    chapter_id: int
    chapter_order: int
    difficulty: int


class MapperChapter(BaseModel):
    chapter_id: int
    order_index: int
    title: str


class MapperProfileSummary(BaseModel):
    major: Literal["CS", "NON_CS_STEM", "NON_CS_HUMANITIES", "OTHER"]
    python_self_rated_level: int = Field(ge=1, le=5)
    target_kc_codes: list[str] = Field(default_factory=list)
    media_priority: list[str] = Field(default_factory=list)


class MapperInput(BaseModel):
    user_id: int
    profile_summary: MapperProfileSummary
    chapters: list[MapperChapter]
    kcs: list[MapperKc]
    row_width: int = Field(default=5, ge=3, le=10)
    field_origin_x: int = Field(default=2, ge=0)
    field_origin_y: int = Field(default=2, ge=0)


class TileAssignment(BaseModel):
    kc_code: str
    tile_x: int
    tile_y: int
    chapter_id: int


class PlanStep(BaseModel):
    order: int
    kc_code: str
    chapter_order: int
    rationale: str


class MapperOutput(BaseModel):
    plan_steps: list[PlanStep]
    tile_assignments: list[TileAssignment]
    unlocked_kcs: list[str]
    deterministic_signature: str
