"""导学工作流 SSE 端点（6 阶段卡片式导学，移植 Alethicode tutor workflow）。

POST /agents/tutor-workflow/run/stream：一次工作流事件 = 一次 SSE 请求（替代源版
异步 run + WebSocket 回推）。流内事件：
- trace（STEP_*）：validate_transition / evidence_fetcher / card.<key> 节点进度；
- result（RUN_FINISHED）：WorkflowRunResult（phase / card / available_actions / seq）；
- error（RUN_ERROR）：TRANSITION_VIOLATION / SCHEMA_VIOLATION / SYSTEM_ERROR 等
  failure bucket，fail fast 不静默降级。

阶段权威状态在 tutor_workflow_session（进程内单 worker）；Java 收到 result 后落投影表。
请求可带 restore_phase（Java 投影回放），仅对进程内尚无该会话时生效。
"""

from __future__ import annotations

import asyncio
import os
import uuid
from collections.abc import AsyncIterator
from typing import Any

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from pydantic import Field

from pythontopia_graph import sse_agui
from pythontopia_graph.graph import tutor_workflow_session as session_store
from pythontopia_graph.graph.tutor_workflow_graph import TutorWorkflowGraph
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceFetcher,
    HttpRagEvidenceFetcher,
)
from pythontopia_graph.schemas.tutor_workflow import (
    WorkflowRunRequest,
    WorkflowRunResult,
)

router = APIRouter()


class WorkflowRunEnvelope(WorkflowRunRequest):
    """请求信封：在 run 请求上附加 Java 投影回放字段（进程重启后恢复阶段）。"""

    restore_phase: str | None = Field(default=None)
    restore_seq: int = Field(default=0, ge=0)


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


def _card_short_text(card_key: str, card: dict[str, Any]) -> str:
    """卡片一句话摘要（CHAT 引用上下文用）。"""
    for field in (
        "problem_restatement",
        "analysis",
        "description",
        "root_cause",
        "success_summary",
        "review_content",
        "summary",
        "drill_goal",
        "title",
        "content",
    ):
        value = card.get(field)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return card_key


# 卡片生成是长 LLM 调用：每 10s 无输出补一行 SSE 注释 ping，防代理/浏览器断连。
_HEARTBEAT_S = 10.0
_PING = ": ping\n\n"
_SENTINEL: Any = object()


@router.post("/agents/tutor-workflow/run/stream")
async def tutor_workflow_run_stream(
    body: WorkflowRunEnvelope,
    provider: LlmProvider = Depends(_provider),
    evidence_fetcher: EvidenceFetcher = Depends(_evidence_fetcher),
) -> StreamingResponse:
    async def stages() -> AsyncIterator[str]:
        # 节点 trace 经队列真流式推给客户端（卡片生成期间持续可见进度 + 心跳保活）。
        queue: asyncio.Queue[Any] = asyncio.Queue()

        async def emit(event: str, data: dict[str, Any]) -> None:
            await queue.put(sse_agui.translate(event, data))

        async def run_graph() -> dict[str, Any]:
            try:
                if body.restore_phase:
                    # Java 投影回放：进程内尚无会话时按持久化快照恢复阶段（已存在则忽略）。
                    await session_store.restore_phase(
                        body.session_id, body.restore_phase, body.restore_seq
                    )
                session = await session_store.get_state(body.session_id)
                graph = TutorWorkflowGraph(provider, evidence_fetcher, emit=emit)
                request = WorkflowRunRequest.model_validate(
                    body.model_dump(exclude={"restore_phase", "restore_seq"})
                )
                state = await graph.run(
                    {
                        "request": request,
                        "current_phase": session.phase,
                        "last_cards": session.cards,
                        "chat_history": session.chat_history,
                        "next_practice_direction": _last_direction(session.cards),
                    }
                )
                return {"state": dict(state), "session_phase": session.phase}
            finally:
                await queue.put(_SENTINEL)

        task = asyncio.create_task(run_graph())
        try:
            while True:
                try:
                    item = await asyncio.wait_for(queue.get(), timeout=_HEARTBEAT_S)
                except TimeoutError:
                    yield _PING
                    continue
                if item is _SENTINEL:
                    break
                yield item

            outcome = await task
            state = outcome["state"]
            if state.get("runtime_state") == "FAILED":
                yield sse_agui.translate(
                    "error",
                    {
                        "code": state.get("failure_bucket") or "SYSTEM_ERROR",
                        "message": state.get("last_error") or "tutor workflow run failed",
                    },
                )
                return

            card_key = state.get("card_key")
            card = state.get("card")
            card_id = f"{body.session_id}:{uuid.uuid4().hex[:8]}" if card_key else None
            chat_turns: list[dict[str, str]] | None = None
            if card_key == "ai_reply" and isinstance(card, dict):
                chat_turns = [
                    {"role": "user", "content": str(body.event_data.get("message", ""))},
                    {"role": "assistant", "content": str(card.get("content", ""))},
                ]
            extra: dict[str, Any] | None = None
            if card_key == "post_ac" and isinstance(card, dict):
                extra = {"next_practice_direction": card.get("next_practice_direction", "")}
            new_phase = state.get("new_phase") or outcome["session_phase"]
            seq = await session_store.commit_run(
                body.session_id,
                phase=new_phase,
                card_key=card_key,
                card_id=card_id,
                short_text=_card_short_text(card_key, card) if card_key and card else "",
                extra=extra,
                chat_turns=chat_turns,
            )
            if isinstance(card, dict) and card_id is not None:
                card = {**card, "card_id": card_id}

            result = WorkflowRunResult(
                session_id=body.session_id,
                event=body.event,
                phase=new_phase,
                card_key=card_key,
                card=card,
                available_actions=state.get("available_actions", []),
                seq=seq,
            )
            yield sse_agui.translate("result", result.model_dump(mode="json"))
        except Exception as exc:  # noqa: BLE001 - 端点级兜底，fail fast 上报
            yield sse_agui.translate(
                "error", {"code": "TUTOR_WORKFLOW_FAILED", "message": str(exc)}
            )
        finally:
            if not task.done():
                task.cancel()

    return StreamingResponse(
        stages(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


def _last_direction(cards: list[dict[str, Any]]) -> str:
    """会话内最近一张 post_ac 卡的练习方向（TRANSFER 依赖；short_text 不含方向时为空）。"""
    for card in reversed(cards):
        if card.get("card_key") == "post_ac":
            direction = card.get("next_practice_direction")
            if isinstance(direction, str) and direction.strip():
                return direction
    return ""
