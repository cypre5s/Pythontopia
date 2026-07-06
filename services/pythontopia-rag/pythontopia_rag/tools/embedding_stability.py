from __future__ import annotations

import argparse
import asyncio
import json
import math
import time
from dataclasses import asdict, dataclass
from typing import Any, Protocol

from pythontopia_rag.index.embedder import Embedder, select_embedder

DEFAULT_TEXTS = [
    "列表推导式如何避免越界错误？",
    "for 循环遍历字符串和列表有什么区别？",
    "input 函数读取后为什么需要类型转换？",
    "函数参数默认值在 Python 中应该如何使用？",
    "字典 key 不存在时如何安全读取？",
    "调试 TypeError 时先看哪几类信息？",
]


class StabilityEmbedder(Protocol):
    name: str
    dim: int

    async def embed(self, texts: list[str]) -> list[list[float]]: ...


@dataclass(frozen=True)
class StabilitySummary:
    model: str
    dim: int
    rounds: int
    concurrency: int
    batch_size: int
    total_requests: int
    total_vectors: int
    failures: int
    dimension_mismatches: int
    non_finite_vectors: int
    zero_vectors: int
    latency_ms_min: float
    latency_ms_p50: float
    latency_ms_p95: float
    latency_ms_max: float
    min_repeat_cosine: float | None
    fallback_used: bool
    fallback_reason: str | None
    errors: list[str]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class _RequestResult:
    latency_ms: float
    vectors: list[list[float]]
    error: str | None


class RouteEmbedder:
    """Exercise the project `/embed` route while reusing the active embedder."""

    def __init__(self, embedder: Embedder) -> None:
        from fastapi.testclient import TestClient

        from pythontopia_rag.app_state import AppState
        from pythontopia_rag.index.store import InMemoryVectorStore
        from pythontopia_rag.main import app

        app.state.rag = AppState(embedder=embedder, store=InMemoryVectorStore())
        self._client = TestClient(app)
        self._embedder = embedder
        self.name = f"/embed:{embedder.name}"
        self.dim = embedder.dim

    @property
    def fallback_reason(self) -> str | None:
        value = getattr(self._embedder, "fallback_reason", None)
        return value if isinstance(value, str) else None

    @property
    def using_fallback(self) -> bool:
        return bool(getattr(self._embedder, "using_fallback", False))

    async def embed(self, texts: list[str]) -> list[list[float]]:
        response = await asyncio.to_thread(self._client.post, "/embed", json={"texts": texts})
        response.raise_for_status()
        body = response.json()
        self.dim = int(body.get("dim", self.dim))
        return [[float(component) for component in vector] for vector in body.get("vectors", [])]


async def run_stability_probe(
    embedder: StabilityEmbedder,
    *,
    texts: list[str] | None = None,
    rounds: int = 20,
    concurrency: int = 4,
    batch_size: int = 4,
) -> StabilitySummary:
    probes = texts or DEFAULT_TEXTS
    if not probes:
        raise ValueError("at least one probe text is required")
    rounds = max(1, rounds)
    concurrency = max(1, concurrency)
    batch_size = max(1, batch_size)
    semaphore = asyncio.Semaphore(concurrency)

    async def one_request(round_index: int) -> _RequestResult:
        batch = _batch_for_round(probes, batch_size, round_index)
        started = time.perf_counter()
        async with semaphore:
            try:
                vectors = await embedder.embed(batch)
                error = None
            except Exception as exc:  # noqa: BLE001 - stability probe records provider failures
                vectors = []
                error = f"{type(exc).__name__}: {exc}"
        latency_ms = (time.perf_counter() - started) * 1000
        return _RequestResult(latency_ms=latency_ms, vectors=vectors, error=error)

    results = await asyncio.gather(*(one_request(index) for index in range(rounds)))
    expected_dim = int(embedder.dim)
    latencies = [result.latency_ms for result in results]
    vectors = [vector for result in results for vector in result.vectors]
    errors = [result.error for result in results if result.error]
    dimension_mismatches = sum(1 for vector in vectors if len(vector) != expected_dim)
    non_finite_vectors = sum(1 for vector in vectors if not _is_finite_vector(vector))
    zero_vectors = sum(1 for vector in vectors if _is_zero_vector(vector))
    repeat_vectors = [
        result.vectors[0]
        for result in results
        if result.vectors
        and len(result.vectors[0]) == expected_dim
        and _is_finite_vector(result.vectors[0])
        and not _is_zero_vector(result.vectors[0])
    ]

    return StabilitySummary(
        model=embedder.name,
        dim=expected_dim,
        rounds=rounds,
        concurrency=concurrency,
        batch_size=batch_size,
        total_requests=rounds,
        total_vectors=len(vectors),
        failures=len(errors),
        dimension_mismatches=dimension_mismatches,
        non_finite_vectors=non_finite_vectors,
        zero_vectors=zero_vectors,
        latency_ms_min=round(min(latencies), 2),
        latency_ms_p50=round(_percentile(latencies, 0.50), 2),
        latency_ms_p95=round(_percentile(latencies, 0.95), 2),
        latency_ms_max=round(max(latencies), 2),
        min_repeat_cosine=_min_repeat_cosine(repeat_vectors),
        fallback_used=bool(getattr(embedder, "using_fallback", False)),
        fallback_reason=_fallback_reason(embedder),
        errors=errors[:10],
    )


