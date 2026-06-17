from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

# 自适应路由模式：off=恒向量；auto=简单走向量、多跳走图；force=恒走图。
GraphMode = Literal["off", "auto", "force"]


class KcEdge(BaseModel):
    """KC 关系子图的一条可审计边（前置链路）。"""

    from_kc: str
    to_kc: str
    relation: str = "prerequisite"


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
    # 自 GraphRAG 自适应路由（R-17）起作为 graph_mode 的兼容别名：
    # graph_mode 未传时由本字段驱动（向后兼容旧调用）；graph_mode != "off" 时等价生效。
    expand_prerequisites: bool = True
    # GraphRAG 自适应路由（向后兼容追加，可选）：
    #   None  = 旧调用兼容路径（行为完全等同历史版本，由 expand_prerequisites 驱动）；
    #   off   = 恒向量、不扩展；auto = 简单问走向量、多跳问走图；force = 恒走图扩展。
    # 语义默认 auto；字段层用 None 哨兵以严格区分「未传」与「显式 auto」，保证旧调用零行为漂移。
    graph_mode: GraphMode | None = None
    # 前置 KC 扩展跳数；router 夹到上界（MAX_PREREQ_HOPS=2），超界请求按上界裁剪而非拒绝。
    prereq_hops: int = Field(default=1, ge=1)


class RagQueryResponse(BaseModel):
    hits: list[RagHit] = Field(default_factory=list)
    # 无有效证据时为 True，下游不得伪造 page_id/页码引用
    no_evidence_found: bool = False
    rewritten_query: str | None = None
    # 检索引擎模式：hybrid（BM25+向量 RRF 融合）/ vector_only（BM25 缺失回退）/ graph（图扩展路）。
    retrieval_mode: Literal["hybrid", "vector_only", "graph"] | None = None
    # 自适应路由决策（可审计，向后兼容追加）：vector / graph / hybrid（旧 hybrid 管线）。
    route: Literal["vector", "graph", "hybrid"] | None = None
    # KC 关系子图（前置链路），仅图路由非空；供下游做「为什么先学 X」的可审计引用。
    subgraph: list[KcEdge] = Field(default_factory=list)


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
