"""Phase 4 C3 有界搜索合同测试。"""

from __future__ import annotations

from statistics import median
from time import perf_counter

import pytest
from pydantic import ValidationError

from pythontopia_graph.routing.workshop_policy import compile_route_decision
from pythontopia_graph.schemas.intent import (
    IntentRouteRequest,
    LearnerRecentResource,
    LearnerRecentSubmission,
    LearnerRoutingContext,
)
from pythontopia_graph.schemas.workshop_route import (
    RouteDecision,
    RouteOrchestration,
    RouteOutput,
    RouteSearch,
    RouteSearchCandidate,
    RouteTask,
    RouteTools,
)


@pytest.mark.parametrize(
    ("intent_request", "route_kwargs", "complexity"),
    [
        (
            IntentRouteRequest(message="帮我弄一下"),
            {
                "intent": "ASK",
                "confidence": 0.54,
                "needs_clarification": False,
                "clarification_prompt": "",
                "render_mode": "answer",
            },
            "C0",
        ),
        (
            IntentRouteRequest(message="解释 Python 列表"),
            {
                "intent": "ASK",
                "confidence": 0.91,
                "needs_clarification": False,
                "clarification_prompt": "",
                "render_mode": "answer",
            },
            "C1",
        ),
        (
            IntentRouteRequest(
                message="总结循环课件，然后根据总结出三道题",
                manual_kc_codes=["PY43-KC-012"],
            ),
            {
                "intent": "HANDOUT",
                "secondary_intent": "PRACTICE",
                "confidence": 0.93,
                "needs_clarification": False,
                "clarification_prompt": "",
                "render_mode": "resource",
            },
            "C2",
        ),
    ],
)
def test_c0_to_c2_routes_do_not_carry_search(
    intent_request: IntentRouteRequest,
    route_kwargs: dict[str, object],
    complexity: str,
) -> None:
    decision = compile_route_decision(
        intent_request,
        **route_kwargs,  # type: ignore[arg-type]
    )

    assert decision.task.complexity == complexity
    assert decision.search is None


def _candidate(
    candidate_id: str,
    label: str,
    *,
    agent_path: list[str],
    tool_path: list[str],
    depth: int,
    g_cost: float,
    h_cost: float,
    penalty: float,
) -> RouteSearchCandidate:
    return RouteSearchCandidate(
        candidate_id=candidate_id,
        label=label,
        agent_path=agent_path,
        tool_path=tool_path,
        depth=depth,
        g_cost=g_cost,
        h_cost=h_cost,
        penalty=penalty,
        total_cost=g_cost + h_cost + penalty,
    )


def _three_candidates() -> list[RouteSearchCandidate]:
    return [
        _candidate(
            "candidate_diagnose_first",
            "诊断优先",
            agent_path=["ProfileAgent", "DebugAgent", "PathPlannerAgent"],
            tool_path=["PROGRESS", "FIX_BUG", "PLAN"],
            depth=3,
            g_cost=3.0,
            h_cost=1.5,
            penalty=0.5,
        ),
        _candidate(
            "candidate_rebuild_first",
            "知识重构优先",
            agent_path=["TutorAgent", "MindMapAgent", "PathPlannerAgent"],
            tool_path=["ASK", "MINDMAP", "PLAN"],
            depth=3,
            g_cost=2.5,
            h_cost=1.0,
            penalty=0.25,
        ),
        _candidate(
            "candidate_probe_first",
            "练习探测优先",
            agent_path=["PracticeAgent", "DebugAgent", "PathPlannerAgent"],
            tool_path=["PRACTICE", "FIX_BUG", "PLAN"],
            depth=3,
            g_cost=2.0,
            h_cost=2.0,
            penalty=0.75,
        ),
    ]


def _c3_decision(candidates: list[RouteSearchCandidate]) -> RouteDecision:
    return RouteDecision(
        route_id="route_0123456789abcdef",
        task=RouteTask(
            family="PATH",
            subtype="PLAN",
            sub_intents=["PLAN"],
            complexity="C3",
            confidence=0.92,
        ),
        orchestration=RouteOrchestration(
            topology="PLANNER_EXECUTOR",
            primary_agent="PathPlannerAgent",
            supporting_agents=["ProfileAgent", "DebugAgent"],
        ),
        tools=RouteTools(required=["PLAN"], optional=[], forbidden=[]),
        output=RouteOutput(
            schema_name="PathPlanCard",
            render_mode="plan",
            require_evidence=True,
        ),
        search=RouteSearch(
            strategy="BOUNDED_TOT_ASTAR",
            candidates=candidates,
            selected_candidate_id="candidate_rebuild_first",
        ),
    )


