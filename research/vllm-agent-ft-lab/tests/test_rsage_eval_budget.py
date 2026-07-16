from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

import pytest

from pyt_agent_lab.rsage_eval_budget import (
    CandidateRejectedError,
    EvaluationGateError,
    FreshFull5000ManifestError,
    Full5000BudgetConsumedError,
    StageOrderError,
    claim_full5000_receipt,
    classify_full5000_manifest,
    complete_full5000_receipt,
    create_gate_state,
    next_stage,
    plan_next_commands,
    record_evaluation_stage,
    record_final_stage,
    record_seed_stage,
    validate_fresh_full5000_manifest,
)
from pyt_agent_lab.rsage_generalization_gate import SeedGateReport, evaluate_checkpoint


UNIFORM_ACTION_COUNTS = {
    "READ": 10,
    "REVIEW": 10,
    "TRACE": 10,
    "SCAFFOLD": 10,
    "PRACTICE": 10,
    "ASK": 10,
    "CHECK": 10,
}


def _state(*, demo: bool = False):
    return create_gate_state(
        candidate_id="candidate-a",
        adapter_sha256="a" * 64,
        demo=demo,
    )


def _pass_gate():
    return evaluate_checkpoint(action_counts=UNIFORM_ACTION_COUNTS)


def _state_after_500():
    state = record_evaluation_stage(
        _state(),
        stage="golden50",
        case_count=50,
        gate=_pass_gate(),
        artifact_sha256="c" * 64,
    )
    return record_evaluation_stage(
        state,
        stage="stratified500",
        case_count=500,
        gate=_pass_gate(),
        artifact_sha256="d" * 64,
    )


def _ready_for_final(*, demo: bool = False):
    state = _state_after_500() if not demo else _state_after_500_demo()
    return record_seed_stage(
        state,
        [
            SeedGateReport(42, 0.05, _pass_gate()),
            SeedGateReport(43, 0.04, _pass_gate()),
            SeedGateReport(44, -0.01, _pass_gate()),
        ],
        artifact_sha256="e" * 64,
    )


def _state_after_500_demo():
    state = record_evaluation_stage(
        _state(demo=True),
        stage="golden50",
        case_count=50,
        gate=_pass_gate(),
        artifact_sha256="c" * 64,
    )
    return record_evaluation_stage(
        state,
        stage="stratified500",
        case_count=500,
        gate=_pass_gate(),
        artifact_sha256="d" * 64,
    )


def _fresh_manifest(*, overlap: int = 0, declared: bool = True):
    return {
        "format": "fresh_full5000_v2",
        "actual_count": 5000,
        "dataset_sha256": "f" * 64,
        "overlap_audit": {
            "historical_eval_overlap_count": overlap,
            "declared_zero_historical_overlap": declared,
            "training_overlap_count": 0,
        },
    }


def test_gate_order_starts_at_50_then_requires_500() -> None:
    state = _state()

    assert next_stage(state) == "golden50"
    with pytest.raises(StageOrderError, match="golden50"):
        record_evaluation_stage(
            state,
            stage="stratified500",
            case_count=500,
            gate=_pass_gate(),
            artifact_sha256="b" * 64,
        )

    state = record_evaluation_stage(
        state,
        stage="golden50",
        case_count=50,
        gate=_pass_gate(),
        artifact_sha256="c" * 64,
    )

    assert next_stage(state) == "stratified500"


def test_stratified_stage_requires_exactly_500_cases() -> None:
    state = record_evaluation_stage(
        _state(),
        stage="golden50",
        case_count=50,
        gate=_pass_gate(),
        artifact_sha256="c" * 64,
    )

    with pytest.raises(ValueError, match="500"):
        record_evaluation_stage(
            state,
            stage="stratified500",
            case_count=499,
            gate=_pass_gate(),
            artifact_sha256="d" * 64,
        )


def test_generalization_hard_stop_rejects_candidate_and_blocks_progress() -> None:
    collapse = evaluate_checkpoint(action_counts={"READ": 50})

    state = record_evaluation_stage(
        _state(),
        stage="golden50",
        case_count=50,
        gate=collapse,
        artifact_sha256="c" * 64,
    )

    assert state.status == "rejected"
    assert "ACTION_COLLAPSE" in state.rejection_reason_codes
    assert next_stage(state) is None
    with pytest.raises(CandidateRejectedError):
        record_evaluation_stage(
            state,
            stage="stratified500",
            case_count=500,
            gate=_pass_gate(),
            artifact_sha256="d" * 64,
        )


def test_explicit_candidate_rejection_stops_even_when_gate_passes() -> None:
    state = record_evaluation_stage(
        _state(),
        stage="golden50",
        case_count=50,
        gate=_pass_gate(),
        artifact_sha256="c" * 64,
        accepted=False,
    )

    assert state.status == "rejected"
    assert "CANDIDATE_REJECTED" in state.rejection_reason_codes


