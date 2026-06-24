from __future__ import annotations

import logging
import os

from fastapi import APIRouter, Depends, Header, HTTPException, Request, status

from pythontopia_rag.app_state import AppState
from pythontopia_rag.data.loader import DEFAULT_DB_URL, fetch_pages
from pythontopia_rag.graph.kc_graph import build_kc_graph_from_chunks
from pythontopia_rag.graph.router import route_and_retrieve
from pythontopia_rag.graph.traverse import traverse_subgraph
from pythontopia_rag.index.builder import rebuild_index
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


def _runtime_neighbors_fn(state: AppState):
    """Return a relation-aware neighbors function when the runtime KC graph is available."""
    graph = state.knowledge_graph
    if graph.is_empty:
        rows = getattr(state.store, "rows", None)
        if rows:
            chunks = [row.chunk for row in rows]
            graph = build_kc_graph_from_chunks(chunks, prerequisites=state.kc_graph.prerequisites)
            state.knowledge_graph = graph
    if graph.is_empty:
        return None

    def _neighbors(kc: str, hops: int):
        _nodes, edges = traverse_subgraph(graph, [kc], hops)
        return edges

    return _neighbors


@router.post("/rag/query", response_model=RagQueryResponse)
async def rag_query(
    body: RagQueryRequest,
    state: AppState = Depends(_state),
) -> RagQueryResponse:
    """课件检索 + GraphRAG 自适应路由（向后兼容）。

    路由层（`graph/router.py`）：graph_mode 未传=旧版混合检索；off/auto/force 为自适应路由
    （简单走向量、多跳调 G1a 前置图扩展并标 via_prerequisite，回带可审计 subgraph）。
    防幻觉门控仍在 fusion 层：min_score 过滤 + BM25 正分，两路皆空则 no_evidence_found=True。
    hit.text 始终是课件原文片段（不含上下文前缀）；no_evidence 时下游不得伪造页码引用。
    """
    if is_forbidden(body.query):
        return RagQueryResponse(hits=[], no_evidence_found=True)
    result = await route_and_retrieve(
        query=body.query,
        embedder=state.embedder,
        store=state.store,
        bm25=state.bm25,
        top_k=body.top_k,
        min_score=body.min_score,
        kc_codes=body.kc_codes,
        chapter_ids=body.chapter_ids,
        index_kc_graph=state.kc_graph,
        graph_mode=body.graph_mode,
        prereq_hops=body.prereq_hops,
        expand_prerequisites=body.expand_prerequisites,
        neighbors_fn=_runtime_neighbors_fn(state),
    )
    if not result.hits:
        return RagQueryResponse(
            hits=[],
            no_evidence_found=True,
            retrieval_mode=result.retrieval_mode,
            route=result.route,
            subgraph=result.subgraph,
            graph_trace=result.graph_trace,
        )
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
            via_relation=hit.via_relation,
        )
        for hit in result.hits
    ]
    return RagQueryResponse(
        hits=response_hits,
        no_evidence_found=False,
        retrieval_mode=result.retrieval_mode,
        route=result.route,
        subgraph=result.subgraph,
        graph_trace=result.graph_trace,
    )


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
    state.knowledge_graph = build_kc_graph_from_chunks(
        result.chunks, prerequisites=state.kc_graph.prerequisites
    )
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
