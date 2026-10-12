from __future__ import annotations

from fastapi.testclient import TestClient

from pythontopia_graph.main import app
from pythontopia_graph.tools.browse.contract import BrowseResult, BrowseStep


def test_browse_run_endpoint_returns_structured_evidence(monkeypatch) -> None:
    captured = {}
    monkeypatch.setenv("BROWSE_ENABLED", "on")

    async def fake_execute(request):
        captured["request"] = request
        return BrowseResult(
            steps=[
                BrowseStep(
                    index=0,
                    action="read",
                    url="https://docs.python.org/3/library/functions.html#sorted",
                    title="Built-in Functions",
                    snippet="sorted returns a new sorted list.",
                    summary="sorted returns a new sorted list.",
                )
            ],
            final_summary="【浏览器读取·外部内容，非课件证据】sorted returns a new sorted list.",
            citations=["https://docs.python.org/3/library/functions.html#sorted"],
        )

    monkeypatch.setattr("pythontopia_graph.main.execute_browse", fake_execute)
    response = TestClient(app).post(
        "/agents/browse/run",
        json={
            "goal": "查证 sorted",
            "allowed_domains": ["docs.python.org"],
            "max_steps": 2,
            "user_id": 7,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["steps"][0]["title"] == "Built-in Functions"
    assert body["steps"][0]["snippet"] == "sorted returns a new sorted list."
    assert captured["request"].user_id == 7


def test_browse_run_endpoint_rejects_client_supplied_oversized_limits() -> None:
    response = TestClient(app).post(
        "/agents/browse/run",
        json={
            "goal": "查证 sorted",
            "allowed_domains": ["docs.python.org"],
            "max_steps": 12,
            "max_bytes": 999_999,
            "timeout_seconds": 120,
            "user_id": 7,
        },
    )

    assert response.status_code == 422
