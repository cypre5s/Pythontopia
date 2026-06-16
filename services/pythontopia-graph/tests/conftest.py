"""Pytest defaults: isolate graph route tests from host PYT_RAG_ADMIN_TOKEN."""
from __future__ import annotations

import pytest


@pytest.fixture(autouse=True)
def _isolate_internal_token_env(monkeypatch: pytest.MonkeyPatch) -> None:
    """Unless a test sets its own token, behave like dev without service auth.

    内部鉴权现在默认 fail-closed（无令牌即拒绝）；测试默认显式开启 dev 放行逃生舱，
    使不关心鉴权的路由测试照常运行，而 test_internal_auth.py 自行设置令牌验证强制路径。
    """
    monkeypatch.delenv("PYT_RAG_ADMIN_TOKEN", raising=False)
    monkeypatch.setenv("PYT_INTERNAL_AUTH_OPTIONAL", "1")
