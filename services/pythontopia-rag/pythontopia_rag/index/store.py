from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Iterable, Protocol

from pythontopia_rag.index.chunker import Chunk


@dataclass
class Hit:
    chunk: Chunk
    score: float


@dataclass
class StoreStatus:
    built_chunks: int
    last_rebuilt_at: str | None
    embed_dim: int
    store: str


class VectorStore(Protocol):
    name: str

    async def upsert(
        self, chunks: list[Chunk], vectors: list[list[float]]
    ) -> None:  # pragma: no cover
        ...

    async def search(
        self,
        vector: list[float],
        top_k: int,
        *,
        kc_codes: list[str] | None = None,
        chapter_ids: list[int] | None = None,
    ) -> list[Hit]:  # pragma: no cover
        ...

    async def clear(self) -> None:  # pragma: no cover
        ...

    async def status(self) -> StoreStatus:  # pragma: no cover
        ...


@dataclass
class _Row:
    chunk: Chunk
    vector: list[float]


@dataclass
class InMemoryVectorStore:
    name: str = "in-memory"
    embed_dim: int = 0
    last_rebuilt_at: str | None = None
    rows: list[_Row] = field(default_factory=list)

    async def upsert(self, chunks: list[Chunk], vectors: list[list[float]]) -> None:
        if len(chunks) != len(vectors):
            raise ValueError("chunks and vectors length mismatch")
        if vectors:
            self.embed_dim = len(vectors[0])
        existing = {row.chunk.chunk_id: index for index, row in enumerate(self.rows)}
        for chunk, vector in zip(chunks, vectors, strict=False):
            row = _Row(chunk=chunk, vector=list(vector))
            if chunk.chunk_id in existing:
                self.rows[existing[chunk.chunk_id]] = row
            else:
                existing[chunk.chunk_id] = len(self.rows)
                self.rows.append(row)

    async def clear(self) -> None:
        self.rows = []
        self.embed_dim = 0
        self.last_rebuilt_at = None

    async def search(
        self,
        vector: list[float],
        top_k: int,
        *,
        kc_codes: list[str] | None = None,
        chapter_ids: list[int] | None = None,
    ) -> list[Hit]:
        if not self.rows:
            return []
        normalized = _normalize(vector)
        scored: list[Hit] = []
        for row in self.rows:
            if kc_codes and not _intersect(row.chunk.kc_codes, kc_codes):
                continue
            if chapter_ids and row.chunk.chapter_id not in chapter_ids:
                continue
            row_vector = _normalize(row.vector)
            score = _cosine(normalized, row_vector)
            scored.append(Hit(chunk=row.chunk, score=score))
        scored.sort(key=lambda hit: hit.score, reverse=True)
        return scored[:top_k]

    async def status(self) -> StoreStatus:
        return StoreStatus(
            built_chunks=len(self.rows),
            last_rebuilt_at=self.last_rebuilt_at,
            embed_dim=self.embed_dim,
            store=self.name,
        )

    def mark_rebuilt(self, timestamp: str) -> None:
        self.last_rebuilt_at = timestamp


def _intersect(left: Iterable[str], right: Iterable[str]) -> bool:
    right_set = set(right)
    return any(value in right_set for value in left)


def _normalize(vector: list[float]) -> list[float]:
    norm = math.sqrt(sum(x * x for x in vector))
    if norm <= 0:
        return vector
    return [x / norm for x in vector]


def _cosine(a: list[float], b: list[float]) -> float:
    return sum(x * y for x, y in zip(a, b, strict=False))
