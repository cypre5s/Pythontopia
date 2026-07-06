from __future__ import annotations

import csv
import json
from pathlib import Path
from typing import Any

from .reporting import aggregate_fold_metrics, build_report
from .scoring import score_case_response


def rescore_run(run_dir: str | Path) -> dict[str, Any]:
    target = Path(run_dir)
    responses_path = target / "responses.jsonl"
    folds_by_case = _load_existing_folds(target / "metrics.csv")
    rows: list[dict[str, Any]] = []
    with responses_path.open(encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            record = json.loads(line)
            case = record["case"]
            score = score_case_response(case, record.get("response", ""))
            case_id = str(case["id"])
            rows.append(
                {
                    "fold": record.get("fold", case.get("fold", folds_by_case.get(case_id, 0))),
                    **score.as_row(),
                    "latency_ms": record.get("latency_ms", 0),
                    "response_chars": len(record.get("response", "")),
                }
            )
    _write_metrics(target / "metrics.csv", rows)
    summary = aggregate_fold_metrics(rows)
    build_report(summary, target)
    return summary


def _load_existing_folds(path: Path) -> dict[str, str]:
    if not path.exists() or path.stat().st_size == 0:
        return {}
    with path.open(encoding="utf-8") as handle:
        return {row["case_id"]: row.get("fold", "0") for row in csv.DictReader(handle) if row.get("case_id")}


def _write_metrics(path: Path, rows: list[dict[str, Any]]) -> None:
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
