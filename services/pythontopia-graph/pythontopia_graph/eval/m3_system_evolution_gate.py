from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, computed_field

from pythontopia_graph.schemas.loop_contract import LoopType

DEFAULT_M3_DATASET = (
    Path(__file__).resolve().parent / "golden" / "agentic_m3_system_evolution.jsonl"
)
DATASET_ID = "agentic-m3-system-evolution-v1"
POLICY_VERSION = "agentic_loop_v2"


class M3Model(BaseModel):
    model_config = ConfigDict(extra="forbid")


class EvalDatasetItem(M3Model):
    id: str = Field(min_length=1, max_length=128)
    loop_type: LoopType
    policy_version: str = Field(min_length=1, max_length=128)
    trace_id: str = Field(min_length=1, max_length=128)
    passed: bool
    failure_signature: str = Field(min_length=1, max_length=128)
    metrics: dict[str, float] = Field(default_factory=dict)
    baseline_score: float = Field(ge=0.0, le=1.0)
    candidate_score: float = Field(ge=0.0, le=1.0)


class DatasetSummary(M3Model):
    case_count: int
    stable_id_count: int
    failed_count: int
    passed_count: int


class FailureCluster(M3Model):
    cluster_id: str
    failure_signature: str
    count: int
    item_ids: list[str]
    loop_types: list[LoopType]
    recommended_variable: str


class PolicyCandidate(M3Model):
    candidate_id: str
    base_policy_version: str
    policy_version: str
    changed_variables: list[str] = Field(min_length=1, max_length=1)
    config_patch: dict[str, int | float | str]
    rationale: str
    source_cluster_id: str

    @computed_field
    @property
    def one_variable_only(self) -> bool:
        return len(self.changed_variables) == 1


class OfflineReplayGate(M3Model):
    passed: bool
    baseline_mean: float
    candidate_mean: float
    mean_uplift: float
    regression_rate: float
    replayed_item_ids: list[str]
    gate_reasons: list[str] = Field(default_factory=list)


class CanaryPlan(M3Model):
    policy_version: str
    traffic_fraction: float
    real_traffic_enabled: bool
    one_variable_only: bool
    cohort: str
    rollback_rules: list[str]


class CanaryExecution(M3Model):
    status: Literal["DRY_RUN_READY", "BLOCKED"]
    dry_run: bool
    scheduled_steps: list[str]


class RollbackStatus(M3Model):
    status: Literal["READY", "BLOCKED"]
    rollback_to_policy_version: str
    rules: list[str]
    metadata: dict[str, str | float]


class M3SystemEvolutionReport(M3Model):
    dataset_id: str
    dataset: DatasetSummary
    failure_clusters: list[FailureCluster]
    policy_candidate: PolicyCandidate
    offline_replay_gate: OfflineReplayGate
    canary_plan: CanaryPlan
    canary_execution: CanaryExecution
    rollback_status: RollbackStatus
    passed: bool


