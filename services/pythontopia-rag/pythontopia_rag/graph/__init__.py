"""KC 知识图谱子包（GraphRAG 多跳 / 前置扩展）。

公开 API（G1a 落地；router 由 G1b 落地，不在此 re-export）：

  * 建图：``build_kc_graph`` / ``build_kc_graph_from_chunks`` / ``KcKnowledgeGraph`` / ``KcEdge``
  * 遍历：``neighbors`` / ``traverse_subgraph``（多跳，``hops <= MAX_HOPS``）
  * 探测：``networkx_available``（networkx 懒加载，缺包优雅退化为纯 dict 邻接）

re-export 仅触达本子包自有模块（kc_graph / traverse），不引入 networkx（其在
``_import_networkx`` 内按需加载），保持 import 轻量与 fail-soft。
"""

from pythontopia_rag.graph.kc_graph import (
    RELATION_CO_OCCUR,
    RELATION_PREREQUISITE,
    RELATIONS,
    KcEdge,
    KcKnowledgeGraph,
    build_kc_graph,
    build_kc_graph_from_chunks,
    networkx_available,
)
from pythontopia_rag.graph.traverse import MAX_HOPS, neighbors, traverse_subgraph

__all__ = [
    "RELATION_PREREQUISITE",
    "RELATION_CO_OCCUR",
    "RELATIONS",
    "KcEdge",
    "KcKnowledgeGraph",
    "build_kc_graph",
    "build_kc_graph_from_chunks",
    "networkx_available",
    "MAX_HOPS",
    "neighbors",
    "traverse_subgraph",
]
