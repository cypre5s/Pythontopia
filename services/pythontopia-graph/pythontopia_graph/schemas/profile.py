from __future__ import annotations

from datetime import date
from typing import Literal

from pydantic import BaseModel, Field


Major = Literal["CS", "NON_CS_STEM", "NON_CS_HUMANITIES", "OTHER"]
GoalKind = Literal["EXAM", "PROJECT", "INTEREST", "JOB"]
MediaKind = Literal["video", "text", "mindmap", "code"]
class BaseLevel(BaseModel):
    programming_years: int = Field(ge=0, le=20)
    languages_known: list[str] = Field(default_factory=list)
    python_self_rated_level: int = Field(ge=1, le=5)


class LearningGoal(BaseModel):
    kind: GoalKind
    deadline_iso: date | None = None
    target_kc_codes: list[str] = Field(default_factory=list)


class LearningPreference(BaseModel):
    media_priority: list[MediaKind] = Field(default_factory=list)
    session_minutes_per_day: int = Field(ge=5, le=240)


class ProfileDraft(BaseModel):
    major: Major | None = None
    base_level: BaseLevel | None = None
    learning_goal: LearningGoal | None = None
    learning_preference: LearningPreference | None = None

    @property
    def filled_dimensions(self) -> list[str]:
        dimensions = [
            "major",
            "base_level",
            "learning_goal",
            "learning_preference",
        ]
        return [dimension for dimension in dimensions if getattr(self, dimension) is not None]

    @classmethod
    def example_complete(cls, target_kc_codes: list[str]) -> "ProfileDraft":
        return cls(
            major="CS",
            base_level=BaseLevel(
                programming_years=1,
                languages_known=["C"],
                python_self_rated_level=2,
            ),
            learning_goal=LearningGoal(
                kind="EXAM",
                deadline_iso=None,
                target_kc_codes=target_kc_codes,
            ),
            learning_preference=LearningPreference(
                media_priority=["code", "text", "mindmap"],
                session_minutes_per_day=45,
            ),
        )


class ProfileTurn(BaseModel):
    role: Literal["assistant", "user"]
    content: str


class KcIndexItem(BaseModel):
    kc_code: str
    kc_name: str


class EvidenceAnchor(BaseModel):
    source: Literal["interview", "kc_index", "profile_schema"]
    ref: str
    summary: str


class SafetyResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)


class GroundingResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)


class ProfileAgentInput(BaseModel):
    user_id: int
    interview_session_id: str
    last_turn_history: list[ProfileTurn] = Field(default_factory=list)
    last_user_message: str
    course_kc_index: list[KcIndexItem]


class ProfileAgentOutput(BaseModel):
    next_question: str | None = None
    profile_draft: ProfileDraft | None = None
    done: bool
    dimensions_filled: list[str] = Field(default_factory=list)
    evidence_anchors: list[EvidenceAnchor] = Field(default_factory=list)
    safety_result: SafetyResult
    grounding_result: GroundingResult
