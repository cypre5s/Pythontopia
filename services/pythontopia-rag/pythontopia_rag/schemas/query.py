from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class RagHit(BaseModel):
    chunk_id: str
    page_id: int
    page_no: int
    chapter_id: int | None = None
    kc_codes: list[str] = Field(default_factory=list)
    text: str
    score: float
    # KC 图谱感知扩展（R-16）：True = 命中来自前置 KC 召回路（「困惑根源在前置知识」），
    # 下游可据此把该页标注为「前置知识」。向后兼容追加，默认 False。
    via_prerequisite: bool = False


class RagQueryRequest(BaseModel):
    query: str = Field(min_length=1, max_length=512)
    top_k: int = Field(default=8, ge=1, le=20)
    kc_codes: list[str] | None = None
    chapter_ids: list[int] | None = None
    min_score: float = Field(default=0.3, ge=0.0, le=1.0)
    # KC 图谱感知扩展开关（默认开）：锚定 kc_codes 时沿前置边追加低权重召回路。
    expand_prerequisites: bool = True


class RagQueryResponse(BaseModel):
    hits: list[RagHit] = Field(default_factory=list)
    # 无有效证据时为 True，下游不得伪造 page_id/页码引用
    no_evidence_found: bool = False
    rewritten_query: str | None = None
    # 新增可选字段：hybrid（BM25+向量 RRF 融合）或 vector_only（BM25 索引缺失自动回退）。
    retrieval_mode: Literal["hybrid", "vector_only"] | None = None


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
