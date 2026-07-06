"""KC 知识图谱多跳遍历（GraphRAG 自适应路由的图侧原语）。

对外暴露两个函数，供 G1b 的 router 调用：

  * ``neighbors(graph, kc, hops=1)`` —— 从锚点 KC 出发的多跳邻居（**hops 上界 2**，
    超过自动裁剪；0/负返回空）。结果不含锚点自身，按发现顺序去重。
  * ``traverse_subgraph(graph, kcs, hops=1)`` —— 多锚点遍历，额外返回走过的边
    （``list[KcEdge]``，relation 可区分前置/共现），供「前置链路」可审计子图。

后端（``backend``）：
  * ``"auto"`` —— networkx 可用走 networkx，否则走纯 dict 邻接（缺包优雅退化）。
  * ``"dict"`` —— 强制纯 dict 邻接（确定性、零依赖）。
  * ``"networkx"`` —— 强制 networkx；缺包抛 ``ValueError``（显式失败，不静默）。

两后端在「可达邻居集合」上等价；dict 后端额外保证 (to_kc) 字典序的稳定输出。

Phase 2 接线（供 G1b router 的 ``neighbors_fn(kc, hops)`` 注入，二选一）：

  * 仅要邻居 KC code（router 自行封装为前置边）::

        neighbors_fn = functools.partial(neighbors, kc_knowledge_graph)
        # 调用 neighbors_fn(kc, hops) -> list[str]

  * 要带 relation 的可审计子图边（前置/共现可区分）::

        def neighbors_fn(kc: str, hops: int) -> list[KcEdge]:
            _reached, edges = traverse_subgraph(kc_knowledge_graph, [kc], hops)
            return edges  # KcEdge.from_kc/to_kc/relation 与 schemas.query.KcEdge 同形

  其中 ``kc_knowledge_graph`` 由 G1b 在 rebuild 时构建：前置边来自
  ``index.kc_graph.fetch_kc_graph(...).prerequisites``，共现边来自已构建 chunk 的
  ``kc_codes``（见 ``kc_graph.build_kc_graph_from_chunks``）。
"""

from __future__ import annotations

from typing import Any, Callable, Iterable, Literal

from pythontopia_rag.graph.kc_graph import (
    RELATION_CO_OCCUR,
    KcEdge,
    KcKnowledgeGraph,
)

MAX_HOPS = 2

Backend = Literal["auto", "dict", "networkx"]
_VALID_BACKENDS: frozenset[str] = frozenset({"auto", "dict", "networkx"})

Expander = Callable[[str], list[str]]


def _clamp_hops(hops: int) -> int:
    if hops <= 0:
        return 0
    return min(hops, MAX_HOPS)


def neighbors(
    graph: KcKnowledgeGraph,
    kc: str,
    hops: int = 1,
    *,
    relations: Iterable[str] | None = None,
    backend: Backend = "auto",
) -> list[str]:
    """锚点 ``kc`` 的多跳邻居（≤ ``MAX_HOPS`` 跳），不含锚点自身。

    BFS 逐层扩展，``visited`` 防环，按发现顺序去重输出（每层内按 to_kc 字典序，
    保证确定性）。空图 / 未知锚点 / hops≤0 → 返回空列表。

    ``hops`` 为位置或关键字参数：``functools.partial(neighbors, graph)`` 即可直接作为
    G1b router 的 ``neighbors_fn(kc, hops)`` 注入（见模块 docstring 的 Phase 2 接线说明）。
    """
    effective_hops = _clamp_hops(hops)
    if effective_hops == 0 or kc not in graph.nodes:
        return []
    expand = _make_expander(graph, relations, backend)
    visited: set[str] = {kc}
    frontier: list[str] = [kc]
    found: list[str] = []
    for _ in range(effective_hops):
        next_frontier: list[str] = []
        for node in frontier:
            for neighbor in expand(node):
                if neighbor not in visited:
                    visited.add(neighbor)
                    found.append(neighbor)
                    next_frontier.append(neighbor)
        if not next_frontier:
            break
        frontier = next_frontier
    return found


