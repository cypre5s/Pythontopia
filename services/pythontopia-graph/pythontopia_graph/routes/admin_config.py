from __future__ import annotations

import os
from typing import Literal

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, Field, field_validator

from pythontopia_graph.llm.provider import OpenAiCompatProvider, _openai_compat_env

router = APIRouter(prefix="/internal/admin", tags=["internal-admin"])
DEEPSEEK_BASE_URL = "https://api.deepseek.com"
FAST_MODEL = "deepseek-flash"


class LlmConfigRequest(BaseModel):
    model: Literal["deepseek-flash", "deepseek-v4-pro"]
    apiKey: str | None = Field(default=None, min_length=8, max_length=512)

    @field_validator("apiKey")
    @classmethod
    def empty_key_is_omitted(cls, value: str | None) -> str | None:
        return value.strip() or None if value is not None else None


class LlmConfigStatus(BaseModel):
    provider: Literal["DeepSeek"] = "DeepSeek"
    model: str
    apiKeyConfigured: bool


class LlmConfigTestResult(BaseModel):
    success: bool
    model: str
    message: str


def _make_provider(model: str, api_key: str | None) -> OpenAiCompatProvider:
    _, environment_key, _ = _openai_compat_env()
    key = api_key or environment_key
    if not key:
        raise HTTPException(status_code=409, detail="DeepSeek API key is not configured")
    try:
        return OpenAiCompatProvider(
            base_url=DEEPSEEK_BASE_URL,
            api_key=key,
            model=model,
            fast_model=FAST_MODEL,
            vision_model=FAST_MODEL,
        )
    except RuntimeError as exc:
        raise HTTPException(status_code=422, detail="DeepSeek model configuration is invalid") from exc


@router.get("/llm-config", response_model=LlmConfigStatus)
async def get_llm_config() -> LlmConfigStatus:
    _, api_key, model = _openai_compat_env()
    return LlmConfigStatus(
        model=model if model in {"deepseek-flash", "deepseek-v4-pro"} else "deepseek-v4-pro",
        apiKeyConfigured=bool(api_key),
    )


@router.post("/llm-config/test", response_model=LlmConfigTestResult)
async def test_llm_config(body: LlmConfigRequest) -> LlmConfigTestResult:
    provider = _make_provider(body.model, body.apiKey)
    try:
        await provider.chat(
            [{"role": "user", "content": "Reply with OK."}],
            json_mode=False,
            max_tokens=8,
        )
    except Exception as exc:  # noqa: BLE001 - never return provider errors that may contain request data
        raise HTTPException(status_code=502, detail="DeepSeek connection failed") from exc
    return LlmConfigTestResult(success=True, model=body.model, message="DeepSeek 连接成功")


@router.put("/llm-config", response_model=LlmConfigStatus)
async def apply_llm_config(body: LlmConfigRequest, request: Request) -> LlmConfigStatus:
    provider = _make_provider(body.model, body.apiKey)
    api_key = body.apiKey or _openai_compat_env()[1]
    if not api_key:
        raise HTTPException(status_code=409, detail="DeepSeek API key is not configured")
    for key, value in {
        "PYT_LLM_PROVIDER": "openai_compat",
        "PYT_OPENAI_BASE_URL": DEEPSEEK_BASE_URL,
        "PYT_OPENAI_API_KEY": api_key,
        "PYT_OPENAI_MODEL": body.model,
        "PYT_OPENAI_MODEL_FAST": FAST_MODEL,
        "PYT_OPENAI_MODEL_VISION": FAST_MODEL,
    }.items():
        os.environ[key] = value
    request.app.state.llm_provider = provider
    return LlmConfigStatus(model=body.model, apiKeyConfigured=True)
