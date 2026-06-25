"""A/B gates for the 2026-06-23/24 GraphRAG-style retrieval optimizations."""

from __future__ import annotations

from dataclasses import dataclass

import pytest

from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.graph.kc_graph import RELATION_CO_OCCUR
from pythontopia_rag.graph.router import route_and_retrieve
from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.store import InMemoryVectorStore
from pythontopia_rag.schemas.query import KcEdge


RELATION_COMMON_MISTAKE = "has_common_mistake"


@dataclass(frozen=True)
class RetrievalAbCase:
    name: str
    query: str
    anchor_kc: str
    related_kc: str
    expected_pages: frozenset[int]
    relation: str


AB_CASES = [
    RetrievalAbCase(
        name="prerequisite_return_for_recursion",
        query="为什么递归函数 return 终止条件 看不懂",
        anchor_kc="PY43-KC-021R",
        related_kc="PY43-KC-021",
        expected_pages=frozenset({101, 102}),
        relation="prerequisite",
    ),
    RetrievalAbCase(
        name="co_occur_dictionary_methods_for_word_count",
        query="为什么字典词频统计要先理解 get items keys values",
        anchor_kc="PY43-KC-041",
        related_kc="PY43-KC-042",
        expected_pages=frozenset({201, 202}),
        relation=RELATION_CO_OCCUR,
    ),
    RetrievalAbCase(
        name="common_mistake_input_string_plus_number",
        query="input 加 1 为什么 TypeError 需要 int 转换",
        anchor_kc="PY43-KC-001",
        related_kc="PY43-KC-009",
        expected_pages=frozenset({301, 302}),
        relation=RELATION_COMMON_MISTAKE,
    ),
]


def _pages() -> list[IndexablePage]:
    pages: list[IndexablePage] = [
        IndexablePage(
            page_id=101,
            page_no=70,
            document_id=1,
            chapter_id=7,
            kc_codes=["PY43-KC-021R"],
            title="递归函数",
            text="递归函数在函数体内调用自身，必须有终止条件。" * 8,
            chapter_no=7,
            chapter_title="第七章：归纳与抽象",
        ),
        IndexablePage(
            page_id=102,
            page_no=15,
            document_id=1,
            chapter_id=7,
            kc_codes=["PY43-KC-021"],
            title="函数定义与返回值",
            text="def 定义函数，return 把结果返回给调用者，没有 return 会返回 None。" * 8,
            chapter_no=7,
            chapter_title="第七章：归纳与抽象",
        ),
        IndexablePage(
            page_id=201,
            page_no=61,
            document_id=2,
            chapter_id=5,
            kc_codes=["PY43-KC-041"],
            title="字典词频统计",
            text="字典可以用于统计词频，解决键值映射类问题。" * 8,
            chapter_no=5,
            chapter_title="第五章：数据结构",
        ),
        IndexablePage(
            page_id=202,
            page_no=62,
            document_id=2,
            chapter_id=5,
            kc_codes=["PY43-KC-042"],
            title="字典基本操作",
            text="get items keys values 是完成词频统计前需要掌握的字典基本操作。" * 8,
            chapter_no=5,
            chapter_title="第五章：数据结构",
        ),
        IndexablePage(
            page_id=301,
            page_no=8,
            document_id=3,
            chapter_id=2,
            kc_codes=["PY43-KC-001"],
            title="input 输入",
            text="input 会读取用户输入，得到的是字符串文本。" * 8,
            chapter_no=2,
            chapter_title="第二章：输入与输出",
        ),
        IndexablePage(
            page_id=302,
            page_no=18,
            document_id=3,
            chapter_id=3,
            kc_codes=["PY43-KC-009"],
            title="类型转换",
            text="字符串和数字不能直接相加，input 的结果常需要 int 转换再参与计算。" * 8,
            chapter_no=3,
            chapter_title="第三章：数据类型",
        ),
        IndexablePage(
            page_id=9900,
            page_no=99,
            document_id=9,
            chapter_id=9,
            kc_codes=["PY43-KC-999"],
            title="无关页面",
            text="这页包含 return get input 等词，但 KC 不在锚点或扩展子图中。" * 8,
            chapter_no=9,
            chapter_title="噪声页",
        ),
    ]
    return pages


def _neighbors(case: RetrievalAbCase):
    def _fn(kc: str, hops: int) -> list[KcEdge]:
        if kc != case.anchor_kc:
            return []
        return [
            KcEdge(
                from_kc=case.anchor_kc,
                to_kc=case.related_kc,
                relation=case.relation,
            )
        ]

    return _fn


async def _build_index() -> tuple[HashEmbedder, InMemoryVectorStore, Bm25Index]:
    embedder = HashEmbedder()
    store = InMemoryVectorStore()
    bm25 = Bm25Index()
    await rebuild_index(
        source=InMemoryPageSource(pages=_pages()),
        embedder=embedder,
        store=store,
        bm25=bm25,
    )
    return embedder, store, bm25


def _recall(pages: set[int], expected: frozenset[int]) -> float:
    return len(pages & expected) / len(expected)


@pytest.mark.asyncio
async def test_graph_retrieval_stably_beats_strict_kc_baseline_across_recent_ab_cases() -> None:
    embedder, store, bm25 = await _build_index()
    wins = 0
    rows: list[tuple[str, float, float, int, int]] = []

    for case in AB_CASES:
        baseline = await route_and_retrieve(
            query=case.query,
            embedder=embedder,
            store=store,
            bm25=bm25,
            top_k=6,
            min_score=0.0,
            kc_codes=[case.anchor_kc],
            graph_mode="off",
        )
        treatment = await route_and_retrieve(
            query=case.query,
            embedder=embedder,
            store=store,
            bm25=bm25,
            top_k=6,
            min_score=0.0,
            kc_codes=[case.anchor_kc],
            graph_mode="force",
            neighbors_fn=_neighbors(case),
        )

        baseline_pages = {hit.chunk.page_id for hit in baseline.hits}
        treatment_pages = {hit.chunk.page_id for hit in treatment.hits}
        baseline_recall = _recall(baseline_pages, case.expected_pages)
        treatment_recall = _recall(treatment_pages, case.expected_pages)
        rows.append(
            (
                case.name,
                baseline_recall,
                treatment_recall,
                len(baseline_pages - case.expected_pages),
                len(treatment_pages - case.expected_pages),
            )
        )

        assert baseline.route == "vector"
        assert treatment.route == "graph"
        assert 9900 not in treatment_pages
        assert treatment.graph_trace is not None
        assert treatment.graph_trace.supplemental_hit_count >= 1
        assert treatment.graph_trace.supplemental_hit_count <= treatment.graph_trace.supplemental_hit_limit
        assert treatment.graph_trace.relation_counts[case.relation] == 1
        assert treatment_recall > baseline_recall
        wins += 1

    win_rate = wins / len(AB_CASES)
    mean_baseline = sum(row[1] for row in rows) / len(rows)
    mean_treatment = sum(row[2] for row in rows) / len(rows)

    assert win_rate == 1.0, rows
    assert mean_treatment - mean_baseline >= 0.45, rows
