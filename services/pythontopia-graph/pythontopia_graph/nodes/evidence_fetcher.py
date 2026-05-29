from __future__ import annotations

import os
from typing import Protocol

import httpx
from pydantic import BaseModel, Field


class RagHit(BaseModel):
    chunk_id: str
    page_id: int
    page_no: int
    chapter_id: int | None = None
    kc_codes: list[str] = Field(default_factory=list)
    text: str
    score: float


class EvidenceBundle(BaseModel):
    chunks: list[RagHit] = Field(default_factory=list)
    no_evidence_found: bool = False

    @property
    def page_ids(self) -> list[int]:
        seen: set[int] = set()
        ordered: list[int] = []
        for hit in self.chunks:
            if hit.page_id in seen:
                continue
            seen.add(hit.page_id)
            ordered.append(hit.page_id)
        return ordered

    @property
    def page_nos(self) -> set[int]:
        return {hit.page_no for hit in self.chunks}


class EvidenceFetcher(Protocol):
    async def fetch(
        self, *, kc_codes: list[str], query: str, top_k: int = 6
    ) -> EvidenceBundle: ...


class StaticEvidenceFetcher:
    """Deterministic fetcher backed by an in-memory list; used in tests."""

    def __init__(self, hits: list[RagHit]) -> None:
        self._hits = hits

    async def fetch(
        self, *, kc_codes: list[str], query: str, top_k: int = 6
    ) -> EvidenceBundle:
        filtered = [hit for hit in self._hits if any(code in hit.kc_codes for code in kc_codes)]
        return EvidenceBundle(chunks=filtered[:top_k], no_evidence_found=not filtered)


class HttpRagEvidenceFetcher:
    """Fetcher that calls the Pythontopia RAG service over HTTP."""

    def __init__(self, base_url: str | None = None, timeout: float = 5.0) -> None:
        self._base_url = (base_url or os.environ.get("PYT_RAG_URL", "http://localhost:18092")).rstrip("/")
        self._timeout = timeout

    async def fetch(
        self, *, kc_codes: list[str], query: str, top_k: int = 6
    ) -> EvidenceBundle:
        # 命中 KC 过滤时（资源工坊按 KC 生成）候选已被限定在该 KC，精度由过滤保证，
        # 故阈值放到 0 以确保该 KC 的页面一定作为证据返回（hash 向量对会话式 query
        # 余弦偏低，0.3 会把相关页全部过滤成 NO_EVIDENCE）。无 KC（自由答疑）时用
        # 适中阈值 0.15 抑制噪声页被误引。
        min_score = 0.0 if kc_codes else 0.15
        payload = {
            "query": query,
            "top_k": top_k,
            "kc_codes": list(kc_codes),
            "min_score": min_score,
        }
        async with httpx.AsyncClient(timeout=self._timeout, trust_env=False) as client:
            response = await client.post(f"{self._base_url}/rag/query", json=payload)
        response.raise_for_status()
        body = response.json()
        chunks = [RagHit(**hit) for hit in body.get("hits", [])]
        return EvidenceBundle(chunks=chunks, no_evidence_found=bool(body.get("no_evidence_found")))
