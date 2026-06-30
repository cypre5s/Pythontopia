"""Phase 7 system evolution loop.

The M3 gate already proves offline clustering, one-variable candidate creation,
dry-run canary planning, and rollback metadata. This runtime wraps that baseline
in an API-visible Trace-Eval-Cluster-Improve-Deploy-Monitor loop contract.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from pythontopia_graph.eval.m3_system_evolution_gate import (
    CanaryExecution,
    CanaryPlan,
    EvalDatasetItem,
    FailureCluster,
    OfflineReplayGate,
    PolicyCandidate,
    RollbackStatus,
    build_canary_plan,
    execute_canary_plan,
    generate_failure_clusters,
    propose_policy_candidate,
    rollback_status_for,
    run_offline_replay_gate,
)
from pythontopia_graph.runtime.loop_runtime import RuntimeStep
from pythontopia_graph.schemas.loop_contract import (
    EvidenceRef,
    LoopResult,
    LoopRunStatus,
    LoopScore,
    SCHEMA_VERSION,
)

EvalSuiteCategory = Literal["regression", "adversarial", "long_running", "audit"]
DeploymentStatus = Literal["CANARY_DRY_RUN_READY", "BLOCKED", "ROLLBACK_REQUIRED"]
MonitorStatus = Literal["CONTINUE_DRY_RUN", "BLOCK_DEPLOY", "ROLLBACK_REQUIRED"]

REQUIRED_SUITE_CATEGORIES: list[EvalSuiteCategory] = [
    "regression",
    "adversarial",
    "long_running",
    "audit",
]


class SystemEvolutionModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class EvalSuiteResult(SystemEvolutionModel):
    category: EvalSuiteCategory
    passed: bool
    case_count: int = Field(ge=0)
    failure_count: int = Field(ge=0)
    metrics: dict[str, float] = Field(default_factory=dict)


class MonitorSnapshot(SystemEvolutionModel):
    schema_validity: float = Field(ge=0.0, le=1.0)
    regression_rate: float = Field(ge=0.0, le=1.0)
    cost_ratio: float = Field(ge=0.0)
    live_error_rate: float = Field(default=0.0, ge=0.0, le=1.0)
    canary_sample_size: int = Field(default=0, ge=0)


class SystemEvolutionLoopRequest(SystemEvolutionModel):
    eval_items: list[EvalDatasetItem] = Field(min_length=1)
    evaluation_suites: list[EvalSuiteResult] = Field(min_length=1)
    monitor: MonitorSnapshot
    policy_version: str = Field(min_length=1, max_length=128)
    trace_id: str = Field(min_length=1, max_length=128)
    dry_run: bool = True


class TraceSummary(SystemEvolutionModel):
    dataset_size: int
    stable_id_count: int
    failed_trace_count: int
    passed_trace_count: int
    loop_types: list[str]
    policy_versions: list[str]
    trace_ids: list[str]


class EvaluationGate(SystemEvolutionModel):
    passed: bool
    offline_replay: OfflineReplayGate
    suite_results: list[EvalSuiteResult]
    required_suite_categories: list[EvalSuiteCategory]
    failed_suite_categories: list[EvalSuiteCategory]
    gate_reasons: list[str] = Field(default_factory=list)


class DeploymentPlan(SystemEvolutionModel):
    status: DeploymentStatus
    canary_plan: CanaryPlan
    canary_execution: CanaryExecution
    rollback_status: RollbackStatus
    dry_run: bool
    reasons: list[str] = Field(default_factory=list)


class MonitorDecision(SystemEvolutionModel):
    status: MonitorStatus
    action: Literal["continue_dry_run", "block_deploy", "rollback"]
    reasons: list[str] = Field(default_factory=list)
    rollback_to_policy_version: str = ""
    observed_metrics: dict[str, float] = Field(default_factory=dict)


class SystemEvolutionLoopResult(SystemEvolutionModel):
    status: LoopRunStatus
    stop_reason: str
    steps: list[RuntimeStep]
    trace_summary: TraceSummary
    evaluation_gate: EvaluationGate
    failure_clusters: list[FailureCluster]
    policy_candidate: PolicyCandidate
    deployment_plan: DeploymentPlan
    monitor_decision: MonitorDecision
    next_actions: list[str]
    loop_result: LoopResult


class SystemEvolutionLoop:
    """Run the Trace-Eval-Cluster-Improve-Deploy-Monitor loop."""

    loop_name = "system_evolution_loop"
    loop_type = "IMPROVE"
    states = ("trace", "eval", "cluster", "improve", "deploy", "monitor")

    def run(self, request: SystemEvolutionLoopRequest) -> SystemEvolutionLoopResult:
        trace_summary = self._trace_summary(request)
        clusters = generate_failure_clusters(request.eval_items)
        candidate = propose_policy_candidate(clusters[0])
        offline_replay = run_offline_replay_gate(request.eval_items, candidate)
        evaluation_gate = self._evaluation_gate(request, offline_replay)
        canary_plan = build_canary_plan(candidate, offline_replay)
        rollback_status = rollback_status_for(canary_plan, offline_replay)

        if not evaluation_gate.passed:
            deployment = self._blocked_deployment(
                canary_plan,
                rollback_status,
                request.dry_run,
                evaluation_gate.gate_reasons,
            )
            monitor_decision = self._blocked_monitor(evaluation_gate.gate_reasons)
            return self._result(
                request=request,
                status="REJECTED",
                stop_reason=self._gate_stop_reason(evaluation_gate),
                trace_summary=trace_summary,
                evaluation_gate=evaluation_gate,
                clusters=clusters,
                candidate=candidate,
                deployment=deployment,
                monitor_decision=monitor_decision,
                steps=self._failed_steps("eval", self._gate_stop_reason(evaluation_gate)),
                next_actions=["hold:release-candidate"],
            )

        canary_execution = execute_canary_plan(canary_plan, dry_run=request.dry_run)
        monitor_decision = self._monitor_decision(request.monitor, rollback_status)
        deployment = self._deployment_plan(
            canary_plan=canary_plan,
            canary_execution=canary_execution,
            rollback_status=rollback_status,
            monitor_decision=monitor_decision,
            dry_run=request.dry_run,
        )
        status, stop_reason, next_actions = self._termination(
            canary_execution, monitor_decision
        )
        steps = [RuntimeStep(state=state, status="done") for state in self.states]
        if status == "PARTIAL":
            steps[-1] = RuntimeStep(
                state="monitor",
                status="failed",
                reason=stop_reason,
            )
        return self._result(
            request=request,
            status=status,
            stop_reason=stop_reason,
            trace_summary=trace_summary,
            evaluation_gate=evaluation_gate,
            clusters=clusters,
            candidate=candidate,
            deployment=deployment,
            monitor_decision=monitor_decision,
            steps=steps,
            next_actions=next_actions,
        )

    def _trace_summary(self, request: SystemEvolutionLoopRequest) -> TraceSummary:
        return TraceSummary(
            dataset_size=len(request.eval_items),
            stable_id_count=len({item.id for item in request.eval_items}),
            failed_trace_count=sum(1 for item in request.eval_items if not item.passed),
            passed_trace_count=sum(1 for item in request.eval_items if item.passed),
            loop_types=sorted({item.loop_type for item in request.eval_items}),
            policy_versions=sorted({item.policy_version for item in request.eval_items}),
            trace_ids=[item.trace_id for item in request.eval_items],
        )

    def _evaluation_gate(
        self,
        request: SystemEvolutionLoopRequest,
        offline_replay: OfflineReplayGate,
    ) -> EvaluationGate:
        failed_suites = [
            item.category
            for item in request.evaluation_suites
            if not item.passed or item.failure_count > 0
        ]
        provided = {item.category for item in request.evaluation_suites}
        missing = [
            category for category in REQUIRED_SUITE_CATEGORIES if category not in provided
        ]
        gate_reasons = list(offline_replay.gate_reasons)
        gate_reasons.extend(f"suite_failed:{category}" for category in failed_suites)
        gate_reasons.extend(f"suite_missing:{category}" for category in missing)
        return EvaluationGate(
            passed=offline_replay.passed and not failed_suites and not missing,
            offline_replay=offline_replay,
            suite_results=list(request.evaluation_suites),
            required_suite_categories=REQUIRED_SUITE_CATEGORIES,
            failed_suite_categories=failed_suites,
            gate_reasons=gate_reasons,
        )

    def _blocked_deployment(
        self,
        canary_plan: CanaryPlan,
        rollback_status: RollbackStatus,
        dry_run: bool,
        reasons: list[str],
    ) -> DeploymentPlan:
        return DeploymentPlan(
            status="BLOCKED",
            canary_plan=canary_plan,
            canary_execution=CanaryExecution(
                status="BLOCKED",
                dry_run=dry_run,
                scheduled_steps=[],
            ),
            rollback_status=rollback_status,
            dry_run=dry_run,
            reasons=reasons,
        )

    def _deployment_plan(
        self,
        *,
        canary_plan: CanaryPlan,
        canary_execution: CanaryExecution,
        rollback_status: RollbackStatus,
        monitor_decision: MonitorDecision,
        dry_run: bool,
    ) -> DeploymentPlan:
        if monitor_decision.status == "ROLLBACK_REQUIRED":
            status: DeploymentStatus = "ROLLBACK_REQUIRED"
        elif canary_execution.status == "DRY_RUN_READY":
            status = "CANARY_DRY_RUN_READY"
        else:
            status = "BLOCKED"
        return DeploymentPlan(
            status=status,
            canary_plan=canary_plan,
            canary_execution=canary_execution,
            rollback_status=rollback_status,
            dry_run=dry_run,
            reasons=list(monitor_decision.reasons),
        )

    def _monitor_decision(
        self,
        monitor: MonitorSnapshot,
        rollback_status: RollbackStatus,
    ) -> MonitorDecision:
        reasons: list[str] = []
        if monitor.schema_validity < 1.0:
            reasons.append("schema_validity_below_1.0")
        if monitor.regression_rate > 0.05:
            reasons.append("regression_rate_above_0.05")
        if monitor.cost_ratio > 1.2:
            reasons.append("cost_ratio_above_1.20")
        if monitor.live_error_rate > 0.03:
            reasons.append("live_error_rate_above_0.03")
        observed = {
            "schema_validity": monitor.schema_validity,
            "regression_rate": monitor.regression_rate,
            "cost_ratio": monitor.cost_ratio,
            "live_error_rate": monitor.live_error_rate,
            "canary_sample_size": float(monitor.canary_sample_size),
        }
        if reasons:
            return MonitorDecision(
                status="ROLLBACK_REQUIRED",
                action="rollback",
                reasons=reasons,
                rollback_to_policy_version=rollback_status.rollback_to_policy_version,
                observed_metrics=observed,
            )
        return MonitorDecision(
            status="CONTINUE_DRY_RUN",
            action="continue_dry_run",
            reasons=[],
            rollback_to_policy_version=rollback_status.rollback_to_policy_version,
            observed_metrics=observed,
        )

    def _blocked_monitor(self, reasons: list[str]) -> MonitorDecision:
        return MonitorDecision(
            status="BLOCK_DEPLOY",
            action="block_deploy",
            reasons=reasons,
            observed_metrics={},
        )

    def _termination(
        self,
        canary_execution: CanaryExecution,
        monitor_decision: MonitorDecision,
    ) -> tuple[LoopRunStatus, str, list[str]]:
        if monitor_decision.status == "ROLLBACK_REQUIRED":
            return (
                "PARTIAL",
                "monitor_rollback_required",
                [f"rollback:{monitor_decision.rollback_to_policy_version}"],
            )
        if canary_execution.status != "DRY_RUN_READY":
            return "REJECTED", "canary_plan_blocked", ["hold:release-candidate"]
        return "SUCCESS", "completed", ["complete:system_evolution_loop"]

    def _result(
        self,
        *,
        request: SystemEvolutionLoopRequest,
        status: LoopRunStatus,
        stop_reason: str,
        trace_summary: TraceSummary,
        evaluation_gate: EvaluationGate,
        clusters: list[FailureCluster],
        candidate: PolicyCandidate,
        deployment: DeploymentPlan,
        monitor_decision: MonitorDecision,
        steps: list[RuntimeStep],
        next_actions: list[str],
    ) -> SystemEvolutionLoopResult:
        loop_result = self._loop_result(
            request=request,
            status=status,
            stop_reason=stop_reason,
            trace_summary=trace_summary,
            evaluation_gate=evaluation_gate,
            clusters=clusters,
            candidate=candidate,
            deployment=deployment,
            monitor_decision=monitor_decision,
            steps=steps,
            next_actions=next_actions,
        )
        return SystemEvolutionLoopResult(
            status=status,
            stop_reason=stop_reason,
            steps=steps,
            trace_summary=trace_summary,
            evaluation_gate=evaluation_gate,
            failure_clusters=clusters,
            policy_candidate=candidate,
            deployment_plan=deployment,
            monitor_decision=monitor_decision,
            next_actions=next_actions,
            loop_result=loop_result,
        )

    def _loop_result(
        self,
        *,
        request: SystemEvolutionLoopRequest,
        status: LoopRunStatus,
        stop_reason: str,
        trace_summary: TraceSummary,
        evaluation_gate: EvaluationGate,
        clusters: list[FailureCluster],
        candidate: PolicyCandidate,
        deployment: DeploymentPlan,
        monitor_decision: MonitorDecision,
        steps: list[RuntimeStep],
        next_actions: list[str],
    ) -> LoopResult:
        result_payload = {
            "loop_name": self.loop_name,
            "stop_reason": stop_reason,
            "trace_summary": trace_summary.model_dump(mode="json"),
            "evaluation_gate": evaluation_gate.model_dump(mode="json"),
            "failure_clusters": [cluster.model_dump(mode="json") for cluster in clusters],
            "policy_candidate": candidate.model_dump(mode="json"),
            "deployment_plan": deployment.model_dump(mode="json"),
            "monitor_decision": monitor_decision.model_dump(mode="json"),
            "failed_suite_categories": list(evaluation_gate.failed_suite_categories),
            "steps": [step.model_dump(mode="json") for step in steps],
        }
        return LoopResult(
            run_id=f"{request.trace_id}:{self.loop_name}",
            loop_type="IMPROVE",
            status=status,
            result=result_payload,
            scores=[
                LoopScore(name="steps_used", value=float(len(steps))),
                LoopScore(
                    name="offline_mean_uplift",
                    value=evaluation_gate.offline_replay.mean_uplift,
                ),
                LoopScore(
                    name="monitor_regression_rate",
                    value=request.monitor.regression_rate,
                ),
                LoopScore(name="suite_pass_rate", value=self._suite_pass_rate(request)),
            ],
            artifacts=[
                candidate.model_dump(mode="json"),
                deployment.model_dump(mode="json"),
            ],
            next_actions=next_actions,
            policy_version=request.policy_version,
            trace_id=request.trace_id,
            evidence_refs=[
                EvidenceRef(type="eval_trace", id=item.trace_id, label=item.id)
                for item in request.eval_items
            ],
            schema_version=SCHEMA_VERSION,
        )

    def _failed_steps(self, failed_state: str, reason: str) -> list[RuntimeStep]:
        steps: list[RuntimeStep] = []
        for state in self.states[: self.states.index(failed_state) + 1]:
            steps.append(
                RuntimeStep(
                    state=state,
                    status="failed" if state == failed_state else "done",
                    reason=reason if state == failed_state else "",
                )
            )
        return steps

    def _gate_stop_reason(self, gate: EvaluationGate) -> str:
        if gate.failed_suite_categories:
            return "evaluation_suite_failed"
        return "offline_replay_gate_failed"

    def _suite_pass_rate(self, request: SystemEvolutionLoopRequest) -> float:
        if not request.evaluation_suites:
            return 0.0
        passed = sum(1 for item in request.evaluation_suites if item.passed)
        return passed / len(request.evaluation_suites)
