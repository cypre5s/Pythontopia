import json
from pathlib import Path

from pyt_agent_lab.folds import kfold
from pyt_agent_lab.reporting import aggregate_fold_metrics, build_report


def test_kfold_is_deterministic_and_covers_each_case_once() -> None:
    cases = [{"id": f"case-{idx}"} for idx in range(10)]

    folds_a = kfold(cases, k=5, seed=20260702)
    folds_b = kfold(cases, k=5, seed=20260702)

    assert folds_a == folds_b
    validation_ids = [case["id"] for fold in folds_a for case in fold.validation]
    assert sorted(validation_ids) == [case["id"] for case in cases]
    assert all(not ({case["id"] for case in fold.train} & {case["id"] for case in fold.validation}) for fold in folds_a)


def test_build_report_writes_summary_and_svg_charts(tmp_path: Path) -> None:
    rows = [
        {"fold": 0, "score": 0.2, "json_valid": False, "grounding_safe": True},
        {"fold": 0, "score": 0.4, "json_valid": True, "grounding_safe": True},
        {"fold": 1, "score": 0.8, "json_valid": True, "grounding_safe": False},
        {"fold": 1, "score": 1.0, "json_valid": True, "grounding_safe": True},
    ]

    summary = aggregate_fold_metrics(rows)
    build_report(summary, tmp_path)

    assert summary["overall"]["score_mean"] == 0.6
    assert summary["folds"]["0"]["score_mean"] == 0.3
    assert (tmp_path / "summary.json").exists()
    assert json.loads((tmp_path / "summary.json").read_text(encoding="utf-8"))["overall"]["case_count"] == 4
    assert (tmp_path / "charts" / "score_by_fold.svg").read_text(encoding="utf-8").startswith("<svg")
    assert (tmp_path / "charts" / "rates_by_fold.svg").read_text(encoding="utf-8").startswith("<svg")
