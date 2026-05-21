from __future__ import annotations

import time
from dataclasses import dataclass

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
    MAX_RETRIES = 1

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
        while attempt <= self.MAX_RETRIES:
            draft = await self._step(
                trace,
                f"{agent.AGENT_NAME}.attempt{attempt}",
                agent.design(request, evidence),
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