def test_c3_route_carries_three_public_bounded_candidates_and_selection() -> None:
    decision = _c3_decision(_three_candidates())

    assert decision.search is not None
    assert decision.search.strategy == "BOUNDED_TOT_ASTAR"
    assert decision.search.max_depth == 3
    assert decision.search.score_formula == "total_cost=g_cost+h_cost+penalty"
    assert decision.search.replan_policy.max_candidate_switches == 1
    assert decision.search.selected_candidate_id == "candidate_rebuild_first"
    assert len(decision.search.candidates) == 3
    for candidate in decision.search.candidates:
        assert candidate.depth <= 3
        assert len(candidate.agent_path) <= 3
        assert isinstance(candidate.label, str)
        assert candidate.label
        assert isinstance(candidate.g_cost, float)
        assert isinstance(candidate.h_cost, float)
        assert isinstance(candidate.penalty, float)
        assert isinstance(candidate.total_cost, float)


@pytest.mark.parametrize("candidate_count", [2, 4])
def test_c3_search_rejects_any_candidate_count_other_than_three(
    candidate_count: int,
) -> None:
    candidates = _three_candidates()[:candidate_count]
    if candidate_count == 4:
        candidates.append(
            _candidate(
                "candidate_extra",
                "额外候选",
                agent_path=["PathPlannerAgent"],
                tool_path=["PLAN"],
                depth=1,
                g_cost=1.0,
                h_cost=1.0,
                penalty=0.0,
            )
        )
    with pytest.raises(ValidationError):
        _c3_decision(candidates)


@pytest.mark.parametrize(
    ("agent_path", "tool_path", "depth"),
    [
        (
            [
                "ProfileAgent",
                "DebugAgent",
                "PracticeAgent",
                "PathPlannerAgent",
            ],
            ["PROGRESS", "FIX_BUG", "PLAN"],
            3,
        ),
        (
            ["ProfileAgent", "DebugAgent", "PathPlannerAgent"],
            ["PROGRESS", "FIX_BUG", "PRACTICE", "PLAN"],
            3,
        ),
        (
            ["ProfileAgent", "DebugAgent", "PathPlannerAgent"],
            ["PROGRESS", "FIX_BUG", "PLAN"],
            4,
        ),
    ],
)
def test_c3_search_rejects_each_depth_bound_independently(
    agent_path: list[str],
    tool_path: list[str],
    depth: int,
) -> None:
    with pytest.raises(ValidationError):
        _candidate(
            "candidate_unbounded",
            "越界路径",
            agent_path=agent_path,
            tool_path=tool_path,
            depth=depth,
            g_cost=4.0,
            h_cost=1.0,
            penalty=1.0,
        )


@pytest.mark.parametrize(
    ("path_field", "invalid_identifier"),
    [
        ("agent_path", ""),
        ("agent_path", "A" * 65),
        ("tool_path", ""),
        ("tool_path", "T" * 65),
    ],
)
def test_c3_search_rejects_invalid_public_path_identifiers(
    path_field: str,
    invalid_identifier: str,
) -> None:
    payload = {
        "candidate_id": "candidate_invalid_identifier",
        "label": "无效公开路径",
        "agent_path": ["PathPlannerAgent"],
        "tool_path": ["PLAN"],
        "depth": 1,
        "g_cost": 1.0,
        "h_cost": 1.0,
        "penalty": 0.0,
        "total_cost": 2.0,
    }
    payload[path_field] = [invalid_identifier]

    with pytest.raises(ValidationError):
        RouteSearchCandidate.model_validate(payload)


@pytest.mark.parametrize("cost_field", ["g_cost", "h_cost", "penalty", "total_cost"])
@pytest.mark.parametrize(
    "invalid_cost",
    [-0.1, float("inf"), float("-inf"), float("nan")],
    ids=["negative", "positive-infinity", "negative-infinity", "not-a-number"],
)
def test_c3_search_rejects_each_invalid_numeric_cost_independently(
    cost_field: str,
    invalid_cost: float,
) -> None:
    payload = {
        "candidate_id": "candidate_invalid_cost",
        "label": "无效数值成本",
        "agent_path": ["PathPlannerAgent"],
        "tool_path": ["PLAN"],
        "depth": 1,
        "g_cost": 1.0,
        "h_cost": 1.0,
        "penalty": 0.0,
        "total_cost": 2.0,
    }
    payload[cost_field] = invalid_cost

    with pytest.raises(ValidationError):
        RouteSearchCandidate.model_validate(payload)


_CANONICAL_C3_MESSAGE = (
    "我循环这一章学得很乱，先给我总结一下，再根据我以前犯的错出几道题，" "并告诉我先做哪一道。"
)


def _compiled_c3(
    *,
    message: str = _CANONICAL_C3_MESSAGE,
    context: LearnerRoutingContext | None = None,
    as_scope: str = "default",
    confidence: float = 0.94,
    needs_clarification: bool = False,
    intent: str = "HANDOUT",
    secondary_intent: str = "PRACTICE",
    source: str = "composer_text",
    include_message_diagnostic: bool = True,
) -> RouteDecision:
    effective_message = (
        f"我循环学得不好，{message}" if include_message_diagnostic else message
    )
    return compile_route_decision(
        IntentRouteRequest(
            message=effective_message,
            manual_kc_codes=["PY43-KC-031"],
            learner_context=context,
            scope=as_scope,  # type: ignore[arg-type]
            source=source,  # type: ignore[arg-type]
        ),
        intent=intent,
        secondary_intent=secondary_intent,
        confidence=confidence,
        needs_clarification=needs_clarification,
        clarification_prompt="",
        render_mode="resource",
        resolved_kc_codes=["PY43-KC-031"],
    )


