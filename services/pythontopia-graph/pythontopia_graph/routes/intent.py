"""IntentRouterAgent HTTP 端点（百晓工坊意图路由）。

* ``POST /agents/intent/route`` → 非流式 ``IntentRouteResponse``。

非 SSE：前端在分流到 tutor/workshop SSE 流水线之前做一次性 LLM 分类。
Provider 优先 ``app.state`` 共享实例，按请求回退 env，与其它 agent 路由一致。
"""

from __future__ import annotations

from typing import cast

from fastapi import APIRouter, Depends, Request

from pythontopia_graph.agents.intent_router_agent import IntentRouterAgent
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.schemas.intent import IntentRouteRequest, IntentRouteResponse

router = APIRouter()


def _provider(request: Request) -> LlmProvider:
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is not None:
        return cast(LlmProvider, provider)
    return provider_from_env()


@router.post("/agents/intent/route", response_model=IntentRouteResponse)
async def intent_route(
    body: IntentRouteRequest,
    provider: LlmProvider = Depends(_provider),
) -> IntentRouteResponse:
    agent = IntentRouterAgent(provider)
    return await agent.route(body)
