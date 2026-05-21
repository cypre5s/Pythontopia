from __future__ import annotations

import hashlib
import math
import os
from typing import Protocol

DEFAULT_DIM = 384


class Embedder(Protocol):
    name: str
    dim: int

    async def embed(self, texts: list[str]) -> list[list[float]]:  # pragma: no cover - protocol
        ...


class HashEmbedder:
    """Deterministic embedding without external dependencies.

    Each text is hashed into a fixed-dimension unit vector using char-trigram features.
    Two semantically similar Chinese strings tend to produce overlapping trigram hashes,
    which is sufficient for stable tests and offline CI use. Real production should use
    ``BgeSmallEmbedder``.
    """

    name = "hash-bow-384"
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
        trigrams = [cleaned[i : i + 3] for i in range(0, max(0, len(cleaned) - 2))]
        words = cleaned.split()
        return trigrams + words


class BgeSmallEmbedder:
    """Optional BGE small Chinese embedder; requires ``sentence-transformers``.

    Construction lazily imports the dependency and downloads ``BAAI/bge-small-zh-v1.5``.
    Disabled by default in CI. Used in production by setting ``PYT_RAG_EMBEDDER=bge``.
    """

    name = "bge-small-zh-v1.5"
    dim = 512

    def __init__(self, model_dir: str | None = None) -> None:
        from sentence_transformers import SentenceTransformer  # type: ignore[import-not-found]

        target = model_dir or os.environ.get("PYT_RAG_BGE_MODEL_DIR", "BAAI/bge-small-zh-v1.5")
        self._model = SentenceTransformer(target)

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [vector.tolist() for vector in self._model.encode(texts, normalize_embeddings=True)]


class IflytekSparkEmbedder:
    """Phase 11 placeholder; intentionally raises until iFlytek provider is wired."""

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
