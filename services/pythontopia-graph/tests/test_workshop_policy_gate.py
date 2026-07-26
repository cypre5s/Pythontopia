"""百晓工坊确定性策略门测试（Phase 1 RED/GREEN）。"""

from __future__ import annotations

import pytest
from pydantic import ValidationError

from pythontopia_graph.routing.workshop_policy import (
    RoutePolicyError,
    compile_route_decision,
    validate_route_decision,
)
from pythontopia_graph.schemas.intent import IntentRouteRequest
from pythontopia_graph.schemas.workshop_route import RouteDecision
from pythontopia_graph.tools.registry import TOOLS, tool_catalog_payload


def _ask_decision() -> RouteDecision:
    return compile_route_decision(
        IntentRouteRequest(message="解释列表切片"),
        intent="ASK",
        confidence=0.9,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="answer",
    )


def test_every_registered_tool_declares_agent_context_and_result_governance() -> None:
    for spec in TOOLS.values():
        assert isinstance(spec.allowed_agents, tuple)
        assert isinstance(spec.required_context, tuple)
        assert spec.result_schema
        assert spec.sensitive_data in {"none", "learner", "submission", "external"}
        assert spec.side_effects in {"none", "navigation", "resource_write", "external_read"}

    assert TOOLS["FIX_BUG"].allowed_agents == ("DebugAgent",)
    assert TOOLS["FIX_BUG"].exec_mode == "bugmine"
    assert "code_or_error" in TOOLS["FIX_BUG"].required_context
    assert TOOLS["HANDOUT"].side_effects == "resource_write"


def test_catalog_exposes_governance_metadata() -> None:
    first = tool_catalog_payload()[0]
    assert {
        "allowedAgents",
        "requiredContext",
        "resultSchema",
        "sensitiveData",
        "sideEffects",
    } <= set(first)


def test_policy_gate_rejects_unknown_agent() -> None:
    payload = _ask_decision().model_dump()
    payload["orchestration"]["primary_agent"] = "ImaginaryAgent"
    decision = RouteDecision.model_validate(payload)

    with pytest.raises(RoutePolicyError, match="unknown agent"):
        validate_route_decision(decision, scope="default")


def test_policy_gate_rejects_unknown_tool() -> None:
    payload = _ask_decision().model_dump()
    payload["tools"]["required"] = ["GET_MAGIC_ANSWER"]
    decision = RouteDecision.model_validate(payload)

    with pytest.raises(RoutePolicyError, match="unknown tool"):
        validate_route_decision(decision, scope="default")


def test_policy_gate_rejects_agent_not_allowed_for_tool() -> None:
    payload = _ask_decision().model_dump()
    payload["orchestration"]["primary_agent"] = "DebugAgent"
    decision = RouteDecision.model_validate(payload)

    with pytest.raises(RoutePolicyError, match="not allowed"):
        validate_route_decision(decision, scope="default")


def test_policy_gate_rejects_optional_tool_outside_agent_allowlist() -> None:
    payload = _ask_decision().model_dump()
    payload["tools"]["optional"] = ["MINDMAP"]
    payload["tools"]["forbidden"].remove("MINDMAP")
    decision = RouteDecision.model_validate(payload)

    with pytest.raises(RoutePolicyError, match="not allowed"):
        validate_route_decision(decision, scope="default")


def test_policy_gate_rejects_tool_list_overlap() -> None:
    payload = _ask_decision().model_dump()
    payload["tools"]["forbidden"].append("ASK")
    decision = RouteDecision.model_validate(payload)

    with pytest.raises(RoutePolicyError, match="overlap"):
        validate_route_decision(decision, scope="default")


def test_policy_gate_requires_read_before_write_for_required_resource_tool() -> None:
    payload = _c3_decision().model_dump()
    payload["policies"]["read_before_write"] = False

    with pytest.raises(RoutePolicyError, match="read_before_write"):
        _validate_mutated_c3(payload)


def test_policy_gate_requires_read_before_write_for_optional_resource_tool() -> None:
    payload = _ask_decision().model_dump()
    payload["tools"]["optional"] = ["HANDOUT"]
    payload["tools"]["forbidden"].remove("HANDOUT")
    payload["orchestration"]["supporting_agents"] = ["HandoutAgent"]
    decision = RouteDecision.model_validate(payload)

    with pytest.raises(RoutePolicyError, match="read_before_write"):
        validate_route_decision(decision, scope="default")


def test_policy_gate_rejects_output_schema_diverging_from_registry() -> None:
    payload = _ask_decision().model_dump()
    payload["output"]["schema_name"] = "MadeUpCard"
    decision = RouteDecision.model_validate(payload)

    with pytest.raises(RoutePolicyError, match="output schema"):
        validate_route_decision(decision, scope="default")


def test_policy_budget_cannot_exceed_hard_limits() -> None:
    payload = _ask_decision().model_dump()
    payload["policies"]["max_tool_calls"] = 9

    with pytest.raises(ValidationError):
        RouteDecision.model_validate(payload)


