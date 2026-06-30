from __future__ import annotations

import pytest

from pythontopia_graph.runtime.loop_runtime import (
    LoopExecutionRequest,
    LoopRuntime,
    LoopRuntimeConfig,
    supported_loop_templates,
)
from pythontopia_graph.schemas.loop_contract import EventEnvelope, EvidenceRef, LoopResult


def event(loop_name: str, *, evidence: bool = True) -> EventEnvelope:
    return EventEnvelope(
        event_id=f"evt-{loop_name}",
        event_type=f"{loop_name}.requested",
        user_id=7,
        session_id="sess-phase2",
        trace_id=f"trace-{loop_name}",
        timestamp="2026-06-30T21:30:00Z",
        context={"kc_codes": ["PY43-KC-001"]},
        payload={"goal": "run bounded loop", "loop_name": loop_name},
        evidence_refs=[EvidenceRef(type="kc", id="PY43-KC-001")] if evidence else [],
    )


def request(loop_name: str, *, evidence: bool = True, max_steps: int = 8) -> LoopExecutionRequest:
    return LoopExecutionRequest(
        loop_name=loop_name,
        event=event(loop_name, evidence=evidence),
        policy_version="agentic_loop_v1",
        max_steps=max_steps,
    )


def test_runtime_templates_cover_all_five_phase2_loop_types() -> None:
    templates = supported_loop_templates()

    assert set(templates) == {
        "student_learning_loop",
        "code_debug_loop",
        "resource_generation_loop",
        "game_world_loop",
        "system_evolution_loop",
    }
    assert {template.loop_type for template in templates.values()} == {
        "LEARNER",
        "DEBUG",
        "RESOURCE",
        "WORLD",
        "IMPROVE",
    }
    assert all(template.max_attempts >= 1 for template in templates.values())
    assert all(template.fallback_action for template in templates.values())


@pytest.mark.parametrize("loop_name", sorted(supported_loop_templates().keys()))
def test_runtime_executes_each_loop_happy_path_as_structured_loop_result(loop_name: str) -> None:
    result = LoopRuntime().execute(request(loop_name))

    assert result.loop_name == loop_name
    assert result.status == "SUCCESS"
    assert result.stop_reason == "completed"
    assert result.fallback_used is False
    assert result.steps_used == len(result.steps)
    assert 1 <= result.steps_used <= result.max_steps
    assert result.loop_result.loop_type == result.loop_type
    assert result.loop_result.policy_version == "agentic_loop_v1"
    assert result.loop_result.trace_id == f"trace-{loop_name}"
    assert result.loop_result.evidence_refs
    assert isinstance(LoopResult.model_validate(result.loop_result.model_dump()), LoopResult)


@pytest.mark.parametrize("loop_name", sorted(supported_loop_templates().keys()))
def test_runtime_missing_evidence_uses_bounded_fail_path(loop_name: str) -> None:
    result = LoopRuntime().execute(request(loop_name, evidence=False))

    assert result.status == "REJECTED"
    assert result.stop_reason == "missing_evidence"
    assert result.fallback_used is True
    assert result.next_actions == [supported_loop_templates()[loop_name].fallback_action]
    assert result.steps_used == 1
    assert result.steps[0].status == "failed"
    assert result.loop_result.status == "REJECTED"


def test_runtime_respects_max_steps_and_returns_partial_checkpoint() -> None:
    result = LoopRuntime().execute(request("student_learning_loop", max_steps=3))

    assert result.status == "PARTIAL"
    assert result.stop_reason == "max_steps_reached"
    assert result.fallback_used is True
    assert result.steps_used == 3
    assert result.max_steps == 3
    assert result.next_actions == ["resume:student_learning_loop:practice"]
    assert result.loop_result.status == "PARTIAL"
    assert result.loop_result.result["resume_state"] == "practice"


def test_runtime_fails_fast_on_invalid_bounds() -> None:
    with pytest.raises(ValueError):
        LoopRuntime(LoopRuntimeConfig(default_max_steps=0))

    with pytest.raises(ValueError):
        LoopRuntime().execute(request("student_learning_loop", max_steps=0))
