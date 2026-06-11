"""IntentRouterAgent HTTP 路由测试。

注入 ``app.state.llm_provider`` 的 StubProvider 以避免真实 LLM；TestClient 不用 ``with``，
以免 lifespan 经 ``provider_from_env`` 覆盖 ``llm_provider``（与 orchestrator_route 测试一致）。
"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest
from fastapi.testclient import TestClient

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.main import app
from pythontopia_graph.tools.harness import reset_harness_state


@pytest.fixture(autouse=True)
def _isolate_harness_state():
    reset_harness_state()
    yield
    reset_harness_state()


class StubProvider(LlmProvider):
    def __init__(self, payload: dict | None = None) -> None:
        self._payload = payload

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_json=self._payload)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


def test_intent_route_returns_200_and_shape() -> None:
    app.state.llm_provider = StubProvider(
        {"intent": "MINDMAP", "kc_codes": ["PY43-KC-001"], "confidence": 0.8}
    )
    client = TestClient(app)
    body = {
        "message": "帮我做个 for 循环的思维导图",
        "course_kcs": [{"code": "PY43-KC-001", "name": "for 循环"}],
    }

    response = client.post("/agents/intent/route", json=body)

    assert response.status_code == 200
    data = response.json()
    assert data["intent"] == "MINDMAP"
    assert data["kc_codes"] == ["PY43-KC-001"]
    assert 0.0 <= data["confidence"] <= 1.0
    assert isinstance(data["focus_hint"], str)
    # R-16 响应契约：target / secondary_intent 始终在线（snake_case）。
    assert data["target"] == ""
    assert data["secondary_intent"] is None

    del app.state.llm_provider


def test_intent_route_keyword_fallback_without_provider_payload() -> None:
    app.state.llm_provider = StubProvider(None)
    client = TestClient(app)
    body = {"message": "出几道练习题", "course_kcs": []}

    response = client.post("/agents/intent/route", json=body)

    assert response.status_code == 200
    assert response.json()["intent"] == "PRACTICE"

    del app.state.llm_provider


def test_intent_route_accepts_r16_context_fields_and_clamps_quiz_scope() -> None:
    """R-16 请求契约全字段可达，quiz scope 下越界意图被收缩为 ASK。"""
    app.state.llm_provider = StubProvider({"intent": "HANDOUT", "confidence": 0.9})
    client = TestClient(app)
    body = {
        "message": "给我整理份讲义",
        "course_kcs": [{"code": "PY43-KC-001", "name": "for 循环"}],
        "recent_turns": [
            {"role": "user", "content": "讲讲 for 循环"},
            {"role": "assistant", "content": "for 循环用于遍历……"},
        ],
        "last_intent": "ASK",
        "last_kc_codes": ["PY43-KC-001"],
        "profile_brief": "认知风格: visual",
        "session_corrections": [{"message": "出题", "corrected_intent": "PRACTICE"}],
        "scope": "quiz",
    }

    response = client.post("/agents/intent/route", json=body)

    assert response.status_code == 200
    data = response.json()
    assert data["intent"] == "ASK"  # HANDOUT 不在 quiz 工具集，回退 ASK
    assert "考核模式" in data["reason"]

    del app.state.llm_provider


def test_intent_route_navigate_returns_target() -> None:
    app.state.llm_provider = StubProvider(
        {"intent": "NAVIGATE", "target": "arena", "confidence": 0.9}
    )
    client = TestClient(app)

    response = client.post(
        "/agents/intent/route", json={"message": "带我去闯关塔", "course_kcs": []}
    )

    assert response.status_code == 200
    data = response.json()
    assert data["intent"] == "NAVIGATE"
    assert data["target"] == "arena"

    del app.state.llm_provider


def test_intent_tools_endpoint_returns_catalog() -> None:
    client = TestClient(app)

    response = client.get("/agents/intent/tools")

    assert response.status_code == 200
    payload = response.json()
    assert len(payload) == 16
    names = {entry["name"] for entry in payload}
    assert {"ASK", "ASKME", "NAVIGATE", "MY_STUDY"} <= names
    first = payload[0]
    assert {"name", "zhLabel", "desc", "examples", "execMode"} <= set(first.keys())


def test_harness_stats_endpoint_reports_intent_route_calls() -> None:
    app.state.llm_provider = StubProvider({"intent": "ASK", "confidence": 0.9})
    client = TestClient(app)
    client.post("/agents/intent/route", json={"message": "什么是闭包", "course_kcs": []})

    response = client.get("/agents/harness/stats")

    assert response.status_code == 200
    data = response.json()
    assert "tools" in data and "breakers" in data
    assert data["tools"]["intent_route"]["calls"] >= 1
    assert data["breakers"]["provider"]["open"] is False

    del app.state.llm_provider
