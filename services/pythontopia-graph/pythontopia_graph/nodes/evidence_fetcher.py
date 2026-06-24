from __future__ import annotations

import os
from typing import Literal, Protocol

import httpx
from pydantic import BaseModel, Field

# GraphRAG 检索路由开关（契约 §3.3）：off=恒向量；auto=自适应（简单走向量、多跳走图）；
# force=恒走图。运行时按字符串透传给 RAG 服务，非法值业务 fail-fast。
GraphMode = Literal["off", "auto", "force"]
_GRAPH_MODES: frozenset[str] = frozenset({"off", "auto", "force"})
# 前置 KC 扩展跳数上界（契约 §3.3「上界 2」）；客户端侧裁剪与 G1 端裁剪语义一致。
_MAX_PREREQ_HOPS = 2


def _clamp_prereq_hops(prereq_hops: int) -> int:
    """把前置扩展跳数裁剪进 [0, _MAX_PREREQ_HOPS]，防越界多跳拖垮 2C4G。"""

    return max(0, min(prereq_hops, _MAX_PREREQ_HOPS))


def _ensure_graph_mode(graph_mode: str) -> None:
    """非法 graph_mode 直接 fail-fast（不发起请求），避免静默走错检索路。"""

    if graph_mode not in _GRAPH_MODES:
        raise ValueError(
            f"graph_mode must be one of {sorted(_GRAPH_MODES)}, got {graph_mode!r}"
        )


class RagHit(BaseModel):
    chunk_id: str
    page_id: int
    page_no: int
    chapter_id: int | None = None
    kc_codes: list[str] = Field(default_factory=list)
    text: str
    score: float
    # KC 图谱感知扩展（R-16）：该证据来自前置 KC 召回路（学生困惑可能源于前置知识）。
    via_prerequisite: bool = False
    # 图扩展命中的主导关系；None 表示直接命中。用于 UI/审计说明，不改变 grounding 边界。
    via_relation: str | None = None


class KcEdge(BaseModel):
    """KC 知识图谱有向边（GraphRAG 子图审计单元，契约 §3.3）。

    from_kc/to_kc 为 KC 编码端点（缺一即为畸形边，Pydantic 校验 fail-fast）；
    relation 缺省取主导关系 ``prerequisite``，避免次级审计字段缺失拖垮主链路解析。
    """

    from_kc: str
    to_kc: str
    relation: str = "prerequisite"


class EvidenceBundle(BaseModel):
    chunks: list[RagHit] = Field(default_factory=list)
    no_evidence_found: bool = False
    # GraphRAG 可审计字段（契约 §3.3）：route=本次检索实际走的路（vector|graph|hybrid）；
    # subgraph=多跳前置链路边集，供 grounding_critic 做「为什么先学 X」的可审计解释，
    # 但前置页不得冒充直接证据出处（由 G2b grounding_critic 把关）。
    route: str = "vector"
    retrieval_mode: str = "vector_only"
    subgraph: list[KcEdge] = Field(default_factory=list)
    graph_trace: dict[str, object] = Field(default_factory=dict)

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
        self,
        *,
        kc_codes: list[str],
        query: str,
        top_k: int = 6,
        graph_mode: GraphMode = "auto",
        prereq_hops: int = 1,
    ) -> EvidenceBundle: ...


class StaticEvidenceFetcher:
    """测试用确定性 fetcher，基于内存 RagHit 列表。

    同步支持 GraphRAG 新字段（route/subgraph）便于兄弟单元（S1/G2b）注入；
    并记录每次 fetch 入参（含 graph_mode/prereq_hops）便于断言上游透传。
    """

    def __init__(
        self,
        hits: list[RagHit],
        *,
        route: str = "vector",
        retrieval_mode: str = "vector_only",
        subgraph: list[KcEdge] | None = None,
        graph_trace: dict[str, object] | None = None,
    ) -> None:
        self._hits = hits
        self._route = route
        self._retrieval_mode = retrieval_mode
        self._subgraph = list(subgraph or [])
        self._graph_trace = dict(graph_trace or {})
        self.calls: list[dict[str, object]] = []

    async def fetch(
        self,
        *,
        kc_codes: list[str],
        query: str,
        top_k: int = 6,
        graph_mode: GraphMode = "auto",
        prereq_hops: int = 1,
    ) -> EvidenceBundle:
        self.calls.append(
            {
                "kc_codes": list(kc_codes),
                "query": query,
                "top_k": top_k,
                "graph_mode": graph_mode,
                "prereq_hops": prereq_hops,
            }
        )
        if kc_codes:
            filtered = [
                hit
                for hit in self._hits
                if any(code in hit.kc_codes for code in kc_codes)
                or hit.via_relation is not None
                or hit.via_prerequisite
            ]
        else:
            filtered = list(self._hits)
        return EvidenceBundle(
            chunks=filtered[:top_k],
            no_evidence_found=not filtered,
            route=self._route,
            retrieval_mode=self._retrieval_mode,
            subgraph=list(self._subgraph),
            graph_trace=dict(self._graph_trace),
        )


class HttpRagEvidenceFetcher:
    """经 HTTP 调用 Pythontopia RAG 服务的 EvidenceFetcher。"""

    def __init__(self, base_url: str | None = None, timeout: float = 5.0) -> None:
        self._base_url = (base_url or os.environ.get("PYT_RAG_URL", "http://localhost:18092")).rstrip("/")
        self._timeout = timeout

    async def fetch(
        self,
        *,
        kc_codes: list[str],
        query: str,
        top_k: int = 6,
        graph_mode: GraphMode = "auto",
        prereq_hops: int = 1,
    ) -> EvidenceBundle:
        # 非法 graph_mode fail-fast（在发请求前），前置跳数裁剪进上界。
        _ensure_graph_mode(graph_mode)
        # 有 kc_codes 时 min_score=0（KC 已过滤，hash 向量对会话 query 余弦偏低，高阈值会误成 no_evidence）；无 KC 时用 0.15 抑噪。
        min_score = 0.0 if kc_codes else 0.15
        payload = {
            "query": query,
            "top_k": top_k,
            "kc_codes": list(kc_codes),
            "min_score": min_score,
            "graph_mode": graph_mode,
            "prereq_hops": _clamp_prereq_hops(prereq_hops),
        }
        async with httpx.AsyncClient(timeout=self._timeout, trust_env=False) as client:
            response = await client.post(f"{self._base_url}/rag/query", json=payload)
        response.raise_for_status()
        body = response.json()
        chunks = [RagHit(**hit) for hit in body.get("hits", [])]
        # 新字段向后兼容：旧版 RAG 不返回 route/subgraph 时回退 vector / 空子图；
        # 显式 null 也回退 vector，避免 str(None) 污染审计。
        route = str(body.get("route") or "vector")
        retrieval_mode = str(body.get("retrieval_mode") or "vector_only")
        subgraph = [KcEdge(**edge) for edge in (body.get("subgraph") or [])]
        graph_trace_raw = body.get("graph_trace")
        graph_trace = graph_trace_raw if isinstance(graph_trace_raw, dict) else {}
        return EvidenceBundle(
            chunks=chunks,
            no_evidence_found=bool(body.get("no_evidence_found")),
            route=route,
            retrieval_mode=retrieval_mode,
            subgraph=subgraph,
            graph_trace=graph_trace,
        )
