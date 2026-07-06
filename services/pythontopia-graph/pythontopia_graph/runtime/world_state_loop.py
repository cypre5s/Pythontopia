"""Phase 6 game world state loop.

This module turns evidence-bound learning actions into a deterministic town hub
world proposal: state, quest, action, reward, world update, and next quest.
"""

from __future__ import annotations

import hashlib
import json
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

from pythontopia_graph.runtime.loop_runtime import RuntimeStep
from pythontopia_graph.schemas.loop_contract import (
    EvidenceRef as ContractEvidenceRef,
)
from pythontopia_graph.schemas.loop_contract import (
    LoopResult,
    LoopRunStatus,
    LoopScore,
    SCHEMA_VERSION,
)
from pythontopia_graph.schemas.world_loop import (
    EvidenceRef as WorldEvidenceRef,
)
from pythontopia_graph.schemas.world_loop import (
    NpcDialoguePatch,
    RewardPlan,
    WorldLoopProposal,
    WorldQuestProposal,
    WorldStatePatch,
)

ActionKind = Literal[
    "formal_submission_passed",
    "formal_submission_failed",
    "page_read_confirmed",
    "resource_consumed",
    "debug_recovered",
    "profile_updated",
    "learning_action_recorded",
]
RequiredEvidenceType = Literal["submission", "resource", "page_read", "kc_mastery"]
NarrativeRewardType = Literal[
    "badge",
    "mentor_trust",
    "mastery_delta",
    "quest_branch_unlock",
    "title",
    "none",
]

FORBIDDEN_ECONOMY_TERMS = {
    "inventory",
    "currency",
    "shop",
    "coin",
    "coins",
    "gold",
    "money",
    "cash",
    "store",
    "wallet",
}


class WorldStateLoopModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class LearnerWorldSnapshot(WorldStateLoopModel):
    user_id: int = Field(ge=1)
    map_key: str = Field(default="town_hub", min_length=1, max_length=80)
    current_stage_id: str = Field(default="", max_length=128)
    focus_kcs: list[str] = Field(default_factory=list)
    mastery_by_kc: dict[str, float] = Field(default_factory=dict)
    active_quest_ids: list[str] = Field(default_factory=list)


class WorldQuestHistory(WorldStateLoopModel):
    active_quest_ids: list[str] = Field(default_factory=list)
    completed_quest_ids: list[str] = Field(default_factory=list)
    last_completed_quest_id: str = Field(default="", max_length=160)


class LearningActionEvidence(WorldStateLoopModel):
    event_type: str = Field(min_length=1, max_length=128)
    kc_code: str = Field(min_length=1, max_length=64)
    route: str = Field(default="", max_length=160)
    evidence_refs: list[WorldEvidenceRef] = Field(default_factory=list)
    payload: dict[str, Any] = Field(default_factory=dict)


class WorldStateLoopRequest(WorldStateLoopModel):
    learner: LearnerWorldSnapshot
    quest_history: WorldQuestHistory = Field(default_factory=WorldQuestHistory)
    action: LearningActionEvidence
    previous_world_state: dict[str, Any] = Field(default_factory=dict)
    reward_preferences: list[str] = Field(default_factory=lambda: ["badge"])
    policy_version: str = Field(min_length=1, max_length=128)
    trace_id: str = Field(min_length=1, max_length=128)


class WorldStateSnapshot(WorldStateLoopModel):
    user_id: int
    map_key: Literal["town_hub"]
    current_stage_id: str
    focus_kcs: list[str]
    mastery_by_kc: dict[str, float]
    active_quest_ids: list[str]
    visible_flags: list[str] = Field(default_factory=list)
    risk_flags: list[str] = Field(default_factory=list)


class QuestGraphUpdate(WorldStateLoopModel):
    active_quest_ids: list[str]
    completed_quest_ids: list[str]
    completed_this_run: str = ""
    next_quest_id: str = ""
    evidence_refs: list[WorldEvidenceRef] = Field(default_factory=list)


class WorldActionResult(WorldStateLoopModel):
    action_kind: ActionKind
    kc_code: str
    completed_required_evidence: bool
    required_evidence_type: RequiredEvidenceType
    source_route: str = ""
    evidence_refs: list[WorldEvidenceRef] = Field(default_factory=list)


