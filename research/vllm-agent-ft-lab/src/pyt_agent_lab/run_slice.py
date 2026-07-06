from __future__ import annotations

import csv
from pathlib import Path
from typing import Any

from .reporting import aggregate_fold_metrics, build_report


def filter_rows_by_case_ids(rows: list[dict[str, Any]], case_ids: list[str]) -> list[dict[str, Any]]:
    wanted = set(case_ids)
    return [row for row in rows if str(row.get("case_id")) in wanted]


def write_run_slice(metrics_path: str | Path, case_ids: list[str], output_dir: str | Path) -> dict[str, Any]:
    with Path(metrics_path).open(encoding="utf-8") as handle:
        rows = filter_rows_by_case_ids(list(csv.DictReader(handle)), case_ids)
    summary = aggregate_fold_metrics(rows)
    build_report(summary, output_dir)
    return summary
