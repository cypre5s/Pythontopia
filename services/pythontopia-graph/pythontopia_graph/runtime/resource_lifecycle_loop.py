"""Phase 5 resource lifecycle loop.

The existing ``ResourceGraph`` already performs retrieval, generation, critique,
revision retries, publish decision, and evaluation-hook binding. This runtime
turns that graph response into a recoverable artifact lifecycle contract.
"""

from __future__ import annotations

import hashlib
from typing import Literal

from pydantic import BaseModel, Field

from pythontopia_graph.runtime.loop_runtime import RuntimeStep
from pythontopia_graph.schemas.loop_contract import (
    EvidenceRef,
    LoopResult,
    LoopRunStatus,
    LoopScore,
    SCHEMA_VERSION,
)
from pythontopia_graph.schemas.resource import (
    ResourceProduceResponse,
    ResourceType,
)

CritiqueStatus = Literal["PASSED", "NEEDS_REVIEW", "FAILED"]
PublishLifecycleState = Literal["PUBLISHED", "HOLD_FOR_REVIEW", "REJECTED"]
EvaluationSignal = Literal["PENDING", "INSUFFICIENT_EVIDENCE", "EFFECTIVE", "NEEDS_REVISION"]


class ResourceGoal(BaseModel):
    user_id: int = Field(ge=1)
    kc_codes: list[str] = Field(min_length=1, max_length=10)
    resource_type: ResourceType
    focus_hint: str = Field(default="", max_length=512)


class NormalizedResourceGoal(BaseModel):
    user_id: int
    target_kcs: list[str]
    resource_type: ResourceType
    goal_summary: str = Field(min_length=1, max_length=512)


class ArtifactEffectivenessSnapshot(BaseModel):
    resource_id: int = Field(ge=1)
    feedback_adoption_rate: float = Field(default=0.0, ge=0.0, le=1.0)
    micro_check_pass_rate: float = Field(default=0.0, ge=0.0, le=1.0)
    mastery_delta_sum: float = Field(default=0.0)


class ResourceLifecycleLoopRequest(BaseModel):
    goal: ResourceGoal
    graph_response: ResourceProduceResponse
    revision_of: str | None = Field(default=None, max_length=160)
    evaluation: ArtifactEffectivenessSnapshot | None = None
    policy_version: str = Field(min_length=1, max_length=128)
    trace_id: str = Field(min_length=1, max_length=128)


class ArtifactManifest(BaseModel):
    artifact_id: str = Field(min_length=1, max_length=160)
    resource_type: ResourceType
    title: str
    revision_of: str | None = None
    grounding_refs: list[int] = Field(default_factory=list)
    publish_state: PublishLifecycleState
    evaluation_hook: str = Field(default="", max_length=128)


class CritiqueDimension(BaseModel):
    name: Literal["pedagogy", "grounding", "safety", "game_fit"]
    passed: bool
    status: CritiqueStatus
    reasons: list[str] = Field(default_factory=list)


class CritiqueReport(BaseModel):
    pedagogy: CritiqueDimension
    grounding: CritiqueDimension
    safety: CritiqueDimension
    game_fit: CritiqueDimension


class RevisionPlan(BaseModel):
    required: bool
    revision_of: str | None = None
    reasons: list[str] = Field(default_factory=list)
    next_revision_focus: list[str] = Field(default_factory=list)


class PublishState(BaseModel):
    status: PublishLifecycleState
    reasons: list[str] = Field(default_factory=list)
    evidence_page_ids: list[int] = Field(default_factory=list)


class EvaluationResult(BaseModel):
    signal: EvaluationSignal
    metric_values: dict[str, float] = Field(default_factory=dict)
    evaluation_hook: str = Field(default="", max_length=128)
    resource_id: int | None = None


class ResourceLifecycleLoopResult(BaseModel):
    status: LoopRunStatus
    stop_reason: str
    steps: list[RuntimeStep]
    normalized_goal: NormalizedResourceGoal
    artifact_manifest: ArtifactManifest
    critique_report: CritiqueReport
    revision_plan: RevisionPlan
    publish_state: PublishState
    evaluation_result: EvaluationResult
    next_actions: list[str]
    loop_result: LoopResult


