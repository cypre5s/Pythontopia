from __future__ import annotations

import pytest

from pythontopia_rag.index.embedder import (
    FallbackEmbedder,
    HashEmbedder,
    ZHIPU_DEFAULT_DIM,
    select_embedder,
)


class _BrokenEmbedder:
    name = "broken-primary"
    dim = ZHIPU_DEFAULT_DIM

    async def embed(self, texts: list[str]) -> list[list[float]]:
        raise RuntimeError("expired api key")


@pytest.mark.asyncio
async def test_fallback_embedder_returns_same_dimension_hash_vectors_after_primary_failure() -> None:
    embedder = FallbackEmbedder(
        primary=_BrokenEmbedder(),
        fallback=HashEmbedder(dim=ZHIPU_DEFAULT_DIM),
        dim=ZHIPU_DEFAULT_DIM,
        name="zhipu-embedding-3+hash-fallback",
    )

    assert embedder.fallback_reason is None
    assert embedder.using_fallback is False

    vectors = await embedder.embed(["列表推导式", "for 循环"])

    assert embedder.name == "zhipu-embedding-3+hash-fallback"
    assert embedder.dim == ZHIPU_DEFAULT_DIM
    assert len(vectors) == 2
    assert all(len(vector) == ZHIPU_DEFAULT_DIM for vector in vectors)
    assert "expired api key" in (embedder.fallback_reason or "")
    assert embedder.using_fallback is True


@pytest.mark.asyncio
async def test_select_zhipu_without_key_still_uses_2048_dim_hash_fallback(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PYT_RAG_EMBEDDER", "zhipu")
    monkeypatch.delenv("PYT_RAG_EMBEDDING_API_KEY", raising=False)
    monkeypatch.delenv("EMBEDDING_API_KEY", raising=False)

    embedder = select_embedder()
    vectors = await embedder.embed(["input 函数"])

    assert embedder.dim == ZHIPU_DEFAULT_DIM
    assert "hash-fallback" in embedder.name
    assert len(vectors[0]) == ZHIPU_DEFAULT_DIM
