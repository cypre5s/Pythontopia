"""黄金集 Recall@K 回归：hybrid（BM25+向量 RRF）对比 vector_only。

语料与标注见 tests/golden_retrieval.py（38 页 fixture、59 条查询）。
HashEmbedder 与 BM25 均为确定性实现，本评测无随机性，可作回归门禁。
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass

import pytest

from pythontopia_rag.data.page_source import InMemoryPageSource
from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.fusion import hybrid_search
from pythontopia_rag.index.store import InMemoryVectorStore

# pytest 默认 importmode=prepend 会把 tests/ 注入 sys.path，按兄弟模块导入黄金集数据。
from golden_retrieval import GOLDEN_CASES, GoldenCase, fixture_pages

RECALL_K = 5
HYBRID_RECALL_FLOOR = 0.8


@dataclass
class EvalReport:
    recall: float
    total: int
    hit_count: int
    by_category: dict[str, tuple[int, int]]
    misses: list[str]


async def _build_indexes() -> tuple[HashEmbedder, InMemoryVectorStore, Bm25Index]:
    embedder = HashEmbedder()
    store = InMemoryVectorStore()
    bm25 = Bm25Index()
    await rebuild_index(
        source=InMemoryPageSource(pages=fixture_pages()),
        embedder=embedder,
        store=store,
        bm25=bm25,
    )
    return embedder, store, bm25


async def _evaluate(
    embedder: HashEmbedder,
    store: InMemoryVectorStore,
    bm25: Bm25Index | None,
    cases: list[GoldenCase],
) -> EvalReport:
    hit_count = 0
    by_category: dict[str, list[int]] = defaultdict(lambda: [0, 0])
    misses: list[str] = []
    for case in cases:
        hits, _ = await hybrid_search(
            query=case.query,
            embedder=embedder,
            store=store,
            bm25=bm25,
            top_k=RECALL_K,
            min_score=0.0,
        )
        retrieved_pages = {hit.chunk.page_id for hit in hits}
        matched = bool(retrieved_pages & case.expected_page_ids)
        by_category[case.category][1] += 1
        if matched:
            hit_count += 1
            by_category[case.category][0] += 1
        else:
            misses.append(case.query)
    total = len(cases)
    return EvalReport(
        recall=hit_count / total if total else 0.0,
        total=total,
        hit_count=hit_count,
        by_category={key: (value[0], value[1]) for key, value in by_category.items()},
        misses=misses,
    )


def _format_report(label: str, report: EvalReport) -> str:
    lines = [
        f"[golden] {label} Recall@{RECALL_K} = {report.recall:.3f}"
        f" ({report.hit_count}/{report.total})"
    ]
    for category, (hit, total) in sorted(report.by_category.items()):
        lines.append(f"[golden]   {category}: {hit}/{total}")
    if report.misses:
        lines.append(f"[golden]   misses: {', '.join(report.misses)}")
    return "\n".join(lines)


def test_golden_set_size_meets_requirement() -> None:
    assert len(GOLDEN_CASES) >= 50
    assert len(fixture_pages()) >= 30
    categories = {case.category for case in GOLDEN_CASES}
    assert {"concept", "code", "confusable", "colloquial"} <= categories


@pytest.mark.asyncio
async def test_hybrid_recall_at_5_meets_floor_and_beats_vector_only() -> None:
    embedder, store, bm25 = await _build_indexes()

    hybrid_report = await _evaluate(embedder, store, bm25, GOLDEN_CASES)
    vector_report = await _evaluate(embedder, store, None, GOLDEN_CASES)

    print()
    print(_format_report("hybrid", hybrid_report))
    print(_format_report("vector_only", vector_report))

    assert hybrid_report.recall >= HYBRID_RECALL_FLOOR, _format_report("hybrid", hybrid_report)
    assert hybrid_report.recall >= vector_report.recall, (
        f"hybrid {hybrid_report.recall:.3f} < vector_only {vector_report.recall:.3f}"
    )


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("query", "expected_pages"),
    [
        ("input函数", {107, 108}),
        ("for循环", {116, 117}),
        ("列表", {121, 122, 126}),
    ],
)
async def test_agents_md_acceptance_queries_hit_top3(
    query: str, expected_pages: set[int]
) -> None:
    """AGENTS.md RAG 验收三查询：在黄金语料上断言 top-3 命中并打印引用页供人工抽检。"""
    embedder, store, bm25 = await _build_indexes()
    hits, mode = await hybrid_search(
        query=query,
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=3,
        min_score=0.0,
    )
    assert mode == "hybrid"
    assert hits
    print(f"\n[acceptance] query={query!r} mode={mode}")
    for rank, hit in enumerate(hits, start=1):
        chunk = hit.chunk
        print(
            f"[acceptance]   #{rank} page_id={chunk.page_id} chapter={chunk.chapter_id}"
            f" page_no={chunk.page_no} score={hit.score:.4f} text={chunk.text[:40]!r}"
        )
    top_pages = {hit.chunk.page_id for hit in hits}
    assert top_pages & expected_pages, f"{query} top3 未命中期望页：{top_pages}"
