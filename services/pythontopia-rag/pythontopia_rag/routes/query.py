from __future__ import annotations

import os

from fastapi import APIRouter, Depends, Header, HTTPException, Request, status

from pythontopia_rag.app_state import AppState
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.data.page_source import InMemoryPageSource
from pythontopia_rag.safety.forbidden import is_forbidden
from pythontopia_rag.schemas.query import (
    RagHit,
    RagIndexStatus,
    RagQueryRequest,
    RagQueryResponse,
    RagRebuildResponse,
)


router = APIRouter()


def _state(request: Request) -> AppState:
    state: AppState = request.app.state.rag
    return state


@router.post("/rag/query", response_model=RagQueryResponse)
async def rag_query(
    body: RagQueryRequest,
    state: AppState = Depends(_state),
) -> RagQueryResponse:
    if is_forbidden(body.query):
        return RagQueryResponse(hits=[], no_evidence_found=True)
    vectors = await state.embedder.embed([body.query])
    if not vectors:
        return RagQueryResponse(hits=[], no_evidence_found=True)
    hits = await state.store.search(
        vectors[0],
        body.top_k,
        kc_codes=body.kc_codes,
        chapter_ids=body.chapter_ids,
    )
    filtered = [hit for hit in hits if hit.score >= body.min_score]
    if not filtered:
        return RagQueryResponse(hits=[], no_evidence_found=True)
    response_hits = [
        RagHit(
            chunk_id=hit.chunk.chunk_id,
            page_id=hit.chunk.page_id,
            page_no=hit.chunk.page_no,
            chapter_id=hit.chunk.chapter_id,
            kc_codes=hit.chunk.kc_codes,
            text=hit.chunk.text,
            score=round(hit.score, 4),
        )
        for hit in filtered
    ]
    return RagQueryResponse(hits=response_hits, no_evidence_found=False)


def _require_admin_token(x_internal_token: str | None) -> None:
    expected = os.environ.get("PYT_RAG_ADMIN_TOKEN", "")
    if not expected:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="PYT_RAG_ADMIN_TOKEN not configured",
        )
    if x_internal_token != expected:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid token")


@router.post("/rag/index/rebuild", response_model=RagRebuildResponse)
async def rag_rebuild(
    request: Request,
    x_internal_token: str | None = Header(default=None, alias="X-Internal-Token"),
) -> RagRebuildResponse:
    _require_admin_token(x_internal_token)
    state: AppState = request.app.state.rag
    source = getattr(request.app.state, "rag_page_source", None)
    if source is None:
        source = InMemoryPageSource(pages=[])
    result = await rebuild_index(source=source, embedder=state.embedder, store=state.store)
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
