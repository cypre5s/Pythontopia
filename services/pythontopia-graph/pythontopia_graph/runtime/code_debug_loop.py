"""Phase 4 code debug loop.

This module makes the Debug loop explicit and recoverable: a failed formal
submission becomes structured failure observation, ranked hypotheses, minimal
hint, optional resubmission comparison, transfer task, and memory writeback.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

from pythontopia_graph.runtime.loop_runtime import RuntimeStep
from pythontopia_graph.schemas.debug import CounterExampleCard, VariableTrace
from pythontopia_graph.schemas.loop_contract import (
    EvidenceRef,
    LoopResult,
    LoopRunStatus,
    LoopScore,
    SCHEMA_VERSION,
)

DebugVerdict = Literal["AC", "WA", "RE", "TLE", "CE", "MLE", "SYSTEM_ERROR"]
FailureKind = Literal[
    "wrong_answer",
    "runtime_error",
    "timeout",
    "compile_error",
    "memory_error",
    "system_error",
]
HypothesisAngle = Literal["runtime_behavior", "code_structure", "kc_prerequisite"]
HintDisclosurePolicy = Literal["minimal_hint", "evidence_only"]
TransferAssignmentType = Literal["transfer_practice", "prerequisite_remediation"]


class DebugSubmissionSnapshot(BaseModel):
    submission_id: int = Field(ge=1)
    problem_id: int = Field(ge=1)
    user_id: int = Field(ge=1)
    kc_codes: list[str] = Field(default_factory=list)
    verdict: DebugVerdict
    code: str = Field(max_length=8192)
    counter_example: CounterExampleCard
    variable_trace: VariableTrace = Field(default_factory=VariableTrace)
    stderr: str = Field(default="", max_length=2048)
    evidence_refs: list[EvidenceRef] = Field(default_factory=list)


class ResubmissionSnapshot(BaseModel):
    submission_id: int = Field(ge=1)
    verdict: DebugVerdict
    code: str = Field(max_length=8192)
    passed_cases: int = Field(default=0, ge=0)
    total_cases: int = Field(default=0, ge=0)


class DebugCourseEvidence(BaseModel):
    evidence_ref: EvidenceRef
    kc_codes: list[str] = Field(default_factory=list)
    summary: str = Field(min_length=1, max_length=500)


class CodeDebugLoopRequest(BaseModel):
    failure: DebugSubmissionSnapshot
    resubmission: ResubmissionSnapshot | None = None
    prior_failure_count: int = Field(default=0, ge=0, le=20)
    course_evidence: list[DebugCourseEvidence] = Field(default_factory=list)
    policy_version: str = Field(min_length=1, max_length=128)
    trace_id: str = Field(min_length=1, max_length=128)
    max_debug_attempts: int = Field(default=3, ge=1, le=8)


class FailureObservation(BaseModel):
    submission_id: int
    problem_id: int
    verdict: DebugVerdict
    failure_kind: FailureKind
    target_kcs: list[str]
    attempt_count: int = Field(ge=1)
    evidence_refs: list[EvidenceRef]
    risk_flags: list[str]


class DebugHypothesis(BaseModel):
    hypothesis_id: str = Field(min_length=1, max_length=128)
    angle: HypothesisAngle
    root_cause: str = Field(min_length=1, max_length=128)
    confidence: float = Field(ge=0.0, le=1.0)
    evidence_refs: list[EvidenceRef]
    evidence_summary: str = Field(min_length=1, max_length=500)


class HintPlan(BaseModel):
    level: int = Field(ge=1, le=3)
    disclosure_policy: HintDisclosurePolicy
    message: str = Field(min_length=1, max_length=500)
    related_pages: list[int] = Field(default_factory=list)


class ResubmissionComparison(BaseModel):
    previous_submission_id: int
    current_submission_id: int | None = None
    verdict_transition: str = Field(min_length=1, max_length=64)
    code_change_summary: str = Field(min_length=1, max_length=500)
    behavior_change: str = Field(min_length=1, max_length=500)
    resolved: bool
    needs_transfer: bool


class TransferTask(BaseModel):
    assignment_type: TransferAssignmentType
    target_kcs: list[str]
    source_problem_id: int
    difficulty: Literal["remedial", "basic", "medium"]
    requires_formal_judge: bool
    prompt: str = Field(min_length=1, max_length=500)


class DebugMemoryWriteback(BaseModel):
    writeback_recommended: bool
    error_pattern: str = Field(default="", max_length=160)
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    evidence_submission_ids: list[int] = Field(default_factory=list)


class CodeDebugLoopResult(BaseModel):
    status: LoopRunStatus
    stop_reason: str
    steps: list[RuntimeStep]
    failure_observation: FailureObservation
    hypotheses: list[DebugHypothesis]
    hint: HintPlan
    resubmission_comparison: ResubmissionComparison
    transfer_task: TransferTask
    memory_writeback: DebugMemoryWriteback
    next_actions: list[str]
    loop_result: LoopResult


class CodeDebugLoop:
    """Run the bounded Fail-Hypothesize-Hint-Resubmit-Compare-Transfer loop."""

    loop_name = "code_debug_loop"
    loop_type = "DEBUG"
    states = ("fail", "hypothesize", "hint", "resubmit", "compare", "transfer")

    def run(self, request: CodeDebugLoopRequest) -> CodeDebugLoopResult:
        observation = self._observe_failure(request)
        hypotheses = self._hypothesize(request, observation)
        hint = self._hint(request, observation, hypotheses[0])
        comparison = self._compare(request)
        transfer_task = self._transfer_task(request, observation, comparison)
        memory_writeback = self._memory_writeback(request, observation, hypotheses[0])
        status, stop_reason, next_actions = self._termination(
            observation, comparison, transfer_task
        )
        steps = self._steps_for(status, stop_reason, request.resubmission)
        loop_result = self._loop_result(
            request=request,
            status=status,
            stop_reason=stop_reason,
            observation=observation,
            hypotheses=hypotheses,
            hint=hint,
            comparison=comparison,
            transfer_task=transfer_task,
            memory_writeback=memory_writeback,
            steps=steps,
            next_actions=next_actions,
        )
        return CodeDebugLoopResult(
            status=status,
            stop_reason=stop_reason,
            steps=steps,
            failure_observation=observation,
            hypotheses=hypotheses,
            hint=hint,
            resubmission_comparison=comparison,
            transfer_task=transfer_task,
            memory_writeback=memory_writeback,
            next_actions=next_actions,
            loop_result=loop_result,
        )

    def _observe_failure(self, request: CodeDebugLoopRequest) -> FailureObservation:
        evidence_refs = self._evidence_refs(request)
        attempt_count = request.prior_failure_count + 1
        risk_flags: list[str] = []
        if not evidence_refs:
            risk_flags.append("missing_evidence")
        if attempt_count >= request.max_debug_attempts:
            risk_flags.append("max_attempts_reached")
        return FailureObservation(
            submission_id=request.failure.submission_id,
            problem_id=request.failure.problem_id,
            verdict=request.failure.verdict,
            failure_kind=self._failure_kind(request.failure.verdict),
            target_kcs=list(request.failure.kc_codes),
            attempt_count=attempt_count,
            evidence_refs=evidence_refs,
            risk_flags=risk_flags,
        )

    def _hypothesize(
        self, request: CodeDebugLoopRequest, observation: FailureObservation
    ) -> list[DebugHypothesis]:
        if "missing_evidence" in observation.risk_flags:
            return self._missing_evidence_hypotheses(observation)
        if request.failure.verdict == "RE":
            raw = [
                ("runtime_behavior", "runtime_exception", 0.84, self._stderr_summary(request)),
                ("code_structure", "unsafe_state_or_index", 0.72, "Code likely reads or indexes a value before checking shape."),
                ("kc_prerequisite", "defensive_input_reasoning", 0.64, "Review input shape and branch preconditions before retry."),
            ]
        elif request.failure.verdict == "TLE":
            raw = [
                ("runtime_behavior", "loop_or_complexity", 0.84, "Execution did not finish within the judge limit."),
                ("code_structure", "unbounded_loop", 0.76, "Loop boundary or update step is likely missing or too broad."),
                ("kc_prerequisite", "complexity_reasoning_gap", 0.66, "Review loop termination and data-size reasoning."),
            ]
        elif request.failure.verdict == "CE":
            raw = [
                ("runtime_behavior", "static_restriction", 0.8, "Static check rejected the submitted source."),
                ("code_structure", "forbidden_or_invalid_syntax", 0.72, "Inspect imports, syntax, and restricted expressions."),
                ("kc_prerequisite", "language_rule_gap", 0.62, "Review the Python syntax rule tied to this KC."),
            ]
        else:
            raw = [
                ("runtime_behavior", "output_mismatch", 0.8, request.failure.counter_example.diff_summary),
                ("code_structure", "missing_transform", 0.72, "The code echoes input or skips the required transformation."),
                ("kc_prerequisite", "kc_misapplied", 0.66, self._course_summary(request)),
            ]
        hypotheses = [
            DebugHypothesis(
                hypothesis_id=f"{request.failure.submission_id}:{angle}",
                angle=angle,  # type: ignore[arg-type]
                root_cause=root_cause,
                confidence=confidence,
                evidence_refs=observation.evidence_refs,
                evidence_summary=summary,
            )
            for angle, root_cause, confidence, summary in raw
        ]
        return sorted(hypotheses, key=lambda item: item.confidence, reverse=True)

    def _missing_evidence_hypotheses(
        self, observation: FailureObservation
    ) -> list[DebugHypothesis]:
        return [
            DebugHypothesis(
                hypothesis_id=f"{observation.submission_id}:{angle}",
                angle=angle,  # type: ignore[arg-type]
                root_cause="insufficient_evidence",
                confidence=0.4,
                evidence_refs=[],
                evidence_summary="No course page, KC, counterexample, or judge evidence was supplied.",
            )
            for angle in ("runtime_behavior", "code_structure", "kc_prerequisite")
        ]

    def _hint(
        self,
        request: CodeDebugLoopRequest,
        observation: FailureObservation,
        top_hypothesis: DebugHypothesis,
    ) -> HintPlan:
        if "missing_evidence" in observation.risk_flags:
            return HintPlan(
                level=1,
                disclosure_policy="evidence_only",
                message="Provide a failing judge result, counterexample, or course evidence before deeper hints.",
                related_pages=[],
            )
        if "max_attempts_reached" in observation.risk_flags:
            return HintPlan(
                level=1,
                disclosure_policy="minimal_hint",
                message="Pause debugging and return to the prerequisite KC with one tiny formal check.",
                related_pages=self._related_pages(request),
            )
        return HintPlan(
            level=1,
            disclosure_policy="minimal_hint",
            message=(
                "Before editing broadly, compare the failing output with the expected output "
                f"and inspect the likely cause: {top_hypothesis.root_cause}."
            ),
            related_pages=self._related_pages(request),
        )

    def _compare(self, request: CodeDebugLoopRequest) -> ResubmissionComparison:
        if request.resubmission is None:
            return ResubmissionComparison(
                previous_submission_id=request.failure.submission_id,
                current_submission_id=None,
                verdict_transition=f"{request.failure.verdict}->PENDING",
                code_change_summary="No formal resubmission has been provided yet.",
                behavior_change="Awaiting judge result.",
                resolved=False,
                needs_transfer=False,
            )
        resolved = request.resubmission.verdict == "AC"
        return ResubmissionComparison(
            previous_submission_id=request.failure.submission_id,
            current_submission_id=request.resubmission.submission_id,
            verdict_transition=f"{request.failure.verdict}->{request.resubmission.verdict}",
            code_change_summary=self._code_change_summary(
                request.failure.code, request.resubmission.code
            ),
            behavior_change=(
                f"Formal judge cases {request.resubmission.passed_cases}/"
                f"{request.resubmission.total_cases}; verdict {request.resubmission.verdict}."
            ),
            resolved=resolved,
            needs_transfer=resolved,
        )

    def _transfer_task(
        self,
        request: CodeDebugLoopRequest,
        observation: FailureObservation,
        comparison: ResubmissionComparison,
    ) -> TransferTask:
        target_kcs = observation.target_kcs or ["UNKNOWN_KC"]
        if "max_attempts_reached" in observation.risk_flags and not comparison.resolved:
            return TransferTask(
                assignment_type="prerequisite_remediation",
                target_kcs=target_kcs,
                source_problem_id=observation.problem_id,
                difficulty="remedial",
                requires_formal_judge=True,
                prompt="Return to one prerequisite micro-task, then retry the original problem after a formal pass.",
            )
        return TransferTask(
            assignment_type="transfer_practice",
            target_kcs=target_kcs,
            source_problem_id=observation.problem_id,
            difficulty="medium" if comparison.resolved else "basic",
            requires_formal_judge=True,
            prompt="Solve a nearby task with the same KC and submit it to the formal judge.",
        )

    def _memory_writeback(
        self,
        request: CodeDebugLoopRequest,
        observation: FailureObservation,
        top_hypothesis: DebugHypothesis,
    ) -> DebugMemoryWriteback:
        if "missing_evidence" in observation.risk_flags:
            return DebugMemoryWriteback(writeback_recommended=False)
        submission_ids = [request.failure.submission_id]
        if request.resubmission is not None:
            submission_ids.append(request.resubmission.submission_id)
        return DebugMemoryWriteback(
            writeback_recommended=True,
            error_pattern=top_hypothesis.root_cause,
            confidence=top_hypothesis.confidence,
            evidence_submission_ids=submission_ids,
        )

    def _termination(
        self,
        observation: FailureObservation,
        comparison: ResubmissionComparison,
        transfer_task: TransferTask,
    ) -> tuple[LoopRunStatus, str, list[str]]:
        if "missing_evidence" in observation.risk_flags:
            return "REJECTED", "missing_debug_evidence", ["request:debug-evidence"]
        if transfer_task.assignment_type == "prerequisite_remediation":
            return "PARTIAL", "max_debug_attempts_reached", ["fallback:prerequisite-remediation"]
        if comparison.current_submission_id is None:
            return "PARTIAL", "awaiting_resubmission", ["resubmit:formal-judge"]
        if comparison.resolved:
            return "SUCCESS", "completed", ["complete:code_debug_loop"]
        return "PARTIAL", "resubmission_still_failing", ["continue:code-debug-loop"]

    def _steps_for(
        self,
        status: LoopRunStatus,
        stop_reason: str,
        resubmission: ResubmissionSnapshot | None,
    ) -> list[RuntimeStep]:
        if status == "REJECTED":
            return [
                RuntimeStep(state="fail", status="done"),
                RuntimeStep(state="hypothesize", status="failed", reason=stop_reason),
            ]
        if resubmission is None:
            return [
                RuntimeStep(state="fail", status="done"),
                RuntimeStep(state="hypothesize", status="done"),
                RuntimeStep(state="hint", status="done"),
            ]
        return [RuntimeStep(state=state, status="done") for state in self.states]

    def _loop_result(
        self,
        *,
        request: CodeDebugLoopRequest,
        status: LoopRunStatus,
        stop_reason: str,
        observation: FailureObservation,
        hypotheses: list[DebugHypothesis],
        hint: HintPlan,
        comparison: ResubmissionComparison,
        transfer_task: TransferTask,
        memory_writeback: DebugMemoryWriteback,
        steps: list[RuntimeStep],
        next_actions: list[str],
    ) -> LoopResult:
        return LoopResult(
            run_id=f"{request.failure.submission_id}:{self.loop_name}",
            loop_type="DEBUG",
            status=status,
            result={
                "loop_name": self.loop_name,
                "stop_reason": stop_reason,
                "failure_observation": observation.model_dump(mode="json"),
                "hypotheses": [item.model_dump(mode="json") for item in hypotheses],
                "hint": hint.model_dump(mode="json"),
                "resubmission_comparison": comparison.model_dump(mode="json"),
                "transfer_task": transfer_task.model_dump(mode="json"),
                "memory_writeback": memory_writeback.model_dump(mode="json"),
                "steps": [step.model_dump(mode="json") for step in steps],
            },
            scores=[
                LoopScore(name="top_hypothesis_confidence", value=hypotheses[0].confidence),
                LoopScore(name="steps_used", value=float(len(steps))),
            ],
            artifacts=[],
            next_actions=next_actions,
            policy_version=request.policy_version,
            trace_id=request.trace_id,
            evidence_refs=observation.evidence_refs,
            schema_version=SCHEMA_VERSION,
        )

    def _evidence_refs(self, request: CodeDebugLoopRequest) -> list[EvidenceRef]:
        refs: list[EvidenceRef] = []
        for ref in request.failure.evidence_refs:
            self._append_ref(refs, ref)
        for evidence in request.course_evidence:
            self._append_ref(refs, evidence.evidence_ref)
        return refs

    def _append_ref(self, refs: list[EvidenceRef], ref: EvidenceRef) -> None:
        key = (ref.type, str(ref.id))
        if not any((existing.type, str(existing.id)) == key for existing in refs):
            refs.append(ref)

    def _failure_kind(self, verdict: DebugVerdict) -> FailureKind:
        return {
            "WA": "wrong_answer",
            "RE": "runtime_error",
            "TLE": "timeout",
            "CE": "compile_error",
            "MLE": "memory_error",
            "SYSTEM_ERROR": "system_error",
            "AC": "wrong_answer",
        }[verdict]  # type: ignore[return-value]

    def _stderr_summary(self, request: CodeDebugLoopRequest) -> str:
        if request.failure.stderr:
            return request.failure.stderr[:300]
        return "Runtime failure was reported by the judge."

    def _course_summary(self, request: CodeDebugLoopRequest) -> str:
        if request.course_evidence:
            return request.course_evidence[0].summary
        return "Review the KC evidence before retrying."

    def _related_pages(self, request: CodeDebugLoopRequest) -> list[int]:
        pages: list[int] = []
        for evidence in request.course_evidence:
            if evidence.evidence_ref.type == "course_page":
                try:
                    pages.append(int(evidence.evidence_ref.id))
                except (TypeError, ValueError):
                    continue
        return pages[:3]

    def _code_change_summary(self, before: str, after: str) -> str:
        before_lines = before.splitlines()
        after_lines = after.splitlines()
        changed = sum(1 for left, right in zip(before_lines, after_lines) if left != right)
        changed += abs(len(before_lines) - len(after_lines))
        return f"{changed} line(s) changed; no full solution text stored in the debug loop."
