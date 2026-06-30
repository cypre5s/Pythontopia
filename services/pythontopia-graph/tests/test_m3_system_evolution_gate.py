from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

import pytest

from pythontopia_graph.eval.m3_system_evolution_gate import (
    DEFAULT_M3_DATASET,
    load_eval_dataset,
    run_m3_system_evolution_gate,
)


def test_m3_gate_runs_trace_to_canary_and_rollback_chain() -> None:
    report = run_m3_system_evolution_gate(DEFAULT_M3_DATASET)

    assert report.passed is True
    assert report.dataset_id == "agentic-m3-system-evolution-v1"
    assert report.dataset.case_count >= 8
    assert report.dataset.stable_id_count == report.dataset.case_count
    assert report.failure_clusters
    assert report.policy_candidate.changed_variables == ["retrieval_top_k"]
    assert report.policy_candidate.one_variable_only is True
    assert report.offline_replay_gate.passed is True
    assert report.canary_plan.traffic_fraction == 0.05
    assert report.canary_plan.real_traffic_enabled is False
    assert report.canary_execution.status == "DRY_RUN_READY"
    assert report.rollback_status.status == "READY"
    assert report.rollback_status.rules


def test_eval_dataset_loader_rejects_duplicate_or_blank_stable_ids(tmp_path: Path) -> None:
    duplicate = tmp_path / "duplicate.jsonl"
    duplicate.write_text(
        "\n".join(
            [
                json.dumps({"id": "case-1", "loop_type": "LEARNER", "passed": True}),
                json.dumps({"id": "case-1", "loop_type": "DEBUG", "passed": False}),
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    with pytest.raises(ValueError, match="duplicate"):
        load_eval_dataset(duplicate)

    blank = tmp_path / "blank.jsonl"
    blank.write_text(
        json.dumps({"id": "", "loop_type": "LEARNER", "passed": True}) + "\n",
        encoding="utf-8",
    )

    with pytest.raises(ValueError, match="stable id"):
        load_eval_dataset(blank)


def test_m3_gate_cli_outputs_json_report() -> None:
    completed = subprocess.run(
        [
            sys.executable,
            "-m",
            "pythontopia_graph.eval.m3_system_evolution_gate",
            str(DEFAULT_M3_DATASET),
        ],
        check=True,
        capture_output=True,
        text=True,
    )

    payload = json.loads(completed.stdout)
    assert payload["passed"] is True
    assert payload["policy_candidate"]["one_variable_only"] is True
    assert payload["canary_execution"]["status"] == "DRY_RUN_READY"
