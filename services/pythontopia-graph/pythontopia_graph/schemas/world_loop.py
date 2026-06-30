from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class WorldLoopModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class EvidenceRef(WorldLoopModel):
    type: Literal[
        "submission_id",
        "resource_id",
        "page_read_id",
        "kc_mastery_delta",
        "loop_run_id",
    ]
    id: int | float | str
    label: str = Field(default="", max_length=200)


class WorldStatePatch(WorldLoopModel):
    key: str = Field(min_length=1, max_length=160)
    value: Any
    reason: str = Field(min_length=1, max_length=300)
    evidence_refs: list[EvidenceRef] = Field(min_length=1)


class WorldQuestProposal(WorldLoopModel):
    title: str = Field(min_length=1, max_length=120)
    kc_code: str = Field(min_length=1, max_length=64)
    node_type: Literal[
        "READ_RESOURCE",
        "SOLVE_PROBLEM",
        "FIX_BUG",
        "PLANT_KC",
        "PROFILE_UPDATE",
    ]
    required_evidence_type: Literal["submission", "resource", "page_read", "kc_mastery"]
    deep_link_route: str = Field(min_length=1, max_length=160)
    evidence_refs: list[EvidenceRef] = Field(min_length=1)


class NpcDialoguePatch(WorldLoopModel):
    npc_key: str = Field(min_length=1, max_length=80)
    lines: list[str] = Field(min_length=1, max_length=4)
    evidence_refs: list[EvidenceRef] = Field(min_length=1)


class RewardPlan(WorldLoopModel):
    summary: str = Field(min_length=1, max_length=240)
    mastery_delta: float = Field(default=0.0, ge=0.0, le=1.0)
    badges: list[str] = Field(default_factory=list)
    evidence_refs: list[EvidenceRef] = Field(min_length=1)


class WorldLoopProposal(WorldLoopModel):
    schema_version: str = "world-loop-proposal.v1"
    proposal_id: str = Field(min_length=1, max_length=128)
    loop_run_id: str = Field(min_length=1, max_length=128)
    state_patch: list[WorldStatePatch] = Field(min_length=1)
    new_quests: list[WorldQuestProposal] = Field(min_length=1)
    npc_dialogue: list[NpcDialoguePatch] = Field(min_length=1)
    reward_plan: RewardPlan
    evidence_refs: list[EvidenceRef] = Field(min_length=1)

    @model_validator(mode="after")
    def _proposal_changes_are_evidence_bound(self) -> "WorldLoopProposal":
        if not self.has_complete_evidence_binding():
            raise ValueError("world loop proposal changes require evidence_refs")
        return self

    def has_complete_evidence_binding(self) -> bool:
        if not self.evidence_refs or not self.reward_plan.evidence_refs:
            return False
        return (
            all(patch.evidence_refs for patch in self.state_patch)
            and all(quest.evidence_refs for quest in self.new_quests)
            and all(dialogue.evidence_refs for dialogue in self.npc_dialogue)
        )
