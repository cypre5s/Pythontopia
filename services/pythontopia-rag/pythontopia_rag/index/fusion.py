"""混合检索：向量 top-50 与 BM25 top-50 并行召回，RRF（k=60）融合取 top_k。

防幻觉门控保持在各自原生分数空间：向量候选沿用请求 min_score（余弦语义不变，
与纯向量时代校准一致），BM25 候选要求正分（查询词项至少命中一次）。融合分仅用于
排序与展示，归一到 0~1（按启用路数的权重和动态归一，双路第 1 名 = 1.0），不再用
min_score 二次过滤融合分。BM25 索引缺失/为空时自动退回 vector_only，行为与历史
纯向量检索一致。

KC 图谱感知扩展（R-16，GraphRAG 思想零抽取自建版）：查询锚定 kc_codes 且 KcGraph
可用时，沿前置边取 1 跳前置 KC 追加一对低权重召回路（权重 0.5），让「困惑根源在
前置知识」的页面也能进入证据——命中页带 ``via_prerequisite=True`` 标注，且配额上限
``top_k // 3``（防前置淹没主题页）。vector_only 退化路径**不**扩展（保持与历史响应
位级兼容）。
"""

from __future__ import annotations

from typing import Literal, Sequence

from pythontopia_rag.index.bm25 import Bm25Index
from pythontopia_rag.index.chunker import Chunk
from pythontopia_rag.index.embedder import Embedder
from pythontopia_rag.index.kc_graph import KcGraph
from pythontopia_rag.index.store import Hit, VectorStore

RRF_K = 60
CANDIDATE_POOL_SIZE = 50
# 前置扩展路：召回池减半、RRF 权重打折、结果配额 top_k//3——前置是"补充证据"不是主角。
PREREQ_POOL_SIZE = 20
PREREQ_WEIGHT = 0.5

RetrievalMode = Literal["hybrid", "vector_only"]


def rrf_scores(rankings: Sequence[Sequence[str]], *, k: int = RRF_K) -> dict[str, float]:
    """Reciprocal Rank Fusion：score(d) = Σ 1/(k + rank_d)，rank 从 1 起。"""
    return weighted_rrf_scores([(ranking, 1.0) for ranking in rankings], k=k)


