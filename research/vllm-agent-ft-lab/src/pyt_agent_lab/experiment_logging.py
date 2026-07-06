from __future__ import annotations

import csv
import json
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


class ExperimentLogger:
    def __init__(self, run_dir: str | Path) -> None:
        self.run_dir = Path(run_dir)
        self.run_dir.mkdir(parents=True, exist_ok=True)
        self.events_path = self.run_dir / "events.jsonl"
        self.metrics_path = self.run_dir / "metrics.csv"
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


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")
