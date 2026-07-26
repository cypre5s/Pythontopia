"""Phase 4 C3 TaskPlan candidate consumption and bounded switching."""

from __future__ import annotations

from collections import Counter
from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.routing import workshop_task_plan
from pythontopia_graph.routing.workshop_metrics import reset_routing_metrics
from pythontopia_graph.routing.workshop_policy import compile_route_decision
from pythontopia_graph.routing.workshop_task_plan import (
    TaskPlanPolicyError,
    compile_task_plan,
    validate_task_plan,
)
from pythontopia_graph.schemas.intent import IntentRouteRequest, LearnerRoutingContext
from pythontopia_graph.schemas.orchestrator import (
    DispatchResult,
    OrchestratorRequest,
    PlanStep,
)
from pythontopia_graph.schemas.workshop_route import RouteDecision


@pytest.fixture(autouse=True)
def _isolated_routing_metrics():
    # dispatch 会写入进程级路由指标单例；显式复位避免污染同进程其它测试的精确计数。
    reset_routing_metrics()
    yield
    reset_routing_metrics()


class NoModelProvider(LlmProvider):
    async def chat(self, messages, **kwargs):  # type: ignore[override]
        raise AssertionError("authoritative C3 route must not call the model")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield "已完成 C3 学习任务。"


def _c3_decision(*, context: LearnerRoutingContext | None = None):
    request = IntentRouteRequest(
        message=(
            "我循环这一章学得很乱，先给我总结一下，再根据我以前犯的错出几道题，"
            "并告诉我先做哪一道。"
        ),
        manual_kc_codes=["PY43-KC-031"],
        learner_context=context,
    )
    return (
        request,
        compile_route_decision(
            request,
            intent="HANDOUT",
            secondary_intent="PRACTICE",
            confidence=0.94,
            needs_clarification=False,
            clarification_prompt="",
            render_mode="resource",
            resolved_kc_codes=["PY43-KC-031"],
        ),
    )


def test_c3_task_plan_consumes_selected_candidate_exact_tool_and_agent_order() -> None:
    request, decision = _c3_decision()
    assert decision.search is not None
    selected = next(
        candidate
        for candidate in decision.search.candidates
        if candidate.candidate_id == decision.search.selected_candidate_id
    )

    plan = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    )

    assert [step.tool_name for step in plan.steps] == selected.tool_path
    assert [step.agent for step in plan.steps] == selected.agent_path
    assert plan.initial_candidate_id == selected.candidate_id
    assert plan.active_candidate_id == selected.candidate_id
    assert plan.replanned_candidate_id is None


def test_rebuild_runtime_after_switch_returns_consistent_collections() -> None:
    request, decision = _c3_decision()
    plan = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    )
    successful_step = plan.steps[0]
    switched = workshop_task_plan.switch_task_plan_candidate_once(
        plan,
        decision,
        successful_step_ids={successful_step.id},
    )

    state = workshop_task_plan.rebuild_runtime_after_switch(
        switched,
        successful_step_ids={successful_step.id},
    )

    assert state.plan == list(switched.steps)
    assert state.completed == {successful_step.id}
    assert state.failed == set()
    assert list(state.pending) == [
        step.id for step in switched.steps if step.id != successful_step.id
    ]
    assert all(state.pending[step_id].id == step_id for step_id in state.pending)
    assert state.step_status == {
        step.id: ("done" if step.id == successful_step.id else "pending")
        for step in switched.steps
    }
    assert state.replans_used == switched.replans_used == 1


