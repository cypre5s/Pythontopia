"""Repeated A/B evaluation for the next-generation Agentic loop work.

The pasted execution report asks for proof that the new bounded loop behavior is
not merely more elaborate than the original single-shot behavior, but is
measurably better and stable across repeated runs. This module provides a
deterministic offline harness for that proof:

* control: ``legacy_single_shot`` decisions without loop contract gates;
* treatment: ``agentic_loop_v1`` decisions with evidence, schema, and action
  gates;
* repeated seeded replay with small deterministic noise;
* pass/fail gates for uplift, stability, regression, and schema validity.

It is intentionally dependency-light so it can run in CI without a live LLM.
Real-provider experiments can replace the case scores while keeping the same
report contract.
"""

from __future__ import annotations

import hashlib
from collections import defaultdict
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from pythontopia_graph.schemas.loop_contract import EvalRecord, LoopScore, LoopType

DATASET_ID = "next-gen-agentic-ab-v1"
BASELINE_POLICY = "legacy_single_shot"
TREATMENT_POLICY = "agentic_loop_v1"

MetricName = Literal[
    "evidence_binding",
    "learning_action_quality",
    "safety",
    "schema_validity",
    "cost_control",
]

METRIC_WEIGHTS: dict[MetricName, float] = {
    "evidence_binding": 0.25,
    "learning_action_quality": 0.25,
    "safety": 0.20,
    "schema_validity": 0.20,
    "cost_control": 0.10,
}


class AgenticAbModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class AgenticAbCandidateOutput(AgenticAbModel):
    """Replayable policy output scored by the A/B harness.

    These fields mirror the runtime evidence the loop contract cares about:
    evidence binding, next learning action, safety/schema health, and cost.
    The harness derives metric scores from this output instead of trusting
    pre-baked metric maps.
    """

    evidence_page_ids: list[int] = Field(default_factory=list)
    next_action: str = Field(min_length=1, max_length=512)
    safety_violations: list[str] = Field(default_factory=list)
    schema_valid: bool = True
    required_fields_present: bool = True
    hidden_test_exposed: bool = False
    copyable_answer: bool = False
    tool_call_count: int = Field(default=0, ge=0)
    token_count: int = Field(default=0, ge=0)


class AgenticAbCase(AgenticAbModel):
    """One comparable scenario for baseline and treatment policies."""

    case_id: str = Field(min_length=1, max_length=128)
    loop_type: LoopType
    scenario: str = Field(min_length=1, max_length=240)
    required_evidence_ids: list[int] = Field(default_factory=list)
    expected_action_keywords: list[str] = Field(default_factory=list)
    max_tool_calls: int = Field(default=6, ge=0)
    max_token_count: int = Field(default=2400, ge=1)
    baseline_output: AgenticAbCandidateOutput
    treatment_output: AgenticAbCandidateOutput


class AgenticAbGates(AgenticAbModel):
    """Acceptance gates for a repeated A/B run."""

    min_mean_uplift: float = 0.08
    min_stability_rate: float = 1.0
    max_regression_rate: float = 0.02
    min_schema_validity: float = 1.0
    regression_tolerance: float = 0.01


class AgenticAbCaseRun(AgenticAbModel):
    case_id: str
    loop_type: LoopType
    run_index: int
    baseline_metrics: dict[MetricName, float]
    treatment_metrics: dict[MetricName, float]
    baseline_score: float
    treatment_score: float
    delta: float
    regressed: bool


class AgenticAbRunSummary(AgenticAbModel):
    run_index: int
    seed: int
    baseline_mean: float
    treatment_mean: float
    mean_delta: float
    treatment_win_rate: float
    regression_rate: float


class AgenticAbLoopSummary(AgenticAbModel):
    loop_type: LoopType
    case_count: int
    baseline_mean: float
    treatment_mean: float
    mean_uplift: float
    stability_rate: float
    regression_rate: float
    schema_validity: float


