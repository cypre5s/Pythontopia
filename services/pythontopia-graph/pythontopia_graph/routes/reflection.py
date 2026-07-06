"""ReflectionAgent（学情反思器）HTTP 端点。

* ``POST /agents/reflection`` → 非流式 ``ReflectionOutput``。

非 SSE：后端取 topK 记忆条目后一次性调用，做单次有界反思综合 grounded 学情洞察。
Provider 优先 ``app.state`` 共享实例，按请求回退 env，与其它 agent 路由一致。
"""

from __future__ import annotations

from typing import cast

from fastapi import APIRouter, Depends, Request

from pythontopia_graph.agents.reflection_agent import ReflectionAgent
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.schemas.reflection import ReflectionInput, ReflectionOutput

router = APIRouter()


def _provider(request: Request) -> LlmProvider:
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is not None:
        return cast(LlmProvider, provider)
    return provider_from_env()


@router.post("/agents/reflection", response_model=ReflectionOutput)
async def reflect(
    body: ReflectionInput,
    provider: LlmProvider = Depends(_provider),
) -> ReflectionOutput:
    agent = ReflectionAgent(provider)
    return await agent.reflect(body)
