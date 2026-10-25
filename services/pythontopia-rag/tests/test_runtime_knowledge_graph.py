from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from pythontopia_rag.app_state import AppState
from pythontopia_rag.data.loader import DisabledCourseResources
from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.graph.kc_graph import RELATION_CO_OCCUR
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.kc_graph import KcGraph as IndexKcGraph
from pythontopia_rag.index.store import InMemoryVectorStore
from pythontopia_rag.main import app
from pythontopia_rag.routes import query as query_route


ANCHOR_KC = "PY43-KC-041"
RELATED_KC = "PY43-KC-042"


@pytest.fixture(autouse=True)
def _course_resources_enabled(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(
        query_route, "fetch_disabled_course_resources", lambda _db_url: DisabledCourseResources()
    )


def _pages() -> InMemoryPageSource:
    return InMemoryPageSource(
        pages=[
            IndexablePage(
                page_id=4101,
                page_no=61,
                document_id=5,
                chapter_id=5,
                kc_codes=[ANCHOR_KC],
                title="字典应用",
                text="字典可以用于统计词频，解决键值映射类问题。" * 8,
                chapter_no=5,
                chapter_title="第五章：数据结构",
            ),
            IndexablePage(
                page_id=4201,
                page_no=62,
                document_id=5,
                chapter_id=5,
                kc_codes=[RELATED_KC],
                title="字典基本操作",
                text="字典 get、items、keys、values 是完成词频统计前需要掌握的基本操作。" * 8,
                chapter_no=5,
                chapter_title="第五章：数据结构",
            ),
            IndexablePage(
                page_id=4301,
                page_no=63,
                document_id=5,
                chapter_id=5,
                kc_codes=[ANCHOR_KC, RELATED_KC],
                title="字典词频统计综合页",
                text="词频统计同时使用字典应用和字典基本操作。" * 8,
                chapter_no=5,
                chapter_title="第五章：数据结构",
            ),
        ]
    )


async def test_rag_query_uses_runtime_co_occurrence_graph() -> None:
    state = AppState(embedder=HashEmbedder(), store=InMemoryVectorStore())
    state.kc_graph = IndexKcGraph(prerequisites={ANCHOR_KC: [], RELATED_KC: []})
    app.state.rag = state

    await rebuild_index(
        source=_pages(),
        embedder=state.embedder,
        store=state.store,
        bm25=state.bm25,
    )

    client = TestClient(app)
    response = client.post(
        "/rag/query",
        json={
            "query": "为什么字典词频统计要先理解 get items keys values",
            "top_k": 6,
            "min_score": 0.0,
            "kc_codes": [ANCHOR_KC],
            "graph_mode": "force",
            "prereq_hops": 1,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["route"] == "graph"
    assert body["retrieval_mode"] == "graph"
    assert {
        "from_kc": ANCHOR_KC,
        "to_kc": RELATED_KC,
        "relation": RELATION_CO_OCCUR,
    } in body["subgraph"]
    related_hits = [hit for hit in body["hits"] if RELATED_KC in hit["kc_codes"]]
    assert related_hits
    assert any(hit.get("via_relation") == RELATION_CO_OCCUR for hit in related_hits)
    assert all(hit.get("via_prerequisite") is False for hit in related_hits)


async def test_rag_query_trace_quantifies_runtime_co_occurrence_graph() -> None:
    state = AppState(embedder=HashEmbedder(), store=InMemoryVectorStore())
    state.kc_graph = IndexKcGraph(prerequisites={ANCHOR_KC: [], RELATED_KC: []})
    app.state.rag = state

    await rebuild_index(
        source=_pages(),
        embedder=state.embedder,
        store=state.store,
        bm25=state.bm25,
    )

    client = TestClient(app)
    response = client.post(
        "/rag/query",
        json={
            "query": "为什么字典词频统计要先理解 get items keys values",
            "top_k": 6,
            "min_score": 0.0,
            "kc_codes": [ANCHOR_KC],
            "graph_mode": "force",
            "prereq_hops": 1,
        },
    )

    assert response.status_code == 200
    trace = response.json()["graph_trace"]
    assert trace["route"] == "graph"
    assert trace["retrieval_mode"] == "graph"
    assert trace["top_k"] == 6
    assert trace["hit_count"] == len(response.json()["hits"])
    assert trace["co_occur_hit_count"] >= 1
    assert trace["prerequisite_hit_count"] == 0
    assert trace["supplemental_hit_count"] >= 1
    assert trace["supplemental_hit_limit"] == 2
    assert trace["supplemental_hit_ratio"] <= 1 / 3
    assert trace["relation_counts"][RELATION_CO_OCCUR] == 1
    assert trace["noise_guard"] == "supplemental_hits_capped_at_top_k_div_3"


async def test_co_occurrence_trace_reports_noise_guard_cap() -> None:
    related_codes = [f"PY43-KC-04{i}" for i in range(2, 8)]
    pages = [
        IndexablePage(
            page_id=5000,
            page_no=80,
            document_id=5,
            chapter_id=5,
            kc_codes=[ANCHOR_KC],
            title="字典应用主线",
            text="字典应用 词频统计 主线任务。" * 6,
            chapter_no=5,
            chapter_title="第五章：数据结构",
        )
    ]
    for offset, code in enumerate(related_codes, start=1):
        pages.append(
            IndexablePage(
                page_id=5100 + offset,
                page_no=80 + offset,
                document_id=5,
                chapter_id=5,
                kc_codes=[ANCHOR_KC, code],
                title=f"字典综合桥接页{offset}",
                text="字典综合应用会同时关联多个操作知识点。" * 2,
                chapter_no=5,
                chapter_title="第五章：数据结构",
            )
        )
        pages.append(
            IndexablePage(
                page_id=5200 + offset,
                page_no=90 + offset,
                document_id=5,
                chapter_id=5,
                kc_codes=[code],
                title=f"字典操作补充页{offset}",
                text=f"get items keys values 词频统计 字典操作 需要先掌握 第{offset}页。" * 8,
                chapter_no=5,
                chapter_title="第五章：数据结构",
            )
        )

    state = AppState(embedder=HashEmbedder(), store=InMemoryVectorStore())
    state.kc_graph = IndexKcGraph(
        prerequisites={ANCHOR_KC: [], **{code: [] for code in related_codes}}
    )
    app.state.rag = state

    await rebuild_index(
        source=InMemoryPageSource(pages=pages),
        embedder=state.embedder,
        store=state.store,
        bm25=state.bm25,
    )

    client = TestClient(app)
    response = client.post(
        "/rag/query",
        json={
            "query": "为什么字典词频统计要先理解 get items keys values",
            "top_k": 6,
            "min_score": 0.0,
            "kc_codes": [ANCHOR_KC],
            "graph_mode": "force",
            "prereq_hops": 1,
        },
    )

    assert response.status_code == 200
    trace = response.json()["graph_trace"]
    assert trace["route"] == "graph"
    assert trace["expanded_kc_count"] == len(related_codes)
    assert trace["relation_counts"][RELATION_CO_OCCUR] == len(related_codes)
    assert trace["supplemental_hit_limit"] == 2
    assert 1 <= trace["co_occur_hit_count"] <= 2
    assert trace["supplemental_hit_count"] <= 2
    assert trace["supplemental_hit_ratio"] <= 1 / 3
