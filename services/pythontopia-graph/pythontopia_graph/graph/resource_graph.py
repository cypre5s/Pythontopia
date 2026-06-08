from __future__ import annotations

import asyncio
import time
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from typing import Any

from pythontopia_graph.agents.code_lab_agent import CodeLabAgent
from pythontopia_graph.agents.handout_agent import HandoutAgent
from pythontopia_graph.agents.mind_map_agent import MindMapAgent
from pythontopia_graph.agents.practice_agent import PracticeAgent
from pythontopia_graph.agents.reading_agent import ReadingAgent
from pythontopia_graph.agents.storyboard_agent import StoryboardAgent
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, EvidenceFetcher
from pythontopia_graph.nodes.grounding_critic import grounding_critic
from pythontopia_graph.nodes.hallucination_detector import hallucination_detector
from pythontopia_graph.nodes.output_persistor import build_response
from pythontopia_graph.nodes.safety_critic import safety_critic
from pythontopia_graph.schemas.resource import (
    GraphTraceStep,
    GroundingResult,
    ResourceDraft,
    ResourceProduceRequest,
    ResourceProduceResponse,
    SafetyResult,
)


class ResourceGraphError(RuntimeError):
    def __init__(self, message: str, response: ResourceProduceResponse) -> None:
        super().__init__(message)
        self.response = response


@dataclass
class ResourceGraphConfig:
    valid_kc_codes: set[str]


