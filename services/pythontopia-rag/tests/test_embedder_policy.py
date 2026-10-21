from __future__ import annotations

import sys
from types import ModuleType

import pytest

import pythontopia_rag.index.embedder as embedder_module
from pythontopia_rag.index.embedder import (
    DEFAULT_DIM,
    BgeSmallEmbedder,
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
        raise RuntimeError("embedding-secret-sentinel")


@pytest.mark.asyncio
async def test_fallback_embedder_redacts_primary_exception(caplog) -> None:
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
    assert embedder.fallback_reason == "RuntimeError"
    assert "RuntimeError" in caplog.text
    assert "embedding-secret-sentinel" not in caplog.text
    assert "embedding-secret-sentinel" not in (embedder.fallback_reason or "")
    assert embedder.using_fallback is True


@pytest.mark.parametrize("provider", ["zhipu", "zhipu-strict", "iflytek"])
def test_select_embedder_rejects_external_providers(
    monkeypatch: pytest.MonkeyPatch, provider: str
) -> None:
    monkeypatch.setenv("PYT_RAG_EMBEDDER", provider)

    with pytest.raises(ValueError, match="Only local hash and BGE embedders are supported"):
        select_embedder()


@pytest.mark.asyncio
async def test_select_hash_embedder_uses_configured_dimension_and_precedence(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("PYT_RAG_EMBEDDER", "hash")
    monkeypatch.setenv("EMBEDDING_DIM", "2048")
    monkeypatch.setenv("PYT_RAG_EMBEDDING_DIM", "512")

    selected = select_embedder()
    vectors = await selected.embed(["Python 学习"])

    assert selected.dim == 512
    assert selected.name == "hash-bow-512"
    assert len(vectors[0]) == 512


@pytest.mark.parametrize("value", ["0", "-2", "not-a-number"])
def test_select_hash_embedder_rejects_invalid_dimensions(
    monkeypatch: pytest.MonkeyPatch, value: str
) -> None:
    monkeypatch.setenv("PYT_RAG_EMBEDDER", "hash")
    monkeypatch.setenv("PYT_RAG_EMBEDDING_DIM", value)

    with pytest.raises(ValueError, match="embedding dimension must be a positive integer"):
        select_embedder()


def test_bge_weights_are_loaded_from_local_cache_without_network(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, object] = {}

    class _SentenceTransformer:
        def __init__(self, target: str, **kwargs: object) -> None:
            captured["target"] = target
            captured.update(kwargs)

    module = ModuleType("sentence_transformers")
    module.SentenceTransformer = _SentenceTransformer  # type: ignore[attr-defined]
    monkeypatch.setitem(sys.modules, "sentence_transformers", module)
    monkeypatch.setenv("PYT_RAG_BGE_MODEL_DIR", "/models/bge-small-zh-v1.5")

    BgeSmallEmbedder()

    assert captured["target"] == "/models/bge-small-zh-v1.5"
    assert captured["local_files_only"] is True
