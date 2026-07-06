from __future__ import annotations

from fastapi.testclient import TestClient

from pythontopia_graph.eval.m3_system_evolution_gate import (
    DEFAULT_M3_DATASET,
    EvalDatasetItem,
    load_eval_dataset,
)
from pythontopia_graph.main import app
from pythontopia_graph.runtime.system_evolution_loop import (
    EvalSuiteResult,
    MonitorSnapshot,
    SystemEvolutionLoop,
    SystemEvolutionLoopRequest,
)
from pythontopia_graph.schemas.loop_contract import LoopResult


def suites(*, adversarial_passed: bool = True) -> list[EvalSuiteResult]:
    return [
        EvalSuiteResult(category="regression", passed=True, case_count=55, failure_count=0),
        EvalSuiteResult(
            category="adversarial",
            passed=adversarial_passed,
            case_count=18,
            failure_count=0 if adversarial_passed else 2,
        ),
        EvalSuiteResult(category="long_running", passed=True, case_count=12, failure_count=0),
        EvalSuiteResult(category="audit", passed=True, case_count=8, failure_count=0),
    ]


def request(
    *,
    eval_items: list[EvalDatasetItem] | None = None,
    eval_suites: list[EvalSuiteResult] | None = None,
    monitor: MonitorSnapshot | None = None,
) -> SystemEvolutionLoopRequest:
    return SystemEvolutionLoopRequest(
        eval_items=eval_items if eval_items is not None else load_eval_dataset(DEFAULT_M3_DATASET),
        evaluation_suites=eval_suites if eval_suites is not None else suites(),
        monitor=monitor or MonitorSnapshot(
            schema_validity=1.0,
            regression_rate=0.01,
            cost_ratio=1.05,
            live_error_rate=0.0,
            canary_sample_size=120,
        ),
        policy_version="agentic_loop_v1",
        trace_id="trace-phase7",
        dry_run=True,
    )


def test_system_evolution_runs_trace_to_monitor_dry_run() -> None:
    result = SystemEvolutionLoop().run(request())

    assert [step.state for step in result.steps] == [
        "trace",
        "eval",
        "cluster",
        "improve",
        "deploy",
        "monitor",
    ]
    assert result.status == "SUCCESS"
    assert result.trace_summary.dataset_size >= 8
    assert "RESOURCE" in result.trace_summary.loop_types
    assert result.evaluation_gate.passed is True
    assert result.evaluation_gate.required_suite_categories == [
        "regression",
        "adversarial",
        "long_running",
        "audit",
    ]
    assert result.failure_clusters[0].failure_signature == "evidence_missing"
    assert result.policy_candidate.changed_variables == ["retrieval_top_k"]
    assert result.deployment_plan.status == "CANARY_DRY_RUN_READY"
    assert result.deployment_plan.canary_execution.status == "DRY_RUN_READY"
    assert result.monitor_decision.status == "CONTINUE_DRY_RUN"
    assert result.loop_result.loop_type == "IMPROVE"
    assert isinstance(LoopResult.model_validate(result.loop_result.model_dump()), LoopResult)


def test_monitor_regression_triggers_rollback_plan() -> None:
    result = SystemEvolutionLoop().run(
        request(
            monitor=MonitorSnapshot(
                schema_validity=1.0,
                regression_rate=0.12,
                cost_ratio=1.04,
                live_error_rate=0.02,
                canary_sample_size=200,
            )
        )
    )

    assert result.status == "PARTIAL"
    assert result.stop_reason == "monitor_rollback_required"
    assert result.deployment_plan.status == "ROLLBACK_REQUIRED"
    assert result.monitor_decision.status == "ROLLBACK_REQUIRED"
    assert "rollback:agentic_loop_v1" in result.next_actions
    assert result.loop_result.status == "PARTIAL"


def test_adversarial_suite_failure_blocks_policy_deploy() -> None:
    result = SystemEvolutionLoop().run(
        request(eval_suites=suites(adversarial_passed=False))
    )

    assert result.status == "REJECTED"
    assert result.stop_reason == "evaluation_suite_failed"
    assert result.deployment_plan.status == "BLOCKED"
    assert result.monitor_decision.status == "BLOCK_DEPLOY"
    assert result.next_actions == ["hold:release-candidate"]
    assert result.loop_result.result["failed_suite_categories"] == ["adversarial"]


def test_offline_regression_blocks_policy_deploy() -> None:
    items = load_eval_dataset(DEFAULT_M3_DATASET)
    degraded = [
        item.model_copy(update={"candidate_score": max(item.baseline_score - 0.2, 0.0)})
        for item in items
    ]

    result = SystemEvolutionLoop().run(request(eval_items=degraded))

    assert result.status == "REJECTED"
    assert result.stop_reason == "offline_replay_gate_failed"
    assert result.evaluation_gate.offline_replay.passed is False
    assert result.deployment_plan.status == "BLOCKED"
    assert result.next_actions == ["hold:release-candidate"]


def test_route_returns_api_visible_system_evolution_state(monkeypatch) -> None:
    monkeypatch.setenv("PYT_INTERNAL_AUTH_OPTIONAL", "1")
    client = TestClient(app)

    response = client.post(
        "/runtime/system-evolution-runs",
        json=request().model_dump(mode="json"),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCESS"
    assert body["deployment_plan"]["status"] == "CANARY_DRY_RUN_READY"
    assert body["loop_result"]["trace_id"] == "trace-phase7"
