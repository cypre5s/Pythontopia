from __future__ import annotations

from dataclasses import dataclass, field

from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.embedder import Embedder, select_embedder
from pythontopia_rag.index.store import InMemoryVectorStore, VectorStore


@dataclass
class AppState:
    embedder: Embedder
    store: VectorStore
    bm25: Bm25Index = field(default_factory=Bm25Index)


def create_default_state() -> AppState:
    return AppState(embedder=select_embedder(), store=InMemoryVectorStore(), bm25=Bm25Index())
