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
        payload = {
            "query": query,
            "top_k": top_k,
            "kc_codes": list(kc_codes),
            "min_score": 0.3,
        }
        async with httpx.AsyncClient(timeout=self._timeout) as client:
            response = await client.post(f"{self._base_url}/rag/query", json=payload)
        response.raise_for_status()
        body = response.json()
        chunks = [RagHit(**hit) for hit in body.get("hits", [])]
        return EvidenceBundle(chunks=chunks, no_evidence_found=bool(body.get("no_evidence_found")))
