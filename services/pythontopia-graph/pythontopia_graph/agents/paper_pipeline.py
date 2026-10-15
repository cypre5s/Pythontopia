"""章末大测试生成图（§10 节点链）。

paper_designer → grounding_critic（客观题证据页校验）→ sandbox_self_test（编程题沙箱自测）
→ safety_critic。任一节点失败 → 抛 ``ChapterTestGraphError``（路由转结构化 502，backend 落
FAILED 并重试/兜底）。绝不静默降级成纯文本。

house style 对齐 ``graph/resource_graph.py``：纯异步编排类 + GraphTraceStep 轨迹 + 结构化错误，
不在本服务内引入额外 StateGraph 拓扑。
"""

from __future__ import annotations

import time
from datetime import datetime, timezone
from typing import Awaitable, TypeVar
from uuid import uuid4

from pythontopia_graph.agents.paper_designer import PaperDesignerAgent, PaperDesignError
from pythontopia_graph.agents.paper_reviews import (
    grounding_critic,
    safety_critic,
    sandbox_self_test,
)
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, EvidenceFetcher
from pythontopia_graph.schemas.chapter_test import (
    ChapterTestErrorDetail,
    ChapterTestGenerateRequest,
    ChapterTestGenerateResponse,
    GeneratorMeta,
    GroundingReport,
    QuestionPaper,
    SafetyReport,
    SelfTestReport,
)
from pythontopia_graph.schemas.resource import GraphTraceStep

T = TypeVar("T")

AGENT_CHAIN = "PaperDesignerAgent→grounding_critic→sandbox_self_test→safety_critic"
_EVIDENCE_TOP_K = 12


class ChapterTestGraphError(RuntimeError):
    """节点失败的结构化错误，``detail`` 直接序列化进 502 响应体。"""

    def __init__(self, detail: ChapterTestErrorDetail) -> None:
        super().__init__(f"{detail.stage}: {detail.message}")
        self.detail = detail


