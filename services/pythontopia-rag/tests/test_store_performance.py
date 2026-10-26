from __future__ import annotations

import pytest

import pythontopia_rag.index.store as store_module
from pythontopia_rag.index.chunker import Chunk
from pythontopia_rag.index.store import InMemoryVectorStore


@pytest.mark.asyncio
async def test_index_vectors_are_normalized_once_when_upserted(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    normalization_count = 0
    normalize = store_module._normalize

    def count_normalization(vector: list[float]) -> list[float]:
        nonlocal normalization_count
        normalization_count += 1
        return normalize(vector)

    monkeypatch.setattr(store_module, "_normalize", count_normalization)
    store = InMemoryVectorStore()
    chunks = [
        Chunk("a", 1, 1, 1, 1, ["PY43-KC-001"], "a", 1),
        Chunk("b", 2, 2, 1, 1, ["PY43-KC-001"], "b", 1),
    ]

    await store.upsert(chunks, [[3.0, 4.0], [0.0, 2.0]])
    assert normalization_count == 2

    hits = await store.search([1.0, 0.0], top_k=2)

    assert normalization_count == 3
    assert [hit.chunk.chunk_id for hit in hits] == ["a", "b"]
    assert [hit.score for hit in hits] == pytest.approx([0.6, 0.0])
