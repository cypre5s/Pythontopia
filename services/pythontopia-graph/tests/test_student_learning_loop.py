from __future__ import annotations

from fastapi.testclient import TestClient

from pythontopia_graph.main import app
from pythontopia_graph.runtime.student_learning_loop import (
    CourseEvidence,
    LearnerKcState,
    LearnerProfileState,
    StudentLearningLoop,
    StudentLearningLoopRequest,
)
from pythontopia_graph.schemas.loop_contract import EventEnvelope, EvidenceRef, LoopResult


def evt(
    event_id: str,
    event_type: str,
    *,
    kc: str = "PY43-KC-016",
    evidence: bool = True,
    payload: dict[str, object] | None = None,
) -> EventEnvelope:
    return EventEnvelope(
        event_id=event_id,
        event_type=event_type,
        user_id=7,
        session_id="sess-phase3",
        trace_id="trace-phase3",
        timestamp="2026-06-30T22:30:00Z",
        context={"kc_codes": [kc], "chapter_id": 2},
        payload=payload or {"problem_id": 301, "verdict": "WA", "formal": True},
        evidence_refs=[
            EvidenceRef(type="course_page", id=118, label="列表基础页"),
            EvidenceRef(type="kc", id=kc),
        ]
        if evidence
        else [],
    )


def request(
    trigger: EventEnvelope,
    *,
    recent_events: list[EventEnvelope] | None = None,
    evidence: list[CourseEvidence] | None = None,
    mastery: float = 0.32,
) -> StudentLearningLoopRequest:
    return StudentLearningLoopRequest(
        event=trigger,
        recent_events=recent_events if recent_events is not None else [trigger],
        profile=LearnerProfileState(
            user_id=7,
            dimensions={
                "knowledge_base": "Python 初学者",
                "error_pattern": "索引和列表可变性反复出错",
            },
            confidence_by_dimension={"knowledge_base": 0.7, "error_pattern": 0.62},
        ),
        kc_states=[
            LearnerKcState(
                kc_code="PY43-KC-016",
                mastery=mastery,
                confidence=0.58,
                last_practiced_at="2026-06-29T21:00:00Z",
            )
        ],
        course_evidence=evidence
        if evidence is not None
        else [
            CourseEvidence(
                evidence_ref=EvidenceRef(type="course_page", id=118, label="列表基础页"),
                kc_codes=["PY43-KC-016"],
                summary="课件说明列表索引从 0 开始，append 会原地修改列表。",
            )
        ],
        policy_version="agentic_loop_v1",
    )


def test_repeated_failed_submission_runs_full_observe_to_update_loop() -> None:
    failed_once = evt("evt-fail-1", "submission.failed")
    failed_twice = evt("evt-fail-2", "submission.failed")
    page_read = evt(
        "evt-read-1",
        "page.read",
        payload={"page_id": 118, "seconds": 90},
    )

    result = StudentLearningLoop().run(
        request(
            failed_twice,
            recent_events=[failed_once, page_read, failed_twice],
        )
    )

    assert [step.state for step in result.steps] == [
        "observe",
        "diagnose",
        "teach",
        "practice",
        "reflect",
        "update",
    ]
    assert result.status == "SUCCESS"
    assert result.observation.focus_kcs == ["PY43-KC-016"]
    assert result.observation.repeated_failure_count_by_kc["PY43-KC-016"] == 2
    assert result.diagnosis.confidence >= 0.65
    assert "repeated_failure" in result.diagnosis.root_causes
    assert result.teaching_plan.intervention_type == "remediation"
    assert result.teaching_plan.evidence_refs
    assert result.practice_assignment.assignment_type == "remediation_practice"
    assert result.practice_assignment.difficulty in {"remedial", "basic"}
    assert "完整答案" not in result.practice_assignment.prompt
    assert result.reflection.evidence_event_ids == ["evt-fail-1", "evt-read-1", "evt-fail-2"]
    assert result.update_plan.kc_mastery_deltas["PY43-KC-016"] < 0
    assert result.update_plan.profile_dimension_patches[0].dimension_key == "error_pattern"
    assert isinstance(LoopResult.model_validate(result.loop_result.model_dump()), LoopResult)


