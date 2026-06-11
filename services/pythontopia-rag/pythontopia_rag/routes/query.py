from __future__ import annotations

import logging
import os

from fastapi import APIRouter, Depends, Header, HTTPException, Request, status

from pythontopia_rag.app_state import AppState
from pythontopia_rag.data.loader import DEFAULT_DB_URL, fetch_pages
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.fusion import hybrid_search
from pythontopia_rag.index.kc_graph import fetch_kc_graph
from pythontopia_rag.data.page_source import InMemoryPageSource, page_records_to_indexable
from pythontopia_rag.safety.forbidden import is_forbidden
from pythontopia_rag.schemas.query import (
    RagHit,
    RagIndexStatus,
    RagQueryRequest,
    RagQueryResponse,
    RagRebuildResponse,
)


logger = logging.getLogger(__name__)
router = APIRouter()


def _state(request: Request) -> AppState:
    state: AppState = request.app.state.rag
    return state


@router.post("/rag/query", response_model=RagQueryResponse)
async def rag_query(
    body: RagQueryRequest,
    state: AppState = Depends(_state),
) -> RagQueryResponse:
    """课件混合检索（BM25+向量 RRF 融合，BM25 缺失退回纯向量）。

    防幻觉门控在 fusion 层完成：向量候选按 min_score（余弦）过滤、BM25 候选须正分，
    两路皆空则 no_evidence_found=True。hit.text 始终是课件原文片段（不含上下文前缀）。
    """
    if is_forbidden(body.query):
        return RagQueryResponse(hits=[], no_evidence_found=True)
    hits, mode = await hybrid_search(
        query=body.query,
        embedder=state.embedder,
        store=state.store,
        bm25=state.bm25,
        top_k=body.top_k,
        min_score=body.min_score,
        kc_codes=body.kc_codes,
        chapter_ids=body.chapter_ids,
        kc_graph=state.kc_graph,
        expand_prerequisites=body.expand_prerequisites,
    )
    if not hits:
        return RagQueryResponse(hits=[], no_evidence_found=True, retrieval_mode=mode)
    response_hits = [
        RagHit(
            chunk_id=hit.chunk.chunk_id,
            page_id=hit.chunk.page_id,
            page_no=hit.chunk.page_no,
            chapter_id=hit.chunk.chapter_id,
            kc_codes=hit.chunk.kc_codes,
            text=hit.chunk.text,
            score=round(hit.score, 4),
            via_prerequisite=hit.via_prerequisite,
        )
        for hit in hits
    ]
    return RagQueryResponse(hits=response_hits, no_evidence_found=False, retrieval_mode=mode)


def _require_admin_token(x_internal_token: str | None) -> None:
    expected = os.environ.get("PYT_RAG_ADMIN_TOKEN", "")
    if not expected:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="PYT_RAG_ADMIN_TOKEN not configured",
        )
    if x_internal_token != expected:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid token")


def _resolve_rebuild_source(request: Request) -> InMemoryPageSource:
    """重建前从 DB 重读课程页，确保索引反映最新种子；DB 不可用时回退启动快照。"""
    db_url = os.environ.get("PYT_DB_URL", DEFAULT_DB_URL)
    try:
        pages = page_records_to_indexable(fetch_pages(db_url))
    except Exception as exc:  # fail-soft：DB 不可用时回退启动快照
        logger.warning("RAG rebuild DB fetch failed; using startup snapshot: %s", exc)
        pages = None
    if pages:
        source = InMemoryPageSource(pages=pages)
        request.app.state.rag_page_source = source
        return source
    existing = getattr(request.app.state, "rag_page_source", None)
    return existing if existing is not None else InMemoryPageSource(pages=[])


@router.post("/rag/index/rebuild", response_model=RagRebuildResponse)
async def rag_rebuild(
    request: Request,
    x_internal_token: str | None = Header(default=None, alias="X-Internal-Token"),
) -> RagRebuildResponse:
    _require_admin_token(x_internal_token)
    state: AppState = request.app.state.rag
    source = _resolve_rebuild_source(request)
    result = await rebuild_index(
        source=source, embedder=state.embedder, store=state.store, bm25=state.bm25
    )
    # 重建顺带刷新 KC 前置图（重灌库后前置关系可能变化；失败保留旧图）。
    refreshed = fetch_kc_graph(os.environ.get("PYT_DB_URL", DEFAULT_DB_URL))
    if refreshed.size > 0:
        state.kc_graph = refreshed
    return RagRebuildResponse(
        built_chunks=result.built_chunks,
        elapsed_ms=result.elapsed_ms,
        embed_model=result.embed_model,
        store=result.store,
    )


@router.get("/rag/index/status", response_model=RagIndexStatus)
async def rag_status(state: AppState = Depends(_state)) -> RagIndexStatus:
    status_info = await state.store.status()
    return RagIndexStatus(
        built_chunks=status_info.built_chunks,
        last_rebuilt_at=status_info.last_rebuilt_at,
        embed_model=state.embedder.name,
        store=state.store.name,
    )
