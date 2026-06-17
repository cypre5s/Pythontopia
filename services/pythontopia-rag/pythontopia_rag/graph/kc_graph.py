"""KC 知识图谱（GraphRAG / LightRAG 思路的轻量自建版）。

与既有 ``pythontopia_rag.index.kc_graph``（仅「前置」邻接表，服务 fusion 的前置扩展召回）
不同，本模块承载一张**更完整的双关系 KC 图**，供 GraphRAG 自适应路由的多跳遍历使用：

  * ``prerequisite`` —— 有向边 ``KC → 其前置 KC``，数据来自 V125 前置种子
    （``course_kc.prerequisite_codes``，与 ``index.kc_graph`` 同源；rag 服务通过
    ``index.kc_graph.fetch_kc_graph`` 已能从 DB 取到该邻接表）。
  * ``co_occur`` —— 无向边 ``KC ↔ KC``，数据来自 chunk 的 ``kc_codes`` 共现
    （同一 chunk 列出的多个 KC 视为彼此相关）。

设计取向（贴 2C4G 单 worker）：

  * **零 LLM 抽取**：实体关系直接来自结构化种子 + chunk 元数据，不调模型。
  * **内存图优先**：核心邻接与遍历用纯 ``dict``，确定性、零外部依赖。
  * **networkx 懒加载**：``networkx`` 在 pyproject ``[graph]`` optional 组，按需 import；
    缺包时 ``as_networkx()`` 返回 ``None``、遍历自动回退 dict 邻接（见 ``traverse``）。

本模块只负责「建图 + 暴露图结构」；多跳遍历在 ``pythontopia_rag.graph.traverse``。
接线（前置种子/chunk 如何进 rag）由 G1b / Phase 2 完成，本模块通过显式入参解耦。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Iterable, Mapping, Protocol

RELATION_PREREQUISITE = "prerequisite"
RELATION_CO_OCCUR = "co_occur"
RELATIONS: frozenset[str] = frozenset({RELATION_PREREQUISITE, RELATION_CO_OCCUR})


@dataclass(frozen=True)
class KcEdge:
    """KC 图的一条边（结构化、可审计）。

    ``relation`` ∈ {prerequisite, co_occur}。prerequisite 有向（from→to 表示「from 以 to 为前置」）；
    co_occur 无向（规范化为端点字典序 from<=to）。``weight`` 为出现频次（前置=列出次数，
    共现=同 chunk 出现次数），仅作软信号，不影响遍历可达性。
    """

    from_kc: str
    to_kc: str
    relation: str
    weight: float = 1.0


def _import_networkx() -> Any:
    """懒加载 networkx；缺包返回 None（不抛，保持 fail-soft）。

    返回 ``Any``：networkx 无 type stub，本函数与 ``as_networkx`` / traverse 的 networkx
    后端是第三方动态图库的**隔离边界**（AGENTS「第三方动态对象隔离在 adapter 层」），
    ``Any`` 仅限于此，不外泄到 ``KcEdge`` / ``KcKnowledgeGraph`` 等业务类型。
    """
    try:
        import networkx  # type: ignore[import-untyped]
    except Exception:  # noqa: BLE001 - 缺 optional 依赖时优雅退化
        return None
    return networkx


def networkx_available() -> bool:
    """networkx 是否可用（供调用方决定是否走图后端 / 跳过相关测试）。"""
    return _import_networkx() is not None


class _ChunkLike(Protocol):
    """鸭子类型：任何带 ``kc_codes`` 的对象（如 index.chunker.Chunk）。"""

    kc_codes: list[str]


@dataclass
class KcKnowledgeGraph:
    """KC 双关系图的不可变视图（节点 + 前置出边邻接 + 共现对称邻接）。

    空图（无节点）= GraphRAG 退化为不扩展；调用方据此回退纯向量/混合检索。
    """

    _nodes: frozenset[str] = field(default_factory=frozenset)
    # 前置出边：from_kc -> {prereq_kc: weight}（有向）。
    _prereq_out: dict[str, dict[str, float]] = field(default_factory=dict)
    # 共现邻接：kc -> {kc: weight}（对称，两个方向都存，便于无向遍历）。
    _co_adj: dict[str, dict[str, float]] = field(default_factory=dict)

    @property
    def nodes(self) -> frozenset[str]:
        return self._nodes

    @property
    def is_empty(self) -> bool:
        return not self._nodes

    @property
    def networkx_available(self) -> bool:
        return _import_networkx() is not None

    def edge_count(self) -> int:
        return len(self.edges())

    def edges(self) -> list[KcEdge]:
        """全部边的规范化列表（确定性排序；co_occur 端点字典序去重为一条）。"""
        result: list[KcEdge] = []
        for frm in sorted(self._prereq_out):
            for to in sorted(self._prereq_out[frm]):
                result.append(
                    KcEdge(frm, to, RELATION_PREREQUISITE, self._prereq_out[frm][to])
                )
        seen: set[tuple[str, str]] = set()
        for frm in sorted(self._co_adj):
            for to in sorted(self._co_adj[frm]):
                low, high = (frm, to) if frm <= to else (to, frm)
                if (low, high) in seen:
                    continue
                seen.add((low, high))
                result.append(
                    KcEdge(low, high, RELATION_CO_OCCUR, self._co_adj[frm][to])
                )
        return result

    def out_neighbors(
        self, kc: str, relations: Iterable[str] | None = None
    ) -> list[KcEdge]:
        """``kc`` 的 1 跳出边（前置正向 + 共现双向），按 (to_kc, relation) 稳定排序。

        ``relations`` 为 None 表示两类边都要；可传 ``[RELATION_PREREQUISITE]`` 等过滤。
        """
        wanted = _normalize_relations(relations)
        edges: list[KcEdge] = []
        if RELATION_PREREQUISITE in wanted:
            for to, weight in self._prereq_out.get(kc, {}).items():
                edges.append(KcEdge(kc, to, RELATION_PREREQUISITE, weight))
        if RELATION_CO_OCCUR in wanted:
            for to, weight in self._co_adj.get(kc, {}).items():
                edges.append(KcEdge(kc, to, RELATION_CO_OCCUR, weight))
        edges.sort(key=lambda edge: (edge.to_kc, edge.relation))
        return edges

    def as_networkx(self) -> object | None:
        """构造 networkx MultiDiGraph（前置有向 + 共现双向）；缺包返回 None。

        返回 ``object | None`` 以免在缺包环境引入 networkx 类型依赖；调用方需要时按
        networkx API 使用（见 ``traverse`` 的 networkx 后端）。
        """
        nx = _import_networkx()
        if nx is None:
            return None
        graph = nx.MultiDiGraph()
        graph.add_nodes_from(self._nodes)
        for frm, tos in self._prereq_out.items():
            for to, weight in tos.items():
                graph.add_edge(frm, to, relation=RELATION_PREREQUISITE, weight=weight)
        for frm, tos in self._co_adj.items():
            for to, weight in tos.items():
                graph.add_edge(frm, to, relation=RELATION_CO_OCCUR, weight=weight)
        built: object = graph
        return built


def _normalize_relations(relations: Iterable[str] | None) -> frozenset[str]:
    if relations is None:
        return RELATIONS
    return frozenset(relations)


def _clean(code: object) -> str:
    return str(code).strip() if code is not None else ""


def _bump(adjacency: dict[str, dict[str, float]], src: str, dst: str) -> None:
    bucket = adjacency.setdefault(src, {})
    bucket[dst] = bucket.get(dst, 0.0) + 1.0


def build_kc_graph(
    *,
    prerequisites: Mapping[str, Iterable[str]] | None = None,
    co_occurrences: Iterable[Iterable[str]] | None = None,
) -> KcKnowledgeGraph:
    """由前置种子 + 共现分组构图。

    参数
    ----
    prerequisites:
        ``{kc_code: [prereq_kc, ...]}``，即 V125 前置种子的邻接表形态
        （与 ``index.kc_graph.KcGraph.prerequisites`` 同形，可直接喂入）。
    co_occurrences:
        共现分组的可迭代，每组是一个 chunk/页 的 ``kc_codes``。组内任意两 KC 结一条
        无向 co_occur 边；多组重复出现累加权重。

    两者皆缺 → 返回空图（GraphRAG 优雅退化为不扩展）。
    """
    nodes: set[str] = set()
    prereq_out: dict[str, dict[str, float]] = {}
    if prerequisites:
        for raw_kc, prereqs in prerequisites.items():
            kc = _clean(raw_kc)
            if not kc:
                continue
            nodes.add(kc)
            for raw_prereq in prereqs or []:
                prereq = _clean(raw_prereq)
                if not prereq or prereq == kc:
                    continue
                nodes.add(prereq)
                _bump(prereq_out, kc, prereq)

    co_adj: dict[str, dict[str, float]] = {}
    if co_occurrences:
        for group in co_occurrences:
            unique: list[str] = []
            seen: set[str] = set()
            for raw_code in group or []:
                code = _clean(raw_code)
                if code and code not in seen:
                    seen.add(code)
                    unique.append(code)
            for code in unique:
                nodes.add(code)
            for i in range(len(unique)):
                for j in range(i + 1, len(unique)):
                    left, right = unique[i], unique[j]
                    _bump(co_adj, left, right)
                    _bump(co_adj, right, left)

    return KcKnowledgeGraph(
        _nodes=frozenset(nodes),
        _prereq_out=prereq_out,
        _co_adj=co_adj,
    )


def build_kc_graph_from_chunks(
    chunks: Iterable[_ChunkLike],
    *,
    prerequisites: Mapping[str, Iterable[str]] | None = None,
) -> KcKnowledgeGraph:
    """从已构建的 chunk 列表抽取共现分组（每 chunk 的 ``kc_codes`` 为一组）再建图。

    这是 rag 服务的真实数据通路：``index.builder.rebuild_index`` 产出的 chunk 已携带
    页级 ``kc_codes``（见 builder 第 63 行），故无需额外查询即可拿到共现信号；前置边由
    调用方传入（Phase 2 由 G1b 注入 ``fetch_kc_graph`` 得到的前置邻接）。
    """
    groups = [list(getattr(chunk, "kc_codes", []) or []) for chunk in chunks]
    return build_kc_graph(prerequisites=prerequisites, co_occurrences=groups)
