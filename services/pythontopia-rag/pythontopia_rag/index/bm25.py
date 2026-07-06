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
    ) -> list[tuple[str, float]]:
        """返回 [(chunk_id, bm25_score)]，仅含正分（query 词项至少命中一次）。"""
        if top_k <= 0 or not self._docs or self._avgdl <= 0:
            return []
        doc_count = len(self._docs)
        scores: dict[int, float] = {}
        for term in tokenize(query):
            posting = self._postings.get(term)
            if not posting:
                continue
            df = len(posting)
            idf = math.log(1.0 + (doc_count - df + 0.5) / (df + 0.5))
            for doc_index, term_freq in posting.items():
                doc = self._docs[doc_index]
                norm = self.k1 * (1.0 - self.b + self.b * doc.length / self._avgdl)
                gain = idf * term_freq * (self.k1 + 1.0) / (term_freq + norm)
                scores[doc_index] = scores.get(doc_index, 0.0) + gain

        ranked: list[tuple[str, float]] = []
        for doc_index, score in scores.items():
            if score <= 0.0:
                continue
            chunk = self._docs[doc_index].chunk
            if kc_codes and not _intersect(chunk.kc_codes, kc_codes):
                continue
            if chapter_ids and chunk.chapter_id not in chapter_ids:
                continue
            ranked.append((chunk.chunk_id, score))
        ranked.sort(key=lambda pair: (-pair[1], pair[0]))
        return ranked[:top_k]


def _intersect(left: Iterable[str], right: Iterable[str]) -> bool:
    right_set = set(right)
    return any(value in right_set for value in left)
