from __future__ import annotations

import pytest

from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.store import InMemoryVectorStore


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
