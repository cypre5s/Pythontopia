"""AGENTS.md RAG 验收烟测：在仓库内置的真实课程种子（packId=43，561 页）上跑混合检索。

不依赖 Postgres：直接读 data/course-pack-43/json 种子；种子缺失（如服务目录单独构建）时跳过。
三个验收查询「input函数」「for循环」「列表」断言 hybrid 模式命中且 top-3 文本与查询主题相关。
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pytest

from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.fusion import hybrid_search
from pythontopia_rag.index.store import InMemoryVectorStore

_SEED_DIR = Path(__file__).resolve().parents[3] / "data" / "course-pack-43" / "json"

pytestmark = pytest.mark.skipif(
    not (_SEED_DIR / "course_page.json").exists(),
    reason="course-pack-43 种子 JSON 不在本机（服务目录单独构建时跳过）",
)


def _load_real_pages() -> list[IndexablePage]:
    raw_pages: list[dict[str, Any]] = json.loads((_SEED_DIR / "course_page.json").read_text())
    chapters: dict[int, dict[str, Any]] = {
        chapter["id"]: chapter
        for chapter in json.loads((_SEED_DIR / "course_chapter.json").read_text())
    }
    pages: list[IndexablePage] = []
    for page in raw_pages:
        chapter = chapters.get(page["chapter_id"]) or {}
        pages.append(
            IndexablePage(
                page_id=page["id"],
                page_no=page["page_no"],
                document_id=page["document_id"],
                chapter_id=page["chapter_id"],
                kc_codes=[],
                title=page.get("title") or "",
                text=page.get("content_markdown") or "",
                chapter_no=chapter.get("order_index"),
                chapter_title=chapter.get("title", ""),
            )
        )
    return pages


@pytest.mark.asyncio
async def test_acceptance_queries_on_real_561_page_corpus() -> None:
    pages = _load_real_pages()
    assert len(pages) == 561, "packId=43 种子应有 561 页课件"

    embedder = HashEmbedder()
    store = InMemoryVectorStore()
    bm25 = Bm25Index()
    result = await rebuild_index(
        source=InMemoryPageSource(pages=pages), embedder=embedder, store=store, bm25=bm25
    )
    assert result.built_chunks >= len(pages)

    # 查询 -> top-3 至少一条原文须包含的主题词（宽断言，不钉死页码，种子演进不脆断）
    expectations = {
        "input函数": "input(",
        "for循环": "for",
        "列表": "列表",
    }
    for query, must_contain in expectations.items():
        hits, mode = await hybrid_search(
            query=query,
            embedder=embedder,
            store=store,
            bm25=bm25,
            top_k=3,
            min_score=0.0,
        )
        assert mode == "hybrid"
        assert len(hits) == 3
        assert any(must_contain in hit.chunk.text for hit in hits), (
            f"{query!r} top-3 未含主题词 {must_contain!r}"
        )
        for hit in hits:
            assert "《Python语言基础》" not in hit.chunk.text, "上下文前缀泄漏到证据原文"