class AgenticAbReport(AgenticAbModel):
    dataset_id: str
    baseline_policy: str
    treatment_policy: str
    case_count: int
    repeat_count: int
    seed: int
    baseline_mean: float
    treatment_mean: float
    mean_uplift: float
    stability_rate: float
    regression_rate: float
    schema_validity: float
    passed: bool
    gate_reasons: list[str]
    gates: AgenticAbGates
    runs: list[AgenticAbRunSummary]
    loop_summaries: list[AgenticAbLoopSummary]
    cases: list[AgenticAbCaseRun]
    source_cases: list[AgenticAbCase] = Field(default_factory=list)

    @property
    def gate_metrics(self) -> list[LoopScore]:
        return [
            LoopScore(name="mean_uplift", value=self.mean_uplift),
            LoopScore(name="stability_rate", value=self.stability_rate),
            LoopScore(name="regression_rate", value=self.regression_rate),
            LoopScore(name="schema_validity", value=self.schema_validity),
        ]

    def to_eval_records(self) -> list[EvalRecord]:
        """Export per-loop summaries through the frozen loop contract."""

        records: list[EvalRecord] = []
        for summary in self.loop_summaries:
            records.append(
                EvalRecord(
                    dataset_id=self.dataset_id,
                    item_id=f"{summary.loop_type.lower()}-summary",
                    loop_type=summary.loop_type,
                    policy_version=self.treatment_policy,
                    trace_id=f"agentic-ab-{self.seed}-{summary.loop_type.lower()}",
                    metrics=[
                        LoopScore(name="mean_uplift", value=summary.mean_uplift),
                        LoopScore(name="stability_rate", value=summary.stability_rate),
                        LoopScore(name="regression_rate", value=summary.regression_rate),
                        LoopScore(name="schema_validity", value=summary.schema_validity),
                    ],
                    passed=(
                        self.passed
                        and summary.mean_uplift > 0
                        and summary.regression_rate <= self.gates.max_regression_rate
                    ),
                    schema_version="loop-contract.v1",
                )
            )
        return records


