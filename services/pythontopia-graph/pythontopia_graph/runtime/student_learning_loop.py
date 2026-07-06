"""Phase 3 student learning loop.

The loop is deterministic and evidence-bound. It turns a normalized
``EventEnvelope`` plus recent learner context into the six required stages:
observe, diagnose, teach, practice, reflect, update.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

from pythontopia_graph.runtime.loop_runtime import RuntimeStep
from pythontopia_graph.schemas.loop_contract import (
    EvidenceRef,
    EventEnvelope,
    LoopResult,
    LoopRunStatus,
    LoopScore,
    SCHEMA_VERSION,
)

InterventionType = Literal[
    "evidence_review",
    "minimal_hint",
    "remediation",
    "consolidation",
]
PracticeAssignmentType = Literal[
    "evidence_micro_check",
    "targeted_practice",
    "remediation_practice",
    "transfer_practice",
    "formal_submission",
]
PracticeDifficulty = Literal["remedial", "basic", "medium"]


class LearnerKcState(BaseModel):
    kc_code: str = Field(min_length=1, max_length=64)
    mastery: float = Field(default=0.0, ge=0.0, le=1.0)
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    last_practiced_at: str = Field(default="", max_length=64)


class LearnerProfileState(BaseModel):
    user_id: int = Field(ge=1)
    dimensions: dict[str, str] = Field(default_factory=dict)
    confidence_by_dimension: dict[str, float] = Field(default_factory=dict)


class CourseEvidence(BaseModel):
    evidence_ref: EvidenceRef
    kc_codes: list[str] = Field(default_factory=list)
    summary: str = Field(min_length=1, max_length=500)


class StudentLearningLoopRequest(BaseModel):
    event: EventEnvelope
    recent_events: list[EventEnvelope] = Field(default_factory=list)
    profile: LearnerProfileState
    kc_states: list[LearnerKcState] = Field(default_factory=list)
    course_evidence: list[CourseEvidence] = Field(default_factory=list)
    policy_version: str = Field(min_length=1, max_length=128)
    max_steps: int = Field(default=6, ge=1, le=8)


class ObservationSnapshot(BaseModel):
    user_id: int
    observed_event_ids: list[str]
    focus_kcs: list[str]
    repeated_failure_count_by_kc: dict[str, int]
    formal_submission_events: list[str]
    pyodide_preview_events: list[str]
    evidence_refs: list[EvidenceRef]
    risk_flags: list[str]


class Diagnosis(BaseModel):
    gap_kcs: list[str]
    root_causes: list[str]
    confidence: float = Field(ge=0.0, le=1.0)
    evidence_refs: list[EvidenceRef]
    risk_flags: list[str]


class TeachingPlan(BaseModel):
    intervention_type: InterventionType
    target_kcs: list[str]
    evidence_refs: list[EvidenceRef]
    stop_condition: str = Field(min_length=1, max_length=240)
    message: str = Field(min_length=1, max_length=500)


class PracticeAssignment(BaseModel):
    assignment_type: PracticeAssignmentType
    target_kcs: list[str]
    difficulty: PracticeDifficulty
    requires_formal_judge: bool
    problem_id: int | None = None
    prompt: str = Field(min_length=1, max_length=500)


class ReflectionPrompt(BaseModel):
    prompt: str = Field(min_length=1, max_length=500)
    evidence_event_ids: list[str]
    target_dimensions: list[str]


class ProfileDimensionPatch(BaseModel):
    dimension_key: str = Field(min_length=1, max_length=64)
    signal: str = Field(min_length=1, max_length=240)
    confidence: float = Field(ge=0.0, le=1.0)
    evidence_event_ids: list[str]


class ReviewScheduleItem(BaseModel):
    kc_code: str = Field(min_length=1, max_length=64)
    due_in_hours: int = Field(ge=1, le=24 * 30)
    reason: str = Field(min_length=1, max_length=128)


class LearnerUpdatePlan(BaseModel):
    profile_dimension_patches: list[ProfileDimensionPatch] = Field(default_factory=list)
    kc_mastery_deltas: dict[str, float] = Field(default_factory=dict)
    review_schedule: list[ReviewScheduleItem] = Field(default_factory=list)


class StudentLearningLoopResult(BaseModel):
    status: LoopRunStatus
    stop_reason: str
    steps: list[RuntimeStep]
    observation: ObservationSnapshot
    diagnosis: Diagnosis
    teaching_plan: TeachingPlan
    practice_assignment: PracticeAssignment
    reflection: ReflectionPrompt
    update_plan: LearnerUpdatePlan
    next_actions: list[str]
    loop_result: LoopResult


class StudentLearningLoop:
    """Run the bounded Observe-Diagnose-Teach-Practice-Reflect-Update loop."""

    loop_name = "student_learning_loop"
    loop_type = "LEARNER"
    states = ("observe", "diagnose", "teach", "practice", "reflect", "update")

    def run(self, request: StudentLearningLoopRequest) -> StudentLearningLoopResult:
        events = self._recent_events(request)
        observation = self._observe(request, events)
        diagnosis = self._diagnose(request, observation)
        teaching_plan = self._teach(request, observation, diagnosis)
        practice_assignment = self._practice(request, observation, diagnosis)
        reflection = self._reflect(events, diagnosis)
        update_plan = self._update(request, observation, diagnosis)
        status, stop_reason, next_actions = self._termination(
            observation, diagnosis, practice_assignment
        )
        completed_steps = self._steps_for(status, stop_reason)
        loop_result = self._loop_result(
            request=request,
            status=status,
            stop_reason=stop_reason,
            observation=observation,
            diagnosis=diagnosis,
            teaching_plan=teaching_plan,
            practice_assignment=practice_assignment,
            reflection=reflection,
            update_plan=update_plan,
            steps=completed_steps,
            next_actions=next_actions,
        )
        return StudentLearningLoopResult(
            status=status,
            stop_reason=stop_reason,
            steps=completed_steps,
            observation=observation,
            diagnosis=diagnosis,
            teaching_plan=teaching_plan,
            practice_assignment=practice_assignment,
            reflection=reflection,
            update_plan=update_plan,
            next_actions=next_actions,
            loop_result=loop_result,
        )

    def _recent_events(
        self, request: StudentLearningLoopRequest
    ) -> list[EventEnvelope]:
        events = list(request.recent_events)
        if not any(event.event_id == request.event.event_id for event in events):
            events.append(request.event)
        return events

    def _observe(
        self, request: StudentLearningLoopRequest, events: list[EventEnvelope]
    ) -> ObservationSnapshot:
        focus_kcs = self._focus_kcs(request)
        repeated_failures = {kc: 0 for kc in focus_kcs}
        formal_events: list[str] = []
        pyodide_events: list[str] = []
        evidence_refs = self._evidence_refs(request)
        for event in events:
            if event.event_type.startswith("pyodide."):
                pyodide_events.append(event.event_id)
            if self._is_formal_submission(event):
                formal_events.append(event.event_id)
            if event.event_type == "submission.failed" and self._is_formal_submission(event):
                for kc in self._event_kcs(event):
                    if kc in repeated_failures:
                        repeated_failures[kc] += 1
        risk_flags: list[str] = []
        if any(count >= 2 for count in repeated_failures.values()):
            risk_flags.append("repeated_failure")
        if self._low_mastery_focus(request, focus_kcs):
            risk_flags.append("low_mastery")
        if pyodide_events and not formal_events:
            risk_flags.append("formal_judge_required")
        if not evidence_refs:
            risk_flags.append("missing_evidence")
        return ObservationSnapshot(
            user_id=request.event.user_id,
            observed_event_ids=[event.event_id for event in events],
            focus_kcs=focus_kcs,
            repeated_failure_count_by_kc=repeated_failures,
            formal_submission_events=formal_events,
            pyodide_preview_events=pyodide_events,
            evidence_refs=evidence_refs,
            risk_flags=risk_flags,
        )

    def _diagnose(
        self, request: StudentLearningLoopRequest, observation: ObservationSnapshot
    ) -> Diagnosis:
        if "missing_evidence" in observation.risk_flags:
            return Diagnosis(
                gap_kcs=observation.focus_kcs,
                root_causes=["insufficient_evidence"],
                confidence=0.4,
                evidence_refs=[],
                risk_flags=observation.risk_flags,
            )
        if "formal_judge_required" in observation.risk_flags:
            return Diagnosis(
                gap_kcs=observation.focus_kcs,
                root_causes=["pyodide_preview_only"],
                confidence=0.55,
                evidence_refs=observation.evidence_refs,
                risk_flags=observation.risk_flags,
            )
        if request.event.event_type == "submission.passed":
            return Diagnosis(
                gap_kcs=observation.focus_kcs,
                root_causes=["practice_success"],
                confidence=0.78,
                evidence_refs=observation.evidence_refs,
                risk_flags=observation.risk_flags,
            )
        root_causes: list[str] = []
        if "repeated_failure" in observation.risk_flags:
            root_causes.append("repeated_failure")
        if "low_mastery" in observation.risk_flags:
            root_causes.append("low_mastery")
        if not root_causes:
            root_causes.append("single_practice_failure")
        confidence = 0.72 if "repeated_failure" in root_causes else 0.66
        return Diagnosis(
            gap_kcs=observation.focus_kcs,
            root_causes=root_causes,
            confidence=confidence,
            evidence_refs=observation.evidence_refs,
            risk_flags=observation.risk_flags,
        )

    def _teach(
        self,
        request: StudentLearningLoopRequest,
        observation: ObservationSnapshot,
        diagnosis: Diagnosis,
    ) -> TeachingPlan:
        target_kcs = diagnosis.gap_kcs or observation.focus_kcs
        if diagnosis.root_causes == ["insufficient_evidence"]:
            return TeachingPlan(
                intervention_type="evidence_review",
                target_kcs=target_kcs,
                evidence_refs=[],
                stop_condition="Wait for a course page, KC, resource, or formal judge reference.",
                message="Evidence is missing, so the loop only asks for grounded learning evidence.",
            )
        if diagnosis.root_causes == ["practice_success"]:
            return TeachingPlan(
                intervention_type="consolidation",
                target_kcs=target_kcs,
                evidence_refs=diagnosis.evidence_refs,
                stop_condition="Student completes one transfer practice item with formal judge evidence.",
                message="The formal submission passed; consolidate by transferring the same KC to a nearby task.",
            )
        if "repeated_failure" in diagnosis.root_causes:
            return TeachingPlan(
                intervention_type="remediation",
                target_kcs=target_kcs,
                evidence_refs=diagnosis.evidence_refs,
                stop_condition="Student can explain the mistaken step and pass a small formal check.",
                message=self._evidence_message(request, target_kcs, "Review the smallest prerequisite before retrying."),
            )
        return TeachingPlan(
            intervention_type="minimal_hint",
            target_kcs=target_kcs,
            evidence_refs=diagnosis.evidence_refs,
            stop_condition="Student applies the hint and resubmits to the formal judge.",
            message=self._evidence_message(request, target_kcs, "Use the course evidence to inspect one likely mistake."),
        )

    def _practice(
        self,
        request: StudentLearningLoopRequest,
        observation: ObservationSnapshot,
        diagnosis: Diagnosis,
    ) -> PracticeAssignment:
        problem_id = self._problem_id(request.event)
        target_kcs = diagnosis.gap_kcs or observation.focus_kcs
        if diagnosis.root_causes == ["insufficient_evidence"]:
            return PracticeAssignment(
                assignment_type="evidence_micro_check",
                target_kcs=target_kcs,
                difficulty="basic",
                requires_formal_judge=False,
                problem_id=problem_id,
                prompt="Collect a real course page or formal judge reference before making a strong diagnosis.",
            )
        if diagnosis.root_causes == ["pyodide_preview_only"]:
            return PracticeAssignment(
                assignment_type="formal_submission",
                target_kcs=target_kcs,
                difficulty="basic",
                requires_formal_judge=True,
                problem_id=problem_id,
                prompt="The preview is useful for exploration; submit once to the formal judge to update mastery.",
            )
        if diagnosis.root_causes == ["practice_success"]:
            return PracticeAssignment(
                assignment_type="transfer_practice",
                target_kcs=target_kcs,
                difficulty="medium",
                requires_formal_judge=True,
                problem_id=problem_id,
                prompt="Solve a nearby transfer task using the same idea, then submit it to the formal judge.",
            )
        failures = max(observation.repeated_failure_count_by_kc.values() or [0])
        if failures >= 2:
            difficulty: PracticeDifficulty = "remedial" if failures >= 3 else "basic"
            return PracticeAssignment(
                assignment_type="remediation_practice",
                target_kcs=target_kcs,
                difficulty=difficulty,
                requires_formal_judge=True,
                problem_id=problem_id,
                prompt="Retry with a smaller check: predict one list/index step, then submit the adjusted solution.",
            )
        return PracticeAssignment(
            assignment_type="targeted_practice",
            target_kcs=target_kcs,
            difficulty="basic",
            requires_formal_judge=True,
            problem_id=problem_id,
            prompt="Use the hint to make one focused change and submit the result to the formal judge.",
        )

    def _reflect(
        self, events: list[EventEnvelope], diagnosis: Diagnosis
    ) -> ReflectionPrompt:
        dimension = (
            "error_pattern"
            if any(cause in diagnosis.root_causes for cause in ("repeated_failure", "single_practice_failure"))
            else "knowledge_base"
        )
        return ReflectionPrompt(
            prompt="What cue will help you notice this KC pattern next time before submitting?",
            evidence_event_ids=[event.event_id for event in events],
            target_dimensions=[dimension],
        )

    def _update(
        self,
        request: StudentLearningLoopRequest,
        observation: ObservationSnapshot,
        diagnosis: Diagnosis,
    ) -> LearnerUpdatePlan:
        if diagnosis.root_causes == ["insufficient_evidence"]:
            return LearnerUpdatePlan()
        if diagnosis.root_causes == ["pyodide_preview_only"]:
            return LearnerUpdatePlan(
                review_schedule=[
                    self._review_item(observation.focus_kcs[0], 2, "formal_judge_required")
                ]
            )
        event_ids = observation.observed_event_ids
        if diagnosis.root_causes == ["practice_success"]:
            kc = observation.focus_kcs[0]
            return LearnerUpdatePlan(
                profile_dimension_patches=[
                    ProfileDimensionPatch(
                        dimension_key="knowledge_base",
                        signal=f"Formal pass on {kc}",
                        confidence=0.78,
                        evidence_event_ids=event_ids,
                    )
                ],
                kc_mastery_deltas={kc: 0.08},
                review_schedule=[self._review_item(kc, 24, "formal_pass")],
            )
        kc = observation.focus_kcs[0]
        return LearnerUpdatePlan(
            profile_dimension_patches=[
                ProfileDimensionPatch(
                    dimension_key="error_pattern",
                    signal=f"{','.join(diagnosis.root_causes)} on {kc}",
                    confidence=diagnosis.confidence,
                    evidence_event_ids=event_ids,
                )
            ],
            kc_mastery_deltas={kc: -0.03},
            review_schedule=[self._review_item(kc, 4, "remediation_needed")],
        )

    def _termination(
        self,
        observation: ObservationSnapshot,
        diagnosis: Diagnosis,
        practice_assignment: PracticeAssignment,
    ) -> tuple[LoopRunStatus, str, list[str]]:
        if diagnosis.root_causes == ["insufficient_evidence"]:
            return "REJECTED", "missing_evidence", ["request:learner-evidence"]
        if practice_assignment.assignment_type == "formal_submission":
            return "PARTIAL", "formal_judge_evidence_required", ["submit:formal-judge"]
        return "SUCCESS", "completed", ["complete:student_learning_loop"]

    def _steps_for(self, status: LoopRunStatus, stop_reason: str) -> list[RuntimeStep]:
        if status == "REJECTED":
            return [
                RuntimeStep(state="observe", status="done"),
                RuntimeStep(state="diagnose", status="failed", reason=stop_reason),
            ]
        return [RuntimeStep(state=state, status="done") for state in self.states]

    def _loop_result(
        self,
        *,
        request: StudentLearningLoopRequest,
        status: LoopRunStatus,
        stop_reason: str,
        observation: ObservationSnapshot,
        diagnosis: Diagnosis,
        teaching_plan: TeachingPlan,
        practice_assignment: PracticeAssignment,
        reflection: ReflectionPrompt,
        update_plan: LearnerUpdatePlan,
        steps: list[RuntimeStep],
        next_actions: list[str],
    ) -> LoopResult:
        return LoopResult(
            run_id=f"{request.event.event_id}:{self.loop_name}",
            loop_type="LEARNER",
            status=status,
            result={
                "loop_name": self.loop_name,
                "stop_reason": stop_reason,
                "observation": observation.model_dump(mode="json"),
                "diagnosis": diagnosis.model_dump(mode="json"),
                "teaching_plan": teaching_plan.model_dump(mode="json"),
                "practice_assignment": practice_assignment.model_dump(mode="json"),
                "reflection": reflection.model_dump(mode="json"),
                "update_plan": update_plan.model_dump(mode="json"),
                "steps": [step.model_dump(mode="json") for step in steps],
            },
            scores=[
                LoopScore(name="diagnosis_confidence", value=diagnosis.confidence),
                LoopScore(name="steps_used", value=float(len(steps))),
            ],
            artifacts=[],
            next_actions=next_actions,
            policy_version=request.policy_version,
            trace_id=request.event.trace_id,
            evidence_refs=observation.evidence_refs,
            schema_version=SCHEMA_VERSION,
        )

    def _focus_kcs(self, request: StudentLearningLoopRequest) -> list[str]:
        codes: list[str] = []
        for event in [request.event, *request.recent_events]:
            for kc in self._event_kcs(event):
                if kc not in codes:
                    codes.append(kc)
        for state in request.kc_states:
            if state.kc_code not in codes:
                codes.append(state.kc_code)
        return codes or ["UNKNOWN_KC"]

    def _event_kcs(self, event: EventEnvelope) -> list[str]:
        values = event.context.get("kc_codes") or event.payload.get("kc_codes")
        if isinstance(values, list):
            return [str(value) for value in values if str(value)]
        value = event.context.get("kc_code") or event.payload.get("kc_code")
        if value:
            return [str(value)]
        refs = [
            str(ref.id)
            for ref in event.evidence_refs
            if ref.type == "kc" and str(ref.id)
        ]
        return refs

    def _evidence_refs(self, request: StudentLearningLoopRequest) -> list[EvidenceRef]:
        refs: list[EvidenceRef] = []
        for ref in request.event.evidence_refs:
            self._append_ref(refs, ref)
        for evidence in request.course_evidence:
            self._append_ref(refs, evidence.evidence_ref)
        return refs

    def _append_ref(self, refs: list[EvidenceRef], ref: EvidenceRef) -> None:
        key = (ref.type, str(ref.id))
        if not any((existing.type, str(existing.id)) == key for existing in refs):
            refs.append(ref)

    def _low_mastery_focus(
        self, request: StudentLearningLoopRequest, focus_kcs: list[str]
    ) -> bool:
        by_code = {state.kc_code: state for state in request.kc_states}
        return any(by_code.get(kc) is not None and by_code[kc].mastery < 0.4 for kc in focus_kcs)

    def _is_formal_submission(self, event: EventEnvelope) -> bool:
        if not event.event_type.startswith("submission."):
            return False
        return event.payload.get("formal", True) is not False

    def _problem_id(self, event: EventEnvelope) -> int | None:
        raw = event.payload.get("problem_id")
        if raw is None:
            return None
        try:
            return int(raw)
        except (TypeError, ValueError):
            return None

    def _review_item(self, kc_code: str, due_in_hours: int, reason: str) -> ReviewScheduleItem:
        return ReviewScheduleItem(
            kc_code=kc_code,
            due_in_hours=due_in_hours,
            reason=reason,
        )

    def _evidence_message(
        self, request: StudentLearningLoopRequest, target_kcs: list[str], fallback: str
    ) -> str:
        for evidence in request.course_evidence:
            if not target_kcs or set(evidence.kc_codes).intersection(target_kcs):
                return f"{fallback} Evidence: {evidence.summary}"
        return fallback