def test_quiz_scope_rejects_out_of_scope_required_tool() -> None:
    decision = compile_route_decision(
        IntentRouteRequest(message="生成循环讲义", scope="quiz"),
        intent="HANDOUT",
        confidence=0.9,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
    )

    assert decision.task.needs_clarification is True
    assert decision.tools.required == []
    assert "HANDOUT" in decision.tools.forbidden


def test_multi_agent_policy_validates_each_tool_against_declared_agent_set() -> None:
    decision = compile_route_decision(
        IntentRouteRequest(
            message="总结课件再出题",
            manual_kc_codes=["PY43-KC-012"],
        ),
        intent="HANDOUT",
        secondary_intent="PRACTICE",
        confidence=0.9,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
    )

    validate_route_decision(decision, scope="default")
    payload = decision.model_dump()
    payload["orchestration"]["supporting_agents"] = ["DebugAgent"]
    invalid = RouteDecision.model_validate(payload)
    with pytest.raises(RoutePolicyError, match="no declared agent"):
        validate_route_decision(invalid, scope="default")


def _c3_request(
    *,
    message: str = (
        "我循环这一章学得很乱，先给我总结一下，再根据我以前犯的错"
        "出几道题，并告诉我先做哪一道。"
    ),
) -> IntentRouteRequest:
    return IntentRouteRequest(
        message=message,
        manual_kc_codes=["PY43-KC-031"],
    )


def _c3_decision() -> RouteDecision:
    return compile_route_decision(
        _c3_request(),
        intent="HANDOUT",
        secondary_intent="PRACTICE",
        confidence=0.94,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
        resolved_kc_codes=["PY43-KC-031"],
    )


def _validate_mutated_c3(payload: dict[str, object]) -> None:
    decision = RouteDecision.model_validate(payload)
    validate_route_decision(decision, scope="default", request=_c3_request())


def _equal_cost_c3_payload(*, selected_candidate_id: str) -> dict[str, object]:
    payload = _c3_decision().model_dump()
    for candidate in payload["search"]["candidates"]:
        candidate["g_cost"] = 1.0
        candidate["h_cost"] = 1.0
        candidate["penalty"] = 0.0
        candidate["total_cost"] = 2.0
    candidates = payload["search"]["candidates"]
    payload["search"]["candidates"] = [candidates[1], candidates[2], candidates[0]]
    payload["search"]["selected_candidate_id"] = selected_candidate_id
    return payload


