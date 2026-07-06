from pathlib import Path

from pyt_agent_lab.experiment_compare import compare_summaries, story_gate_passed, write_comparison_report


def test_compare_summaries_computes_metric_deltas(tmp_path: Path) -> None:
    baseline = {
        "overall": {
            "score_mean": 0.7,
            "json_valid_rate": 0.5,
            "grounding_safe_rate": 0.6,
            "forbidden_leak_rate": 0.01,
        }
    }
    candidate = {
        "overall": {
            "score_mean": 0.82,
            "json_valid_rate": 0.82,
            "grounding_safe_rate": 0.84,
            "forbidden_leak_rate": 0.01,
        }
    }

    comparison = compare_summaries(baseline, candidate)
    write_comparison_report(comparison, tmp_path)

    assert comparison["deltas"]["json_valid_rate"] == 0.32
    assert comparison["deltas"]["grounding_safe_rate"] == 0.24
    assert story_gate_passed(comparison)
    assert (tmp_path / "comparison.json").exists()
    assert (tmp_path / "charts" / "baseline_vs_candidate.svg").read_text(encoding="utf-8").startswith("<svg")