def weighted_rrf_scores(
    rankings: Sequence[tuple[Sequence[str], float]], *, k: int = RRF_K
) -> dict[str, float]:
    """加权 RRF：score(d) = Σ weight_r / (k + rank_d)。权重=1.0 时与经典 RRF 一致。"""
    scores: dict[str, float] = {}
    for ranking, weight in rankings:
        for rank, doc_id in enumerate(ranking, start=1):
            scores[doc_id] = scores.get(doc_id, 0.0) + weight / (k + rank)
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
    kc_graph: KcGraph | None = None,
    expand_prerequisites: bool = True,
) -> tuple[list[Hit], RetrievalMode]:
    """两路（+可选前置两路）召回 + 加权 RRF 融合；返回 (hits, retrieval_mode)。

    hybrid 模式下 Hit.score 是归一化融合分（0~1]；vector_only 模式下保持余弦分，
    与历史响应位级兼容。来自前置扩展的命中 ``via_prerequisite=True``。
    """
    vectors = await embedder.embed([query])
    query_vector = vectors[0] if vectors else None

    vector_hits: list[Hit] = []
    if query_vector is not None:
        candidates = await store.search(
            query_vector,
            CANDIDATE_POOL_SIZE,
            kc_codes=kc_codes,
            chapter_ids=chapter_ids,
        )
        vector_hits = [hit for hit in candidates if hit.score >= min_score]

    if bm25 is None or bm25.built_chunks == 0:
        # 历史纯向量退化路径：位级兼容，不做前置扩展。
        return vector_hits[:top_k], "vector_only"

    bm25_pairs = bm25.search(
        query,
        CANDIDATE_POOL_SIZE,
        kc_codes=kc_codes,
        chapter_ids=chapter_ids,
    )

    # ── KC 前置扩展路（仅 hybrid；查询未锚定 KC / 图为空时自然跳过）──
    prereq_codes: list[str] = []
    prereq_vector_hits: list[Hit] = []
    prereq_bm25_pairs: list[tuple[str, float]] = []
    if expand_prerequisites and kc_codes and kc_graph is not None:
        prereq_codes = kc_graph.prerequisites_of(kc_codes)
    if prereq_codes:
        if query_vector is not None:
            prereq_candidates = await store.search(
                query_vector,
                PREREQ_POOL_SIZE,
                kc_codes=prereq_codes,
                chapter_ids=chapter_ids,
            )
            prereq_vector_hits = [hit for hit in prereq_candidates if hit.score >= min_score]
        prereq_bm25_pairs = bm25.search(
            query,
            PREREQ_POOL_SIZE,
            kc_codes=prereq_codes,
            chapter_ids=chapter_ids,
        )

    if not vector_hits and not bm25_pairs and not prereq_vector_hits and not prereq_bm25_pairs:
        return [], "hybrid"

    # chunk 登记：主路优先（同 chunk 同时被前置路召回时按主路对待）。
    chunk_by_id: dict[str, Chunk] = {hit.chunk.chunk_id: hit.chunk for hit in vector_hits}
    main_ids = set(chunk_by_id)
    for chunk_id, _ in bm25_pairs:
        main_ids.add(chunk_id)
        if chunk_id not in chunk_by_id:
            chunk = bm25.chunk(chunk_id)
            if chunk is not None:
                chunk_by_id[chunk_id] = chunk
    prereq_only_ids: set[str] = set()
    for hit in prereq_vector_hits:
        if hit.chunk.chunk_id not in chunk_by_id:
            chunk_by_id[hit.chunk.chunk_id] = hit.chunk
        if hit.chunk.chunk_id not in main_ids:
            prereq_only_ids.add(hit.chunk.chunk_id)
    for chunk_id, _ in prereq_bm25_pairs:
        if chunk_id not in chunk_by_id:
            chunk = bm25.chunk(chunk_id)
            if chunk is not None:
                chunk_by_id[chunk_id] = chunk
        if chunk_id not in main_ids:
            prereq_only_ids.add(chunk_id)

    rankings: list[tuple[Sequence[str], float]] = [
        ([hit.chunk.chunk_id for hit in vector_hits], 1.0),
        ([chunk_id for chunk_id, _ in bm25_pairs], 1.0),
    ]
    if prereq_vector_hits or prereq_bm25_pairs:
        rankings.append(([hit.chunk.chunk_id for hit in prereq_vector_hits], PREREQ_WEIGHT))
        rankings.append(([chunk_id for chunk_id, _ in prereq_bm25_pairs], PREREQ_WEIGHT))

    fused = weighted_rrf_scores(rankings)
    # 归一化：分母按启用路数的权重和动态计算——无前置路时 = 2/(k+1)，与历史位级一致。
    max_possible = sum(weight for _, weight in rankings) / (RRF_K + 1)
    ranked = sorted(fused.items(), key=lambda item: (-item[1], item[0]))

    # 前置席位语义：前置是"补充证据"——配额 top_k//3（至少 1），且**预留席位**：
    # 前置路有候选时，主题页最多占 top_k - 预留数（否则前置权重 0.5 排名必然靠后，
    # 主题候选充足时前置永远进不了 top_k，扩展形同虚设）。两段各取后按融合分重排。
    prereq_quota = max(1, top_k // 3)
    ranked_prereq = [
        (chunk_id, raw) for chunk_id, raw in ranked if chunk_id in prereq_only_ids
    ][:prereq_quota]
    reserved = len(ranked_prereq)
    ranked_main = [
        (chunk_id, raw) for chunk_id, raw in ranked if chunk_id not in prereq_only_ids
    ][: top_k - reserved]

    merged = sorted(ranked_main + ranked_prereq, key=lambda item: (-item[1], item[0]))
    hits: list[Hit] = []
    for chunk_id, raw in merged[:top_k]:
        chunk = chunk_by_id.get(chunk_id)
        if chunk is None:
            continue
        hits.append(
            Hit(
                chunk=chunk,
                score=min(1.0, raw / max_possible),
                via_prerequisite=chunk_id in prereq_only_ids,
            )
        )
    return hits, "hybrid"
