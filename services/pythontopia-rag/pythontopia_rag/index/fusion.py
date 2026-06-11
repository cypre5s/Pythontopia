"""混合检索：向量 top-50 与 BM25 top-50 并行召回，RRF（k=60）融合取 top_k。

防幻觉门控保持在各自原生分数空间：向量候选沿用请求 min_score（余弦语义不变，
与纯向量时代校准一致），BM25 候选要求正分（查询词项至少命中一次）。融合分仅用于
排序与展示，归一到 0~1（双路第 1 名 = 1.0），不再用 min_score 二次过滤融合分。
BM25 索引缺失/为空时自动退回 vector_only，行为与历史纯向量检索一致。
"""

from __future__ import annotations

from typing import Literal, Sequence

from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.chunker import Chunk
from pythontopia_rag.index.embedder import Embedder
from pythontopia_rag.index.store import Hit, VectorStore

RRF_K = 60
CANDIDATE_POOL_SIZE = 50

RetrievalMode = Literal["hybrid", "vector_only"]


def rrf_scores(rankings: Sequence[Sequence[str]], *, k: int = RRF_K) -> dict[str, float]:
    """Reciprocal Rank Fusion：score(d) = Σ 1/(k + rank_d)，rank 从 1 起。"""
    scores: dict[str, float] = {}
    for ranking in rankings:
        for rank, doc_id in enumerate(ranking, start=1):
            scores[doc_id] = scores.get(doc_id, 0.0) + 1.0 / (k + rank)
    return scores


async def hybrid_search(
    *,
    query: str,
    embedder: Embedder,
    store: VectorStore,
    bm25: Bm25Index | None,
    top_k: int,
    min_score: float,
    kc_codes: list[str] | None = None,
    chapter_ids: list[int] | None = None,
) -> tuple[list[Hit], RetrievalMode]:
    """两路并行召回 + RRF 融合；返回 (hits, retrieval_mode)。

    hybrid 模式下 Hit.score 是归一化融合分（0~1]；vector_only 模式下保持余弦分，
    与历史响应位级兼容。
    """
    vectors = await embedder.embed([query])
    vector_hits: list[Hit] = []
    if vectors:
        candidates = await store.search(
            vectors[0],
            CANDIDATE_POOL_SIZE,
            kc_codes=kc_codes,
            chapter_ids=chapter_ids,
        )
        vector_hits = [hit for hit in candidates if hit.score >= min_score]

    if bm25 is None or bm25.built_chunks == 0:
        return vector_hits[:top_k], "vector_only"

    bm25_pairs = bm25.search(
        query,
        CANDIDATE_POOL_SIZE,
        kc_codes=kc_codes,
        chapter_ids=chapter_ids,
    )
    if not vector_hits and not bm25_pairs:
        return [], "hybrid"

    chunk_by_id: dict[str, Chunk] = {hit.chunk.chunk_id: hit.chunk for hit in vector_hits}
    for chunk_id, _ in bm25_pairs:
        if chunk_id not in chunk_by_id:
            chunk = bm25.chunk(chunk_id)
            if chunk is not None:
                chunk_by_id[chunk_id] = chunk

    fused = rrf_scores(
        [
            [hit.chunk.chunk_id for hit in vector_hits],
            [chunk_id for chunk_id, _ in bm25_pairs],
        ]
    )
    # 双路第 1 名的理论最大原始分为 2/(RRF_K+1)，以此归一到 0~1。
    max_possible = 2.0 / (RRF_K + 1)
    ranked = sorted(fused.items(), key=lambda item: (-item[1], item[0]))
    hits = [
        Hit(chunk=chunk_by_id[chunk_id], score=min(1.0, raw / max_possible))
        for chunk_id, raw in ranked[:top_k]
        if chunk_id in chunk_by_id
    ]
    return hits, "hybrid"
