"""Bounded public login burst probe with hard safety caps."""

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


@dataclass(frozen=True)
class BurstConfig:
    requests: int = 200
    concurrency: int = 20

    def __post_init__(self) -> None:
        if not 1 <= self.requests <= 500:
            raise ValueError("requests must be between 1 and 500")
        if not 1 <= self.concurrency <= 25:
            raise ValueError("concurrency must be between 1 and 25")


def _attempt(url: str, username: str, index: int, timeout: float) -> dict[str, Any]:
    payload = json.dumps(
        {"username": username, "password": f"definitely-wrong-{index}"}
    ).encode("utf-8")
    request = urllib.request.Request(
        url.rstrip("/") + "/api/auth/login",
        data=payload,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "User-Agent": "Pythontopia-Security-Audit/1.0",
        },
    )
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    started = time.monotonic()
    try:
        with opener.open(request, timeout=timeout) as response:
            response.read(4096)
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
        "index": index,
        "status": status,
        "elapsed_ms": round((time.monotonic() - started) * 1000, 2),
        "error": error,
    }


def run_burst(
    base_url: str,
    config: BurstConfig,
    timeout: float = 8.0,
    username: str = "audit-bruteforce",
) -> dict[str, Any]:
    validate_target(base_url, "public", {"47.120.29.62"})
    started = time.monotonic()
    results: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=config.concurrency) as executor:
        futures = [
            executor.submit(_attempt, base_url, username, index, timeout)
            for index in range(config.requests)
        ]
        for future in as_completed(futures):
            results.append(future.result())
    duration = time.monotonic() - started
    statuses: dict[str, int] = {}
    for result in results:
        key = str(result["status"] if result["status"] is not None else "error")
        statuses[key] = statuses.get(key, 0) + 1
    elapsed_values = [float(item["elapsed_ms"]) for item in results]
    return {
        "config": {"requests": config.requests, "concurrency": config.concurrency},
        "username": username,
        "duration_seconds": round(duration, 3),
        "requests_per_second": round(config.requests / duration, 2) if duration else None,
        "statuses": statuses,
        "latency_ms": {
            "min": min(elapsed_values),
            "median": round(statistics.median(elapsed_values), 2),
            "max": max(elapsed_values),
        },
        "rate_limited": statuses.get("429", 0),
        "errors": [item for item in results if item["error"]][:20],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://47.120.29.62")
    parser.add_argument("--requests", type=int, default=200)
    parser.add_argument("--concurrency", type=int, default=20)
    parser.add_argument("--timeout", type=float, default=8.0)
    parser.add_argument("--username", default="audit-bruteforce")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = run_burst(
        args.base_url,
        BurstConfig(requests=args.requests, concurrency=args.concurrency),
        args.timeout,
        args.username,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=True, indent=2, sort_keys=True) + "\n")
    print(json.dumps(report, ensure_ascii=True, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