def test_missing_evidence_degrades_without_fabricating_diagnosis() -> None:
    trigger = evt("evt-no-evidence", "submission.failed", evidence=False)

    result = StudentLearningLoop().run(
        request(trigger, recent_events=[trigger], evidence=[])
    )

    assert result.status == "REJECTED"
    assert result.stop_reason == "missing_evidence"
    assert result.diagnosis.confidence < 0.65
    assert result.diagnosis.gap_kcs == ["PY43-KC-016"]
    assert result.diagnosis.root_causes == ["insufficient_evidence"]
    assert result.teaching_plan.intervention_type == "evidence_review"
    assert result.teaching_plan.evidence_refs == []
    assert result.update_plan.kc_mastery_deltas == {}
    assert result.next_actions == ["request:learner-evidence"]
    assert result.loop_result.status == "REJECTED"


def test_three_failures_route_to_remediation_not_harder_practice() -> None:
    events = [
        evt("evt-fail-1", "submission.failed"),
        evt("evt-fail-2", "submission.failed"),
        evt("evt-fail-3", "submission.failed"),
    ]

    result = StudentLearningLoop().run(request(events[-1], recent_events=events))

    assert result.observation.repeated_failure_count_by_kc["PY43-KC-016"] == 3
    assert result.teaching_plan.intervention_type == "remediation"
    assert result.practice_assignment.assignment_type == "remediation_practice"
    assert result.practice_assignment.difficulty == "remedial"
    assert result.practice_assignment.requires_formal_judge is True
    assert "hard" not in result.practice_assignment.model_dump_json()


def test_pyodide_preview_pass_does_not_update_mastery_without_formal_judge() -> None:
    preview = evt(
        "evt-preview-pass",
        "pyodide.preview.passed",
        payload={"problem_id": 301, "verdict": "AC", "formal": False},
    )

    result = StudentLearningLoop().run(request(preview, recent_events=[preview]))

    assert result.status == "PARTIAL"
    assert result.stop_reason == "formal_judge_evidence_required"
    assert result.observation.pyodide_preview_events == ["evt-preview-pass"]
    assert result.observation.formal_submission_events == []
    assert result.practice_assignment.requires_formal_judge is True
    assert result.update_plan.kc_mastery_deltas == {}
    assert result.next_actions == ["submit:formal-judge"]


def test_formal_pass_closes_loop_with_transfer_and_positive_update() -> None:
    passed = evt(
        "evt-submit-pass",
        "submission.passed",
        payload={"problem_id": 301, "verdict": "AC", "formal": True},
    )

    result = StudentLearningLoop().run(request(passed, recent_events=[passed], mastery=0.72))

    assert result.status == "SUCCESS"
    assert result.stop_reason == "completed"
    assert result.diagnosis.root_causes == ["practice_success"]
    assert result.teaching_plan.intervention_type == "consolidation"
    assert result.practice_assignment.assignment_type == "transfer_practice"
    assert result.update_plan.kc_mastery_deltas["PY43-KC-016"] > 0
    assert result.update_plan.review_schedule[0].reason == "formal_pass"
    assert result.loop_result.next_actions == ["complete:student_learning_loop"]


def test_route_returns_api_visible_student_learning_loop_state(monkeypatch) -> None:
    monkeypatch.setenv("PYT_INTERNAL_AUTH_OPTIONAL", "1")
    client = TestClient(app)
    failed_once = evt("evt-route-1", "submission.failed")
    failed_twice = evt("evt-route-2", "submission.failed")

    response = client.post(
        "/runtime/student-learning-runs",
        json=request(
            failed_twice,
            recent_events=[failed_once, failed_twice],
        ).model_dump(mode="json"),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCESS"
    assert body["observation"]["focus_kcs"] == ["PY43-KC-016"]
    assert body["teaching_plan"]["intervention_type"] == "remediation"
    assert body["loop_result"]["trace_id"] == "trace-phase3"