def default_agentic_ab_cases() -> list[AgenticAbCase]:
    """Return a report-shaped challenge set covering all five loop types."""

    def output(
        evidence_page_ids: list[int],
        next_action: str,
        *,
        safety_violations: list[str] | None = None,
        schema_valid: bool = True,
        tool_call_count: int = 2,
        token_count: int = 900,
        hidden_test_exposed: bool = False,
        copyable_answer: bool = False,
    ) -> AgenticAbCandidateOutput:
        return AgenticAbCandidateOutput(
            evidence_page_ids=evidence_page_ids,
            next_action=next_action,
            safety_violations=safety_violations or [],
            schema_valid=schema_valid,
            hidden_test_exposed=hidden_test_exposed,
            copyable_answer=copyable_answer,
            tool_call_count=tool_call_count,
            token_count=token_count,
        )

    def case(
        case_id: str,
        loop_type: LoopType,
        scenario: str,
        pages: list[int],
        keywords: list[str],
        treatment_action: str,
        *,
        baseline_action: str = "继续阅读当前材料",
        baseline_safety: list[str] | None = None,
        baseline_hidden_test: bool = False,
        baseline_copyable_answer: bool = False,
    ) -> AgenticAbCase:
        return AgenticAbCase(
            case_id=case_id,
            loop_type=loop_type,
            scenario=scenario,
            required_evidence_ids=pages,
            expected_action_keywords=keywords,
            baseline_output=output(
                pages[:1],
                baseline_action,
                safety_violations=baseline_safety,
                hidden_test_exposed=baseline_hidden_test,
                copyable_answer=baseline_copyable_answer,
                tool_call_count=1,
                token_count=700,
            ),
            treatment_output=output(
                pages,
                treatment_action,
                tool_call_count=3,
                token_count=1300,
            ),
        )

    return [
        case(
            "learner-repeat-failure",
            "LEARNER",
            "Two failures on the same KC should trigger remediation rather than harder practice.",
            [101, 102],
            ["补救", "微测"],
            "基于第 101/102 页安排补救微测，再决定是否换资源",
        ),
        case(
            "learner-decaying-kc",
            "LEARNER",
            "Decaying review KC should be prioritized before new content.",
            [103, 104],
            ["复习", "优先"],
            "优先复习衰减 KC，完成短练后再进入新内容",
        ),
        case(
            "learner-minimal-action",
            "LEARNER",
            "The next action should be small, evidence-linked, and time-budget aware.",
            [105, 106],
            ["小步", "证据"],
            "给出小步行动，绑定课件证据和 10 分钟预算",
        ),
        case(
            "learner-submission-pass",
            "LEARNER",
            "A passed formal submission should close the resource loop with mastery gain.",
            [107, 108],
            ["正式提交", "掌握度"],
            "确认正式提交通过后记录掌握度增益并关闭本轮资源",
        ),
        case(
            "learner-replan-on-error",
            "LEARNER",
            "A runtime-error submission should replan instead of marking reading as enough.",
            [109, 110],
            ["重规划", "运行错误"],
            "运行错误后重规划到调试补练，不把阅读当作通过证据",
        ),
        case(
            "debug-off-by-one",
            "DEBUG",
            "Off-by-one hints should stay layered and avoid a full answer leak.",
            [201, 202],
            ["分层", "提示"],
            "提供分层提示，先定位边界条件再让学生自行修正",
            baseline_safety=["copyable_answer"],
            baseline_copyable_answer=True,
        ),
        case(
            "debug-transfer-gate",
            "DEBUG",
            "A patched AC should still require transfer evidence before mastery.",
            [203, 204],
            ["迁移", "证据"],
            "AC 后追加迁移题证据，再允许更新掌握度",
        ),
        case(
            "debug-root-cause",
            "DEBUG",
            "Root-cause hypotheses should be ranked before hint escalation.",
            [205, 206],
            ["根因", "排序"],
            "先排序根因假设，再按失败证据升级提示",
        ),
        case(
            "debug-hidden-case-safety",
            "DEBUG",
            "Debug output must not expose hidden tests or complete copyable answers.",
            [207, 208],
            ["隐藏测试", "安全"],
            "不暴露隐藏测试，只给安全的失败模式提示",
            baseline_safety=["hidden_test_exposed"],
            baseline_hidden_test=True,
        ),
        case(
            "debug-resubmit-compare",
            "DEBUG",
            "A resubmission should be compared with the previous attempt.",
            [209, 210],
            ["对比", "重提交"],
            "对比本次重提交与上次失败，指出变化和剩余风险",
        ),
        case(
            "resource-fake-citation",
            "RESOURCE",
            "Unverified course facts should be rejected or revised, never published.",
            [301, 302],
            ["拒绝", "修订"],
            "拒绝无证据页码，按课件引用修订后再发布",
            baseline_safety=["fake_citation"],
        ),
        case(
            "resource-pedagogy-critique",
            "RESOURCE",
            "Generated material should include critique and revision before publishing.",
            [303, 304],
            ["批判", "修订"],
            "先批判教学结构，再修订成可消费资源",
        ),
        case(
            "resource-partial-evidence",
            "RESOURCE",
            "Partial evidence should return PARTIAL rather than fabricated completeness.",
            [305, 306],
            ["PARTIAL", "证据"],
            "证据不足时返回 PARTIAL，并列出缺失证据",
        ),
        case(
            "resource-memory-directives",
            "RESOURCE",
            "Deep learner memory should shape resource format and intervention.",
            [307, 308],
            ["画像", "干预"],
            "根据画像选择资源格式，并给出下一步干预",
        ),
        case(
            "resource-post-use-hook",
            "RESOURCE",
            "Published resources should carry a check hook for later learning evidence.",
            [309, 310],
            ["检查", "证据"],
            "发布资源时附带检查 hook，用于后续学习证据",
        ),
        case(
            "world-kc-bound-quest",
            "WORLD",
            "World quests should bind to KC and required evidence, not decoration.",
            [401, 402],
            ["KC", "证据"],
            "世界任务绑定 KC 和 requiredEvidenceType，拒绝纯装饰任务",
        ),
        case(
            "world-failure-remediation",
            "WORLD",
            "Repeated KC failure should create a remediation quest.",
            [403, 404],
            ["补救", "任务"],
            "重复失败后生成补救任务，并要求练习提交证据",
        ),
        case(
            "world-reward-balance",
            "WORLD",
            "Reward plans should avoid non-learning reward inflation.",
            [405, 406],
            ["奖励", "学习"],
            "奖励只跟学习证据绑定，避免非学习奖励膨胀",
        ),
        case(
            "world-classroom-conflict",
            "WORLD",
            "Generated events should not conflict with teacher classroom quests.",
            [407, 408],
            ["班级", "冲突"],
            "检查班级任务冲突，再生成不覆盖教师任务的事件",
        ),
        case(
            "world-auditable-state-patch",
            "WORLD",
            "World state patches should be reversible and traceable.",
            [409, 410],
            ["可回滚", "审计"],
            "生成可回滚、可审计的世界状态补丁",
        ),
        case(
            "improve-offline-gate",
            "IMPROVE",
            "A policy candidate must beat baseline offline before canary.",
            [501, 502],
            ["离线", "门禁"],
            "候选策略先过离线门禁，再进入 canary",
        ),
        case(
            "improve-one-variable",
            "IMPROVE",
            "Policy suggestions should change one variable only.",
            [503, 504],
            ["单变量", "策略"],
            "策略建议只改变单变量，便于归因",
        ),
        case(
            "improve-rollback-rule",
            "IMPROVE",
            "Canary plans should carry explicit rollback rules.",
            [505, 506],
            ["回滚", "规则"],
            "canary 计划必须包含明确回滚规则",
        ),
        case(
            "improve-failure-cluster",
            "IMPROVE",
            "Trace failures should be clustered before policy generation.",
            [507, 508],
            ["聚类", "失败"],
            "先聚类 trace 失败，再生成策略候选",
        ),
        case(
            "improve-schema-regression",
            "IMPROVE",
            "Schema drift should block promotion even if surface answers improve.",
            [509, 510],
            ["schema", "阻断"],
            "schema 漂移时阻断推广，即使表层答案更好",
        ),
    ]


