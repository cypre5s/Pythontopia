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


class EmbeddingDegradedError(RuntimeError):
    """重建过程中嵌入后端由真实语义向量降级为兜底 hash —— 索引会混入两套向量空间。

    维度相同（如智谱 embedding-3 的 2048 维与同维 hash 兜底）时任何校验都发现不了，
    但降级前后的向量互相做余弦等于随机噪声。此处**直接失败**而非静默续跑，
    调用方应修复凭据/网络后重跑（旧索引未被触碰，仍可继续服务）。
    """


@dataclass
class RebuildResult:
    built_chunks: int
    elapsed_ms: int
    embed_model: str
    store: str
    rebuilt_at: str
    chunks: list[Chunk]
    # 本次索引是否整体由降级嵌入构建（启动即无可用凭据的场景）；禁止静默降级，须暴露。
    degraded: bool = False
    fallback_reason: str | None = None


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

    # 先把全部向量算完再动索引：embed 阶段任何失败都不能留下"空向量库 + 陈旧 BM25"
    # 的分裂状态（clear() 一旦先跑，向量库清空后失败就再也回不来，而 BM25 还持旧 chunk，
    # 查询会拿旧索引证据，/rag/index/status 却报 built_chunks=0）。
    started_degraded, _ = _embedder_degradation(embedder)
    embedded: list[tuple[list[Chunk], list[list[float]]]] = []
    for batch_index in range(0, len(all_chunks), batch_size):
        batch = all_chunks[batch_index : batch_index + batch_size]
        vectors = await embedder.embed([chunk.index_text for chunk in batch])
        degraded, reason = _embedder_degradation(embedder)
        if degraded and not started_degraded:
            raise EmbeddingDegradedError(
                f"embedder degraded to fallback mid-rebuild after {len(embedded) * batch_size}"
                f"/{len(all_chunks)} chunks; index left untouched. reason: {reason}"
            )
        embedded.append((batch, vectors))

    await store.clear()
    for batch, vectors in embedded:
        await store.upsert(batch, vectors)
    if bm25 is not None:
        bm25.build(all_chunks)
    rebuilt_at = datetime.now(timezone.utc).isoformat()
    mark = getattr(store, "mark_rebuilt", None)
    if callable(mark):
        mark(rebuilt_at)
    degraded, reason = _embedder_degradation(embedder)
    mark_degraded = getattr(store, "mark_degraded", None)
    if callable(mark_degraded):
        mark_degraded(degraded, reason)
    elapsed = int((datetime.now(timezone.utc) - started).total_seconds() * 1000)
    return RebuildResult(
        built_chunks=len(all_chunks),
        elapsed_ms=elapsed,
        embed_model=embedder.name,
        store=store.name,
        rebuilt_at=rebuilt_at,
        chunks=all_chunks,
        degraded=degraded,
        fallback_reason=reason,
    )


def _embedder_degradation(embedder: Embedder) -> tuple[bool, str | None]:
    """读取 embedder 的降级身份；Hash 会自报非语义降级，Zhipu strict 不带该标志。"""
    using_fallback = getattr(embedder, "using_fallback", None)
    if using_fallback is None:
        return False, None
    return bool(using_fallback), getattr(embedder, "fallback_reason", None)


async def _iter_pages(source: PageSource) -> list[IndexablePage]:
    result: object = source.iter_pages()
    if asyncio.iscoroutine(result):
        result = await result
    return list(cast("Iterable[IndexablePage]", result))
