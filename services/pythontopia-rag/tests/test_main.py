from __future__ import annotations

import asyncio
import threading
from types import SimpleNamespace

import pytest
from fastapi import FastAPI

from pythontopia_rag import main as rag_main
from pythontopia_rag.app_state import AppState
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.kc_graph import KcGraph
from pythontopia_rag.index.store import InMemoryVectorStore


@pytest.mark.asyncio
async def test_startup_database_failure_does_not_log_database_url(monkeypatch, caplog) -> None:
    secret = "postgresql://reader:database-secret-sentinel@db.internal/course"

    def fail_fetch(_db_url: str):
        raise RuntimeError(secret)

    async def rebuild_without_chunks(**_kwargs):
        return SimpleNamespace(chunks=[], built_chunks=0)

    async def skip_self_heal(*_args, **_kwargs):
        return None

    monkeypatch.setattr(rag_main, "fetch_pages", fail_fetch)
    monkeypatch.setattr(rag_main, "fetch_kc_graph", lambda _db_url: KcGraph())
    monkeypatch.setattr(rag_main, "rebuild_index", rebuild_without_chunks)
    monkeypatch.setattr(rag_main, "_self_heal_empty_index", skip_self_heal)
    app = FastAPI()
    app.state.rag = AppState(embedder=HashEmbedder(), store=InMemoryVectorStore())

    async with rag_main.lifespan(app):
        pass

    assert "RuntimeError" in caplog.text
    assert secret not in caplog.text


@pytest.mark.asyncio
async def test_self_heal_database_failure_does_not_log_database_url(monkeypatch, caplog) -> None:
    secret = "postgresql://reader:database-secret-sentinel@db.internal/course"

    def fail_fetch(_db_url: str):
        raise RuntimeError(secret)

    monkeypatch.setattr(rag_main, "fetch_pages", fail_fetch)

    await rag_main._self_heal_empty_index(FastAPI(), secret, attempts=1, delay_s=0)

    assert "RuntimeError" in caplog.text
    assert secret not in caplog.text


@pytest.mark.asyncio
async def test_self_heal_database_fetch_does_not_block_event_loop(monkeypatch) -> None:
    loop = asyncio.get_running_loop()
    callback_ran = threading.Event()
    observed: list[bool] = []

    def fetch_pages(_db_url: str):
        loop.call_soon_threadsafe(callback_ran.set)
        observed.append(callback_ran.wait(0.2))
        return []

    monkeypatch.setattr(rag_main, "fetch_pages", fetch_pages)

    await rag_main._self_heal_empty_index(FastAPI(), "postgresql://example", attempts=1, delay_s=0)

    assert observed == [True]
