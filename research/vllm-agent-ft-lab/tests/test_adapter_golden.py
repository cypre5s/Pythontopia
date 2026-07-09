import json
from collections import Counter
from pathlib import Path
from typing import Any

from pyt_agent_lab.guard import apply_guard
from pyt_agent_lab.scoring import AGENT_SCHEMA, _parse_json_object, score_case_response


ROOT = Path(__file__).resolve().parents[1]
GOLDEN_CASES = ROOT / "cases" / "golden_adapter_50_v1.jsonl"
EXPECTED_BASELINE = ROOT / "outputs" / "golden" / "adapter_baseline_50_v1.jsonl"


def test_adapter_golden_set_shape_and_coverage() -> None:
    cases = _load_jsonl(GOLDEN_CASES)

    assert len(cases) == 50
    assert Counter(case["agent"] for case in cases) == {agent: 5 for agent in AGENT_SCHEMA}
    assert any(case.get("requires_evidence") and case.get("evidence_ids") for case in cases)
    assert any(not case.get("evidence_ids") for case in cases)
    assert any(case.get("forbidden_answer_risk") or case["agent"] == "SafetyAgent" for case in cases)
    assert len({case["id"] for case in cases}) == 50


def test_adapter_golden_expected_baseline_replays_through_guard_and_scoring() -> None:
    cases = {case["id"]: case for case in _load_jsonl(GOLDEN_CASES)}
    records = _load_jsonl(EXPECTED_BASELINE)

    assert len(records) == 50
    assert {record["case"]["id"] for record in records} == set(cases)

    for record in records:
        case = record["case"]
        expected_case = cases[case["id"]]
        response = record["response"]
        parsed = _parse_json_object(response)
        assert parsed is not None, case["id"]
        assert case == expected_case

        _assert_required_schema(case, parsed)
        _assert_evidence_ids_match(case, parsed)

        score = score_case_response(case, response)
        guard = apply_guard(case, response)
        expected_score = record["score"]
        assert score.as_row() == expected_score
        assert guard.passed is True


def _load_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def _assert_required_schema(case: dict[str, Any], parsed: dict[str, Any]) -> None:
    schema = AGENT_SCHEMA[str(case["agent"])]
    for key, expected_type in schema["required"].items():
        assert key in parsed, f"{case['id']} missing {key}"
        assert isinstance(parsed[key], expected_type), f"{case['id']} {key} has wrong type"


def _assert_evidence_ids_match(case: dict[str, Any], parsed: dict[str, Any]) -> None:
    expected = [str(item) for item in case.get("evidence_ids", [])]
    if "evidence_ids" not in parsed:
        return
    assert [str(item) for item in parsed["evidence_ids"]] == expected
