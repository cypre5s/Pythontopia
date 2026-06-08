"""OrchestratorAgent HTTP + SSE 端点（学习总管）。

* ``POST /agents/orchestrator/turn`` → 非流式 ``OrchestratorResponse``；
* ``POST /agents/orchestrator/turn/stream`` → SSE（``trace`` / ``token`` / ``result`` / ``error``），
  线格式 ``event: <name>\\ndata: <json>\\n\\n``（与前端 useSseStream 契约一致）。

SSE 生成器沿用 ``routes/streaming.py`` 模式：``asyncio.Queue`` + ``emit`` 回调 + 后台图任务，
``finally`` 入队 ``None`` 哨兵后 drain 产出 chunk。
"""

from __future__ import annotations

import asyncio
import json
from collections.abc import AsyncIterator
from typing import Any, cast

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.schemas.orchestrator import (
    OrchestratorRequest,
    OrchestratorResponse,
)

router = APIRouter()


def _provider(request: Request) -> LlmProvider:
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is not None:
        return cast(LlmProvider, provider)
    return provider_from_env()


def _format_sse(event: str, data: dict[str, Any]) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/agents/orchestrator/turn", response_model=OrchestratorResponse)
async def orchestrator_turn(
    body: OrchestratorRequest,
    provider: LlmProvider = Depends(_provider),
) -> OrchestratorResponse:
    graph = OrchestratorGraph(provider=provider)
    return await graph.run(body)


async def _orchestrator_stream_generator(
    body: OrchestratorRequest,
    provider: LlmProvider,
) -> AsyncIterator[str]:
    queue: asyncio.Queue[str | None] = asyncio.Queue()

    async def emit(event: str, data: dict[str, Any]) -> None:
        await queue.put(_format_sse(event, data))

    graph = OrchestratorGraph(provider=provider)

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


@router.post("/agents/orchestrator/turn/stream")
async def orchestrator_turn_stream(
    body: OrchestratorRequest,
    provider: LlmProvider = Depends(_provider),
) -> StreamingResponse:
    return StreamingResponse(
        _orchestrator_stream_generator(body, provider),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
