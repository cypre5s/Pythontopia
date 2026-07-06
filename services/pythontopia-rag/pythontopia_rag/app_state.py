from __future__ import annotations

from dataclasses import dataclass, field

from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.embedder import Embedder, select_embedder
from pythontopia_rag.graph.kc_graph import KcKnowledgeGraph
from pythontopia_rag.index.kc_graph import KcGraph
from pythontopia_rag.index.store import InMemoryVectorStore, VectorStore


@dataclass
class AppState:
    embedder: Embedder
    store: VectorStore
    bm25: Bm25Index = field(default_factory=Bm25Index)
    # KC 前置关系图（空图 = 不做图谱扩展，行为退化为纯混合检索）。
    kc_graph: KcGraph = field(default_factory=KcGraph)
    # 运行态 typed KC 图（前置 + 共现），供 graph_mode=auto/force 的多跳遍历使用。
    knowledge_graph: KcKnowledgeGraph = field(default_factory=KcKnowledgeGraph)


def create_default_state() -> AppState:
    return AppState(embedder=select_embedder(), store=InMemoryVectorStore(), bm25=Bm25Index())
