"""IntentRouterAgent HTTP 端点（百晓工坊意图路由）。

* ``POST /agents/intent/route`` → 非流式 ``IntentRouteResponse``。
* ``GET /agents/intent/tools`` → 16 工具注册表目录（前端欢迎屏「能力一览」/测试对账同源）。
* ``GET /agents/harness/stats`` → 工具治理 harness 统计快照（调用/失败/降级/熔断器状态）。

非 SSE：前端在分流到 tutor/workshop SSE 流水线之前做一次性 LLM 分类。
Provider 优先 ``app.state`` 共享实例，按请求回退 env，与其它 agent 路由一致。
"""

from __future__ import annotations

from typing import Any, cast

from fastapi import APIRouter, Depends, Request

from pythontopia_graph.agents.intent_router_agent import IntentRouterAgent
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.schemas.intent import IntentRouteRequest, IntentRouteResponse
from pythontopia_graph.tools.harness import harness_stats
from pythontopia_graph.tools.registry import tool_catalog_payload

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


@router.get("/agents/intent/tools")
async def intent_tools() -> list[dict[str, object]]:
    """工具注册表目录（registry 单一事实源，零 LLM 成本）。"""
    return tool_catalog_payload()


@router.get("/agents/harness/stats")
async def harness_stats_endpoint() -> dict[str, Any]:
    """harness 统计快照：各工具调用数/失败数/降级数/均耗时 + 熔断器状态。"""
    return harness_stats()
