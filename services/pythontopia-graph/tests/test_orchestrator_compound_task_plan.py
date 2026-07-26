"""Phase 2 OrchestratorGraph must execute the authoritative TaskPlan DAG."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.routing.workshop_policy import compile_route_decision
from pythontopia_graph.routing.workshop_task_plan import TaskPlanPolicyError
from pythontopia_graph.schemas.intent import IntentRouteRequest
from pythontopia_graph.schemas.orchestrator import DispatchResult, OrchestratorRequest


class NoModelProvider(LlmProvider):
    async def chat(self, messages, **kwargs):  # type: ignore[override]
        raise AssertionError("authoritative compound route must not be decomposed by the model")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield "已完成复合学习任务。"


def _request(primary: str, secondary: str, session: str) -> OrchestratorRequest:
    source = IntentRouteRequest(
        message=f"{primary} then {secondary}",
        manual_kc_codes=["PY43-KC-012"],
    )
    decision = compile_route_decision(
        source,
        intent=primary,
        secondary_intent=secondary,
        confidence=0.94,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
    )
    return OrchestratorRequest(
        user_id=7,
        session_id=session,
        user_message=source.message,
        recent_kc_codes=["PY43-KC-012"],
        course_kc_index=["PY43-KC-012"],
        route_decision=decision,
    )


@pytest.mark.asyncio
async def test_supervisor_builds_exact_two_step_plan_without_model_redecomposition() -> None:
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    req = _request("HANDOUT", "PRACTICE", "compound-build")

    understood = await graph._node_understand({"req": req})  # noqa: SLF001
    built = await graph._node_build_plan(  # noqa: SLF001
        {"req": req, "intent": understood["intent"], "goal": understood["goal"]}
    )

    assert understood["intent"] == "compound"
    assert built["task_plan"].topology == "SEQUENTIAL"
    assert [step.tool_name for step in built["plan"]] == ["HANDOUT", "PRACTICE"]
    assert built["plan"][1].depends_on == [built["plan"][0].id]
    assert built["task_plan"].initial_candidate_id is None
    assert built["task_plan"].active_candidate_id is None
    assert built["task_plan"].replanned_candidate_id is None


@pytest.mark.asyncio
async def test_dispatch_ignores_forged_fix_bug_state_plan_and_executes_authoritative_plan() -> None:
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    req = _request("HANDOUT", "PRACTICE", "compound-forged-state-plan")
    built = await graph._node_build_plan(  # noqa: SLF001
        {"req": req, "intent": "compound", "goal": req.user_message}
    )
    forged_step = built["plan"][0].model_copy(
        update={
            "id": "forged-fix-bug",
            "agent": "DebugAgent",
            "tool_name": "FIX_BUG",
            "resource_type": None,
            "depends_on": [],
            "input_refs": [],
            "idempotency_key": f"{req.route_decision.route_id}:forged-fix-bug",
        }
    )
    calls: list[str] = []
    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    async def fake_dispatch(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        calls.append(step.tool_name or "")
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=True,
            summary=step.tool_name or "",
            artifact={"title": step.tool_name},
        )

    graph._emit_cb = emit  # type: ignore[method-assign]  # noqa: SLF001
    graph._dispatch_step = fake_dispatch  # type: ignore[method-assign]  # noqa: SLF001
    result = await graph._node_dispatch(  # noqa: SLF001
        {
            "req": req,
            "plan": [forged_step],
            "task_plan": built["task_plan"],
        }
    )

    assert calls == ["HANDOUT", "PRACTICE"]
    assert [step.tool_name for step in result["task_plan"].steps] == calls
    assert all(
        node["node"] != "dispatch.DebugAgent"
        for event, data in events
        if event == "plan"
        for node in data["nodes"]
    )


@pytest.mark.asyncio
async def test_dispatch_rejects_invalid_task_plan_before_any_event_or_side_effect() -> None:
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    req = _request("HANDOUT", "PRACTICE", "compound-invalid-preflight")
    built = await graph._node_build_plan(  # noqa: SLF001
        {"req": req, "intent": "compound", "goal": req.user_message}
    )
    forged_task_plan = built["task_plan"].model_copy(
        update={"max_tool_calls": 99}
    )
    events: list[tuple[str, dict]] = []
    calls: list[str] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    async def fake_dispatch(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        calls.append(step.tool_name or "")
        raise AssertionError("invalid TaskPlan must fail before dispatch")

    graph._emit_cb = emit  # type: ignore[method-assign]  # noqa: SLF001
    graph._dispatch_step = fake_dispatch  # type: ignore[method-assign]  # noqa: SLF001
    with pytest.raises(TaskPlanPolicyError, match="schema"):
        await graph._node_dispatch(  # noqa: SLF001
            {
                "req": req,
                "plan": built["plan"],
                "task_plan": forged_task_plan,
            }
        )

    assert events == []
    assert calls == []


@pytest.mark.asyncio
async def test_dispatch_requires_route_decision_before_any_task_plan_event() -> None:
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    req = _request("HANDOUT", "PRACTICE", "compound-missing-route")
    built = await graph._node_build_plan(  # noqa: SLF001
        {"req": req, "intent": "compound", "goal": req.user_message}
    )
    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    graph._emit_cb = emit  # type: ignore[method-assign]  # noqa: SLF001
    with pytest.raises(TaskPlanPolicyError, match="RouteDecision"):
        await graph._node_dispatch(  # noqa: SLF001
            {
                "req": req.model_copy(update={"route_decision": None}),
                "plan": built["plan"],
                "task_plan": built["task_plan"],
            }
        )

    assert events == []


@pytest.mark.asyncio
async def test_compound_plan_falls_back_to_authoritative_course_index_for_kc_anchor() -> None:
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    req = _request("HANDOUT", "PRACTICE", "compound-course-kc").model_copy(
        update={
            "recent_kc_codes": [],
            "decaying_kcs": [],
            "course_kc_index": ["PY43-KC-031"],
        }
    )

    built = await graph._node_build_plan(  # noqa: SLF001
        {"req": req, "intent": "compound", "goal": req.user_message}
    )

    assert [step.kc_codes for step in built["plan"]] == [
        ["PY43-KC-031"],
        ["PY43-KC-031"],
    ]


@pytest.mark.asyncio
async def test_parallel_executor_runs_ready_steps_concurrently_with_stable_result_order() -> None:
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    req = _request("MINDMAP", "PRACTICE", "compound-parallel")
    understood = await graph._node_understand({"req": req})  # noqa: SLF001
    built = await graph._node_build_plan(  # noqa: SLF001
        {"req": req, "intent": understood["intent"], "goal": understood["goal"]}
    )
    active = 0
    max_active = 0

    async def fake_dispatch(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        nonlocal active, max_active
        active += 1
        max_active = max(max_active, active)
        await asyncio.sleep(0.02 if step.tool_name == "MINDMAP" else 0.005)
        active -= 1
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=True,
            summary=f"done:{step.tool_name}",
            artifact={"title": step.tool_name},
        )

    graph._dispatch_step = fake_dispatch  # type: ignore[method-assign]  # noqa: SLF001
    result = await graph._node_dispatch(  # noqa: SLF001
        {"req": req, "plan": built["plan"], "task_plan": built["task_plan"]}
    )

    assert max_active == 2
    assert [item.step_id for item in result["dispatch_results"]] == [
        step.id for step in built["plan"]
    ]


@pytest.mark.asyncio
async def test_sequential_executor_passes_predecessor_artifact_to_dependent_step() -> None:
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    req = _request("HANDOUT", "PRACTICE", "compound-sequential")
    understood = await graph._node_understand({"req": req})  # noqa: SLF001
    built = await graph._node_build_plan(  # noqa: SLF001
        {"req": req, "intent": understood["intent"], "goal": understood["goal"]}
    )
    calls: list[tuple[str, str]] = []

    async def fake_dispatch(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        calls.append((step.tool_name or "", focus_hint_extra))
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=True,
            summary=f"done:{step.tool_name}",
            artifact={"title": "循环总结", "summary": "边界和次数"},
        )

    graph._dispatch_step = fake_dispatch  # type: ignore[method-assign]  # noqa: SLF001
    await graph._node_dispatch(  # noqa: SLF001
        {"req": req, "plan": built["plan"], "task_plan": built["task_plan"]}
    )

    assert calls[0] == ("HANDOUT", "")
    assert calls[1][0] == "PRACTICE"
    assert "循环总结" in calls[1][1]


@pytest.mark.asyncio
async def test_sequential_step_result_is_emitted_before_dependent_step_starts() -> None:
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    req = _request("HANDOUT", "PRACTICE", "compound-persist-order")
    understood = await graph._node_understand({"req": req})  # noqa: SLF001
    built = await graph._node_build_plan(  # noqa: SLF001
        {"req": req, "intent": understood["intent"], "goal": understood["goal"]}
    )
    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    async def fake_dispatch(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=True,
            summary=f"done:{step.tool_name}",
            artifact={"title": step.tool_name},
        )

    graph._emit_cb = emit  # type: ignore[method-assign]  # noqa: SLF001
    graph._dispatch_step = fake_dispatch  # type: ignore[method-assign]  # noqa: SLF001
    await graph._node_dispatch(  # noqa: SLF001
        {"req": req, "plan": built["plan"], "task_plan": built["task_plan"]}
    )

    first_id, second_id = [step.id for step in built["plan"]]
    persisted_index = next(
        index
        for index, (event, data) in enumerate(events)
        if event == "CUSTOM"
        and data.get("name") == "task_step_result"
        and data["value"]["step_id"] == first_id
    )
    dependent_start_index = next(
        index
        for index, (event, data) in enumerate(events)
        if event == "trace" and data.get("step_id") == second_id and data.get("phase") == "start"
    )
    assert persisted_index < dependent_start_index


@pytest.mark.asyncio
async def test_signed_task_plan_skips_legacy_resource_downgrade_and_debug_replan() -> None:
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    req = _request("HANDOUT", "PRACTICE", "compound-no-authority-expansion")
    req = req.model_copy(update={"recent_failures": ["PY43-KC-012"]})
    understood = await graph._node_understand({"req": req})  # noqa: SLF001
    built = await graph._node_build_plan(  # noqa: SLF001
        {"req": req, "intent": understood["intent"], "goal": understood["goal"]}
    )
    failed = DispatchResult(
        step_id=built["plan"][0].id,
        agent=built["plan"][0].agent,
        ok=False,
        summary="provider down",
        artifact={"kind": "dispatch_failure"},
    )

    reflected = await graph._node_reflect(  # noqa: SLF001
        {
            "req": req,
            "plan": built["plan"],
            "task_plan": built["task_plan"],
            "dispatch_results": [failed],
        }
    )
    next_node = graph._route_after_dispatch(  # noqa: SLF001
        {
            "req": req,
            "intent": "compound",
            "allowed_set": {"PY43-KC-012"},
            "plan": built["plan"],
            "task_plan": built["task_plan"],
        }
    )

    assert reflected == {}
    assert next_node == "extract_profile"
    assert "READING" in req.route_decision.tools.forbidden
    assert "FIX_BUG" in req.route_decision.tools.forbidden


@pytest.mark.asyncio
async def test_planner_executor_evaluates_and_replans_exact_failed_step_once() -> None:
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    req = _request("HANDOUT", "PLAN", "compound-planner-executor")
    understood = await graph._node_understand({"req": req})  # noqa: SLF001
    built = await graph._node_build_plan(  # noqa: SLF001
        {"req": req, "intent": understood["intent"], "goal": understood["goal"]}
    )
    calls: list[tuple[str, str]] = []
    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    async def flaky_dispatch(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        calls.append((step.agent, step.tool_name or ""))
        attempt = sum(1 for _, tool in calls if tool == step.tool_name)
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=not (step.tool_name == "HANDOUT" and attempt == 1),
            summary=f"attempt:{attempt}",
            artifact={"title": step.tool_name},
        )

    graph._emit_cb = emit  # type: ignore[method-assign]  # noqa: SLF001
    graph._dispatch_step = flaky_dispatch  # type: ignore[method-assign]  # noqa: SLF001
    result = await graph._node_dispatch(  # noqa: SLF001
        {"req": req, "plan": built["plan"], "task_plan": built["task_plan"]}
    )

    assert calls == [
        ("PathPlannerAgent", "PLAN"),
        ("HandoutAgent", "HANDOUT"),
        ("HandoutAgent", "HANDOUT"),
    ]
    assert result["task_plan"].replans_used == 1
    assert result["task_plan"].initial_candidate_id is None
    assert result["task_plan"].active_candidate_id is None
    assert result["task_plan"].replanned_candidate_id is None
    assert all(item.ok for item in result["dispatch_results"])
    assert all(step.status == "done" for step in result["task_plan"].steps)
    assert any(step.node == "evaluate" for step in result["trace"])
    assert any(step.node == "replan" for step in result["trace"])
    handout_attempts = [
        data["value"]["attempt"]
        for event, data in events
        if event == "CUSTOM"
        and data.get("name") == "task_step_result"
        and data["value"]["tool_name"] == "HANDOUT"
    ]
    assert handout_attempts == [1, 2]
    handout_keys = [
        data["value"]["idempotency_key"]
        for event, data in events
        if event == "CUSTOM"
        and data.get("name") == "task_step_result"
        and data["value"]["tool_name"] == "HANDOUT"
    ]
    handout_step = next(
        step for step in result["task_plan"].steps if step.tool_name == "HANDOUT"
    )
    assert handout_keys == [
        handout_step.idempotency_key,
        handout_step.idempotency_key,
    ]
    assert [step.node for step in result["trace"]] == [
        "dispatch.PathPlannerAgent",
        "evaluate",
        "dispatch.HandoutAgent",
        "evaluate",
        "replan",
        "dispatch.HandoutAgent",
    ]


@pytest.mark.asyncio
async def test_consumed_c2_replan_budget_returns_partial_without_retry() -> None:
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    req = _request("HANDOUT", "PLAN", "compound-consumed-c2-replan")
    built = await graph._node_build_plan(  # noqa: SLF001
        {"req": req, "intent": "compound", "goal": req.user_message}
    )
    consumed = built["task_plan"].model_copy(update={"replans_used": 1})
    calls: list[str] = []

    async def fail_handout(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        tool_name = step.tool_name or ""
        calls.append(tool_name)
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=tool_name != "HANDOUT",
            summary=tool_name,
            artifact={"title": tool_name},
        )

    graph._dispatch_step = fail_handout  # type: ignore[method-assign]  # noqa: SLF001
    result = await graph._node_dispatch(  # noqa: SLF001
        {
            "req": req,
            "plan": consumed.steps,
            "task_plan": consumed,
        }
    )

    assert calls == ["PLAN", "HANDOUT"]
    assert result["task_plan"].replans_used == 1
    assert result["task_plan"].status == "PARTIAL"
    assert [step.status for step in result["task_plan"].steps] == [
        "done",
        "failed",
    ]
    assert all(trace.node != "replan" for trace in result["trace"])