def test_canonical_three_goal_request_compiles_deterministic_c3_search() -> None:
    decision = _compiled_c3()

    assert decision.task.family == "MULTI_TASK"
    assert decision.task.complexity == "C3"
    assert decision.task.sub_intents == ["HANDOUT", "PRACTICE", "PLAN"]
    assert decision.orchestration.topology == "PLANNER_EXECUTOR"
    assert decision.tools.required == ["HANDOUT", "PRACTICE", "PLAN"]
    assert decision.search is not None
    assert len(decision.search.candidates) == 3
    assert len({candidate.candidate_id for candidate in decision.search.candidates}) == 3
    selected = min(
        decision.search.candidates,
        key=lambda candidate: (candidate.total_cost, candidate.candidate_id),
    )
    assert decision.search.selected_candidate_id == selected.candidate_id


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message", "expected_missing"),
    [
        (
            "HANDOUT",
            "PLAN",
            "先总结循环知识，再安排学习路径，并给我出几道题。",
            "PRACTICE",
        ),
        (
            "PRACTICE",
            "PLAN",
            "给我几道循环练习并安排顺序，最后补一份讲义。",
            "HANDOUT",
        ),
        (
            "PLAN",
            "PRACTICE",
            "先安排循环复习顺序，再出几道题，并补一份总结讲义。",
            "HANDOUT",
        ),
    ],
)
def test_explicit_third_goal_completes_only_the_authorized_c3_triad(
    intent: str,
    secondary_intent: str,
    message: str,
    expected_missing: str,
) -> None:
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
    )

    assert decision.task.complexity == "C3"
    assert expected_missing in decision.tools.required
    assert set(decision.tools.required) == {"HANDOUT", "PRACTICE", "PLAN"}
    assert decision.policies.read_before_write is True


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "HANDOUT",
            "PRACTICE",
            "总结循环课件并出三道题，但不要告诉我先做哪一道。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结循环课件并安排学习路径，不要给我出题。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道循环题并安排顺序，不用总结，也不要讲义。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，练习我不需要。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，但我平时会做练习。",
        ),
    ],
)
def test_negated_third_goal_never_expands_a_c2_route(
    intent: str,
    secondary_intent: str,
    message: str,
) -> None:
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == [intent, secondary_intent]


@pytest.mark.parametrize(
    "message",
    [
        "总结并安排学习路径，练习我不需要。",
        "总结并安排学习路径，但我平时会做练习。",
    ],
)
def test_non_action_practice_mentions_do_not_complete_c3_even_with_error_context(
    message: str,
) -> None:
    decision = _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PLAN",
        message=message,
        context=LearnerRoutingContext(
            weak_kcs=["PY43-KC-031"],
            recent_error_kcs=["PY43-KC-031"],
            struggle_patterns=["循环边界重复失败"],
        ),
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == ["HANDOUT", "PLAN"]


@pytest.mark.parametrize(
    "message",
    [
        "总结并安排学习路径，但老师平时会给我出题。",
        "总结并安排学习路径，但我平时会出题。",
        "总结并安排学习路径，但老师会给我出题。",
    ],
)
@pytest.mark.parametrize("with_error_context", [False, True])
def test_descriptive_practice_actions_never_complete_c3(
    message: str,
    with_error_context: bool,
) -> None:
    context = (
        LearnerRoutingContext(
            weak_kcs=["PY43-KC-031"],
            recent_error_kcs=["PY43-KC-031"],
        )
        if with_error_context
        else None
    )
    decision = _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PLAN",
        message=message,
        context=context,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == ["HANDOUT", "PLAN"]


def test_request_marked_practice_action_completes_c3_without_error_history() -> None:
    decision = _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PLAN",
        message="总结并安排学习路径，再给我出几道题。",
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None
    assert decision.tools.required == ["HANDOUT", "PLAN", "PRACTICE"]


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message", "required"),
    [
        (
            "PRACTICE",
            "PLAN",
            "给我几道循环题并安排学习路径，但老师会总结一下。",
            ["PRACTICE", "PLAN"],
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "给我总结并出几道题，但老师会安排学习顺序。",
            ["HANDOUT", "PRACTICE"],
        ),
    ],
)
@pytest.mark.parametrize("with_error_context", [False, True])
def test_descriptive_missing_action_never_completes_c3_for_any_tool(
    intent: str,
    secondary_intent: str,
    message: str,
    required: list[str],
    with_error_context: bool,
) -> None:
    context = (
        LearnerRoutingContext(
            weak_kcs=["PY43-KC-031"],
            recent_error_kcs=["PY43-KC-031"],
        )
        if with_error_context
        else None
    )
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
        context=context,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == required


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "PRACTICE",
            "PLAN",
            "给我几道循环题并安排学习路径，然后总结一下。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "给我总结并出几道题，然后安排学习顺序。",
        ),
    ],
)
def test_direct_request_for_missing_action_still_completes_c3(
    intent: str,
    secondary_intent: str,
    message: str,
) -> None:
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None
    assert set(decision.tools.required) == {"HANDOUT", "PRACTICE", "PLAN"}


