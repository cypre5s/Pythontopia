from __future__ import annotations

import hashlib
import logging
import math
import os
from typing import Any, Protocol

import httpx

DEFAULT_DIM = 1024
# 智谱 embedding-3 默认输出维度，便于本项目各向量链路复用同一向量空间。
ZHIPU_DEFAULT_DIM = 2048
ZHIPU_DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
ZHIPU_DEFAULT_MODEL = "embedding-3"

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


def _env(*names: str, default: str = "") -> str:
    """按优先级取首个非空环境变量；PYT_RAG_* 覆盖在前，通用 EMBEDDING_* 在后。"""
    for name in names:
        value = os.environ.get(name)
        if value is not None and value.strip():
            return value.strip()
    return default


class ZhipuEmbedder:
    """智谱开放平台 embedding-3（OpenAI 兼容 /embeddings 协议）真实语义向量。

    读取 EMBEDDING_API_KEY / EMBEDDING_BASE_URL / EMBEDDING_MODEL / EMBEDDING_DIM，
    亦可用 PYT_RAG_EMBEDDING_* 覆盖。生产设 PYT_RAG_EMBEDDER=zhipu 启用；离线 / CI
    仍用 HashEmbedder 兜底，避免无 key 环境构建失败。

    向量库为 InMemoryVectorStore（维度随首批向量自适应），故切换到 2048 维只需重建索引，
    无需迁移表结构。
    """

    name = "zhipu-embedding-3"

    def __init__(self) -> None:
        self.api_key = _env("PYT_RAG_EMBEDDING_API_KEY", "EMBEDDING_API_KEY")
        self.base_url = _env(
            "PYT_RAG_EMBEDDING_BASE_URL", "EMBEDDING_BASE_URL", default=ZHIPU_DEFAULT_BASE_URL
        ).rstrip("/")
        self.model = _env("PYT_RAG_EMBEDDING_MODEL", "EMBEDDING_MODEL", default=ZHIPU_DEFAULT_MODEL)
        self.dim = int(
            _env("PYT_RAG_EMBEDDING_DIM", "EMBEDDING_DIM", default=str(ZHIPU_DEFAULT_DIM))
        )
        self.name = f"zhipu-{self.model}"
        self._timeout = float(_env("PYT_RAG_EMBEDDING_TIMEOUT", default="30"))
        self._batch = max(1, int(_env("PYT_RAG_EMBEDDING_BATCH", default="64")))
        if not self.api_key:
            raise ValueError(
                "ZhipuEmbedder 需要 EMBEDDING_API_KEY（或 PYT_RAG_EMBEDDING_API_KEY）"
            )

    async def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        out: list[list[float]] = []
        headers = {"Authorization": f"Bearer {self.api_key}"}
        async with httpx.AsyncClient(timeout=self._timeout) as client:
            for start in range(0, len(texts), self._batch):
                batch = texts[start : start + self._batch]
                payload: dict[str, Any] = {
                    "model": self.model,
                    "input": batch,
                    "dimensions": self.dim,
                }
                resp = await client.post(
                    f"{self.base_url}/embeddings", headers=headers, json=payload
                )
                resp.raise_for_status()
                data = resp.json().get("data", [])
                # 按 index 还原与输入一致的顺序（OpenAI 兼容响应携带 index）。
                ordered = sorted(data, key=lambda item: int(item.get("index", 0)))
                out.extend([float(x) for x in item.get("embedding", [])] for item in ordered)
        return out


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
                self._fallback_reason = f"{type(exc).__name__}: {exc}"
                log.warning("embedding primary failed; switching to hash fallback: %s", exc)
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


def _zhipu_with_hash_fallback() -> Embedder:
    dim = int(_env("PYT_RAG_EMBEDDING_DIM", "EMBEDDING_DIM", default=str(ZHIPU_DEFAULT_DIM)))
    model = _env("PYT_RAG_EMBEDDING_MODEL", "EMBEDDING_MODEL", default=ZHIPU_DEFAULT_MODEL)
    fallback_mode = _env("PYT_RAG_EMBEDDING_FALLBACK", default="hash").lower()
    if fallback_mode in ("", "none", "off", "false", "strict"):
        return ZhipuEmbedder()

    fallback = HashEmbedder(dim=dim)
    fallback_reason: str | None = None
    try:
        primary: Embedder | None = ZhipuEmbedder()
    except ValueError as exc:
        fallback_reason = f"{type(exc).__name__}: {exc}"
        log.warning("embedding primary unavailable at startup; using hash fallback: %s", exc)
        primary = None
    return FallbackEmbedder(
        primary=primary,
        fallback=fallback,
        dim=dim,
        name=f"zhipu-{model}+hash-fallback",
        fallback_reason=fallback_reason,
    )


def select_embedder() -> Embedder:
    kind = os.environ.get("PYT_RAG_EMBEDDER", "hash").lower()
    if kind == "hash":
        return HashEmbedder()
    if kind == "bge":
        return BgeSmallEmbedder()
    if kind == "iflytek":
        return IflytekSparkEmbedder()
    if kind in ("zhipu", "embedding-3", "glm"):
        return _zhipu_with_hash_fallback()
    if kind in ("zhipu-strict", "embedding-3-strict"):
        return ZhipuEmbedder()
    raise ValueError(f"Unknown PYT_RAG_EMBEDDER: {kind}")
