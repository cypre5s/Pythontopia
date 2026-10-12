from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from pythontopia_rag.app_state import AppState
from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.embedder import FallbackEmbedder, HashEmbedder
from pythontopia_rag.index.store import InMemoryVectorStore
from pythontopia_rag.main import app


class _SemanticEmbedder:
    name = "semantic-test"
    dim = 8

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [[1.0] + [0.0] * (self.dim - 1) for _ in texts]


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
    assert body["route_reason"]
    assert body["citations"]
    assert body["citations"][0]["page_id"] == 11
    assert body["citations"][0]["source"] == "courseware"
    assert body["citations"][0]["snippet"]


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("query", "expected_page_id", "expected_page_no", "expected_kc_codes"),
    [
        ("input函数", 11, 12, ["PY43-KC-012"]),
        ("for循环", 22, 20, ["PY43-KC-020"]),
        ("列表", 33, 27, ["PY43-KC-030"]),
    ],
)
async def test_required_course_queries_return_evidence_paths(
    query: str,
    expected_page_id: int,
    expected_page_no: int,
    expected_kc_codes: list[str],
) -> None:
    state = app.state.rag
    await rebuild_index(source=_seed_pages(), embedder=state.embedder, store=state.store)

    client = TestClient(app)
    response = client.post(
        "/rag/query", json={"query": query, "top_k": 5, "min_score": 0.1}
    )

    assert response.status_code == 200
    body = response.json()
    assert body["no_evidence_found"] is False
    assert body["hits"]
    top = body["hits"][0]
    assert top["page_id"] == expected_page_id
    assert top["page_no"] == expected_page_no
    assert top["kc_codes"] == expected_kc_codes


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
    assert body["citations"] == []
    assert body["route_reason"]


@pytest.mark.asyncio
async def test_query_no_evidence_when_forbidden_keyword() -> None:
    client = TestClient(app)
    response = client.post("/rag/query", json={"query": "色情内容", "top_k": 3})
    body = response.json()
    assert body["no_evidence_found"] is True
    assert body["hits"] == []
    assert body["citations"] == []


def test_status_returns_default_when_empty() -> None:
    client = TestClient(app)
    response = client.get("/rag/index/status")
    assert response.status_code == 200
    body = response.json()
    assert body["built_chunks"] == 0
    assert body["embed_model"] == HashEmbedder.name


@pytest.mark.asyncio
async def test_query_uses_hybrid_mode_when_bm25_built() -> None:
    state = app.state.rag
    await rebuild_index(
        source=_seed_pages(), embedder=state.embedder, store=state.store, bm25=state.bm25
    )

    client = TestClient(app)
    response = client.post(
        "/rag/query", json={"query": "input 函数", "top_k": 5, "min_score": 0.1}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["retrieval_mode"] == "hybrid"
    assert "hybrid" in body["route_reason"]
    assert body["no_evidence_found"] is False
    assert body["hits"][0]["page_id"] == 11
    for hit in body["hits"]:
        assert 0.0 < hit["score"] <= 1.0


@pytest.mark.asyncio
async def test_query_falls_back_to_vector_only_without_bm25_index() -> None:
    state = app.state.rag
    # 只建向量索引，不建 BM25：模拟 BM25 索引缺失时的向后安全回退
    await rebuild_index(source=_seed_pages(), embedder=state.embedder, store=state.store)

    client = TestClient(app)
    response = client.post(
        "/rag/query", json={"query": "input 函数", "top_k": 5, "min_score": 0.1}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["retrieval_mode"] == "vector_only"
    assert "vector_only" in body["route_reason"]
    assert body["no_evidence_found"] is False
    assert body["hits"][0]["page_id"] == 11


@pytest.mark.asyncio
async def test_hits_and_citations_carry_document_id_and_native_scores() -> None:
    """page_no 跨文档重复 → 引用键必须能定位到文档；同时暴露各路原生相关度。"""
    state = app.state.rag
    await rebuild_index(
        source=_seed_pages(), embedder=state.embedder, store=state.store, bm25=state.bm25
    )

    client = TestClient(app)
    response = client.post(
        "/rag/query", json={"query": "input 函数", "top_k": 5, "min_score": 0.1}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["hits"]
    for hit in body["hits"]:
        assert hit["document_id"] == 2
        assert hit["vector_score"] is not None or hit["bm25_score"] is not None
    for citation in body["citations"]:
        assert citation["document_id"] == 2


@pytest.mark.asyncio
async def test_index_status_exposes_embedding_degradation() -> None:
    """禁止静默降级：兜底 embedder 生效时 /rag/index/status 必须报 degraded。"""
    state = app.state.rag
    dim = HashEmbedder().dim
    state.embedder = FallbackEmbedder(
        primary=None,
        fallback=HashEmbedder(dim=dim),
        dim=dim,
        name="zhipu-embedding-3+hash-fallback",
        fallback_reason="ValueError: missing EMBEDDING_API_KEY",
    )
    await rebuild_index(
        source=_seed_pages(), embedder=state.embedder, store=state.store, bm25=state.bm25
    )

    client = TestClient(app)
    body = client.get("/rag/index/status").json()
    assert body["degraded"] is True
    assert "EMBEDDING_API_KEY" in body["fallback_reason"]


@pytest.mark.asyncio
async def test_index_status_reports_healthy_embedder_as_not_degraded() -> None:
    state = app.state.rag
    state.embedder = _SemanticEmbedder()
    await rebuild_index(
        source=_seed_pages(), embedder=state.embedder, store=state.store, bm25=state.bm25
    )
    client = TestClient(app)
    body = client.get("/rag/index/status").json()
    assert body["degraded"] is False
    assert body["fallback_reason"] is None


@pytest.mark.asyncio
async def test_query_text_never_leaks_context_prefix() -> None:
    state = app.state.rag
    await rebuild_index(
        source=_seed_pages(), embedder=state.embedder, store=state.store, bm25=state.bm25
    )

    client = TestClient(app)
    response = client.post(
        "/rag/query", json={"query": "for循环", "top_k": 8, "min_score": 0.0}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["hits"]
    for hit in body["hits"]:
        assert "《Python语言基础》" not in hit["text"]
        assert "第20页：" not in hit["text"]
