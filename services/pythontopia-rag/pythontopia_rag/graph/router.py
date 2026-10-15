"""GraphRAG 自适应检索路由（LightRAG 思路的轻量自建版，G1b-route）。

在既有混合检索（`index/fusion.py` 的 BM25+向量 RRF + 前置扩展）之上叠一层**自适应路由**：

- **简单 / 事实型问**（单 KC、"是什么 / 怎么用"）→ 纯向量召回，省掉图扩展开销；
- **多跳问**（"为什么 / 区别 / 先学"、或锚定多个 KC）→ 调 G1a 的 ``neighbors(kc, hops)``
  扩展前置 KC 闭包，再走召回并把前置命中标 ``via_prerequisite=True``，同时回带可审计 ``subgraph``。

设计取舍（贴 ECS 2C4G，不重复造）：
- **复用 fusion**：本模块不重写召回，把多跳前置闭包**压平成 1 跳**喂给既有
  ``hybrid_search``，直接复用它经过测试的前置扩展 + 配额（``top_k//3``）+ 降权（0.5）逻辑。
- **复用既有数据**：G1a 的 ``graph/kc_graph.py`` 未合并时，``neighbors`` 自动回退到既有
  ``index/kc_graph.py`` 的前置邻接表派生（``prerequisites_of``）——零依赖即可交付，
  G1a 合并后自动升级到更富的图（前置 + 共现）。
- **向后兼容**：``graph_mode is None``（旧调用未传）时**完全复刻历史 fusion 行为**，
  由 ``expand_prerequisites`` 驱动，绝不触碰新图机制，旧调用零行为漂移。

fail-soft：``neighbors`` 抛错 / 无前置数据 / 引擎回退 vector_only 时，一律优雅退化为向量召回，
返回真实证据，绝不阻塞主链路、绝不伪造前置子图。
"""

from __future__ import annotations

import logging
from collections import Counter
from collections.abc import Iterable
from dataclasses import dataclass, field
from typing import Callable, Literal, Sequence

from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.embedder import Embedder
from pythontopia_rag.index.fusion import RetrievalMode, hybrid_search
from pythontopia_rag.index.kc_graph import KcGraph as IndexKcGraph
from pythontopia_rag.index.store import Hit, VectorStore
from pythontopia_rag.schemas.query import GraphMode, KcEdge, RagGraphTrace

logger = logging.getLogger(__name__)

# 前置扩展跳数上界：教育语义上「直接前置」最相关，2 跳已覆盖绝大多数困惑根源；
# 更深的祖先离当前问题过远且会稀释主题页配额。超界请求按此裁剪（不拒绝）。
MAX_PREREQ_HOPS = 2

RouteName = Literal["vector", "graph", "hybrid"]
RetrievalModeName = Literal["hybrid", "vector_only", "graph"]

# G1a 契约：neighbors(kc, hops) → 可迭代的前置邻居描述（见 _coerce_edge 接受的形态）。
NeighborsFn = Callable[[str, int], object]

# 多跳 / 关系型意图提示词（中 / 英）。命中即判为需要图扩展的多跳问。
_MULTI_HOP_HINTS_ZH: tuple[str, ...] = (
    "为什么",
    "为啥",
    "为何",
    "怎么会",
    "凭什么",
    "区别",
    "不同",
    "差异",
    "差别",
    "区分",
    "比较",
    "对比",
    "异同",
    "先学",
    "前置",
    "先决",
    "先修",
    "之前",
    "依赖",
    "关系",
    "联系",
    "关联",
    "原理",
    "看不懂",
    "听不懂",
    "不理解",
    "理解不了",
    "搞不懂",
)
_MULTI_HOP_HINTS_EN: tuple[str, ...] = (
    "why",
    "difference",
    "differ",
    "compare",
    "comparison",
    "versus",
    " vs ",
    "prerequisite",
    "prereq",
    "relate",
    "relationship",
    "depend",
    "before learning",
)


@dataclass
class RouteResult:
    """路由 + 召回结果：``hits`` 复用 fusion 的 Hit（含 via_prerequisite），其余为审计元数据。"""

    hits: list[Hit]
    route: RouteName
    retrieval_mode: RetrievalModeName
    subgraph: list[KcEdge] = field(default_factory=list)
    graph_trace: RagGraphTrace | None = None


def classify_query(query: str, kc_codes: Sequence[str] | None) -> Literal["vector", "graph"]:
    """自适应路由判别（auto 模式用）：多 KC 锚点或命中多跳提示词 → graph，否则 vector。

    轻量纯文本启发式（无 LLM，贴 2C4G）；force/off 模式不经此函数。
    """
    distinct = {code for code in (kc_codes or []) if code}
    if len(distinct) >= 2:
        return "graph"
    text = query or ""
    if any(hint in text for hint in _MULTI_HOP_HINTS_ZH):
        return "graph"
    lowered = text.lower()
    if any(hint in lowered for hint in _MULTI_HOP_HINTS_EN):
        return "graph"
    return "vector"


