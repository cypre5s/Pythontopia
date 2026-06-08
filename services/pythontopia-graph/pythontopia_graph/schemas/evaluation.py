"""EvaluationNarrativeAgent 结构化输出 schema。"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class EvaluationMetric(BaseModel):
    metric_key: str = Field(min_length=1, max_length=64)
    numerator: float = 0.0
    denominator: float = 0.0
    value: float = 0.0
    display_unit: str = ""
    notes: str | None = None


class KcSummary(BaseModel):
    kc_code: str = Field(min_length=1, max_length=64)
    kc_name: str = ""
    mastery_score: float = Field(ge=0.0, le=1.0, default=0.0)
    delta_score: float = Field(ge=-1.0, le=1.0, default=0.0)


class EventSummary(BaseModel):
    event_kind: str
    occurred_at: str
    detail: str = ""


class ProfileBrief(BaseModel):
    major: str = "OTHER"
    python_level: int = Field(default=2, ge=1, le=5)
    has_recent_failures: bool = False


class EvaluationNarrativeRequest(BaseModel):
    user_id: int = Field(ge=1)
    pack_id: int = Field(default=43, ge=1)
    metrics: list[EvaluationMetric] = Field(default_factory=list)
    top_kcs: list[KcSummary] = Field(default_factory=list)
    recent_events: list[EventSummary] = Field(default_factory=list)
    profile_brief: ProfileBrief = ProfileBrief()


class GroundingResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)
    unknown_numbers: list[str] = Field(default_factory=list)
    unknown_kc: list[str] = Field(default_factory=list)


class SafetyResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)


class EvaluationNarrativeResponse(BaseModel):
    narrative_md: str = Field(max_length=4000)
    key_recommendations: list[str] = Field(default_factory=list, max_length=5)
    path_diff_explainer: str = Field(default="", max_length=2000)
    safety_result: SafetyResult
    grounding_result: GroundingResult
    deterministic_signature: str = Field(max_length=128)
    status: Literal["SUCCESS", "PARTIAL", "FAILED"] = "SUCCESS"
