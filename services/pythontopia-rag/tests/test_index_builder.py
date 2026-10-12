from __future__ import annotations

import pytest

from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.builder import EmbeddingDegradedError, rebuild_index
from pythontopia_rag.index.embedder import FallbackEmbedder, HashEmbedder
from pythontopia_rag.index.store import InMemoryVectorStore


class _SemanticEmbedder:
    name = "semantic-test"
    dim = 8

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [[1.0] + [0.0] * (self.dim - 1) for _ in texts]


def _sample_pages() -> list[IndexablePage]:
    return [
        IndexablePage(
            page_id=101,
            page_no=1,
            document_id=1,
            chapter_id=1,
            kc_codes=["PY43-KC-012"],
            title="input 函数",
            text="input() 函数会读取一行字符串。" * 10,
        ),
        IndexablePage(
            page_id=102,
            page_no=2,
            document_id=1,
            chapter_id=2,
            kc_codes=["PY43-KC-020"],
            title="for 循环",
            text="for 循环用来遍历序列，例如 for i in range(10)。" * 10,
        ),
        IndexablePage(
            page_id=103,
            page_no=3,
            document_id=1,
            chapter_id=3,
            kc_codes=["PY43-KC-030"],
            title="列表",
            text="列表 list 可以保存多个数据，支持 append 和 pop。" * 10,
        ),
    ]


@pytest.mark.asyncio
async def test_rebuild_writes_vectors_with_metadata() -> None:
    store = InMemoryVectorStore()
    embedder = HashEmbedder()
    source = InMemoryPageSource(pages=_sample_pages())

    result = await rebuild_index(source=source, embedder=embedder, store=store)

    assert result.built_chunks >= 3
    assert result.embed_model == embedder.name
    assert result.store == store.name
    status = await store.status()
    assert status.built_chunks == result.built_chunks
    assert status.last_rebuilt_at is not None


@pytest.mark.asyncio
async def test_rebuild_clears_previous_chunks() -> None:
    store = InMemoryVectorStore()
    embedder = HashEmbedder()
    source = InMemoryPageSource(pages=_sample_pages())
    await rebuild_index(source=source, embedder=embedder, store=store)
    first_count = (await store.status()).built_chunks

    source.pages = source.pages[:1]
    await rebuild_index(source=source, embedder=embedder, store=store)
    second_count = (await store.status()).built_chunks

    assert second_count < first_count


class _FlakyPrimary:
    """第 fail_at 次调用起抛错的"真实"嵌入后端（模拟 key 过期 / 网络抖动）。"""

    name = "flaky-primary"

    def __init__(self, dim: int, fail_at: int) -> None:
        self.dim = dim
        self._fail_at = fail_at
        self.calls = 0

    async def embed(self, texts: list[str]) -> list[list[float]]:
        self.calls += 1
        if self.calls >= self._fail_at:
            raise TimeoutError("embedding API timed out")
        return [[1.0] + [0.0] * (self.dim - 1) for _ in texts]


def _fallback_embedder(fail_at: int) -> FallbackEmbedder:
    dim = HashEmbedder().dim
    return FallbackEmbedder(
        primary=_FlakyPrimary(dim, fail_at),
        fallback=HashEmbedder(dim=dim),
        dim=dim,
        name="flaky+hash-fallback",
    )


@pytest.mark.asyncio
async def test_mid_rebuild_fallback_switch_aborts_instead_of_mixing_vector_spaces() -> None:
    """兜底 embedder 中途由真实向量切到同维 hash → 索引会混入两套向量空间，必须整体失败。"""
    store = InMemoryVectorStore()
    bm25 = Bm25Index()
    source = InMemoryPageSource(pages=_sample_pages())
    await rebuild_index(source=source, embedder=HashEmbedder(), store=store, bm25=bm25)
    healthy_count = (await store.status()).built_chunks
    assert healthy_count > 0

    embedder = _fallback_embedder(fail_at=2)
    with pytest.raises(EmbeddingDegradedError):
        await rebuild_index(
            source=source, embedder=embedder, store=store, bm25=bm25, batch_size=1
        )

    # 旧索引必须原封不动（既没被清空，也没被写进半套 hash 向量）。
    status = await store.status()
    assert status.built_chunks == healthy_count
    assert bm25.built_chunks == healthy_count


@pytest.mark.asyncio
async def test_embed_failure_leaves_previous_index_intact() -> None:
    """严格 embedder 下 embed 失败：不得留下「空向量库 + 陈旧 BM25」的分裂状态。"""

    class _AlwaysFails:
        name = "always-fails"
        dim = 8

        async def embed(self, texts: list[str]) -> list[list[float]]:
            raise RuntimeError("embedding API unreachable")

    store = InMemoryVectorStore()
    bm25 = Bm25Index()
    source = InMemoryPageSource(pages=_sample_pages())
    await rebuild_index(source=source, embedder=HashEmbedder(), store=store, bm25=bm25)
    healthy_count = (await store.status()).built_chunks

    with pytest.raises(RuntimeError):
        await rebuild_index(source=source, embedder=_AlwaysFails(), store=store, bm25=bm25)

    status = await store.status()
    assert status.built_chunks == healthy_count, "向量库不得在 embed 失败后被清空"
    assert bm25.built_chunks == healthy_count
    assert status.built_chunks == bm25.built_chunks, "向量库与 BM25 不得分裂"


@pytest.mark.asyncio
async def test_startup_degraded_embedder_is_surfaced_not_silent() -> None:
    """启动即无可用凭据 → 整体走兜底是允许的，但必须暴露 degraded / fallback_reason。"""
    store = InMemoryVectorStore()
    dim = HashEmbedder().dim
    embedder = FallbackEmbedder(
        primary=None,
        fallback=HashEmbedder(dim=dim),
        dim=dim,
        name="test-primary+hash-fallback",
        fallback_reason="ValueError: missing EMBEDDING_API_KEY",
    )
    result = await rebuild_index(
        source=InMemoryPageSource(pages=_sample_pages()), embedder=embedder, store=store
    )

    assert result.degraded is True
    assert result.fallback_reason == "ValueError: missing EMBEDDING_API_KEY"
    status = await store.status()
    assert status.degraded is True
    assert status.fallback_reason == "ValueError: missing EMBEDDING_API_KEY"


@pytest.mark.asyncio
async def test_healthy_embedder_reports_not_degraded() -> None:
    store = InMemoryVectorStore()
    result = await rebuild_index(
        source=InMemoryPageSource(pages=_sample_pages()),
        embedder=_SemanticEmbedder(),
        store=store,
    )
    assert result.degraded is False
    assert result.fallback_reason is None
    assert (await store.status()).degraded is False
