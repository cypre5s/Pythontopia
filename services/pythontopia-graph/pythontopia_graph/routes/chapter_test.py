"""章末大测试生成端点（§10）。

``POST /agents/chapter-test/generate``：输入 user_id/chapter/kc_codes/profile_brief/
errorbook_brief/counts → 输出 §3 题面 JSON + 生成审计 meta。

节点链失败 → 结构化 502（detail 为 ``ChapterTestErrorDetail``），backend 据此落
chapter_test.status=FAILED 并重试。绝不静默降级成纯文本或缩减题量。
"""

from __future__ import annotations

import os
from typing import cast

from fastapi import APIRouter, Depends, HTTPException, Request, status

from pythontopia_graph.agents.paper_pipeline import (
    ChapterTestGraph,
    ChapterTestGraphError,
)
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceFetcher,
    HttpRagEvidenceFetcher,
)
from pythontopia_graph.schemas.chapter_test import (
    ChapterTestGenerateRequest,
    ChapterTestGenerateResponse,
)

router = APIRouter()


def _provider(request: Request) -> LlmProvider:
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is not None:
        return cast(LlmProvider, provider)
    return provider_from_env()


def _evidence_fetcher(request: Request) -> EvidenceFetcher:
    fetcher = getattr(request.app.state, "evidence_fetcher", None)
    if fetcher is not None:
        return cast(EvidenceFetcher, fetcher)
    return HttpRagEvidenceFetcher(os.environ.get("PYT_RAG_URL"))


@router.post(
    "/agents/chapter-test/generate",
    response_model=ChapterTestGenerateResponse,
)
async def generate_chapter_test(
    body: ChapterTestGenerateRequest,
    provider: LlmProvider = Depends(_provider),
    evidence_fetcher: EvidenceFetcher = Depends(_evidence_fetcher),
) -> ChapterTestGenerateResponse:
    graph = ChapterTestGraph(provider=provider, evidence_fetcher=evidence_fetcher)
    try:
        return await graph.run(body)
    except ChapterTestGraphError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=exc.detail.model_dump(mode="json"),
        ) from exc
