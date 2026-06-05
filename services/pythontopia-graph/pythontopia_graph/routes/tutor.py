"""SSE streaming endpoint for the TutorAgent.

答疑链路（R-14 真协同）：不再只有 馆员(RAG)+导师(LLM)，而是把资源流水线里的
三道审查迁到答案上，让答疑真正经过 馆员 → 导师 → 审查官 → 校对官 → 安全员。
开场发一个 ``plan`` 工位清单 + 每个智能体 ``start``/``finish`` 的 ``trace`` 事件，
驱动侧栏 marvis「智能体工作室」实时点亮工位与交接动画（与资源流同一套线协议）。
"""

from __future__ import annotations

import json
import os
import time
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
from pythontopia_graph.nodes.tutor_review import (
    grounding_review,
    hallucination_review,
    safety_review,
)
from pythontopia_graph.schemas.tutor import TutorAskRequest

router = APIRouter()

_TOP_K = 5

# 答疑开场工位清单：与资源流 `plan` 同形状，让侧栏 marvis 复用同一套渲染 + 交接动画。
# 忠实反映真实节点：evidence_fetcher / TutorAgent / 三道审查，绝不摆假 agent。
_ASK_PLAN: dict[str, Any] = {
    "type": "ASK",
    "nodes": [
        {"node": "evidence_fetcher", "agent_label": "证据检索·馆员", "kind": "evidence"},
        {"node": "TutorAgent", "agent_label": "导师·作答", "kind": "tutor"},
        {"node": "grounding_critic", "agent_label": "引用审查·审查官", "kind": "grounding"},
        {
            "node": "hallucination_detector",
            "agent_label": "防幻觉核查·校对官",
            "kind": "hallucination",
        },
        {"node": "safety_critic", "agent_label": "安全审查·安全员", "kind": "safety"},
    ],
}


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


def _trace_start(node: str) -> str:
    return _format_sse(
        "trace",
        {"node": node, "phase": "start", "status": "running", "elapsed_ms": 0, "note": None},
    )


def _trace_finish(node: str, status: str, elapsed_ms: int, **extra: Any) -> str:
    payload: dict[str, Any] = {
        "node": node,
        "phase": "finish",
        "status": status,
        "elapsed_ms": elapsed_ms,
    }
    payload.update(extra)
    return _format_sse("trace", payload)


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

            # 开场工位清单（零 LLM 成本，在任何节点运行前发出）。
            yield _format_sse("plan", _ASK_PLAN)

            # ── 馆员：课件 RAG 检索 ──
            started = time.monotonic()
            evidence = await evidence_fetcher.fetch(
                kc_codes=kc_codes, query=query, top_k=_TOP_K,
            )
            yield _trace_finish(
                "evidence_fetcher",
                "ok",
                int((time.monotonic() - started) * 1000),
                chunks=len(evidence.chunks),
            )

            # ── 导师：流式作答 ──
            agent = TutorAgent(provider)
            history = [{"role": t.role, "content": t.content} for t in body.conversation_history]
            collected: list[str] = []

            yield _trace_start("TutorAgent")
            tutor_started = time.monotonic()
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
            yield _trace_finish(
                "TutorAgent", "ok", int((time.monotonic() - tutor_started) * 1000)
            )

            # ── 审查官：引用审查（答案 [页 X] 是否命中证据页） ──
            yield _trace_start("grounding_critic")
            g_started = time.monotonic()
            grounding = grounding_review(answer, evidence.chunks)
            yield _trace_finish(
                "grounding_critic",
                "ok" if grounding.passed else "retry",
                int((time.monotonic() - g_started) * 1000),
                note=str(grounding.reasons),
            )

            # ── 校对官：防幻觉（统计型数字是否有据） ──
            yield _trace_start("hallucination_detector")
            h_started = time.monotonic()
            hallucination = hallucination_review(answer, evidence.chunks)
            yield _trace_finish(
                "hallucination_detector",
                "ok" if hallucination.passed else "retry",
                int((time.monotonic() - h_started) * 1000),
                note=str(hallucination.reasons),
            )

            # ── 安全员：泄答 / 歧视 / PII / 危险代码 ──
            yield _trace_start("safety_critic")
            s_started = time.monotonic()
            safety = safety_review(answer)
            yield _trace_finish(
                "safety_critic",
                "ok" if safety.passed else "fail",
                int((time.monotonic() - s_started) * 1000),
                note=str(safety.reasons),
            )

            # ── 汇总结果（答案优先：审查只标注、不丢弃答案；安全失败时不再附图解） ──
            based_on_general = not evidence.chunks
            # 命中证据按 page_id 去重投影成可展示引用卡（页码 + 原文片段），page_ids 同源同序，
            # 供前端「课件证据」面板（CoursewareEvidence）渲染——复用 TutorAgent 同一套截断逻辑。
            citations = TutorAgent._build_citations(evidence.chunks)
            evidence_list = [c.model_dump(mode="json") for c in citations]
            page_ids = [c.page_id for c in citations]
            combined_passed = grounding.passed and hallucination.passed
            if based_on_general:
                grounding_status = "NO_EVIDENCE"
            elif combined_passed:
                grounding_status = "PASSED"
            else:
                grounding_status = "FAILED"

            # 图解按需触发：仅当学生语句明确「要图/流程/结构」且安全通过时生成一张
            # 贴合该主题的 mermaid 结构图（LLM + 课件证据，确定式兜底）。
            visual_aids: list[dict[str, Any]] = []
            if safety.passed and TutorAgent.wants_diagram(base_question):
                aids = await agent.build_diagram(base_question, evidence.chunks)
                visual_aids = [aid.model_dump(mode="json") for aid in aids]

            # 「课件证据」整组一句话总览说明（结合提问/上下文/画像）；best-effort，失败回空串。
            evidence_note = await agent.summarize_evidence(
                base_question, context, body.profile_brief, evidence.chunks,
            )

            yield _format_sse("result", _build_result(
                answer=answer,
                page_ids=page_ids,
                evidence=evidence_list,
                grounding_status=grounding_status,
                combined_passed=combined_passed,
                grounding=grounding,
                hallucination=hallucination,
                safety=safety,
                based_on_general=based_on_general,
                visual_aids=visual_aids,
                evidence_note=evidence_note,
            ))
        except Exception as exc:
            yield _format_sse("error", {"code": "TUTOR_FAILED", "message": str(exc)})

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


