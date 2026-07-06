"""G1a-kc-graph 单元测试矩阵（GraphRAG KC 知识图谱 + 多跳遍历）。

被测模块属于 `pythontopia_rag.graph` 子包（区别于既有 `pythontopia_rag.index.kc_graph`
的「仅前置」轻量扩展器）。本图同时承载两类边：

  * prerequisite —— 来自 V125 前置种子（``course_kc.prerequisite_codes``），有向 KC→前置 KC。
  * co_occur    —— 来自 chunk ``kc_codes`` 共现，无向 KC↔KC。

测试矩阵（对应任务要求）：
  ① 由前置种子 + chunk kc_codes 共现构图（节点/边/权重正确）
  ② neighbors(kc, hops) 多跳正确，含 hops 上界裁剪（>2 等价 2，0/负为空）
  ③ 无前置数据 → 优雅空图（neighbors 返回空、is_empty）
  ④ 缺 networkx → dict 邻接回退仍可 neighbors（含 backend 强制 / auto 降级 / forced 报错）
  ⑤ co_occur 边与 prerequisite 边可区分（relations 过滤 + 子图审计 relation 正确）
  附：dict 与 networkx 两后端结果一致；从真实 chunk 构图的数据通路。
"""

from __future__ import annotations

import pytest

from pythontopia_rag.graph import kc_graph as kc_graph_mod
from pythontopia_rag.graph.kc_graph import (
    RELATION_CO_OCCUR,
    RELATION_PREREQUISITE,
    KcEdge,
    KcKnowledgeGraph,
    build_kc_graph,
    build_kc_graph_from_chunks,
    networkx_available,
)
from pythontopia_rag.graph.traverse import MAX_HOPS, neighbors, traverse_subgraph

# 教学顺序前置链 003→002→001（V125 风格：后一个 KC 以前一个为前置）。
PREREQUISITES = {
    "PY43-KC-002": ["PY43-KC-001"],
    "PY43-KC-003": ["PY43-KC-002"],
}
# chunk 共现：001 与 050 同页两次（权重 2）；003 与 060 同页一次（权重 1）。
CO_OCCURRENCES = [
    ["PY43-KC-001", "PY43-KC-050"],
    ["PY43-KC-001", "PY43-KC-050"],
    ["PY43-KC-003", "PY43-KC-060"],
]


def _full_graph() -> KcKnowledgeGraph:
    return build_kc_graph(prerequisites=PREREQUISITES, co_occurrences=CO_OCCURRENCES)


# ─────────────────────────── ① 构图 ───────────────────────────


def test_build_collects_all_kc_nodes() -> None:
    graph = _full_graph()
    assert graph.nodes == frozenset(
        {
            "PY43-KC-001",
            "PY43-KC-002",
            "PY43-KC-003",
            "PY43-KC-050",
            "PY43-KC-060",
        }
    )
    assert not graph.is_empty


def test_build_creates_directed_prerequisite_edges() -> None:
    graph = _full_graph()
    prereq_edges = {
        (edge.from_kc, edge.to_kc)
        for edge in graph.edges()
        if edge.relation == RELATION_PREREQUISITE
    }
    assert prereq_edges == {
        ("PY43-KC-003", "PY43-KC-002"),
        ("PY43-KC-002", "PY43-KC-001"),
    }


def test_build_creates_co_occurrence_edges_with_accumulated_weight() -> None:
    graph = _full_graph()
    co_edges = {
        (edge.from_kc, edge.to_kc): edge.weight
        for edge in graph.edges()
        if edge.relation == RELATION_CO_OCCUR
    }
    # 无向 co_occur 以排序端点唯一登记一次。
    assert co_edges == {
        ("PY43-KC-001", "PY43-KC-050"): 2.0,
        ("PY43-KC-003", "PY43-KC-060"): 1.0,
    }


def test_co_occurrence_self_pairs_and_dupes_ignored() -> None:
    graph = build_kc_graph(co_occurrences=[["PY43-KC-001", "PY43-KC-001", "PY43-KC-002"]])
    co_edges = [e for e in graph.edges() if e.relation == RELATION_CO_OCCUR]
    assert len(co_edges) == 1
    assert (co_edges[0].from_kc, co_edges[0].to_kc) == ("PY43-KC-001", "PY43-KC-002")


def test_single_kc_chunk_contributes_node_but_no_co_edge() -> None:
    graph = build_kc_graph(co_occurrences=[["PY43-KC-009"]])
    assert graph.nodes == frozenset({"PY43-KC-009"})
    assert [e for e in graph.edges() if e.relation == RELATION_CO_OCCUR] == []


