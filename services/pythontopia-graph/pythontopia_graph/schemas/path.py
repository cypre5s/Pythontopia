"""Schemas for the PathPlannerAgent (stage 08)."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, model_validator

PathNodeType = Literal[
    "READ_RESOURCE",
    "SOLVE_PROBLEM",
    "FIX_BUG",
    "PLANT_KC",
    "PROFILE_UPDATE",
]

ResourceTypeHint = Literal[
    "HANDOUT",
    "MINDMAP",
    "PRACTICE",
    "CODE_LAB",
    "READING",
    "STORYBOARD",
]


class PlannerKcSnapshot(BaseModel):
    """Minimal KC snapshot fed into the planner."""

    kc_code: str = Field(min_length=1, max_length=64)
    mastery_score: float = Field(ge=0.0, le=1.0, default=0.0)
    confidence: float = Field(ge=0.0, le=1.0, default=0.0)
    unlocked: bool = True
    chapter_id: int | None = None
    prerequisite_codes: list[str] = Field(default_factory=list)


class PlannerResourceUsage(BaseModel):
    kc_code: str
    type: ResourceTypeHint
    created_at: str


class PlannerProblemSubmission(BaseModel):
    problem_id: int
    kc_code: str
    passed: bool


class PlannerProfileSnapshot(BaseModel):
    has_profile: bool = False
    major: Literal["CS", "NON_CS_STEM", "NON_CS_HUMANITIES", "OTHER"] = "OTHER"
    python_self_rated_level: int = Field(default=2, ge=1, le=5)
    days_since_last_profile_update: int = Field(default=999, ge=0)


class PlannerClassroomQuest(BaseModel):
    classroom_quest_id: int
    classroom_id: int
    kc_codes: list[str] = Field(default_factory=list)
    node_type: PathNodeType
    title: str
    due_date: str


class PathPlannerRequest(BaseModel):
    user_id: int = Field(ge=1)
    pack_id: int = Field(default=43, ge=1)
    profile: PlannerProfileSnapshot
    kcs: list[PlannerKcSnapshot] = Field(default_factory=list)
    recent_resources: list[PlannerResourceUsage] = Field(default_factory=list)
    recent_submissions: list[PlannerProblemSubmission] = Field(default_factory=list)
    classroom_quests: list[PlannerClassroomQuest] = Field(default_factory=list)
    target_days: int = Field(default=7, ge=1, le=7)


class PlannerNodeDraft(BaseModel):
    day_index: int = Field(ge=1, le=7)
    order_in_day: int = Field(ge=1, le=8)
    kc_code: str = Field(min_length=1, max_length=64)
    node_type: PathNodeType
    reason_summary: str = Field(max_length=512)
    evidence_summary: str = Field(default="", max_length=512)
    suggested_resource_type: ResourceTypeHint | None = None
    suggested_problem_id: int | None = None
    source_classroom_quest_id: int | None = None


class PathPlannerSafetyResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)


class PathPlannerGroundingResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)
    unknown_kc: list[str] = Field(default_factory=list)


class PathPlannerResponse(BaseModel):
    user_id: int
    pack_id: int
    goal_summary: str = Field(max_length=512)
    nodes: list[PlannerNodeDraft] = Field(default_factory=list)
    safety_result: PathPlannerSafetyResult
    grounding_result: PathPlannerGroundingResult
    deterministic_signature: str = Field(max_length=128)
    status: Literal["SUCCESS", "PARTIAL", "FAILED"] = "SUCCESS"

    @model_validator(mode="after")
    def _node_indices_unique(self) -> "PathPlannerResponse":
        seen: set[tuple[int, int]] = set()
        for node in self.nodes:
            key = (node.day_index, node.order_in_day)
            if key in seen:
                raise ValueError(
                    f"duplicate (day_index, order_in_day) pair: {key}"
                )
            seen.add(key)
        return self
