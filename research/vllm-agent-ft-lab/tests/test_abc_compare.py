from pathlib import Path

from pyt_agent_lab.abc_compare import (
    compare_by_agent,
    compare_three,
    write_abc_report,
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


def _summary(score: float = 0.9, json_rate: float = 0.8, grounding: float = 0.7, leak: float = 0.0) -> dict:
    return {
        "overall": {
            "score_mean": score,
            "json_valid_rate": json_rate,
            "grounding_safe_rate": grounding,
            "forbidden_leak_rate": leak,
            "case_count": 10,
        }
    }


def test_compare_three_all_equal() -> None:
    s = _summary()
    result = compare_three(s, s, s)
    for metric in ["score_mean", "json_valid_rate", "grounding_safe_rate", "forbidden_leak_rate"]:
        assert result["deltas_b_vs_a"][metric] == 0.0
        assert result["deltas_c_vs_a"][metric] == 0.0
        assert result["guard_effect"][metric] == 0.0
    assert result["ab_test"]["verdict"] == "candidate_parity"


def test_compare_three_c_wins() -> None:
    a = _summary(score=0.8, json_rate=0.6, grounding=0.5)
    b = _summary(score=0.82, json_rate=0.65, grounding=0.55)
    c = _summary(score=0.90, json_rate=0.95, grounding=0.90)
    result = compare_three(a, b, c)
    assert result["deltas_c_vs_a"]["score_mean"] > 0
    assert result["deltas_c_vs_a"]["json_valid_rate"] > 0
    assert result["guard_effect"]["score_mean"] > 0  # C better than B
    assert "candidate_wins" in result["ab_test"]["verdict"]


def test_compare_three_c_behind() -> None:
    a = _summary(score=0.95, json_rate=0.95, grounding=0.90)
    b = _summary(score=0.70, json_rate=0.50, grounding=0.40)
    c = _summary(score=0.75, json_rate=0.55, grounding=0.45)  # guard helps but still behind A
    result = compare_three(a, b, c)
    assert result["deltas_c_vs_a"]["score_mean"] < 0
    # Score delta is -0.20 which meets the < -0.05 threshold for baseline_wins
    assert result["ab_test"]["verdict"] in {"baseline_wins", "candidate_behind_reliability"}


def test_compare_three_guard_improves() -> None:
    a = _summary()
    b = _summary(score=0.7, json_rate=0.5, grounding=0.4)
    c = _summary(score=0.85, json_rate=0.9, grounding=0.85)  # big guard improvement
    result = compare_three(a, b, c)
    assert result["guard_effect"]["score_mean"] > 0.1
    assert result["guard_effect"]["json_valid_rate"] > 0.3


def test_write_abc_report_creates_files(tmp_path: Path) -> None:
    s = _summary()
    comparison = compare_three(s, s, s)
    paths = write_abc_report(comparison, tmp_path)
    assert (tmp_path / "abc_comparison.json").exists()
    assert (tmp_path / "abc_comparison.md").exists()
    charts = tmp_path / "charts"
    assert (charts / "metrics_3way_bars.svg").exists()
    assert (charts / "deltas_vs_external.svg").exists()
    assert (charts / "guard_effect.svg").exists()


def test_compare_by_agent(tmp_path: Path) -> None:
    import csv

    # Create minimal metrics CSVs
    agents = ["TutorAgent", "PracticeAgent", "SafetyAgent"]
    for fname, scores in [
        ("a.csv", [0.9, 0.85, 0.95]),
        ("b.csv", [0.7, 0.65, 0.92]),
        ("c.csv", [0.88, 0.82, 0.94]),
    ]:
        p = tmp_path / fname
        with p.open("w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(
                f, fieldnames=["case_id", "agent", "score", "json_valid", "grounding_safe", "forbidden_leak"]
            )
            w.writeheader()
            for agent, score in zip(agents, scores):
                for i in range(3):
                    w.writerow(
                        {
                            "case_id": f"{agent}-{i}",
                            "agent": agent,
                            "score": score,
                            "json_valid": "1",
                            "grounding_safe": "1",
                            "forbidden_leak": "0",
                        }
                    )

    charts = tmp_path / "charts"
    result = compare_by_agent(tmp_path / "a.csv", tmp_path / "b.csv", tmp_path / "c.csv", charts)
    assert "TutorAgent" in result
    assert "PracticeAgent" in result
    assert "SafetyAgent" in result
    for agent in agents:
        assert "A" in result[agent]
        assert "B" in result[agent]
        assert "C" in result[agent]
        assert result[agent]["A"]["case_count"] == 3
    assert (charts / "per_agent_heatmap.svg").exists()


def test_ab_verdict_safety_risk() -> None:
    a = _summary(leak=0.0)
    b = _summary(leak=0.01)
    c = _summary(leak=0.05)  # worse than A
    result = compare_three(a, b, c)
    assert "safety_risk" in result["ab_test"]["verdict"] or result["deltas_c_vs_a"]["forbidden_leak_rate"] > 0
