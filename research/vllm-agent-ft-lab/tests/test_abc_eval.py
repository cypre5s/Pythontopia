import json
from pathlib import Path

from pyt_agent_lab.abc_eval import AbcEvalConfig, GroupEndpoint, _ProgressTracker
from pyt_agent_lab.baseline import BaselineResult
from pyt_agent_lab.scoring import CaseScore


# ---------------------------------------------------------------------------
# Config tests
# ---------------------------------------------------------------------------


def test_config_creation() -> None:
    config = AbcEvalConfig(
        output_dir=Path("/tmp/test-abc"),
        cases=[],
        group_a=GroupEndpoint(label="A", base_url="http://ext:8080/v1", api_key="k1", model="ext-model"),
        group_b=GroupEndpoint(label="B", base_url="http://loc:18094/v1", api_key="k2", model="lora-model"),
    )
    assert config.concurrency == 1
    assert config.progress_every == 10
    assert config.timeout_seconds == 180
    assert config.group_a.label == "A"
    assert config.group_b.label == "B"


def test_group_endpoint_immutable() -> None:
    ep = GroupEndpoint(label="test", base_url="http://x", api_key="k", model="m")
    assert ep.label == "test"
    assert ep.base_url == "http://x"


# ---------------------------------------------------------------------------
# Progress tracker tests
# ---------------------------------------------------------------------------


def test_progress_tracker_initial_state() -> None:
    config = AbcEvalConfig(
        output_dir=Path("/tmp/t"),
        cases=[{}] * 50,
        group_a=GroupEndpoint("A", "http://a", "k", "m"),
        group_b=GroupEndpoint("B", "http://b", "k", "m"),
    )
    pt = _ProgressTracker(50, config)
    assert pt.total == 50
    assert pt._a_complete == 0
    assert pt._b_complete == 0
    assert pt._c_complete == 0
    assert pt._c_guarded == 0


def test_progress_tracker_tick_increments() -> None:
    config = AbcEvalConfig(
        output_dir=Path("/tmp/t"),
        cases=[{}] * 10,
        group_a=GroupEndpoint("A", "http://a", "k", "m"),
        group_b=GroupEndpoint("B", "http://b", "k", "m"),
    )
    pt = _ProgressTracker(10, config)
    pt.tick(0.9, 0.8)
    assert pt._a_complete == 1
    assert pt._b_complete == 1
    assert pt._c_complete == 0


def test_progress_tracker_tick_c() -> None:
    config = AbcEvalConfig(
        output_dir=Path("/tmp/t"),
        cases=[{}] * 10,
        group_a=GroupEndpoint("A", "http://a", "k", "m"),
        group_b=GroupEndpoint("B", "http://b", "k", "m"),
    )
    pt = _ProgressTracker(10, config)
    pt.tick_c(0.85, guarded=True)
    assert pt._c_complete == 1
    assert pt._c_guarded == 1
    pt.tick_c(0.95, guarded=False)
    assert pt._c_complete == 2
    assert pt._c_guarded == 1


# ---------------------------------------------------------------------------
# Group C guard + fallback pipeline (unit-level)
# ---------------------------------------------------------------------------


def test_group_c_pipeline_passes_clean_response(tmp_path: Path) -> None:
    """Verify that a clean response passes the guard unchanged."""
    from pyt_agent_lab.abc_eval import _run_group_c
    from pyt_agent_lab.guard import apply_guard

    case = {
        "id": "test-1",
        "agent": "TutorAgent",
        "kc": "变量",
        "expected_markers": ["next_hint"],
        "evidence_ids": [],
        "max_tokens": 512,
    }
    response = (
        '{"diagnosis": "variables store values", '
        '"next_hint": "Try explaining what x stores before printing it.", '
        '"evidence_ids": [], '
        '"safety_flags": [], '
        '"grounding_note": "common_knowledge"}'
    )
    score = CaseScore(
        case_id="test-1",
        agent="TutorAgent",
        score=1.0,
        json_valid=True,
        grounding_safe=True,
        forbidden_leak=False,
        matched_expectations=1,
        expectation_count=1,
        schema_valid=True,
    )

    config = AbcEvalConfig(
        output_dir=tmp_path,
        cases=[case],
        group_a=GroupEndpoint("A", "http://a", "k", "m"),
        group_b=GroupEndpoint("B", "http://b", "k", "m"),
    )

    b_result = BaselineResult(case=case, response=response, latency_ms=100, score=score)
    c_dir = tmp_path / "group_c_guarded"

    # Minimal progress tracker
    class _SilentProgress:
        def tick_c(self, *a: object, **kw: object) -> None:
            pass

        def finish(self) -> None:
            pass

    _run_group_c(config, [b_result], c_dir, _SilentProgress())  # type: ignore[arg-type]

    # Verify outputs
    assert (c_dir / "responses.jsonl").exists()
    assert (c_dir / "metrics.csv").exists()
    assert (c_dir / "guard_log.jsonl").exists()

    # Check guard log
    guard_lines = (c_dir / "guard_log.jsonl").read_text().strip().splitlines()
    assert len(guard_lines) == 1
    entry = json.loads(guard_lines[0])
    assert entry["guard_passed"] is True
    assert entry["guard_applied"] is False