class NarrativeReward(WorldStateLoopModel):
    reward_type: NarrativeRewardType
    summary: str = Field(min_length=1, max_length=240)
    mastery_delta: float = Field(default=0.0, ge=0.0, le=1.0)
    badges: list[str] = Field(default_factory=list)
    mentor_trust_delta: float = Field(default=0.0, ge=0.0, le=1.0)
    evidence_refs: list[WorldEvidenceRef] = Field(default_factory=list)


class RollbackPatch(WorldStateLoopModel):
    key: str = Field(min_length=1, max_length=160)
    previous_value: Any = None
    reason: str = Field(min_length=1, max_length=300)
    evidence_refs: list[WorldEvidenceRef] = Field(default_factory=list)


class WorldStateLoopResult(WorldStateLoopModel):
    status: LoopRunStatus
    stop_reason: str
    steps: list[RuntimeStep]
    world_state: WorldStateSnapshot
    quest_graph: QuestGraphUpdate
    action_result: WorldActionResult
    narrative_reward: NarrativeReward
    world_proposal: WorldLoopProposal | None
    rollback_patch: list[RollbackPatch]
    next_actions: list[str]
    loop_result: LoopResult


class WorldStateLoop:
    """Run the bounded State-Quest-Action-Reward-WorldUpdate-NewQuest loop."""

    loop_name = "game_world_loop"
    loop_type = "WORLD"
    states = ("state", "quest", "action", "reward", "world_update", "new_quest")

    def run(self, request: WorldStateLoopRequest) -> WorldStateLoopResult:
        world_state = self._world_state(request)
        quest_graph = self._quest_graph(request)
        action_result = self._action_result(request)

        if not request.action.evidence_refs:
            return self._rejected(
                request=request,
                world_state=world_state,
                quest_graph=quest_graph,
                action_result=action_result,
                stop_reason="missing_evidence",
                failed_state="state",
                next_actions=["hold:world-patch-review"],
                extra_result={},
            )

        forbidden_terms = self._forbidden_terms(request)
        if forbidden_terms:
            return self._rejected(
                request=request,
                world_state=world_state,
                quest_graph=quest_graph,
                action_result=action_result,
                stop_reason="forbidden_economy_reward",
                failed_state="reward",
                next_actions=["hold:world-patch-review"],
                extra_result={"forbidden_terms": forbidden_terms},
            )

        reward = self._reward(request, action_result)
        proposal = self._world_proposal(request, action_result, reward)
        rollback = self._rollback_patch(request, proposal)
        steps = [RuntimeStep(state=state, status="done") for state in self.states]
        next_actions = ["complete:game_world_loop"]
        loop_result = self._loop_result(
            request=request,
            status="SUCCESS",
            stop_reason="completed",
            world_state=world_state,
            quest_graph=quest_graph,
            action_result=action_result,
            reward=reward,
            proposal=proposal,
            rollback=rollback,
            steps=steps,
            next_actions=next_actions,
            extra_result={},
        )
        return WorldStateLoopResult(
            status="SUCCESS",
            stop_reason="completed",
            steps=steps,
            world_state=world_state,
            quest_graph=quest_graph,
            action_result=action_result,
            narrative_reward=reward,
            world_proposal=proposal,
            rollback_patch=rollback,
            next_actions=next_actions,
            loop_result=loop_result,
        )

    def _world_state(self, request: WorldStateLoopRequest) -> WorldStateSnapshot:
        risk_flags: list[str] = []
        if request.learner.map_key != "town_hub":
            risk_flags.append("map_normalized_to_town_hub")
        focus_kcs = request.learner.focus_kcs or [request.action.kc_code]
        return WorldStateSnapshot(
            user_id=request.learner.user_id,
            map_key="town_hub",
            current_stage_id=request.learner.current_stage_id,
            focus_kcs=focus_kcs,
            mastery_by_kc=dict(request.learner.mastery_by_kc),
            active_quest_ids=list(request.learner.active_quest_ids),
            visible_flags=[
                "town_hub.notice_board",
                "town_hub.training_gate",
                "town_hub.bai_xiao",
            ],
            risk_flags=risk_flags,
        )

    def _quest_graph(self, request: WorldStateLoopRequest) -> QuestGraphUpdate:
        completed = list(request.quest_history.completed_quest_ids)
        active = list(request.quest_history.active_quest_ids or request.learner.active_quest_ids)
        completed_this_run = ""
        quest_id = str(request.action.payload.get("quest_id", "") or "")
        if self._action_completes_required_evidence(request.action):
            completed_this_run = quest_id or (active[0] if active else "")
            if completed_this_run and completed_this_run not in completed:
                completed.append(completed_this_run)
            active = [item for item in active if item != completed_this_run]
        next_quest_id = self._next_quest_id(request.action)
        if next_quest_id not in active:
            active.append(next_quest_id)
        return QuestGraphUpdate(
            active_quest_ids=active,
            completed_quest_ids=completed,
            completed_this_run=completed_this_run,
            next_quest_id=next_quest_id,
            evidence_refs=list(request.action.evidence_refs),
        )

    def _action_result(self, request: WorldStateLoopRequest) -> WorldActionResult:
        action = request.action
        action_kind = self._action_kind(action)
        required_type = self._required_evidence_type(action_kind)
        return WorldActionResult(
            action_kind=action_kind,
            kc_code=action.kc_code,
            completed_required_evidence=self._action_completes_required_evidence(action),
            required_evidence_type=required_type,
            source_route=action.route,
            evidence_refs=list(action.evidence_refs),
        )

    def _reward(
        self, request: WorldStateLoopRequest, action_result: WorldActionResult
    ) -> NarrativeReward:
        reward_type = self._reward_type(request.reward_preferences, action_result)
        mastery_delta = 0.0
        badges: list[str] = []
        mentor_trust_delta = 0.0
        if action_result.action_kind == "formal_submission_passed":
            mastery_delta = 0.05
            badges = [f"kc-ready-{action_result.kc_code.lower()}"]
            mentor_trust_delta = 0.08
        elif action_result.action_kind == "page_read_confirmed":
            mentor_trust_delta = 0.03
        elif action_result.action_kind == "debug_recovered":
            mastery_delta = 0.03
            badges = [f"debug-recovered-{action_result.kc_code.lower()}"]
            mentor_trust_delta = 0.06
        elif action_result.action_kind == "resource_consumed":
            mentor_trust_delta = 0.04
        elif action_result.action_kind == "formal_submission_failed":
            mentor_trust_delta = 0.02
        summary = self._reward_summary(action_result, reward_type, mastery_delta)
        return NarrativeReward(
            reward_type=reward_type,
            summary=summary,
            mastery_delta=mastery_delta,
            badges=badges,
            mentor_trust_delta=mentor_trust_delta,
            evidence_refs=list(action_result.evidence_refs),
        )

    def _world_proposal(
        self,
        request: WorldStateLoopRequest,
        action_result: WorldActionResult,
        reward: NarrativeReward,
    ) -> WorldLoopProposal:
        evidence = list(request.action.evidence_refs)
        proposal_id = self._proposal_id(request)
        state_patch = [
            WorldStatePatch(
                key="town_hub.training_gate.flash",
                value=action_result.kc_code,
                reason="Learning action updates the visible training gate focus.",
                evidence_refs=evidence,
            ),
            WorldStatePatch(
                key="town_hub.notice_board.highlight",
                value=action_result.kc_code,
                reason="Notice board highlights the next evidence-bound step.",
                evidence_refs=evidence,
            ),
        ]
        quest = self._next_quest(request, action_result)
        dialogue = NpcDialoguePatch(
            npc_key="bai_xiao",
            lines=[self._dialogue_line(action_result)],
            evidence_refs=evidence,
        )
        return WorldLoopProposal(
            proposal_id=proposal_id,
            loop_run_id=f"{request.trace_id}:{self.loop_name}",
            state_patch=state_patch,
            new_quests=[quest],
            npc_dialogue=[dialogue],
            reward_plan=RewardPlan(
                summary=reward.summary,
                mastery_delta=reward.mastery_delta,
                badges=reward.badges,
                evidence_refs=evidence,
            ),
            evidence_refs=evidence,
        )

    def _next_quest(
        self, request: WorldStateLoopRequest, action_result: WorldActionResult
    ) -> WorldQuestProposal:
        evidence = list(request.action.evidence_refs)
        if action_result.action_kind == "formal_submission_failed":
            return WorldQuestProposal(
                title=f"Debug {action_result.kc_code} with one counterexample",
                kc_code=action_result.kc_code,
                node_type="FIX_BUG",
                required_evidence_type="submission",
                deep_link_route="/bug-mine",
                evidence_refs=evidence,
            )
        if action_result.action_kind in {"page_read_confirmed", "resource_consumed"}:
            return WorldQuestProposal(
                title=f"Submit a formal practice for {action_result.kc_code}",
                kc_code=action_result.kc_code,
                node_type="SOLVE_PROBLEM",
                required_evidence_type="submission",
                deep_link_route="/training",
                evidence_refs=evidence,
            )
        return WorldQuestProposal(
            title=f"Transfer practice for {action_result.kc_code}",
            kc_code=action_result.kc_code,
            node_type="SOLVE_PROBLEM",
            required_evidence_type="submission",
            deep_link_route="/training",
            evidence_refs=evidence,
        )

    def _rollback_patch(
        self,
        request: WorldStateLoopRequest,
        proposal: WorldLoopProposal,
    ) -> list[RollbackPatch]:
        patches: list[RollbackPatch] = []
        for patch in proposal.state_patch:
            patches.append(
                RollbackPatch(
                    key=patch.key,
                    previous_value=request.previous_world_state.get(patch.key),
                    reason="Restore prior town hub value if this proposal is rejected or rolled back.",
                    evidence_refs=list(patch.evidence_refs),
                )
            )
        return patches

    def _rejected(
        self,
        *,
        request: WorldStateLoopRequest,
        world_state: WorldStateSnapshot,
        quest_graph: QuestGraphUpdate,
        action_result: WorldActionResult,
        stop_reason: str,
        failed_state: str,
        next_actions: list[str],
        extra_result: dict[str, Any],
    ) -> WorldStateLoopResult:
        reward = NarrativeReward(
            reward_type="none",
            summary="World state is held until the learning evidence and reward policy are valid.",
            evidence_refs=list(request.action.evidence_refs),
        )
        steps = [
            RuntimeStep(
                state=state,
                status="failed" if state == failed_state else "done",
                reason=stop_reason if state == failed_state else "",
            )
            for state in self.states[: self.states.index(failed_state) + 1]
        ]
        loop_result = self._loop_result(
            request=request,
            status="REJECTED",
            stop_reason=stop_reason,
            world_state=world_state,
            quest_graph=quest_graph,
            action_result=action_result,
            reward=reward,
            proposal=None,
            rollback=[],
            steps=steps,
            next_actions=next_actions,
            extra_result=extra_result,
        )
        return WorldStateLoopResult(
            status="REJECTED",
            stop_reason=stop_reason,
            steps=steps,
            world_state=world_state,
            quest_graph=quest_graph,
            action_result=action_result,
            narrative_reward=reward,
            world_proposal=None,
            rollback_patch=[],
            next_actions=next_actions,
            loop_result=loop_result,
        )

    def _loop_result(
        self,
        *,
        request: WorldStateLoopRequest,
        status: LoopRunStatus,
        stop_reason: str,
        world_state: WorldStateSnapshot,
        quest_graph: QuestGraphUpdate,
        action_result: WorldActionResult,
        reward: NarrativeReward,
        proposal: WorldLoopProposal | None,
        rollback: list[RollbackPatch],
        steps: list[RuntimeStep],
        next_actions: list[str],
        extra_result: dict[str, Any],
    ) -> LoopResult:
        result_payload: dict[str, Any] = {
            "loop_name": self.loop_name,
            "stop_reason": stop_reason,
            "world_state": world_state.model_dump(mode="json"),
            "quest_graph": quest_graph.model_dump(mode="json"),
            "action_result": action_result.model_dump(mode="json"),
            "narrative_reward": reward.model_dump(mode="json"),
            "rollback_patch": [patch.model_dump(mode="json") for patch in rollback],
            "steps": [step.model_dump(mode="json") for step in steps],
        }
        result_payload.update(extra_result)
        artifacts: list[dict[str, Any]] = []
        if proposal is not None:
            proposal_dump = proposal.model_dump(mode="json")
            result_payload["world_proposal"] = proposal_dump
            artifacts.append(proposal_dump)
        return LoopResult(
            run_id=f"{request.trace_id}:{self.loop_name}",
            loop_type="WORLD",
            status=status,
            result=result_payload,
            scores=[
                LoopScore(name="steps_used", value=float(len(steps))),
                LoopScore(
                    name="world_patch_count",
                    value=float(len(proposal.state_patch) if proposal is not None else 0),
                ),
            ],
            artifacts=artifacts,
            next_actions=next_actions,
            policy_version=request.policy_version,
            trace_id=request.trace_id,
            evidence_refs=self._contract_evidence_refs(request.action.evidence_refs),
            schema_version=SCHEMA_VERSION,
        )

    def _action_kind(self, action: LearningActionEvidence) -> ActionKind:
        event_type = action.event_type
        if event_type == "submission.passed":
            return "formal_submission_passed"
        if event_type == "submission.failed":
            return "formal_submission_failed"
        if event_type == "page.read":
            return "page_read_confirmed"
        if event_type in {"resource.consumed", "resource.feedback"}:
            return "resource_consumed"
        if event_type in {"debug.recovered", "bugmine.recovered"}:
            return "debug_recovered"
        if event_type.startswith("profile."):
            return "profile_updated"
        return "learning_action_recorded"

    def _required_evidence_type(self, action_kind: ActionKind) -> RequiredEvidenceType:
        if action_kind == "page_read_confirmed":
            return "page_read"
        if action_kind == "resource_consumed":
            return "resource"
        if action_kind in {"profile_updated", "learning_action_recorded"}:
            return "kc_mastery"
        return "submission"

    def _action_completes_required_evidence(self, action: LearningActionEvidence) -> bool:
        kind = self._action_kind(action)
        if kind in {"formal_submission_passed", "page_read_confirmed", "resource_consumed", "debug_recovered"}:
            return bool(action.evidence_refs)
        return False

    def _reward_type(
        self,
        reward_preferences: list[str],
        action_result: WorldActionResult,
    ) -> NarrativeRewardType:
        normalized = [item.lower().strip() for item in reward_preferences]
        if action_result.action_kind == "page_read_confirmed" and "mentor_trust" in normalized:
            return "mentor_trust"
        for candidate in ("badge", "mentor_trust", "mastery_delta", "quest_branch_unlock", "title"):
            if candidate in normalized:
                return candidate  # type: ignore[return-value]
        return "badge" if action_result.action_kind == "formal_submission_passed" else "mentor_trust"

    def _reward_summary(
        self,
        action_result: WorldActionResult,
        reward_type: NarrativeRewardType,
        mastery_delta: float,
    ) -> str:
        if mastery_delta > 0:
            return (
                f"{reward_type} reward: evidence-bound mastery progress for "
                f"{action_result.kc_code}."
            )
        return f"{reward_type} reward: evidence-bound town hub guidance for {action_result.kc_code}."

    def _dialogue_line(self, action_result: WorldActionResult) -> str:
        if action_result.action_kind == "formal_submission_failed":
            return f"Evidence says {action_result.kc_code} still needs a small debug step."
        if action_result.action_kind == "page_read_confirmed":
            return f"You read the evidence for {action_result.kc_code}; now prove it once in training."
        return f"{action_result.kc_code} changed through real evidence; I opened the next town step."

    def _next_quest_id(self, action: LearningActionEvidence) -> str:
        safe_kc = action.kc_code.lower().replace(":", "-")
        kind = self._action_kind(action).replace("_", "-")
        return f"quest-{safe_kc}-{kind}"

    def _proposal_id(self, request: WorldStateLoopRequest) -> str:
        payload = "|".join(
            [
                request.trace_id,
                request.action.event_type,
                request.action.kc_code,
                ",".join(str(ref.id) for ref in request.action.evidence_refs),
            ]
        )
        return "world-" + hashlib.sha256(payload.encode()).hexdigest()[:16]

    def _forbidden_terms(self, request: WorldStateLoopRequest) -> list[str]:
        payload = json.dumps(request.action.payload, ensure_ascii=True, sort_keys=True)
        text = " ".join([payload, *request.reward_preferences]).lower()
        return sorted(term for term in FORBIDDEN_ECONOMY_TERMS if term in text)

    def _contract_evidence_refs(
        self, refs: list[WorldEvidenceRef]
    ) -> list[ContractEvidenceRef]:
        out: list[ContractEvidenceRef] = []
        for ref in refs:
            ref_id: int | str = ref.id if isinstance(ref.id, int) else str(ref.id)
            out.append(ContractEvidenceRef(type=ref.type, id=ref_id, label=ref.label))
        return out