class ResourceGraph:
    MAX_RETRIES = 2

    # 设计师工位的中文 label（仅用于线上 `plan` 事件，零 LLM 成本）。键对应
    # ResourceProduceRequest.type，与 _select_agent 选出的设计师 AGENT_NAME 配对。
    _DESIGNER_LABELS: dict[str, str] = {
        "HANDOUT": "讲义设计师",
        "MINDMAP": "思维导图师",
        "PRACTICE": "出题官",
        "CODE_LAB": "代码教练",
        "READING": "阅读策展人",
        "STORYBOARD": "动画分镜师",
    }

    def __init__(
        self,
        *,
        provider: LlmProvider,
        evidence_fetcher: EvidenceFetcher,
        config: ResourceGraphConfig,
    ) -> None:
        self._provider = provider
        self._evidence_fetcher = evidence_fetcher
        self._config = config

    async def run(self, request: ResourceProduceRequest) -> ResourceProduceResponse:
        trace: list[GraphTraceStep] = []

        evidence = await self._step(
            trace,
            "evidence_fetcher",
            self._fetch_evidence(request),
        )

        agent = self._select_agent(request)

        attempt = 0
        last_grounding: GroundingResult | None = None
        last_safety: SafetyResult | None = None
        draft: ResourceDraft | None = None
        revision_notes: list[str] | None = None
        while attempt <= self.MAX_RETRIES:
            draft = await self._step(
                trace,
                f"{agent.AGENT_NAME}.attempt{attempt}",
                agent.design(request, evidence, revision_notes=revision_notes),
            )

            grounding = grounding_critic(draft, evidence)
            self._record_sync(trace, "grounding_critic", grounding.passed)

            hallucination = hallucination_detector(draft, evidence, self._config.valid_kc_codes)
            self._record_sync(
                trace, "hallucination_detector", hallucination.passed, note=str(hallucination.reasons)
            )

            safety = safety_critic(draft)
            self._record_sync(trace, "safety_critic", safety.passed, note=str(safety.reasons))

            if not safety.passed:
                response = build_response(
                    artifact=draft,
                    resource_type=request.type,
                    evidence_page_ids=evidence.page_ids,
                    grounding=GroundingResult(passed=False, reasons=["SAFETY_FAILED"]),
                    safety=safety,
                    graph_trace=trace,
                    status="FAILED",
                )
                raise ResourceGraphError("safety failed", response)

            combined = GroundingResult(
                passed=grounding.passed and hallucination.passed,
                reasons=list(grounding.reasons) + list(hallucination.reasons),
                no_evidence_found=grounding.no_evidence_found,
                retried=attempt > 0,
            )
            last_grounding, last_safety = combined, safety
            if combined.passed:
                status = "PARTIAL" if combined.no_evidence_found else "SUCCESS"
                return build_response(
                    artifact=draft,
                    resource_type=request.type,
                    evidence_page_ids=evidence.page_ids,
                    grounding=combined,
                    safety=safety,
                    graph_trace=trace,
                    status=status,
                )
            # 收集失败原因供下一轮 revision_notes 回灌设计师
            revision_notes = list(grounding.reasons) + list(hallucination.reasons)
            if not revision_notes:
                revision_notes = ["引用审查或防幻觉核查未通过，请检查证据引用与内容准确性。"]
            attempt += 1

        assert draft is not None and last_grounding is not None and last_safety is not None
        response = build_response(
            artifact=draft,
            resource_type=request.type,
            evidence_page_ids=evidence.page_ids,
            grounding=last_grounding,
            safety=last_safety,
            graph_trace=trace,
            status="FAILED",
        )
        raise ResourceGraphError("grounding failed after retry", response)

    async def run_streaming(
        self,
        request: ResourceProduceRequest,
        emit: Callable[[str, dict[str, Any]], Awaitable[None]],
    ) -> None:
        """流式执行图：每步 SSE 回调。

        协议为落库 trace 超集：开场 plan 列出确定性工位；每节点先发 start 再 finish trace
        （含 phase 供前端动画）。落库 trace 与终端 result/error 与 run() 字节级一致。
        """
        trace: list[GraphTraceStep] = []

        try:
            agent = self._select_agent(request)
            # 开场工位清单：在任何节点运行前发出（零 LLM 成本）。
            await emit("plan", self._build_plan(request, agent))

            evidence = await self._step_streaming(
                emit,
                trace,
                "evidence_fetcher",
                self._fetch_evidence(request),
            )

            attempt = 0
            last_grounding: GroundingResult | None = None
            last_safety: SafetyResult | None = None
            draft: ResourceDraft | None = None
            revision_notes: list[str] | None = None
            while attempt <= self.MAX_RETRIES:
                draft = await self._step_streaming(
                    emit,
                    trace,
                    f"{agent.AGENT_NAME}.attempt{attempt}",
                    agent.design(request, evidence, revision_notes=revision_notes),
                )

                grounding = grounding_critic(draft, evidence)
                await self._record_sync_streaming(
                    emit, trace, "grounding_critic", grounding.passed,
                )
                await asyncio.sleep(0.05)

                hallucination = hallucination_detector(
                    draft, evidence, self._config.valid_kc_codes
                )
                await self._record_sync_streaming(
                    emit, trace, "hallucination_detector", hallucination.passed,
                    note=str(hallucination.reasons),
                )
                await asyncio.sleep(0.05)

                safety = safety_critic(draft)
                await self._record_sync_streaming(
                    emit, trace, "safety_critic", safety.passed, note=str(safety.reasons),
                )
                await asyncio.sleep(0.05)

                if not safety.passed:
                    response = build_response(
                        artifact=draft,
                        resource_type=request.type,
                        evidence_page_ids=evidence.page_ids,
                        grounding=GroundingResult(passed=False, reasons=["SAFETY_FAILED"]),
                        safety=safety,
                        graph_trace=trace,
                        status="FAILED",
                    )
                    await emit("error", {
                        "code": "SAFETY_FAILED",
                        "message": "safety failed",
                    })
                    return

                combined = GroundingResult(
                    passed=grounding.passed and hallucination.passed,
                    reasons=list(grounding.reasons) + list(hallucination.reasons),
                    no_evidence_found=grounding.no_evidence_found,
                    retried=attempt > 0,
                )
                last_grounding, last_safety = combined, safety
                if combined.passed:
                    status = "PARTIAL" if combined.no_evidence_found else "SUCCESS"
                    resp = build_response(
                        artifact=draft,
                        resource_type=request.type,
                        evidence_page_ids=evidence.page_ids,
                        grounding=combined,
                        safety=safety,
                        graph_trace=trace,
                        status=status,
                    )
                    await emit("result", resp.model_dump(mode="json"))
                    return
                # 收集失败原因供下一轮 revision_notes 回灌设计师
                revision_notes = list(grounding.reasons) + list(hallucination.reasons)
                if not revision_notes:
                    revision_notes = ["引用审查或防幻觉核查未通过，请检查证据引用与内容准确性。"]
                attempt += 1

            assert draft is not None and last_grounding is not None and last_safety is not None
            build_response(
                artifact=draft,
                resource_type=request.type,
                evidence_page_ids=evidence.page_ids,
                grounding=last_grounding,
                safety=last_safety,
                graph_trace=trace,
                status="FAILED",
            )
            await emit("error", {
                "code": "RESOURCE_GRAPH_FAILED",
                "message": "grounding failed after retry",
            })
        except Exception as exc:
            await emit("error", {
                "code": "RESOURCE_GRAPH_FAILED",
                "message": str(exc),
            })

    def _select_agent(self, request: ResourceProduceRequest):
        if request.type == "HANDOUT":
            return HandoutAgent(self._provider)
        if request.type == "MINDMAP":
            return MindMapAgent(self._provider)
        if request.type == "PRACTICE":
            return PracticeAgent(self._provider)
        if request.type == "CODE_LAB":
            return CodeLabAgent(self._provider)
        if request.type == "READING":
            return ReadingAgent(self._provider)
        if request.type == "STORYBOARD":
            return StoryboardAgent(self._provider)
        raise ValueError(f"Unsupported type: {request.type}")

    async def _fetch_evidence(self, request: ResourceProduceRequest) -> EvidenceBundle:
        query = request.focus_hint or " ".join(request.kc_codes)
        return await self._evidence_fetcher.fetch(kc_codes=request.kc_codes, query=query)

    async def _step(self, trace: list[GraphTraceStep], name: str, awaitable):
        started = time.monotonic()
        try:
            result = await awaitable
        except Exception as exc:  # noqa: BLE001
            elapsed_ms = int((time.monotonic() - started) * 1000)
            trace.append(GraphTraceStep(node=name, elapsed_ms=elapsed_ms, status="fail", note=str(exc)))
            raise
        elapsed_ms = int((time.monotonic() - started) * 1000)
        trace.append(GraphTraceStep(node=name, elapsed_ms=elapsed_ms, status="ok"))
        return result

    @staticmethod
    def _record_sync(
        trace: list[GraphTraceStep], name: str, passed: bool, note: str | None = None
    ) -> None:
        trace.append(
            GraphTraceStep(
                node=name,
                elapsed_ms=0,
                status="ok" if passed else "retry",
                note=note,
            )
        )

    # 流式专用助手（不影响同步 run() 与落库 trace）

    def _build_plan(
        self, request: ResourceProduceRequest, agent: Any
    ) -> dict[str, Any]:
        """开场工位清单：evidence → designer → grounding → hallucination → safety。

        设计师工位用 ``_select_agent`` 选出的真实 ``AGENT_NAME``（attempt0），
        附中文 label，忠实反映真实节点，不摆假 agent。
        """
        designer_label = self._DESIGNER_LABELS.get(request.type, request.type)
        return {
            "type": request.type,
            "nodes": [
                {"node": "evidence_fetcher", "agent_label": "证据检索·馆员", "kind": "evidence"},
                {
                    "node": f"{agent.AGENT_NAME}.attempt0",
                    "agent_label": designer_label,
                    "kind": "designer",
                },
                {"node": "grounding_critic", "agent_label": "引用审查·审查官", "kind": "grounding"},
                {
                    "node": "hallucination_detector",
                    "agent_label": "防幻觉核查·校对官",
                    "kind": "hallucination",
                },
                {"node": "safety_critic", "agent_label": "安全审查·安全员", "kind": "safety"},
            ],
        }

    async def _step_streaming(
        self,
        emit: Callable[[str, dict[str, Any]], Awaitable[None]],
        trace: list[GraphTraceStep],
        name: str,
        awaitable,
    ):
        """异步节点：先发 ``start``，跑既有 ``_step``，再用落库的步骤发 ``finish``。"""
        await self._emit_start(emit, name)
        try:
            result = await self._step(trace, name, awaitable)
        except Exception:
            # _step 已写 fail 步骤；发 finish(fail) 后抛给外层转 error
            await self._emit_finish(emit, trace[-1])
            raise
        await self._emit_finish(emit, trace[-1])
        return result

    async def _record_sync_streaming(
        self,
        emit: Callable[[str, dict[str, Any]], Awaitable[None]],
        trace: list[GraphTraceStep],
        name: str,
        passed: bool,
        note: str | None = None,
    ) -> None:
        """同步审查节点：先发 ``start``，跑既有 ``_record_sync``，再发 ``finish``。"""
        await self._emit_start(emit, name)
        self._record_sync(trace, name, passed, note=note)
        await self._emit_finish(emit, trace[-1])

    @staticmethod
    async def _emit_start(
        emit: Callable[[str, dict[str, Any]], Awaitable[None]], name: str
    ) -> None:
        await emit(
            "trace",
            {"node": name, "phase": "start", "status": "running", "elapsed_ms": 0, "note": None},
        )

    @staticmethod
    async def _emit_finish(
        emit: Callable[[str, dict[str, Any]], Awaitable[None]], step: GraphTraceStep
    ) -> None:
        payload = step.model_dump(mode="json")
        payload["phase"] = "finish"
        await emit("trace", payload)
