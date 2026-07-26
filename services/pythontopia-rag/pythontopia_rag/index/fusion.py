"""混合检索：向量 top-50 与 BM25 top-50 并行召回，RRF（k=60）融合取 top_k。

防幻觉门控保持在各自原生分数空间：向量候选沿用请求 min_score（余弦语义不变，
与纯向量时代校准一致），BM25 候选要求**查询覆盖率 + 实义词命中**（仅"正分"不够——
CJK bigram 分词下任意中文问句都能蹭到正分，见 ``BM25_MIN_QUERY_COVERAGE``）。
两路（含前置扩展路）皆空才判定无证据。

融合分仅用于排序与展示：归一到 0~1，分母只统计**本次实际有结果**的召回路，
故它是"位次分"而非相关度；真实相关度由 ``Hit.vector_score`` / ``Hit.bm25_score``
原生分给出。BM25 索引缺失/为空时自动退回 vector_only，行为与历史纯向量检索一致。

KC 图谱感知扩展（R-16，GraphRAG 思想零抽取自建版）：查询锚定 kc_codes 且 KcGraph
可用时，沿前置边取 1 跳前置 KC 追加一对低权重召回路（权重 0.5），让「困惑根源在
前置知识」的页面也能进入证据——命中页带 ``via_prerequisite=True`` 标注，配额上限
``top_k // 3``，且前置候选必须在**原生分空间**不低于主路 top-1 的
``PREREQ_MIN_RELEVANCE_RATIO``（防不相关前置页白占预留席位）。
vector_only 退化路径**不**扩展（保持与历史响应位级兼容）。

证据多样性：同一 page_id 最多占 ``MAX_CHUNKS_PER_PAGE`` 个席位，正文完全相同的
chunk（7 份文档共用的封面页等）只保留一条，避免单页/重复页吃满 top_k。
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
# 前置候选的原生相关度下限（相对主路 top-1）：低于此比例视为"蹭配额"，退还席位给主题页。
PREREQ_MIN_RELEVANCE_RATIO = 0.4
# BM25 防幻觉门槛（与向量路的 min_score 同源，作用在 BM25 原生分空间）：
# 覆盖率 = bm25_score / Σ_t idf(t)·(k1+1)，即"查询全部词项都被打满"的理论上限占比；
# 分母含语料里根本不存在的词项（df=0 → idf 最大），故"问的大多是课件没有的东西"
# 会天然把覆盖率压到 0 附近。同时要求命中至少由一个实义词支撑（见 is_topical_term）。
#
# 0.08 是在 packId=43 真实语料（591 chunk）上扫出的拐点：15 条站外提问（天气/写诗/
# 菜谱/签证/理财/追剧/高铁票…）13 条降到 0 命中，46 条课程内提问只有「海龟绘图怎么用」
# 失去词法支撑（turtle/画图 字面不在课件正文，由向量路兜）。再往上抬到 0.13 才能拦住
# 剩下 2 条「邻域技术问」（Java+MySQL、篮球规则——课件里确实出现过这些词），
# 代价是「列表怎么用」「注释怎么写」「循环怎么写」等 8 条核心课程问句一并失去词法证据，
# 不划算。剩余 2 条泄漏的命中页确实字面包含被问词，属可解释证据而非幻觉。
BM25_MIN_QUERY_COVERAGE = 0.08
# BM25 尾部裁剪：候选还须达到本次查询最佳候选的该比例，避免"只蹭到一个边缘词"的页面
# 靠 RRF 位次混进证据（相对门槛不会让任何查询归零——最佳候选恒过线）。
BM25_RELATIVE_FLOOR = 0.35
# 同一 page_id 最多贡献的 chunk 数：滑窗 overlap 会把一页切成多个高度重叠的 chunk，
# 不限额时单页可吃掉大半 top_k 席位（实测最多一页 6 个 chunk）。
MAX_CHUNKS_PER_PAGE = 2

# 图扩展关系优先级：同一 chunk 可能同时挂前置 KC 与共现 KC（实测 33 页带 2 个 KC，
# 且 kc_codes 来自 array_agg 顺序不定）。必须按语义优先级选，不能取首个。
_RELATION_PRIORITY: dict[str, int] = {"prerequisite": 0}
_DEFAULT_RELATION_PRIORITY = 1

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
    expansion_relations: dict[str, str] | None = None,
) -> tuple[list[Hit], RetrievalMode]:
    """两路（+可选前置两路）召回 + 加权 RRF 融合；返回 (hits, retrieval_mode)。

    hybrid 模式下 Hit.score 是归一化融合位次分（0~1]，原生相关度见 vector_score /
    bm25_score；vector_only 模式下保持余弦分，与历史响应位级兼容。来自前置扩展的命中
    ``via_prerequisite=True``。
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

    bm25_pairs = _gated_bm25_search(
        bm25, query, CANDIDATE_POOL_SIZE, kc_codes=kc_codes, chapter_ids=chapter_ids
    )

    # ── KC 前置扩展路（仅 hybrid；查询未锚定 KC / 图为空时自然跳过）──
    prereq_codes: list[str] = []
    relation_by_kc: dict[str, str] = {}
    prereq_vector_hits: list[Hit] = []
    prereq_bm25_pairs: list[tuple[str, float]] = []
    if expansion_relations:
        relation_by_kc = {
            code: relation for code, relation in expansion_relations.items() if code
        }
        prereq_codes = list(relation_by_kc)
    elif expand_prerequisites and kc_codes and kc_graph is not None:
        prereq_codes = kc_graph.prerequisites_of(kc_codes)
        relation_by_kc = {code: "prerequisite" for code in prereq_codes}
    if prereq_codes:
        if query_vector is not None:
            prereq_candidates = await store.search(
                query_vector,
                PREREQ_POOL_SIZE,
                kc_codes=prereq_codes,
                chapter_ids=chapter_ids,
            )
            prereq_vector_hits = [hit for hit in prereq_candidates if hit.score >= min_score]
        prereq_bm25_pairs = _gated_bm25_search(
            bm25, query, PREREQ_POOL_SIZE, kc_codes=prereq_codes, chapter_ids=chapter_ids
        )

    # chunk 登记：主路优先（同 chunk 同时被前置路召回时按主路对待）。
    chunk_by_id: dict[str, Chunk] = {hit.chunk.chunk_id: hit.chunk for hit in vector_hits}
    vector_score_by_id: dict[str, float] = {
        hit.chunk.chunk_id: hit.score for hit in vector_hits
    }
    bm25_score_by_id: dict[str, float] = dict(bm25_pairs)
    main_ids = set(chunk_by_id)
    for chunk_id, _ in bm25_pairs:
        main_ids.add(chunk_id)
        if chunk_id not in chunk_by_id:
            chunk = bm25.chunk(chunk_id)
            if chunk is not None:
                chunk_by_id[chunk_id] = chunk

    # 前置候选的原生相关度门槛：不达主路 top-1 的既定比例就不占预留席位。
    prereq_vector_hits, prereq_bm25_pairs = _filter_prereq_by_relevance(
        prereq_vector_hits=prereq_vector_hits,
        prereq_bm25_pairs=prereq_bm25_pairs,
        main_ids=main_ids,
        best_vector=max((hit.score for hit in vector_hits), default=0.0),
        best_bm25=max((score for _, score in bm25_pairs), default=0.0),
    )

    if not vector_hits and not bm25_pairs and not prereq_vector_hits and not prereq_bm25_pairs:
        return [], "hybrid"

    prereq_only_ids: set[str] = set()
    relation_by_chunk_id: dict[str, str] = {}
    for hit in prereq_vector_hits:
        if hit.chunk.chunk_id not in chunk_by_id:
            chunk_by_id[hit.chunk.chunk_id] = hit.chunk
        vector_score_by_id.setdefault(hit.chunk.chunk_id, hit.score)
        if hit.chunk.chunk_id not in main_ids:
            prereq_only_ids.add(hit.chunk.chunk_id)
            relation = _relation_for_chunk(hit.chunk.kc_codes, relation_by_kc)
            if relation is not None:
                relation_by_chunk_id[hit.chunk.chunk_id] = relation
    for chunk_id, score in prereq_bm25_pairs:
        if chunk_id not in chunk_by_id:
            chunk = bm25.chunk(chunk_id)
            if chunk is not None:
                chunk_by_id[chunk_id] = chunk
        bm25_score_by_id.setdefault(chunk_id, score)
        if chunk_id not in main_ids:
            prereq_only_ids.add(chunk_id)
            chunk = chunk_by_id.get(chunk_id)
            if chunk is not None:
                relation = _relation_for_chunk(chunk.kc_codes, relation_by_kc)
                if relation is not None:
                    relation_by_chunk_id[chunk_id] = relation

    rankings: list[tuple[Sequence[str], float]] = [
        ([hit.chunk.chunk_id for hit in vector_hits], 1.0),
        ([chunk_id for chunk_id, _ in bm25_pairs], 1.0),
    ]
    if prereq_vector_hits or prereq_bm25_pairs:
        rankings.append(([hit.chunk.chunk_id for hit in prereq_vector_hits], PREREQ_WEIGHT))
        rankings.append(([chunk_id for chunk_id, _ in prereq_bm25_pairs], PREREQ_WEIGHT))

    fused = weighted_rrf_scores(rankings)
    # 归一化：分母只累加**本次实际有结果**的召回路权重。按"启用路数"归一会让单路场景
    # 的 rank-1 恒为 0.5、rank-2 恒 0.492……与查询内容无关（无关查询与主题查询拿到
    # 完全相同的分数序列），使 score 彻底失去可解释性。
    active_weight = sum(weight for ranking, weight in rankings if ranking)
    max_possible = active_weight / (RRF_K + 1) if active_weight > 0 else 1.0
    ranked = sorted(fused.items(), key=lambda item: (-item[1], item[0]))

    # 前置席位语义：前置是"补充证据"——配额 top_k//3（至少 1），且**预留席位**：
    # 前置路有候选时，主题页最多占 top_k - 预留数（否则前置权重 0.5 排名必然靠后，
    # 主题候选充足时前置永远进不了 top_k，扩展形同虚设）。两段各取后按融合分重排。
    prereq_quota = max(1, top_k // 3)
    page_counts: dict[int, int] = {}
    seen_texts: set[str] = set()
    selected_main = _select_diverse(
        [item for item in ranked if item[0] not in prereq_only_ids],
        chunk_by_id,
        limit=top_k,
        page_counts=page_counts,
        seen_texts=seen_texts,
    )
    selected_prereq = _select_diverse(
        [item for item in ranked if item[0] in prereq_only_ids],
        chunk_by_id,
        limit=prereq_quota,
        page_counts=page_counts,
        seen_texts=seen_texts,
    )
    reserved = len(selected_prereq)
    merged = sorted(
        selected_main[: max(0, top_k - reserved)] + selected_prereq,
        key=lambda item: (-item[1], item[0]),
    )

    hits: list[Hit] = []
    for chunk_id, raw in merged[:top_k]:
        chunk = chunk_by_id.get(chunk_id)
        if chunk is None:
            continue
        via_relation = relation_by_chunk_id.get(chunk_id)
        hits.append(
            Hit(
                chunk=chunk,
                score=min(1.0, raw / max_possible),
                via_prerequisite=via_relation == "prerequisite",
                via_relation=via_relation,
                vector_score=vector_score_by_id.get(chunk_id),
                bm25_score=bm25_score_by_id.get(chunk_id),
            )
        )
    return hits, "hybrid"


def _gated_bm25_search(
    bm25: Bm25Index,
    query: str,
    pool_size: int,
    *,
    kc_codes: list[str] | None,
    chapter_ids: list[int] | None,
) -> list[tuple[str, float]]:
    """BM25 召回 + 防幻觉门槛（与向量路 min_score 同源，作用在 BM25 原生分空间）。"""
    pairs = bm25.search(
        query,
        pool_size,
        kc_codes=kc_codes,
        chapter_ids=chapter_ids,
        min_query_coverage=BM25_MIN_QUERY_COVERAGE,
        require_topical_term=True,
    )
    if not pairs:
        return pairs
    floor = pairs[0][1] * BM25_RELATIVE_FLOOR
    return [(chunk_id, score) for chunk_id, score in pairs if score >= floor]


def _filter_prereq_by_relevance(
    *,
    prereq_vector_hits: list[Hit],
    prereq_bm25_pairs: list[tuple[str, float]],
    main_ids: set[str],
    best_vector: float,
    best_bm25: float,
) -> tuple[list[Hit], list[tuple[str, float]]]:
    """前置候选必须在至少一路原生分上达到主路 top-1 的既定比例，否则不占预留席位。

    主路完全无候选时没有参照系（前置就是唯一证据），全部放行。已在主路命中的 chunk
    不受此门槛约束——它本来就是主题页。
    """
    if best_vector <= 0.0 and best_bm25 <= 0.0:
        return prereq_vector_hits, prereq_bm25_pairs
    vector_floor = best_vector * PREREQ_MIN_RELEVANCE_RATIO
    bm25_floor = best_bm25 * PREREQ_MIN_RELEVANCE_RATIO
    qualified: set[str] = {
        hit.chunk.chunk_id for hit in prereq_vector_hits if hit.score >= vector_floor
    }
    qualified |= {chunk_id for chunk_id, score in prereq_bm25_pairs if score >= bm25_floor}
    return (
        [
            hit
            for hit in prereq_vector_hits
            if hit.chunk.chunk_id in qualified or hit.chunk.chunk_id in main_ids
        ],
        [
            (chunk_id, score)
            for chunk_id, score in prereq_bm25_pairs
            if chunk_id in qualified or chunk_id in main_ids
        ],
    )


def _select_diverse(
    ranked: list[tuple[str, float]],
    chunk_by_id: dict[str, Chunk],
    *,
    limit: int,
    page_counts: dict[int, int],
    seen_texts: set[str],
) -> list[tuple[str, float]]:
    """按融合分贪心取证据，同页限额 MAX_CHUNKS_PER_PAGE 且跳过正文完全重复的 chunk。

    ``page_counts`` / ``seen_texts`` 由调用方跨主路/前置路共享，保证两段之间也去冗。
    """
    selected: list[tuple[str, float]] = []
    for chunk_id, raw in ranked:
        if len(selected) >= limit:
            break
        chunk = chunk_by_id.get(chunk_id)
        if chunk is None:
            continue
        if page_counts.get(chunk.page_id, 0) >= MAX_CHUNKS_PER_PAGE:
            continue
        normalized_text = " ".join(chunk.text.split())
        if normalized_text and normalized_text in seen_texts:
            continue
        page_counts[chunk.page_id] = page_counts.get(chunk.page_id, 0) + 1
        seen_texts.add(normalized_text)
        selected.append((chunk_id, raw))
    return selected


def _relation_for_chunk(kc_codes: list[str], relation_by_kc: dict[str, str]) -> str | None:
    """按关系优先级（prerequisite > 其它）选主导关系，而非取 kc_codes 首个匹配。

    chunk.kc_codes 来自 ``array_agg(DISTINCT ...)``，顺序不定；取首个会让同时挂前置 KC
    与共现 KC 的页面随机丢掉前置标记（下游据此渲染「前置知识」提示）。
    """
    relations = [
        relation
        for code in kc_codes
        if (relation := relation_by_kc.get(code)) is not None and relation
    ]
    if not relations:
        return None
    return min(
        relations,
        key=lambda relation: (
            _RELATION_PRIORITY.get(relation, _DEFAULT_RELATION_PRIORITY),
            relation,
        ),
    )
