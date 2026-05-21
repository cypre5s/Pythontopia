from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from pythontopia_rag.app_state import AppState
from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.store import InMemoryVectorStore
from pythontopia_rag.main import app


@pytest.fixture(autouse=True)
def _reset_state() -> AppState:
    state = AppState(embedder=HashEmbedder(), store=InMemoryVectorStore())
    app.state.rag = state
    return state


def _seed_pages() -> InMemoryPageSource:
    pages = [
        IndexablePage(
            page_id=11,
            page_no=12,
            document_id=2,
            chapter_id=3,
            kc_codes=["PY43-KC-012"],
            title="input 函数",
            text=("input 函数 input() 用于从控制台读取一行字符串。" * 8),
        ),
        IndexablePage(
            page_id=22,
            page_no=20,
            document_id=2,
            chapter_id=4,
            kc_codes=["PY43-KC-020"],
            title="for 循环",
            text=("for 循环用于遍历序列，例如 for i in range(10)。" * 8),
        ),
        IndexablePage(
            page_id=33,
            page_no=27,
            document_id=2,
            chapter_id=5,
            kc_codes=["PY43-KC-030"],
            title="列表",
            text=("列表 list 是可变序列，可使用 append 和 pop。" * 8),
        ),
        IndexablePage(
            page_id=44,
            page_no=33,
            document_id=2,
            chapter_id=6,
            kc_codes=["PY43-KC-040"],
            title="字典",
            text=("字典 dict 用键值对存储，常用 get 与 setdefault。" * 8),
        ),
        IndexablePage(
            page_id=55,
            page_no=40,
            document_id=2,
            chapter_id=7,
            kc_codes=["PY43-KC-050"],
            title="函数定义",
            text=("函数使用 def 定义，参数可以有默认值与关键字参数。" * 8),
        ),
    ]
    return InMemoryPageSource(pages=pages)


@pytest.mark.asyncio
async def test_query_returns_input_evidence() -> None:
    state = app.state.rag
    await rebuild_index(source=_seed_pages(), embedder=state.embedder, store=state.store)

    client = TestClient(app)
    response = client.post(
        "/rag/query", json={"query": "input 函数", "top_k": 5, "min_score": 0.1}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["no_evidence_found"] is False
    assert body["hits"]
    top = body["hits"][0]
    assert top["page_id"] == 11
    assert top["kc_codes"] == ["PY43-KC-012"]


@pytest.mark.asyncio
async def test_query_supports_kc_filter() -> None:
    state = app.state.rag
    await rebuild_index(source=_seed_pages(), embedder=state.embedder, store=state.store)

    client = TestClient(app)
    response = client.post(
        "/rag/query",
        json={
            "query": "for 循环用于遍历序列",
            "top_k": 5,
            "kc_codes": ["PY43-KC-020"],
            "min_score": 0.05,
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["no_evidence_found"] is False
    for hit in body["hits"]:
        assert hit["kc_codes"] == ["PY43-KC-020"]


@pytest.mark.asyncio
async def test_query_no_evidence_when_nothing_matches() -> None:
    state = app.state.rag
    await rebuild_index(source=_seed_pages(), embedder=state.embedder, store=state.store)

    client = TestClient(app)
    response = client.post(
        "/rag/query",
        json={"query": "奥利给奥利给", "top_k": 3, "min_score": 0.95},
    )
    body = response.json()
    assert body["no_evidence_found"] is True
    assert body["hits"] == []


@pytest.mark.asyncio
async def test_query_no_evidence_when_forbidden_keyword() -> None:
    client = TestClient(app)
    response = client.post("/rag/query", json={"query": "色情内容", "top_k": 3})
    body = response.json()
    assert body["no_evidence_found"] is True
    assert body["hits"] == []


def test_status_returns_default_when_empty() -> None:
    client = TestClient(app)
    response = client.get("/rag/index/status")
    assert response.status_code == 200
    body = response.json()
    assert body["built_chunks"] == 0
    assert body["embed_model"] == HashEmbedder.name
