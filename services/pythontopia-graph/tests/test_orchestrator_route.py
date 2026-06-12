"""OrchestratorAgent HTTP 与 SSE 路由测试。

注入 ``app.state.llm_provider`` 的 StubProvider 以避免真实 LLM；TestClient 不用 ``with``，
以免 lifespan 经 ``provider_from_env`` 覆盖 ``llm_provider``。
"""

from __future__ import annotations

from collections.abc import AsyncIterator

from fastapi.testclient import TestClient

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.main import app


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


def test_orchestrator_turn_returns_200_and_response_shape() -> None:
    app.state.llm_provider = StubProvider([], reply="我们先看再学。")
    client = TestClient(app)
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


def test_orchestrator_stream_emits_trace_token_and_result_events() -> None:
    app.state.llm_provider = StubProvider([], reply="本轮安排已就绪。")
    client = TestClient(app)
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
    assert "event: trace" in text
    assert "event: token" in text
    assert "event: result" in text
    assert "event: error" not in text

    del app.state.llm_provider


def test_orchestrator_stream_emits_state_delta_plan_progress() -> None:
    """dispatch 经 AG-UI STATE_DELTA 实时推送 plan 步骤进度（真实产出，不造假）。"""
    app.state.llm_provider = StubProvider([], reply="进度同步演示。")
    client = TestClient(app)
    body = {
        "user_id": 1,
        "session_id": "route-state-delta",
        "user_message": "我想学 for 循环",
        "course_kc_index": ["PY43-KC-001", "PY43-KC-003"],
    }

    response = client.post("/agents/orchestrator/turn/stream", json=body)

    assert response.status_code == 200
    text = response.text
    # AG-UI 官方事件名 + plan 进度袋，前端 useSseStream 双语兼容层可直接归一化进 state。
    assert "event: STATE_DELTA" in text
    assert "plan_steps" in text
    # 开场全 pending 快照 + 每步真实推进到 active（done/failed 视 dispatch 结果）。
    assert '"pending"' in text
    assert '"active"' in text

    del app.state.llm_provider
