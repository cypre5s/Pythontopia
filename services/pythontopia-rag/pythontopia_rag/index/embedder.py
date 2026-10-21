from __future__ import annotations

import hashlib
import logging
import math
import os
from typing import Protocol


DEFAULT_DIM = 1024

log = logging.getLogger(__name__)


class Embedder(Protocol):
    name: str
    dim: int

    async def embed(self, texts: list[str]) -> list[list[float]]:  # pragma: no cover - protocol
        ...


class HashEmbedder:
    """无外部依赖的确定性嵌入：字符 n-gram 哈希为固定维单位向量，供测试、离线 CI 与 API 兜底。"""

    name = "hash-bow-1024"
    dim = DEFAULT_DIM
    using_fallback = True
    fallback_reason = "non-semantic hash embedder"

    def __init__(self, dim: int = DEFAULT_DIM, name: str | None = None) -> None:
        self.dim = dim
        self.name = name or f"hash-bow-{dim}"

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
    """可选本地 BGE 中文嵌入；只读缓存/本地权重，不从 Hugging Face 下载。"""

    name = "bge-small-zh-v1.5"
    dim = 512

    def __init__(self, model_dir: str | None = None) -> None:
        from sentence_transformers import SentenceTransformer  # type: ignore[import-not-found]

        target = model_dir or os.environ.get("PYT_RAG_BGE_MODEL_DIR", "BAAI/bge-small-zh-v1.5")
        self._model = SentenceTransformer(target, local_files_only=True)

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [vector.tolist() for vector in self._model.encode(texts, normalize_embeddings=True)]


class IflytekSparkEmbedder:
    """讯飞嵌入占位实现，接入前调用即抛 NotImplementedError。"""

    name = "iflytek-spark-embedding"
    dim = 1024

    async def embed(self, _texts: list[str]) -> list[list[float]]:
        raise NotImplementedError("讯飞 Embedding 将在阶段 11 接入")


class FallbackEmbedder:
    """优先使用真实语义向量；失败后在本进程内固定切到同维 hash，避免 API 过期拖垮检索链路。"""

    def __init__(
        self,
        *,
        primary: Embedder | None,
        fallback: Embedder,
        dim: int,
        name: str,
        fallback_reason: str | None = None,
    ) -> None:
        self._primary = primary
        self._fallback = fallback
        self._fallback_reason: str | None = fallback_reason
        self.dim = dim
        self.name = name

    @property
    def fallback_reason(self) -> str | None:
        return self._fallback_reason

    @property
    def using_fallback(self) -> bool:
        return self._primary is None or self._fallback_reason is not None

    async def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        if self._primary is not None and self._fallback_reason is None:
            try:
                vectors = await self._primary.embed(texts)
                self._validate(vectors, len(texts), self._primary.name)
                return vectors
            except Exception as exc:  # noqa: BLE001 - 兜底边界需要吞掉 provider 细节
                error_type = type(exc).__name__
                self._fallback_reason = error_type
                log.warning("embedding primary failed; switching to hash fallback: %s", error_type)
        vectors = await self._fallback.embed(texts)
        self._validate(vectors, len(texts), self._fallback.name)
        return vectors

    def _validate(self, vectors: list[list[float]], expected_count: int, model: str) -> None:
        if len(vectors) != expected_count:
            raise ValueError(
                f"{model} returned {len(vectors)} vectors for {expected_count} inputs"
            )
        bad_dims = [len(vector) for vector in vectors if len(vector) != self.dim]
        if bad_dims:
            raise ValueError(f"{model} returned dim {bad_dims[0]}, expected {self.dim}")


def select_embedder() -> Embedder:
    kind = os.environ.get("PYT_RAG_EMBEDDER", "hash").lower()
    if kind == "hash":
        return HashEmbedder(dim=_configured_embedding_dim(DEFAULT_DIM))
    if kind == "bge":
        return BgeSmallEmbedder()
    if kind in {
        "iflytek",
        "zhipu",
        "embedding-3",
        "glm",
        "zhipu-strict",
        "embedding-3-strict",
    }:
        raise ValueError("Only local hash and BGE embedders are supported")
    raise ValueError(f"Unknown PYT_RAG_EMBEDDER: {kind}")
