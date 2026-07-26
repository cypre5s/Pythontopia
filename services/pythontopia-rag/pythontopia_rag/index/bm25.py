"""纯 Python BM25Okapi 词法检索，零新依赖，与向量检索并行供 RRF 融合。

分词复用 embedder.py 的 _tokens/_cjk_bigrams 思路：英文/代码标识符按词保留原词
（print、for、input、len 等不可拆），中文按 bigram；与 HashEmbedder 不同的是
不引入字符 trigram（对词法精确匹配是噪声），并用正则抽词以剥离标点
（"print(" 必须命中查询 "print"）。
"""

from __future__ import annotations

import math
import re
from collections import Counter
from dataclasses import dataclass, field
from typing import Iterable

from pythontopia_rag.index.chunker import Chunk

DEFAULT_K1 = 1.5
DEFAULT_B = 0.75

_WORD_RE = re.compile(r"[a-z0-9_]+")
_CJK_RE = re.compile(r"[\u4e00-\u9fff]+")
# Python 运算符（==、!=、//、** 等）是课程一等检索对象，按连续运算符串保留为词项。
_OPERATOR_RE = re.compile(r"[=!<>+\-*/%]+")

# 中文虚词/疑问词字表：CJK bigram 分词会把「如何用」「有什么」这类问句骨架切成词项，
# 而课件是陈述文体、问句虚词在语料里天然稀有 → idf 虚高，任意中文提问都能"命中"课件。
# 该字表仅用于**召回门槛**判定（一条命中是否由实义词支撑），不参与分词与 BM25 打分，
# 因此不损失召回，只影响 fusion 层是否采信该候选。
_FUNCTION_CHARS = frozenset(
    "的了是在和与我你他她它这那些什么怎样为何如吗呢吧请帮给多少几个用于把被就都也还有没"
    "不很更最可以能会要想问哪谁呀啊嘛之其而且但或者到从向对里中上下前后时候一二三下面下列"
)


def is_topical_term(term: str) -> bool:
    """实义词判定：非中文词项（标识符/运算符/数字）恒为实义；中文词项至少含一个非虚词字。"""
    if not term:
        return False
    if not all("一" <= char <= "鿿" for char in term):
        return True
    return any(char not in _FUNCTION_CHARS for char in term)


def tokenize(text: str) -> list[str]:
    """英文/数字/下划线按词（小写），中文连续段切 bigram（单字段保留单字），运算符串保留。"""
    cleaned = text.lower()
    tokens: list[str] = _WORD_RE.findall(cleaned)
    tokens.extend(_OPERATOR_RE.findall(cleaned))
    for span in _CJK_RE.findall(cleaned):
        if len(span) == 1:
            tokens.append(span)
        else:
            tokens.extend(span[index : index + 2] for index in range(len(span) - 1))
    return tokens


@dataclass
class _DocEntry:
    chunk: Chunk
    length: int


