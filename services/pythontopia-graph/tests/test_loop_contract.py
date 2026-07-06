"""Frozen loop runtime contract tests.

The pasted next-gen Agentic report asks for five internal objects shared across
loop runtime, persistence, AG-UI, and eval gates. These tests keep that contract
machine-checkable instead of living only in docs.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from pythontopia_graph import sse_agui
from pythontopia_graph.schemas.loop_contract import (
    AgentToolCall,
    EvalRecord,
    EventEnvelope,
    EvidenceRef,
    LoopCheckpoint,
    LoopResult,
    LoopScore,
    validate_loop_result_payload,
)


def test_loop_contract_models_accept_report_shape() -> None:
    evidence = EvidenceRef(type="course_page", id=123, label="第 12 页")
    envelope = EventEnvelope(
        event_id="evt-001",
        event_type="submission.failed",
        user_id=1024,
        session_id="town-2026-06-29-01",
        trace_id="otel-trace-id",
        timestamp="2026-06-29T18:30:00Z",
        context={"kc_codes": ["PY43-KC-001"], "source": "TRAINING"},
        payload={"submission_id": 9981},
        evidence_refs=[evidence],
    )
    checkpoint = LoopCheckpoint(
        loop_id="loop-001",
        loop_type="DEBUG",
        thread_id="user-1024-debug-problem-101",
        policy_version="debug-v3.2.0",
        step_index=2,
        state={"phase": "hint"},
        pending_human_input=False,
        resume_token="opaque",
        expires_at="2026-06-30T18:30:00Z",
    )
    tool_call = AgentToolCall(
        run_id="run-001",
        tool_name="judge.run",
        args_hash="sha256:abc",
        result_ref="submission:9981",
        timeout_ms=8000,
    )
    result = LoopResult(
        run_id="run-001",
        loop_type="RESOURCE",
        status="SUCCESS",
        result={"resource_id": 77},
        scores=[LoopScore(name="grounding_precision", value=0.98)],
        artifacts=[],
        next_actions=["practice"],
        policy_version="resource-v1.0.0",
        trace_id="otel-trace-id",
        evidence_refs=[evidence],
        schema_version="loop-contract.v1",
    )
    eval_record = EvalRecord(
        dataset_id="resource-golden-v1",
        item_id="case-001",
        loop_type="RESOURCE",
        policy_version="resource-v1.0.0",
        trace_id="otel-trace-id",
        metrics=[LoopScore(name="schema_validity", value=1.0)],
        passed=True,
        schema_version="loop-contract.v1",
    )

    assert envelope.evidence_refs[0].type == "course_page"
    assert checkpoint.schema_version == "loop-contract.v1"
    assert tool_call.status == "SUCCESS"
    assert result.loop_type == "RESOURCE"
    assert eval_record.metrics[0].name == "schema_validity"


def test_loop_result_requires_runtime_audit_fields() -> None:
    with pytest.raises(ValidationError) as exc:
        LoopResult(
            run_id="run-001",
            loop_type="LEARNER",
            status="SUCCESS",
            result={},
            scores=[],
            artifacts=[],
            next_actions=[],
            # policy_version / trace_id / evidence_refs / schema_version are required.
        )

    message = str(exc.value)
    assert "policy_version" in message
    assert "trace_id" in message
    assert "evidence_refs" in message
    assert "schema_version" in message


def test_loop_result_rejects_unversioned_or_untraced_payload() -> None:
    bad_payload = {
        "run_id": "run-001",
        "loop_type": "DEBUG",
        "status": "SUCCESS",
        "result": {},
        "scores": [],
        "artifacts": [],
        "next_actions": [],
        "policy_version": "debug-v1",
        "trace_id": "",
        "evidence_refs": [],
        "schema_version": "loop-contract.v1",
    }

    with pytest.raises(ValidationError):
        validate_loop_result_payload(bad_payload)


def test_run_finished_loop_result_wraps_validated_contract() -> None:
    result = LoopResult(
        run_id="run-002",
        loop_type="LEARNER",
        status="SUCCESS",
        result={"diagnosis": "循环边界"},
        scores=[LoopScore(name="schema_validity", value=1.0)],
        artifacts=[],
        next_actions=["micro_check"],
        policy_version="learner-v1.0.0",
        trace_id="trace-002",
        evidence_refs=[EvidenceRef(type="kc", id="PY43-KC-016")],
        schema_version="loop-contract.v1",
    )

    raw = sse_agui.run_finished_loop_result(result)

    event, data_line = raw.splitlines()[:2]
    assert event == "event: RUN_FINISHED"
    body = json.loads(data_line.removeprefix("data: "))
    payload = body["result"]
    assert payload["loop_type"] == "LEARNER"
    assert payload["policy_version"] == "learner-v1.0.0"
    assert payload["trace_id"] == "trace-002"
    assert payload["schema_version"] == "loop-contract.v1"


def test_repo_loop_contract_check_requires_audit_fields() -> None:
    contract_path = Path(__file__).resolve().parents[3] / "docs/contracts/loop_contract_check.json"
    contract = json.loads(contract_path.read_text(encoding="utf-8"))

    assert contract["schema_version"] == "loop-contract.v1"
    required = set(contract["loop_result_required_fields"])
    assert {
        "policy_version",
        "loop_type",
        "trace_id",
        "evidence_refs",
        "schema_version",
    } <= required
    assert "RUN_FINISHED.result" in contract["agui_terminal_mapping"]


def test_repo_loop_contract_check_matches_pydantic_models() -> None:
    """The JSON checklist must stay in lockstep with executable models."""

    contract_path = Path(__file__).resolve().parents[3] / "docs/contracts/loop_contract_check.json"
    contract = json.loads(contract_path.read_text(encoding="utf-8"))

    model_pairs = {
        "event_envelope_required_fields": EventEnvelope,
        "checkpoint_required_fields": LoopCheckpoint,
        "agent_tool_call_required_fields": AgentToolCall,
        "loop_result_required_fields": LoopResult,
        "eval_record_required_fields": EvalRecord,
    }

    for contract_key, model in model_pairs.items():
        documented = set(contract[contract_key])
        actual = set(model.model_fields)
        assert documented == actual, contract_key

    assert set(contract["loop_types"]) == {"LEARNER", "DEBUG", "RESOURCE", "WORLD", "IMPROVE"}


def test_loop_contract_checker_returns_actionable_summary() -> None:
    from pythontopia_graph.eval.loop_contract_check import check_loop_contract

    contract_path = Path(__file__).resolve().parents[3] / "docs/contracts/loop_contract_check.json"

    summary = check_loop_contract(contract_path)

    assert summary.ok is True
    assert summary.schema_version == "loop-contract.v1"
    assert summary.checked_models == [
        "EventEnvelope",
        "LoopCheckpoint",
        "AgentToolCall",
        "LoopResult",
        "EvalRecord",
    ]
    assert summary.errors == []