def test_group_c_pipeline_applies_fallback_on_bad_response(tmp_path: Path) -> None:
    """Verify that a broken response triggers guard → fallback."""
    from pyt_agent_lab.abc_eval import _run_group_c

    case = {
        "id": "test-bad",
        "agent": "TutorAgent",
        "kc": "变量",
        "expected_markers": ["explanation"],
        "evidence_ids": [],
        "max_tokens": 512,
    }
    # Not valid JSON → guard will fail
    bad_response = "here is some prose about variables, not JSON at all"
    score = CaseScore(
        case_id="test-bad",
        agent="TutorAgent",
        score=0.3,
        json_valid=False,
        grounding_safe=True,
        forbidden_leak=False,
        matched_expectations=0,
        expectation_count=1,
    )

    config = AbcEvalConfig(
        output_dir=tmp_path,
        cases=[case],
        group_a=GroupEndpoint("A", "http://a", "k", "m"),
        group_b=GroupEndpoint("B", "http://b", "k", "m"),
    )

    b_result = BaselineResult(case=case, response=bad_response, latency_ms=100, score=score)
    c_dir = tmp_path / "group_c_guarded"

    class _SilentProgress:
        def tick_c(self, *a: object, **kw: object) -> None:
            pass

        def finish(self) -> None:
            pass

    _run_group_c(config, [b_result], c_dir, _SilentProgress())  # type: ignore[arg-type]

    guard_lines = (c_dir / "guard_log.jsonl").read_text().strip().splitlines()
    entry = json.loads(guard_lines[0])
    assert entry["guard_passed"] is False
    assert entry["guard_applied"] is True
    assert "json_invalid" in entry["guard_failures"]

    # The fallback response should be valid JSON
    responses = (c_dir / "responses.jsonl").read_text().strip().splitlines()
    assert len(responses) == 1
    record = json.loads(responses[0])
    fallback_parsed = json.loads(record["response"])
    assert isinstance(fallback_parsed, dict)


def test_group_c_scores_improve_after_fallback(tmp_path: Path) -> None:
    """Fallback response should score higher than the broken original."""
    from pyt_agent_lab.abc_eval import _run_group_c

    case = {
        "id": "test-improve",
        "agent": "SafetyAgent",
        "kc": "变量",
        "expected_markers": ["safer_alternative"],
        "evidence_ids": [],
        "max_tokens": 512,
    }
    bad_response = "just some text, not even JSON"
    score = CaseScore(
        case_id="test-improve",
        agent="SafetyAgent",
        score=0.0,
        json_valid=False,
        grounding_safe=True,
        forbidden_leak=False,
        matched_expectations=0,
        expectation_count=1,
    )

    config = AbcEvalConfig(
        output_dir=tmp_path,
        cases=[case],
        group_a=GroupEndpoint("A", "http://a", "k", "m"),
        group_b=GroupEndpoint("B", "http://b", "k", "m"),
    )

    b_result = BaselineResult(case=case, response=bad_response, latency_ms=100, score=score)
    c_dir = tmp_path / "group_c_guarded"

    class _SilentProgress:
        def tick_c(self, *a: object, **kw: object) -> None:
            pass

        def finish(self) -> None:
            pass

    _run_group_c(config, [b_result], c_dir, _SilentProgress())  # type: ignore[arg-type]

    guard_entry = json.loads((c_dir / "guard_log.jsonl").read_text().strip().splitlines()[0])
    assert guard_entry["guard_applied"] is True
    # Score after fallback should be higher
    assert guard_entry["score_after"]["score"] > guard_entry["score_before"]["score"]