def _coerce_edge(anchor: str, item: object) -> KcEdge | None:
    """把 G1a neighbors 的单个返回项归一为 KcEdge（容忍多种实现形态，解耦 G1a 具体类型）。

    接受：KcEdge / str(KC code) / dict{to_kc,from_kc?,relation?} /
    (from_kc, to_kc[, relation]) 元组 / 任意带 ``.to_kc`` 的对象。
    """
    if isinstance(item, KcEdge):
        return item
    if isinstance(item, str):
        return KcEdge(from_kc=anchor, to_kc=item) if item else None
    if isinstance(item, dict):
        to_kc = item.get("to_kc") or item.get("kc") or item.get("code")
        if not to_kc:
            return None
        return KcEdge(
            from_kc=str(item.get("from_kc", anchor)),
            to_kc=str(to_kc),
            relation=str(item.get("relation", "prerequisite")),
        )
    if isinstance(item, (tuple, list)):
        if len(item) >= 3:
            return KcEdge(from_kc=str(item[0]), to_kc=str(item[1]), relation=str(item[2]))
        if len(item) == 2:
            return KcEdge(from_kc=str(item[0]), to_kc=str(item[1]))
        if len(item) == 1 and item[0]:
            return KcEdge(from_kc=anchor, to_kc=str(item[0]))
        return None
    to_kc = getattr(item, "to_kc", None)
    if to_kc:
        return KcEdge(
            from_kc=str(getattr(item, "from_kc", anchor)),
            to_kc=str(to_kc),
            relation=str(getattr(item, "relation", "prerequisite")),
        )
    return None


def _normalize_edges(anchor: str, raw: object) -> list[KcEdge]:
    edges: list[KcEdge] = []
    # str/bytes 虽可迭代但绝非邻居列表，显式排除；非可迭代直接放弃（fail-soft）。
    if not isinstance(raw, Iterable) or isinstance(raw, (str, bytes)):
        return edges
    for item in raw:
        edge = _coerce_edge(anchor, item)
        if edge is not None:
            edges.append(edge)
    return edges


def _empty_neighbors(kc: str, hops: int) -> list[KcEdge]:
    return []


def _index_graph_neighbors(index_kc_graph: IndexKcGraph) -> NeighborsFn:
    """既有 index/kc_graph.py 派生的 neighbors（G1a 未合并时的回退源，复用既有前置数据）。"""

    def _fn(kc: str, hops: int) -> list[KcEdge]:
        prereqs = index_kc_graph.prerequisites_of([kc], depth=hops)
        return [KcEdge(from_kc=kc, to_kc=code) for code in prereqs]

    return _fn


def _load_g1a_neighbors() -> NeighborsFn | None:
    """惰性加载 G1a 的 graph/kc_graph.neighbors；未合并 / 不可用时返回 None（fail-soft）。"""
    try:
        from pythontopia_rag.graph.kc_graph import neighbors as g1a_neighbors  # type: ignore
    except Exception:  # noqa: BLE001 - G1a 未合并属预期，静默回退
        return None
    return g1a_neighbors if callable(g1a_neighbors) else None


def _resolve_neighbors_fn(
    neighbors_fn: NeighborsFn | None, index_kc_graph: IndexKcGraph | None
) -> NeighborsFn:
    """neighbors 解析优先级：显式注入（测试 / Phase 2） > G1a 图 > 既有 index 图 > 空。"""
    if neighbors_fn is not None:
        return neighbors_fn
    g1a = _load_g1a_neighbors()
    if g1a is not None:
        return g1a
    if index_kc_graph is not None and index_kc_graph.size > 0:
        return _index_graph_neighbors(index_kc_graph)
    return _empty_neighbors


def _collect_subgraph(
    anchors: list[str], hops: int, neighbors: NeighborsFn
) -> list[KcEdge]:
    """逐锚点取前置邻居并归一去重为子图边（neighbors 抛错则该锚点跳过，fail-soft）。"""
    edges: list[KcEdge] = []
    seen: set[tuple[str, str, str]] = set()
    for anchor in anchors:
        try:
            raw = neighbors(anchor, hops)
        except Exception as exc:  # noqa: BLE001 - 图扩展失败优雅退化，不拖垮检索
            logger.warning(
                "neighbors(%s, %d) failed; skipping anchor: %s",
                anchor,
                hops,
                type(exc).__name__,
            )
            continue
        for edge in _normalize_edges(anchor, raw):
            key = (edge.from_kc, edge.to_kc, edge.relation)
            if key not in seen:
                seen.add(key)
                edges.append(edge)
    return edges