def load_eval_dataset(path: str | Path = DEFAULT_M3_DATASET) -> list[EvalDatasetItem]:
    file_path = Path(path)
    raw_items: list[dict[str, object]] = []
    seen_ids: set[str] = set()
    for lineno, raw_line in enumerate(
        file_path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        line = raw_line.strip()
        if not line:
            continue
        raw = json.loads(line)
        stable_id = raw.get("id")
        if not isinstance(stable_id, str) or not stable_id.strip():
            raise ValueError(f"{file_path}:{lineno}: stable id is required")
        if stable_id in seen_ids:
            raise ValueError(f"{file_path}:{lineno}: duplicate stable id {stable_id}")
        seen_ids.add(stable_id)
        raw_items.append(raw)
    return [EvalDatasetItem.model_validate(raw) for raw in raw_items]


def run_m3_system_evolution_gate(
    path: str | Path = DEFAULT_M3_DATASET,
) -> M3SystemEvolutionReport:
    items = load_eval_dataset(path)
    dataset = DatasetSummary(
        case_count=len(items),
        stable_id_count=len({item.id for item in items}),
        failed_count=sum(1 for item in items if not item.passed),
        passed_count=sum(1 for item in items if item.passed),
    )
    clusters = generate_failure_clusters(items)
    candidate = propose_policy_candidate(clusters[0])
    replay_gate = run_offline_replay_gate(items, candidate)
    canary_plan = build_canary_plan(candidate, replay_gate)
    canary_execution = execute_canary_plan(canary_plan, dry_run=True)
    rollback_status = rollback_status_for(canary_plan, replay_gate)
    passed = (
        bool(items)
        and dataset.stable_id_count == dataset.case_count
        and bool(clusters)
        and candidate.one_variable_only
        and replay_gate.passed
        and canary_execution.status == "DRY_RUN_READY"
        and rollback_status.status == "READY"
    )
    return M3SystemEvolutionReport(
        dataset_id=DATASET_ID,
        dataset=dataset,
        failure_clusters=clusters,
        policy_candidate=candidate,
        offline_replay_gate=replay_gate,
        canary_plan=canary_plan,
        canary_execution=canary_execution,
        rollback_status=rollback_status,
        passed=passed,
    )


def generate_failure_clusters(items: list[EvalDatasetItem]) -> list[FailureCluster]:
    grouped: dict[str, list[EvalDatasetItem]] = defaultdict(list)
    for item in items:
        if item.passed:
            continue
        grouped[item.failure_signature].append(item)
    clusters: list[FailureCluster] = []
    for signature, group in grouped.items():
        clusters.append(
            FailureCluster(
                cluster_id=f"cluster-{signature}",
                failure_signature=signature,
                count=len(group),
                item_ids=[item.id for item in group],
                loop_types=sorted({item.loop_type for item in group}),  # type: ignore[arg-type]
                recommended_variable=_variable_for_signature(signature),
            )
        )
    return sorted(clusters, key=lambda cluster: (-cluster.count, cluster.cluster_id))


def propose_policy_candidate(cluster: FailureCluster) -> PolicyCandidate:
    variable = cluster.recommended_variable
    config_patch = _config_patch_for(variable)
    return PolicyCandidate(
        candidate_id=f"{POLICY_VERSION}-{variable}",
        base_policy_version="agentic_loop_v1",
        policy_version=POLICY_VERSION,
        changed_variables=[variable],
        config_patch=config_patch,
        rationale=(
            f"{cluster.failure_signature} appears in {cluster.count} eval traces; "
            f"change only {variable} and replay before canary."
        ),
        source_cluster_id=cluster.cluster_id,
    )


def run_offline_replay_gate(
    items: list[EvalDatasetItem],
    candidate: PolicyCandidate,
    *,
    min_mean_uplift: float = 0.08,
    max_regression_rate: float = 0.05,
    regression_tolerance: float = 0.01,
) -> OfflineReplayGate:
    baseline_mean = _mean([item.baseline_score for item in items])
    candidate_mean = _mean([item.candidate_score for item in items])
    regressions = [
        item
        for item in items
        if item.candidate_score < item.baseline_score - regression_tolerance
    ]
    regression_rate = len(regressions) / len(items) if items else 1.0
    mean_uplift = candidate_mean - baseline_mean
    gate_reasons: list[str] = []
    if not candidate.one_variable_only:
        gate_reasons.append("candidate_changed_more_than_one_variable")
    if mean_uplift < min_mean_uplift:
        gate_reasons.append(
            f"mean_uplift {mean_uplift:.3f} < min_mean_uplift {min_mean_uplift:.3f}"
        )
    if regression_rate > max_regression_rate:
        gate_reasons.append(
            f"regression_rate {regression_rate:.3f} > max_regression_rate {max_regression_rate:.3f}"
        )
    return OfflineReplayGate(
        passed=not gate_reasons,
        baseline_mean=round(baseline_mean, 6),
        candidate_mean=round(candidate_mean, 6),
        mean_uplift=round(mean_uplift, 6),
        regression_rate=round(regression_rate, 6),
        replayed_item_ids=[item.id for item in items],
        gate_reasons=gate_reasons,
    )


def build_canary_plan(candidate: PolicyCandidate, replay_gate: OfflineReplayGate) -> CanaryPlan:
    return CanaryPlan(
        policy_version=candidate.policy_version,
        traffic_fraction=0.05 if replay_gate.passed else 0.0,
        real_traffic_enabled=False,
        one_variable_only=candidate.one_variable_only,
        cohort="eligible-active-students-placeholder",
        rollback_rules=[
            "rollback if offline replay gate fails",
            "rollback if schema_validity < 1.0",
            "rollback if regression_rate > 0.05",
            "rollback if cost_ratio > 1.20",
        ],
    )


def execute_canary_plan(plan: CanaryPlan, *, dry_run: bool) -> CanaryExecution:
    if not dry_run or not plan.one_variable_only or plan.traffic_fraction <= 0:
        return CanaryExecution(status="BLOCKED", dry_run=dry_run, scheduled_steps=[])
    return CanaryExecution(
        status="DRY_RUN_READY",
        dry_run=True,
        scheduled_steps=[
            "register policy as CANARY metadata",
            "route 5% eligible cohort after human approval",
            "watch replay gate, schema validity, regression rate, and cost ratio",
        ],
    )


def rollback_status_for(
    plan: CanaryPlan, replay_gate: OfflineReplayGate
) -> RollbackStatus:
    return RollbackStatus(
        status="READY" if plan.rollback_rules else "BLOCKED",
        rollback_to_policy_version="agentic_loop_v1",
        rules=plan.rollback_rules,
        metadata={
            "offline_mean_uplift": replay_gate.mean_uplift,
            "offline_regression_rate": replay_gate.regression_rate,
        },
    )


def write_markdown_report(report: M3SystemEvolutionReport, path: Path) -> Path:
    lines = [
        "# Agentic M3 System Evolution Gate",
        "",
        f"- Dataset: `{report.dataset_id}`",
        f"- Cases: {report.dataset.case_count}",
        f"- Stable IDs: {report.dataset.stable_id_count}",
        f"- Result: {'PASS' if report.passed else 'FAIL'}",
        "",
        "## Candidate",
        "",
        f"- Policy: `{report.policy_candidate.policy_version}`",
        f"- Changed variable: `{report.policy_candidate.changed_variables[0]}`",
        f"- Offline mean uplift: `{report.offline_replay_gate.mean_uplift}`",
        f"- Canary dry-run: `{report.canary_execution.status}`",
        f"- Rollback status: `{report.rollback_status.status}`",
        "",
        "## Failure Clusters",
        "",
        "| Cluster | Count | Loop Types | Recommended Variable |",
        "|---|---:|---|---|",
    ]
    for cluster in report.failure_clusters:
        lines.append(
            f"| `{cluster.failure_signature}` | {cluster.count} | "
            f"{', '.join(cluster.loop_types)} | `{cluster.recommended_variable}` |"
        )
    lines.extend(
        [
            "",
            "## Rollback Rules",
            "",
            *[f"- {rule}" for rule in report.rollback_status.rules],
            "",
        ]
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")
    return path


def _variable_for_signature(signature: str) -> str:
    return {
        "evidence_missing": "retrieval_top_k",
        "replay_gap": "checkpoint_resume_cursor_required",
        "quest_evidence_gap": "quest_required_evidence_type",
        "policy_drift": "candidate_prompt_temperature",
    }.get(signature, "retrieval_top_k")


def _config_patch_for(variable: str) -> dict[str, int | float | str]:
    if variable == "retrieval_top_k":
        return {"retrieval_top_k": 6}
    if variable == "checkpoint_resume_cursor_required":
        return {"checkpoint_resume_cursor_required": "true"}
    if variable == "quest_required_evidence_type":
        return {"quest_required_evidence_type": "strict"}
    if variable == "candidate_prompt_temperature":
        return {"candidate_prompt_temperature": 0.2}
    return {variable: "enabled"}


def _mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else 0.0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("dataset", nargs="?", default=str(DEFAULT_M3_DATASET))
    parser.add_argument("--markdown", type=Path, default=None)
    args = parser.parse_args()
    report = run_m3_system_evolution_gate(args.dataset)
    if args.markdown is not None:
        write_markdown_report(report, args.markdown)
    print(report.model_dump_json(indent=2))
    return 0 if report.passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