class ChapterTestGraph:
    def __init__(
        self,
        *,
        provider: LlmProvider,
        evidence_fetcher: EvidenceFetcher,
    ) -> None:
        self._provider = provider
        self._evidence_fetcher = evidence_fetcher

    async def run(
        self, request: ChapterTestGenerateRequest
    ) -> ChapterTestGenerateResponse:
        run_id = f"chtest-{uuid4().hex[:12]}"
        trace: list[GraphTraceStep] = []

        # 1) 证据检索（RAG）
        query = request.focus_hint or " ".join(request.kc_codes)
        evidence = await self._async_node(
            trace,
            "evidence_fetcher",
            self._evidence_fetcher.fetch(
                kc_codes=request.kc_codes, query=query, top_k=_EVIDENCE_TOP_K
            ),
            run_id=run_id,
            chapter=request.chapter,
        )
        evidence_pages = evidence.page_ids

        # 2) 出题
        designer = PaperDesignerAgent(self._provider)
        paper = await self._async_node(
            trace,
            "paper_designer",
            designer.design(request, evidence),
            run_id=run_id,
            chapter=request.chapter,
            evidence_pages=evidence_pages,
            expected=(PaperDesignError,),
        )

        # 3) 引用审查
        grounding = grounding_critic(paper, evidence)
        self._record(trace, "grounding_critic", grounding.passed, note=str(grounding.reasons) or None)
        if not grounding.passed:
            self._fail(
                stage="grounding_critic",
                message="objective items not grounded to RAG evidence pages",
                run_id=run_id,
                chapter=request.chapter,
                trace=trace,
                evidence_pages=evidence_pages,
                grounding=grounding,
            )

        # 4) 编程题沙箱自测
        self_test = sandbox_self_test(paper)
        self._record(trace, "sandbox_self_test", self_test.passed, note=str(self_test.reasons) or None)
        if not self_test.passed:
            self._fail(
                stage="sandbox_self_test",
                message="reference_solution failed self-test cases",
                run_id=run_id,
                chapter=request.chapter,
                trace=trace,
                evidence_pages=evidence_pages,
                grounding=grounding,
                self_test=self_test,
            )

        # 5) 安全审查
        safety = safety_critic(paper)
        self._record(trace, "safety_critic", safety.passed, note=str(safety.reasons) or None)
        if not safety.passed:
            self._fail(
                stage="safety_critic",
                message="safety review failed",
                run_id=run_id,
                chapter=request.chapter,
                trace=trace,
                evidence_pages=evidence_pages,
                grounding=grounding,
                safety=safety,
                self_test=self_test,
            )

        meta = self._build_meta(
            run_id=run_id,
            request=request,
            paper=paper,
            evidence_pages=evidence_pages,
            grounding=grounding,
            safety=safety,
            self_test=self_test,
            trace=trace,
        )
        return ChapterTestGenerateResponse(
            status="SUCCESS",
            questions=paper,
            profile_snapshot=self._profile_snapshot(request),
            generator_meta=meta,
        )

    # -- nodes / helpers ---------------------------------------------------- #
    async def _async_node(
        self,
        trace: list[GraphTraceStep],
        name: str,
        awaitable: Awaitable[T],
        *,
        run_id: str,
        chapter: int,
        evidence_pages: list[int] | None = None,
        expected: tuple[type[Exception], ...] = (),
    ) -> T:
        started = time.monotonic()
        try:
            result = await awaitable
        except Exception as exc:  # noqa: BLE001 - 任一节点异常都转结构化 502
            elapsed = int((time.monotonic() - started) * 1000)
            trace.append(
                GraphTraceStep(
                    node=name,
                    elapsed_ms=elapsed,
                    status="fail",
                    note=type(exc).__name__,
                )
            )
            message = str(exc) if isinstance(exc, expected) else type(exc).__name__
            self._fail(
                stage=name,
                message=message,
                run_id=run_id,
                chapter=chapter,
                trace=trace,
                evidence_pages=evidence_pages,
            )
        elapsed = int((time.monotonic() - started) * 1000)
        trace.append(GraphTraceStep(node=name, elapsed_ms=elapsed, status="ok"))
        return result

    @staticmethod
    def _record(
        trace: list[GraphTraceStep], name: str, passed: bool, note: str | None = None
    ) -> None:
        trace.append(
            GraphTraceStep(
                node=name,
                elapsed_ms=0,
                status="ok" if passed else "fail",
                note=note,
            )
        )

    def _fail(
        self,
        *,
        stage: str,
        message: str,
        run_id: str,
        chapter: int,
        trace: list[GraphTraceStep],
        evidence_pages: list[int] | None = None,
        grounding: GroundingReport | None = None,
        safety: SafetyReport | None = None,
        self_test: SelfTestReport | None = None,
    ) -> None:
        detail = ChapterTestErrorDetail(
            stage=stage,
            message=message,
            run_id=run_id,
            chapter=chapter,
            evidence_page_ids=list(evidence_pages or []),
            grounding=grounding,
            safety=safety,
            self_test=self_test,
            graph_trace=list(trace),
        )
        raise ChapterTestGraphError(detail)

    def _build_meta(
        self,
        *,
        run_id: str,
        request: ChapterTestGenerateRequest,
        paper: QuestionPaper,
        evidence_pages: list[int],
        grounding: GroundingReport,
        safety: SafetyReport,
        self_test: SelfTestReport,
        trace: list[GraphTraceStep],
    ) -> GeneratorMeta:
        counts = paper.type_counts()
        input_summary = (
            f"user={request.user_id} chapter={request.chapter} "
            f"kc={len(request.kc_codes)} "
            f"err_tags={len(request.profile_brief.error_patterns)} "
            f"err_kcs={len(request.errorbook_brief.frequent_error_kcs)}"
        )
        output_summary = (
            f"{len(paper.items)} items "
            f"(choice={counts['CHOICE']},judge={counts['JUDGE']},"
            f"fill={counts['FILL']},coding={counts['CODING']}) "
            f"score={paper.total_score()}"
        )
        return GeneratorMeta(
            run_id=run_id,
            agent=AGENT_CHAIN,
            chapter=request.chapter,
            input_summary=input_summary,
            output_summary=output_summary,
            counts={k.lower(): v for k, v in counts.items()},
            requested_counts=request.counts.as_dict(),
            total_score=paper.total_score(),
            evidence_page_ids=list(evidence_pages),
            grounding=grounding,
            safety=safety,
            self_test=self_test,
            graph_trace=list(trace),
            fallback="none",
            created_at=datetime.now(timezone.utc).isoformat(),
        )

    @staticmethod
    def _profile_snapshot(request: ChapterTestGenerateRequest) -> dict[str, object]:
        return {
            "chapter": request.chapter,
            "kc_codes": list(request.kc_codes),
            "profile_brief": request.profile_brief.model_dump(),
            "errorbook_brief": request.errorbook_brief.model_dump(),
            "requested_counts": request.counts.as_dict(),
        }


def evidence_bundle_is_empty(evidence: EvidenceBundle) -> bool:
    """便捷判断（供测试/调用方）：无命中或显式 no_evidence_found。"""
    return evidence.no_evidence_found or not evidence.page_ids
