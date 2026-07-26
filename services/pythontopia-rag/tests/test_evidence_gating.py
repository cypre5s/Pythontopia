"""防幻觉证据门控回归：BM25 相关度门槛、融合分归一、页级去冗、前置席位门槛、关系优先级。

每个用例都锚定一个已实测复现的缺陷；修复前会失败，修复后通过。
"""

from __future__ import annotations

import pytest

from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.index.bm25 import Bm25Index, is_topical_term
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.fusion import (
    MAX_CHUNKS_PER_PAGE,
    _relation_for_chunk,
    hybrid_search,
)
from pythontopia_rag.index.kc_graph import KcGraph
from pythontopia_rag.index.store import InMemoryVectorStore

LIST_KC = "PY43-KC-030"
VARIABLE_KC = "PY43-KC-002"


def _page(
    page_id: int,
    *,
    kc_codes: list[str],
    title: str,
    text: str,
    document_id: int = 1,
    page_no: int | None = None,
) -> IndexablePage:
    return IndexablePage(
        page_id=page_id,
        page_no=page_no if page_no is not None else page_id,
        document_id=document_id,
        chapter_id=5,
        kc_codes=kc_codes,
        title=title,
        text=text,
        chapter_no=5,
        chapter_title="第五章：数据结构",
    )


async def _build(pages: list[IndexablePage]) -> tuple[HashEmbedder, InMemoryVectorStore, Bm25Index]:
    embedder = HashEmbedder()
    store = InMemoryVectorStore()
    bm25 = Bm25Index()
    await rebuild_index(
        source=InMemoryPageSource(pages=pages), embedder=embedder, store=store, bm25=bm25
    )
    return embedder, store, bm25


# ───────────────────────── 缺陷 1：BM25 只要求正分 ─────────────────────────


def _function_word_corpus() -> list[IndexablePage]:
    """课件正文里混入问句虚词（「怎么样」「适合」「今天」），与无关提问共享 CJK bigram。"""
    return [
        _page(
            1,
            kc_codes=[LIST_KC],
            title="列表",
            text="列表 list 是可变序列，支持 append 与 pop。今天我们看看它适合怎么样使用。" * 6,
        ),
        _page(
            2,
            kc_codes=[VARIABLE_KC],
            title="变量",
            text="变量是给数据起的名字。今天这一节适合初学者，怎么样命名更清楚。" * 6,
        ),
    ]


@pytest.mark.asyncio
async def test_off_topic_question_yields_no_evidence_even_though_bm25_scores_positive() -> None:
    """无关提问只蹭到问句虚词 bigram —— 旧实现「BM25 正分即入选」会当成课件证据。"""
    embedder, store, bm25 = await _build(_function_word_corpus())
    query = "今天天气怎么样适合去打篮球吗"

    # 旧行为基线：不带门槛的 BM25 对这条无关提问确实给出正分候选。
    assert bm25.search(query, 50), "语料应与无关提问共享虚词 bigram，否则本用例无意义"

    hits, mode = await hybrid_search(
        query=query,
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=8,
        min_score=0.3,
    )
    assert mode == "hybrid"
    assert hits == [], f"无关提问不应拿到课件证据：{[hit.chunk.page_id for hit in hits]}"


@pytest.mark.asyncio
async def test_on_topic_query_still_retrieves_evidence_after_gating() -> None:
    """门槛不得误伤课程内提问（防幻觉不能变成防召回）。"""
    embedder, store, bm25 = await _build(_function_word_corpus())
    for query, expected_page in (("列表", 1), ("变量", 2)):
        hits, _ = await hybrid_search(
            query=query,
            embedder=embedder,
            store=store,
            bm25=bm25,
            top_k=8,
            min_score=0.3,
        )
        assert hits, f"{query!r} 应有课件证据"
        assert hits[0].chunk.page_id == expected_page


def test_is_topical_term_separates_question_scaffolding_from_content_words() -> None:
    assert is_topical_term("列表")
    assert is_topical_term("input")
    assert is_topical_term("==")
    assert not is_topical_term("如何")
    assert not is_topical_term("怎么")
    assert not is_topical_term("有什")


# ───────────────────── 缺陷 2：融合分是位次常数而非相关度 ─────────────────────