def test_negating_answers_does_not_cancel_explicit_practice_request() -> None:
    decision = _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PLAN",
        message="总结并安排学习路径，再给我出题但不要答案。",
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None
    assert decision.tools.required == ["HANDOUT", "PLAN", "PRACTICE"]


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "PRACTICE",
            "PLAN",
            "给我几道循环题并安排学习路径，再总结一下但不要答案。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "给我总结并出几道题，再安排学习顺序但不要答案。",
        ),
    ],
)
def test_unrelated_suffix_negation_does_not_cancel_other_missing_actions(
    intent: str,
    secondary_intent: str,
    message: str,
) -> None:
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None
    assert set(decision.tools.required) == {"HANDOUT", "PRACTICE", "PLAN"}


@pytest.mark.parametrize(
    "message",
    [
        "总结并安排学习路径，不要给我出题。",
        "总结并安排学习路径，再给我出题但不要出题/练习。",
    ],
)
def test_negating_the_missing_practice_action_keeps_route_c2(message: str) -> None:
    decision = _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PLAN",
        message=message,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == ["HANDOUT", "PLAN"]


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message", "required"),
    [
        (
            "PRACTICE",
            "PLAN",
            "给我几道循环题并安排学习路径，再总结一下但不要总结/讲义。",
            ["PRACTICE", "PLAN"],
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "给我总结并出几道题，再安排学习顺序但不要安排/计划。",
            ["HANDOUT", "PRACTICE"],
        ),
    ],
)
def test_suffix_negation_binds_to_each_missing_action(
    intent: str,
    secondary_intent: str,
    message: str,
    required: list[str],
) -> None:
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == required


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，但不能给我出题。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，你之前给我出的题太难了。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，你之前给我的讲义太长了。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，老师安排的学习计划太快了。",
        ),
    ],
)
@pytest.mark.parametrize("with_error_context", [False, True])
def test_negated_historical_or_referenced_action_never_authorizes_c3(
    intent: str,
    secondary_intent: str,
    message: str,
    with_error_context: bool,
) -> None:
    context = LearnerRoutingContext(weak_kcs=["PY43-KC-031"]) if with_error_context else None
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
        context=context,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == [intent, secondary_intent]


@pytest.mark.parametrize("negation", ["不能", "无法", "没法", "不可以", "不准", "禁止"])
def test_capability_or_prohibition_negation_never_authorizes_c3(negation: str) -> None:
    decision = _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PLAN",
        message=f"总结并安排学习路径，但{negation}给我出题。",
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == ["HANDOUT", "PLAN"]


@pytest.mark.parametrize(
    "history_marker",
    ["之前", "以前", "曾经", "上次", "刚才", "已经", "过去", "先前"],
)
def test_historical_action_statement_never_authorizes_c3(history_marker: str) -> None:
    decision = _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PLAN",
        message=f"总结并安排学习路径，你{history_marker}给我出的题太难了。",
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == ["HANDOUT", "PLAN"]


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，你给我出过的题太难了。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，你给我做过的讲义太长了。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，老师安排过的学习计划太快了。",
        ),
    ],
)
def test_completed_action_reference_never_authorizes_c3(
    intent: str,
    secondary_intent: str,
    message: str,
) -> None:
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == [intent, secondary_intent]


@pytest.mark.parametrize("modal", ["会", "要", "将", "准备", "打算"])
@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message_template"),
    [
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，老师{modal}给我出题。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，老师{modal}给我讲义。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，老师{modal}安排学习计划。",
        ),
    ],
)
def test_third_party_future_statement_never_authorizes_c3(
    intent: str,
    secondary_intent: str,
    message_template: str,
    modal: str,
) -> None:
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message_template.format(modal=modal),
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == [intent, secondary_intent]


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，给我出几道难一点的题。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，给我生成简短的讲义。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，给我安排合理的学习计划。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，老师，请给我出几道难一点的题。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，老师，请给我生成简短的讲义。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，老师，请给我安排合理的学习计划。",
        ),
    ],
)
def test_explicit_current_request_still_authorizes_missing_action(
    intent: str,
    secondary_intent: str,
    message: str,
) -> None:
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None
    assert set(decision.tools.required) == {"HANDOUT", "PRACTICE", "PLAN"}


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，但我不想让你给我出题。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，但不应该给我出题。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，但不要让老师给我出题。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，老师去年给我布置的练习太难了。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，老师去年给我写的讲义太长了。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，平台将给我出题。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，班长准备给我出题。",
        ),
    ],
)
@pytest.mark.parametrize("with_error_context", [False, True])
def test_only_current_request_clause_can_authorize_missing_action(
    intent: str,
    secondary_intent: str,
    message: str,
    with_error_context: bool,
) -> None:
    context = LearnerRoutingContext(weak_kcs=["PY43-KC-031"]) if with_error_context else None
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
        context=context,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == [intent, secondary_intent]


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，我要你总结一下。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，我要你安排学习计划。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，我准备请你给我出题。",
        ),
    ],
)
@pytest.mark.parametrize("with_error_context", [False, True])
def test_first_person_current_request_clause_authorizes_missing_action(
    intent: str,
    secondary_intent: str,
    message: str,
    with_error_context: bool,
) -> None:
    context = LearnerRoutingContext(weak_kcs=["PY43-KC-031"]) if with_error_context else None
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
        context=context,
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None
    assert set(decision.tools.required) == {"HANDOUT", "PRACTICE", "PLAN"}


