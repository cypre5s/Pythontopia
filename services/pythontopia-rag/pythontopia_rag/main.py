import asyncio
import logging
import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel

from pythontopia_rag.app_state import create_default_state
from pythontopia_rag.data.loader import DEFAULT_DB_URL, fetch_pages
from pythontopia_rag.data.page_source import InMemoryPageSource, page_records_to_indexable
from pythontopia_rag.graph.kc_graph import build_kc_graph_from_chunks
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.kc_graph import fetch_kc_graph
from pythontopia_rag.routes.embed import router as embed_router
from pythontopia_rag.routes.query import router as query_router

logger = logging.getLogger(__name__)


class HealthResponse(BaseModel):
    status: str


async def _self_heal_empty_index(
    app: FastAPI, db_url: str, *, attempts: int = 60, delay_s: float = 5.0
) -> None:
    """索引为空时后台轮询 DB，课程种子落库后自动重建一次。

    RAG 启动可能早于 Flyway 种子写入，初始空索引会导致检索均返回 no_evidence_found。
    轮询到课程页后立即重建，避免人工执行 init_rag_index.sh。
    """
    for _ in range(attempts):
        await asyncio.sleep(delay_s)
        try:
            pages = page_records_to_indexable(fetch_pages(db_url))
        except Exception as exc:  # noqa: BLE001 - 轮询期 DB 抖动不应中断自愈
            logger.warning("RAG self-heal DB fetch failed; will retry: %s", exc)
            continue
        if not pages:
            continue
        app.state.rag_page_source = InMemoryPageSource(pages=pages)
        app.state.rag.kc_graph = fetch_kc_graph(db_url)
        result = await rebuild_index(
            source=app.state.rag_page_source,
            embedder=app.state.rag.embedder,
            store=app.state.rag.store,
            bm25=app.state.rag.bm25,
        )
        app.state.rag.knowledge_graph = build_kc_graph_from_chunks(
            result.chunks, prerequisites=app.state.rag.kc_graph.prerequisites
        )
        logger.info(
            "RAG self-heal 成功：课程种子就绪后重建索引，built_chunks=%d", result.built_chunks
        )
        return
    logger.warning("RAG self-heal 轮询结束仍未取到课程页；请确认种子已导入并手动重建索引")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    db_url = os.environ.get("PYT_DB_URL", DEFAULT_DB_URL)
    try:
        pages = page_records_to_indexable(fetch_pages(db_url))
    except Exception as exc:  # fail-soft：DB 不可用时仍启动，索引为空
        logger.warning("RAG page load failed; starting with empty index: %s", exc)
        pages = []
    app.state.rag_page_source = InMemoryPageSource(pages=pages)
    # KC 前置图：与课程页同源加载；失败为空图（检索退化为不扩展，fail-soft）。
    app.state.rag.kc_graph = fetch_kc_graph(db_url)
    result = await rebuild_index(
        source=app.state.rag_page_source,
        embedder=app.state.rag.embedder,
        store=app.state.rag.store,
        bm25=app.state.rag.bm25,
    )
    app.state.rag.knowledge_graph = build_kc_graph_from_chunks(
        result.chunks, prerequisites=app.state.rag.kc_graph.prerequisites
    )
    heal_task: asyncio.Task[None] | None = None
    if not pages:
        logger.warning("RAG 启动时索引为空（课程种子可能尚未落库），启动后台自愈轮询")
        heal_task = asyncio.create_task(_self_heal_empty_index(app, db_url))
    try:
        yield
    finally:
        if heal_task is not None:
            heal_task.cancel()


app = FastAPI(title="Pythontopia RAG", version="0.1.0", lifespan=lifespan)
app.state.rag = create_default_state()
app.include_router(query_router)
app.include_router(embed_router)


@app.get("/healthz", response_model=HealthResponse)
def healthz() -> HealthResponse:
    return HealthResponse(status="ok")
