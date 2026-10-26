from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Protocol

from pythontopia_rag.index.chunker import Chunk


@dataclass
class Hit:
    chunk: Chunk
    # hybrid 模式下是**融合排序分**（RRF 位次归一，0~1]，用于排序/展示，非相关度；
    # vector_only 模式下就是余弦相关度。真实相关度请读 vector_score / bm25_score。
    score: float
    # KC 图谱感知扩展：命中来自「前置 KC 召回路」而非主题 KC（融合层判定并标注）。
    via_prerequisite: bool = False
    # 命中来自图扩展时记录主导关系；None 表示主题 KC 直接命中或非图扩展。
    via_relation: str | None = None
    # 各召回路的原生分（向后兼容追加）：余弦相似度 / BM25 分。未被该路召回时为 None。
    vector_score: float | None = None
    bm25_score: float | None = None


@dataclass
class StoreStatus:
    built_chunks: int
    last_rebuilt_at: str | None
    embed_dim: int
    store: str
    # 索引是否由降级嵌入（hash 兜底）构建；True 时向量空间不是真实语义空间。
    degraded: bool = False
    fallback_reason: str | None = None


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
    degraded: bool = False
    fallback_reason: str | None = None

    async def upsert(self, chunks: list[Chunk], vectors: list[list[float]]) -> None:
        if len(chunks) != len(vectors):
            raise ValueError("chunks and vectors length mismatch")
        if vectors:
            self.embed_dim = len(vectors[0])
        existing = {row.chunk.chunk_id: index for index, row in enumerate(self.rows)}
        for chunk, vector in zip(chunks, vectors, strict=False):
            row = _Row(chunk=chunk, vector=_normalize(list(vector)))
            if chunk.chunk_id in existing:
                self.rows[existing[chunk.chunk_id]] = row
            else:
                existing[chunk.chunk_id] = len(self.rows)
                self.rows.append(row)

    async def clear(self) -> None:
        self.rows = []
        self.embed_dim = 0
        self.last_rebuilt_at = None
        self.degraded = False
        self.fallback_reason = None

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
        kc_filter = set(kc_codes or ())
        chapter_filter = set(chapter_ids or ())
        scored: list[Hit] = []
        for row in self.rows:
            if kc_filter and not any(code in kc_filter for code in row.chunk.kc_codes):
                continue
            if chapter_filter and row.chunk.chapter_id not in chapter_filter:
                continue
            score = _cosine(normalized, row.vector)
            scored.append(Hit(chunk=row.chunk, score=score))
        scored.sort(key=lambda hit: hit.score, reverse=True)
        return scored[:top_k]

    async def status(self) -> StoreStatus:
        return StoreStatus(
            built_chunks=len(self.rows),
            last_rebuilt_at=self.last_rebuilt_at,
            embed_dim=self.embed_dim,
            store=self.name,
            degraded=self.degraded,
            fallback_reason=self.fallback_reason,
        )

    def mark_rebuilt(self, timestamp: str) -> None:
        self.last_rebuilt_at = timestamp

    def mark_degraded(self, degraded: bool, reason: str | None) -> None:
        """记录本次重建所用嵌入是否为降级兜底，供 /rag/index/status 暴露（禁止静默降级）。"""
        self.degraded = degraded
        self.fallback_reason = reason


def _normalize(vector: list[float]) -> list[float]:
    norm = math.sqrt(sum(x * x for x in vector))
    if norm <= 0:
        return vector
    return [x / norm for x in vector]


def _cosine(a: list[float], b: list[float]) -> float:
    return sum(x * y for x, y in zip(a, b, strict=False))