@pytest.mark.parametrize("connector", ["然后再", "最后再", "接着再", "并且"])
@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message_template"),
    [
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，{connector}总结一下。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，{connector}出几道题。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，{connector}安排学习计划。",
        ),
    ],
)
def test_stacked_connectors_authorize_each_missing_action(
    intent: str,
    secondary_intent: str,
    message_template: str,
    connector: str,
) -> None:
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message_template.format(connector=connector),
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None
    assert set(decision.tools.required) == {"HANDOUT", "PRACTICE", "PLAN"}


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，但现在请给我出几道题。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，这次请给我出几道题。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，麻烦给我出几道题。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，能不能给我出几道题。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，下一步：请给我出几道题。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，请特别给我出几道题。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，请特别给我总结一下。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，请分别安排学习计划。",
        ),
    ],
)
def test_common_current_request_frames_authorize_missing_action(
    intent: str,
    secondary_intent: str,
    message: str,
) -> None:
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None
    assert set(decision.tools.required) == {"HANDOUT", "PRACTICE", "PLAN"}


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，给我难一点的题。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，给我简短的讲义。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，老师，请给我难一点的题。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，老师，请给我简短的讲义。",
        ),
    ],
)
@pytest.mark.parametrize("with_error_context", [False, True])
def test_narrow_request_object_grammar_authorizes_approved_noun_requests(
    intent: str,
    secondary_intent: str,
    message: str,
    with_error_context: bool,
) -> None:
    context = LearnerRoutingContext(weak_kcs=["PY43-KC-031"]) if with_error_context else None
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
        context=context,
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None
    assert set(decision.tools.required) == {"HANDOUT", "PRACTICE", "PLAN"}


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，请给我出三道题；算了，不要给我出题。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，请给我生成一份讲义；" "算了，不要再给我总结讲义。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，请给我安排学习计划；" "算了，不要给我安排学习计划。",
        ),
    ],
)
@pytest.mark.parametrize("with_error_context", [False, True])
def test_later_action_bound_cancellation_overrides_prior_authorization(
    intent: str,
    secondary_intent: str,
    message: str,
    with_error_context: bool,
) -> None:
    context = LearnerRoutingContext(weak_kcs=["PY43-KC-031"]) if with_error_context else None
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
        context=context,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == [intent, secondary_intent]


def test_later_explicit_request_can_reauthorize_after_cancellation() -> None:
    decision = _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PLAN",
        message=(
            "总结并安排学习路径，请给我出三道题；算了，不要给我出题；" "还是请给我出三道新题。"
        ),
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None
    assert set(decision.tools.required) == {"HANDOUT", "PRACTICE", "PLAN"}


@pytest.mark.parametrize(
    (
        "intent",
        "secondary_intent",
        "base_message",
        "noun_request",
        "noun_cancellation",
        "noun_reauthorization",
    ),
    [
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径",
            "给我难一点的题",
            "不要再给我难一点的题",
            "还是给我三道题",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径",
            "给我简短的讲义",
            "不要再给我一份讲义",
            "还是给我一份讲义",
        ),
    ],
)
@pytest.mark.parametrize("with_error_context", [False, True])
def test_noun_object_request_cancel_and_reauthorize_in_clause_order(
    intent: str,
    secondary_intent: str,
    base_message: str,
    noun_request: str,
    noun_cancellation: str,
    noun_reauthorization: str,
    with_error_context: bool,
) -> None:
    context = LearnerRoutingContext(weak_kcs=["PY43-KC-031"]) if with_error_context else None
    authorized_message = f"{base_message}，{noun_request}。"
    cancelled_message = f"{base_message}，{noun_request}；算了，{noun_cancellation}。"
    reauthorized_message = (
        f"{base_message}，{noun_request}；算了，{noun_cancellation}；" f"{noun_reauthorization}。"
    )

    authorized = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=authorized_message,
        context=context,
    )
    cancelled = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=cancelled_message,
        context=context,
    )
    reauthorized = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=reauthorized_message,
        context=context,
    )

    assert authorized.task.complexity == "C3"
    assert cancelled.task.complexity == "C2"
    assert cancelled.search is None
    assert reauthorized.task.complexity == "C3"
    assert reauthorized.search is not None


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，请不要让那个总是特别严格而且喜欢突然检查的" "老师给我出三道题。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，请不要让那个总是特别严格而且喜欢突然检查的"
            "老师给我总结一下。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，请不要让那个总是特别严格而且喜欢突然检查的" "老师给我安排学习计划。",
        ),
    ],
)
def test_blocker_binds_to_later_action_without_distance_cap(
    intent: str,
    secondary_intent: str,
    message: str,
) -> None:
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == [intent, secondary_intent]


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，出三道题的是老师。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，总结一下是老师的口头禅。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，安排一下学习计划是老师的要求。",
        ),
    ],
)
@pytest.mark.parametrize("with_error_context", [False, True])
def test_imperative_shaped_declarative_predicate_never_authorizes_c3(
    intent: str,
    secondary_intent: str,
    message: str,
    with_error_context: bool,
) -> None:
    context = LearnerRoutingContext(weak_kcs=["PY43-KC-031"]) if with_error_context else None
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
        context=context,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == [intent, secondary_intent]


