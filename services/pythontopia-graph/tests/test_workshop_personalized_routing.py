"""Phase 3: authoritative learner context must change routing deterministically."""

from __future__ import annotations

from pythontopia_graph.routing.workshop_policy import compile_route_decision
from pythontopia_graph.schemas.intent import (
    IntentRouteRequest,
    LearnerRecentResource,
    LearnerRecentSubmission,
    LearnerRoutingContext,
)


def _request(context: LearnerRoutingContext | None = None) -> IntentRouteRequest:
    return IntentRouteRequest(
        message="生成一份循环讲义和思维导图",
        learner_context=context,
    )


def _decision(request: IntentRouteRequest):  # type: ignore[no-untyped-def]
    return compile_route_decision(
        request,
        intent="HANDOUT",
        secondary_intent="MINDMAP",
        confidence=0.94,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
        resolved_kc_codes=["PY43-KC-031"],
    )


def test_high_load_profile_serializes_parallel_work_without_expanding_authority() -> None:
    standard = _decision(_request())
    overloaded = _decision(
        _request(
            LearnerRoutingContext(
                kc_mastery={"PY43-KC-031": 0.28},
                kc_confidence={"PY43-KC-031": 0.8},
                weak_kcs=["PY43-KC-031"],
                recent_error_kcs=["PY43-KC-031"],
                cognitive_load="HIGH",
                frustration_level="HIGH",
                cognitive_style="visual",
                learning_preferences=["偏好图解"],
                recent_resources=[
                    LearnerRecentResource(
                        kc_code="PY43-KC-031",
                        type="HANDOUT",
                        created_at="2026-07-22T15:00:00Z",
                    )
                ],
                recent_submissions=[
                    LearnerRecentSubmission(
                        problem_id=41,
                        kc_code="PY43-KC-031",
                        passed=False,
                    )
                ],
                struggle_patterns=["for 循环边界连续失败"],
                intervention_hints=["短步骤+鼓励反馈"],
            )
        )
    )

    assert standard.orchestration.topology == "PARALLEL"
    assert overloaded.orchestration.topology == "SEQUENTIAL"
    assert overloaded.policies.max_parallel_agents == 1
    assert overloaded.tools.required == standard.tools.required
    assert overloaded.orchestration.primary_agent == standard.orchestration.primary_agent
    assert overloaded.personalization.strategy == "SCAFFOLD_FIRST"
    assert "cognitive_load:HIGH" in overloaded.personalization.signals_used
    assert "frustration:HIGH" in overloaded.personalization.signals_used
    assert overloaded.personalization.writeback_mode == "DOMAIN_EVENTS_ONLY"
    assert overloaded.route_id != standard.route_id


def test_visual_preference_is_explainable_without_changing_explicit_tool() -> None:
    decision = _decision(
        _request(
            LearnerRoutingContext(
                cognitive_style="visual",
                learning_preferences=["图解", "结构化小标题"],
            )
        )
    )

    assert decision.personalization.strategy == "VISUAL_FIRST"
    assert decision.tools.required == ["HANDOUT", "MINDMAP"]
    assert decision.personalization.explanations
    assert any("图" in item for item in decision.personalization.explanations)


def test_context_timestamp_does_not_destabilize_route_id() -> None:
    first = _decision(
        _request(
            LearnerRoutingContext(
                cognitive_style="logical",
                learning_preferences=["逻辑链"],
                as_of="2026-07-22T15:00:00Z",
            )
        )
    )
    second = _decision(
        _request(
            LearnerRoutingContext(
                cognitive_style="logical",
                learning_preferences=["逻辑链"],
                as_of="2026-07-22T15:05:00Z",
            )
        )
    )

    assert first.route_id == second.route_id
