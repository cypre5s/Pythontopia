from __future__ import annotations

import pytest

from pythontopia_rag.tools.embedding_stability import run_stability_probe


class _StableEmbedder:
    name = "stable-test"
    dim = 4
    fallback_reason = None
    using_fallback = False

    async def embed(self, texts: list[str]) -> list[list[float]]:
        vectors: list[list[float]] = []
        for text in texts:
            if "列表" in text:
                vectors.append([1.0, 0.0, 0.0, 0.0])
            else:
                vectors.append([0.0, 1.0, 0.0, 0.0])
        return vectors


@pytest.mark.asyncio
async def test_stability_probe_summarizes_successful_project_embedder() -> None:
    summary = await run_stability_probe(
        _StableEmbedder(),
        texts=["列表推导式", "for 循环"],
        rounds=3,
        concurrency=2,
        batch_size=2,
    )

    assert summary.model == "stable-test"
    assert summary.dim == 4
    assert summary.total_requests == 3
    assert summary.total_vectors == 6
    assert summary.failures == 0
    assert summary.dimension_mismatches == 0
    assert summary.non_finite_vectors == 0
    assert summary.min_repeat_cosine == pytest.approx(1.0)
    assert summary.fallback_used is False


class _BadDimEmbedder:
    name = "bad-dim-test"
    dim = 4
    fallback_reason = None
    using_fallback = False

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [[1.0, 0.0] for _ in texts]


@pytest.mark.asyncio
async def test_stability_probe_counts_dimension_mismatches() -> None:
    summary = await run_stability_probe(
        _BadDimEmbedder(),
        texts=["input 函数"],
        rounds=2,
        concurrency=1,
        batch_size=1,
    )

    assert summary.total_requests == 2
    assert summary.dimension_mismatches == 2
    assert summary.failures == 0
