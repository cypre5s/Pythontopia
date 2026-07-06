from __future__ import annotations

import json
import re
from collections import Counter
from pathlib import Path
from typing import Any


PAGE_ID_RE = re.compile(r"page-\d+-\d+")


def load_response_records(path: str | Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if line.strip():
            records.append(json.loads(line))
    return records


def audit_response_records(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [_audit_record(record) for record in records]


def summarize_audits(audits: list[dict[str, Any]]) -> dict[str, Any]:
    flag_counts: Counter[str] = Counter()
    for audit in audits:
        flag_counts.update(audit["flags"])
    clean_count = sum(1 for audit in audits if not audit["flags"])
    total = len(audits)
    return {
        "case_count": total,
        "clean_count": clean_count,
        "clean_rate": round(clean_count / total, 6) if total else 0.0,
        "flag_counts": dict(sorted(flag_counts.items())),
    }


def write_audit_report(audits: list[dict[str, Any]], output_dir: str | Path) -> None:
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    summary = summarize_audits(audits)
    (target / "audit_summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    with (target / "response_audit.jsonl").open("w", encoding="utf-8") as handle:
        for audit in audits:
            handle.write(json.dumps(audit, ensure_ascii=False, sort_keys=True) + "\n")


def _audit_record(record: dict[str, Any]) -> dict[str, Any]:
    case = record["case"]
    response = str(record.get("response", ""))
    flags: list[str] = []
    parsed = _parse_json(response)
    evidence_ids = [str(item) for item in case.get("evidence_ids", [])]

    if parsed is None:
        flags.append("invalid_json")
    if evidence_ids and not all(evidence_id in response for evidence_id in evidence_ids):
        flags.append("missing_required_evidence_id")
    if not evidence_ids and PAGE_ID_RE.search(response):
        flags.append("page_mention_without_evidence_contract")
    if "```" in response:
        flags.append("markdown_fence")

    return {
        "case_id": str(case["id"]),
        "agent": str(case["agent"]),
        "flags": flags,
    }


def _parse_json(response: str) -> dict[str, Any] | None:
    try:
        parsed = json.loads(response)
    except json.JSONDecodeError:
        return None
    return parsed if isinstance(parsed, dict) else None
