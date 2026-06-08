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
    """测试用确定性 fetcher，基于内存 RagHit 列表。"""

    def __init__(self, hits: list[RagHit]) -> None:
        self._hits = hits

    async def fetch(
        self, *, kc_codes: list[str], query: str, top_k: int = 6
    ) -> EvidenceBundle:
        filtered = [hit for hit in self._hits if any(code in hit.kc_codes for code in kc_codes)]
        return EvidenceBundle(chunks=filtered[:top_k], no_evidence_found=not filtered)


class HttpRagEvidenceFetcher:
    """经 HTTP 调用 Pythontopia RAG 服务的 EvidenceFetcher。"""

    def __init__(self, base_url: str | None = None, timeout: float = 5.0) -> None:
        self._base_url = (base_url or os.environ.get("PYT_RAG_URL", "http://localhost:18092")).rstrip("/")
        self._timeout = timeout

    async def fetch(
        self, *, kc_codes: list[str], query: str, top_k: int = 6
    ) -> EvidenceBundle:
        # 有 kc_codes 时 min_score=0（KC 已过滤，hash 向量对会话 query 余弦偏低，高阈值会误成 no_evidence）；无 KC 时用 0.15 抑噪。
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
