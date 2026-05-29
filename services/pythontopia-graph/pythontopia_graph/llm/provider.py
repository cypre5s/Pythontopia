from __future__ import annotations

import json as _json
import os
from collections.abc import AsyncIterator
from pathlib import Path
from typing import Any, Protocol

import httpx
from dotenv import dotenv_values
from pydantic import BaseModel

from pythontopia_graph.schemas.profile import ProfileDraft


class LlmResponse(BaseModel):
    content_json: dict[str, Any] | None = None
    content_text: str | None = None


class LlmProvider(Protocol):
    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        raise NotImplementedError

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        raise NotImplementedError
        yield  # noqa: RET503 – makes this a proper async generator in Protocol


class EnvLoadResult(BaseModel):
    path: str
    exists: bool
    loaded_keys: int


def load_alethicode_env(path: str = "/home/cypress/Alethicode/backend/.env") -> EnvLoadResult:
    env_path = Path(path)
    if not env_path.exists():
        return EnvLoadResult(path=str(env_path), exists=False, loaded_keys=0)
    values = dotenv_values(env_path)
    loaded = 0
    for key, value in values.items():
        if key and value is not None and key not in os.environ:
            os.environ[key] = value
            loaded += 1
    return EnvLoadResult(path=str(env_path), exists=True, loaded_keys=loaded)


class DryRunProvider:
    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        text = "\n".join(message["content"] for message in messages)
        if "敏感" in text:
            return LlmResponse(content_json={"safety_failed": True, "reason": "PII_OR_SENSITIVE"})
        if "PY43-KC-999" in text or "虚构" in text or "不存在" in text:
            draft = ProfileDraft.example_complete(target_kc_codes=["PY43-KC-999"])
            return LlmResponse(content_json={"profile_draft": draft.model_dump(mode="json")})
        draft = ProfileDraft.example_complete(target_kc_codes=["PY43-KC-001", "PY43-KC-003"])
        return LlmResponse(content_json={"profile_draft": draft.model_dump(mode="json")})

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        text = "\n".join(message["content"] for message in messages)
        for i in range(0, len(text), 20):
            yield text[i : i + 20]


class OpenAiCompatProvider:
    def __init__(self) -> None:
        load_alethicode_env()
        self.base_url = os.environ.get("OPENAI_BASE_URL") or os.environ.get("LLM_BASE_URL")
        self.api_key = os.environ.get("OPENAI_API_KEY") or os.environ.get("LLM_API_KEY")
        self.model = os.environ.get("OPENAI_MODEL") or os.environ.get("LLM_MODEL")
        if not self.base_url or not self.api_key or not self.model:
            raise RuntimeError("OpenAI-compatible LLM env is missing")

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(
                f"{self.base_url.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json={
                    "model": self.model,
                    "messages": messages,
                    "response_format": {"type": "json_object"},
                    **kwargs,
                },
            )
            response.raise_for_status()
        data = response.json()
        content = data["choices"][0]["message"]["content"]
        return LlmResponse(content_text=content)

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        url = f"{self.base_url.rstrip('/')}/chat/completions"
        payload: dict[str, object] = {
            "model": self.model,
            "messages": messages,
            "stream": True,
            **kwargs,
        }
        async with httpx.AsyncClient(timeout=60) as client:
            async with client.stream(
                "POST",
                url,
                headers={"Authorization": f"Bearer {self.api_key}"},
                json=payload,
            ) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if not line.startswith("data: "):
                        continue
                    data_str = line[len("data: "):]
                    if data_str.strip() == "[DONE]":
                        break
                    chunk = _json.loads(data_str)
                    delta = chunk.get("choices", [{}])[0].get("delta", {})
                    content = delta.get("content")
                    if content:
                        yield content


class LlmProviderError(RuntimeError):
    """Raised when an upstream provider returns a hard failure (rate limit / 余额不足等)."""

    def __init__(self, message: str, *, kind: str, retryable: bool = False) -> None:
        super().__init__(message)
        self.kind = kind
        self.retryable = retryable