def _build_result(
    *,
    answer: str,
    page_ids: list[int],
    evidence: list[dict[str, Any]],
    grounding_status: str,
    combined_passed: bool,
    grounding: Any,
    hallucination: Any,
    safety: Any,
    based_on_general: bool,
    visual_aids: list[dict[str, Any]],
    evidence_note: str = "",
) -> dict[str, Any]:
    """组装答疑结果 payload。

    同时给出 snake_case 与 camelCase 两套键：
      - Java ``TutorService.persistSession`` 读 snake_case（``answer_md`` /
        ``evidence_page_ids`` / ``grounding_status``）落库；
      - 前端 ``useSseStream`` 直接 ``JSON.parse`` 后按 camelCase 读（``answerMd`` /
        ``evidencePageIds`` / ``groundingStatus``，``visual_aids`` 历史沿用 snake）。
    另附 ``review`` 三道审查明细，供答案气泡展示「已过审查官/校对官/安全员」。
    """
    return {
        # snake_case（落库 + 规范）
        "answer_md": answer,
        "evidence_page_ids": page_ids,
        "evidence": evidence,
        "grounding_result": {
            "passed": combined_passed,
            "reasons": list(grounding.reasons) + list(hallucination.reasons),
        },
        "safety_result": {"passed": safety.passed, "reasons": list(safety.reasons)},
        "based_on_general_knowledge": based_on_general,
        "grounding_status": grounding_status,
        "visual_aids": visual_aids,
        # 「课件证据」整组总览说明（结合提问/上下文/画像；无证据或生成失败时为空串）。
        "evidence_note": evidence_note,
        # camelCase（前端直读）
        "answerMd": answer,
        "evidencePageIds": page_ids,
        "groundingStatus": grounding_status,
        # 三道审查明细（透明化）
        "review": {
            "grounding": {"passed": grounding.passed, "reasons": list(grounding.reasons)},
            "hallucination": {
                "passed": hallucination.passed,
                "reasons": list(hallucination.reasons),
            },
            "safety": {"passed": safety.passed, "reasons": list(safety.reasons)},
        },
    }