def test_candidate_switch_preserves_success_and_reorders_only_remaining_steps() -> None:
    request, decision = _c3_decision()
    plan = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    )
    assert decision.search is not None
    initial_step_by_tool = {step.tool_name: step for step in plan.steps}
    successful_step = plan.steps[0]
    next_candidate = min(
        (
            candidate
            for candidate in decision.search.candidates
            if candidate.candidate_id != plan.active_candidate_id
        ),
        key=lambda candidate: (candidate.total_cost, candidate.candidate_id),
    )

    switched = workshop_task_plan.switch_task_plan_candidate_once(
        plan,
        decision,
        successful_step_ids={successful_step.id},
    )

    expected_remaining_tools = [
        tool for tool in next_candidate.tool_path if tool != successful_step.tool_name
    ]
    assert [step.tool_name for step in switched.steps] == [
        successful_step.tool_name,
        *expected_remaining_tools,
    ]
    assert len({step.id for step in switched.steps}) == len(switched.steps)
    assert switched.steps[0].id == successful_step.id
    assert {step.tool_name: step.idempotency_key for step in switched.steps} == {
        tool: step.idempotency_key for tool, step in initial_step_by_tool.items()
    }
    for index, step in enumerate(switched.steps):
        expected_dependencies = [] if index == 0 else [switched.steps[index - 1].id]
        assert step.depends_on == expected_dependencies
        assert step.input_refs == (
            [] if not expected_dependencies else [f"{expected_dependencies[0]}.artifact"]
        )
    assert switched.initial_candidate_id == plan.initial_candidate_id
    assert switched.active_candidate_id == next_candidate.candidate_id
    assert switched.replanned_candidate_id == next_candidate.candidate_id
    assert switched.replans_used == 1
    assert [step.status for step in switched.steps] == ["done", "pending", "pending"]
    validate_task_plan(switched, decision)


def test_candidate_switch_is_bounded_once_and_rejects_forged_candidate_id() -> None:
    request, decision = _c3_decision()
    plan = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    )
    switched = workshop_task_plan.switch_task_plan_candidate_once(
        plan,
        decision,
        successful_step_ids={plan.steps[0].id},
    )

    with pytest.raises(TaskPlanPolicyError, match="already used"):
        workshop_task_plan.switch_task_plan_candidate_once(
            switched,
            decision,
            successful_step_ids={plan.steps[0].id},
        )

    forged = plan.model_copy(update={"active_candidate_id": "candidate_not_authorized"})
    with pytest.raises(TaskPlanPolicyError, match="schema"):
        validate_task_plan(forged, decision)

    assert decision.search is not None
    other_candidate_id = next(
        candidate.candidate_id
        for candidate in decision.search.candidates
        if candidate.candidate_id != plan.initial_candidate_id
    )
    structurally_forged = plan.model_copy(update={"active_candidate_id": other_candidate_id})
    with pytest.raises(TaskPlanPolicyError, match="unreplanned"):
        validate_task_plan(structurally_forged, decision)


@pytest.mark.parametrize(
    ("field", "invalid_value"),
    [
        ("max_tool_calls", 99),
        ("max_calls_per_tool", 3),
        ("max_parallel_agents", 4),
        ("max_planning_depth", 4),
        ("max_replans", 2),
    ],
)
def test_validate_task_plan_reconstructs_model_copy_scalar_constraints(
    field: str,
    invalid_value: int,
) -> None:
    request, decision = _c3_decision()
    plan = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    ).model_copy(update={field: invalid_value})

    with pytest.raises(TaskPlanPolicyError, match="schema"):
        validate_task_plan(plan, decision)


def test_task_plan_rejects_terminal_status_that_disagrees_with_steps() -> None:
    request, decision = _c3_decision()
    plan = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    )

    with pytest.raises(TaskPlanPolicyError, match="schema"):
        validate_task_plan(plan.model_copy(update={"status": "COMPLETED"}), decision)

    inconsistent_pending = plan.model_copy(
        update={
            "steps": [
                plan.steps[0].model_copy(update={"status": "done"}),
                *plan.steps[1:],
            ]
        }
    )
    with pytest.raises(TaskPlanPolicyError, match="schema"):
        validate_task_plan(inconsistent_pending, decision)


@pytest.mark.parametrize(
    ("field", "changed_value"),
    [
        ("max_tool_calls", 7),
        ("max_calls_per_tool", 1),
        ("max_parallel_agents", 2),
        ("max_planning_depth", 2),
        ("max_replans", 0),
    ],
)
def test_validate_task_plan_binds_every_runtime_budget_to_route_policy(
    field: str,
    changed_value: int,
) -> None:
    request, decision = _c3_decision()
    plan = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    ).model_copy(update={field: changed_value})

    with pytest.raises(TaskPlanPolicyError, match="budget"):
        validate_task_plan(plan, decision)


