"""Phase 4 Task 4：百晓路由运行指标（复用现有 harness stats 端点）。"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest
from fastapi.testclient import TestClient

from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.main import app
from pythontopia_graph.routing.workshop_metrics import (
    record_execution,
    record_route,
    reset_routing_metrics,
    routing_metrics_snapshot,
)
from pythontopia_graph.routing.workshop_policy import compile_route_decision
from pythontopia_graph.routing.workshop_task_plan import compile_task_plan
from pythontopia_graph.schemas.intent import IntentRouteRequest
from pythontopia_graph.schemas.orchestrator import DispatchResult, OrchestratorRequest


@pytest.fixture(autouse=True)
def _isolated_metrics():
    reset_routing_metrics()
    yield
    reset_routing_metrics()


class NoModelProvider(LlmProvider):
    async def chat(self, messages, **kwargs):  # type: ignore[override]
        raise AssertionError("authoritative route must not call the model")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


class StubProvider(LlmProvider):
    def __init__(self, payload: dict | None = None) -> None:
        self._payload = payload

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_json=self._payload)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


def _c3_decision():
    request = IntentRouteRequest(
        message=(
            "我循环这一章学得很乱，先给我总结一下，再根据我以前犯的错出几道题，"
            "并告诉我先做哪一道。"
        ),
        manual_kc_codes=["PY43-KC-031"],
    )
    return request, compile_route_decision(
        request,
        intent="HANDOUT",
        secondary_intent="PRACTICE",
        confidence=0.94,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
        resolved_kc_codes=["PY43-KC-031"],
    )


def _c2_decision():
    request = IntentRouteRequest(
        message="总结循环知识并安排学习路径",
        manual_kc_codes=["PY43-KC-031"],
    )
    return request, compile_route_decision(
        request,
        intent="HANDOUT",
        secondary_intent="PLAN",
        confidence=0.94,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
    )


def test_snapshot_starts_at_zero_and_reset_clears_recorded_state() -> None:
    snapshot = routing_metrics_snapshot()

    assert snapshot["routes"]["total"] == 0
    assert snapshot["routes"]["clarifications"] == 0
    assert snapshot["routes"]["c3WithSearch"] == 0
    assert snapshot["routes"]["byComplexity"] == {}
    assert snapshot["routes"]["selectedCandidates"] == {}
    assert snapshot["routes"]["latencyMs"] == {"p50": 0, "p95": 0}
    assert snapshot["routes"]["clarificationRate"] == 0.0
    assert snapshot["routes"]["c3Rate"] == 0.0
    assert snapshot["executions"]["total"] == 0
    assert snapshot["executions"]["byStatus"] == {}
    assert snapshot["executions"]["candidateSwitches"] == 0
    assert snapshot["executions"]["latencyMs"] == {"p50": 0, "p95": 0}

    _, decision = _c2_decision()
    record_route(decision, latency_ms=12)
    reset_routing_metrics()

    assert routing_metrics_snapshot()["routes"]["total"] == 0


def test_record_route_counts_complexity_clarification_c3_and_selection() -> None:
    _, c2 = _c2_decision()
    _, c3 = _c3_decision()
    assert c3.search is not None
    clarify_request = IntentRouteRequest(message="帮我修一下我的报错")
    clarification = compile_route_decision(
        clarify_request,
        intent="FIX_BUG",
        secondary_intent=None,
        confidence=0.94,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="debug",
    )
    assert clarification.task.needs_clarification is True

    record_route(c2, latency_ms=10)
    record_route(c3, latency_ms=30)
    record_route(clarification, latency_ms=20)

    snapshot = routing_metrics_snapshot()
    routes = snapshot["routes"]
    assert routes["total"] == 3
    assert routes["clarifications"] == 1
    assert routes["byComplexity"]["C2"] == 1
    assert routes["byComplexity"]["C3"] == 1
    assert routes["c3WithSearch"] == 1
    assert routes["selectedCandidates"] == {c3.search.selected_candidate_id: 1}
    assert routes["clarificationRate"] == pytest.approx(1 / 3, abs=1e-4)
    assert routes["c3Rate"] == pytest.approx(1 / 3, abs=1e-4)
    assert routes["latencyMs"]["p50"] == 20
    assert routes["latencyMs"]["p95"] == 30


def test_execution_percentiles_use_nearest_rank_over_recorded_window() -> None:
    for latency in (100, 200, 300, 400, 500, 600, 700, 800, 900, 1000):
        record_execution(
            status="COMPLETED",
            dispatched_steps=2,
            blocked_steps=0,
            candidate_switches=0,
            same_step_retries=0,
            latency_ms=latency,
        )

    snapshot = routing_metrics_snapshot()["executions"]
    assert snapshot["total"] == 10
    assert snapshot["byStatus"] == {"COMPLETED": 10}
    assert snapshot["agentDispatches"] == 20
    assert snapshot["latencyMs"]["p50"] == 500
    assert snapshot["latencyMs"]["p95"] == 1000


@pytest.mark.asyncio
async def test_c3_dispatch_with_candidate_switch_records_execution_metrics() -> None:
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
    failed_tool = selected.tool_path[1]
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)

    async def flaky_dispatch(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        tool_name = step.tool_name or ""
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=tool_name != failed_tool,
            summary=tool_name,
            artifact={"title": tool_name},
        )

    graph._dispatch_step = flaky_dispatch  # type: ignore[method-assign]  # noqa: SLF001
    result = await graph._node_dispatch(  # noqa: SLF001
        {
            "req": OrchestratorRequest(
                user_id=7,
                session_id="metrics-c3",
                user_message=request.message,
                recent_kc_codes=["PY43-KC-031"],
                route_decision=decision,
            ),
            "plan": plan.steps,
            "task_plan": plan,
        }
    )

    assert result["task_plan"].status == "PARTIAL"
    snapshot = routing_metrics_snapshot()["executions"]
    assert snapshot["total"] == 1
    assert snapshot["byStatus"] == {"PARTIAL": 1}
    assert snapshot["candidateSwitches"] == 1
    assert snapshot["sameStepRetries"] == 0
    # PRACTICE 成功 + 失败工具两次真实尝试 = 3 次派发；PLAN 被依赖阻断不计入派发。
    assert snapshot["agentDispatches"] == 3
    assert snapshot["blockedSteps"] == 1
    assert snapshot["partialRate"] == pytest.approx(1.0)
    assert snapshot["switchRate"] == pytest.approx(1.0)
    # 单样本时 p50 与 p95 都必须等于该样本本身，证明延迟确实被记录。
    assert snapshot["latencyMs"]["p50"] == snapshot["latencyMs"]["p95"]


def test_intent_route_http_records_metrics_and_harness_stats_exposes_routing() -> None:
    app.state.llm_provider = StubProvider(
        {"intent": "MINDMAP", "kc_codes": ["PY43-KC-001"], "confidence": 0.8}
    )
    try:
        client = TestClient(app)

        route_response = client.post(
            "/agents/intent/route",
            json={
                "message": "帮我做个 for 循环的思维导图",
                "course_kcs": [{"code": "PY43-KC-001", "name": "for 循环"}],
            },
        )
        assert route_response.status_code == 200

        stats_response = client.get("/agents/harness/stats")
        assert stats_response.status_code == 200
        payload = stats_response.json()
        # 既有 harness 快照字段保持在线，routing 为新增兄弟字段而非替换。
        assert "tools" in payload
        assert "breakers" in payload
        routing = payload["routing"]
        assert routing["routes"]["total"] == 1
        assert routing["routes"]["byComplexity"]
        assert routing["executions"]["total"] == 0
    finally:
        del app.state.llm_provider


@pytest.mark.asyncio
async def test_c2_same_step_retry_records_same_step_retry_metric() -> None:
    request, decision = _c2_decision()
    assert decision.orchestration.topology == "PLANNER_EXECUTOR"
    plan = compile_task_plan(
        decision,
        message=request.message,
        kc_codes=["PY43-KC-031"],
    )
    graph = OrchestratorGraph(provider=NoModelProvider(), checkpointer=None)
    calls: list[str] = []

    async def flaky_handout(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        tool_name = step.tool_name or ""
        calls.append(tool_name)
        attempt = calls.count(tool_name)
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=not (tool_name == "HANDOUT" and attempt == 1),
            summary=tool_name,
            artifact={"title": tool_name},
        )

    graph._dispatch_step = flaky_handout  # type: ignore[method-assign]  # noqa: SLF001
    result = await graph._node_dispatch(  # noqa: SLF001
        {
            "req": OrchestratorRequest(
                user_id=7,
                session_id="metrics-c2-retry",
                user_message=request.message,
                recent_kc_codes=["PY43-KC-031"],
                route_decision=decision,
            ),
            "plan": plan.steps,
            "task_plan": plan,
        }
    )

    assert result["task_plan"].status == "COMPLETED"
    snapshot = routing_metrics_snapshot()["executions"]
    assert snapshot["total"] == 1
    assert snapshot["byStatus"] == {"COMPLETED": 1}
    assert snapshot["sameStepRetries"] == 1
    assert snapshot["candidateSwitches"] == 0
    assert snapshot["agentDispatches"] == len(calls) == 3