def traverse_subgraph(
    graph: KcKnowledgeGraph,
    kcs: Iterable[str],
    hops: int = 1,
    *,
    relations: Iterable[str] | None = None,
) -> tuple[list[str], list[KcEdge]]:
    """多锚点多跳遍历，返回 ``(reached_nodes, edges)``。

    ``reached_nodes`` 为不含锚点的可达节点（发现顺序去重）；``edges`` 为遍历触达的规范化
    边集合（co_occur 端点字典序、按 (from,to,relation) 去重），用于 GraphRAG 子图审计。
    边读自图结构（dict），故不接受 backend——审计需要 relation/方向元数据。
    """
    effective_hops = _clamp_hops(hops)
    seeds = [kc for kc in dict.fromkeys(kcs) if kc in graph.nodes]
    if effective_hops == 0 or not seeds:
        return [], []
    visited: set[str] = set(seeds)
    frontier: list[str] = list(seeds)
    found: list[str] = []
    edges: list[KcEdge] = []
    seen_edges: set[tuple[str, str, str]] = set()
    for _ in range(effective_hops):
        next_frontier: list[str] = []
        for node in frontier:
            for edge in graph.out_neighbors(node, relations):
                canonical = _canonical_edge(edge)
                key = (canonical.from_kc, canonical.to_kc, canonical.relation)
                if key not in seen_edges:
                    seen_edges.add(key)
                    edges.append(canonical)
                neighbor = edge.to_kc
                if neighbor not in visited:
                    visited.add(neighbor)
                    found.append(neighbor)
                    next_frontier.append(neighbor)
        if not next_frontier:
            break
        frontier = next_frontier
    return found, edges


def _canonical_edge(edge: KcEdge) -> KcEdge:
    """co_occur 端点规范化为字典序（无向去重）；prerequisite 保持有向。"""
    if edge.relation == RELATION_CO_OCCUR and edge.from_kc > edge.to_kc:
        return KcEdge(edge.to_kc, edge.from_kc, edge.relation, edge.weight)
    return edge


def _resolve_backend(graph: KcKnowledgeGraph, backend: Backend) -> str:
    if backend not in _VALID_BACKENDS:
        raise ValueError(f"unknown backend: {backend!r}")
    if backend == "auto":
        return "networkx" if graph.networkx_available else "dict"
    return backend


def _make_expander(
    graph: KcKnowledgeGraph, relations: Iterable[str] | None, backend: Backend
) -> Expander:
    resolved = _resolve_backend(graph, backend)
    if resolved == "networkx":
        return _networkx_expander(graph, relations)
    return _dict_expander(graph, relations)


def _dict_expander(
    graph: KcKnowledgeGraph, relations: Iterable[str] | None
) -> Expander:
    def expand(node: str) -> list[str]:
        result: list[str] = []
        seen: set[str] = set()
        for edge in graph.out_neighbors(node, relations):
            if edge.to_kc not in seen:
                seen.add(edge.to_kc)
                result.append(edge.to_kc)
        return result

    return expand


def _networkx_expander(
    graph: KcKnowledgeGraph, relations: Iterable[str] | None
) -> Expander:
    # networkx 隔离边界：句柄按 Any 处理（无 stub），不外泄到返回类型 list[str]。
    nx_graph: Any = graph.as_networkx()
    if nx_graph is None:
        raise ValueError("networkx backend requested but networkx is unavailable")
    wanted = None if relations is None else frozenset(relations)

    def expand(node: str) -> list[str]:
        if node not in nx_graph:
            return []
        result: list[str] = []
        seen: set[str] = set()
        for _src, dst, relation in nx_graph.out_edges(node, data="relation"):
            if wanted is not None and relation not in wanted:
                continue
            if dst not in seen:
                seen.add(dst)
                result.append(str(dst))
        result.sort()
        return result

    return expand
