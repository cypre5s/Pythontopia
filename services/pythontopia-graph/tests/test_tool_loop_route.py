"""混合工具编排端点测试（MVP Phase F 接线）：flag off → 404；flag on → 端到端运行。

注入 stub provider（路由器有把握选 HANDOUT + 循环可终止）+ StaticEvidenceFetcher，
避免真实 LLM/网络；TestClient 不用 with（避免 lifespan 覆盖注入的 provider）。
"""

from __future__ import annotations

from collections.abc import AsyncIterator, Iterator

import pytest
from fastapi.testclient import TestClient

from pythontopia_graph.llm.provider import LlmResponse
from pythontopia_graph.main import app
from pythontopia_graph.nodes.evidence_fetcher import StaticEvidenceFetcher

_BODY = {
    "user_id": 1,
    "session_id": "tool-loop-route",
    "user_message": "给我整理一份函数的讲义",
    "course_kc_index": ["PY43-KC-001"],
}


class _RouteStub:
    """路由器调用（无 tools）→ 返回有把握的 HANDOUT 意图；循环调用（有 tools）→ 不再调工具收尾。"""

    supports_tool_calling = True

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        if kwargs.get("tools"):
            return LlmResponse(content_text="已经为你准备好讲义。")
        return LlmResponse(
            content_json={
                "intent": "HANDOUT",
                "kc_codes": ["PY43-KC-001"],
                "confidence": 0.9,
                "focus_hint": "函数讲义",
            }
        )

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        yield ""


@pytest.fixture
def _client() -> Iterator[TestClient]:
    app.state.evidence_fetcher = StaticEvidenceFetcher([])
    yield TestClient(app)
    for attr in ("llm_provider", "evidence_fetcher"):
        if hasattr(app.state, attr):
            delattr(app.state, attr)


def test_tool_loop_404_when_disabled(
    monkeypatch: pytest.MonkeyPatch, _client: TestClient
) -> None:
    monkeypatch.delenv("PYT_TOOL_LOOP", raising=False)
    app.state.llm_provider = _RouteStub()
    response = _client.post("/agents/orchestrator/tool-loop", json=_BODY)
    assert response.status_code == 404


def test_tool_loop_runs_when_enabled(
    monkeypatch: pytest.MonkeyPatch, _client: TestClient
) -> None:
    monkeypatch.setenv("PYT_TOOL_LOOP", "on")
    app.state.llm_provider = _RouteStub()
    response = _client.post("/agents/orchestrator/tool-loop", json=_BODY)
    assert response.status_code == 200
    data = response.json()
    # 路由器有把握选 HANDOUT → 种子 make_resource → 循环编排。
    assert data["intent"] == "HANDOUT"
    assert data["seed_tool"] == "make_resource"
    assert data["loop"] is not None
    assert data["loop"]["tool_trail"][0] == "make_resource"


def test_tool_loop_stream_emits_events_when_enabled(
    monkeypatch: pytest.MonkeyPatch, _client: TestClient
) -> None:
    monkeypatch.setenv("PYT_TOOL_LOOP", "on")
    app.state.llm_provider = _RouteStub()
    response = _client.post("/agents/orchestrator/tool-loop/stream", json=_BODY)
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    text = response.text
    # loop_step 以 AG-UI CUSTOM 事件承载（前端 loopSlice 契约），种子步骤即触发。
    assert "event: CUSTOM" in text
    assert '"loop_step"' in text
    assert "event: result" in text
    assert "event: error" not in text
