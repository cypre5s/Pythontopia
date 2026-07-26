"""Phase 2 unified TaskPlan and bounded DAG execution contract."""

from __future__ import annotations

import pytest
from pydantic import ValidationError

from pythontopia_graph.routing.workshop_policy import compile_route_decision
from pythontopia_graph.routing.workshop_task_plan import (
    TaskPlanPolicyError,
    compile_task_plan,
    validate_task_plan,
)
from pythontopia_graph.schemas.intent import IntentRouteRequest, WorkshopInteractionContextRef
from pythontopia_graph.schemas.orchestrator import TaskPlan


def _decision(primary: str, secondary: str):
    return compile_route_decision(
        IntentRouteRequest(
            message=f"{primary} then {secondary}",
            manual_kc_codes=["PY43-KC-012"],
        ),
        intent=primary,
        secondary_intent=secondary,
        confidence=0.94,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
    )


def test_summary_practice_task_plan_declares_artifact_dependency() -> None:
    decision = _decision("HANDOUT", "PRACTICE")

    plan = compile_task_plan(
        decision,
        message="总结循环课件，然后根据总结出题",
        kc_codes=["PY43-KC-012"],
    )

    assert plan.route_id == decision.route_id
    assert plan.topology == "SEQUENTIAL"
    assert [step.tool_name for step in plan.steps] == ["HANDOUT", "PRACTICE"]
    assert plan.steps[0].depends_on == []
    assert plan.steps[1].depends_on == [plan.steps[0].id]
    assert plan.steps[1].input_refs == [f"{plan.steps[0].id}.artifact"]
    assert all(step.idempotency_key for step in plan.steps)


def test_mindmap_microtest_task_plan_has_parallel_ready_group() -> None:
    plan = compile_task_plan(
        _decision("MINDMAP", "PRACTICE"),
        message="知识图谱和微测",
        kc_codes=["PY43-KC-012"],
    )

    assert plan.topology == "PARALLEL"
    assert all(step.depends_on == [] for step in plan.steps)
    assert len({step.parallel_group for step in plan.steps}) == 1


def test_resource_path_task_plan_is_planner_executor_with_dependency() -> None:
    plan = compile_task_plan(
        _decision("HANDOUT", "PLAN"),
        message="生成资料并安排路径",
        kc_codes=["PY43-KC-012"],
    )

    assert plan.topology == "PLANNER_EXECUTOR"
    assert plan.steps[0].agent == "PathPlannerAgent"
    assert plan.steps[0].tool_name == "PLAN"
    assert plan.steps[1].tool_name == "HANDOUT"
    assert plan.steps[1].depends_on == [plan.steps[0].id]
    assert all(step.on_failure == "REPLAN_ONCE" for step in plan.steps)
    assert plan.max_replans == 1


def test_verified_diagnosis_then_remediation_compiles_debug_dependency() -> None:
    request = IntentRouteRequest(
        message="诊断这次 WA，然后生成补救练习",
        manual_kc_codes=["PY43-KC-012"],
        interaction=WorkshopInteractionContextRef(
            current_problem_id=41,
            current_code="for i in range(n + 1):\n    total += i",
            latest_submission_id=901,
            error_text="WA: expected 10, got 15",
        ),
    )
    decision = compile_route_decision(
        request,
        intent="FIX_BUG",
        secondary_intent="PRACTICE",
        confidence=0.95,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="answer",
    )

    plan = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-012"],
    )

    assert [step.agent for step in plan.steps] == ["DebugAgent", "PracticeAgent"]
    assert plan.steps[1].depends_on == [plan.steps[0].id]


def test_task_plan_rejects_cycle_and_duplicate_step_ids() -> None:
    payload = compile_task_plan(
        _decision("HANDOUT", "PRACTICE"),
        message="总结再练习",
        kc_codes=["PY43-KC-012"],
    ).model_dump()
    payload["steps"][0]["depends_on"] = [payload["steps"][1]["id"]]
    cyclic = TaskPlan.model_validate(payload)
    with pytest.raises(TaskPlanPolicyError, match="cycle"):
        validate_task_plan(cyclic, _decision("HANDOUT", "PRACTICE"))

    payload["steps"][1]["id"] = payload["steps"][0]["id"]
    with pytest.raises(ValidationError):
        TaskPlan.model_validate(payload)


def test_task_plan_rejects_agent_tool_permission_expansion() -> None:
    decision = _decision("HANDOUT", "PRACTICE")
    payload = compile_task_plan(
        decision,
        message="总结再练习",
        kc_codes=["PY43-KC-012"],
    ).model_dump()
    payload["steps"][1]["agent"] = "DebugAgent"
    plan = TaskPlan.model_validate(payload)

    with pytest.raises(TaskPlanPolicyError, match="not allowed"):
        validate_task_plan(plan, decision)
