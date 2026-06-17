from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pytest

from pythontopia_graph.agents.guide_coach_agent import CoachContext
from pythontopia_graph.agents.guide_episode_planner import (
    EpisodeRevisionEvidence,
    GuideEpisodePlanner,
)
from pythontopia_graph.schemas.tutor_workflow import (
    GuideCoachDecision,
    TutorStudentState,
    WorkflowDiagnosisContext,
    WorkflowEvent,
    WorkflowProblemContext,
)

_FORBIDDEN_STEP_LABELS = ("COMPLETE_SOLUTION", "FULL_SOLUTION", "FULL_ANSWER")


def _scenarios() -> list[dict[str, Any]]:
    path = Path(__file__).parent / "fixtures" / "guide_episode_planner_golden.json"
    return json.loads(path.read_text(encoding="utf-8"))


def _ctx(scenario: dict[str, Any]) -> CoachContext:
    verdict = scenario["verdict"]
    diagnosis = None
    if verdict and verdict != "AC":
        diagnosis = WorkflowDiagnosisContext(
            submission_id=7,
            verdict=verdict,
            code="n=int(input())\nprint(n)",
            consecutive_errors=scenario["consecutive_errors"],
        )
    elif verdict == "AC":
        diagnosis = WorkflowDiagnosisContext(submission_id=7, verdict="AC")
    return CoachContext(
        requested_event=scenario["decision_event"],
        allowed_events=scenario["allowed_events"],
        state=TutorStudentState(
            session_id=f"wf-{scenario['name']}",
            user_id=1,
            problem_id=42,
            phase="CODING",
            latest_verdict=verdict,
            consecutive_errors=scenario["consecutive_errors"],
        ),
        problem=WorkflowProblemContext(
            problem_id=42, title="循环求和", kc_codes=["PY43-KC-016"]
        ),
        diagnosis=diagnosis,
    )


def _decision(scenario: dict[str, Any]) -> GuideCoachDecision:
    event: WorkflowEvent = scenario["decision_event"]
    return GuideCoachDecision(
        next_event=event,
        tool_name=event.lower(),
        action_goal="按当前证据推进下一步导学。",
        stop_condition="学生完成该步检查点。",
        reason_summary="基于判题证据选择下一步教学动作。",
    )


@pytest.mark.parametrize("scenario", _scenarios(), ids=lambda s: s["name"])
def test_episode_plan_golden(scenario: dict[str, Any]) -> None:
    planner = GuideEpisodePlanner()
    plan = planner.plan(_ctx(scenario), _decision(scenario))

    events = [step.event for step in plan.steps]

    assert 2 <= len(plan.steps) <= 4
    assert plan.steps[0].event == scenario["decision_event"]
    assert plan.steps[0].status == "ACTIVE"
    assert sum(1 for s in plan.steps if s.status == "ACTIVE") == 1

    allowed = set(scenario["allowed_events"])
    for step in plan.steps:
        assert step.event in allowed
        assert step.goal.strip()
        assert step.stop_condition.strip()
        assert step.student_visible_reason.strip()
        assert step.event not in _FORBIDDEN_STEP_LABELS
        assert all(label not in step.tool_name.upper() for label in _FORBIDDEN_STEP_LABELS)

    for forbidden in scenario.get("forbidden_events", []):
        assert forbidden not in events

    if "expected_first" in scenario:
        assert events[0] == scenario["expected_first"]
    if "expected_first_in" in scenario:
        assert events[0] in scenario["expected_first_in"]
    if "expected_second_in" in scenario:
        assert events[1] in scenario["expected_second_in"]
    for required in scenario.get("expected_contains", []):
        assert required in events
    if "expected_tail_in" in scenario:
        assert any(event in scenario["expected_tail_in"] for event in events[1:])


def test_plan_is_schema_valid_and_visible_reason_clean() -> None:
    planner = GuideEpisodePlanner()
    scenario = next(s for s in _scenarios() if s["name"] == "WA_first_attempt")
    plan = planner.plan(_ctx(scenario), _decision(scenario))
    assert plan.objective
    assert plan.created_by == "coach"
    for step in plan.steps:
        assert "chain-of-thought" not in step.student_visible_reason.lower()
        assert "system prompt" not in step.student_visible_reason.lower()


def test_plan_dependencies_chain_in_order() -> None:
    planner = GuideEpisodePlanner()
    scenario = next(s for s in _scenarios() if s["name"] == "AC_after_retries")
    plan = planner.plan(_ctx(scenario), _decision(scenario))
    assert plan.steps[0].depends_on == []
    for prev, step in zip(plan.steps, plan.steps[1:]):
        assert prev.step_id in step.depends_on


def test_revise_bumps_revision_and_keeps_one_active() -> None:
    planner = GuideEpisodePlanner()
    scenario = next(s for s in _scenarios() if s["name"] == "WA_first_attempt")
    ctx = _ctx(scenario)
    plan = planner.plan(ctx, _decision(scenario))

    repeated_ctx = ctx.model_copy(
        update={
            "requested_event": "VISUALIZE",
            "state": ctx.state.model_copy(update={"consecutive_errors": 3}),
        }
    )
    revise_decision = GuideCoachDecision(
        next_event="VISUALIZE",
        tool_name="visualize_flow",
        action_goal="连续失败，降低认知负担。",
        stop_condition="学生完成一个小检查点。",
        reason_summary="连续失败次数较多，先降低负担。",
    )
    evidence = EpisodeRevisionEvidence(
        latest_verdict="WA", consecutive_errors=3, reason="连续错 3 次，降低认知负荷"
    )
    revised = planner.revise(repeated_ctx, plan, revise_decision, evidence)

    assert revised.revision == plan.revision + 1
    assert revised.plan_id == plan.plan_id
    assert revised.revision_reason
    assert sum(1 for s in revised.steps if s.status == "ACTIVE") == 1
    active = next(s for s in revised.steps if s.status == "ACTIVE")
    assert active.event == "VISUALIZE"
    assert 2 <= len(revised.steps) <= 4
    for step in revised.steps:
        assert step.event in set(repeated_ctx.allowed_events)


def test_fallback_two_step_plan_for_uncovered_event() -> None:
    planner = GuideEpisodePlanner()
    ctx = CoachContext(
        requested_event="CHAT",
        allowed_events=["CHAT", "CODING"],
        state=TutorStudentState(
            session_id="wf-fallback", user_id=1, problem_id=42, phase="CODING"
        ),
        problem=WorkflowProblemContext(problem_id=42, title="循环求和"),
    )
    decision = GuideCoachDecision(
        next_event="CHAT",
        tool_name="tutor_chat",
        action_goal="回答学生追问。",
        stop_condition="学生确认理解。",
        reason_summary="学生主动追问。",
    )
    plan = planner.plan(ctx, decision)
    assert len(plan.steps) == 2
    assert plan.steps[0].event == "CHAT"
    assert plan.steps[1].event == "CODING"
