from __future__ import annotations

from dataclasses import dataclass

from pythontopia_rag.index.embedder import Embedder, HashEmbedder
from pythontopia_rag.index.store import InMemoryVectorStore, VectorStore


@dataclass
class AppState:
    embedder: Embedder
    store: VectorStore


def create_default_state() -> AppState:
    return AppState(embedder=HashEmbedder(), store=InMemoryVectorStore())
