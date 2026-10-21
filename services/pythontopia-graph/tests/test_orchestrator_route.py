"""OrchestratorAgent HTTP 与 SSE 路由测试。

注入 ``app.state.llm_provider`` 的 StubProvider 以避免真实 LLM；TestClient 不用 ``with``，
以免 lifespan 经 ``provider_from_env`` 覆盖 ``llm_provider``。
"""

from __future__ import annotations

import json
from collections.abc import AsyncIterator

import pytest

from fastapi.testclient import TestClient

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.main import app
from pythontopia_graph.routes import orchestrator as orchestrator_route
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher
from pythontopia_graph.routing.workshop_policy import compile_route_decision
from pythontopia_graph.schemas.intent import IntentRouteRequest


class StubProvider(LlmProvider):
    def __init__(self, payloads: list[dict | None] | None = None, reply: str = "规划完成，开始吧。") -> None:
        self._payloads = list(payloads or [])
        self._reply = reply

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        payload = self._payloads.pop(0) if self._payloads else None
        return LlmResponse(content_json=payload)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        for index in range(0, len(self._reply), 5):
            yield self._reply[index : index + 5]


def _sse_events(body: str) -> list[tuple[str, dict]]:
    events = []
    for block in body.split("\n\n"):
        lines = block.splitlines()
        name = next((line[7:] for line in lines if line.startswith("event: ")), None)
        data = [line[6:] for line in lines if line.startswith("data: ")]
        if name and data:
            events.append((name, json.loads("\n".join(data))))
    return events


@pytest.mark.asyncio
async def test_tool_loop_stream_error_does_not_return_exception_text(monkeypatch) -> None:
    class BrokenOrchestrator:
        async def run(self, **kwargs):
            raise RuntimeError("tool-loop-secret-sentinel")

    monkeypatch.setattr(orchestrator_route, "_build_hybrid", lambda *args: BrokenOrchestrator())
    body = {
        "user_id": 1,
        "session_id": "tool-loop-secret",
        "user_message": "private question",
        "course_kc_index": ["PY43-KC-001"],
    }
    request = orchestrator_route.OrchestratorRequest(**body)
    chunks = [
        chunk
        async for chunk in orchestrator_route._tool_loop_stream_generator(
            request, StubProvider(), StaticEvidenceFetcher([]), {"PY43-KC-001"}
        )
    ]
    combined = "".join(chunks)

    assert "TOOL_LOOP_FAILED" in combined
    assert "The tool workflow failed." in combined
    assert "tool-loop-secret-sentinel" not in combined


def test_orchestrator_turn_returns_200_and_response_shape() -> None:
    app.state.llm_provider = StubProvider([], reply="我们先看再学。")
    client = TestClient(app, headers={"X-Internal-Subject": "user:1"})
    body = {
        "user_id": 1,
        "session_id": "route-turn",
        "user_message": "我想学 for 循环",
        "course_kc_index": ["PY43-KC-001", "PY43-KC-003"],
    }

    response = client.post("/agents/orchestrator/turn", json=body)

    assert response.status_code == 200
    data = response.json()
    assert isinstance(data["reply_md"], str) and data["reply_md"]
    assert isinstance(data["plan"], list) and len(data["plan"]) >= 4
    assert [step["phase"] for step in data["plan"][:4]] == [
        "watch",
        "learn",
        "practice",
        "assess",
    ]
    assert data["needs_user_input"] is False
    assert "safety_result" in data and data["safety_result"]["passed"] is True
    assert "grounding_result" in data
    assert isinstance(data["trace"], list) and len(data["trace"]) > 0
    assert isinstance(data["dispatch_results"], list) and len(data["dispatch_results"]) >= 4

    del app.state.llm_provider


def test_orchestrator_stream_emits_canonical_agui_events() -> None:
    app.state.llm_provider = StubProvider([], reply="本轮安排已就绪。")
    client = TestClient(app, headers={"X-Internal-Subject": "user:1"})
    body = {
        "user_id": 1,
        "session_id": "route-stream",
        "user_message": "帮我安排循环的学习",
        "course_kc_index": ["PY43-KC-001"],
    }

    response = client.post("/agents/orchestrator/turn/stream", json=body)

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    text = response.text
    assert "event: RUN_STARTED" in text
    assert "event: STEP_STARTED" in text
    assert "event: STEP_FINISHED" in text
    assert "event: TEXT_MESSAGE_CONTENT" in text
    assert "event: RUN_FINISHED" in text
    assert "event: RUN_ERROR" not in text
    assert "event: trace" not in text
    assert "event: token" not in text
    assert "event: result" not in text

    events = _sse_events(text)
    started = next(data for name, data in events if name == "RUN_STARTED")
    finished = next(data for name, data in events if name == "RUN_FINISHED")
    assert started["threadId"] == "route-stream"
    assert started["runId"].startswith("orchestrator:")
    assert started["runId"] != "orchestrator:route-stream"
    assert finished["threadId"] == started["threadId"]
    assert finished["runId"] == started["runId"]

    del app.state.llm_provider


def test_orchestrator_stream_uses_unique_run_id_per_request() -> None:
    app.state.llm_provider = StubProvider([], reply="本轮安排已就绪。")
    client = TestClient(app, headers={"X-Internal-Subject": "user:1"})
    body = {
        "user_id": 1,
        "session_id": "route-run-identity",
        "user_message": "帮我安排循环的学习",
        "course_kc_index": ["PY43-KC-001"],
    }

    first = client.post("/agents/orchestrator/turn/stream", json=body)
    second = client.post("/agents/orchestrator/turn/stream", json=body)

    assert first.status_code == 200
    assert second.status_code == 200
    starts = [
        next(data for name, data in _sse_events(response.text) if name == "RUN_STARTED")
        for response in (first, second)
    ]
    assert [event["threadId"] for event in starts] == [
        "route-run-identity",
        "route-run-identity",
    ]
    assert starts[0]["runId"] != starts[1]["runId"]

    del app.state.llm_provider


