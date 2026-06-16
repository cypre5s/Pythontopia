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
    # 沿父目录向上查找 deploy/.env（避免写死 parents[n] 在容器内越界 IndexError）；未命中返回占位路径，由 load_pythontopia_env fail-soft。
    here = Path(__file__).resolve()
    for parent in here.parents:
        candidate = parent / "deploy" / ".env"
        if candidate.exists():
            return candidate
    return here.parent / "deploy" / ".env"


def load_pythontopia_env(path: str | None = None) -> EnvLoadResult:
    """加载 <repo>/deploy/.env（PYT_LLM_PROVIDER / PYT_OPENAI_* 等）。

    供 graph 直跑 uvicorn、未经 compose 注入时读取 DeepSeek 凭证；容器内找不到则 exists=False 无副作用。
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


def _openai_fast_model() -> str | None:
    """快档模型名（NPC 寒暄/对话用，低延迟、少推理 token）。

    兼容三种命名：OPENAI_MODEL_FAST / LLM_MODEL_FAST（docker-compose 注入）与
    PYT_OPENAI_MODEL_FAST（deploy/.env 原始名）。未配置则返回 None，由调用方回落默认档。
    """
    return (
        os.environ.get("OPENAI_MODEL_FAST")
        or os.environ.get("LLM_MODEL_FAST")
        or os.environ.get("PYT_OPENAI_MODEL_FAST")
    )


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
        # dry-run 禁止回显 system/证据消息，否则 SYSTEM_PROMPT 会泄露到答疑气泡；仅依据最后一条 user 问题生成占位答复。
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
        # 快档模型（NPC 寒暄/对话）未配置时回落默认档，行为零回归。
        self.fast_model = _openai_fast_model() or self.model

    def _resolve_model(self, kwargs: dict[str, object]) -> str:
        """按调用方声明的 model_tier 选模型：fast→快档（寒暄/对话），其余→默认档（答疑/工坊）。

        从 kwargs 弹出 model_tier，避免它被透传进 OpenAI 兼容请求体。
        """
        tier = kwargs.pop("model_tier", None)
        return self.fast_model if tier == "fast" else self.model

    async def chat(
        self, messages: list[dict[str, str]], *, json_mode: bool = True, **kwargs: object
    ) -> LlmResponse:
        model = self._resolve_model(kwargs)
        # 按需结构化输出（json_mode）：仅当 json_mode=True（默认）才强制
        # response_format=json_object——资源/画像/图解/证据注/辩论等依赖 JSON 的调用沿用默认；
        # 纯文本答疑（导师作答 / 修订 / 考考我点评）显式传 json_mode=False，避免回答被压成
        # JSON 串而变短、夹结构噪声。调用方显式传入的 response_format 始终优先（保持既有
        # *_agent 行为不变）。默认保持 True 是为不波及本次改动范围外、未显式声明 JSON 的调用方
        # （6 个资源 Agent + profile_graph 等），它们靠该默认拿到结构化输出。
        body: dict[str, object] = {"model": model, "messages": messages}
        if json_mode and "response_format" not in kwargs:
            body["response_format"] = {"type": "json_object"}
        body.update(kwargs)
        # trust_env=True：有 HTTP(S)_PROXY 则走代理，否则直连；NO_PROXY 保护 localhost/RAG。
        # 读超时 90s：讲义/练习生成属长输出任务，30s 会让 ResourceDesigner 等 agent 高频
        # 超时（实测 DeepSeek 长文常超 30s）；连接超时仍保持 10s 快速失败。
        async with httpx.AsyncClient(
            timeout=httpx.Timeout(90, connect=10), trust_env=True
        ) as client:
            response = await client.post(
                f"{self.base_url.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json=body,
            )
            response.raise_for_status()
        data = response.json()
        content = data["choices"][0]["message"]["content"]
        # 始终尝试把 content 解析为 JSON 到 content_json（json_mode 调用走这条拿结构化结果）；
        # 解析失败（含 json_mode=False 的纯文本答疑）则 content_json=None、仅保留 content_text。
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
        model = self._resolve_model(kwargs)
        url = f"{self.base_url.rstrip('/')}/chat/completions"
        payload: dict[str, object] = {
            "model": model,
            "messages": messages,
            "stream": True,
            **kwargs,
        }
        # 同 chat()：trust_env 代理策略。
        async with httpx.AsyncClient(timeout=60, trust_env=True) as client:
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
    """上游 Provider 硬性失败（限流、余额不足等）时抛出。"""

    def __init__(self, message: str, *, kind: str, retryable: bool = False) -> None:
        super().__init__(message)
        self.kind = kind
        self.retryable = retryable


class SparkProvider:
    """讯飞 Spark OpenAI 兼容 Provider（/v1/chat/completions，Bearer api_key:api_secret）；负责 HTTP 调用与限流/欠费翻译。"""

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
        # 同 OpenAiCompatProvider.chat：长输出任务读超时 90s，连接超时 10s。
        async with httpx.AsyncClient(timeout=httpx.Timeout(90, connect=10)) as client:
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
        # 未指定 PYT_LLM_PROVIDER 时：有 OpenAI 兼容凭证默认 openai_compat，否则 dryrun。
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
        fast = _openai_fast_model()
        suffix = f" (fast={fast})" if fast and fast != model else ""
        return f"openai_compat:{model or 'unknown'}{suffix}"
    return name
