"""Pytest defaults: isolate graph route tests from host PYT_RAG_ADMIN_TOKEN."""
from __future__ import annotations

import pytest


@pytest.fixture(autouse=True)
def _isolate_internal_token_env(monkeypatch: pytest.MonkeyPatch) -> None:
    """Unless a test sets its own token, behave like dev without service auth."""
    monkeypatch.delenv("PYT_RAG_ADMIN_TOKEN", raising=False)
