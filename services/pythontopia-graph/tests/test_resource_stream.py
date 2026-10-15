from __future__ import annotations

import pytest

from pythontopia_graph.graph.resource_graph import ResourceGraph, ResourceGraphConfig
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher
from pythontopia_graph.schemas.resource import ResourceProduceRequest

VALID_FINISH_STATUSES = {"ok", "retry", "fail"}


class StubProvider(LlmProvider):
    """按序返回预设 payload；None 时走确定性 fallback。"""

    def __init__(self, payloads: list[dict | None]) -> None:
        self._payloads = list(payloads)

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        payload = self._payloads.pop(0) if self._payloads else None
        return LlmResponse(content_json=payload)


def request_of(type_: str = "HANDOUT") -> ResourceProduceRequest:
    return ResourceProduceRequest(user_id=1, kc_codes=["PY43-KC-001"], type=type_)  # type: ignore[arg-type]


HIT = RagHit(
    chunk_id="c1",
    page_id=101,
    page_no=12,
    chapter_id=3,
    kc_codes=["PY43-KC-001"],
    text="第 12 页讲解 input 函数。",
    score=0.81,
)


def _graph() -> ResourceGraph:
    return ResourceGraph(
        provider=StubProvider([None]),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )


async def _collect(
    graph: ResourceGraph, request: ResourceProduceRequest
) -> list[tuple[str, dict]]:
    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    await graph.run_streaming(request, emit)
    return events


@pytest.mark.asyncio
async def test_stream_first_event_is_plan_with_six_nodes() -> None:
    events = await _collect(_graph(), request_of("HANDOUT"))

    assert events, "expected at least one streamed event"
    first_event, first_data = events[0]
    assert first_event == "plan"
    assert first_data["type"] == "HANDOUT"

    nodes = first_data["nodes"]
    assert len(nodes) == 6
    assert [n["kind"] for n in nodes] == [
        "evidence",
        "designer",
        "grounding",
        "hallucination",
        "grounding",
        "safety",
    ]
    designer = next(n for n in nodes if n["kind"] == "designer")
    assert designer["agent_label"] == "讲义设计师"
    assert designer["node"] == "ResourceDesignerAgent.handout.attempt0"


@pytest.mark.asyncio
async def test_stream_each_plan_node_emits_start_then_finish() -> None:
    events = await _collect(_graph(), request_of("HANDOUT"))
    plan = events[0][1]
    traces = [data for (event, data) in events if event == "trace"]

    for node in plan["nodes"]:
        name = node["node"]
        phases = [t["phase"] for t in traces if t["node"] == name]
        assert "start" in phases, f"{name} missing start phase"
        assert "finish" in phases, f"{name} missing finish phase"
        assert phases.index("start") < phases.index("finish")


@pytest.mark.asyncio
async def test_stream_phase_payloads_are_well_formed_and_result_terminal() -> None:
    events = await _collect(_graph(), request_of("HANDOUT"))

    starts = [d for (e, d) in events if e == "trace" and d.get("phase") == "start"]
    finishes = [d for (e, d) in events if e == "trace" and d.get("phase") == "finish"]
    assert starts and finishes

    for start in starts:
        assert start["status"] == "running"
        assert start["elapsed_ms"] == 0
    for finish in finishes:
        assert finish["status"] in VALID_FINISH_STATUSES

    # HANDOUT + 静态证据在 attempt0 即成功，流以 result 终止。
    assert events[-1][0] == "result"


@pytest.mark.asyncio
async def test_stream_storyboard_designer_node_is_real_agent() -> None:
    events = await _collect(_graph(), request_of("STORYBOARD"))

    assert events[0][0] == "plan"
    nodes = events[0][1]["nodes"]
    designer = next(n for n in nodes if n["kind"] == "designer")
    assert designer["node"] == "StoryboardAgent.attempt0"
    assert designer["agent_label"] == "动画分镜师"

    designer_phases = [
        d["phase"]
        for (e, d) in events
        if e == "trace" and d["node"] == "StoryboardAgent.attempt0"
    ]
    assert "start" in designer_phases
    assert "finish" in designer_phases


@pytest.mark.asyncio
async def test_stream_semantic_grounding_failure_retries_and_ends_error() -> None:
    bad_payload = {
        "title": "讲义",
        "summary": "包含未经证据支持的强断言",
        "sections": [{
            "heading": "section",
            "paragraphs": ["根据第 999 页可知 input 函数总是运行 3 秒。"],
            "code_snippets": [],
            "evidence_refs": [],
        }],
    }
    graph = ResourceGraph(
        provider=StubProvider([bad_payload, bad_payload, bad_payload]),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )

    events = await _collect(graph, request_of("HANDOUT"))

    semantic_finishes = [
        data
        for event, data in events
        if event == "trace"
        and data["node"] == "semantic_grounding"
        and data["phase"] == "finish"
    ]
    assert semantic_finishes
    assert all(data["status"] == "retry" for data in semantic_finishes)
    assert events[-1][0] == "error"
    assert events[-1][1]["code"] == "RESOURCE_GRAPH_FAILED"


@pytest.mark.asyncio
async def test_stream_unexpected_failure_does_not_return_exception_text(monkeypatch) -> None:
    graph = _graph()

    async def fail_fetch(request):
        raise RuntimeError("resource-content-secret-sentinel")

    monkeypatch.setattr(graph, "_fetch_evidence", fail_fetch)
    events = await _collect(graph, request_of("HANDOUT"))

    assert events[-1][0] == "error"
    assert events[-1][1] == {
        "code": "RESOURCE_GRAPH_FAILED",
        "message": "Resource generation failed. Please retry.",
    }
    assert "resource-content-secret-sentinel" not in str(events)