async def _vector_only_retrieve(
    *,
    query: str,
    embedder: Embedder,
    store: VectorStore,
    bm25: Bm25Index | None,
    top_k: int,
    min_score: float,
    kc_codes: list[str] | None,
    chapter_ids: list[int] | None,
) -> tuple[list[Hit], RetrievalMode]:
    """纯向量 / 混合召回但**关闭**前置扩展（route=vector 用）。"""
    return await hybrid_search(
        query=query,
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=top_k,
        min_score=min_score,
        kc_codes=kc_codes,
        chapter_ids=chapter_ids,
        kc_graph=None,
        expand_prerequisites=False,
    )


async def route_and_retrieve(
    *,
    query: str,
    embedder: Embedder,
    store: VectorStore,
    bm25: Bm25Index | None,
    top_k: int,
    min_score: float,
    kc_codes: list[str] | None = None,
    chapter_ids: list[int] | None = None,
    index_kc_graph: IndexKcGraph | None = None,
    graph_mode: GraphMode | None = None,
    prereq_hops: int = 1,
    expand_prerequisites: bool = True,
    neighbors_fn: NeighborsFn | None = None,
) -> RouteResult:
    """自适应路由 + 召回入口。

    ``graph_mode`` 语义：
      - None  → 旧调用兼容：完全复刻历史 fusion（由 ``expand_prerequisites`` 驱动），route 取 vector/hybrid。
      - off   → 恒向量、不扩展。
      - auto  → ``classify_query`` 判别：简单走向量、多跳走图。
      - force → 恒走图扩展（无锚点 / 无前置数据时优雅退化向量）。
    """
    anchors = [code for code in (kc_codes or []) if code]

    # ── 旧调用兼容路径：零行为漂移，绝不触新图机制 ──
    if graph_mode is None:
        hits, mode = await hybrid_search(
            query=query,
            embedder=embedder,
            store=store,
            bm25=bm25,
            top_k=top_k,
            min_score=min_score,
            kc_codes=kc_codes,
            chapter_ids=chapter_ids,
            kc_graph=index_kc_graph,
            expand_prerequisites=expand_prerequisites,
        )
        legacy_route: RouteName = "vector" if mode == "vector_only" else "hybrid"
        return RouteResult(
            hits=hits,
            route=legacy_route,
            retrieval_mode=mode,
            subgraph=[],
            graph_trace=_build_graph_trace(
                hits=hits,
                route=legacy_route,
                retrieval_mode=mode,
                top_k=top_k,
                subgraph=[],
                anchor_set=set(anchors),
            ),
        )

    # ── 新自适应路由：决定走向量还是图 ──
    if graph_mode == "force":
        decision = "graph"
    elif graph_mode == "auto":
        decision = classify_query(query, anchors)
    else:  # "off"
        decision = "vector"

    if decision == "vector" or not anchors:
        hits, mode = await _vector_only_retrieve(
            query=query,
            embedder=embedder,
            store=store,
            bm25=bm25,
            top_k=top_k,
            min_score=min_score,
            kc_codes=kc_codes,
            chapter_ids=chapter_ids,
        )
        return RouteResult(
            hits=hits,
            route="vector",
            retrieval_mode=mode,
            subgraph=[],
            graph_trace=_build_graph_trace(
                hits=hits,
                route="vector",
                retrieval_mode=mode,
                top_k=top_k,
                subgraph=[],
                anchor_set=set(anchors),
            ),
        )

    # ── 图路由：调 G1a neighbors 扩展前置 KC 闭包 ──
    hops = max(1, min(prereq_hops, MAX_PREREQ_HOPS))
    neighbors = _resolve_neighbors_fn(neighbors_fn, index_kc_graph)
    edges = _collect_subgraph(anchors, hops, neighbors)

    anchor_set = set(anchors)
    expansion_relations = _expansion_relations(edges, anchor_set)
    prereq_codes = list(expansion_relations)

    if not prereq_codes:
        # 无前置数据（或 neighbors 全失败）→ 优雅退化向量，不挂空子图、不伪造。
        hits, mode = await _vector_only_retrieve(
            query=query,
            embedder=embedder,
            store=store,
            bm25=bm25,
            top_k=top_k,
            min_score=min_score,
            kc_codes=kc_codes,
            chapter_ids=chapter_ids,
        )
        return RouteResult(
            hits=hits,
            route="vector",
            retrieval_mode=mode,
            subgraph=[],
            graph_trace=_build_graph_trace(
                hits=hits,
                route="vector",
                retrieval_mode=mode,
                top_k=top_k,
                subgraph=[],
                anchor_set=anchor_set,
            ),
        )

    hits, mode = await hybrid_search(
        query=query,
        embedder=embedder,
        store=store,
        bm25=bm25,
        top_k=top_k,
        min_score=min_score,
        kc_codes=kc_codes,
        chapter_ids=chapter_ids,
        kc_graph=None,
        expand_prerequisites=True,
        expansion_relations=expansion_relations,
    )

    if mode == "vector_only":
        # BM25 缺失 → fusion 走 vector_only 退化路径不扩展；诚实标记 vector，不挂空子图。
        return RouteResult(
            hits=hits,
            route="vector",
            retrieval_mode="vector_only",
            subgraph=[],
            graph_trace=_build_graph_trace(
                hits=hits,
                route="vector",
                retrieval_mode="vector_only",
                top_k=top_k,
                subgraph=[],
                anchor_set=anchor_set,
            ),
        )

    if not hits:
        # 无证据页 → 不回带前置子图（无可审计的证据支撑），与 no_evidence 语义一致。
        return RouteResult(
            hits=hits,
            route="graph",
            retrieval_mode="graph",
            subgraph=[],
            graph_trace=_build_graph_trace(
                hits=hits,
                route="graph",
                retrieval_mode="graph",
                top_k=top_k,
                subgraph=[],
                anchor_set=anchor_set,
                expansion_enabled=True,
            ),
        )

    subgraph = [
        edge
        for edge in edges
        if _edge_expansion_target(edge, anchor_set) in expansion_relations
    ]
    return RouteResult(
        hits=hits,
        route="graph",
        retrieval_mode="graph",
        subgraph=subgraph,
        graph_trace=_build_graph_trace(
            hits=hits,
            route="graph",
            retrieval_mode="graph",
            top_k=top_k,
            subgraph=subgraph,
            anchor_set=anchor_set,
            expansion_enabled=True,
        ),
    )


