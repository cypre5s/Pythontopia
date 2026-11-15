"""HTTP inputs and resource limits for course package exchange."""

from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from app.course_packages.manifest import PackageLimits
from app.models import Activity, CourseExample, KnowledgePoint, Material, Question

LIMITS = PackageLimits()


EXTRAS = {Material: {"chapter_ids", "asset_id", "preview_asset_id"}, Question: {"chapter_ids", "knowledge_point_ids"}, Activity: {"question_ids", "resources"}}


JSONL_FILES = {
    Material: "content/materials.jsonl",
    Question: "content/questions/questions-001.jsonl",
    KnowledgePoint: "content/knowledge-points.jsonl",
    CourseExample: "content/examples.jsonl",
}


CONFIG_FILES = {
    "world": "world/interactions.yaml",
    "buildings": "world/buildings.yaml",
    "assistant": "assistant/settings.yaml",
    "editor": "editor/layouts.json",
}


class AvailablePackageInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    package_key: str = Field(pattern=r"^mounted-[0-9a-f]{64}$", max_length=80)


class ApplyInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    request_id: UUID
    expected_revision: int = Field(ge=0, strict=True)
