from __future__ import annotations

from fastapi.testclient import TestClient

from pythontopia_graph.main import app
from pythontopia_graph.nodes.output_persistor import build_response
from pythontopia_graph.runtime.resource_lifecycle_loop import (
    ArtifactEffectivenessSnapshot,
    ResourceGoal,
    ResourceLifecycleLoop,
    ResourceLifecycleLoopRequest,
)
from pythontopia_graph.schemas.loop_contract import LoopResult
from pythontopia_graph.schemas.resource import (
    GraphTraceStep,
    GroundingResult,
    HandoutContent,
    PageRef,
    ResourceDraft,
    SafetyResult,
)


def draft(title: str = "input 函数讲义") -> ResourceDraft:
    return ResourceDraft(
        title=title,
        summary="围绕 input 函数的短讲义",
        content_format="MARKDOWN",
        handout=HandoutContent(
            sections=[
                {
                    "heading": "input 基础",
                    "paragraphs": ["input() 读取字符串，计算前通常要转换类型。"],
                    "page_refs": [PageRef(page_id=101, page_no=12, chapter_id=3)],
                }
            ]
        ),
    )


def graph_response(
    *,
    evidence: bool = True,
    grounding_passed: bool = True,
    safety_passed: bool = True,
):
    evidence_page_ids = [101] if evidence else []
    return build_response(
        artifact=draft(),
        resource_type="HANDOUT",
        evidence_page_ids=evidence_page_ids,
        grounding=GroundingResult(
            passed=grounding_passed,
            reasons=[] if grounding_passed else ["GROUNDING_MISMATCH"],
            no_evidence_found=not evidence,
        ),
        safety=SafetyResult(
            passed=safety_passed,
            reasons=[] if safety_passed else ["ANSWER_LEAK"],
        ),
        graph_trace=[
            GraphTraceStep(node="evidence_fetcher", elapsed_ms=8, status="ok"),
            GraphTraceStep(node="ResourceDesignerAgent.handout.attempt0", elapsed_ms=23, status="ok"),
            GraphTraceStep(node="grounding_critic", elapsed_ms=2, status="ok" if grounding_passed else "fail"),
            GraphTraceStep(node="safety_critic", elapsed_ms=2, status="ok" if safety_passed else "fail"),
        ],
        status="SUCCESS" if grounding_passed and safety_passed else "FAILED",
    )


def goal() -> ResourceGoal:
    return ResourceGoal(
        user_id=7,
        kc_codes=["PY43-KC-001"],
        resource_type="HANDOUT",
        focus_hint="讲清楚 input 的类型转换",
    )


def request(
    *,
    response=None,
    revision_of: str | None = None,
    evaluation: ArtifactEffectivenessSnapshot | None = None,
) -> ResourceLifecycleLoopRequest:
    return ResourceLifecycleLoopRequest(
        goal=goal(),
        graph_response=response or graph_response(),
        revision_of=revision_of,
        evaluation=evaluation,
        policy_version="agentic_loop_v1",
        trace_id="trace-phase5",
    )


def test_published_resource_runs_goal_to_evaluate_lifecycle() -> None:
    result = ResourceLifecycleLoop().run(
        request(
            evaluation=ArtifactEffectivenessSnapshot(
                resource_id=77,
                feedback_adoption_rate=0.8,
                micro_check_pass_rate=0.75,
                mastery_delta_sum=0.18,
            )
        )
    )

    assert [step.state for step in result.steps] == [
        "goal",
        "generate",
        "critique",
        "revise",
        "publish",
        "evaluate",
    ]
    assert result.status == "SUCCESS"
    assert result.normalized_goal.target_kcs == ["PY43-KC-001"]
    assert result.artifact_manifest.publish_state == "PUBLISHED"
    assert result.artifact_manifest.grounding_refs == [101]
    assert result.revision_plan.required is False
    assert result.publish_state.status == "PUBLISHED"
    assert result.evaluation_result.signal == "EFFECTIVE"
    assert result.loop_result.loop_type == "RESOURCE"
    assert isinstance(LoopResult.model_validate(result.loop_result.model_dump()), LoopResult)


def test_no_evidence_holds_publish_and_requests_grounding_revision() -> None:
    result = ResourceLifecycleLoop().run(
        request(response=graph_response(evidence=False))
    )

    assert result.status == "PARTIAL"
    assert result.stop_reason == "held_for_review"
    assert result.artifact_manifest.publish_state == "HOLD_FOR_REVIEW"
    assert result.critique_report.grounding.passed is False
    assert result.revision_plan.required is True
    assert "grounding" in result.revision_plan.reasons[0]
    assert result.next_actions == ["revise:add-grounding-evidence"]


def test_critic_failure_rejects_publish() -> None:
    result = ResourceLifecycleLoop().run(
        request(response=graph_response(grounding_passed=False, safety_passed=False))
    )

    assert result.status == "FAILED"
    assert result.stop_reason == "critic_rejected"
    assert result.publish_state.status == "REJECTED"
    assert result.critique_report.safety.passed is False
    assert result.revision_plan.required is True
    assert result.next_actions == ["revise:critic-failure"]


def test_revision_preserves_revision_parent_in_manifest() -> None:
    result = ResourceLifecycleLoop().run(request(revision_of="artifact-old"))

    assert result.status == "SUCCESS"
    assert result.artifact_manifest.revision_of == "artifact-old"
    assert result.revision_plan.revision_of == "artifact-old"
    assert result.publish_state.status == "PUBLISHED"


def test_negative_effectiveness_requests_revision_after_publish() -> None:
    result = ResourceLifecycleLoop().run(
        request(
            evaluation=ArtifactEffectivenessSnapshot(
                resource_id=77,
                feedback_adoption_rate=0.1,
                micro_check_pass_rate=0.2,
                mastery_delta_sum=-0.08,
            )
        )
    )

    assert result.status == "PARTIAL"
    assert result.stop_reason == "effectiveness_needs_revision"
    assert result.evaluation_result.signal == "NEEDS_REVISION"
    assert result.next_actions == ["revise:effectiveness"]


def test_route_returns_api_visible_resource_lifecycle_state(monkeypatch) -> None:
    monkeypatch.setenv("PYT_INTERNAL_AUTH_OPTIONAL", "1")
    client = TestClient(app)

    response = client.post(
        "/runtime/resource-lifecycle-runs",
        json=request().model_dump(mode="json"),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCESS"
    assert body["artifact_manifest"]["publish_state"] == "PUBLISHED"
    assert body["loop_result"]["trace_id"] == "trace-phase5"
