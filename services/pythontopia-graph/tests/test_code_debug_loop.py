from __future__ import annotations

from fastapi.testclient import TestClient

from pythontopia_graph.main import app
from pythontopia_graph.runtime.code_debug_loop import (
    CodeDebugLoop,
    CodeDebugLoopRequest,
    DebugCourseEvidence,
    DebugSubmissionSnapshot,
    ResubmissionSnapshot,
)
from pythontopia_graph.schemas.debug import (
    CounterExampleCard,
    TraceStep,
    VariableTrace,
)
from pythontopia_graph.schemas.loop_contract import EvidenceRef, LoopResult


def counter_example() -> CounterExampleCard:
    return CounterExampleCard(
        stdin="5\n",
        expected_stdout="10\n",
        your_stdout="5\n",
        diff_summary="第 1 行：期望 '10'，实际 '5'",
        redacted=False,
    )


def failure(
    *,
    verdict: str = "WA",
    evidence: bool = True,
    code: str = "x = input()\nprint(x)\n",
) -> DebugSubmissionSnapshot:
    return DebugSubmissionSnapshot(
        submission_id=101,
        problem_id=476,
        user_id=7,
        kc_codes=["PY43-KC-001"],
        verdict=verdict,
        code=code,
        counter_example=counter_example(),
        variable_trace=VariableTrace(
            steps=[TraceStep(line=2, locals={"x": "5"})],
            truncated=False,
        ),
        stderr="IndexError: list index out of range" if verdict == "RE" else "",
        evidence_refs=[
            EvidenceRef(type="course_page", id=12, label="input 与输出"),
            EvidenceRef(type="kc", id="PY43-KC-001"),
        ]
        if evidence
        else [],
    )


def passed_resubmission() -> ResubmissionSnapshot:
    return ResubmissionSnapshot(
        submission_id=102,
        verdict="AC",
        code="x = int(input())\nprint(x * 2)\n",
        passed_cases=3,
        total_cases=3,
    )


def request(
    *,
    failed: DebugSubmissionSnapshot | None = None,
    resubmission: ResubmissionSnapshot | None = None,
    prior_failure_count: int = 0,
    evidence: list[DebugCourseEvidence] | None = None,
) -> CodeDebugLoopRequest:
    return CodeDebugLoopRequest(
        failure=failed or failure(),
        resubmission=resubmission,
        prior_failure_count=prior_failure_count,
        course_evidence=evidence
        if evidence is not None
        else [
            DebugCourseEvidence(
                evidence_ref=EvidenceRef(type="course_page", id=12, label="input 与输出"),
                kc_codes=["PY43-KC-001"],
                summary="课件强调 input() 读到的是字符串，计算前要按题意转换。",
            )
        ],
        policy_version="agentic_loop_v1",
        trace_id="trace-phase4",
    )


def test_resolved_wa_runs_full_fail_to_transfer_loop() -> None:
    result = CodeDebugLoop().run(request(resubmission=passed_resubmission()))

    assert [step.state for step in result.steps] == [
        "fail",
        "hypothesize",
        "hint",
        "resubmit",
        "compare",
        "transfer",
    ]
    assert result.status == "SUCCESS"
    assert result.failure_observation.verdict == "WA"
    assert len(result.hypotheses) == 3
    assert {item.angle for item in result.hypotheses} == {
        "runtime_behavior",
        "code_structure",
        "kc_prerequisite",
    }
    assert result.hypotheses[0].confidence >= result.hypotheses[1].confidence
    assert result.hint.disclosure_policy == "minimal_hint"
    assert "完整答案" not in result.hint.message
    assert result.resubmission_comparison.resolved is True
    assert result.resubmission_comparison.verdict_transition == "WA->AC"
    assert result.transfer_task.requires_formal_judge is True
    assert result.memory_writeback.writeback_recommended is True
    assert result.loop_result.loop_type == "DEBUG"
    assert isinstance(LoopResult.model_validate(result.loop_result.model_dump()), LoopResult)


def test_missing_resubmission_returns_partial_with_formal_judge_next_action() -> None:
    result = CodeDebugLoop().run(request())

    assert result.status == "PARTIAL"
    assert result.stop_reason == "awaiting_resubmission"
    assert [step.state for step in result.steps] == ["fail", "hypothesize", "hint"]
    assert result.resubmission_comparison.current_submission_id is None
    assert result.next_actions == ["resubmit:formal-judge"]
    assert result.loop_result.status == "PARTIAL"


def test_runtime_error_prioritizes_runtime_behavior_hypothesis() -> None:
    result = CodeDebugLoop().run(request(failed=failure(verdict="RE")))

    assert result.failure_observation.failure_kind == "runtime_error"
    assert result.hypotheses[0].root_cause == "runtime_exception"
    assert "IndexError" in result.hypotheses[0].evidence_summary
    assert result.hint.level == 1


def test_tle_prioritizes_loop_or_complexity_hypothesis() -> None:
    result = CodeDebugLoop().run(
        request(failed=failure(verdict="TLE", code="while True:\n    pass\n"))
    )

    assert result.failure_observation.failure_kind == "timeout"
    assert result.hypotheses[0].root_cause == "loop_or_complexity"
    assert result.transfer_task.target_kcs == ["PY43-KC-001"]


def test_missing_evidence_rejects_without_memory_writeback() -> None:
    result = CodeDebugLoop().run(
        request(failed=failure(evidence=False), evidence=[])
    )

    assert result.status == "REJECTED"
    assert result.stop_reason == "missing_debug_evidence"
    assert result.hypotheses[0].confidence < 0.65
    assert result.hypotheses[0].root_cause == "insufficient_evidence"
    assert result.hint.disclosure_policy == "evidence_only"
    assert result.memory_writeback.writeback_recommended is False
    assert result.next_actions == ["request:debug-evidence"]


def test_three_failed_attempts_fallback_to_prerequisite_remediation() -> None:
    result = CodeDebugLoop().run(request(prior_failure_count=2))

    assert result.status == "PARTIAL"
    assert result.stop_reason == "max_debug_attempts_reached"
    assert result.failure_observation.attempt_count == 3
    assert result.hint.level == 1
    assert result.transfer_task.assignment_type == "prerequisite_remediation"
    assert result.next_actions == ["fallback:prerequisite-remediation"]


def test_route_returns_api_visible_code_debug_loop_state(monkeypatch) -> None:
    monkeypatch.setenv("PYT_INTERNAL_AUTH_OPTIONAL", "1")
    client = TestClient(app)

    response = client.post(
        "/runtime/code-debug-runs",
        json=request(resubmission=passed_resubmission()).model_dump(mode="json"),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCESS"
    assert len(body["hypotheses"]) == 3
    assert body["resubmission_comparison"]["resolved"] is True
    assert body["loop_result"]["trace_id"] == "trace-phase4"
