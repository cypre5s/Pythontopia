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


@pytest.mark.asyncio
async def test_select_hash_honors_configured_embedding_dimension(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("PYT_RAG_EMBEDDER", "hash")
    monkeypatch.delenv("PYT_RAG_EMBEDDING_DIM", raising=False)
    monkeypatch.setenv("EMBEDDING_DIM", "2048")

    embedder = select_embedder()
    vectors = await embedder.embed(["列表推导式"])

    assert embedder.name == "hash-bow-2048"
    assert embedder.dim == 2048
    assert len(vectors[0]) == 2048


def test_pyt_dimension_takes_precedence_for_hash(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PYT_RAG_EMBEDDER", "hash")
    monkeypatch.setenv("PYT_RAG_EMBEDDING_DIM", "64")
    monkeypatch.setenv("EMBEDDING_DIM", "128")

    embedder = select_embedder()

    assert embedder.name == "hash-bow-64"
    assert embedder.dim == 64


@pytest.mark.parametrize("configured_dim", ["0", "-1", "not-an-integer"])
def test_select_hash_rejects_invalid_configured_dimension(
    monkeypatch: pytest.MonkeyPatch,
    configured_dim: str,
) -> None:
    monkeypatch.setenv("PYT_RAG_EMBEDDER", "hash")
    monkeypatch.setenv("PYT_RAG_EMBEDDING_DIM", configured_dim)

    with pytest.raises(ValueError, match="embedding dimension"):
        select_embedder()
