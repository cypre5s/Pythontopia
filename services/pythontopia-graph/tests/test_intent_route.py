"""IntentRouterAgent HTTP route tests (FastAPI TestClient).

Injects ``app.state.llm_provider`` with a StubProvider so no real LLM is called.
The client is created WITHOUT the ``with`` context manager so the app lifespan
(which would overwrite ``llm_provider`` via ``provider_from_env``) does not run —
matching tests/test_orchestrator_route.py.
"""

from __future__ import annotations

from collections.abc import AsyncIterator

from fastapi.testclient import TestClient

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.main import app


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

    del app.state.llm_provider


def test_intent_route_keyword_fallback_without_provider_payload() -> None:
    app.state.llm_provider = StubProvider(None)
    client = TestClient(app)
    body = {"message": "出几道练习题", "course_kcs": []}

    response = client.post("/agents/intent/route", json=body)

    assert response.status_code == 200
    assert response.json()["intent"] == "PRACTICE"

    del app.state.llm_provider
