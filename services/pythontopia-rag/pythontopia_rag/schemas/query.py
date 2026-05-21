from __future__ import annotations

from pydantic import BaseModel, Field


class RagHit(BaseModel):
    chunk_id: str
    page_id: int
    page_no: int
    chapter_id: int | None = None
    kc_codes: list[str] = Field(default_factory=list)
    text: str
    score: float


class RagQueryRequest(BaseModel):
    query: str = Field(min_length=1, max_length=512)
    top_k: int = Field(default=8, ge=1, le=20)
    kc_codes: list[str] | None = None
    chapter_ids: list[int] | None = None
    min_score: float = Field(default=0.3, ge=0.0, le=1.0)


class RagQueryResponse(BaseModel):
    hits: list[RagHit] = Field(default_factory=list)
    no_evidence_found: bool = False
    rewritten_query: str | None = None


class RagIndexStatus(BaseModel):
    built_chunks: int = 0
    last_rebuilt_at: str | None = None
    embed_model: str
    store: str


class RagRebuildResponse(BaseModel):
    built_chunks: int
    elapsed_ms: int
    embed_model: str
    store: str