@pytest.mark.parametrize(
    "message",
    [
        "总结并安排学习路径，请根据我以前做过的题再给我出三道新题。",
        "总结并安排学习路径，再根据我以前做过的题给我出三道新题。",
    ],
)
def test_history_elsewhere_in_request_clause_does_not_cancel_new_action(
    message: str,
) -> None:
    decision = _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PLAN",
        message=message,
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None
    assert set(decision.tools.required) == {"HANDOUT", "PRACTICE", "PLAN"}


@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message"),
    [
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，给我留的题太难了。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，给我推荐的讲义太长了。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，给我推荐的学习计划太快了。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，出题的是老师。",
        ),
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，总结是老师做的。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，安排学习计划的是老师。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，请拒绝给我出题。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，我需要你停止给我出题。",
        ),
    ],
)
@pytest.mark.parametrize("with_error_context", [False, True])
def test_noun_reference_bare_statement_or_stop_operator_never_authorizes_c3(
    intent: str,
    secondary_intent: str,
    message: str,
    with_error_context: bool,
) -> None:
    context = LearnerRoutingContext(weak_kcs=["PY43-KC-031"]) if with_error_context else None
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message,
        context=context,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == [intent, secondary_intent]


@pytest.mark.parametrize("operator", ["拒绝", "停止", "取消", "避免", "阻止"])
@pytest.mark.parametrize(
    ("intent", "secondary_intent", "message_template"),
    [
        (
            "PRACTICE",
            "PLAN",
            "出几道题并安排学习路径，请{operator}总结一下。",
        ),
        (
            "HANDOUT",
            "PLAN",
            "总结并安排学习路径，请{operator}给我出几道题。",
        ),
        (
            "HANDOUT",
            "PRACTICE",
            "总结并出几道题，请{operator}安排学习计划。",
        ),
    ],
)
def test_stop_operator_binds_to_each_missing_action(
    intent: str,
    secondary_intent: str,
    message_template: str,
    operator: str,
) -> None:
    decision = _compiled_c3(
        intent=intent,
        secondary_intent=secondary_intent,
        message=message_template.format(operator=operator),
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == [intent, secondary_intent]


def test_repeated_near_limit_noun_cues_stay_linear_and_fail_closed() -> None:
    message = ("老师去年给我留的题太难了，" * 260).rstrip("，")
    assert 3000 < len(message) < 4000

    _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PLAN",
        message=message,
    )
    samples: list[float] = []
    for _ in range(5):
        started = perf_counter()
        decision = _compiled_c3(
            intent="HANDOUT",
            secondary_intent="PLAN",
            message=message,
        )
        samples.append(perf_counter() - started)
        assert decision.task.complexity == "C2"

    assert median(samples) < 0.15