@pytest.mark.asyncio
async def test_single_path_scores_normalize_over_active_paths_and_expose_native_scores() -> None:
    """向量路被阈值滤空时，融合分应按实际生效路数归一（旧实现 rank-1 恒 0.5）。"""
    embedder, store, bm25 = await _build(_function_word_corpus())
    hits, _ = await hybrid_search(
        query="列表 append",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=5,
        min_score=0.99,  # 余弦不可能达到，向量路必空
    )
    assert hits, "BM25 路仍应召回"
    assert all(hit.vector_score is None for hit in hits), "向量路被滤空，不应有余弦分"
    assert hits[0].score == pytest.approx(1.0), "单路 rank-1 应归一到 1.0，而非启用路数的 0.5"
    assert hits[0].bm25_score is not None and hits[0].bm25_score > 0.0


@pytest.mark.asyncio
async def test_hit_carries_native_scores_when_both_paths_fire() -> None:
    embedder, store, bm25 = await _build(_function_word_corpus())
    hits, _ = await hybrid_search(
        query="列表 append pop",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=5,
        min_score=0.0,
    )
    top = hits[0]
    assert top.vector_score is not None and 0.0 <= top.vector_score <= 1.0
    assert top.bm25_score is not None and top.bm25_score > 0.0


# ───────────────── 缺陷 5：同页多 chunk / 跨文档重复正文占满 top_k ─────────────────


def _page_monopoly_corpus() -> list[IndexablePage]:
    """一页超长 → 滑窗（target=700/overlap=100）切出十余个高度重叠的 chunk。"""
    long_text = "".join(
        f"列表 list 的第{index}段讲解：append 追加元素，pop 弹出元素，切片取子列表，"
        f"下标从 0 开始，len 返回元素个数，sort 就地排序。" * 3
        for index in range(12)
    )
    return [
        _page(1, kc_codes=[LIST_KC], title="列表详解", text=long_text),
        _page(2, kc_codes=[LIST_KC], title="列表排序", text="列表 sort 排序与 sorted 的区别。" * 6),
        _page(3, kc_codes=[LIST_KC], title="列表切片", text="列表 切片 取出子列表的下标区间。" * 6),
    ]


@pytest.mark.asyncio
async def test_single_page_cannot_monopolise_top_k() -> None:
    """一页被滑窗切成多个 chunk 时最多占 MAX_CHUNKS_PER_PAGE 个证据席位。"""
    pages = _page_monopoly_corpus()
    embedder, store, bm25 = await _build(pages)
    hits, _ = await hybrid_search(
        query="列表 append pop 切片",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
    )
    page_one_hits = [hit for hit in hits if hit.chunk.page_id == 1]
    assert len(page_one_hits) <= MAX_CHUNKS_PER_PAGE
    assert {hit.chunk.page_id for hit in hits} >= {1, 2, 3}, "其它页应拿回被独占的席位"


@pytest.mark.asyncio
async def test_identical_text_across_documents_is_deduplicated() -> None:
    """7 份文档共用的封面页正文完全相同——不应重复占用证据席位。"""
    cover = "《Python语言基础》课程封面：列表 append 与 pop 的课后练习说明。" * 6
    pages = [
        _page(10 + index, kc_codes=[LIST_KC], title="封面", text=cover, document_id=index + 1)
        for index in range(4)
    ]
    pages.append(_page(99, kc_codes=[LIST_KC], title="列表", text="列表 append pop 用法讲解。" * 6))
    embedder, store, bm25 = await _build(pages)
    hits, _ = await hybrid_search(
        query="列表 append pop 练习",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
    )
    cover_page_ids = {10, 11, 12, 13}
    cover_hits = [hit for hit in hits if hit.chunk.page_id in cover_page_ids]
    assert len(cover_hits) <= 1, f"重复封面正文应只保留一条：{[h.chunk.page_id for h in cover_hits]}"
    assert 99 in {hit.chunk.page_id for hit in hits}


# ─────────────────── 缺陷 6：前置扩展「预留席位」无相关度下限 ───────────────────


PREREQ_GRAPH = KcGraph(prerequisites={LIST_KC: [VARIABLE_KC], VARIABLE_KC: []})


