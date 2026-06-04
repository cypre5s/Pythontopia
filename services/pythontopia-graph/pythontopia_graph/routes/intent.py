"""HTTP endpoint for the IntentRouterAgent（百晓工坊 意图路由）.

* ``POST /agents/intent/route`` – non-streaming ``IntentRouteResponse``.

Unlike the orchestrator / tutor routes this is NOT an SSE stream: it is a quick,
single LLM classification the frontend calls *before* dispatching the chosen tool
to the existing tutor / workshop SSE pipelines. Provider is taken from
``app.state`` (shared instance) with a per-request env fallback, matching the
other agent routes.
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
