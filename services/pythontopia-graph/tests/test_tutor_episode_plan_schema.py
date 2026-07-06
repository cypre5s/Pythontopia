from __future__ import annotations

import pytest
from pydantic import ValidationError

from pythontopia_graph.schemas.tutor_workflow import (
    TutorEpisodePlan,
    TutorEpisodePlanPatch,
    TutorEpisodeStep,
    WorkflowRunRequest,
    WorkflowRunResult,
)


def episode_step(
    step_id: str,
    event: str,
    status: str = "PENDING",
    *,
    tool_name: str = "",
    student_visible_reason: str = "先看变量怎么跑偏",
) -> TutorEpisodeStep:
    return TutorEpisodeStep(
        step_id=step_id,
        event=event,  # type: ignore[arg-type]
        tool_name=tool_name or event.lower(),
        title=f"步骤 {step_id}",
        goal="定位问题",
        student_visible_reason=student_visible_reason,
        stop_condition="学生看完并点击继续",
        status=status,  # type: ignore[arg-type]
    )


def _plan(**overrides: object) -> TutorEpisodePlan:
    base: dict[str, object] = dict(
        plan_id="ep-1",
        session_id="s-1",
        problem_id=476,
        user_id=1,
        objective="Fix wrong answer",
        trigger_event="ERROR_FEEDBACK",
        current_step_id="s1",
        steps=[
            episode_step("s1", "EXECUTION_TRACE", "ACTIVE"),
            episode_step("s2", "KNOWLEDGE_REVIEW", "PENDING"),
        ],
    )
    base.update(overrides)
    return TutorEpisodePlan(**base)  # type: ignore[arg-type]


def test_episode_plan_accepts_two_to_four_steps() -> None:
    plan = _plan()
    assert 2 <= len(plan.steps) <= 4
    assert plan.current_step_id == "s1"
    assert plan.created_by == "coach"
    assert plan.stop_policy == "WAIT_FOR_CLICK"


def test_episode_plan_accepts_four_steps() -> None:
    plan = _plan(
        steps=[
            episode_step("s1", "EXECUTION_TRACE", "ACTIVE"),
            episode_step("s2", "KNOWLEDGE_REVIEW"),
            episode_step("s3", "SKELETON"),
            episode_step("s4", "CODING"),
        ]
    )
    assert len(plan.steps) == 4


def test_episode_plan_rejects_more_than_four_steps() -> None:
    with pytest.raises(ValidationError):
        _plan(
            steps=[
                episode_step("s1", "EXECUTION_TRACE", "ACTIVE"),
                episode_step("s2", "KNOWLEDGE_REVIEW"),
                episode_step("s3", "SKELETON"),
                episode_step("s4", "CODING"),
                episode_step("s5", "AC_REVIEW"),
            ]
        )


def test_episode_plan_rejects_fewer_than_two_steps() -> None:
    with pytest.raises(ValidationError):
        _plan(steps=[episode_step("s1", "EXECUTION_TRACE", "ACTIVE")])


def test_episode_plan_requires_current_step_in_steps() -> None:
    with pytest.raises(ValidationError):
        _plan(current_step_id="missing")


def test_episode_plan_rejects_duplicate_step_ids() -> None:
    with pytest.raises(ValidationError):
        _plan(
            steps=[
                episode_step("s1", "EXECUTION_TRACE", "ACTIVE"),
                episode_step("s1", "KNOWLEDGE_REVIEW"),
            ]
        )


def test_episode_plan_rejects_multiple_active_steps() -> None:
    with pytest.raises(ValidationError):
        _plan(
            steps=[
                episode_step("s1", "EXECUTION_TRACE", "ACTIVE"),
                episode_step("s2", "KNOWLEDGE_REVIEW", "ACTIVE"),
            ]
        )


def test_episode_step_rejects_invalid_event() -> None:
    with pytest.raises(ValidationError):
        episode_step("s1", "NOT_AN_EVENT", "ACTIVE")


def test_episode_plan_rejects_raw_reasoning_in_evidence_summary() -> None:
    with pytest.raises(ValidationError):
        _plan(evidence_summary="hidden reasoning: the model internally thinks ...")


def test_episode_plan_rejects_raw_reasoning_in_revision_reason() -> None:
    with pytest.raises(ValidationError):
        _plan(revision_reason="see system prompt for chain-of-thought trace")


def test_episode_step_rejects_raw_reasoning_in_student_visible_reason() -> None:
    with pytest.raises(ValidationError):
        _plan(
            steps=[
                episode_step(
                    "s1",
                    "EXECUTION_TRACE",
                    "ACTIVE",
                    student_visible_reason="chain-of-thought: internal step by step",
                ),
                episode_step("s2", "KNOWLEDGE_REVIEW"),
            ]
        )


def test_episode_plan_patch_constructs() -> None:
    patch = TutorEpisodePlanPatch(
        plan_id="ep-1",
        revision=1,
        current_step_id="s2",
        revised_step_ids=["s2"],
        revision_reason="连续错 3 次，降低认知负荷",
        stop_policy="WAIT_FOR_SUBMISSION",
    )
    assert patch.revision == 1
    assert patch.current_step_id == "s2"


def test_workflow_request_and_result_keep_episode_fields_optional() -> None:
    request = WorkflowRunRequest(
        session_id="wf-1",
        user_id=1,
        event="READING",
        problem={"problem_id": 42, "title": "循环求和"},
    )
    result = WorkflowRunResult(session_id="wf-1", event="READING", phase="READING")

    assert request.episode_plan is None
    assert result.episode_plan is None
    assert result.episode_patch is None
