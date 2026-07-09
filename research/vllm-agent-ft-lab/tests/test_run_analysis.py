from __future__ import annotations

from pathlib import Path

from pyt_agent_lab.run_analysis import build_extended_summary, write_additional_charts


def test_build_extended_summary_groups_reliability_by_noise_profile_and_agent() -> None:
    rows = [
        {
            "fold": "0",
            "case_id": "domain-0001-tutoragent-c1-beginner-vague_reference",
            "agent": "TutorAgent",
            "score": "1.0",
            "json_valid": "True",
            "schema_valid": "True",
            "grounding_safe": "False",
            "forbidden_leak": "False",
            "latency_ms": "1000",
        },
        {
            "fold": "0",
            "case_id": "domain-0002-tutoragent-c1-beginner-vague_reference",
            "agent": "TutorAgent",
            "score": "0.6",
            "json_valid": "False",
            "schema_valid": "False",
            "grounding_safe": "True",
            "forbidden_leak": "True",
            "latency_ms": "2000",
        },
        {
            "fold": "1",
            "case_id": "domain-0003-safetyagent-c2-beginner-fragmented_sentence",
            "agent": "SafetyAgent",
            "score": "0.8",
            "json_valid": "True",
            "schema_valid": "False",
            "grounding_safe": "True",
            "forbidden_leak": "False",
            "latency_ms": "3000",
        },
    ]

    summary = build_extended_summary(rows)

    assert summary["overall"]["case_count"] == 3
    assert summary["overall"]["json_invalid_count"] == 1
    assert summary["overall"]["schema_invalid_count"] == 2
    assert summary["overall"]["schema_valid_rate"] == 0.333333
    assert summary["overall"]["grounding_false_count"] == 1
    assert summary["overall"]["forbidden_leak_count"] == 1
    assert summary["by_noise_profile"]["vague_reference"]["case_count"] == 2
    assert summary["by_noise_profile"]["vague_reference"]["json_valid_rate"] == 0.5
    assert summary["by_agent"]["TutorAgent"]["forbidden_leak_count"] == 1
    assert summary["by_fold"]["1"]["latency_ms_mean"] == 3000.0


def test_write_additional_charts_creates_svg_files(tmp_path: Path) -> None:
    summary = build_extended_summary(
        [
            {
                "fold": "0",
                "case_id": "domain-0001-tutoragent-c1-beginner-vague_reference",
                "agent": "TutorAgent",
                "score": "1.0",
                "json_valid": "True",
                "schema_valid": "True",
                "grounding_safe": "True",
                "forbidden_leak": "False",
                "latency_ms": "1000",
            },
            {
                "fold": "1",
                "case_id": "domain-0002-safetyagent-c1-beginner-fragmented_sentence",
                "agent": "SafetyAgent",
                "score": "0.7",
                "json_valid": "False",
                "schema_valid": "False",
                "grounding_safe": "False",
                "forbidden_leak": "True",
                "latency_ms": "2500",
            },
        ]
    )

    write_additional_charts(summary, tmp_path)

    assert (tmp_path / "error_counts_by_agent.svg").read_text(encoding="utf-8").startswith("<svg")
    assert (tmp_path / "rates_by_noise_profile.svg").read_text(encoding="utf-8").startswith("<svg")
    assert (tmp_path / "latency_by_fold.svg").read_text(encoding="utf-8").startswith("<svg")
