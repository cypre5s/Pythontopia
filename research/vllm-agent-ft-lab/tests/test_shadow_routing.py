from __future__ import annotations

import json

import pytest

from pyt_agent_lab.shadow_routing import compose_shadow_route, write_shadow_route


def _record(case_id: str, agent: str, response: str, version: str = "agent-schema-v2") -> dict:
    return {
        "case": {"id": case_id, "agent": agent, "schema_version": version},
        "response": response,
        "score": {"contract_pass": True},
    }


def test_shadow_route_uses_specialist_only_for_exact_agent_and_version(tmp_path) -> None:
    champion = [
        _record("c1", "ResourceDesignerAgent.code_lab", "champ-code", "agent-schema-v2.1"),
        _record("c2", "SafetyAgent", "champ-safe"),
    ]
    specialist = [
        _record("c1", "ResourceDesignerAgent.code_lab", "spec-code", "agent-schema-v2.1"),
        _record("c2", "SafetyAgent", "spec-safe"),
    ]
    routed, decisions = compose_shadow_route(
        champion_records=champion,
        specialist_records=specialist,
        specialist_agents=["ResourceDesignerAgent.code_lab"],
        specialist_schema_versions=["agent-schema-v2.1"],
    )
    assert [row["response"] for row in routed] == ["spec-code", "champ-safe"]
    assert [row["selected"] for row in decisions] == ["specialist", "champion"]
    manifest = write_shadow_route(
        routed_records=routed,
        decisions=decisions,
        output_dir=tmp_path,
        champion_source="champion.jsonl",
        specialist_source="specialist.jsonl",
    )
    assert manifest["serving_enabled"] is False
    assert manifest["specialist_rate"] == 0.5
    assert len((tmp_path / "responses.jsonl").read_text().splitlines()) == 2
    assert json.loads((tmp_path / "route_manifest.json").read_text())["case_count"] == 2


def test_shadow_route_rejects_case_payload_or_set_mismatch() -> None:
    champion = [_record("c1", "A", "one")]
    specialist = [_record("c2", "A", "two")]
    with pytest.raises(ValueError, match="case mismatch"):
        compose_shadow_route(
            champion_records=champion,
            specialist_records=specialist,
            specialist_agents=["A"],
        )

    specialist = [_record("c1", "B", "two")]
    with pytest.raises(ValueError, match="payload mismatch"):
        compose_shadow_route(
            champion_records=champion,
            specialist_records=specialist,
            specialist_agents=["A"],
        )
