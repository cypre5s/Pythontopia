"""资源图与画像访谈的 SSE 流式端点。"""

from __future__ import annotations

import asyncio
import json
import os
from collections.abc import AsyncIterator
from typing import Any

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from pythontopia_graph.graph.resource_graph import (
    ResourceGraph,
    ResourceGraphConfig,
)
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceFetcher,
    HttpRagEvidenceFetcher,
)
from pythontopia_graph.schemas.resource import ResourceProduceRequest

router = APIRouter()


def _provider(request: Request) -> LlmProvider:
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is not None:
        return provider
    return provider_from_env()


def _evidence_fetcher(request: Request) -> EvidenceFetcher:
    fetcher = getattr(request.app.state, "evidence_fetcher", None)
    if fetcher is not None:
        return fetcher
    return HttpRagEvidenceFetcher(os.environ.get("PYT_RAG_URL"))


def _valid_kc_codes(request: Request) -> set[str]:
    codes = getattr(request.app.state, "valid_kc_codes", None)
    if codes is not None:
        return codes
    return set()


def _format_sse(event: str, data: dict[str, Any]) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


async def _resource_stream_generator(
    body: ResourceProduceRequest,
    provider: LlmProvider,
    evidence_fetcher: EvidenceFetcher,
    valid_kc_codes: set[str],
) -> AsyncIterator[str]:
    queue: asyncio.Queue[str | None] = asyncio.Queue()

    async def emit(event: str, data: dict[str, Any]) -> None:
        await queue.put(_format_sse(event, data))

    graph = ResourceGraph(
        provider=provider,
        evidence_fetcher=evidence_fetcher,
        config=ResourceGraphConfig(
            valid_kc_codes=valid_kc_codes or set(body.kc_codes),
        ),
    )

    async def run_graph() -> None:
        try:
            await graph.run_streaming(body, emit)
        finally:
            await queue.put(None)

    task = asyncio.create_task(run_graph())
    try:
        while True:
            item = await queue.get()
            if item is None:
                break
            yield item
    finally:
        if not task.done():
            task.cancel()


@router.post("/agents/resource/produce/stream")
async def produce_resource_stream(
    body: ResourceProduceRequest,
    provider: LlmProvider = Depends(_provider),
    evidence_fetcher: EvidenceFetcher = Depends(_evidence_fetcher),
    valid_kc_codes: set[str] = Depends(_valid_kc_codes),
) -> StreamingResponse:
    return StreamingResponse(
        _resource_stream_generator(body, provider, evidence_fetcher, valid_kc_codes),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.post("/agents/profile/turn/stream")
async def profile_turn_stream(
    body: dict[str, Any],
    provider: LlmProvider = Depends(_provider),
) -> StreamingResponse:
    """画像访谈 SSE 桩端点：将现有 profile turn 包装为 SSE；token 级流式待 ProfileGraph 支持后实现。"""
    from pythontopia_graph.agents.profile_agent import ProfileAgent
    from pythontopia_graph.schemas.profile import ProfileAgentInput

    async def generate() -> AsyncIterator[str]:
        try:
            input_data = ProfileAgentInput.model_validate(body)
            agent = ProfileAgent(provider)
            yield _format_sse("trace", {"node": "ProfileAgent", "status": "running", "elapsed_ms": 0})
            result = await agent.run(input_data)
            yield _format_sse("result", result.model_dump(mode="json"))
        except Exception as exc:
            yield _format_sse("error", {"code": "PROFILE_TURN_FAILED", "message": str(exc)})

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