def test_policy_gate_rejects_search_on_non_c3_route() -> None:
    payload = _c3_decision().model_dump()
    payload["task"]["complexity"] = "C2"

    with pytest.raises(RoutePolicyError, match="only allowed for C3"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_missing_search_on_c3_route() -> None:
    payload = _c3_decision().model_dump()
    payload["search"] = None

    with pytest.raises(RoutePolicyError, match="requires search"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_non_planner_executor_c3_route() -> None:
    payload = _c3_decision().model_dump()
    payload["orchestration"]["topology"] = "SEQUENTIAL"

    with pytest.raises(RoutePolicyError, match="PLANNER_EXECUTOR"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_c3_sub_intents_diverging_from_required_tools() -> None:
    payload = _c3_decision().model_dump()
    payload["task"]["sub_intents"] = ["HANDOUT", "PRACTICE"]

    with pytest.raises(RoutePolicyError, match="sub_intents"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_duplicate_c3_candidate_ids() -> None:
    payload = _c3_decision().model_dump()
    payload["search"]["candidates"][1]["candidate_id"] = payload["search"]["candidates"][0][
        "candidate_id"
    ]

    with pytest.raises(RoutePolicyError, match="candidate ids"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_distinct_candidate_ids_with_duplicate_tool_paths() -> None:
    payload = _c3_decision().model_dump()
    first = payload["search"]["candidates"][0]
    second = payload["search"]["candidates"][1]
    second["tool_path"] = list(first["tool_path"])
    second["agent_path"] = list(first["agent_path"])

    with pytest.raises(RoutePolicyError, match="candidate tool paths must be unique"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_missing_selected_c3_candidate() -> None:
    payload = _c3_decision().model_dump()
    payload["search"]["selected_candidate_id"] = "candidate_missing"

    with pytest.raises(RoutePolicyError, match="selected candidate"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_selected_candidate_that_is_not_lowest_cost() -> None:
    payload = _c3_decision().model_dump()
    payload["search"]["selected_candidate_id"] = "candidate_plan_first"

    with pytest.raises(RoutePolicyError, match="lowest-cost"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_even_stably_selected_forged_equal_costs() -> None:
    payload = _equal_cost_c3_payload(selected_candidate_id="candidate_plan_first")

    with pytest.raises(RoutePolicyError, match="costs or selection"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_non_first_id_for_equal_cost_candidates() -> None:
    payload = _equal_cost_c3_payload(selected_candidate_id="candidate_rebuild_first")
    assert payload["search"]["candidates"][0]["candidate_id"] == "candidate_rebuild_first"

    with pytest.raises(RoutePolicyError, match="stable lowest-cost"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_candidate_total_not_equal_to_public_formula() -> None:
    payload = _c3_decision().model_dump()
    payload["search"]["candidates"][0]["total_cost"] += 0.1

    with pytest.raises(RoutePolicyError, match="total_cost"):
        _validate_mutated_c3(payload)


def test_policy_gate_requires_current_request_context_for_c3() -> None:
    with pytest.raises(RoutePolicyError, match="current request context"):
        validate_route_decision(_c3_decision(), scope="default")


def test_policy_gate_rejects_c3_bound_to_a_different_message() -> None:
    with pytest.raises(RoutePolicyError, match="does not match the current request"):
        validate_route_decision(
            _c3_decision(),
            scope="default",
            request=_c3_request(message="你好，只聊聊天，不要生成资源。"),
        )


@pytest.mark.parametrize(
    ("field_path", "invalid_value", "expected_error"),
    [
        (("policies", "require_safety"), False, "safety"),
        (("policies", "require_grounding"), False, "grounding"),
        (("policies", "allow_common_knowledge"), True, "common knowledge"),
        (("output", "require_evidence"), False, "output evidence"),
    ],
)
def test_policy_gate_rejects_weakened_c3_guard_policies(
    field_path: tuple[str, str],
    invalid_value: bool,
    expected_error: str,
) -> None:
    payload = _c3_decision().model_dump()
    section, field = field_path
    payload[section][field] = invalid_value

    with pytest.raises(RoutePolicyError, match=expected_error):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_incomplete_forbidden_complement() -> None:
    payload = _c3_decision().model_dump()
    payload["tools"]["forbidden"] = []

    with pytest.raises(RoutePolicyError, match="executable complement"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_candidate_tool_authority_expansion() -> None:
    payload = _c3_decision().model_dump()
    candidate = payload["search"]["candidates"][0]
    candidate["tool_path"][2] = "MINDMAP"
    candidate["agent_path"][2] = "MindMapAgent"

    with pytest.raises(RoutePolicyError, match="required tool authority"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_candidate_missing_required_tool() -> None:
    payload = _c3_decision().model_dump()
    candidate = payload["search"]["candidates"][0]
    candidate["tool_path"] = candidate["tool_path"][:2]
    candidate["agent_path"] = candidate["agent_path"][:2]
    candidate["depth"] = 2

    with pytest.raises(RoutePolicyError, match="required tool authority"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_duplicate_tool_inside_candidate() -> None:
    payload = _c3_decision().model_dump()
    candidate = payload["search"]["candidates"][0]
    candidate["tool_path"][1] = candidate["tool_path"][0]
    candidate["agent_path"][1] = candidate["agent_path"][0]

    with pytest.raises(RoutePolicyError, match="duplicate tools"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_unregistered_candidate_template_id() -> None:
    payload = _c3_decision().model_dump()
    payload["search"]["candidates"][0]["candidate_id"] = "candidate_invented"

    with pytest.raises(RoutePolicyError, match="bounded templates"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_unregistered_candidate_permutation() -> None:
    payload = _c3_decision().model_dump()
    candidate = payload["search"]["candidates"][0]
    candidate["tool_path"] = ["PLAN", "PRACTICE", "HANDOUT"]
    candidate["agent_path"] = ["PathPlannerAgent", "PracticeAgent", "HandoutAgent"]

    with pytest.raises(RoutePolicyError, match="bounded template"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_candidate_agent_tool_mismatch() -> None:
    payload = _c3_decision().model_dump()
    payload["search"]["candidates"][0]["agent_path"][0] = "PracticeAgent"

    with pytest.raises(RoutePolicyError, match="not allowed for candidate tool"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_unknown_candidate_agent() -> None:
    payload = _c3_decision().model_dump()
    payload["search"]["candidates"][0]["agent_path"][0] = "ImaginaryAgent"

    with pytest.raises(RoutePolicyError, match="unknown candidate agent"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_candidate_depth_not_matching_paths() -> None:
    payload = _c3_decision().model_dump()
    payload["search"]["candidates"][0]["depth"] = 2

    with pytest.raises(RoutePolicyError, match="depth"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_candidate_depth_over_route_policy() -> None:
    payload = _c3_decision().model_dump()
    payload["policies"]["max_planning_depth"] = 2

    with pytest.raises(RoutePolicyError, match="planning depth"):
        _validate_mutated_c3(payload)


def test_policy_gate_rejects_search_switch_budget_over_route_replan_budget() -> None:
    payload = _c3_decision().model_dump()
    payload["policies"]["max_replans"] = 0

    with pytest.raises(RoutePolicyError, match="switch budget"):
        _validate_mutated_c3(payload)
