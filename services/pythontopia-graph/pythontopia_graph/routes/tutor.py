"""SSE streaming endpoint for the TutorAgent."""

from __future__ import annotations

import json
import os
from collections.abc import AsyncIterator
from typing import Any

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from pythontopia_graph.agents.tutor_agent import TutorAgent
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceFetcher,
    HttpRagEvidenceFetcher,
)
from pythontopia_graph.schemas.tutor import TutorAskRequest

router = APIRouter()

_TOP_K = 5


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


def _format_sse(event: str, data: dict[str, Any]) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/agents/tutor/ask/stream")
async def tutor_ask_stream(
    body: TutorAskRequest,
    provider: LlmProvider = Depends(_provider),
    evidence_fetcher: EvidenceFetcher = Depends(_evidence_fetcher),
) -> StreamingResponse:
    async def generate() -> AsyncIterator[str]:
        try:
            base_question = body.question
            context = (body.context or "").strip()
            # 上下文存在时，把它并入检索 query，让证据更贴合当前视频/PPT/题目。
            query = f"{context}\n{base_question}".strip() if context else base_question
            kc_codes = body.kc_codes or []
            evidence = await evidence_fetcher.fetch(
                kc_codes=kc_codes, query=query, top_k=_TOP_K,
            )
            yield _format_sse("trace", {
                "node": "evidence_fetcher",
                "status": "ok",
                "elapsed_ms": 0,
                "chunks": len(evidence.chunks),
            })

            agent = TutorAgent(provider)
            history = [{"role": t.role, "content": t.content} for t in body.conversation_history]
            collected: list[str] = []

            yield _format_sse("trace", {"node": "TutorAgent", "status": "streaming", "elapsed_ms": 0})

            # 上下文随题面一并给到 agent，让引导贴合「学生当前在看/在做什么」。
            agent_question = (
                f"【当前学习上下文】{context}\n\n【学生问题】{base_question}"
                if context
                else base_question
            )
            async for token in agent.ask_stream(
                question=agent_question,
                profile_brief=body.profile_brief,
                conversation_history=history,
                evidence_chunks=evidence.chunks,
            ):
                collected.append(token)
                yield _format_sse("token", {"delta": token})

            answer = "".join(collected)
            response = agent._build_response(answer, evidence.chunks)
            yield _format_sse("result", response.model_dump(mode="json"))
        except Exception as exc:
            yield _format_sse("error", {"code": "TUTOR_FAILED", "message": str(exc)})

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
