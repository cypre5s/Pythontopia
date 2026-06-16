"""Bounded read-only public pressure probe with hard safety caps."""

from __future__ import annotations

import argparse
import json
import statistics
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from attack_runner import validate_target


ALLOWED_PATHS = {"/", "/healthz", "/api/course/pack"}


@dataclass(frozen=True)
class PressureConfig:
    requests: int = 500
    concurrency: int = 25
    path: str = "/api/course/pack"

    def __post_init__(self) -> None:
        if not 1 <= self.requests <= 1000:
            raise ValueError("requests must be between 1 and 1000")
        if not 1 <= self.concurrency <= 40:
            raise ValueError("concurrency must be between 1 and 40")
        if self.path not in ALLOWED_PATHS:
            raise ValueError("path is not approved for read-only pressure testing")


def _request(base_url: str, path: str, timeout: float) -> dict[str, Any]:
    request = urllib.request.Request(
        base_url.rstrip("/") + path,
        method="GET",
        headers={"User-Agent": "Pythontopia-Security-Audit/1.0"},
    )
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    started = time.monotonic()
    try:
        with opener.open(request, timeout=timeout) as response:
            response.read(16 * 1024)
            status = response.status
            error = None
    except urllib.error.HTTPError as exc:
        exc.read(4096)
        status = exc.code
        error = None
    except Exception as exc:
        status = None
        error = f"{type(exc).__name__}: {exc}"
    return {
        "status": status,
        "elapsed_ms": round((time.monotonic() - started) * 1000, 2),
        "error": error,
    }


def run_pressure(
    base_url: str, config: PressureConfig, timeout: float = 8.0
) -> dict[str, Any]:
    validate_target(base_url, "public", {"47.120.29.62"})
    started = time.monotonic()
    results: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=config.concurrency) as executor:
        futures = [
            executor.submit(_request, base_url, config.path, timeout)
            for _ in range(config.requests)
        ]
        for future in as_completed(futures):
            results.append(future.result())
    duration = time.monotonic() - started
    statuses: dict[str, int] = {}
    for result in results:
        key = str(result["status"] if result["status"] is not None else "error")
        statuses[key] = statuses.get(key, 0) + 1
    elapsed = [float(item["elapsed_ms"]) for item in results]
    return {
        "config": {
            "requests": config.requests,
            "concurrency": config.concurrency,
            "path": config.path,
        },
        "duration_seconds": round(duration, 3),
        "requests_per_second": round(config.requests / duration, 2) if duration else None,
        "statuses": statuses,
        "latency_ms": {
            "min": min(elapsed),
            "median": round(statistics.median(elapsed), 2),
            "max": max(elapsed),
        },
        "errors": [item for item in results if item["error"]][:20],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://47.120.29.62")
    parser.add_argument("--requests", type=int, default=500)
    parser.add_argument("--concurrency", type=int, default=25)
    parser.add_argument("--path", default="/api/course/pack")
    parser.add_argument("--timeout", type=float, default=8.0)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = run_pressure(
        args.base_url,
        PressureConfig(args.requests, args.concurrency, args.path),
        args.timeout,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    print(json.dumps(report, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
