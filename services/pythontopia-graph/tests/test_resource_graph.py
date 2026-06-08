from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from pythontopia_graph.graph.resource_graph import (
    ResourceGraph,
    ResourceGraphConfig,
    ResourceGraphError,
)
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.main import app
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceBundle,
    RagHit,
    StaticEvidenceFetcher,
)
from pythontopia_graph.schemas.resource import (
    HandoutContent,
    PageRef,
    ResourceProduceRequest,
)


class StubProvider(LlmProvider):
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


@pytest.mark.asyncio
async def test_resource_graph_success_path() -> None:
    graph = ResourceGraph(
        provider=StubProvider([None]),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )
    response = await graph.run(request_of("HANDOUT"))
    assert response.status == "SUCCESS"
    assert response.evidence_page_ids == [101]
    assert response.grounding_result.passed is True
    assert response.safety_result.passed is True
    node_names = [step.node for step in response.graph_trace]
    assert any(name.startswith("evidence_fetcher") for name in node_names)
    assert any(name.startswith("ResourceDesignerAgent.handout") for name in node_names)


@pytest.mark.asyncio
async def test_resource_graph_no_evidence_marks_partial() -> None:
    graph = ResourceGraph(
        provider=StubProvider([None]),
        evidence_fetcher=StaticEvidenceFetcher([]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )
    response = await graph.run(request_of("HANDOUT"))
    assert response.status == "PARTIAL"
    assert response.grounding_result.no_evidence_found is True


@pytest.mark.asyncio
async def test_resource_graph_safety_failure_raises() -> None:
    leak_payload = {
        "title": "讲义",
        "handout": HandoutContent(
            sections=[
                {
                    "heading": "原版讲义",
                    "paragraphs": ["以下是 answer_key 完整答案。"],
                    "page_refs": [PageRef(page_id=101, page_no=12, chapter_id=3).model_dump()],
                }
            ]
        ).model_dump(),
    }
    graph = ResourceGraph(
        provider=StubProvider([leak_payload]),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )
    with pytest.raises(ResourceGraphError):
        await graph.run(request_of("HANDOUT"))


@pytest.mark.asyncio
async def test_resource_graph_grounding_failure_retries_then_fails() -> None:
    bad_payload = {
        "title": "讲义",
        "handout": HandoutContent(
            sections=[
                {
                    "heading": "section",
                    "paragraphs": ["伪造 KC PY43-KC-999"],
                    "page_refs": [PageRef(page_id=999, page_no=999, chapter_id=3).model_dump()],
                }
            ]
        ).model_dump(),
    }
    # MAX_RETRIES=2 → 3 attempts (0, 1, 2)，全部失败后抛 ResourceGraphError
    graph = ResourceGraph(
        provider=StubProvider([bad_payload, bad_payload, bad_payload]),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )
    with pytest.raises(ResourceGraphError) as exc:
        await graph.run(request_of("HANDOUT"))
    assert exc.value.response.status == "FAILED"


def test_route_returns_success_response(monkeypatch) -> None:
    app.state.llm_provider = StubProvider([None])
    app.state.evidence_fetcher = StaticEvidenceFetcher([HIT])
    app.state.valid_kc_codes = {"PY43-KC-001"}
    client = TestClient(app)
    body = {
        "user_id": 1,
        "kc_codes": ["PY43-KC-001"],
        "type": "HANDOUT",
    }
    response = client.post("/agents/resource/produce", json=body)
    assert response.status_code == 200
    assert response.json()["status"] == "SUCCESS"
    del app.state.llm_provider
    del app.state.evidence_fetcher
    del app.state.valid_kc_codes
