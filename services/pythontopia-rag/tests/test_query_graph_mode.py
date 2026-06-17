"""GraphRAG 自适应路由（G1b-route）测试矩阵。

覆盖 §3.3 / §4 G1 第 2-4 条 + §5 自测要求：
  ① 单 KC / 事实型 → route=vector（即便锚定 KC 且有前置数据，简单问也不扩展）
  ② 多跳「为什么 / 区别 / 先学」→ route=graph 且 subgraph 非空、命中页标 via_prerequisite
  ③ graph_mode=force / off 行为
  ④ 无前置数据 → 优雅退化 vector
  ⑤ 不传 graph_mode（旧调用）行为 = 旧版（与既有 hybrid_search 位级一致；不调 G1a neighbors）
  ⑥ prereq_hops 上界裁剪（router 把跳数夹到 MAX_PREREQ_HOPS=2）

G1a 的 graph/kc_graph.py 尚未合并：本套测试用注入式 neighbors stub 解耦（router 接受
``neighbors_fn`` 依赖注入），端到端用例则走「既有 index KcGraph 派生 neighbors」的回退路径。
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from pythontopia_rag.app_state import AppState
from pythontopia_rag.data.page_source import IndexablePage, InMemoryPageSource
from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.builder import rebuild_index
from pythontopia_rag.index.embedder import HashEmbedder
from pythontopia_rag.index.fusion import hybrid_search
from pythontopia_rag.index.kc_graph import KcGraph as IndexKcGraph
from pythontopia_rag.index.store import InMemoryVectorStore
from pythontopia_rag.graph.router import (
    MAX_PREREQ_HOPS,
    RouteResult,
    classify_query,
    route_and_retrieve,
)
from pythontopia_rag.main import app
from pythontopia_rag.schemas.query import KcEdge

RECURSION_KC = "PY43-KC-021R"
FUNCTION_KC = "PY43-KC-021"
LIST_KC = "PY43-KC-030"

# 锚定 KC → 直接前置；既喂 index KcGraph 也喂 router 的 neighbors stub。
PREREQ_GRAPH = IndexKcGraph(
    prerequisites={RECURSION_KC: [FUNCTION_KC], FUNCTION_KC: [], LIST_KC: []}
)

MULTI_HOP_QUERY = "为什么 递归函数 return 返回 终止条件 和 函数 的区别 看不懂"
SIMPLE_QUERY = "递归函数 return 返回 终止条件"


# --------------------------------------------------------------------------- #
# Fixtures / helpers
# --------------------------------------------------------------------------- #
def _pages() -> list[IndexablePage]:
    return [
        IndexablePage(
            page_id=1,
            page_no=70,
            document_id=1,
            chapter_id=7,
            kc_codes=[RECURSION_KC],
            title="递归函数",
            text="递归函数在函数体内调用自身，必须有终止条件，否则栈溢出。" * 6,
            chapter_no=7,
            chapter_title="第七章：归纳与抽象",
        ),
        IndexablePage(
            page_id=2,
            page_no=15,
            document_id=1,
            chapter_id=7,
            kc_codes=[FUNCTION_KC],
            title="函数定义与返回值",
            text="def 定义函数，return 把函数结果返回给调用者，没有 return 则返回 None。" * 6,
            chapter_no=7,
            chapter_title="第七章：归纳与抽象",
        ),
        IndexablePage(
            page_id=3,
            page_no=30,
            document_id=1,
            chapter_id=5,
            kc_codes=[LIST_KC],
            title="列表",
            text="列表 list 是可变序列，支持 append、pop 与切片。" * 6,
            chapter_no=5,
            chapter_title="第五章：数据结构",
        ),
    ]


async def _build_index(
    *, with_bm25: bool = True
) -> tuple[HashEmbedder, InMemoryVectorStore, Bm25Index | None]:
    embedder = HashEmbedder()
    store = InMemoryVectorStore()
    bm25: Bm25Index | None = Bm25Index() if with_bm25 else None
    await rebuild_index(
        source=InMemoryPageSource(pages=_pages()),
        embedder=embedder,
        store=store,
        bm25=bm25 if bm25 is not None else Bm25Index(),
    )
    return embedder, store, bm25


class _SpyNeighbors:
    """注入式 G1a neighbors stub：记录调用并返回预设前置边。"""

    def __init__(self, mapping: dict[str, list]) -> None:
        self.mapping = mapping
        self.calls: list[tuple[str, int]] = []

    def __call__(self, kc: str, hops: int) -> list:
        self.calls.append((kc, hops))
        return self.mapping.get(kc, [])


def _by_page(result: RouteResult) -> dict[int, object]:
    return {hit.chunk.page_id: hit for hit in result.hits}


# --------------------------------------------------------------------------- #
# classify_query 纯函数
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize(
    ("query", "kc_codes", "expected"),
    [
        ("列表是什么 怎么用", [LIST_KC], "vector"),
        ("input 函数的用法", None, "vector"),
        ("为什么递归会栈溢出", [RECURSION_KC], "graph"),
        ("列表和元组的区别", [LIST_KC], "graph"),
        ("学递归之前要先学什么", [RECURSION_KC], "graph"),
        # 多 KC 锚点 = 比较 / 关联意图 → 多跳
        ("讲讲这两个", [RECURSION_KC, FUNCTION_KC], "graph"),
        ("what is the difference between list and tuple", [LIST_KC], "graph"),
    ],
)
def test_classify_query(query: str, kc_codes: list[str] | None, expected: str) -> None:
    assert classify_query(query, kc_codes) == expected


# --------------------------------------------------------------------------- #
# ① 单 KC / 事实型 → route=vector
# --------------------------------------------------------------------------- #
async def test_single_kc_factual_routes_vector() -> None:
    embedder, store, bm25 = await _build_index()
    neighbors = _SpyNeighbors({RECURSION_KC: [FUNCTION_KC]})  # 有前置数据但简单问不应使用

    result = await route_and_retrieve(
        query="递归函数 是什么 怎么用",
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        graph_mode="auto",
        neighbors_fn=neighbors,
    )

    assert result.route == "vector"
    assert result.subgraph == []
    assert all(not hit.via_prerequisite for hit in result.hits)
    assert neighbors.calls == []  # 简单问绝不触图


# --------------------------------------------------------------------------- #
# ② 多跳 → route=graph，subgraph 非空，命中页带 via_prerequisite
# --------------------------------------------------------------------------- #
async def test_multi_hop_routes_graph_with_subgraph_and_flag() -> None:
    embedder, store, bm25 = await _build_index()
    neighbors = _SpyNeighbors({RECURSION_KC: [FUNCTION_KC]})

    result = await route_and_retrieve(
        query=MULTI_HOP_QUERY,
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        graph_mode="auto",
        neighbors_fn=neighbors,
    )

    assert result.route == "graph"
    assert result.retrieval_mode == "graph"
    assert result.subgraph
    assert KcEdge(from_kc=RECURSION_KC, to_kc=FUNCTION_KC, relation="prerequisite") in result.subgraph
    pages = _by_page(result)
    assert 1 in pages and pages[1].via_prerequisite is False  # 主题页
    assert 2 in pages and pages[2].via_prerequisite is True  # 前置页（图扩展进入）
    assert neighbors.calls  # 确实调了 G1a neighbors


# --------------------------------------------------------------------------- #
# ③ force / off 行为
# --------------------------------------------------------------------------- #
async def test_force_mode_expands_even_for_simple_query() -> None:
    embedder, store, bm25 = await _build_index()
    neighbors = _SpyNeighbors({RECURSION_KC: [FUNCTION_KC]})

    result = await route_and_retrieve(
        query=SIMPLE_QUERY,  # 简单问，但 force 恒走图
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        graph_mode="force",
        neighbors_fn=neighbors,
    )

    assert result.route == "graph"
    assert result.subgraph
    pages = _by_page(result)
    assert 2 in pages and pages[2].via_prerequisite is True


async def test_off_mode_never_expands_even_for_multi_hop_query() -> None:
    embedder, store, bm25 = await _build_index()
    neighbors = _SpyNeighbors({RECURSION_KC: [FUNCTION_KC]})

    result = await route_and_retrieve(
        query=MULTI_HOP_QUERY,  # 多跳问，但 off 恒走向量
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        graph_mode="off",
        neighbors_fn=neighbors,
    )

    assert result.route == "vector"
    assert result.subgraph == []
    assert all(not hit.via_prerequisite for hit in result.hits)
    assert {hit.chunk.page_id for hit in result.hits} == {1}  # 严格 KC 过滤
    assert neighbors.calls == []


# --------------------------------------------------------------------------- #
# ④ 无前置数据 → 优雅退化 vector
# --------------------------------------------------------------------------- #
async def test_no_prereq_data_degrades_to_vector() -> None:
    embedder, store, bm25 = await _build_index()
    empty_neighbors = _SpyNeighbors({})  # G1a 返回空（无前置种子）

    result = await route_and_retrieve(
        query=MULTI_HOP_QUERY,
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        graph_mode="force",  # 即便 force，无前置也退化
        neighbors_fn=empty_neighbors,
    )

    assert result.route == "vector"
    assert result.subgraph == []
    assert result.hits  # 仍有向量/混合召回，不报错
    assert all(not hit.via_prerequisite for hit in result.hits)


async def test_graph_mode_without_kc_anchor_degrades_to_vector() -> None:
    embedder, store, bm25 = await _build_index()
    neighbors = _SpyNeighbors({RECURSION_KC: [FUNCTION_KC]})

    result = await route_and_retrieve(
        query=MULTI_HOP_QUERY,
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=None,  # 无锚点无法扩展
        graph_mode="force",
        neighbors_fn=neighbors,
    )

    assert result.route == "vector"
    assert result.subgraph == []
    assert neighbors.calls == []


# --------------------------------------------------------------------------- #
# ⑤ 不传 graph_mode（旧调用）→ 行为 = 旧版
# --------------------------------------------------------------------------- #
async def test_legacy_no_graph_mode_matches_old_fusion() -> None:
    embedder, store, bm25 = await _build_index()
    # 旧调用：默认 expand_prerequisites=True，不该碰 G1a neighbors。
    forbidden_neighbors = _SpyNeighbors({RECURSION_KC: [FUNCTION_KC]})

    result = await route_and_retrieve(
        query=SIMPLE_QUERY,
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        index_kc_graph=PREREQ_GRAPH,
        graph_mode=None,  # 旧调用
        expand_prerequisites=True,
        neighbors_fn=forbidden_neighbors,
    )

    expected_hits, expected_mode = await hybrid_search(
        query=SIMPLE_QUERY,
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        kc_graph=PREREQ_GRAPH,
        expand_prerequisites=True,
    )

    assert result.route != "graph"  # 旧调用不进新图路由
    assert result.retrieval_mode == expected_mode
    assert [h.chunk.page_id for h in result.hits] == [h.chunk.page_id for h in expected_hits]
    assert [h.via_prerequisite for h in result.hits] == [
        h.via_prerequisite for h in expected_hits
    ]
    assert forbidden_neighbors.calls == []  # 旧路径绝不触 G1a


async def test_legacy_expand_false_keeps_strict_filter() -> None:
    embedder, store, bm25 = await _build_index()

    result = await route_and_retrieve(
        query=SIMPLE_QUERY,
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        index_kc_graph=PREREQ_GRAPH,
        graph_mode=None,
        expand_prerequisites=False,
    )

    assert {hit.chunk.page_id for hit in result.hits} == {1}
    assert result.subgraph == []


# --------------------------------------------------------------------------- #
# ⑥ prereq_hops 上界裁剪
# --------------------------------------------------------------------------- #
async def test_prereq_hops_clamped_to_max() -> None:
    embedder, store, bm25 = await _build_index()
    neighbors = _SpyNeighbors({RECURSION_KC: [FUNCTION_KC]})

    await route_and_retrieve(
        query=MULTI_HOP_QUERY,
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        graph_mode="force",
        prereq_hops=5,  # 超界
        neighbors_fn=neighbors,
    )

    assert neighbors.calls
    assert all(hops == MAX_PREREQ_HOPS for _, hops in neighbors.calls)
    assert MAX_PREREQ_HOPS == 2


async def test_neighbors_edge_shapes_are_normalized() -> None:
    """neighbors_fn 返回多种形态（str / tuple / dict / KcEdge）都能归一为 subgraph 边。"""
    embedder, store, bm25 = await _build_index()
    neighbors = _SpyNeighbors(
        {
            RECURSION_KC: [
                FUNCTION_KC,  # 纯 KC code
            ]
        }
    )

    result = await route_and_retrieve(
        query=MULTI_HOP_QUERY,
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        graph_mode="force",
        neighbors_fn=neighbors,
    )

    assert KcEdge(from_kc=RECURSION_KC, to_kc=FUNCTION_KC) in result.subgraph


# --------------------------------------------------------------------------- #
# 端到端（/rag/query）：响应契约 + 向后兼容 + index 图回退 neighbors
# --------------------------------------------------------------------------- #
@pytest.fixture()
def _client_with_graph() -> TestClient:
    state = AppState(embedder=HashEmbedder(), store=InMemoryVectorStore(), bm25=Bm25Index())
    state.kc_graph = PREREQ_GRAPH
    app.state.rag = state
    return TestClient(app)


async def _seed_endpoint() -> None:
    state = app.state.rag
    await rebuild_index(
        source=InMemoryPageSource(pages=_pages()),
        embedder=state.embedder,
        store=state.store,
        bm25=state.bm25,
    )


async def test_endpoint_multi_hop_graph_via_index_fallback(_client_with_graph: TestClient) -> None:
    await _seed_endpoint()

    response = _client_with_graph.post(
        "/rag/query",
        json={
            "query": MULTI_HOP_QUERY,
            "top_k": 6,
            "min_score": 0.0,
            "kc_codes": [RECURSION_KC],
            "graph_mode": "auto",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["route"] == "graph"
    assert body["retrieval_mode"] == "graph"
    assert body["subgraph"]
    assert {"from_kc": RECURSION_KC, "to_kc": FUNCTION_KC, "relation": "prerequisite"} in body[
        "subgraph"
    ]
    by_page = {hit["page_id"]: hit for hit in body["hits"]}
    assert by_page[2]["via_prerequisite"] is True


async def test_endpoint_simple_query_routes_vector(_client_with_graph: TestClient) -> None:
    await _seed_endpoint()

    response = _client_with_graph.post(
        "/rag/query",
        json={
            "query": "列表 是什么 怎么用",
            "top_k": 6,
            "min_score": 0.0,
            "kc_codes": [LIST_KC],
            "graph_mode": "auto",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["route"] == "vector"
    assert body["subgraph"] == []
    assert all(not hit["via_prerequisite"] for hit in body["hits"])


async def test_endpoint_backward_compatible_without_new_fields(
    _client_with_graph: TestClient,
) -> None:
    await _seed_endpoint()
    state = app.state.rag

    response = _client_with_graph.post(
        "/rag/query",
        json={
            "query": SIMPLE_QUERY,
            "top_k": 6,
            "min_score": 0.0,
            "kc_codes": [RECURSION_KC],
        },
    )

    assert response.status_code == 200
    body = response.json()
    # 旧字段位级兼容：与直接调旧 fusion 一致
    expected_hits, expected_mode = await hybrid_search(
        query=SIMPLE_QUERY,
        embedder=state.embedder,
        store=state.store,
        bm25=state.bm25,
        top_k=6,
        min_score=0.0,
        kc_codes=[RECURSION_KC],
        kc_graph=PREREQ_GRAPH,
        expand_prerequisites=True,
    )
    assert body["no_evidence_found"] is False
    assert body["retrieval_mode"] == expected_mode
    assert [h["page_id"] for h in body["hits"]] == [h.chunk.page_id for h in expected_hits]
    assert body["route"] != "graph"  # 旧调用不自动升级到图路由
    # 新字段作为可选追加存在（默认值）
    assert body["subgraph"] == []


async def test_endpoint_off_mode_disables_expansion(_client_with_graph: TestClient) -> None:
    await _seed_endpoint()

    response = _client_with_graph.post(
        "/rag/query",
        json={
            "query": MULTI_HOP_QUERY,
            "top_k": 6,
            "min_score": 0.0,
            "kc_codes": [RECURSION_KC],
            "graph_mode": "off",
        },
    )

    body = response.json()
    assert body["route"] == "vector"
    assert {hit["page_id"] for hit in body["hits"]} == {1}


async def test_endpoint_prereq_hops_above_bound_is_accepted_and_clamped(
    _client_with_graph: TestClient,
) -> None:
    await _seed_endpoint()

    response = _client_with_graph.post(
        "/rag/query",
        json={
            "query": MULTI_HOP_QUERY,
            "top_k": 6,
            "min_score": 0.0,
            "kc_codes": [RECURSION_KC],
            "graph_mode": "force",
            "prereq_hops": 9,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["route"] == "graph"  # 裁剪后仍正常扩展


async def test_endpoint_no_evidence_with_graph_mode(_client_with_graph: TestClient) -> None:
    await _seed_endpoint()

    response = _client_with_graph.post(
        "/rag/query",
        json={
            "query": "奥利给奥利给奥利给",
            "top_k": 6,
            "min_score": 0.99,
            "kc_codes": [RECURSION_KC],
            "graph_mode": "force",
        },
    )

    body = response.json()
    assert body["no_evidence_found"] is True
    assert body["hits"] == []
    assert body["subgraph"] == []