def test_command_plan_reuses_existing_baseline_and_report_flow(tmp_path: Path) -> None:
    commands = plan_next_commands(
        _state(),
        cases_path=tmp_path / "golden50.jsonl",
        run_root=tmp_path / "runs",
        base_url="http://127.0.0.1:8000/v1",
        model="rsage-candidate",
        adapter_path=tmp_path / "adapter",
    )

    assert len(commands) == 1
    assert commands[0].stage == "golden50"
    assert commands[0].expected_case_count == 50
    assert any(item.endswith("scripts/run_baseline.py") for item in commands[0].argv)


def test_three_registered_seeds_are_required_before_fresh_final(tmp_path: Path) -> None:
    state = _state_after_500()

    assert next_stage(state) == "three_seed"
    commands = plan_next_commands(
        state,
        cases_path=tmp_path / "stratified500.jsonl",
        run_root=tmp_path / "runs",
        base_url="http://127.0.0.1:8000/v1",
        model="rsage-candidate",
        adapter_path=tmp_path / "adapter",
    )
    assert [command.seed for command in commands] == [42, 43, 44]

    state = record_seed_stage(
        state,
        [
            SeedGateReport(42, 0.05, _pass_gate()),
            SeedGateReport(43, 0.04, _pass_gate()),
            SeedGateReport(44, -0.01, _pass_gate()),
        ],
        artifact_sha256="e" * 64,
    )

    assert state.status == "ready_for_final"
    assert next_stage(state) == "fresh_full5000_v2"


def test_three_seed_generalization_failure_rejects_candidate() -> None:
    state = record_seed_stage(
        _state_after_500(),
        [
            SeedGateReport(42, 0.08, _pass_gate()),
            SeedGateReport(43, 0.00, _pass_gate()),
            SeedGateReport(44, -0.03, _pass_gate()),
        ],
        artifact_sha256="e" * 64,
    )

    assert state.status == "rejected"
    assert "SEED_PROMOTION_FAILED" in state.rejection_reason_codes
    assert "SEED_INSTABILITY" in state.rejection_reason_codes


def test_any_seed_leak_hard_stops_before_final() -> None:
    leaking_gate = evaluate_checkpoint(
        action_counts=UNIFORM_ACTION_COUNTS,
        answer_leak_count=1,
    )

    state = record_seed_stage(
        _state_after_500(),
        [
            SeedGateReport(42, 0.06, _pass_gate()),
            SeedGateReport(43, 0.05, leaking_gate),
            SeedGateReport(44, 0.04, _pass_gate()),
        ],
        artifact_sha256="e" * 64,
    )

    assert state.status == "rejected"
    assert "ANSWER_LEAK" in state.rejection_reason_codes


def test_legacy_full5000_is_historical_regression_only() -> None:
    legacy = {
        "format": "domain_cases_5000_v1_manifest",
        "eval_pack_version": "5000-v1",
        "actual_count": 5000,
        "dataset_sha256": "1" * 64,
    }

    classification = classify_full5000_manifest(legacy)

    assert classification["role"] == "historical_regression_only"
    assert classification["eligible_for_final"] is False
    with pytest.raises(FreshFull5000ManifestError, match="历史回归"):
        validate_fresh_full5000_manifest(legacy)


def test_unversioned_known_v1_manifest_signature_is_still_historical() -> None:
    legacy = {
        "actual_count": 5000,
        "seed": 20260706,
        "slice_counts": {
            "new_question": 2000,
            "new_chapter_slice": 1000,
            "new_student_expression": 1500,
            "web_workshop_input": 500,
        },
    }

    classification = classify_full5000_manifest(legacy)

    assert classification["role"] == "historical_regression_only"
    assert classification["eligible_for_final"] is False


@pytest.mark.parametrize(
    "manifest",
    [
        _fresh_manifest(overlap=1),
        _fresh_manifest(declared=False),
        {**_fresh_manifest(), "format": "domain_cases_5000_v1"},
    ],
)
def test_final_manifest_requires_fresh_v2_and_declared_zero_history_overlap(
    manifest: dict[str, object],
) -> None:
    with pytest.raises(FreshFull5000ManifestError):
        validate_fresh_full5000_manifest(manifest)


def test_fresh_v2_manifest_is_eligible_only_after_overlap_audit() -> None:
    audit = validate_fresh_full5000_manifest(_fresh_manifest())

    assert audit["format"] == "fresh_full5000_v2"
    assert audit["actual_count"] == 5000
    assert audit["historical_eval_overlap_count"] == 0
    assert audit["declared_zero_historical_overlap"] is True


