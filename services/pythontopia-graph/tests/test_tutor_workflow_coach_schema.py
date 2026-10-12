from __future__ import annotations

import base64

import pytest
from pydantic import ValidationError

from pythontopia_graph.schemas.tutor_workflow import (
    GuideCoachDecision,
    TutorStudentState,
    WorkflowRunRequest,
    WorkflowRunResult,
)


def test_student_state_defaults_are_safe() -> None:
    state = TutorStudentState(session_id="wf-1", user_id=1, problem_id=42, phase="READING")

    assert state.hint_level == 0
    assert state.frustration_level == "low"
    assert state.clicked_actions == []
    assert state.completed_checkpoints == []


def test_student_state_bounds_hint_level() -> None:
    with pytest.raises(ValidationError):
        TutorStudentState(
            session_id="wf-1",
            user_id=1,
            problem_id=42,
            phase="READING",
            hint_level=4,
        )


def test_coach_decision_requires_bounded_hint_level() -> None:
    decision = GuideCoachDecision(
        next_event="EXECUTION_TRACE",
        tool_name="execution_trace",
        hint_level=2,
        action_goal="定位变量更新偏差",
        stop_condition="学生说出第一处偏差",
        allowed_followups=[
            {"key": "execution_trace", "label": "执行轨迹讲解", "event": "EXECUTION_TRACE"}
        ],
        confidence=0.8,
        reason_summary="WA 且部分样例通过，先观察执行过程。",
    )

    assert decision.next_event == "EXECUTION_TRACE"
    assert decision.allowed_followups[0].key == "execution_trace"


def test_workflow_request_and_result_keep_coach_fields_optional() -> None:
    request = WorkflowRunRequest(
        session_id="wf-1",
        user_id=1,
        event="READING",
        problem={"problem_id": 42, "title": "循环求和"},
    )
    result = WorkflowRunResult(
        session_id="wf-1",
        event="READING",
        phase="READING",
        student_state=request.student_state,
    )

    assert request.coach_mode is True
    assert request.force_event is None
    assert result.agentic is True
    assert result.coach_decision is None


def test_workflow_chat_request_accepts_valid_images() -> None:
    image = "data:image/png;base64," + base64.b64encode(b"\x89PNG\r\n\x1a\nimage").decode()
    request = WorkflowRunRequest(
        session_id="wf-1",
        user_id=1,
        event="CHAT",
        problem={"problem_id": 42},
        event_data={"message": "请看图", "images": [image]},
    )

    assert request.event_data["images"] == [image]


def test_workflow_chat_request_rejects_invalid_image_data() -> None:
    with pytest.raises(ValidationError, match="valid base64"):
        WorkflowRunRequest(
            session_id="wf-1",
            user_id=1,
            event="CHAT",
            problem={"problem_id": 42},
            event_data={"message": "请看图", "images": ["data:image/png;base64,not-base64!"]},
        )