def test_validate_task_plan_rejects_empty_and_fourth_duplicate_write_step() -> None:
    request, decision = _c3_decision()
    plan = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    )

    with pytest.raises(TaskPlanPolicyError, match="schema"):
        validate_task_plan(plan.model_copy(update={"steps": []}), decision)

    duplicate_payload = plan.steps[0].model_dump(mode="python")
    duplicate_payload.update(
        {
            "id": "task-4-handout",
            "depends_on": [],
            "input_refs": [],
            "parallel_group": "ready-3",
            "idempotency_key": f"{decision.route_id}:task-4-handout",
        }
    )
    duplicate = PlanStep.model_validate(duplicate_payload)
    with pytest.raises(TaskPlanPolicyError, match="exactly once"):
        validate_task_plan(
            plan.model_copy(update={"steps": [*plan.steps, duplicate]}),
            decision,
        )


def test_validate_task_plan_rejects_wrong_replan_order_agent_and_idempotency_key() -> None:
    request, decision = _c3_decision()
    initial = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    )
    switched = workshop_task_plan.switch_task_plan_candidate_once(
        initial,
        decision,
        successful_step_ids={initial.steps[0].id},
    )

    wrong_order_source = [
        switched.steps[2],
        switched.steps[1],
        switched.steps[0],
    ]
    wrong_order: list[PlanStep] = []
    for index, step in enumerate(wrong_order_source):
        payload = step.model_dump(mode="python")
        dependency = [wrong_order[-1].id] if wrong_order else []
        payload.update(
            {
                "depends_on": dependency,
                "input_refs": [f"{dependency[0]}.artifact"] if dependency else [],
            }
        )
        wrong_order.append(PlanStep.model_validate(payload))
    with pytest.raises(TaskPlanPolicyError, match="candidate order"):
        validate_task_plan(
            switched.model_copy(update={"steps": wrong_order}),
            decision,
        )

    wrong_agent_payload = switched.steps[1].model_dump(mode="python")
    wrong_agent_payload["agent"] = "DebugAgent"
    wrong_agent = PlanStep.model_validate(wrong_agent_payload)
    with pytest.raises(TaskPlanPolicyError, match="candidate order|not allowed"):
        validate_task_plan(
            switched.model_copy(
                update={
                    "steps": [
                        switched.steps[0],
                        wrong_agent,
                        switched.steps[2],
                    ]
                }
            ),
            decision,
        )

    wrong_key_payload = switched.steps[0].model_dump(mode="python")
    wrong_key_payload["idempotency_key"] = "forged-publication-key"
    wrong_key = PlanStep.model_validate(wrong_key_payload)
    with pytest.raises(TaskPlanPolicyError, match="idempotency"):
        validate_task_plan(
            switched.model_copy(
                update={
                    "steps": [
                        wrong_key,
                        switched.steps[1],
                        switched.steps[2],
                    ]
                }
            ),
            decision,
        )


def test_c2_cannot_escalate_route_max_replans_from_zero() -> None:
    source = IntentRouteRequest(
        message="总结循环知识并安排学习路径",
        manual_kc_codes=["PY43-KC-031"],
    )
    decision = compile_route_decision(
        source,
        intent="HANDOUT",
        secondary_intent="PLAN",
        confidence=0.94,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
    )
    decision_payload = decision.model_dump(mode="python")
    decision_payload["policies"]["max_replans"] = 0
    zero_replan_decision = RouteDecision.model_validate(decision_payload)
    plan = compile_task_plan(
        zero_replan_decision,
        message=source.message,
        kc_codes=["PY43-KC-031"],
    ).model_copy(update={"max_replans": 1})

    with pytest.raises(TaskPlanPolicyError, match="max_replans budget"):
        validate_task_plan(plan, zero_replan_decision)