@dataclass
class Bm25Index:
    """BM25Okapi（k1=1.5, b=0.75）内存倒排索引。

    idf 采用 Lucene 变体 ln(1 + (N - df + 0.5)/(df + 0.5))，恒为正，
    避免经典 Okapi 在 df > N/2 时出现负 idf 需要 epsilon 修补的问题。
    """

    k1: float = DEFAULT_K1
    b: float = DEFAULT_B
    _docs: list[_DocEntry] = field(default_factory=list)
    _postings: dict[str, dict[int, int]] = field(default_factory=dict)
    _chunk_ids: dict[str, int] = field(default_factory=dict)
    _avgdl: float = 0.0

    @property
    def built_chunks(self) -> int:
        return len(self._docs)

    def clear(self) -> None:
        self._docs = []
        self._postings = {}
        self._chunk_ids = {}
        self._avgdl = 0.0

    def build(self, chunks: list[Chunk]) -> None:
        """全量重建（替换旧索引）；分词对象是 chunk.index_text（含上下文前缀的检索文本）。"""
        self.clear()
        for doc_index, chunk in enumerate(chunks):
            tokens = tokenize(chunk.index_text)
            self._docs.append(_DocEntry(chunk=chunk, length=len(tokens)))
            self._chunk_ids[chunk.chunk_id] = doc_index
            for term, term_freq in Counter(tokens).items():
                self._postings.setdefault(term, {})[doc_index] = term_freq
        total_length = sum(doc.length for doc in self._docs)
        self._avgdl = total_length / len(self._docs) if self._docs else 0.0

    def chunk(self, chunk_id: str) -> Chunk | None:
        doc_index = self._chunk_ids.get(chunk_id)
        if doc_index is None:
            return None
        return self._docs[doc_index].chunk

    def search(
        self,
        query: str,
        top_k: int,
        *,
        kc_codes: list[str] | None = None,
        chapter_ids: list[int] | None = None,
        min_query_coverage: float = 0.0,
        require_topical_term: bool = False,
    ) -> list[tuple[str, float]]:
        """返回 [(chunk_id, bm25_score)]，仅含正分（query 词项至少命中一次）。

        ``min_query_coverage`` / ``require_topical_term`` 是**可选的相关度门槛**（默认关闭，
        保持纯排序器语义；召回策略由 ``index/fusion.py`` 注入）：

        - ``min_query_coverage``：要求 ``bm25_score / Σ_t idf(t)·(k1+1) >= 阈值``。分母是本次
          查询所有 distinct 词项（含语料里根本不存在的词项，其 df=0 → idf 最大）都被"打满"
          时的理论上限，故该比值是**查询覆盖率**：只命中问句零碎词的候选自然趋近 0。
        - ``require_topical_term``：要求至少一个命中词项是实义词（见 ``is_topical_term``），
          防止「如何用/有什么」这类问句骨架单独把无关页面顶成"课件证据"。
        """
        if top_k <= 0 or not self._docs or self._avgdl <= 0:
            return []
        doc_count = len(self._docs)
        gated = min_query_coverage > 0.0 or require_topical_term
        scores: dict[int, float] = {}
        matched_terms: dict[int, list[str]] = {}
        coverage_ceiling = 0.0
        for term in dict.fromkeys(tokenize(query)):
            posting = self._postings.get(term)
            df = len(posting) if posting else 0
            idf = math.log(1.0 + (doc_count - df + 0.5) / (df + 0.5))
            coverage_ceiling += idf * (self.k1 + 1.0)
            if not posting:
                continue
            for doc_index, term_freq in posting.items():
                doc = self._docs[doc_index]
                norm = self.k1 * (1.0 - self.b + self.b * doc.length / self._avgdl)
                gain = idf * term_freq * (self.k1 + 1.0) / (term_freq + norm)
                scores[doc_index] = scores.get(doc_index, 0.0) + gain
                if gated:
                    matched_terms.setdefault(doc_index, []).append(term)

        ranked: list[tuple[str, float]] = []
        for doc_index, score in scores.items():
            if score <= 0.0:
                continue
            if gated and not self._passes_gate(
                score=score,
                terms=matched_terms.get(doc_index, []),
                coverage_ceiling=coverage_ceiling,
                min_query_coverage=min_query_coverage,
                require_topical_term=require_topical_term,
            ):
                continue
            chunk = self._docs[doc_index].chunk
            if kc_codes and not _intersect(chunk.kc_codes, kc_codes):
                continue
            if chapter_ids and chunk.chapter_id not in chapter_ids:
                continue
            ranked.append((chunk.chunk_id, score))
        ranked.sort(key=lambda pair: (-pair[1], pair[0]))
        return ranked[:top_k]

    @staticmethod
    def _passes_gate(
        *,
        score: float,
        terms: list[str],
        coverage_ceiling: float,
        min_query_coverage: float,
        require_topical_term: bool,
    ) -> bool:
        if require_topical_term and not any(is_topical_term(term) for term in terms):
            return False
        if min_query_coverage <= 0.0:
            return True
        if coverage_ceiling <= 0.0:
            return False
        return score / coverage_ceiling >= min_query_coverage


def _intersect(left: Iterable[str], right: Iterable[str]) -> bool:
    right_set = set(right)
    return any(value in right_set for value in left)