def test_orchestrator_stream_emits_state_delta_plan_progress() -> None:
    """dispatch 经 AG-UI STATE_DELTA 实时推送 plan 步骤进度（真实产出，不造假）。"""
    app.state.llm_provider = StubProvider([], reply="进度同步演示。")
    client = TestClient(app, headers={"X-Internal-Subject": "user:1"})
    body = {
        "user_id": 1,
        "session_id": "route-state-delta",
        "user_message": "我想学 for 循环",
        "course_kc_index": ["PY43-KC-001", "PY43-KC-003"],
    }

    response = client.post("/agents/orchestrator/turn/stream", json=body)

    assert response.status_code == 200
    text = response.text
    state_deltas = [
        json.loads(block.split("data: ", maxsplit=1)[1])["delta"]
        for block in text.split("\n\n")
        if block.startswith("event: STATE_DELTA\n")
    ]
    assert state_deltas
    assert all(isinstance(delta, list) for delta in state_deltas)
    assert all(
        operation["op"] == "add" and operation["path"] == "/plan_steps"
        for delta in state_deltas
        for operation in delta
    )
    plan_steps = [operation["value"] for delta in state_deltas for operation in delta]
    # 开场全 pending 快照 + 每步真实推进到 active（done/failed 视 dispatch 结果）。
    assert any(step["status"] == "pending" for steps in plan_steps for step in steps)
    assert any(step["status"] == "active" for steps in plan_steps for step in steps)

    del app.state.llm_provider


def test_orchestrator_followup_reuses_stored_plan_without_dispatch() -> None:
    """已有计划后的“第一步/下一步”追问应快速复用 plan，不再重跑子 agent。"""
    session_id = "route-followup-fast"
    app.state.llm_provider = StubProvider([], reply="先完成第一步的视频预习。")
    client = TestClient(app, headers={"X-Internal-Subject": "user:1"})
    first = {
        "user_id": 1,
        "session_id": session_id,
        "user_message": "我想学 for 循环",
        "course_kc_index": ["PY43-KC-001", "PY43-KC-003"],
    }

    first_response = client.post("/agents/orchestrator/turn", json=first)
    assert first_response.status_code == 200
    first_body = first_response.json()
    assert len(first_body["plan"]) >= 4

    followup = {
        "user_id": 1,
        "session_id": session_id,
        "user_message": "那我现在第一步具体做什么？",
        "history": [
            {"role": "user", "content": first["user_message"]},
            {"role": "assistant", "content": first_body["reply_md"]},
        ],
        "course_kc_index": ["PY43-KC-001", "PY43-KC-003"],
    }

    response = client.post("/agents/orchestrator/turn/stream", json=followup)

    assert response.status_code == 200
    text = response.text
    assert "event: RUN_FINISHED" in text
    assert "continue:stored_plan" in text
    assert "event: STATE_DELTA" not in text
    assert "dispatch." not in text

    del app.state.llm_provider


def test_compound_orchestrator_stream_emits_task_plan_and_incremental_results() -> None:
    app.state.llm_provider = StubProvider([], reply="总结和练习已经协同完成。")
    app.state.evidence_fetcher = StaticEvidenceFetcher(
        [
            RagHit(
                chunk_id="compound-route-c1",
                page_id=101,
                page_no=12,
                chapter_id=3,
                kc_codes=["PY43-KC-001"],
                text="for 循环用于按顺序遍历可迭代对象。",
                score=0.81,
            )
        ]
    )
    # 生产进程未预加载 KC 集合时，请求内的课程索引仍是本轮证据接地的权威边界。
    app.state.valid_kc_codes = set()
    client = TestClient(app, headers={"X-Internal-Subject": "user:1"})
    route = compile_route_decision(
        IntentRouteRequest(
            message="总结循环课件，然后根据总结出三道练习",
            manual_kc_codes=["PY43-KC-001"],
        ),
        intent="HANDOUT",
        secondary_intent="PRACTICE",
        confidence=0.94,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
    )
    body = {
        "user_id": 1,
        "session_id": "route-compound-task-plan",
        "user_message": "总结循环课件，然后根据总结出三道练习",
        "recent_kc_codes": ["PY43-KC-001"],
        "course_kc_index": ["PY43-KC-001"],
        "route_decision": route.model_dump(mode="json"),
    }

    response = client.post("/agents/orchestrator/turn/stream", json=body)

    assert response.status_code == 200
    text = response.text
    assert "event: RUN_FINISHED" in text
    assert '"topology": "SEQUENTIAL"' in text
    assert '"task_plan"' in text
    assert '"status": "COMPLETED"' in text
    assert '"name": "task_step_result"' in text
    assert '"tool_name": "HANDOUT"' in text
    assert '"tool_name": "PRACTICE"' in text
    assert text.index('"tool_name": "HANDOUT"') < text.index('"tool_name": "PRACTICE"')
    assert "Unknown KC" not in text
    assert "event: RUN_ERROR" not in text

    del app.state.llm_provider
    del app.state.evidence_fetcher
    del app.state.valid_kc_codes
