from __future__ import annotations

import pytest
from fastapi import FastAPI

import pythontopia_graph.main as graph_main


@pytest.mark.asyncio
async def test_lifespan_provider_failure_logs_only_exception_type(monkeypatch, caplog) -> None:
    sentinel = "provider-config-secret-sentinel"

    def fail_provider():
        raise RuntimeError(sentinel)

    monkeypatch.setattr(graph_main, "init_tracing", lambda: None)
    monkeypatch.setattr(graph_main, "load_pythontopia_env", lambda: None)
    monkeypatch.setattr(graph_main, "provider_from_env", fail_provider)
    test_app = FastAPI()

    async with graph_main.lifespan(test_app):
        assert test_app.state.llm_provider is None

    assert "RuntimeError" in caplog.text
    assert sentinel not in caplog.text