async def _run_c3_failure_case(
    *,
    recover_on_second_attempt: bool,
) -> tuple[
    object,
    list[str],
    list[tuple[str, dict]],
    object,
    object,
]:
    request, decision = _c3_decision()
    plan = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    )
    assert decision.search is not None
    selected = next(
        candidate
        for candidate in decision.search.candidates
        if candidate.candidate_id == decision.search.selected_candidate_id
    )
    next_candidate = min(
        (
            candidate
            for candidate in decision.search.candidates
            if candidate.candidate_id != selected.candidate_id
        ),
        key=lambda candidate: (candidate.total_cost, candidate.candidate_id),
    )
    failed_tool = selected.tool_path[1]
    calls: list[str] = []
    events: list[tuple[str, dict]] = []
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    async def flaky_dispatch(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        tool_name = step.tool_name or ""
        calls.append(tool_name)
        attempt = calls.count(tool_name)
        ok = tool_name != failed_tool or (recover_on_second_attempt and attempt == 2)
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=ok,
            summary=f"{tool_name}:attempt:{attempt}",
            artifact={"title": tool_name} if ok else {"kind": "dispatch_failure"},
        )

    graph._emit_cb = emit  # type: ignore[method-assign]  # noqa: SLF001
    graph._dispatch_step = flaky_dispatch  # type: ignore[method-assign]  # noqa: SLF001
    orchestrator_request = OrchestratorRequest(
        user_id=7,
        session_id="c3-runtime",
        user_message=request.message,
        recent_kc_codes=["PY43-KC-031"],
        route_decision=decision,
    )
    result = await graph._node_dispatch(  # noqa: SLF001
        {"req": orchestrator_request, "plan": plan.steps, "task_plan": plan}
    )
    return result, calls, events, selected, next_candidate


@pytest.mark.asyncio
async def test_c3_failure_switches_candidate_once_and_second_failure_is_partial() -> None:
    result, calls, events, selected, next_candidate = await _run_c3_failure_case(
        recover_on_second_attempt=False
    )
    successful_tool = selected.tool_path[0]
    failed_tool = selected.tool_path[1]

    # 冻结设计：切换预算耗尽后的第二次失败必须返回部分结果并阻塞依赖，
    # 切换后仍未成功的后续步骤不得再被派发。
    assert calls == [successful_tool, failed_tool, failed_tool]
    assert Counter(calls)[successful_tool] == 1
    assert Counter(calls)[failed_tool] == 2
    assert max(Counter(calls).values()) <= 2
    assert result["task_plan"].replans_used == 1
    assert result["task_plan"].initial_candidate_id == selected.candidate_id
    assert result["task_plan"].active_candidate_id == next_candidate.candidate_id
    assert result["task_plan"].replanned_candidate_id == next_candidate.candidate_id
    assert result["task_plan"].status == "PARTIAL"
    assert [step.status for step in result["task_plan"].steps] == [
        "done",
        "failed",
        "skipped",
    ]
    latest_by_tool = {
        step.tool_name: dispatch
        for step, dispatch in zip(
            result["task_plan"].steps,
            result["dispatch_results"],
            strict=True,
        )
    }
    assert latest_by_tool[failed_tool].ok is False
    blocked_tool = next(
        step.tool_name for step in result["task_plan"].steps if step.status == "skipped"
    )
    assert latest_by_tool[blocked_tool].note == "dependency_failed"
    replan_notes = [
        data["note"] for event, data in events if event == "trace" and data.get("node") == "replan"
    ]
    assert replan_notes == [
        f"candidate_switch:{selected.candidate_id}->{next_candidate.candidate_id}"
    ]
    persisted_attempts = [
        data["value"]["attempt"]
        for event, data in events
        if event == "CUSTOM"
        and data.get("name") == "task_step_result"
        and data["value"]["tool_name"] == failed_tool
    ]
    assert persisted_attempts == [1, 2]
    task_steps_by_tool = {step.tool_name: step for step in result["task_plan"].steps}
    publication_events = [
        data["value"]
        for event, data in events
        if event == "CUSTOM" and data.get("name") == "task_step_result"
    ]
    assert all(
        publication["idempotency_key"]
        == task_steps_by_tool[publication["tool_name"]].idempotency_key
        for publication in publication_events
    )
    successful_publications = [
        publication
        for publication in publication_events
        if publication["tool_name"] == successful_tool
    ]
    # Resource generation returns an artifact; this stable-key event is the publication boundary.
    assert len(successful_publications) == 1
    assert successful_publications[0]["artifact"]["title"] == successful_tool
    failed_publications = [
        publication for publication in publication_events if publication["tool_name"] == failed_tool
    ]
    assert [publication["attempt"] for publication in failed_publications] == [1, 2]
    assert len({publication["idempotency_key"] for publication in failed_publications}) == 1

    # 时间线保留两次真实尝试与阻断标记：候选切换后失败步骤按新候选 Agent 再试一次，
    # 其依赖步骤只留下未派发的阻断 trace，不再出现第三次工具调用。
    assert [trace.node for trace in result["trace"]] == [
        f"dispatch.{selected.agent_path[0]}",
        "evaluate",
        f"dispatch.{selected.agent_path[1]}",
        "evaluate",
        "replan",
        f"dispatch.{next_candidate.agent_path[0]}",
        "evaluate",
        f"dispatch.{next_candidate.agent_path[2]}",
    ]
    assert result["trace"][-1].status == "fail"
    assert result["trace"][-1].note == "前置步骤失败，本步骤未执行"
    replan_event_index = next(
        index
        for index, (event, data) in enumerate(events)
        if event == "trace" and data.get("node") == "replan"
    )
    assert any(event == "plan" for event, _ in events[replan_event_index + 1 :])
    assert any(
        event == "CUSTOM" and data.get("name") == "loop_plan"
        for event, data in events[replan_event_index + 1 :]
    )
    assert any(event == "STATE_DELTA" for event, _ in events[replan_event_index + 1 :])


