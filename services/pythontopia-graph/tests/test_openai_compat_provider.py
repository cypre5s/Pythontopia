"""OpenAiCompatProvider 单元测试。"""

from __future__ import annotations

import json
from collections.abc import Callable
from typing import Any

import httpx
import pytest

from pythontopia_graph.llm.provider import OpenAiCompatProvider


@pytest.fixture(autouse=True)
def _openai_compat_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("OPENAI_BASE_URL", "https://api.deepseek.com/v1")
    monkeypatch.setenv("OPENAI_API_KEY", "deepseek-key")
    monkeypatch.setenv("OPENAI_MODEL", "deepseek-v4-pro")
    monkeypatch.setenv("PYT_LLM_PROVIDER", "openai_compat")
    for name in (
        "LLM_BASE_URL",
        "LLM_API_KEY",
        "LLM_MODEL",
        "OPENAI_MODEL_FAST",
        "LLM_MODEL_FAST",
        "PYT_OPENAI_BASE_URL",
        "PYT_OPENAI_API_KEY",
        "PYT_OPENAI_MODEL",
        "PYT_OPENAI_MODEL_FAST",
        "HTTP_PROXY",
        "http_proxy",
        "HTTPS_PROXY",
        "https_proxy",
        "ALL_PROXY",
        "all_proxy",
    ):
        monkeypatch.delenv(name, raising=False)


def _capture_async_client_kwargs(
    monkeypatch: pytest.MonkeyPatch,
    handler: Callable[[httpx.Request], httpx.Response],
) -> list[dict[str, Any]]:
    transport = httpx.MockTransport(handler)
    real_init = httpx.AsyncClient.__init__
    captured: list[dict[str, Any]] = []

    def patched_init(self: httpx.AsyncClient, *args: Any, **kwargs: Any) -> None:
        captured.append(dict(kwargs))
        kwargs.setdefault("transport", transport)
        real_init(self, *args, **kwargs)

    monkeypatch.setattr(httpx.AsyncClient, "__init__", patched_init)
    return captured


@pytest.mark.asyncio
async def test_openai_compat_chat_trusts_proxy_environment(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("HTTPS_PROXY", "http://127.0.0.1:7892")
    monkeypatch.setenv("NO_PROXY", "localhost,127.0.0.1")

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": '{"ok": true}'}}]},
        )

    captured = _capture_async_client_kwargs(monkeypatch, handler)
    provider = OpenAiCompatProvider()

    result = await provider.chat([{"role": "user", "content": "ping"}])

    assert result.content_json == {"ok": True}
    assert captured
    assert captured[0].get("trust_env", True) is True


@pytest.mark.asyncio
async def test_openai_compat_stream_trusts_proxy_environment(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("HTTPS_PROXY", "http://127.0.0.1:7892")
    monkeypatch.setenv("NO_PROXY", "localhost,127.0.0.1")

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            text='data: {"choices":[{"delta":{"content":"hi"}}]}\n\n'
            "data: [DONE]\n\n",
        )

    captured = _capture_async_client_kwargs(monkeypatch, handler)
    provider = OpenAiCompatProvider()

    chunks = [
        chunk
        async for chunk in provider.chat_stream([{"role": "user", "content": "ping"}])
    ]

    assert chunks == ["hi"]
    assert captured
    assert captured[0].get("trust_env", True) is True


def _capture_request_bodies(
    monkeypatch: pytest.MonkeyPatch,
) -> list[dict[str, Any]]:
    """捕获发往 OpenAI 兼容端点的请求体，便于断言其中的 model 字段（双档选模型）。"""
    bodies: list[dict[str, Any]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        bodies.append(json.loads(request.content))
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": '{"ok": true}'}}]},
        )

    _capture_async_client_kwargs(monkeypatch, handler)
    return bodies


@pytest.mark.asyncio
async def test_chat_uses_fast_model_when_tier_is_fast(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OPENAI_MODEL_FAST", "deepseek-v4-flash")
    bodies = _capture_request_bodies(monkeypatch)
    provider = OpenAiCompatProvider()

    await provider.chat([{"role": "user", "content": "ping"}], model_tier="fast")

    assert bodies[0]["model"] == "deepseek-v4-flash"
    # model_tier 是路由提示，不得透传进上游请求体。
    assert "model_tier" not in bodies[0]


@pytest.mark.asyncio
async def test_chat_defaults_to_primary_model_without_tier(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OPENAI_MODEL_FAST", "deepseek-v4-flash")
    bodies = _capture_request_bodies(monkeypatch)
    provider = OpenAiCompatProvider()

    await provider.chat([{"role": "user", "content": "ping"}])

    assert bodies[0]["model"] == "deepseek-v4-pro"


@pytest.mark.asyncio
async def test_fast_tier_falls_back_to_primary_when_fast_model_unset(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # 未配置快档（OPENAI_MODEL_FAST 缺失）→ fast 档回落默认档，行为零回归。
    # 隔离 deploy/.env / Alethicode .env 注入，确保"未配置快档"前提在测试中成立。
    monkeypatch.setattr(
        "pythontopia_graph.llm.provider.load_pythontopia_env", lambda *a, **k: None
    )
    monkeypatch.setattr(
        "pythontopia_graph.llm.provider.load_alethicode_env", lambda *a, **k: None
    )
    bodies = _capture_request_bodies(monkeypatch)
    provider = OpenAiCompatProvider()

    await provider.chat([{"role": "user", "content": "ping"}], model_tier="fast")

    assert bodies[0]["model"] == "deepseek-v4-pro"


@pytest.mark.asyncio
async def test_openai_compat_without_proxy_env_does_not_force_local_proxy(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": '{"ok": true}'}}]},
        )

    captured = _capture_async_client_kwargs(monkeypatch, handler)
    provider = OpenAiCompatProvider()

    await provider.chat([{"role": "user", "content": "ping"}])

    assert captured
    assert captured[0].get("proxy") is None
