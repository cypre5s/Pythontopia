import json
from pathlib import Path

from pyt_agent_lab.progress_report import load_progress_snapshots, write_progress_report


def test_progress_report_writes_table_summary_and_charts(tmp_path: Path) -> None:
    snapshots_path = tmp_path / "progress_snapshots.jsonl"
    snapshots = [
        {
            "completed_cases": 25,
            "total_cases": 100,
            "score_mean": 0.9,
            "json_valid_rate": 0.8,
            "grounding_safe_rate": 0.7,
            "forbidden_leak_rate": 0.0,
            "latency_ms_mean": 1000,
            "eta_seconds": 30,
        },
        {
            "completed_cases": 50,
            "total_cases": 100,
            "score_mean": 0.95,
            "json_valid_rate": 0.9,
            "grounding_safe_rate": 0.8,
            "forbidden_leak_rate": 0.01,
            "latency_ms_mean": 900,
            "eta_seconds": 20,
        },
    ]
    snapshots_path.write_text("\n".join(json.dumps(item) for item in snapshots), encoding="utf-8")

    loaded = load_progress_snapshots(snapshots_path)
    outputs = write_progress_report(loaded, tmp_path / "report")

    assert Path(outputs["table"]).read_text(encoding="utf-8").startswith("# Progress Table")
    assert json.loads(Path(outputs["summary"]).read_text(encoding="utf-8"))["snapshot_count"] == 2
    assert (Path(outputs["charts"]) / "progress_completion.svg").exists()
    assert (Path(outputs["charts"]) / "progress_quality_rates.svg").exists()
    assert (Path(outputs["charts"]) / "progress_latency.svg").exists()
