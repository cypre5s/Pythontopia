"""Unified bounded loop runtime skeleton.

Phase 2 keeps this layer deliberately deterministic: it does not replace the
existing orchestrator or specialist agents. It gives all five next-gen loop
families the same state-machine contract, trace fields, policy version, bounded
step handling, fallback behavior, and ``LoopResult`` output shape.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from pydantic import BaseModel, Field

from pythontopia_graph.schemas.loop_contract import (
    EventEnvelope,
    LoopResult,
    LoopRunStatus,
    LoopScore,
    LoopType,
    SCHEMA_VERSION,
)

RuntimeStepStatus = Literal["done", "failed"]


@dataclass(frozen=True)
class LoopTemplate:
    loop_name: str
    loop_type: LoopType
    states: tuple[str, ...]
    max_attempts: int
    fallback_action: str


@dataclass(frozen=True)
class LoopRuntimeConfig:
    default_max_steps: int = 4

    def __post_init__(self) -> None:
        if self.default_max_steps < 1 or self.default_max_steps > 8:
            raise ValueError("default_max_steps must be in 1..8")


class RuntimeStep(BaseModel):
    state: str = Field(min_length=1, max_length=128)
    status: RuntimeStepStatus
    reason: str = Field(default="", max_length=240)


class LoopExecutionRequest(BaseModel):
    loop_name: str = Field(min_length=1, max_length=128)
    event: EventEnvelope
    policy_version: str = Field(min_length=1, max_length=128)
    max_steps: int = 4


class LoopExecutionResult(BaseModel):
    loop_name: str
    loop_type: LoopType
    status: LoopRunStatus
    stop_reason: str
    fallback_used: bool
    max_steps: int
    steps_used: int
    steps: list[RuntimeStep]
    next_actions: list[str]
    loop_result: LoopResult


_TEMPLATES: dict[str, LoopTemplate] = {
    "student_learning_loop": LoopTemplate(
        loop_name="student_learning_loop",
        loop_type="LEARNER",
        states=("observe", "diagnose", "teach", "practice", "reflect", "update"),
        max_attempts=2,
        fallback_action="request:learner-evidence",
    ),
    "code_debug_loop": LoopTemplate(
        loop_name="code_debug_loop",
        loop_type="DEBUG",
        states=("fail", "hypothesize", "hint", "resubmit", "compare", "transfer"),
        max_attempts=3,
        fallback_action="request:debug-evidence",
    ),
    "resource_generation_loop": LoopTemplate(
        loop_name="resource_generation_loop",
        loop_type="RESOURCE",
        states=("goal", "generate", "critique", "revise", "publish", "evaluate"),
        max_attempts=2,
        fallback_action="hold:resource-review",
    ),
    "game_world_loop": LoopTemplate(
        loop_name="game_world_loop",
        loop_type="WORLD",
        states=("state", "quest", "action", "reward", "world_update", "new_quest"),
        max_attempts=2,
        fallback_action="hold:world-patch-review",
    ),
    "system_evolution_loop": LoopTemplate(
        loop_name="system_evolution_loop",
        loop_type="IMPROVE",
        states=("trace", "eval", "cluster", "improve", "deploy", "monitor"),
        max_attempts=1,
        fallback_action="hold:release-candidate",
    ),
}


def supported_loop_templates() -> dict[str, LoopTemplate]:
    """Return a copy of the five supported loop templates."""

    return dict(_TEMPLATES)


class LoopRuntime:
    """Execute one deterministic bounded state-machine pass for a loop family."""

    def __init__(self, config: LoopRuntimeConfig | None = None) -> None:
        self._config = config or LoopRuntimeConfig()

    def execute(self, request: LoopExecutionRequest) -> LoopExecutionResult:
        template = self._template(request.loop_name)
        max_steps = self._resolve_max_steps(request.max_steps)
        if not request.event.evidence_refs:
            return self._missing_evidence(template, request, max_steps)
        if len(template.states) > max_steps:
            return self._partial(template, request, max_steps)
        return self._success(template, request, max_steps)

    def _template(self, loop_name: str) -> LoopTemplate:
        try:
            return _TEMPLATES[loop_name]
        except KeyError as exc:
            raise ValueError(f"unsupported loop_name: {loop_name}") from exc

    def _resolve_max_steps(self, requested: int) -> int:
        resolved = requested if requested is not None else self._config.default_max_steps
        if resolved < 1 or resolved > 8:
            raise ValueError("max_steps must be in 1..8")
        return resolved

    def _missing_evidence(
        self, template: LoopTemplate, request: LoopExecutionRequest, max_steps: int
    ) -> LoopExecutionResult:
        first_state = template.states[0]
        steps = [RuntimeStep(state=first_state, status="failed", reason="missing_evidence")]
        next_actions = [template.fallback_action]
        return self._result(
            template=template,
            request=request,
            max_steps=max_steps,
            steps=steps,
            status="REJECTED",
            stop_reason="missing_evidence",
            fallback_used=True,
            next_actions=next_actions,
            result_payload={
                "completed_states": [],
                "fallback_action": template.fallback_action,
            },
        )

    def _partial(
        self, template: LoopTemplate, request: LoopExecutionRequest, max_steps: int
    ) -> LoopExecutionResult:
        completed = template.states[:max_steps]
        resume_state = template.states[max_steps]
        steps = [RuntimeStep(state=state, status="done") for state in completed]
        next_actions = [f"resume:{template.loop_name}:{resume_state}"]
        return self._result(
            template=template,
            request=request,
            max_steps=max_steps,
            steps=steps,
            status="PARTIAL",
            stop_reason="max_steps_reached",
            fallback_used=True,
            next_actions=next_actions,
            result_payload={
                "completed_states": list(completed),
                "resume_state": resume_state,
            },
        )

    def _success(
        self, template: LoopTemplate, request: LoopExecutionRequest, max_steps: int
    ) -> LoopExecutionResult:
        steps = [RuntimeStep(state=state, status="done") for state in template.states]
        next_actions = [f"complete:{template.loop_name}"]
        return self._result(
            template=template,
            request=request,
            max_steps=max_steps,
            steps=steps,
            status="SUCCESS",
            stop_reason="completed",
            fallback_used=False,
            next_actions=next_actions,
            result_payload={"completed_states": list(template.states)},
        )

    def _result(
        self,
        *,
        template: LoopTemplate,
        request: LoopExecutionRequest,
        max_steps: int,
        steps: list[RuntimeStep],
        status: LoopRunStatus,
        stop_reason: str,
        fallback_used: bool,
        next_actions: list[str],
        result_payload: dict[str, object],
    ) -> LoopExecutionResult:
        steps_used = len(steps)
        loop_result = LoopResult(
            run_id=f"{request.event.event_id}:{template.loop_name}",
            loop_type=template.loop_type,
            status=status,
            result={
                "loop_name": template.loop_name,
                "stop_reason": stop_reason,
                "steps": [step.model_dump() for step in steps],
                **result_payload,
            },
            scores=[LoopScore(name="steps_used", value=float(steps_used))],
            artifacts=[],
            next_actions=next_actions,
            policy_version=request.policy_version,
            trace_id=request.event.trace_id,
            evidence_refs=request.event.evidence_refs,
            schema_version=SCHEMA_VERSION,
        )
        return LoopExecutionResult(
            loop_name=template.loop_name,
            loop_type=template.loop_type,
            status=status,
            stop_reason=stop_reason,
            fallback_used=fallback_used,
            max_steps=max_steps,
            steps_used=steps_used,
            steps=steps,
            next_actions=next_actions,
            loop_result=loop_result,
        )