# ─────────────────────── ② neighbors 多跳 + 裁剪 ───────────────────────


def test_neighbors_one_hop_follows_prereq_forward_and_co_occur_both_ways() -> None:
    graph = _full_graph()
    # 003 的出边：prereq 003→002，co_occur 003↔060。
    assert neighbors(graph, "PY43-KC-003", hops=1) == ["PY43-KC-002", "PY43-KC-060"]


def test_neighbors_two_hops_walks_ancestors() -> None:
    graph = _full_graph()
    # hop1: 002, 060；hop2: 002→001。060 无新邻居。
    assert neighbors(graph, "PY43-KC-003", hops=2) == [
        "PY43-KC-002",
        "PY43-KC-060",
        "PY43-KC-001",
    ]


def test_neighbors_hops_clamped_to_max() -> None:
    graph = _full_graph()
    assert MAX_HOPS == 2
    capped = neighbors(graph, "PY43-KC-003", hops=99)
    assert capped == neighbors(graph, "PY43-KC-003", hops=MAX_HOPS)


def test_neighbors_zero_and_negative_hops_empty() -> None:
    graph = _full_graph()
    assert neighbors(graph, "PY43-KC-003", hops=0) == []
    assert neighbors(graph, "PY43-KC-003", hops=-3) == []


def test_neighbors_excludes_seed_itself() -> None:
    graph = _full_graph()
    assert "PY43-KC-003" not in neighbors(graph, "PY43-KC-003", hops=2)


def test_neighbors_unknown_kc_empty() -> None:
    graph = _full_graph()
    assert neighbors(graph, "PY43-KC-999", hops=2) == []


def test_prerequisite_direction_is_not_reversed() -> None:
    graph = _full_graph()
    # 001 是 002 的前置（入边），其出边仅 co_occur 001↔050，不应反向走回 002。
    assert neighbors(graph, "PY43-KC-001", hops=1) == ["PY43-KC-050"]


# ─────────────────────── ③ 无前置数据 → 空图 ───────────────────────


def test_empty_graph_is_empty() -> None:
    graph = build_kc_graph()
    assert graph.is_empty
    assert graph.nodes == frozenset()
    assert graph.edges() == []


def test_empty_graph_neighbors_returns_empty() -> None:
    graph = build_kc_graph(prerequisites={}, co_occurrences=[])
    assert neighbors(graph, "PY43-KC-001", hops=2) == []
    nodes, edges = traverse_subgraph(graph, ["PY43-KC-001"], hops=2)
    assert nodes == [] and edges == []


def test_co_occur_only_when_no_prerequisites() -> None:
    graph = build_kc_graph(co_occurrences=[["PY43-KC-001", "PY43-KC-002"]])
    assert not graph.is_empty
    assert neighbors(graph, "PY43-KC-001", hops=1) == ["PY43-KC-002"]


# ─────────────────────── ④ networkx 缺失 → dict 回退 ───────────────────────


def test_dict_backend_neighbors_always_work() -> None:
    graph = _full_graph()
    assert neighbors(graph, "PY43-KC-003", hops=2, backend="dict") == [
        "PY43-KC-002",
        "PY43-KC-060",
        "PY43-KC-001",
    ]