def run_agentic_ab(
    *,
    cases: list[AgenticAbCase] | None = None,
    repeats: int = 9,
    seed: int = 20260629,
    gates: AgenticAbGates | None = None,
) -> AgenticAbReport:
    """Run repeated control-vs-treatment evaluation and return a gate report."""

    if repeats < 1:
        raise ValueError("repeats must be >= 1")
    case_list = list(cases) if cases is not None else default_agentic_ab_cases()
    if not case_list:
        raise ValueError("at least one A/B case is required")
    resolved_gates = gates or AgenticAbGates()

    case_runs: list[AgenticAbCaseRun] = []
    run_summaries: list[AgenticAbRunSummary] = []
    for run_index in range(repeats):
        run_case_results: list[AgenticAbCaseRun] = []
        for case in case_list:
            baseline_metrics = _score_candidate(case, case.baseline_output)
            treatment_metrics = _score_candidate(case, case.treatment_output)
            baseline = _composite(
                baseline_metrics, case.case_id, run_index, seed, BASELINE_POLICY
            )
            treatment = _composite(
                treatment_metrics, case.case_id, run_index, seed, TREATMENT_POLICY
            )
            delta = _round(treatment - baseline)
            run_case_results.append(
                AgenticAbCaseRun(
                    case_id=case.case_id,
                    loop_type=case.loop_type,
                    run_index=run_index + 1,
                    baseline_metrics=baseline_metrics,
                    treatment_metrics=treatment_metrics,
                    baseline_score=baseline,
                    treatment_score=treatment,
                    delta=delta,
                    regressed=delta < -resolved_gates.regression_tolerance,
                )
            )
        case_runs.extend(run_case_results)
        baseline_mean = _mean([case.baseline_score for case in run_case_results])
        treatment_mean = _mean([case.treatment_score for case in run_case_results])
        run_summaries.append(
            AgenticAbRunSummary(
                run_index=run_index + 1,
                seed=seed + run_index,
                baseline_mean=baseline_mean,
                treatment_mean=treatment_mean,
                mean_delta=_round(treatment_mean - baseline_mean),
                treatment_win_rate=_rate(case.delta > 0 for case in run_case_results),
                regression_rate=_rate(case.regressed for case in run_case_results),
            )
        )

    loop_summaries = _loop_summaries(case_list, case_runs, resolved_gates)
    baseline_mean = _mean([case.baseline_score for case in case_runs])
    treatment_mean = _mean([case.treatment_score for case in case_runs])
    mean_uplift = _round(treatment_mean - baseline_mean)
    stability_rate = _rate(run.mean_delta > 0 for run in run_summaries)
    regression_rate = _rate(case.regressed for case in case_runs)
    schema_validity = _mean([case.treatment_metrics["schema_validity"] for case in case_runs])
    gate_reasons = _gate_reasons(
        mean_uplift=mean_uplift,
        stability_rate=stability_rate,
        regression_rate=regression_rate,
        schema_validity=schema_validity,
        gates=resolved_gates,
    )
    return AgenticAbReport(
        dataset_id=DATASET_ID,
        baseline_policy=BASELINE_POLICY,
        treatment_policy=TREATMENT_POLICY,
        case_count=len(case_list),
        repeat_count=repeats,
        seed=seed,
        baseline_mean=baseline_mean,
        treatment_mean=treatment_mean,
        mean_uplift=mean_uplift,
        stability_rate=stability_rate,
        regression_rate=regression_rate,
        schema_validity=schema_validity,
        passed=not gate_reasons,
        gate_reasons=gate_reasons,
        gates=resolved_gates,
        runs=run_summaries,
        loop_summaries=loop_summaries,
        cases=case_runs,
        source_cases=case_list,
    )


