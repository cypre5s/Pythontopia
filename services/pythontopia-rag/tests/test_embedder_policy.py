from __future__ import annotations

import pytest

import pythontopia_rag.index.embedder as embedder_module
from pythontopia_rag.index.embedder import (
    DEFAULT_DIM,
    FallbackEmbedder,
    HashEmbedder,
    select_embedder,
)


def test_external_zhipu_embedder_is_not_constructible() -> None:
    assert not hasattr(embedder_module, "ZhipuEmbedder")


class _BrokenEmbedder:
    name = "broken-primary"
    dim = DEFAULT_DIM

    async def embed(self, texts: list[str]) -> list[list[float]]:
        raise RuntimeError("expired api key")


@pytest.mark.asyncio
async def test_fallback_embedder_returns_same_dimension_hash_vectors_after_primary_failure() -> None:
    embedder = FallbackEmbedder(
        primary=_BrokenEmbedder(),
        fallback=HashEmbedder(dim=DEFAULT_DIM),
        dim=DEFAULT_DIM,
        name="test-primary+hash-fallback",
    )

    assert embedder.fallback_reason is None
    assert embedder.using_fallback is False

    vectors = await embedder.embed(["列表推导式", "for 循环"])

    assert embedder.name == "test-primary+hash-fallback"
    assert embedder.dim == DEFAULT_DIM
    assert len(vectors) == 2
    assert all(len(vector) == DEFAULT_DIM for vector in vectors)
    assert "expired api key" in (embedder.fallback_reason or "")
    assert embedder.using_fallback is True


@pytest.mark.parametrize("provider", ["zhipu", "zhipu-strict", "iflytek"])
def test_select_embedder_rejects_external_providers(
    monkeypatch: pytest.MonkeyPatch, provider: str
) -> None:
    monkeypatch.setenv("PYT_RAG_EMBEDDER", provider)

    with pytest.raises(ValueError, match="Only local hash and BGE embedders are supported"):
        select_embedder()
