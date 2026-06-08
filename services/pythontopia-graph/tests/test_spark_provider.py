"""SparkProvider 单元测试：HTTP 翻译、环境变量选型与错误映射。"""

from __future__ import annotations

import os
from typing import Any

import httpx
import pytest

from pythontopia_graph.llm.provider import (
    LlmProviderError,
    SparkProvider,
    provider_from_env,
    provider_label,
)


@pytest.fixture(autouse=True)
def _spark_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PYT_LLM_PROVIDER", "spark")
    monkeypatch.setenv("PYT_SPARK_APP_ID", "app")
    monkeypatch.setenv("PYT_SPARK_API_KEY", "key")
    monkeypatch.setenv("PYT_SPARK_API_SECRET", "secret")
    monkeypatch.setenv("PYT_SPARK_MODEL_ID", "spark-pro-test")
    monkeypatch.setenv("PYT_SPARK_BASE_URL", "https://example.test/v1")


def _install_transport(monkeypatch: pytest.MonkeyPatch, handler: Any) -> None:
    transport = httpx.MockTransport(handler)
    real_init = httpx.AsyncClient.__init__

    def patched_init(self: httpx.AsyncClient, *args: Any, **kwargs: Any) -> None:
        kwargs.setdefault("transport", transport)
        real_init(self, *args, **kwargs)

    monkeypatch.setattr(httpx.AsyncClient, "__init__", patched_init)


@pytest.mark.asyncio
async def test_spark_provider_returns_text(monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["auth"] = request.headers.get("Authorization")
        return httpx.Response(
            200,
            json={
                "choices": [
                    {"message": {"role": "assistant", "content": "hello"}}
                ]
            },
        )

    _install_transport(monkeypatch, handler)
    provider = SparkProvider()
    result = await provider.chat([{"role": "user", "content": "ping"}])
    assert result.content_text == "hello"
    assert captured["url"].endswith("/chat/completions")
    assert captured["auth"] == "Bearer key:secret"


@pytest.mark.asyncio
async def test_spark_provider_translates_rate_limit(monkeypatch: pytest.MonkeyPatch) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(429, json={"error": "rate limited"})

    _install_transport(monkeypatch, handler)
    provider = SparkProvider()
    with pytest.raises(LlmProviderError) as exc_info:
        await provider.chat([{"role": "user", "content": "ping"}])
    assert exc_info.value.kind == "RATE_LIMITED"
    assert exc_info.value.retryable is True


@pytest.mark.asyncio
async def test_spark_provider_translates_billing(monkeypatch: pytest.MonkeyPatch) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(402, json={"error": "billing"})

    _install_transport(monkeypatch, handler)
    provider = SparkProvider()
    with pytest.raises(LlmProviderError) as exc_info:
        await provider.chat([{"role": "user", "content": "ping"}])
    assert exc_info.value.kind == "BILLING"
    assert exc_info.value.retryable is False


@pytest.mark.asyncio
async def test_spark_provider_translates_upstream(monkeypatch: pytest.MonkeyPatch) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503, text="boom")

    _install_transport(monkeypatch, handler)
    provider = SparkProvider()
    with pytest.raises(LlmProviderError) as exc_info:
        await provider.chat([{"role": "user", "content": "ping"}])
    assert exc_info.value.kind == "UPSTREAM_ERROR"


@pytest.mark.asyncio
async def test_spark_provider_handles_malformed_response(monkeypatch: pytest.MonkeyPatch) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"choices": []})

    _install_transport(monkeypatch, handler)
    provider = SparkProvider()
    with pytest.raises(LlmProviderError) as exc_info:
        await provider.chat([{"role": "user", "content": "ping"}])
    assert exc_info.value.kind == "MALFORMED"


def test_provider_from_env_selects_spark() -> None:
    provider = provider_from_env()
    assert isinstance(provider, SparkProvider)
    assert provider.label.startswith("spark:")


def test_provider_label_reports_model() -> None:
    label = provider_label()
    assert label == "spark:spark-pro-test"


def test_spark_requires_env(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in (
        "PYT_SPARK_APP_ID",
        "PYT_SPARK_API_KEY",
        "PYT_SPARK_API_SECRET",
        "PYT_SPARK_MODEL_ID",
    ):
        monkeypatch.delenv(name, raising=False)
    with pytest.raises(RuntimeError) as exc_info:
        SparkProvider()
    assert "PYT_SPARK_APP_ID" in str(exc_info.value)
