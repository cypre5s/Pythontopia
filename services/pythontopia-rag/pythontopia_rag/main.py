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
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.routes.query import router as query_router

logger = logging.getLogger(__name__)


class HealthResponse(BaseModel):
    status: str


async def _self_heal_empty_index(
    app: FastAPI, db_url: str, *, attempts: int = 60, delay_s: float = 5.0
) -> None:
    """索引为空时后台轮询 DB，等课程种子落库后自动重建一次。

    根因：RAG 服务（docker-compose 里）只 ``depends_on postgres healthy``，不等后端
    Flyway 把课程包种子写入。若 RAG 先于种子启动，初始索引为空，之后所有按 KC 的检索
    都会 NO_EVIDENCE，使资源讲义回落「课件检索未命中」兜底。这里让 RAG 自愈：发现空
    索引就轮询，课程页一旦出现立即重建一次，免去人工 ``init_rag_index.sh``。
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
        result = await rebuild_index(
            source=app.state.rag_page_source,
            embedder=app.state.rag.embedder,
            store=app.state.rag.store,
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
    except Exception as exc:  # fail-soft: keep booting even when the DB is unavailable
        logger.warning("RAG page load failed; starting with empty index: %s", exc)
        pages = []
    app.state.rag_page_source = InMemoryPageSource(pages=pages)
    await rebuild_index(
        source=app.state.rag_page_source,
        embedder=app.state.rag.embedder,
        store=app.state.rag.store,
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


@app.get("/healthz", response_model=HealthResponse)
def healthz() -> HealthResponse:
    return HealthResponse(status="ok")