@pytest.mark.asyncio
async def test_c3_candidate_switch_can_complete_after_later_success() -> None:
    result, calls, _, selected, next_candidate = await _run_c3_failure_case(
        recover_on_second_attempt=True
    )
    successful_tool = selected.tool_path[0]
    failed_tool = selected.tool_path[1]
    expected_remaining = [tool for tool in next_candidate.tool_path if tool != successful_tool]

    assert calls == [successful_tool, failed_tool, *expected_remaining]
    assert Counter(calls)[successful_tool] == 1
    assert Counter(calls)[failed_tool] == 2
    assert result["task_plan"].status == "COMPLETED"
    assert all(item.ok for item in result["dispatch_results"])
    assert all(step.status == "done" for step in result["task_plan"].steps)


@pytest.mark.asyncio
async def test_c3_restored_success_prefix_survives_later_candidate_switch() -> None:
    request, decision = _c3_decision()
    initial = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    )
    restored_payload = initial.model_dump(mode="python")
    restored_payload["status"] = "RUNNING"
    restored_payload["steps"][0]["status"] = "done"
    restored = initial.model_validate(restored_payload)
    validate_task_plan(restored, decision)
    assert decision.search is not None
    next_candidate = min(
        (
            candidate
            for candidate in decision.search.candidates
            if candidate.candidate_id != restored.active_candidate_id
        ),
        key=lambda candidate: (candidate.total_cost, candidate.candidate_id),
    )
    successful_tool = restored.steps[0].tool_name
    failed_tool = restored.steps[1].tool_name
    calls: list[str] = []
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)

    async def fail_then_recover(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        tool_name = step.tool_name or ""
        calls.append(tool_name)
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=tool_name != failed_tool or calls.count(tool_name) == 2,
            summary=tool_name,
            artifact={"title": tool_name},
        )

    graph._dispatch_step = fail_then_recover  # type: ignore[method-assign]  # noqa: SLF001
    result = await graph._node_dispatch(  # noqa: SLF001
        {
            "req": OrchestratorRequest(
                user_id=7,
                session_id="c3-restored-prefix",
                user_message=request.message,
                recent_kc_codes=["PY43-KC-031"],
                route_decision=decision,
            ),
            "plan": restored.steps,
            "task_plan": restored,
        }
    )

    expected_remaining = [tool for tool in next_candidate.tool_path if tool != successful_tool]
    assert calls == [failed_tool, *expected_remaining]
    assert successful_tool not in calls
    assert result["task_plan"].replans_used == 1
    assert result["task_plan"].status == "COMPLETED"
    assert all(step.status == "done" for step in result["task_plan"].steps)


