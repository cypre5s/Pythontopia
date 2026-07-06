from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from pythontopia_rag.app_state import AppState
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.store import InMemoryVectorStore
from pythontopia_rag.main import app


@pytest.fixture(autouse=True)
def _reset_state() -> AppState:
    """每例重置为离线确定性 HashEmbedder，避免污染并保证 CI 无外部依赖。"""
    state = AppState(embedder=HashEmbedder(), store=InMemoryVectorStore())
    app.state.rag = state
    return state


def test_embed_returns_vectors_with_model_and_dim() -> None:
    client = TestClient(app)

    response = client.post("/embed", json={"texts": ["列表是可变对象", "for 循环遍历序列"]})

    assert response.status_code == 200
    body = response.json()
    assert body["model"] == HashEmbedder.name
    assert body["dim"] == HashEmbedder.dim
    assert len(body["vectors"]) == 2
    assert all(len(vector) == HashEmbedder.dim for vector in body["vectors"])


def test_embed_empty_texts_returns_empty_vectors() -> None:
    client = TestClient(app)

    response = client.post("/embed", json={"texts": []})

    assert response.status_code == 200
    body = response.json()
    assert body["vectors"] == []
    # 即便无文本，模型元信息仍返回，供调用方对齐维度。
    assert body["model"] == HashEmbedder.name
    assert body["dim"] == HashEmbedder.dim


def test_embed_missing_texts_defaults_to_empty() -> None:
    client = TestClient(app)

    response = client.post("/embed", json={})

    assert response.status_code == 200
    assert response.json()["vectors"] == []


def test_embed_vectors_are_unit_normalized() -> None:
    """真实向量（非占位）：HashEmbedder 输出单位向量，余弦检索才有意义。"""
    client = TestClient(app)

    response = client.post("/embed", json={"texts": ["列表是可变对象"]})

    vector = response.json()["vectors"][0]
    norm = sum(component * component for component in vector) ** 0.5
    assert norm == pytest.approx(1.0, abs=1e-6)


def test_embed_dim_reflects_active_embedder() -> None:
    """dim 取自当前 embedder（可换源 client 基础）：换 embedder 维度随之变化。"""

    class _Tiny:
        name = "tiny-test-embedder"
        dim = 4

        async def embed(self, texts: list[str]) -> list[list[float]]:
            return [[0.0, 0.0, 0.0, 1.0] for _ in texts]

    app.state.rag = AppState(embedder=_Tiny(), store=InMemoryVectorStore())
    client = TestClient(app)

    response = client.post("/embed", json={"texts": ["abc"]})

    body = response.json()
    assert body["model"] == "tiny-test-embedder"
    assert body["dim"] == 4
    assert body["vectors"] == [[0.0, 0.0, 0.0, 1.0]]