def test_three_explicit_actions_without_error_or_struggle_signal_remain_c2() -> None:
    decision = _compiled_c3(
        message="先总结循环知识，再给我出几道题，并告诉我先做哪一道。",
        include_message_diagnostic=False,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == ["HANDOUT", "PRACTICE"]


def test_authoritative_error_history_preserves_explicit_c3_route() -> None:
    decision = _compiled_c3(
        message="先总结循环知识，再给我出几道题，并告诉我先做哪一道。",
        context=LearnerRoutingContext(
            recent_error_kcs=["PY43-KC-031"],
            recent_submissions=[
                LearnerRecentSubmission(
                    problem_id=41,
                    kc_code="PY43-KC-031",
                    passed=False,
                )
            ],
        ),
        include_message_diagnostic=False,
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None


def test_upstream_intents_cannot_invent_two_unrequested_c3_actions() -> None:
    decision = _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PRACTICE",
        message="我循环学得不好，请安排学习路径。",
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == ["HANDOUT", "PRACTICE"]


@pytest.mark.parametrize(
    "diagnostic_statement",
    [
        "这一章没有困难，我已经完全掌握。",
        "这一章不困难。",
    ],
)
def test_negated_difficulty_never_supplies_the_c3_diagnostic_signal(
    diagnostic_statement: str,
) -> None:
    decision = _compiled_c3(
        message=(
            f"{diagnostic_statement}"
            "请总结循环知识，再给我出几道题，并告诉我先做哪一道。"
        ),
        include_message_diagnostic=False,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None


def test_historical_task_difficulty_supplies_the_c3_diagnostic_signal() -> None:
    decision = _compiled_c3(
        message=(
            "你之前出的题太难了。"
            "请总结循环知识，再给我出几道新题，并告诉我先做哪一道。"
        ),
        include_message_diagnostic=False,
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None


@pytest.mark.parametrize(
    ("confidence", "needs_clarification", "scope"),
    [
        (0.54, False, "default"),
        (0.94, True, "default"),
        (0.94, False, "quiz"),
    ],
)
def test_clarification_or_scope_failure_never_enters_c3_search(
    confidence: float,
    needs_clarification: bool,
    scope: str,
) -> None:
    decision = _compiled_c3(
        confidence=confidence,
        needs_clarification=needs_clarification,
        as_scope=scope,
    )

    assert decision.task.complexity == "C0"
    assert decision.task.needs_clarification is True
    assert decision.search is None
    assert decision.tools.required == []


@pytest.mark.parametrize(
    "source",
    [
        "composer_text",
        "slash_command",
        "capability_launcher",
        "contextual_suggestion",
    ],
)
def test_low_confidence_c3_is_always_clarified_for_every_source(source: str) -> None:
    decision = _compiled_c3(confidence=0.54, source=source)

    assert decision.task.complexity == "C0"
    assert decision.task.needs_clarification is True
    assert decision.search is None
    assert decision.tools.required == []


@pytest.mark.parametrize(
    "source",
    [
        "composer_text",
        "slash_command",
        "capability_launcher",
        "contextual_suggestion",
    ],
)
def test_low_confidence_c2_cannot_hide_behind_an_explicit_source(source: str) -> None:
    decision = _compiled_c3(
        message="请总结循环知识，再给我出几道题。",
        confidence=0.1,
        source=source,
    )

    assert decision.task.complexity == "C0"
    assert decision.task.needs_clarification is True
    assert decision.search is None
    assert decision.tools.required == []


@pytest.mark.parametrize(
    "message",
    [
        "总结并安排学习路径，请问老师会给我出题吗？",
        "总结并安排学习路径，然后给我出题的将是老师。",
        "总结并安排学习路径，请给我出三道题；算了，不要了。",
        "总结并安排学习路径，告诉我老师会不会给我出题。",
        "总结并安排学习路径，请解释为什么老师会给我出题。",
        "总结并安排学习路径，然后给我出题的可能是老师。",
        "总结并安排学习路径，然后给我出题的那位脾气十分古怪老师可能来自学校。",
        "总结并安排学习路径，然后给我出题的那个老师大概来自学校。",
    ],
)
def test_question_declaration_and_standalone_cancellation_never_authorize_c3(
    message: str,
) -> None:
    decision = _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PLAN",
        message=message,
    )

    assert decision.task.complexity == "C2"
    assert decision.search is None
    assert decision.tools.required == ["HANDOUT", "PLAN"]


@pytest.mark.parametrize(
    "message",
    [
        "总结并安排学习路径，请分析我的错误并给我出三道题。",
        "总结并安排学习路径，请根据老师给的错题给我出三道新题。",
        "总结并安排学习路径，请给我出题的难度应该适中。",
    ],
)
def test_direct_practice_requests_keep_context_and_resource_constraints(
    message: str,
) -> None:
    decision = _compiled_c3(
        intent="HANDOUT",
        secondary_intent="PLAN",
        message=message,
    )

    assert decision.task.complexity == "C3"
    assert decision.search is not None


def test_compiled_candidates_never_expand_authority_and_map_registry_agents() -> None:
    decision = _compiled_c3()
    assert decision.search is not None
    expected_agents = {
        "HANDOUT": "HandoutAgent",
        "PRACTICE": "PracticeAgent",
        "PLAN": "PathPlannerAgent",
    }

    for candidate in decision.search.candidates:
        assert len(candidate.tool_path) == len(set(candidate.tool_path))
        assert set(candidate.tool_path) == set(decision.tools.required)
        assert candidate.depth == len(candidate.tool_path) == len(candidate.agent_path) == 3
        assert candidate.depth <= decision.policies.max_planning_depth
        assert candidate.agent_path == [expected_agents[tool] for tool in candidate.tool_path]


def test_same_authoritative_facts_keep_route_search_and_selection_stable() -> None:
    def context(as_of: str) -> LearnerRoutingContext:
        return LearnerRoutingContext(
            kc_mastery={"PY43-KC-031": 0.31},
            kc_confidence={"PY43-KC-031": 0.84},
            weak_kcs=["PY43-KC-031"],
            recent_error_kcs=["PY43-KC-031"],
            cognitive_load="MEDIUM",
            frustration_level="MEDIUM",
            recent_resources=[
                LearnerRecentResource(
                    kc_code="PY43-KC-031",
                    type="PRACTICE",
                    created_at="2026-07-26T08:00:00Z",
                )
            ],
            recent_submissions=[
                LearnerRecentSubmission(
                    problem_id=41,
                    kc_code="PY43-KC-031",
                    passed=False,
                )
            ],
            struggle_patterns=["循环边界重复失败"],
            as_of=as_of,
        )

    first = _compiled_c3(context=context("2026-07-26T08:10:00Z"))
    second = _compiled_c3(context=context("2026-07-26T08:15:00Z"))

    assert first.route_id == second.route_id
    assert first.search is not None
    assert second.search is not None
    assert first.search.model_dump() == second.search.model_dump()


def test_c3_scoring_uses_exact_public_costs_for_standard_learner() -> None:
    decision = _compiled_c3()

    assert decision.search is not None
    costs = {
        candidate.candidate_id: (
            candidate.g_cost,
            candidate.h_cost,
            candidate.penalty,
            candidate.total_cost,
        )
        for candidate in decision.search.candidates
    }
    assert costs == {
        "candidate_plan_first": (2.66, 0.35, 0.0, 3.01),
        "candidate_rebuild_first": (2.63, 0.2, 0.0, 2.83),
        "candidate_practice_first": (2.62, 0.15, 0.0, 2.77),
    }
    assert decision.search.selected_candidate_id == "candidate_practice_first"


def test_c3_scoring_selects_planning_first_for_high_load_repeated_errors() -> None:
    decision = _compiled_c3(
        context=LearnerRoutingContext(
            kc_mastery={"PY43-KC-031": 0.24},
            weak_kcs=["PY43-KC-031"],
            recent_error_kcs=["PY43-KC-031"],
            cognitive_load="HIGH",
            frustration_level="HIGH",
            recent_submissions=[
                LearnerRecentSubmission(
                    problem_id=41,
                    kc_code="PY43-KC-031",
                    passed=False,
                ),
                LearnerRecentSubmission(
                    problem_id=42,
                    kc_code="PY43-KC-031",
                    passed=False,
                ),
            ],
            struggle_patterns=["循环边界重复失败"],
        )
    )

    assert decision.search is not None
    costs = {
        candidate.candidate_id: (
            candidate.g_cost,
            candidate.h_cost,
            candidate.penalty,
            candidate.total_cost,
        )
        for candidate in decision.search.candidates
    }
    assert costs == {
        "candidate_plan_first": (2.66, 0.1, 0.0, 2.76),
        "candidate_rebuild_first": (2.63, 0.15, 0.15, 2.93),
        "candidate_practice_first": (2.62, 0.45, 1.0, 4.07),
    }
    assert decision.search.selected_candidate_id == "candidate_plan_first"


@pytest.mark.parametrize(
    ("resource_type", "expected_penalties", "expected_selected"),
    [
        (
            "HANDOUT",
            {
                "candidate_plan_first": 0.1,
                "candidate_rebuild_first": 0.2,
                "candidate_practice_first": 0.0,
            },
            "candidate_practice_first",
        ),
        (
            "PRACTICE",
            {
                "candidate_plan_first": 0.0,
                "candidate_rebuild_first": 0.0,
                "candidate_practice_first": 0.1,
            },
            "candidate_rebuild_first",
        ),
    ],
)
def test_c3_scoring_penalizes_repeating_each_first_resource_type(
    resource_type: str,
    expected_penalties: dict[str, float],
    expected_selected: str,
) -> None:
    decision = _compiled_c3(
        context=LearnerRoutingContext(
            recent_resources=[
                LearnerRecentResource(
                    kc_code="PY43-KC-031",
                    type=resource_type,  # type: ignore[arg-type]
                    created_at="2026-07-26T08:00:00Z",
                )
            ],
        )
    )

    assert decision.search is not None
    costs = {candidate.candidate_id: candidate.penalty for candidate in decision.search.candidates}
    assert costs == expected_penalties
    assert decision.search.selected_candidate_id == expected_selected


@pytest.mark.parametrize(
    ("context", "expected_probe_h", "expected_probe_penalty"),
    [
        pytest.param(
            LearnerRoutingContext(weak_kcs=["PY43-KC-031"]),
            0.15,
            0.0,
            id="weak-kcs-only",
        ),
        pytest.param(
            LearnerRoutingContext(recent_error_kcs=["PY43-KC-031"]),
            0.45,
            0.25,
            id="recent-error-kcs-only",
        ),
        pytest.param(
            LearnerRoutingContext(
                recent_submissions=[
                    LearnerRecentSubmission(
                        problem_id=41,
                        kc_code="PY43-KC-031",
                        passed=False,
                    )
                ],
            ),
            0.15,
            0.0,
            id="failed-submission-only",
        ),
        pytest.param(
            LearnerRoutingContext(struggle_patterns=["循环边界重复失败"]),
            0.15,
            0.0,
            id="struggle-patterns-only",
        ),
    ],
)
def test_only_recent_error_kcs_raise_probe_h_and_penalty(
    context: LearnerRoutingContext,
    expected_probe_h: float,
    expected_probe_penalty: float,
) -> None:
    decision = _compiled_c3(context=context)

    assert decision.search is not None
    h_costs = {candidate.candidate_id: candidate.h_cost for candidate in decision.search.candidates}
    assert h_costs == {
        "candidate_plan_first": 0.1,
        "candidate_rebuild_first": 0.15,
        "candidate_practice_first": expected_probe_h,
    }
    probe = next(
        candidate
        for candidate in decision.search.candidates
        if candidate.candidate_id == "candidate_practice_first"
    )
    assert probe.penalty == expected_probe_penalty
