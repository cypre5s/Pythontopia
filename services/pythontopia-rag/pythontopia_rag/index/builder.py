from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Iterable, cast

from pythontopia_rag.data.page_source import IndexablePage, PageSource
from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.chunker import Chunk, chunk_page
from pythontopia_rag.index.embedder import Embedder
from pythontopia_rag.index.store import VectorStore

# packId=43 唯一课程底座，前缀课程名为确定性常量，不调 LLM。
COURSE_TITLE = "Python语言基础"


@dataclass
class RebuildResult:
    built_chunks: int
    elapsed_ms: int
    embed_model: str
    store: str
    rebuilt_at: str


def build_context_prefix(page: IndexablePage) -> str:
    """页级上下文一句话（Anthropic contextual retrieval 思路的确定性拼接版）。

    形如「《Python语言基础》第四章：流程自动化 第48页：遍历循环: for语句」，
    由课程名+章名+页码+页标题拼接，仅前置到 embed_text，不进入展示原文。
    """
    chapter = (page.chapter_title or "").strip()
    if not chapter and page.chapter_no is not None:
        chapter = f"第{page.chapter_no}章"
    parts = [f"《{COURSE_TITLE}》"]
    if chapter:
        parts.append(chapter)
    parts.append(f"第{page.page_no}页")
    head = " ".join(parts)
    title = page.title.strip()
    return f"{head}：{title}" if title else f"{head}："


async def rebuild_index(
    *,
    source: PageSource,
    embedder: Embedder,
    store: VectorStore,
    bm25: Bm25Index | None = None,
    batch_size: int = 64,
) -> RebuildResult:
    started = datetime.now(timezone.utc)
    pages: list[IndexablePage] = list(await _iter_pages(source))
    all_chunks: list[Chunk] = []
    for page in pages:
        all_chunks.extend(
            chunk_page(
                page_id=page.page_id,
                page_no=page.page_no,
                document_id=page.document_id,
                chapter_id=page.chapter_id,
                kc_codes=page.kc_codes,
                text=page.text,
                context_prefix=build_context_prefix(page),
            )
        )

    await store.clear()
    for batch_index in range(0, len(all_chunks), batch_size):
        batch = all_chunks[batch_index : batch_index + batch_size]
        vectors = await embedder.embed([chunk.index_text for chunk in batch])
        await store.upsert(batch, vectors)
    if bm25 is not None:
        bm25.build(all_chunks)
    rebuilt_at = datetime.now(timezone.utc).isoformat()
    mark = getattr(store, "mark_rebuilt", None)
    if callable(mark):
        mark(rebuilt_at)
    elapsed = int((datetime.now(timezone.utc) - started).total_seconds() * 1000)
    return RebuildResult(
        built_chunks=len(all_chunks),
        elapsed_ms=elapsed,
        embed_model=embedder.name,
        store=store.name,
        rebuilt_at=rebuilt_at,
    )


async def _iter_pages(source: PageSource) -> list[IndexablePage]:
    result: object = source.iter_pages()
    if asyncio.iscoroutine(result):
        result = await result
    return list(cast("Iterable[IndexablePage]", result))
