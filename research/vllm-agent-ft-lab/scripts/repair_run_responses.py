#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

from pyt_agent_lab.grounding_repair import repair_response_records
from pyt_agent_lab.reporting import aggregate_fold_metrics, build_report
from pyt_agent_lab.response_audit import audit_response_records, load_response_records, write_audit_report


def main() -> None:
    parser = argparse.ArgumentParser(description="Apply deterministic JSON/evidence grounding repair to a response run.")
    parser.add_argument("--responses", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    responses_path = Path(args.responses)
    source_records = load_response_records(responses_path)
    repaired_records = repair_response_records(source_records, fold_by_case_id=_load_fold_by_case_id(responses_path))
    _write_jsonl(output_dir / "responses.jsonl", repaired_records)
    _write_metrics(output_dir / "metrics.csv", repaired_records)
    metrics = list(csv.DictReader((output_dir / "metrics.csv").open(encoding="utf-8")))
    summary = aggregate_fold_metrics(metrics)
    build_report(summary, output_dir)
    write_audit_report(audit_response_records(repaired_records), output_dir / "audit")
    (output_dir / "repair_manifest.json").write_text(
        json.dumps(
            {
                "source_responses": str(Path(args.responses)),
                "case_count": len(source_records),
                "repaired_count": sum(1 for record in repaired_records if record.get("repair", {}).get("applied")),
            },
            ensure_ascii=False,
            indent=2,
            sort_keys=True,
        ),
        encoding="utf-8",
    )
    print(output_dir)
    print(summary)


def _write_jsonl(path: Path, records: list[dict]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")


def _write_metrics(path: Path, records: list[dict]) -> None:
    fieldnames = [
        "fold",
        "case_id",
        "agent",
        "score",
        "json_valid",
        "grounding_safe",
        "forbidden_leak",
        "matched_expectations",
        "expectation_count",
        "latency_ms",
        "response_chars",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for record in records:
            score = dict(record["score"])
            writer.writerow(
                {
                    "fold": score.get("fold", 0),
                    "case_id": score["case_id"],
                    "agent": score["agent"],
                    "score": score["score"],
                    "json_valid": score["json_valid"],
                    "grounding_safe": score["grounding_safe"],
                    "forbidden_leak": score["forbidden_leak"],
                    "matched_expectations": score["matched_expectations"],
                    "expectation_count": score["expectation_count"],
                    "latency_ms": record.get("latency_ms", 0),
                    "response_chars": len(str(record.get("response", ""))),
                }
            )


def _load_fold_by_case_id(responses_path: Path) -> dict[str, int]:
    metrics_path = responses_path.parent / "metrics.csv"
    if not metrics_path.exists():
        return {}
    with metrics_path.open(encoding="utf-8") as handle:
        return {
            str(row["case_id"]): int(row["fold"])
            for row in csv.DictReader(handle)
            if row.get("case_id") and row.get("fold") not in {None, ""}
        }


if __name__ == "__main__":
    main()