def _expansion_relations(edges: list[KcEdge], anchor_set: set[str]) -> dict[str, str]:
    relations: dict[str, str] = {}
    for edge in edges:
        target = _edge_expansion_target(edge, anchor_set)
        if target is None:
            continue
        # prerequisite wins over weaker relation if multiple edges reach the same KC.
        if relations.get(target) == "prerequisite":
            continue
        relations[target] = edge.relation
    return relations


def _edge_expansion_target(edge: KcEdge, anchor_set: set[str]) -> str | None:
    if edge.to_kc and edge.to_kc not in anchor_set:
        return edge.to_kc
    if edge.relation != "prerequisite" and edge.from_kc and edge.from_kc not in anchor_set:
        return edge.from_kc
    return None


def _build_graph_trace(
    *,
    hits: list[Hit],
    route: RouteName,
    retrieval_mode: RetrievalModeName,
    top_k: int,
    subgraph: list[KcEdge],
    anchor_set: set[str],
    expansion_enabled: bool = False,
) -> RagGraphTrace:
    """把召回结果压成可量化审计指标，避免图扩展变成不可解释的“黑盒加料”。"""

    relation_counts = Counter(edge.relation for edge in subgraph)
    expanded_targets = {
        target
        for edge in subgraph
        if (target := _edge_expansion_target(edge, anchor_set)) is not None
    }
    prerequisite_hit_count = sum(
        1
        for hit in hits
        if hit.via_relation == "prerequisite" or hit.via_prerequisite
    )
    co_occur_hit_count = sum(1 for hit in hits if hit.via_relation == "co_occur")
    supplemental_hit_count = sum(
        1 for hit in hits if hit.via_relation is not None or hit.via_prerequisite
    )
    hit_count = len(hits)
    expansion_was_used = expansion_enabled or bool(subgraph) or supplemental_hit_count > 0
    supplemental_hit_limit = max(1, top_k // 3) if expansion_was_used else 0
    supplemental_hit_ratio = (
        round(supplemental_hit_count / max(top_k, 1), 4) if top_k > 0 else 0.0
    )
    return RagGraphTrace(
        route=route,
        retrieval_mode=retrieval_mode,
        top_k=top_k,
        hit_count=hit_count,
        direct_hit_count=max(0, hit_count - supplemental_hit_count),
        supplemental_hit_count=supplemental_hit_count,
        prerequisite_hit_count=prerequisite_hit_count,
        co_occur_hit_count=co_occur_hit_count,
        supplemental_hit_limit=supplemental_hit_limit,
        supplemental_hit_ratio=supplemental_hit_ratio,
        subgraph_edge_count=len(subgraph),
        expanded_kc_count=len(expanded_targets),
        relation_counts=dict(relation_counts),
        noise_guard=(
            "supplemental_hits_capped_at_top_k_div_3"
            if expansion_was_used
            else "graph_expansion_disabled"
        ),
    )
