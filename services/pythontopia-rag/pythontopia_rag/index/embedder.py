from __future__ import annotations

import hashlib
import math
import os
from typing import Protocol

DEFAULT_DIM = 1024


class Embedder(Protocol):
    name: str
    dim: int

    async def embed(self, texts: list[str]) -> list[list[float]]:  # pragma: no cover - protocol
        ...


class HashEmbedder:
    """无外部依赖的确定性嵌入：字符 n-gram 哈希为固定维单位向量，供测试与离线 CI；生产环境用 BgeSmallEmbedder。"""

    name = "hash-bow-1024"
    dim = DEFAULT_DIM

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [self._embed_one(text) for text in texts]

    def _embed_one(self, text: str) -> list[float]:
        vector = [0.0] * self.dim
        if not text:
            return vector
        for token in self._tokens(text):
            digest = hashlib.sha1(token.encode("utf-8")).digest()
            index = int.from_bytes(digest[:4], "big") % self.dim
            sign = 1.0 if digest[4] % 2 == 0 else -1.0
            vector[index] += sign
        norm = math.sqrt(sum(x * x for x in vector))
        if norm <= 0:
            return vector
        return [x / norm for x in vector]

    @staticmethod
    def _tokens(text: str) -> list[str]:
        cleaned = text.lower()
        cjk_bigrams = _cjk_bigrams(cleaned)
        trigrams = [cleaned[i : i + 3] for i in range(0, max(0, len(cleaned) - 2))]
        words = cleaned.split()
        return cjk_bigrams + trigrams + words


def _cjk_bigrams(text: str) -> list[str]:
    spans = "".join(char if "\u4e00" <= char <= "\u9fff" else " " for char in text)
    bigrams: list[str] = []
    for span in spans.split():
        bigrams.extend(span[index : index + 2] for index in range(0, max(0, len(span) - 1)))
    return bigrams


class BgeSmallEmbedder:
    """可选 BGE 中文嵌入，依赖 sentence-transformers；生产环境设 PYT_RAG_EMBEDDER=bge 启用。"""

    name = "bge-small-zh-v1.5"
    dim = 512

    def __init__(self, model_dir: str | None = None) -> None:
        from sentence_transformers import SentenceTransformer  # type: ignore[import-not-found]

        target = model_dir or os.environ.get("PYT_RAG_BGE_MODEL_DIR", "BAAI/bge-small-zh-v1.5")
        self._model = SentenceTransformer(target)

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [vector.tolist() for vector in self._model.encode(texts, normalize_embeddings=True)]


class IflytekSparkEmbedder:
    """讯飞嵌入占位实现，接入前调用即抛 NotImplementedError。"""

    name = "iflytek-spark-embedding"
    dim = 1024

    async def embed(self, _texts: list[str]) -> list[list[float]]:
        raise NotImplementedError("讯飞 Embedding 将在阶段 11 接入")


def select_embedder() -> Embedder:
    kind = os.environ.get("PYT_RAG_EMBEDDER", "hash").lower()
    if kind == "hash":
        return HashEmbedder()
    if kind == "bge":
        return BgeSmallEmbedder()
    if kind == "iflytek":
        return IflytekSparkEmbedder()
    raise ValueError(f"Unknown PYT_RAG_EMBEDDER: {kind}")