def test_final_receipt_is_durable_idempotent_and_fail_closed(tmp_path: Path) -> None:
    receipt_path = tmp_path / "fresh-full5000-receipt.json"
    command = ("python3", "run_baseline.py", "--cases", "fresh.jsonl")

    receipt = claim_full5000_receipt(
        _ready_for_final(),
        manifest=_fresh_manifest(),
        command=command,
        receipt_path=receipt_path,
    )
    original_bytes = receipt_path.read_bytes()

    assert receipt["status"] == "claimed"
    assert receipt["consumes_final_budget"] is True
    assert receipt_path.exists()
    assert claim_full5000_receipt(
        _ready_for_final(),
        manifest=_fresh_manifest(),
        command=command,
        receipt_path=receipt_path,
    ) == receipt
    assert receipt_path.read_bytes() == original_bytes

    with pytest.raises(Full5000BudgetConsumedError, match="已被占用"):
        claim_full5000_receipt(
            _ready_for_final(),
            manifest=_fresh_manifest(),
            command=(*command, "--resume"),
            receipt_path=receipt_path,
        )


def test_completed_receipt_preserves_final_gate_outcome(tmp_path: Path) -> None:
    receipt_path = tmp_path / "fresh-full5000-receipt.json"
    claim_full5000_receipt(
        _ready_for_final(),
        manifest=_fresh_manifest(),
        command=("python3", "run_baseline.py"),
        receipt_path=receipt_path,
    )

    completed = complete_full5000_receipt(
        receipt_path,
        gate=_pass_gate(),
        result_manifest_sha256="9" * 64,
    )

    assert completed["status"] == "completed"
    assert completed["outcome"] == "passed"
    assert completed["result_gate"]["status"] == "pass"
    assert complete_full5000_receipt(
        receipt_path,
        gate=_pass_gate(),
        result_manifest_sha256="9" * 64,
    ) == completed


def test_demo_never_creates_or_consumes_real_final_receipt(tmp_path: Path) -> None:
    real_receipt_path = tmp_path / "real" / "fresh-full5000-receipt.json"

    receipt = claim_full5000_receipt(
        _ready_for_final(demo=True),
        manifest=_fresh_manifest(),
        command=("fixture-evaluator", "fresh_full5000_v2"),
        receipt_path=real_receipt_path,
        demo=True,
    )

    assert receipt["status"] == "demo_only"
    assert receipt["consumes_final_budget"] is False
    assert not real_receipt_path.exists()


def test_final_stage_cannot_bypass_receipt_contract() -> None:
    with pytest.raises(EvaluationGateError, match="receipt"):
        record_evaluation_stage(
            _ready_for_final(),
            stage="fresh_full5000_v2",
            case_count=5000,
            gate=_pass_gate(),
            artifact_sha256="9" * 64,
        )


def test_demo_final_can_only_use_non_consuming_demo_receipt(tmp_path: Path) -> None:
    state = _ready_for_final(demo=True)
    receipt = claim_full5000_receipt(
        state,
        manifest=_fresh_manifest(),
        command=("fixture-evaluator", "fresh_full5000_v2"),
        receipt_path=tmp_path / "must-not-exist.json",
        demo=True,
    )

    state = record_final_stage(
        state,
        case_count=5000,
        gate=_pass_gate(),
        artifact_sha256="9" * 64,
        manifest=_fresh_manifest(),
        receipt=receipt,
    )

    assert state.status == "completed"
    assert [record.stage for record in state.records] == [
        "golden50",
        "stratified500",
        "three_seed",
        "fresh_full5000_v2",
    ]


def test_cli_demo_runs_fixture_gates_without_consuming_real_receipt(
    tmp_path: Path,
) -> None:
    lab_root = Path(__file__).resolve().parents[1]
    repo_root = lab_root.parents[1]
    output_dir = tmp_path / "demo"
    real_receipt = tmp_path / "real" / "fresh-full5000-receipt.json"
    result = subprocess.run(
        [
            sys.executable,
            str(lab_root / "scripts" / "run_rsage_eval_gate.py"),
            "--demo",
            "--output-dir",
            str(output_dir),
            "--receipt-path",
            str(real_receipt),
        ],
        cwd=repo_root,
        env={**os.environ, "PYTHONPATH": str(lab_root / "src")},
        text=True,
        capture_output=True,
        check=False,
    )

    assert result.returncode == 0, result.stderr
    state = json.loads((output_dir / "gate-state.json").read_text(encoding="utf-8"))
    demo_receipt = json.loads(
        (output_dir / "demo-full5000-receipt.json").read_text(encoding="utf-8")
    )
    commands = json.loads(
        (output_dir / "planned-final-command.json").read_text(encoding="utf-8")
    )
    assert state["status"] == "completed"
    assert [record["stage"] for record in state["records"]] == [
        "golden50",
        "stratified500",
        "three_seed",
        "fresh_full5000_v2",
    ]
    assert demo_receipt["status"] == "demo_only"
    assert demo_receipt["consumes_final_budget"] is False
    assert any(item.endswith("scripts/run_baseline.py") for item in commands[0]["argv"])
    assert not real_receipt.exists()
