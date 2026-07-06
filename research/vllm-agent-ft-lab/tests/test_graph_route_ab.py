from __future__ import annotations

import json

from pyt_agent_lab.graph_route_ab import (
    build_graph_route_ab_report,
    compare_graph_route_summaries,
    default_evidence_hits,
    default_graph_route_cases,
    score_graph_route_result,
    summarize_graph_route_results,
    write_graph_route_ab_report,
)


def _ok_result(case_id: str = "case-1") -> dict:
    return {
        "case_id": case_id,
        "variant": "candidate",
        "http_status": 200,
        "elapsed_ms": 1234,
        "body": {
            "status": "SUCCESS",
            "evidence_page_ids": [101],
            "grounding_result": {"passed": True, "reasons": []},
            "safety_result": {"passed": True, "reasons": []},
            "artifact": {
                "title": "讲义",
                "content_format": "MARKDOWN",
                "handout": {"sections": [{"heading": "h", "paragraphs": ["ok"]}]},
            },
        },
    }


def test_default_cases_and_evidence_are_aligned() -> None:
    cases = default_graph_route_cases()
    hits = default_evidence_hits()

    assert [case.case_id for case in cases] == [
        "web-route-handout-input",
        "web-route-mindmap-branch",
        "web-route-practice-loop",
    ]
    case_kcs = {code for case in cases for code in case.request["kc_codes"]}
    hit_kcs = {code for hit in hits for code in hit["kc_codes"]}
    assert case_kcs <= hit_kcs


def test_score_graph_route_result_marks_clean_success() -> None:
    score = score_graph_route_result(_ok_result())

    assert score["http_ok"] is True
    assert score["json_valid"] is True
    assert score["resource_success"] is True
    assert score["grounding_safe"] is True
    assert score["safety_safe"] is True
    assert score["forbidden_leak"] is False


def test_score_graph_route_result_detects_forbidden_leak() -> None:
    result = _ok_result()
    result["body"]["artifact"]["handout"]["sections"][0]["paragraphs"] = [
        "不要输出 hidden_test 或 answer_key。"
    ]

    score = score_graph_route_result(result)

    assert score["forbidden_leak"] is True


def test_summarize_and_compare_gate_pass() -> None:
    baseline = summarize_graph_route_results(
        variant="baseline",
        results=[_ok_result("a"), _ok_result("b")],
    )
    candidate = summarize_graph_route_results(
        variant="candidate",
        results=[_ok_result("a"), _ok_result("b")],
    )

    comparison = compare_graph_route_summaries(baseline, candidate)

    assert baseline["case_count"] == 2
    assert baseline["grounding_safe_rate"] == 1.0
    assert comparison["gate_passed"] is True
    assert comparison["deltas"]["forbidden_leak_rate"] == 0.0


def test_build_and_write_report(tmp_path) -> None:
    baseline = summarize_graph_route_results(variant="baseline", results=[_ok_result()])
    candidate = summarize_graph_route_results(variant="candidate", results=[_ok_result()])
    report = build_graph_route_ab_report(
        baseline_summary=baseline,
        candidate_summary=candidate,
        model_info={"baseline_model": "base", "candidate_model": "adapter"},
    )

    paths = write_graph_route_ab_report(report, tmp_path)

    assert report["recommendation"] == "web_route_candidate_passed"
    assert json.loads(paths["json"].read_text(encoding="utf-8"))["model_info"][
        "candidate_model"
    ] == "adapter"
    assert "Graph Route A/B Report" in paths["markdown"].read_text(encoding="utf-8")