@pytest.mark.asyncio
async def test_c3_second_failure_blocks_candidate_dependents_without_dispatch() -> None:
    # 高认知负荷 + 薄弱 KC：规划诊断优先被选中，且高负荷惩罚练习探测优先，
    # 因此切换目标是知识重构优先（HANDOUT → PRACTICE → PLAN），
    # HANDOUT 第二次失败后 PRACTICE 必须被依赖阻断而不是被派发。
    request, decision = _c3_decision(
        context=LearnerRoutingContext(weak_kcs=["PY43-KC-031"], cognitive_load="HIGH")
    )
    plan = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    )
    assert [step.tool_name for step in plan.steps][:2] == ["PLAN", "HANDOUT"]
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    calls: list[str] = []
    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    async def failing_handout(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        tool_name = step.tool_name or ""
        calls.append(tool_name)
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=tool_name != "HANDOUT",
            summary=tool_name,
            artifact={"title": tool_name},
        )

    graph._emit_cb = emit  # type: ignore[method-assign]  # noqa: SLF001
    graph._dispatch_step = failing_handout  # type: ignore[method-assign]  # noqa: SLF001
    result = await graph._node_dispatch(  # noqa: SLF001
        {
            "req": OrchestratorRequest(
                user_id=7,
                session_id="c3-blocked",
                user_message=request.message,
                recent_kc_codes=["PY43-KC-031"],
                route_decision=decision,
                learner_context=request.learner_context,
            ),
            "plan": plan.steps,
            "task_plan": plan,
        }
    )

    assert calls == ["PLAN", "HANDOUT", "HANDOUT"]
    assert result["task_plan"].status == "PARTIAL"
    blocked = next(
        item
        for step, item in zip(
            result["task_plan"].steps,
            result["dispatch_results"],
            strict=True,
        )
        if step.tool_name == "PRACTICE"
    )
    assert blocked.note == "dependency_failed"
    statuses = {step.tool_name: step.status for step in result["task_plan"].steps}
    assert statuses == {
        "PLAN": "done",
        "HANDOUT": "failed",
        "PRACTICE": "skipped",
    }
    blocked_loop_events = [
        data["value"]
        for event, data in events
        if event == "CUSTOM"
        and data.get("name") == "loop_step"
        and data["value"].get("toolName") == "PRACTICE"
    ]
    assert blocked_loop_events[-1]["status"] == "failed"
    assert blocked_loop_events[-1]["error"] == "dependency_failed"


@pytest.mark.asyncio
async def test_consumed_c3_switch_budget_returns_partial_without_another_switch() -> None:
    request, decision = _c3_decision()
    initial = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    )
    consumed = workshop_task_plan.switch_task_plan_candidate_once(
        initial,
        decision,
        successful_step_ids={initial.steps[0].id},
    )
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    calls: list[str] = []
    failed_tool = consumed.steps[1].tool_name

    async def fail_after_success(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        tool_name = step.tool_name or ""
        calls.append(tool_name)
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=tool_name != failed_tool,
            summary=tool_name,
            artifact={"title": tool_name},
        )

    graph._dispatch_step = fail_after_success  # type: ignore[method-assign]  # noqa: SLF001
    result = await graph._node_dispatch(  # noqa: SLF001
        {
            "req": OrchestratorRequest(
                user_id=7,
                session_id="c3-consumed",
                user_message=request.message,
                recent_kc_codes=["PY43-KC-031"],
                route_decision=decision,
            ),
            "plan": consumed.steps,
            "task_plan": consumed,
        }
    )

    assert calls == [consumed.steps[1].tool_name]
    assert result["task_plan"].replans_used == 1
    assert result["task_plan"].status == "PARTIAL"
    assert [step.status for step in result["task_plan"].steps] == [
        "done",
        "failed",
        "skipped",
    ]
    assert all(trace.node != "replan" for trace in result["trace"])
