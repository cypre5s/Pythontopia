from __future__ import annotations

import csv
import json
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


class ExperimentLogger:
    def __init__(self, run_dir: str | Path, *, total_cases: int | None = None, progress_every: int = 0) -> None:
        self.run_dir = Path(run_dir)
        self.run_dir.mkdir(parents=True, exist_ok=True)
        self.events_path = self.run_dir / "events.jsonl"
        self.metrics_path = self.run_dir / "metrics.csv"
        self.progress_path = self.run_dir / "progress_snapshots.jsonl"
        self.total_cases = total_cases
        self.progress_every = progress_every
        self._started_monotonic = time.perf_counter()
        self._metric_count = _existing_metric_count(self.metrics_path)
        self._lock = threading.Lock()

    def event(self, name: str, payload: dict[str, Any]) -> None:
        record = {"ts": _now(), "event": name, **payload}
        with self._lock:
            with self.events_path.open("a", encoding="utf-8") as handle:
                handle.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")

    def metric(self, row: dict[str, Any]) -> None:
        with self._lock:
            exists = self.metrics_path.exists()
            with self.metrics_path.open("a", newline="", encoding="utf-8") as handle:
                writer = csv.DictWriter(handle, fieldnames=list(row.keys()))
                if not exists:
                    writer.writeheader()
                writer.writerow(row)
            self._metric_count += 1
            if self.progress_every > 0 and (
                self._metric_count % self.progress_every == 0 or self._metric_count == self.total_cases
            ):
                self._write_progress_snapshot()

    def _write_progress_snapshot(self) -> None:
        rows = _load_metrics(self.metrics_path)
        snapshot = _summarize_progress(
            rows,
            total_cases=self.total_cases,
            elapsed_seconds=time.perf_counter() - self._started_monotonic,
        )
        with self.progress_path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(snapshot, ensure_ascii=False, sort_keys=True) + "\n")


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _existing_metric_count(path: Path) -> int:
    if not path.exists():
        return 0
    with path.open(encoding="utf-8") as handle:
        return max(0, sum(1 for _ in handle) - 1)


def _load_metrics(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    with path.open(encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def _summarize_progress(rows: list[dict[str, Any]], *, total_cases: int | None, elapsed_seconds: float) -> dict[str, Any]:
    completed = len(rows)
    latency_values = [_float(row.get("latency_ms")) for row in rows if row.get("latency_ms") not in {None, ""}]
    score_values = [_float(row.get("score")) for row in rows]
    throughput = completed / elapsed_seconds if elapsed_seconds > 0 else 0.0
    remaining = max(0, (total_cases or completed) - completed)
    eta_seconds = remaining / throughput if throughput > 0 and total_cases else None
    return {
        "ts": _now(),
        "completed_cases": completed,
        "total_cases": total_cases,
        "completion_rate": round(completed / total_cases, 6) if total_cases else None,
        "elapsed_seconds": round(elapsed_seconds, 2),
        "eta_seconds": round(eta_seconds, 2) if eta_seconds is not None else None,
        "throughput_cases_per_second": round(throughput, 6),
        "score_mean": _round(_mean(score_values)),
        "json_valid_rate": _rate(rows, "json_valid"),
        "grounding_safe_rate": _rate(rows, "grounding_safe"),
        "forbidden_leak_rate": _rate(rows, "forbidden_leak"),
        "latency_ms_mean": _round(_mean(latency_values), digits=2),
        "latency_ms_p95": _round(_percentile(latency_values, 95), digits=2),
    }


def _truthy(value: Any) -> bool:
    if isinstance(value, str):
        return value.lower() in {"1", "true", "yes"}
    return bool(value)


def _float(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def _mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else 0.0


def _percentile(values: list[float], percentile: int) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    rank = (len(ordered) - 1) * (percentile / 100)
    lower = int(rank)
    upper = min(lower + 1, len(ordered) - 1)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (rank - lower)


def _rate(rows: list[dict[str, Any]], key: str) -> float:
    if not rows:
        return 0.0
    return _round(sum(1 for row in rows if _truthy(row.get(key))) / len(rows))


def _round(value: float, digits: int = 6) -> float:
    return round(value, digits)