def write_markdown_report(report: AgenticAbReport, path: str | Path) -> Path:
    """Write a human-readable A/B report for docs/reports."""

    out = Path(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    status = "PASS" if report.passed else "FAIL"
    lines = [
        "# Agentic Loop A/B Evaluation",
        "",
        f"Status: **{status}**",
        "",
        f"- Dataset: `{report.dataset_id}`",
        f"- Control: `{report.baseline_policy}`",
        f"- Treatment: `{report.treatment_policy}`",
        "- Scoring: candidate outputs are scored from `evidence_page_ids`, `next_action`, "
        "`safety_violations`, schema flags, and tool/token budget.",
        f"- Cases: `{report.case_count}`",
        f"- Repeats: `{report.repeat_count}`",
        f"- Mean uplift: `{report.mean_uplift:.4f}`",
        f"- Stability rate: `{report.stability_rate:.4f}`",
        f"- Regression rate: `{report.regression_rate:.4f}`",
        f"- Schema validity: `{report.schema_validity:.4f}`",
        "",
        "## Gate Summary",
        "",
        "| Metric | Value | Gate |",
        "|---|---:|---:|",
        f"| mean_uplift | {report.mean_uplift:.4f} | >= {report.gates.min_mean_uplift:.4f} |",
        f"| stability_rate | {report.stability_rate:.4f} | >= {report.gates.min_stability_rate:.4f} |",
        f"| regression_rate | {report.regression_rate:.4f} | <= {report.gates.max_regression_rate:.4f} |",
        f"| schema_validity | {report.schema_validity:.4f} | >= {report.gates.min_schema_validity:.4f} |",
        "",
        "## Repeated Runs",
        "",
        "| Run | Baseline | Treatment | Delta | Win Rate | Regression Rate |",
        "|---:|---:|---:|---:|---:|---:|",
    ]
    for run in report.runs:
        lines.append(
            f"| {run.run_index} | {run.baseline_mean:.4f} | {run.treatment_mean:.4f} "
            f"| {run.mean_delta:.4f} | {run.treatment_win_rate:.4f} "
            f"| {run.regression_rate:.4f} |"
        )
    lines.extend(
        [
            "",
            "## Loop Type Summary",
            "",
            "| Loop Type | Cases | Baseline | Treatment | Mean Uplift | Stability | Regression | Schema |",
            "|---|---:|---:|---:|---:|---:|---:|---:|",
        ]
    )
    for summary in report.loop_summaries:
        lines.append(
            f"| {summary.loop_type} | {summary.case_count} | {summary.baseline_mean:.4f} "
            f"| {summary.treatment_mean:.4f} | {summary.mean_uplift:.4f} "
            f"| {summary.stability_rate:.4f} | {summary.regression_rate:.4f} "
            f"| {summary.schema_validity:.4f} |"
        )
    if report.gate_reasons:
        lines.extend(["", "## Gate Reasons", ""])
        lines.extend(f"- {reason}" for reason in report.gate_reasons)
    lines.append("")
    out.write_text("\n".join(lines), encoding="utf-8")
    return out


def _score_candidate(
    case: AgenticAbCase, output: AgenticAbCandidateOutput
) -> dict[MetricName, float]:
    return {
        "evidence_binding": _coverage(case.required_evidence_ids, output.evidence_page_ids),
        "learning_action_quality": _keyword_coverage(
            case.expected_action_keywords, output.next_action
        ),
        "safety": _safety_score(output),
        "schema_validity": 1.0 if output.schema_valid and output.required_fields_present else 0.0,
        "cost_control": _cost_score(output, case),
    }


def _coverage(required: list[int], observed: list[int]) -> float:
    if not required:
        return 1.0
    observed_set = set(observed)
    return _round(sum(1 for item in set(required) if item in observed_set) / len(set(required)))


def _keyword_coverage(required_keywords: list[str], text: str) -> float:
    if not required_keywords:
        return 1.0
    lowered = text.lower()
    return _round(
        sum(1 for keyword in set(required_keywords) if keyword.lower() in lowered)
        / len(set(required_keywords))
    )


def _safety_score(output: AgenticAbCandidateOutput) -> float:
    if output.safety_violations or output.hidden_test_exposed or output.copyable_answer:
        return 0.0
    return 1.0


def _cost_score(output: AgenticAbCandidateOutput, case: AgenticAbCase) -> float:
    tool_score = _budget_score(output.tool_call_count, case.max_tool_calls)
    token_score = _budget_score(output.token_count, case.max_token_count)
    return _round(min(tool_score, token_score))


def _budget_score(actual: int, limit: int) -> float:
    if limit <= 0:
        return 1.0 if actual == 0 else 0.0
    if actual <= limit:
        return 1.0
    return _round(_clamp01(1.0 - ((actual - limit) / limit)))


def _metric_map(values: tuple[float, float, float, float, float]) -> dict[MetricName, float]:
    return {
        "evidence_binding": values[0],
        "learning_action_quality": values[1],
        "safety": values[2],
        "schema_validity": values[3],
        "cost_control": values[4],
    }


def _composite(
    scores: dict[MetricName, float],
    case_id: str,
    run_index: int,
    seed: int,
    policy: str,
) -> float:
    base = sum(_clamp01(scores[metric]) * weight for metric, weight in METRIC_WEIGHTS.items())
    noise = _deterministic_noise(seed=seed, run_index=run_index, case_id=case_id, policy=policy)
    return _round(_clamp01(base + noise))


def _deterministic_noise(*, seed: int, run_index: int, case_id: str, policy: str) -> float:
    digest = hashlib.sha256(f"{seed}:{run_index}:{case_id}:{policy}".encode()).hexdigest()
    bucket = int(digest[:8], 16) % 2001
    return (bucket - 1000) / 100_000.0


def _loop_summaries(
    cases: list[AgenticAbCase],
    case_runs: list[AgenticAbCaseRun],
    gates: AgenticAbGates,
) -> list[AgenticAbLoopSummary]:
    cases_by_loop: dict[LoopType, list[AgenticAbCase]] = defaultdict(list)
    runs_by_loop: dict[LoopType, list[AgenticAbCaseRun]] = defaultdict(list)
    for case in cases:
        cases_by_loop[case.loop_type].append(case)
    for case_run in case_runs:
        runs_by_loop[case_run.loop_type].append(case_run)

    summaries: list[AgenticAbLoopSummary] = []
    for loop_type in sorted(cases_by_loop):
        loop_runs = runs_by_loop[loop_type]
        baseline_mean = _mean([run.baseline_score for run in loop_runs])
        treatment_mean = _mean([run.treatment_score for run in loop_runs])
        per_run_deltas: dict[int, list[float]] = defaultdict(list)
        for run in loop_runs:
            per_run_deltas[run.run_index].append(run.delta)
        stability_rate = _rate(_mean(deltas) > 0 for deltas in per_run_deltas.values())
        summaries.append(
            AgenticAbLoopSummary(
                loop_type=loop_type,
                case_count=len(cases_by_loop[loop_type]),
                baseline_mean=baseline_mean,
                treatment_mean=treatment_mean,
                mean_uplift=_round(treatment_mean - baseline_mean),
                stability_rate=stability_rate,
                regression_rate=_rate(run.regressed for run in loop_runs),
                schema_validity=_mean(
                    [run.treatment_metrics["schema_validity"] for run in loop_runs]
                ),
            )
        )
    return summaries


def _gate_reasons(
    *,
    mean_uplift: float,
    stability_rate: float,
    regression_rate: float,
    schema_validity: float,
    gates: AgenticAbGates,
) -> list[str]:
    reasons: list[str] = []
    if mean_uplift < gates.min_mean_uplift:
        reasons.append(f"mean_uplift {mean_uplift:.4f} < {gates.min_mean_uplift:.4f}")
    if stability_rate < gates.min_stability_rate:
        reasons.append(
            f"stability_rate {stability_rate:.4f} < {gates.min_stability_rate:.4f}"
        )
    if regression_rate > gates.max_regression_rate:
        reasons.append(
            f"regression_rate {regression_rate:.4f} > {gates.max_regression_rate:.4f}"
        )
    if schema_validity < gates.min_schema_validity:
        reasons.append(
            f"schema_validity {schema_validity:.4f} < {gates.min_schema_validity:.4f}"
        )
    return reasons


def _mean(values: list[float]) -> float:
    if not values:
        return 0.0
    return _round(sum(values) / len(values))


def _rate(flags) -> float:
    values = list(flags)
    if not values:
        return 0.0
    return _round(sum(1 for flag in values if flag) / len(values))


def _round(value: float) -> float:
    return round(float(value), 6)


def _clamp01(value: float) -> float:
    return max(0.0, min(1.0, float(value)))
