from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from pythontopia_rag.app_state import AppState
from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.store import InMemoryVectorStore
from pythontopia_rag.main import app


@pytest.fixture(autouse=True)
def _reset_state(monkeypatch: pytest.MonkeyPatch) -> None:
    state = AppState(embedder=HashEmbedder(), store=InMemoryVectorStore())
    app.state.rag = state
    app.state.rag_page_source = InMemoryPageSource(
        pages=[
            IndexablePage(
                page_id=1,
                page_no=1,
                document_id=1,
                chapter_id=None,
                kc_codes=[],
                title="t",
                text="hello world" * 10,
            )
        ]
    )
    monkeypatch.delenv("PYT_RAG_ADMIN_TOKEN", raising=False)


def test_rebuild_rejects_without_token() -> None:
    client = TestClient(app)
    response = client.post("/rag/index/rebuild")
    assert response.status_code == 401


def test_rebuild_rejects_bad_token(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PYT_RAG_ADMIN_TOKEN", "expected-token")
    client = TestClient(app)
    response = client.post(
        "/rag/index/rebuild", headers={"X-Internal-Token": "wrong"}
    )
    assert response.status_code == 401


def test_rebuild_accepts_good_token(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PYT_RAG_ADMIN_TOKEN", "expected-token")
    client = TestClient(app)
    response = client.post(
        "/rag/index/rebuild", headers={"X-Internal-Token": "expected-token"}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["built_chunks"] >= 1
