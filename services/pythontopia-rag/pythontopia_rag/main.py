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
    yield


app = FastAPI(title="Pythontopia RAG", version="0.1.0", lifespan=lifespan)
app.state.rag = create_default_state()
app.include_router(query_router)


@app.get("/healthz", response_model=HealthResponse)
def healthz() -> HealthResponse:
    return HealthResponse(status="ok")
