from __future__ import annotations

import pytest

from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.fusion import hybrid_search, rrf_scores
from pythontopia_rag.index.store import InMemoryVectorStore


def test_rrf_doc_in_both_lists_outranks_single_list_docs() -> None:
    fused = rrf_scores([["a", "b", "c"], ["b", "d"]])
    assert fused["b"] > fused["a"]
    assert fused["b"] > fused["d"]
    # rank 越靠前贡献越大
    assert fused["a"] > fused["c"]


def test_rrf_scores_match_formula() -> None:
    fused = rrf_scores([["a", "b"], ["b"]], k=60)
    assert fused["a"] == pytest.approx(1.0 / 61)
    assert fused["b"] == pytest.approx(1.0 / 62 + 1.0 / 61)


def _pages() -> list[IndexablePage]:
    return [
        IndexablePage(
            page_id=1,
            page_no=10,
            document_id=1,
            chapter_id=2,
            kc_codes=["PY43-KC-001"],
            title="print函数",
            text="print() 函数把内容输出到屏幕，可以用 sep 和 end 参数控制格式。" * 6,
            chapter_no=2,
            chapter_title="第二章：Python 语言基础(2)",
        ),
        IndexablePage(
            page_id=2,
            page_no=20,
            document_id=1,
            chapter_id=4,
            kc_codes=["PY43-KC-020"],
            title="for语句",
            text="for 循环用于遍历序列，例如 for i in range(10) 重复执行循环体。" * 6,
            chapter_no=4,
            chapter_title="第四章：流程自动化",
        ),
        IndexablePage(
            page_id=3,
            page_no=30,
            document_id=1,
            chapter_id=5,
            kc_codes=["PY43-KC-030"],
            title="列表",
            text="列表 list 是可变序列，支持 append、pop 与切片。" * 6,
            chapter_no=5,
            chapter_title="第五章：数据结构",
        ),
    ]


async def _build() -> tuple[HashEmbedder, InMemoryVectorStore, Bm25Index]:
    embedder = HashEmbedder()
    store = InMemoryVectorStore()
    bm25 = Bm25Index()
    await rebuild_index(
        source=InMemoryPageSource(pages=_pages()), embedder=embedder, store=store, bm25=bm25
    )
    return embedder, store, bm25


@pytest.mark.asyncio
async def test_hybrid_search_returns_hybrid_mode_and_normalized_scores() -> None:
    embedder, store, bm25 = await _build()
    hits, mode = await hybrid_search(
        query="print 输出",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=3,
        min_score=0.0,
    )
    assert mode == "hybrid"
    assert hits
    assert hits[0].chunk.page_id == 1
    assert all(0.0 < hit.score <= 1.0 for hit in hits)
    # 双路第一名应拿到归一化满分 1.0
    assert hits[0].score == pytest.approx(1.0)


@pytest.mark.asyncio
async def test_hybrid_falls_back_to_vector_only_without_bm25() -> None:
    embedder, store, _ = await _build()
    hits, mode = await hybrid_search(
        query="print 输出",
        embedder=embedder,
        store=store,
        bm25=None,
        top_k=3,
        min_score=0.0,
    )
    assert mode == "vector_only"
    assert hits
    assert hits[0].chunk.page_id == 1


@pytest.mark.asyncio
async def test_hybrid_falls_back_when_bm25_empty() -> None:
    embedder, store, _ = await _build()
    empty_bm25 = Bm25Index()
    _, mode = await hybrid_search(
        query="print 输出",
        embedder=embedder,
        store=store,
        bm25=empty_bm25,
        top_k=3,
        min_score=0.0,
    )
    assert mode == "vector_only"


@pytest.mark.asyncio
async def test_hybrid_no_candidates_yields_empty() -> None:
    embedder, store, bm25 = await _build()
    hits, mode = await hybrid_search(
        query="奥利给奥利给",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=3,
        min_score=0.95,
    )
    assert mode == "hybrid"
    assert hits == []


@pytest.mark.asyncio
async def test_hybrid_respects_kc_filter() -> None:
    embedder, store, bm25 = await _build()
    hits, mode = await hybrid_search(
        query="循环 遍历",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=5,
        min_score=0.0,
        kc_codes=["PY43-KC-020"],
    )
    assert mode == "hybrid"
    assert hits
    assert all(hit.chunk.kc_codes == ["PY43-KC-020"] for hit in hits)