def _weak_prereq_corpus() -> list[IndexablePage]:
    """前置页只在末尾顺带提一句「列表」：原生分远低于主题页（余弦 0.08 vs 0.88，
    BM25 0.07 vs 9.1），旧实现却因预留席位无相关度下限而稳稳占走 top_k//3。"""
    weak = "变量 命名 规则 与 标识符 的说明，不能以数字开头，区分大小写。" * 6 + " 后面会讲到列表。"
    return [
        _page(1, kc_codes=[LIST_KC], title="列表基础", text="列表 append pop 切片 用法。" * 6),
        _page(2, kc_codes=[LIST_KC], title="列表排序", text="列表 sort sorted 排序 用法。" * 6),
        _page(3, kc_codes=[LIST_KC], title="列表遍历", text="列表 遍历 下标 元素 用法。" * 6),
        _page(50, kc_codes=[VARIABLE_KC], title="变量命名", text=weak),
        _page(51, kc_codes=[VARIABLE_KC], title="标识符", text=weak + "标识符补充说明。"),
    ]


@pytest.mark.asyncio
async def test_irrelevant_prerequisite_pages_do_not_take_reserved_seats() -> None:
    """前置页与提问无关时不得靠预留席位挤掉主题页。"""
    graph = PREREQ_GRAPH
    pages = _weak_prereq_corpus()
    embedder, store, bm25 = await _build(pages)
    hits, _ = await hybrid_search(
        query="列表 append pop 切片",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[LIST_KC],
        kc_graph=graph,
    )
    prereq_pages = {hit.chunk.page_id for hit in hits if hit.via_prerequisite}
    assert prereq_pages == set(), f"无关前置页不应占席位：{prereq_pages}"
    assert {hit.chunk.page_id for hit in hits} == {1, 2, 3}


@pytest.mark.asyncio
async def test_relevant_prerequisite_page_still_joins_results() -> None:
    """相关前置页仍必须进结果——门槛只筛不相关的，不能关掉图扩展。"""
    graph = KcGraph(prerequisites={LIST_KC: [VARIABLE_KC], VARIABLE_KC: []})
    pages = [
        _page(1, kc_codes=[LIST_KC], title="列表基础", text="列表 append pop 切片 用法讲解。" * 6),
        _page(50, kc_codes=[VARIABLE_KC], title="序列与下标", text="列表 切片 下标 从 0 开始计数。" * 6),
    ]
    embedder, store, bm25 = await _build(pages)
    hits, _ = await hybrid_search(
        query="列表 切片 下标",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[LIST_KC],
        kc_graph=graph,
    )
    assert 50 in {hit.chunk.page_id for hit in hits if hit.via_prerequisite}


# ────────────────── 缺陷 7：_relation_for_chunk 取首个匹配 ──────────────────


def test_relation_for_chunk_prefers_prerequisite_regardless_of_kc_order() -> None:
    """kc_codes 来自 array_agg 顺序不定；前置关系必须优先于 co_occur。"""
    relations = {"KC-CO": "co_occur", "KC-PRE": "prerequisite"}
    assert _relation_for_chunk(["KC-CO", "KC-PRE"], relations) == "prerequisite"
    assert _relation_for_chunk(["KC-PRE", "KC-CO"], relations) == "prerequisite"
    assert _relation_for_chunk(["KC-CO"], relations) == "co_occur"
    assert _relation_for_chunk(["KC-OTHER"], relations) is None


@pytest.mark.asyncio
async def test_prerequisite_flag_survives_multi_kc_pages() -> None:
    """同时挂前置 KC 与共现 KC 的页面必须仍被标为前置知识。"""
    pages = [
        _page(1, kc_codes=[LIST_KC], title="列表基础", text="列表 append pop 切片 用法讲解。" * 6),
        _page(
            50,
            kc_codes=["KC-CO", "KC-PRE"],  # array_agg 顺序：共现在前
            title="序列与下标",
            text="列表 切片 下标 从 0 开始计数。" * 6,
        ),
    ]
    embedder, store, bm25 = await _build(pages)
    hits, _ = await hybrid_search(
        query="列表 切片 下标",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[LIST_KC],
        expansion_relations={"KC-CO": "co_occur", "KC-PRE": "prerequisite"},
    )
    expanded = {hit.chunk.page_id: hit for hit in hits if hit.via_relation is not None}
    assert 50 in expanded
    assert expanded[50].via_relation == "prerequisite"
    assert expanded[50].via_prerequisite is True