class ResourceLifecycleLoop:
    """Run Goal-Generate-Critique-Revise-Publish-Evaluate over a graph response."""

    loop_name = "resource_generation_loop"
    loop_type = "RESOURCE"
    states = ("goal", "generate", "critique", "revise", "publish", "evaluate")

    def run(self, request: ResourceLifecycleLoopRequest) -> ResourceLifecycleLoopResult:
        goal = self._normalize_goal(request)
        critique = self._critique(request)
        publish_state = self._publish_state(request)
        evaluation = self._evaluate(request)
        revision = self._revision_plan(request, critique, publish_state, evaluation)
        manifest = self._manifest(request, publish_state)
        status, stop_reason, next_actions = self._termination(publish_state, evaluation)
        steps = [RuntimeStep(state=state, status="done") for state in self.states]
        loop_result = self._loop_result(
            request=request,
            status=status,
            stop_reason=stop_reason,
            goal=goal,
            manifest=manifest,
            critique=critique,
            revision=revision,
            publish_state=publish_state,
            evaluation=evaluation,
            steps=steps,
            next_actions=next_actions,
        )
        return ResourceLifecycleLoopResult(
            status=status,
            stop_reason=stop_reason,
            steps=steps,
            normalized_goal=goal,
            artifact_manifest=manifest,
            critique_report=critique,
            revision_plan=revision,
            publish_state=publish_state,
            evaluation_result=evaluation,
            next_actions=next_actions,
            loop_result=loop_result,
        )

    def _normalize_goal(self, request: ResourceLifecycleLoopRequest) -> NormalizedResourceGoal:
        focus = request.goal.focus_hint.strip()
        summary = focus or f"Generate {request.goal.resource_type} for {','.join(request.goal.kc_codes)}"
        return NormalizedResourceGoal(
            user_id=request.goal.user_id,
            target_kcs=list(request.goal.kc_codes),
            resource_type=request.goal.resource_type,
            goal_summary=summary,
        )

    def _critique(self, request: ResourceLifecycleLoopRequest) -> CritiqueReport:
        response = request.graph_response
        grounding_ok = response.grounding_result.passed and bool(response.evidence_page_ids)
        safety_ok = response.safety_result.passed
        pedagogy_ok = bool(response.artifact.title and response.artifact.content_payload())
        return CritiqueReport(
            pedagogy=CritiqueDimension(
                name="pedagogy",
                passed=pedagogy_ok,
                status="PASSED" if pedagogy_ok else "FAILED",
                reasons=[] if pedagogy_ok else ["artifact_missing_pedagogy_body"],
            ),
            grounding=CritiqueDimension(
                name="grounding",
                passed=grounding_ok,
                status="PASSED" if grounding_ok else "NEEDS_REVIEW",
                reasons=self._grounding_reasons(response),
            ),
            safety=CritiqueDimension(
                name="safety",
                passed=safety_ok,
                status="PASSED" if safety_ok else "FAILED",
                reasons=list(response.safety_result.reasons),
            ),
            game_fit=CritiqueDimension(
                name="game_fit",
                passed=True,
                status="PASSED",
                reasons=[],
            ),
        )

    def _publish_state(self, request: ResourceLifecycleLoopRequest) -> PublishState:
        flow = request.graph_response.publish_flow
        decision = flow.publish_decision if flow is not None else None
        if decision is None:
            return PublishState(status="HOLD_FOR_REVIEW", reasons=["publish_flow_missing"])
        status: PublishLifecycleState = {
            "PUBLISH": "PUBLISHED",
            "HOLD_FOR_REVIEW": "HOLD_FOR_REVIEW",
            "REJECT": "REJECTED",
        }[decision.status]
        return PublishState(
            status=status,
            reasons=list(decision.reasons),
            evidence_page_ids=list(decision.evidence_page_ids),
        )

    def _evaluate(self, request: ResourceLifecycleLoopRequest) -> EvaluationResult:
        hook = ""
        if request.graph_response.publish_flow is not None:
            hook = request.graph_response.publish_flow.evaluation_hook.trigger
        if request.evaluation is None:
            return EvaluationResult(signal="PENDING", evaluation_hook=hook)
        metrics = {
            "feedback_adoption_rate": request.evaluation.feedback_adoption_rate,
            "micro_check_pass_rate": request.evaluation.micro_check_pass_rate,
            "mastery_delta_sum": request.evaluation.mastery_delta_sum,
        }
        if (
            request.evaluation.feedback_adoption_rate < 0.3
            or request.evaluation.micro_check_pass_rate < 0.4
            or request.evaluation.mastery_delta_sum < 0
        ):
            signal: EvaluationSignal = "NEEDS_REVISION"
        elif any(value > 0 for value in metrics.values()):
            signal = "EFFECTIVE"
        else:
            signal = "INSUFFICIENT_EVIDENCE"
        return EvaluationResult(
            signal=signal,
            metric_values=metrics,
            evaluation_hook=hook,
            resource_id=request.evaluation.resource_id,
        )

    def _revision_plan(
        self,
        request: ResourceLifecycleLoopRequest,
        critique: CritiqueReport,
        publish_state: PublishState,
        evaluation: EvaluationResult,
    ) -> RevisionPlan:
        reasons: list[str] = []
        focus: list[str] = []
        if not critique.grounding.passed:
            reasons.append("grounding_missing_or_failed")
            focus.append("add_bound_course_evidence")
        if not critique.safety.passed or publish_state.status == "REJECTED":
            reasons.append("critic_failure")
            focus.append("remove_unsafe_or_ungrounded_content")
        if evaluation.signal == "NEEDS_REVISION":
            reasons.append("effectiveness_needs_revision")
            focus.append("revise_for_learning_effect")
        return RevisionPlan(
            required=bool(reasons),
            revision_of=request.revision_of,
            reasons=reasons,
            next_revision_focus=focus,
        )

    def _manifest(
        self,
        request: ResourceLifecycleLoopRequest,
        publish_state: PublishState,
    ) -> ArtifactManifest:
        response = request.graph_response
        hook = ""
        if response.publish_flow is not None:
            hook = response.publish_flow.evaluation_hook.trigger
        return ArtifactManifest(
            artifact_id=self._artifact_id(request),
            resource_type=response.type,
            title=response.artifact.title,
            revision_of=request.revision_of,
            grounding_refs=list(response.evidence_page_ids),
            publish_state=publish_state.status,
            evaluation_hook=hook,
        )

    def _termination(
        self,
        publish_state: PublishState,
        evaluation: EvaluationResult,
    ) -> tuple[LoopRunStatus, str, list[str]]:
        if publish_state.status == "REJECTED":
            return "FAILED", "critic_rejected", ["revise:critic-failure"]
        if publish_state.status == "HOLD_FOR_REVIEW":
            return "PARTIAL", "held_for_review", ["revise:add-grounding-evidence"]
        if evaluation.signal == "NEEDS_REVISION":
            return "PARTIAL", "effectiveness_needs_revision", ["revise:effectiveness"]
        if evaluation.signal in {"PENDING", "INSUFFICIENT_EVIDENCE"}:
            return "SUCCESS", "published_awaiting_effectiveness", ["evaluate:resource_feedback_or_check"]
        return "SUCCESS", "completed", ["complete:resource_generation_loop"]

    def _loop_result(
        self,
        *,
        request: ResourceLifecycleLoopRequest,
        status: LoopRunStatus,
        stop_reason: str,
        goal: NormalizedResourceGoal,
        manifest: ArtifactManifest,
        critique: CritiqueReport,
        revision: RevisionPlan,
        publish_state: PublishState,
        evaluation: EvaluationResult,
        steps: list[RuntimeStep],
        next_actions: list[str],
    ) -> LoopResult:
        evidence_refs = [
            EvidenceRef(type="course_page", id=page_id)
            for page_id in request.graph_response.evidence_page_ids
        ]
        return LoopResult(
            run_id=f"{manifest.artifact_id}:{self.loop_name}",
            loop_type="RESOURCE",
            status=status,
            result={
                "loop_name": self.loop_name,
                "stop_reason": stop_reason,
                "normalized_goal": goal.model_dump(mode="json"),
                "artifact_manifest": manifest.model_dump(mode="json"),
                "critique_report": critique.model_dump(mode="json"),
                "revision_plan": revision.model_dump(mode="json"),
                "publish_state": publish_state.model_dump(mode="json"),
                "evaluation_result": evaluation.model_dump(mode="json"),
                "steps": [step.model_dump(mode="json") for step in steps],
            },
            scores=[
                LoopScore(name="steps_used", value=float(len(steps))),
                LoopScore(name="grounding_ref_count", value=float(len(manifest.grounding_refs))),
            ],
            artifacts=[manifest.model_dump(mode="json")],
            next_actions=next_actions,
            policy_version=request.policy_version,
            trace_id=request.trace_id,
            evidence_refs=evidence_refs,
            schema_version=SCHEMA_VERSION,
        )

    def _grounding_reasons(self, response: ResourceProduceResponse) -> list[str]:
        reasons = list(response.grounding_result.reasons)
        if not response.evidence_page_ids:
            reasons.append("grounding_evidence_missing")
        return reasons

    def _artifact_id(self, request: ResourceLifecycleLoopRequest) -> str:
        payload = "|".join(
            [
                request.trace_id,
                request.graph_response.type,
                request.graph_response.artifact.title,
                request.revision_of or "",
                ",".join(str(page) for page in request.graph_response.evidence_page_ids),
            ]
        )
        return "artifact-" + hashlib.sha256(payload.encode()).hexdigest()[:16]
