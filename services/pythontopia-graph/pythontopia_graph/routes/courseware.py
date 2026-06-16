"""课件问答（LLM 作答 + 页码引用）路由。

`POST /agents/courseware/answer` → :class:`CoursewareAnswerOutput`

把「课件检索」从"原文片段卡"升级为"LLM 回答 + 可跳转页码引用"：
1. 用 :class:`HttpRagEvidenceFetcher` 取 top-k 命中片段（与导师答疑同一证据源）；
2. 一次 LLM 调用，**只依据命中片段**作答，并标注用到的页码；
3. 引用页码经服务端二次过滤——只保留确实出现在证据里的页码（防幻觉，不伪造页码）。

无证据时 `no_evidence=True` 且不编造页码（AGENTS 防幻觉红线）。
后端 `CoursewareController` 做 HTTP 代理（允许学生访问）。
"""

from __future__ import annotations

import json as _json
import logging
from typing import Any, cast

from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel, Field

from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceBundle,
    EvidenceFetcher,
    HttpRagEvidenceFetcher,
)

router = APIRouter()
LOG = logging.getLogger(__name__)


class CoursewareAnswerInput(BaseModel):
    query: str = Field(min_length=1, max_length=512)
    kc_codes: list[str] = Field(default_factory=list)
    top_k: int = Field(default=6, ge=1, le=12)


class CoursewareCitation(BaseModel):
    page_id: int
    page_no: int
    chapter_id: int | None = None
    quote: str = ""


class CoursewareAnswerOutput(BaseModel):
    answer_md: str
    citations: list[CoursewareCitation] = Field(default_factory=list)
    no_evidence: bool = False


_SYSTEM_PROMPT = (
    "你是 Pythontopia 的课件问答助手，面向高校 Python 初学者。"
    "我会给你学生的问题，以及若干条课件原文证据（每段前用「[页 N]」标注页码）。"
    "请用简体中文、条理清晰地回答，可用要点。硬性要求："
    "1) 只能依据给定证据作答，严禁编造证据里没有的事实、数字、结论或页码；"
    "2) 在正文相关处用「[页 N]」标注依据，N 只能是给定证据中出现过的页码；"
    "3) 若证据不足以回答，明说「课件中未直接覆盖该问题」，不要硬答。"
    '只输出 JSON：{"answer_md": "<带[页N]标注的Markdown回答>", '
    '"cited_page_nos": [<你确实引用到的页码整数，必须来自给定证据>]}'
)


def _provider(request: Request) -> LlmProvider:
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is not None:
        return cast(LlmProvider, provider)
    return provider_from_env()


def _fetcher(request: Request) -> EvidenceFetcher:
    fetcher = getattr(request.app.state, "rag_fetcher", None)
    if fetcher is not None:
        return cast(EvidenceFetcher, fetcher)
    return HttpRagEvidenceFetcher()


def _evidence_text(bundle: EvidenceBundle) -> str:
    return "\n\n".join(f"[页 {hit.page_no}] {hit.text}".strip() for hit in bundle.chunks)


def _fallback_answer(bundle: EvidenceBundle) -> str:
    """LLM 不可用 / dry-run / 解析失败时，只回显证据要点（绝不编造）。"""
    lines = ["（本地未接入大模型，先给出检索到的课件要点）", ""]
    for hit in bundle.chunks[:5]:
        snippet = hit.text.strip().replace("\n", " ")
        if len(snippet) > 120:
            snippet = snippet[:120].rstrip() + "…"
        lines.append(f"- [页 {hit.page_no}] {snippet}")
    return "\n".join(lines)


@router.post("/agents/courseware/answer", response_model=CoursewareAnswerOutput)
async def courseware_answer(
    body: CoursewareAnswerInput,
    provider: LlmProvider = Depends(_provider),
    fetcher: EvidenceFetcher = Depends(_fetcher),
) -> CoursewareAnswerOutput:
    bundle = await fetcher.fetch(
        kc_codes=list(body.kc_codes), query=body.query, top_k=body.top_k
    )
    if bundle.no_evidence_found or not bundle.chunks:
        return CoursewareAnswerOutput(
            answer_md="课件中未直接覆盖该问题，换个关键词或 @ 指定章节/知识点再试试。",
            citations=[],
            no_evidence=True,
        )

    # page_no → 命中片段（用于把 LLM 给的引用页码映射回 page_id，并做防幻觉过滤）。
    by_page_no: dict[int, Any] = {}
    for hit in bundle.chunks:
        by_page_no.setdefault(hit.page_no, hit)

    messages = [
        {"role": "system", "content": _SYSTEM_PROMPT},
        {
            "role": "user",
            "content": (
                f"学生问题：{body.query}\n\n"
                f"【课件证据】（每段前是页码）：\n{_evidence_text(bundle)}"
            ),
        },
    ]

    answer_md = ""
    cited_nos: list[int] = []
    try:
        resp = await provider.chat(messages, json_mode=True)
        data: Any = resp.content_json
        if data is None and resp.content_text:
            try:
                data = _json.loads(resp.content_text)
            except (ValueError, TypeError):
                data = None
        if isinstance(data, dict):
            raw_answer = data.get("answer_md")
            if isinstance(raw_answer, str):
                answer_md = raw_answer.strip()
            raw_pages = data.get("cited_page_nos")
            if isinstance(raw_pages, list):
                for value in raw_pages:
                    try:
                        cited_nos.append(int(value))
                    except (TypeError, ValueError):
                        continue
        elif isinstance(resp.content_text, str):
            answer_md = resp.content_text.strip()
    except Exception as exc:  # noqa: BLE001 - LLM 故障：回退证据要点，不阻断、不伪造
        LOG.warning("courseware answer LLM failed: %s", exc)

    if not answer_md:
        answer_md = _fallback_answer(bundle)

    # 防幻觉：引用页码只保留确实出现在证据里的；LLM 没给则回退证据前若干页。
    citations: list[CoursewareCitation] = []
    seen: set[int] = set()
    for page_no in cited_nos:
        hit = by_page_no.get(page_no)
        if hit is None or page_no in seen:
            continue
        seen.add(page_no)
        quote = hit.text.strip().replace("\n", " ")
        citations.append(
            CoursewareCitation(
                page_id=hit.page_id,
                page_no=hit.page_no,
                chapter_id=hit.chapter_id,
                quote=quote[:160],
            )
        )
    if not citations:
        for hit in bundle.chunks[:5]:
            if hit.page_no in seen:
                continue
            seen.add(hit.page_no)
            quote = hit.text.strip().replace("\n", " ")
            citations.append(
                CoursewareCitation(
                    page_id=hit.page_id,
                    page_no=hit.page_no,
                    chapter_id=hit.chapter_id,
                    quote=quote[:160],
                )
            )

    return CoursewareAnswerOutput(answer_md=answer_md, citations=citations, no_evidence=False)
