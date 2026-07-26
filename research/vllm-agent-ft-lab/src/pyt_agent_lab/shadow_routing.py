from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any, Iterable


def compose_shadow_route(
    *,
    champion_records: list[dict[str, Any]],
    specialist_records: list[dict[str, Any]],
    specialist_agents: Iterable[str],
    specialist_schema_versions: Iterable[str] = (),
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """Compose an offline route from paired responses without serving changes.

    The case payload must be byte-equivalent after canonical JSON serialization. This
    prevents a seemingly successful route from comparing different prompts or cases.
    """
    champion = _index(champion_records, "champion")
    specialist = _index(specialist_records, "specialist")
    if set(champion) != set(specialist):
        raise ValueError(_case_mismatch_message(set(champion), set(specialist)))

    allowed_agents = set(specialist_agents)
    allowed_versions = set(specialist_schema_versions)
    routed: list[dict[str, Any]] = []
    decisions: list[dict[str, Any]] = []
    for case_id in sorted(champion):
        champion_record = champion[case_id]
        specialist_record = specialist[case_id]
        champion_case = champion_record["case"]
        specialist_case = specialist_record["case"]
        if _canonical(champion_case) != _canonical(specialist_case):
            raise ValueError(f"paired route case payload mismatch: {case_id}")

        agent = str(champion_case.get("agent") or "")
        schema_version = str(champion_case.get("schema_version") or "")
        agent_selected = agent in allowed_agents
        version_selected = not allowed_versions or schema_version in allowed_versions
        use_specialist = agent_selected and version_selected
        selected = specialist_record if use_specialist else champion_record
        routed.append(dict(selected))
        decisions.append(
            {
                "case_id": case_id,
                "agent": agent,
                "schema_version": schema_version,
                "selected": "specialist" if use_specialist else "champion",
                "reason": (
                    "agent_and_schema_match"
                    if use_specialist
                    else "champion_default"
                ),
                "case_sha256": hashlib.sha256(
                    _canonical(champion_case).encode("utf-8")
                ).hexdigest(),
            }
        )
    return routed, decisions


def write_shadow_route(
    *,
    routed_records: list[dict[str, Any]],
    decisions: list[dict[str, Any]],
    output_dir: str | Path,
    champion_source: str,
    specialist_source: str,
) -> dict[str, Any]:
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    _write_jsonl(target / "responses.jsonl", routed_records)
    _write_jsonl(target / "route_decisions.jsonl", decisions)
    specialist_count = sum(row["selected"] == "specialist" for row in decisions)
    manifest = {
        "mode": "offline_shadow_route",
        "serving_enabled": False,
        "case_count": len(decisions),
        "champion_count": len(decisions) - specialist_count,
        "specialist_count": specialist_count,
        "specialist_rate": specialist_count / len(decisions) if decisions else 0.0,
        "champion_source": champion_source,
        "specialist_source": specialist_source,
        "responses_sha256": _sha256(target / "responses.jsonl"),
        "route_decisions_sha256": _sha256(target / "route_decisions.jsonl"),
    }
    (target / "route_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return manifest


def load_jsonl(path: str | Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in Path(path).read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def _index(records: list[dict[str, Any]], label: str) -> dict[str, dict[str, Any]]:
    indexed: dict[str, dict[str, Any]] = {}
    for record in records:
        case = record.get("case")
        if not isinstance(case, dict) or not case.get("id"):
            raise ValueError(f"{label} response record missing case.id")
        case_id = str(case["id"])
        if case_id in indexed:
            raise ValueError(f"duplicate {label} case id: {case_id}")
        indexed[case_id] = record
    return indexed


def _case_mismatch_message(champion: set[str], specialist: set[str]) -> str:
    return (
        "paired route case mismatch: "
        f"specialist_missing={sorted(champion - specialist)}, "
        f"specialist_extra={sorted(specialist - champion)}"
    )


def _canonical(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.write_text(
        "".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows),
        encoding="utf-8",
    )


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()
