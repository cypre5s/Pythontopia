"""百晓工坊统一路由合同测试（Phase 1 RED/GREEN）。"""

from __future__ import annotations

from pythontopia_graph.routing.workshop_policy import compile_route_decision
from pythontopia_graph.schemas.intent import (
    IntentRouteRequest,
    WorkshopInteractionContextRef,
)


def test_courseware_qa_compiles_complete_single_agent_route() -> None:
    request = IntentRouteRequest(
        message="结合课件解释 for 循环",
        manual_kc_codes=["PY43-KC-012"],
        course_kcs=[{"code": "PY43-KC-012", "name": "for 循环"}],
    )

    decision = compile_route_decision(
        request,
        intent="ASK",
        confidence=0.91,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="answer",
    )

    assert decision.route_id.startswith("route_")
    assert decision.version == 1
    assert decision.task.family == "COURSEWARE_QA"
    assert decision.task.complexity == "C1"
    assert decision.task.confidence == 0.91
    assert decision.orchestration.topology == "SINGLE_AGENT_TOOL"
    assert decision.orchestration.primary_agent == "TutorAgent"
    assert decision.orchestration.supporting_agents == []
    assert decision.tools.required == ["ASK"]
    assert decision.tools.optional == []
    assert "FIX_BUG" in decision.tools.forbidden
    assert decision.policies.max_tool_calls == 8
    assert decision.policies.max_calls_per_tool == 2
    assert decision.policies.max_agent_calls == 4
    assert decision.policies.max_planning_depth == 3
    assert decision.policies.max_parallel_agents == 3
    assert decision.policies.max_replans == 1
    assert decision.policies.require_grounding is True
    assert decision.policies.require_safety is True
    assert decision.output.schema_name == "WorkshopAnswerCard"
    assert decision.output.require_evidence is True
    assert decision.fallback.on_no_evidence == "DISCLOSE_COMMON_KNOWLEDGE"


def test_course_catalog_alone_is_not_courseware_evidence_anchor() -> None:
    request = IntentRouteRequest(
        message="解释一下鸭子类型",
        course_kcs=[{"code": "PY43-KC-012", "name": "for 循环"}],
    )

    decision = compile_route_decision(
        request,
        intent="ASK",
        confidence=0.91,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="answer",
    )

    assert decision.task.family == "CONCEPT_QA"


def test_debug_route_uses_existing_debug_agent_when_context_is_complete() -> None:
    request = IntentRouteRequest(
        message="这次 WA 帮我定位根因",
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
        confidence=0.94,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="answer",
    )

    assert decision.task.family == "DEBUG"
    assert decision.task.needs_clarification is False
    assert decision.orchestration.primary_agent == "DebugAgent"
    assert decision.tools.required == ["FIX_BUG"]
    assert decision.output.schema_name == "DebugHintCard"


def test_debug_route_clarifies_instead_of_inventing_submission_context() -> None:
    request = IntentRouteRequest(message="代码报错了，帮我修一下")

    decision = compile_route_decision(
        request,
        intent="FIX_BUG",
        confidence=0.88,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="answer",
    )

    assert decision.task.family == "DEBUG"
    assert decision.task.needs_clarification is True
    assert "代码" in decision.task.clarification_question
    assert decision.orchestration.primary_agent is None
    assert decision.fallback.on_missing_context == "ASK_CLARIFICATION"


def test_route_id_is_deterministic_for_same_route_input() -> None:
    request = IntentRouteRequest(message="给我出两道列表题")
    kwargs = {
        "intent": "PRACTICE",
        "confidence": 0.87,
        "needs_clarification": False,
        "clarification_prompt": "",
        "render_mode": "practice",
    }

    first = compile_route_decision(request, **kwargs)
    second = compile_route_decision(request, **kwargs)

    assert first.route_id == second.route_id
    assert first.task.family == "PRACTICE"
    assert first.orchestration.primary_agent == "PracticeAgent"


def test_confidence_below_plan_threshold_forces_clarification() -> None:
    request = IntentRouteRequest(message="帮我弄一下")

    decision = compile_route_decision(
        request,
        intent="ASK",
        confidence=0.54,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="answer",
    )

    assert decision.task.needs_clarification is True
    assert decision.orchestration.topology == "DIRECT"
    assert decision.tools.required == []
    assert decision.output.schema_name == "WorkshopClarificationCard"


def test_summary_then_practice_compiles_sequential_multi_agent_route() -> None:
    request = IntentRouteRequest(
        message="总结循环课件，然后根据总结出三道题",
        manual_kc_codes=["PY43-KC-012"],
    )

    decision = compile_route_decision(
        request,
        intent="HANDOUT",
        secondary_intent="PRACTICE",
        confidence=0.93,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
    )

    assert decision.task.family == "MULTI_TASK"
    assert decision.task.sub_intents == ["HANDOUT", "PRACTICE"]
    assert decision.task.complexity == "C2"
    assert decision.orchestration.topology == "SEQUENTIAL"
    assert decision.orchestration.primary_agent == "HandoutAgent"
    assert decision.orchestration.supporting_agents == ["PracticeAgent"]
    assert decision.tools.required == ["HANDOUT", "PRACTICE"]
    assert decision.output.schema_name == "CompoundLearningSupportCard"


def test_mindmap_and_microtest_compile_parallel_multi_agent_route() -> None:
    request = IntentRouteRequest(
        message="画循环知识图谱，同时给我一组微测",
        manual_kc_codes=["PY43-KC-012"],
    )

    decision = compile_route_decision(
        request,
        intent="MINDMAP",
        secondary_intent="PRACTICE",
        confidence=0.91,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
    )

    assert decision.task.sub_intents == ["MINDMAP", "PRACTICE"]
    assert decision.orchestration.topology == "PARALLEL"
    assert decision.orchestration.supporting_agents == ["PracticeAgent"]


def test_resource_plus_path_compiles_bounded_planner_executor_route() -> None:
    request = IntentRouteRequest(
        message="生成循环复习资料，再安排学习路径",
        manual_kc_codes=["PY43-KC-012"],
    )

    decision = compile_route_decision(
        request,
        intent="HANDOUT",
        secondary_intent="PLAN",
        confidence=0.9,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
    )

    assert decision.orchestration.topology == "PLANNER_EXECUTOR"
    assert decision.task.complexity == "C2"
    assert decision.policies.max_replans == 1
