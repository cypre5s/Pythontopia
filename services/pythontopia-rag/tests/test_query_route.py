from __future__ import annotations

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from starlette.requests import Request
from types import SimpleNamespace

from pythontopia_rag.app_state import AppState
from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.data.video_transcripts import VideoSegment
from pythontopia_rag.index.builder import EmbeddingDegradedError, rebuild_index
from pythontopia_rag.index.embedder import FallbackEmbedder, HashEmbedder
from pythontopia_rag.index.store import InMemoryVectorStore
from pythontopia_rag.index.video_transcripts import VideoTranscriptIndex
from pythontopia_rag.routes import query as query_module
from pythontopia_rag.main import app


class _SemanticEmbedder:
    name = "semantic-test"
    dim = 8

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [[1.0] + [0.0] * (self.dim - 1) for _ in texts]


@pytest.fixture(autouse=True)
def _reset_state(monkeypatch: pytest.MonkeyPatch) -> AppState:
    state = AppState(embedder=HashEmbedder(), store=InMemoryVectorStore())
    app.state.rag = state
    monkeypatch.setattr(
        query_module,
        "fetch_disabled_course_resources",
        lambda _db_url: SimpleNamespace(page_ids=frozenset(), kc_codes=frozenset()),
        raising=False,
    )
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
            chapter_no=3,
        ),
        IndexablePage(
            page_id=22,
            page_no=20,
            document_id=2,
            chapter_id=4,
            kc_codes=["PY43-KC-020"],
            title="for 循环",
            text=("for 循环用于遍历序列，例如 for i in range(10)。" * 8),
            chapter_no=4,
        ),
        IndexablePage(
            page_id=33,
            page_no=27,
            document_id=2,
            chapter_id=5,
            kc_codes=["PY43-KC-030"],
            title="列表",
            text=("列表 list 是可变序列，可使用 append 和 pop。" * 8),
            chapter_no=5,
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
    app.state.rag_page_source = _seed_pages()
    await rebuild_index(source=app.state.rag_page_source, embedder=state.embedder, store=state.store)

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
async def test_query_does_not_return_course_pages_disabled_by_admin(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    state = app.state.rag
    app.state.rag_page_source = _seed_pages()
    await rebuild_index(source=app.state.rag_page_source, embedder=state.embedder, store=state.store)
    monkeypatch.setattr(
        query_module,
        "fetch_disabled_course_resources",
        lambda _db_url: SimpleNamespace(page_ids=frozenset({11}), kc_codes=frozenset()),
        raising=False,
    )

    response = TestClient(app).post(
        "/rag/query",
        json={
            "query": "input函数",
            "kc_codes": ["PY43-KC-012"],
            "top_k": 5,
            "min_score": 0.1,
        },
    )

    body = response.json()
    assert body["no_evidence_found"] is True
    assert body["hits"] == []
    assert body["citations"] == []


@pytest.mark.asyncio
async def test_query_does_not_return_disabled_knowledge_point(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    state = app.state.rag
    app.state.rag_page_source = _seed_pages()
    await rebuild_index(source=app.state.rag_page_source, embedder=state.embedder, store=state.store)
    monkeypatch.setattr(
        query_module,
        "fetch_disabled_course_resources",
        lambda _db_url: SimpleNamespace(page_ids=frozenset(), kc_codes=frozenset({"PY43-KC-012"})),
        raising=False,
    )

    response = TestClient(app).post(
        "/rag/query",
        json={
            "query": "input函数",
            "kc_codes": ["PY43-KC-012"],
            "top_k": 5,
            "min_score": 0.1,
        },
    )

    body = response.json()
    assert body["no_evidence_found"] is True
    assert body["hits"] == []
    assert body["citations"] == []


@pytest.mark.asyncio
async def test_query_fails_closed_when_resource_availability_cannot_be_read(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    state = app.state.rag
    await rebuild_index(source=_seed_pages(), embedder=state.embedder, store=state.store)

    def unavailable(_db_url: str) -> object:
        raise RuntimeError("database unavailable")

    monkeypatch.setattr(
        query_module, "fetch_disabled_course_resources", unavailable, raising=False
    )
    response = TestClient(app, raise_server_exceptions=False).post(
        "/rag/query", json={"query": "input函数", "top_k": 5, "min_score": 0.1}
    )

    assert response.status_code == 503


@pytest.mark.asyncio
async def test_video_match_expands_to_courseware_pages_without_transcript_citations() -> None:
    state = app.state.rag
    app.state.rag_page_source = _seed_pages()
    await rebuild_index(
        source=app.state.rag_page_source,
        embedder=state.embedder,
        store=state.store,
        bm25=state.bm25,
    )
    video_index = VideoTranscriptIndex()
    video_index.build(
        [
            VideoSegment(
                video_id=7,
                chapter_no=5,
                title="列表操作",
                start_sec=42.0,
                end_sec=48.0,
                text="列表末尾追加元素可以使用 append 方法",
                avg_log_prob=-0.1,
                no_speech_prob=0.0,
                review_status="machine_generated_unreviewed",
            )
        ]
    )
    client = TestClient(app)
    baseline = client.post(
        "/rag/query",
        json={"query": "末尾追加新成员", "top_k": 5, "min_score": 0.99},
    )
    assert baseline.json()["no_evidence_found"] is True
    state.video_index = video_index

    response = client.post(
        "/rag/query",
        json={"query": "末尾追加新成员", "top_k": 5, "min_score": 0.99},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["no_evidence_found"] is False
    assert [citation["page_id"] for citation in body["citations"]] == [33]
    assert body["citations"][0]["source"] == "courseware"
    assert body["video_trace"]["matches"][0]["video_id"] == 7
    assert body["video_trace"]["matches"][0]["start_sec"] == 42.0
    assert body["video_trace"]["supplemental_page_ids"] == [33]
    assert "列表末尾追加元素可以使用" not in body["citations"][0]["snippet"]
    assert client.get("/rag/index/status").json()["video_segments"] == 1


@pytest.mark.asyncio
async def test_video_expansion_obeys_chapter_and_kc_filters() -> None:
    state = app.state.rag
    app.state.rag_page_source = _seed_pages()
    await rebuild_index(
        source=app.state.rag_page_source,
        embedder=state.embedder,
        store=state.store,
        bm25=state.bm25,
    )
    video_index = VideoTranscriptIndex()
    video_index.build(
        [
            VideoSegment(
                video_id=7,
                chapter_no=5,
                title="列表操作",
                start_sec=42.0,
                end_sec=48.0,
                text="列表末尾追加元素可以使用 append 方法",
                avg_log_prob=-0.1,
                no_speech_prob=0.0,
                review_status="machine_generated_unreviewed",
            )
        ]
    )
    state.video_index = video_index

    chapter_response = TestClient(app).post(
        "/rag/query",
        json={
            "query": "末尾追加新成员",
            "chapter_ids": [4],
            "top_k": 5,
            "min_score": 0.99,
        },
    )
    kc_response = TestClient(app).post(
        "/rag/query",
        json={
            "query": "末尾追加新成员",
            "kc_codes": ["PY43-KC-040"],
            "top_k": 5,
            "min_score": 0.99,
        },
    )

    assert chapter_response.json()["hits"] == []
    assert chapter_response.json()["video_trace"]["matches"] == []
    assert kc_response.json()["hits"] == []
    assert kc_response.json()["video_trace"]["matches"] == []


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
        name="test-primary+hash-fallback",
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


def test_rebuild_source_does_not_log_database_exception_text(monkeypatch, caplog) -> None:
    sentinel = "postgresql://reader:database-secret-sentinel@db.internal/course"

    def fail_fetch(_db_url: str):
        raise RuntimeError(sentinel)

    monkeypatch.setattr(query_module, "fetch_pages", fail_fetch)
    monkeypatch.setenv("PYT_DB_URL", sentinel)
    app.state.rag_page_source = _seed_pages()
    request = Request({"type": "http", "app": app})

    source = query_module._resolve_rebuild_source(request)

    assert source is app.state.rag_page_source
    assert "RuntimeError" in caplog.text
    assert sentinel not in caplog.text


@pytest.mark.asyncio
async def test_rebuild_failure_does_not_log_or_return_exception_details(
    monkeypatch, caplog
) -> None:
    sentinel = "embedding-secret-sentinel"

    async def fail_rebuild(**_kwargs):
        raise EmbeddingDegradedError(sentinel)

    monkeypatch.setattr(query_module, "_require_admin_token", lambda _token: None)
    monkeypatch.setattr(query_module, "_resolve_rebuild_source", lambda _request: _seed_pages())
    monkeypatch.setattr(query_module, "rebuild_index", fail_rebuild)
    request = Request({"type": "http", "app": app})

    with pytest.raises(HTTPException) as raised:
        await query_module.rag_rebuild(request, x_internal_token="test-token")

    assert "EmbeddingDegradedError" in caplog.text
    assert sentinel not in caplog.text
    assert sentinel not in str(raised.value.detail)


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