def _batch_for_round(texts: list[str], batch_size: int, round_index: int) -> list[str]:
    if batch_size == 1:
        return [texts[0]]
    tail = texts[1:] or texts[:1]
    batch = [texts[0]]
    for offset in range(batch_size - 1):
        batch.append(tail[(round_index + offset) % len(tail)])
    return batch


def _is_finite_vector(vector: list[float]) -> bool:
    return all(math.isfinite(component) for component in vector)


def _is_zero_vector(vector: list[float]) -> bool:
    return all(component == 0.0 for component in vector)


def _cosine(left: list[float], right: list[float]) -> float | None:
    if len(left) != len(right) or not left:
        return None
    numerator = sum(a * b for a, b in zip(left, right))
    left_norm = math.sqrt(sum(a * a for a in left))
    right_norm = math.sqrt(sum(b * b for b in right))
    if left_norm <= 0 or right_norm <= 0:
        return None
    return numerator / (left_norm * right_norm)


def _min_repeat_cosine(vectors: list[list[float]]) -> float | None:
    if len(vectors) < 2:
        return None
    baseline = vectors[0]
    values = [
        cosine
        for vector in vectors[1:]
        if (cosine := _cosine(baseline, vector)) is not None
    ]
    if not values:
        return None
    return round(min(values), 6)


def _percentile(values: list[float], ratio: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    index = min(len(ordered) - 1, max(0, math.ceil(ratio * len(ordered)) - 1))
    return ordered[index]


def _fallback_reason(embedder: StabilityEmbedder) -> str | None:
    value = getattr(embedder, "fallback_reason", None)
    return value if isinstance(value, str) else None


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Probe project embedding stability.")
    parser.add_argument("--mode", choices=("embedder", "route"), default="embedder")
    parser.add_argument("--rounds", type=int, default=20)
    parser.add_argument("--concurrency", type=int, default=4)
    parser.add_argument("--batch-size", type=int, default=4)
    parser.add_argument("--text", action="append", default=[])
    parser.add_argument("--min-repeat-cosine", type=float, default=0.995)
    parser.add_argument("--fail-on-fallback", action="store_true")
    return parser.parse_args()


async def _run_from_args(args: argparse.Namespace) -> StabilitySummary:
    embedder: StabilityEmbedder
    selected = select_embedder()
    if args.mode == "route":
        embedder = RouteEmbedder(selected)
    else:
        embedder = selected
    return await run_stability_probe(
        embedder,
        texts=args.text or DEFAULT_TEXTS,
        rounds=args.rounds,
        concurrency=args.concurrency,
        batch_size=args.batch_size,
    )


def main() -> int:
    args = _parse_args()
    summary = asyncio.run(_run_from_args(args))
    print(json.dumps(summary.to_dict(), ensure_ascii=False, indent=2, sort_keys=True))
    if summary.failures or summary.dimension_mismatches or summary.non_finite_vectors:
        return 1
    if args.fail_on_fallback and summary.fallback_used:
        return 2
    if (
        summary.min_repeat_cosine is not None
        and summary.min_repeat_cosine < args.min_repeat_cosine
    ):
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
