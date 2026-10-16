from __future__ import annotations

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


def test_workflow_chat_request_accepts_private_attachment_ids() -> None:
    attachment_id = "d0162eb8-8c60-4d85-a3da-3aa3f11460ba"
    request = WorkflowRunRequest(
        session_id="wf-1",
        user_id=1,
        event="CHAT",
        problem={"problem_id": 42},
        event_data={"message": "请看图", "attachment_ids": [attachment_id]},
    )

    assert request.event_data["attachment_ids"] == [attachment_id]


def test_workflow_chat_request_accepts_bounded_editor_code() -> None:
    request = WorkflowRunRequest(
        session_id="wf-1",
        user_id=1,
        event="CHAT",
        problem={"problem_id": 42},
        event_data={"message": "解释这段代码", "code": "print(42)"},
    )

    assert request.typed_event_data is not None
    assert request.typed_event_data.code == "print(42)"

    with pytest.raises(ValidationError, match="at most 20000 characters"):
        WorkflowRunRequest(
            session_id="wf-1",
            user_id=1,
            event="CHAT",
            problem={"problem_id": 42},
            event_data={"message": "解释代码", "code": "x" * 20_001},
        )


def test_workflow_chat_request_rejects_inline_image_bytes() -> None:
    with pytest.raises(ValidationError, match="private attachment transport"):
        WorkflowRunRequest(
            session_id="wf-1",
            user_id=1,
            event="CHAT",
            problem={"problem_id": 42},
            event_data={"message": "请看图", "images": ["data:image/png;base64,not-base64!"]},
        )


def test_workflow_chat_rejects_embedded_image_data_and_oversized_messages() -> None:
    with pytest.raises(ValidationError, match="image data URI"):
        WorkflowRunRequest(
            session_id="wf-1",
            user_id=1,
            event="CHAT",
            problem={"problem_id": 42},
            event_data={"message": "看图 data:image/png;base64,aGVsbG8="},
        )
    with pytest.raises(ValidationError, match="message must be at most"):
        WorkflowRunRequest(
            session_id="wf-1",
            user_id=1,
            event="CHAT",
            problem={"problem_id": 42},
            event_data={"message": "x" * 2001},
        )


def test_workflow_chat_rejects_unrecognized_image_fields() -> None:
    with pytest.raises(ValidationError, match="private attachment transport"):
        WorkflowRunRequest(
            session_id="wf-1",
            user_id=1,
            event="CHAT",
            problem={"problem_id": 42},
            event_data={
                "message": "请看图",
                "hidden_image": {"data": "data:image/png;base64,aGVsbG8="},
            },
        )


def test_workflow_rejects_images_outside_chat_event() -> None:
    attachment_id = "d0162eb8-8c60-4d85-a3da-3aa3f11460ba"
    with pytest.raises(ValidationError, match="attachment_ids are only supported for CHAT"):
        WorkflowRunRequest(
            session_id="wf-1",
            user_id=1,
            event="IDEATING",
            problem={"problem_id": 42},
            event_data={"attachment_ids": [attachment_id]},
        )


@pytest.mark.parametrize(
    "event_data",
    [
        {"image": "encoded payload"},
        {"image_url": "https://example.invalid/image.png"},
        {"nested": {"screenshotBase64": "encoded payload"}},
        {"attachment_bytes": "encoded payload"},
    ],
)
def test_workflow_rejects_inline_image_payload_aliases_outside_private_attachments(
    event_data: dict[str, object],
) -> None:
    with pytest.raises(ValidationError, match="private attachment transport"):
        WorkflowRunRequest(
            session_id="wf-1",
            user_id=1,
            event="IDEATING",
            problem={"problem_id": 42},
            event_data=event_data,
        )
