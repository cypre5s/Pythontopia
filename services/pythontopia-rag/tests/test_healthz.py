import pytest
from fastapi.testclient import TestClient

from pythontopia_rag.app_state import AppState
from pythontopia_rag.index.chunker import Chunk
from pythontopia_rag.index.embedder import Embedder, HashEmbedder
from pythontopia_rag.index.store import InMemoryVectorStore
from pythontopia_rag.main import app


class SemanticTestEmbedder:
    name = "semantic-test"
    dim = 3

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [[1.0, 0.0, 0.0] for _ in texts]


def _state(embedder: Embedder) -> AppState:
    return AppState(embedder=embedder, store=InMemoryVectorStore())


async def _seed_one_chunk(state: AppState) -> None:
    chunk = Chunk(
        chunk_id="readyz-chunk",
        page_id=1,
        page_no=1,
        document_id=1,
        chapter_id=1,
        kc_codes=["PY43-KC-001"],
        text="变量是保存数据的名字。",
        token_estimate=8,
    )
    await state.store.upsert([chunk], [[1.0, 0.0, 0.0]])
    state.bm25.build([chunk])


def test_healthz_returns_ok() -> None:
    client = TestClient(app)

    response = client.get("/healthz")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


@pytest.mark.asyncio
async def test_hash_embedder_status_is_explicitly_degraded(monkeypatch) -> None:
    state = _state(HashEmbedder())
    await _seed_one_chunk(state)
    monkeypatch.setattr(app.state, "rag", state)
    client = TestClient(app)

    response = client.get("/rag/index/status")

    assert response.status_code == 200
    assert response.json()["degraded"] is True
    assert response.json()["fallback_reason"] == "non-semantic hash embedder"


def test_readyz_rejects_empty_index(monkeypatch) -> None:
    monkeypatch.setattr(app.state, "rag", _state(SemanticTestEmbedder()))
    client = TestClient(app)

    response = client.get("/readyz")

    assert response.status_code == 503
    assert response.json() == {"detail": "RAG index is empty"}


@pytest.mark.asyncio
async def test_readyz_accepts_hash_index_as_degraded_lexical_service(monkeypatch) -> None:
    state = _state(HashEmbedder())
    await _seed_one_chunk(state)
    monkeypatch.setattr(app.state, "rag", state)
    client = TestClient(app)

    response = client.get("/readyz")

    assert response.status_code == 200
    assert response.json() == {"status": "ready_degraded"}


@pytest.mark.asyncio
async def test_readyz_accepts_nonempty_semantic_index(monkeypatch) -> None:
    state = _state(SemanticTestEmbedder())
    await _seed_one_chunk(state)
    monkeypatch.setattr(app.state, "rag", state)
    client = TestClient(app)

    response = client.get("/readyz")

    assert response.status_code == 200
    assert response.json() == {"status": "ready"}