class SparkProvider:
    """讯飞 Spark OpenAI-compatible Provider.

    讯飞官方为 Spark 模型提供 OpenAI 兼容接口（`https://spark-api-open.xf-yun.com/v1/chat/completions`），
    鉴权使用 `Authorization: Bearer {api_key}:{api_secret}`。本类只关心 HTTP 调用与限流/欠费翻译；
    高阶节点继续通过 ``LlmProvider`` 协议获取结构化输出。
    """

    DEFAULT_BASE_URL = "https://spark-api-open.xf-yun.com/v1"
    RATE_LIMIT_STATUSES = {429}
    BILLING_STATUSES = {402, 403}

    def __init__(self) -> None:
        self.app_id = os.environ.get("PYT_SPARK_APP_ID")
        self.api_key = os.environ.get("PYT_SPARK_API_KEY")
        self.api_secret = os.environ.get("PYT_SPARK_API_SECRET")
        self.base_url = (
            os.environ.get("PYT_SPARK_BASE_URL") or self.DEFAULT_BASE_URL
        ).rstrip("/")
        self.model_id = os.environ.get("PYT_SPARK_MODEL_ID")
        missing = [
            name
            for name, value in (
                ("PYT_SPARK_APP_ID", self.app_id),
                ("PYT_SPARK_API_KEY", self.api_key),
                ("PYT_SPARK_API_SECRET", self.api_secret),
                ("PYT_SPARK_MODEL_ID", self.model_id),
            )
            if value is None or value == ""
        ]
        if missing:
            raise RuntimeError(
                "Spark provider requires env vars: " + ",".join(missing)
            )
        # OpenAI 兼容模式下 token 由 api_key:api_secret 拼接而成
        self._token = f"{self.api_key}:{self.api_secret}"

    @property
    def label(self) -> str:
        return f"spark:{self.model_id}"

    async def chat(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> LlmResponse:
        payload: dict[str, object] = {
            "model": self.model_id,
            "messages": messages,
        }
        for forwarded in ("temperature", "top_p", "max_tokens", "response_format"):
            if forwarded in kwargs:
                payload[forwarded] = kwargs[forwarded]
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(
                f"{self.base_url}/chat/completions",
                headers={
                    "Authorization": f"Bearer {self._token}",
                    "Content-Type": "application/json",
                },
                json=payload,
            )
        if response.status_code in self.RATE_LIMIT_STATUSES:
            raise LlmProviderError(
                "Spark rate limited", kind="RATE_LIMITED", retryable=True
            )
        if response.status_code in self.BILLING_STATUSES:
            raise LlmProviderError(
                "Spark billing rejected", kind="BILLING", retryable=False
            )
        if response.status_code >= 500:
            raise LlmProviderError(
                f"Spark upstream {response.status_code}",
                kind="UPSTREAM_ERROR",
                retryable=True,
            )
        response.raise_for_status()
        data = response.json()
        try:
            content = data["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise LlmProviderError(
                f"Spark response malformed: {exc}", kind="MALFORMED", retryable=False
            ) from exc
        return LlmResponse(content_text=content)

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        payload: dict[str, object] = {
            "model": self.model_id,
            "messages": messages,
            "stream": True,
        }
        for forwarded in ("temperature", "top_p", "max_tokens"):
            if forwarded in kwargs:
                payload[forwarded] = kwargs[forwarded]
        async with httpx.AsyncClient(timeout=60) as client:
            async with client.stream(
                "POST",
                f"{self.base_url}/chat/completions",
                headers={
                    "Authorization": f"Bearer {self._token}",
                    "Content-Type": "application/json",
                },
                json=payload,
            ) as response:
                if response.status_code in self.RATE_LIMIT_STATUSES:
                    raise LlmProviderError(
                        "Spark rate limited", kind="RATE_LIMITED", retryable=True
                    )
                if response.status_code in self.BILLING_STATUSES:
                    raise LlmProviderError(
                        "Spark billing rejected", kind="BILLING", retryable=False
                    )
                if response.status_code >= 500:
                    raise LlmProviderError(
                        f"Spark upstream {response.status_code}",
                        kind="UPSTREAM_ERROR",
                        retryable=True,
                    )
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if not line.startswith("data: "):
                        continue
                    data_str = line[len("data: "):]
                    if data_str.strip() == "[DONE]":
                        break
                    try:
                        chunk = _json.loads(data_str)
                        delta = chunk["choices"][0]["delta"]
                        content = delta.get("content")
                        if content:
                            yield content
                    except (KeyError, IndexError, TypeError, _json.JSONDecodeError):
                        continue


def provider_from_env() -> LlmProvider:
    provider_name = os.environ.get("PYT_LLM_PROVIDER")
    if provider_name is None:
        raise RuntimeError("PYT_LLM_PROVIDER must be set")
    name = provider_name.lower()
    if name == "dryrun":
        return DryRunProvider()
    if name in {"openai_compat", "openai-compatible", "openaicompat"}:
        return OpenAiCompatProvider()
    if name == "spark":
        return SparkProvider()
    raise RuntimeError(f"Unsupported PYT_LLM_PROVIDER: {provider_name}")


def provider_label() -> str:
    name = (os.environ.get("PYT_LLM_PROVIDER") or "dryrun").lower()
    if name == "spark":
        model = os.environ.get("PYT_SPARK_MODEL_ID") or "unknown"
        return f"spark:{model}"
    if name in {"openai_compat", "openai-compatible", "openaicompat"}:
        model = os.environ.get("LLM_MODEL") or os.environ.get("OPENAI_MODEL") or "unknown"
        return f"openai_compat:{model}"
    return name
