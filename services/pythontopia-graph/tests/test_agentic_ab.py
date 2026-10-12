from __future__ import annotations

from pathlib import Path

from pythontopia_graph.eval.agentic_ab import (
    AgenticAbCase,
    AgenticAbGates,
    default_agentic_ab_cases,
    run_agentic_ab,
    write_markdown_report,
)
from pythontopia_graph.schemas.loop_contract import EvalRecord


def test_default_agentic_ab_cases_cover_all_loop_types_with_deep_sample_count() -> None:
    cases = default_agentic_ab_cases()

    assert len(cases) >= 25
    assert {case.loop_type for case in cases} == {
        "LEARNER",
        "DEBUG",
        "RESOURCE",
        "WORLD",
        "IMPROVE",
    }
    assert min(
        sum(1 for case in cases if case.loop_type == loop_type)
        for loop_type in {case.loop_type for case in cases}
    ) >= 5
    assert len({case.case_id for case in cases}) == len(cases)


def test_repeated_agentic_ab_report_proves_treatment_stably_beats_baseline() -> None:
    report = run_agentic_ab(repeats=90, seed=20260629)

    assert report.baseline_policy == "legacy_single_shot"
    assert report.treatment_policy == "agentic_loop_v1"
    assert report.case_count >= 25
    assert report.repeat_count == 90
    assert report.passed is True
    assert report.stability_rate == 1.0
    assert report.mean_uplift >= 0.12
    assert report.regression_rate == 0.0
    assert all(run.mean_delta > 0 for run in report.runs)
    assert all(loop.mean_uplift > 0 for loop in report.loop_summaries)
    assert {
        metric.name for metric in report.gate_metrics
    } >= {
        "mean_uplift",
        "stability_rate",
        "regression_rate",
        "schema_validity",
    }
    assert all(
        case.baseline_output is not None and case.treatment_output is not None
        for case in report.source_cases
    )


def test_agentic_ab_gates_reject_unstable_or_regressing_candidate() -> None:
    cases = [
        AgenticAbCase(
            case_id="unstable-1",
            loop_type="LEARNER",
            scenario="treatment regresses one learning case",
            required_evidence_ids=[101],
            expected_action_keywords=["补救", "微测"],
            baseline_output={
                "evidence_page_ids": [101],
                "next_action": "基于第 101 页安排补救微测",
                "safety_violations": [],
                "schema_valid": True,
                "tool_call_count": 2,
                "token_count": 900,
            },
            treatment_output={
                "evidence_page_ids": [],
                "next_action": "继续阅读即可",
                "safety_violations": [],
                "schema_valid": True,
                "tool_call_count": 2,
                "token_count": 900,
            },
        )
    ]

    report = run_agentic_ab(
        cases=cases,
        repeats=3,
        seed=7,
        gates=AgenticAbGates(
            min_mean_uplift=0.01,
            min_stability_rate=1.0,
            max_regression_rate=0.0,
        ),
    )

    assert report.passed is False
    assert report.mean_uplift < 0
    assert report.regression_rate == 1.0
    assert any("regression_rate" in reason for reason in report.gate_reasons)


def test_agentic_ab_scores_actual_candidate_outputs_not_embedded_scores() -> None:
    cases = [
        AgenticAbCase(
            case_id="candidate-output-scoring",
            loop_type="RESOURCE",
            scenario="Scorer should inspect evidence, action, safety, schema, and cost.",
            required_evidence_ids=[101, 102],
            expected_action_keywords=["引用", "修订"],
            baseline_output={
                "evidence_page_ids": [101],
                "next_action": "生成讲义",
                "safety_violations": [],
                "schema_valid": True,
                "tool_call_count": 1,
                "token_count": 600,
            },
            treatment_output={
                "evidence_page_ids": [101, 102],
                "next_action": "引用课件后修订讲义",
                "safety_violations": [],
                "schema_valid": True,
                "tool_call_count": 3,
                "token_count": 1300,
            },
        )
    ]

    report = run_agentic_ab(cases=cases, repeats=1, seed=1)

    run = report.cases[0]
    assert run.treatment_score > run.baseline_score
    assert run.treatment_metrics["evidence_binding"] == 1.0
    assert run.baseline_metrics["evidence_binding"] < 1.0


def test_agentic_ab_schema_validity_is_computed_from_outputs() -> None:
    cases = [
        AgenticAbCase(
            case_id="bad-schema",
            loop_type="IMPROVE",
            scenario="Schema-invalid candidate must fail the schema gate.",
            required_evidence_ids=[],
            expected_action_keywords=["回滚"],
            baseline_output={
                "evidence_page_ids": [],
                "next_action": "回滚策略",
                "safety_violations": [],
                "schema_valid": True,
                "tool_call_count": 1,
                "token_count": 500,
            },
            treatment_output={
                "evidence_page_ids": [],
                "next_action": "回滚策略",
                "safety_violations": [],
                "schema_valid": False,
                "tool_call_count": 1,
                "token_count": 500,
            },
        )
    ]

    report = run_agentic_ab(cases=cases, repeats=1, seed=1)

    assert report.schema_validity == 0.0
    assert report.passed is False
    assert any("schema_validity" in reason for reason in report.gate_reasons)


def test_agentic_ab_exports_loop_contract_eval_records() -> None:
    report = run_agentic_ab(repeats=4, seed=20260629)

    records = report.to_eval_records()

    assert len(records) == 5
    assert all(isinstance(record, EvalRecord) for record in records)
    assert {record.loop_type for record in records} == {
        "LEARNER",
        "DEBUG",
        "RESOURCE",
        "WORLD",
        "IMPROVE",
    }
    assert all(record.dataset_id == "next-gen-agentic-ab-v1" for record in records)
    assert all(record.policy_version == report.treatment_policy for record in records)
    assert all(record.passed for record in records)


def test_agentic_ab_markdown_report_contains_run_table_and_gate_summary(tmp_path: Path) -> None:
    report = run_agentic_ab(repeats=3, seed=20260629)
    out = tmp_path / "ab.md"

    written = write_markdown_report(report, out)

    assert written == out
    text = out.read_text(encoding="utf-8")
    assert "legacy_single_shot" in text
    assert "agentic_loop_v1" in text
    assert "Run" in text
    assert "Loop Type" in text
    assert "PASS" in text
    assert "mean_uplift" in text
