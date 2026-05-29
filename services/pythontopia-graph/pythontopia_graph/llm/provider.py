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


def _default_deploy_env_path() -> Path:
    # 向上查找最近的 <ancestor>/deploy/.env：host 直跑能命中仓库根的 deploy/.env；
    # 容器内 provider.py 位于 /app/pythontopia_graph/llm/ 仅 3 层父目录且无 deploy 挂载，
    # 此前写死 parents[4] 会越界 IndexError 导致启动崩溃。未命中即返回占位路径，
    # 由 load_pythontopia_env fail-soft（容器内环境变量已由 compose 注入）。
    here = Path(__file__).resolve()
    for parent in here.parents:
        candidate = parent / "deploy" / ".env"
        if candidate.exists():
            return candidate
    return here.parent / "deploy" / ".env"


def load_pythontopia_env(path: str | None = None) -> EnvLoadResult:
    """加载 <repo>/deploy/.env（PYT_LLM_PROVIDER / PYT_OPENAI_* 等）。

    让 graph 服务在「直接 uvicorn 跑、未经 docker-compose 注入环境变量」时也能拿到
    DeepSeek 凭证，从而所有 LLM 调用默认接入真实大模型而非 dryrun。容器内（compose
    已注入 env）找不到该文件时返回 exists=False，无副作用。
    """
    env_path = Path(path) if path else _default_deploy_env_path()
    if not env_path.exists():
        return EnvLoadResult(path=str(env_path), exists=False, loaded_keys=0)
    values = dotenv_values(env_path)
    loaded = 0
    for key, value in values.items():
        if key and value is not None and key not in os.environ:
            os.environ[key] = value
            loaded += 1
    return EnvLoadResult(path=str(env_path), exists=True, loaded_keys=loaded)


def _openai_compat_env() -> tuple[str | None, str | None, str | None]:
    """解析 OpenAI 兼容（DeepSeek）三件套，兼容三种命名：
    OPENAI_* / LLM_*（docker-compose 注入）与 PYT_OPENAI_*（deploy/.env 原始名）。
    """
    base_url = (
        os.environ.get("OPENAI_BASE_URL")
        or os.environ.get("LLM_BASE_URL")
        or os.environ.get("PYT_OPENAI_BASE_URL")
    )
    api_key = (
        os.environ.get("OPENAI_API_KEY")
        or os.environ.get("LLM_API_KEY")
        or os.environ.get("PYT_OPENAI_API_KEY")
    )
    model = (
        os.environ.get("OPENAI_MODEL")
        or os.environ.get("LLM_MODEL")
        or os.environ.get("PYT_OPENAI_MODEL")
    )
    return base_url, api_key, model


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
        # 重要：dry-run 模式严禁回显 system / 证据等内部 message —— 否则会把内置
        # SYSTEM_PROMPT 当成「答案」泄露到答疑气泡里（曾出现「你是 Pythontopia 智能
        # 答疑助教… 规则 1~6」直接出现在导师回复中的 bug）。这里只依据最后一条 user
        # 问题产出离线占位答复。
        user_turns = [
            str(m.get("content", "")) for m in messages if m.get("role") == "user"
        ]
        question = user_turns[-1].strip() if user_turns else ""
        answer = (
            "基于通识知识（本地 dry-run，未接入真实大模型）：\n\n"
            f"已收到你的问题：「{question}」。\n\n"
            "当前为离线占位答复。将 PYT_LLM_PROVIDER 切到 openai_compat 或 spark "
            "并配置密钥后即可获得真实课件答疑。"
        )
        for i in range(0, len(answer), 12):
            yield answer[i : i + 12]


class OpenAiCompatProvider:
    def __init__(self) -> None:
        load_pythontopia_env()
        load_alethicode_env()
        self.base_url, self.api_key, self.model = _openai_compat_env()
        if not self.base_url or not self.api_key or not self.model:
            raise RuntimeError("OpenAI-compatible LLM env is missing")

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        # trust_env=False：忽略容器/宿主注入的 HTTP(S)_PROXY（容器内常被设成失效的
        # 127.0.0.1:7892）。DeepSeek api.deepseek.com 为境内直连，无需代理，与
        # evidence_fetcher 的 HTTP 调用保持一致。
        async with httpx.AsyncClient(timeout=30, trust_env=False) as client:
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
        # response_format=json_object 时模型返回 JSON 字符串。资源类 Agent 统一读
        # content_json（原为 DryRunProvider 的形态），故这里解析一次，避免真实 LLM
        # 输出被当成 None 而回落到离线占位草稿。解析失败时仅保留 content_text。
        content_json: dict[str, Any] | None = None
        try:
            parsed = _json.loads(content)
            if isinstance(parsed, dict):
                content_json = parsed
        except (ValueError, TypeError):
            content_json = None
        return LlmResponse(content_json=content_json, content_text=content)

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
        # trust_env=False：同 chat()，绕过容器内失效代理，直连境内 DeepSeek。
        async with httpx.AsyncClient(timeout=60, trust_env=False) as client:
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
    provider_name = (os.environ.get("PYT_LLM_PROVIDER") or "").strip().lower()
    if not provider_name:
        # 未显式指定时：有 OpenAI 兼容（DeepSeek）凭证就默认走 openai_compat（真实大模型），
        # 否则回落 dryrun（本地离线占位）。满足「除多模态外其余全部接入 DeepSeek」。
        _, api_key, _ = _openai_compat_env()
        provider_name = "openai_compat" if api_key else "dryrun"
    if provider_name == "dryrun":
        return DryRunProvider()
    if provider_name in {"openai_compat", "openai-compatible", "openaicompat"}:
        return OpenAiCompatProvider()
    if provider_name == "spark":
        return SparkProvider()
    raise RuntimeError(f"Unsupported PYT_LLM_PROVIDER: {provider_name}")


def provider_label() -> str:
    name = (os.environ.get("PYT_LLM_PROVIDER") or "").strip().lower()
    if not name:
        _, api_key, model = _openai_compat_env()
        if api_key:
            return f"openai_compat:{model or 'unknown'}"
        return "dryrun"
    if name == "spark":
        model = os.environ.get("PYT_SPARK_MODEL_ID") or "unknown"
        return f"spark:{model}"
    if name in {"openai_compat", "openai-compatible", "openaicompat"}:
        _, _, model = _openai_compat_env()
        return f"openai_compat:{model or 'unknown'}"
    return name
