"""POST /agents/chapter-test/generate 端点：成功 200 + 节点失败结构化 502。

为不改共享 main.py（多代理协作收口），这里把 router 挂到本地 FastAPI 实例上单测；
生产注册行由主代理在集成段加入 main.py（见交付报告）。
"""

from __future__ import annotations

from fastapi import FastAPI
from fastapi.testclient import TestClient

from pythontopia_graph.agents.paper_golden import build_golden_paper
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import EvidenceFetcher, RagHit, StaticEvidenceFetcher
from pythontopia_graph.routes.chapter_test import router as chapter_test_router
from pythontopia_graph.schemas.chapter_test import PaperCounts

_ROUTE = "/agents/chapter-test/generate"
_BODY = {"user_id": 1, "chapter": 1, "kc_codes": ["PY43-KC-001", "PY43-KC-002"]}


class StubProvider(LlmProvider):
    def __init__(self, payloads: list[dict | None]) -> None:
        self._payloads = list(payloads)

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        payload = self._payloads.pop(0) if self._payloads else None
        return LlmResponse(content_json=payload)


def _fetcher() -> StaticEvidenceFetcher:
    return StaticEvidenceFetcher(
        [
            RagHit(
                chunk_id="c101", page_id=101, page_no=12, chapter_id=1,
                kc_codes=["PY43-KC-001"], text="第12页", score=0.8,
            ),
            RagHit(
                chunk_id="c102", page_id=102, page_no=13, chapter_id=1,
                kc_codes=["PY43-KC-002"], text="第13页", score=0.8,
            ),
        ]
    )


def _client(provider: LlmProvider, fetcher: EvidenceFetcher) -> TestClient:
    test_app = FastAPI()
    test_app.state.llm_provider = provider
    test_app.state.evidence_fetcher = fetcher
    test_app.include_router(chapter_test_router)
    return TestClient(test_app)


def test_route_success_returns_paper_and_meta() -> None:
    paper = build_golden_paper(
        chapter=1,
        kc_codes=["PY43-KC-001", "PY43-KC-002"],
        counts=PaperCounts(),
        evidence_page_ids=[101, 102],
    )
    client = _client(StubProvider([paper.model_dump(mode="json")]), _fetcher())
    resp = client.post(_ROUTE, json=_BODY)
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "SUCCESS"
    assert len(body["questions"]["items"]) == 12
    assert body["generator_meta"]["run_id"].startswith("chtest-")
    assert body["generator_meta"]["grounding"]["passed"] is True
    assert body["generator_meta"]["self_test"]["passed"] is True
    assert body["profile_snapshot"]["chapter"] == 1


def test_route_node_failure_returns_structured_502() -> None:
    client = _client(StubProvider([None]), _fetcher())  # 无 JSON → 出题失败
    resp = client.post(_ROUTE, json=_BODY)
    assert resp.status_code == 502
    detail = resp.json()["detail"]
    assert detail["code"] == "CHAPTER_TEST_GRAPH_FAILED"
    assert detail["stage"] == "paper_designer"
    assert detail["run_id"].startswith("chtest-")
    assert detail["graph_trace"]  # 轨迹非空，便于后端审计


def test_route_validation_error_returns_422() -> None:
    client = _client(StubProvider([None]), _fetcher())
    resp = client.post(_ROUTE, json={"user_id": 1, "chapter": 99, "kc_codes": []})
    assert resp.status_code == 422  # FastAPI 入参校验（chapter 越界 / kc_codes 空）
