"""KC 图谱感知检索（前置扩展路）融合测试。

语料设计：递归页（KC-021R）的前置是函数定义页（KC-021）；查询锚定 KC-021R 时，
仅当图谱扩展生效，函数页才能突破 kc_codes 过滤进入结果，并带 via_prerequisite 标注。
"""

from __future__ import annotations

import pytest

from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.fusion import hybrid_search
from pythontopia_rag.index.kc_graph import KcGraph
from pythontopia_rag.index.store import InMemoryVectorStore

RECURSION_KC = "PY43-KC-021R"
FUNCTION_KC = "PY43-KC-021"
LIST_KC = "PY43-KC-030"

GRAPH = KcGraph(prerequisites={RECURSION_KC: [FUNCTION_KC], FUNCTION_KC: []})


def _pages() -> list[IndexablePage]:
    return [
        IndexablePage(
            page_id=1,
            page_no=70,
            document_id=1,
            chapter_id=7,
            kc_codes=[RECURSION_KC],
            title="递归函数",
            text="递归函数在函数体内调用自身，必须有终止条件，否则栈溢出。" * 6,
            chapter_no=7,
            chapter_title="第七章：归纳与抽象",
        ),
        IndexablePage(
            page_id=2,
            page_no=15,
            document_id=1,
            chapter_id=7,
            kc_codes=[FUNCTION_KC],
            title="函数定义与返回值",
            text="def 定义函数，return 把函数结果返回给调用者，没有 return 则返回 None。" * 6,
            chapter_no=7,
            chapter_title="第七章：归纳与抽象",
        ),
        IndexablePage(
            page_id=3,
            page_no=30,
            document_id=1,
            chapter_id=5,
            kc_codes=[LIST_KC],
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
async def test_prerequisite_pages_join_results_with_flag() -> None:
    """锚定递归 KC 提问「函数返回」相关困惑：前置函数页进结果且带 via_prerequisite。"""
    embedder, store, bm25 = await _build()

    hits, mode = await hybrid_search(
        query="递归函数 return 返回 终止条件",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        kc_graph=GRAPH,
    )

    assert mode == "hybrid"
    by_page = {hit.chunk.page_id: hit for hit in hits}
    assert 1 in by_page and by_page[1].via_prerequisite is False  # 主题页
    assert 2 in by_page and by_page[2].via_prerequisite is True  # 前置页（扩展进入）
    assert 3 not in by_page  # 无关 KC 不会被扩展捞进
    # 前置路权重打折：主题页排名应在前置页之前。
    assert hits.index(by_page[1]) < hits.index(by_page[2])


@pytest.mark.asyncio
async def test_expansion_off_keeps_strict_kc_filter() -> None:
    embedder, store, bm25 = await _build()

    hits, _ = await hybrid_search(
        query="递归函数 return 返回 终止条件",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        kc_graph=GRAPH,
        expand_prerequisites=False,
    )

    assert {hit.chunk.page_id for hit in hits} == {1}


@pytest.mark.asyncio
async def test_empty_graph_degrades_to_plain_hybrid() -> None:
    embedder, store, bm25 = await _build()

    hits, _ = await hybrid_search(
        query="递归函数 return 返回 终止条件",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        kc_graph=KcGraph(),
    )

    assert {hit.chunk.page_id for hit in hits} == {1}
    assert all(hit.via_prerequisite is False for hit in hits)


@pytest.mark.asyncio
async def test_no_kc_anchor_means_no_expansion() -> None:
    """自由问答（无 kc_codes 锚点）不扩展：结果不带 via_prerequisite。"""
    embedder, store, bm25 = await _build()

    hits, _ = await hybrid_search(
        query="函数 返回",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=None,
        kc_graph=GRAPH,
    )

    assert hits
    assert all(hit.via_prerequisite is False for hit in hits)


@pytest.mark.asyncio
async def test_prerequisite_quota_caps_at_third_of_top_k() -> None:
    """前置专属命中受 top_k//3 配额限制（主题页永远是主角）。"""
    embedder = HashEmbedder()
    store = InMemoryVectorStore()
    bm25 = Bm25Index()
    # 1 个主题页 + 5 个前置页（同词强命中），top_k=6 时前置最多取 2 个。
    pages = [
        IndexablePage(
            page_id=1,
            page_no=70,
            document_id=1,
            chapter_id=7,
            kc_codes=[RECURSION_KC],
            title="递归函数",
            text="递归函数调用自身需要终止条件 return。" * 6,
            chapter_no=7,
            chapter_title="第七章",
        ),
    ] + [
        IndexablePage(
            page_id=10 + i,
            page_no=10 + i,
            document_id=1,
            chapter_id=7,
            kc_codes=[FUNCTION_KC],
            title=f"函数基础{i}",
            text=f"函数 return 返回值 终止条件 第{i}讲。" * 6,
            chapter_no=7,
            chapter_title="第七章",
        )
        for i in range(5)
    ]
    await rebuild_index(
        source=InMemoryPageSource(pages=pages), embedder=embedder, store=store, bm25=bm25
    )

    hits, _ = await hybrid_search(
        query="递归 函数 return 终止条件",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        kc_graph=GRAPH,
    )

    prereq_hits = [hit for hit in hits if hit.via_prerequisite]
    assert len(prereq_hits) <= 2  # top_k // 3
    assert any(hit.chunk.page_id == 1 for hit in hits)


@pytest.mark.asyncio
async def test_vector_only_fallback_never_expands() -> None:
    """BM25 缺失的 vector_only 退化路径保持历史位级兼容：不扩展。"""
    embedder, store, _ = await _build()

    hits, mode = await hybrid_search(
        query="递归函数 return",
        embedder=embedder,
        store=store,
        bm25=None,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        kc_graph=GRAPH,
    )

    assert mode == "vector_only"
    assert {hit.chunk.page_id for hit in hits} <= {1}
    assert all(hit.via_prerequisite is False for hit in hits)