def test_auto_backend_degrades_to_dict_when_networkx_missing(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(kc_graph_mod, "_import_networkx", lambda: None)
    graph = _full_graph()
    assert graph.networkx_available is False
    assert graph.as_networkx() is None
    assert networkx_available() is False
    # auto 应静默回退 dict，结果与正常一致。
    assert neighbors(graph, "PY43-KC-003", hops=2, backend="auto") == [
        "PY43-KC-002",
        "PY43-KC-060",
        "PY43-KC-001",
    ]


def test_forced_networkx_backend_raises_when_missing(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(kc_graph_mod, "_import_networkx", lambda: None)
    graph = _full_graph()
    with pytest.raises(ValueError):
        neighbors(graph, "PY43-KC-003", hops=1, backend="networkx")


def test_invalid_backend_rejected() -> None:
    graph = _full_graph()
    with pytest.raises(ValueError):
        neighbors(graph, "PY43-KC-003", hops=1, backend="quantum")  # type: ignore[arg-type]


# ─────────────────────── ⑤ co_occur 与 prerequisite 区分 ───────────────────────


def test_neighbors_filtered_to_prerequisite_only() -> None:
    graph = _full_graph()
    assert neighbors(
        graph, "PY43-KC-003", hops=2, relations=[RELATION_PREREQUISITE]
    ) == ["PY43-KC-002", "PY43-KC-001"]


def test_neighbors_filtered_to_co_occur_only() -> None:
    graph = _full_graph()
    assert neighbors(
        graph, "PY43-KC-003", hops=2, relations=[RELATION_CO_OCCUR]
    ) == ["PY43-KC-060"]


def test_subgraph_edges_carry_relation_labels() -> None:
    graph = _full_graph()
    nodes, edges = traverse_subgraph(graph, ["PY43-KC-003"], hops=2)
    by_relation = {edge.relation for edge in edges}
    assert by_relation == {RELATION_PREREQUISITE, RELATION_CO_OCCUR}
    # 子图应含 003→002（prereq）与 003↔060（co_occur）。
    assert KcEdge("PY43-KC-003", "PY43-KC-002", RELATION_PREREQUISITE) in _strip_weight(edges)
    assert KcEdge("PY43-KC-003", "PY43-KC-060", RELATION_CO_OCCUR) in _strip_weight(edges)
    assert set(nodes) == {"PY43-KC-002", "PY43-KC-060", "PY43-KC-001"}


def test_subgraph_filtered_by_relation_excludes_other_edges() -> None:
    graph = _full_graph()
    _, edges = traverse_subgraph(
        graph, ["PY43-KC-003"], hops=2, relations=[RELATION_PREREQUISITE]
    )
    assert all(edge.relation == RELATION_PREREQUISITE for edge in edges)
    assert edges  # 至少含 003→002


def test_subgraph_multi_anchor_dedups_edges() -> None:
    graph = _full_graph()
    _, edges = traverse_subgraph(
        graph, ["PY43-KC-003", "PY43-KC-002"], hops=1
    )
    # 003→002 与 002→001 各一次，不重复。
    keyed = _strip_weight(edges)
    assert len(keyed) == len(set(keyed))


def _strip_weight(edges: list[KcEdge]) -> list[KcEdge]:
    return [KcEdge(e.from_kc, e.to_kc, e.relation) for e in edges]


# ─────────────────────── 附：双后端一致 + 真实 chunk 通路 ───────────────────────


@pytest.mark.skipif(not networkx_available(), reason="networkx 未安装")
def test_dict_and_networkx_backends_agree() -> None:
    graph = _full_graph()
    for seed in ("PY43-KC-003", "PY43-KC-001", "PY43-KC-050"):
        for hops in (1, 2):
            dict_result = neighbors(graph, seed, hops=hops, backend="dict")
            nx_result = neighbors(graph, seed, hops=hops, backend="networkx")
            assert set(dict_result) == set(nx_result)


@pytest.mark.skipif(not networkx_available(), reason="networkx 未安装")
def test_as_networkx_has_all_nodes_and_directed_prereq() -> None:
    graph = _full_graph()
    nxg = graph.as_networkx()
    assert nxg is not None
    assert set(nxg.nodes) == set(graph.nodes)
    # prereq 有向：003→002 存在，002→003 不存在。
    assert nxg.has_edge("PY43-KC-003", "PY43-KC-002")
    assert not nxg.has_edge("PY43-KC-002", "PY43-KC-003")
    # co_occur 双向：001↔050。
    assert nxg.has_edge("PY43-KC-001", "PY43-KC-050")
    assert nxg.has_edge("PY43-KC-050", "PY43-KC-001")


def test_build_from_chunks_real_data_path() -> None:
    from pythontopia_rag.index.chunker import chunk_page

    chunks = chunk_page(
        page_id=1,
        page_no=1,
        document_id=1,
        chapter_id=1,
        kc_codes=["PY43-KC-021", "PY43-KC-022"],
        text="函数定义与返回值：def 定义函数，return 返回结果给调用者。" * 8,
    )
    assert chunks  # 该页至少切出一个 chunk
    graph = build_kc_graph_from_chunks(chunks, prerequisites={"PY43-KC-022": ["PY43-KC-021"]})
    # 共现边（同 chunk 的 kc_codes）+ 前置边并存。
    co_edges = [e for e in graph.edges() if e.relation == RELATION_CO_OCCUR]
    assert ("PY43-KC-021", "PY43-KC-022") in {(e.from_kc, e.to_kc) for e in co_edges}
    assert neighbors(graph, "PY43-KC-022", hops=1, relations=[RELATION_PREREQUISITE]) == [
        "PY43-KC-021"
    ]
