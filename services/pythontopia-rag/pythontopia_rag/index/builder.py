from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import datetime, timezone

from pythontopia_rag.data.page_source import IndexablePage, PageSource
from pythontopia_rag.index.chunker import Chunk, chunk_page
from pythontopia_rag.index.embedder import Embedder
from pythontopia_rag.index.store import VectorStore


@dataclass
class RebuildResult:
    built_chunks: int
    elapsed_ms: int
    embed_model: str
    store: str
    rebuilt_at: str


async def rebuild_index(
    *, source: PageSource, embedder: Embedder, store: VectorStore, batch_size: int = 64
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
            )
        )

    await store.clear()
    for batch_index in range(0, len(all_chunks), batch_size):
        batch = all_chunks[batch_index : batch_index + batch_size]
        vectors = await embedder.embed([chunk.text for chunk in batch])
        await store.upsert(batch, vectors)
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
    result = source.iter_pages()
    if asyncio.iscoroutine(result):
        result = await result
    return list(result)
