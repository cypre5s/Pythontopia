import csv
import json
from pathlib import Path

from pyt_agent_lab.experiment_logging import ExperimentLogger


def test_experiment_logger_writes_jsonl_events_and_metrics_csv(tmp_path: Path) -> None:
    logger = ExperimentLogger(tmp_path)

    logger.event("case_started", {"case_id": "c1", "fold": 0})
    logger.metric({"case_id": "c1", "fold": 0, "score": 0.75, "json_valid": True})
    logger.event("case_finished", {"case_id": "c1", "fold": 0})

    events = [json.loads(line) for line in (tmp_path / "events.jsonl").read_text(encoding="utf-8").splitlines()]
    assert [event["event"] for event in events] == ["case_started", "case_finished"]
    assert all("ts" in event for event in events)

    rows = list(csv.DictReader((tmp_path / "metrics.csv").open(encoding="utf-8")))
    assert rows == [{"case_id": "c1", "fold": "0", "score": "0.75", "json_valid": "True"}]


def test_experiment_logger_writes_progress_snapshots(tmp_path: Path) -> None:
    logger = ExperimentLogger(tmp_path, total_cases=2, progress_every=1)

    logger.metric(
        {
            "case_id": "c1",
            "fold": 0,
            "score": 1.0,
            "json_valid": True,
            "grounding_safe": True,
            "forbidden_leak": False,
            "latency_ms": 100,
        }
    )
    logger.metric(
        {
            "case_id": "c2",
            "fold": 0,
            "score": 0.5,
            "json_valid": False,
            "grounding_safe": True,
            "forbidden_leak": False,
            "latency_ms": 200,
        }
    )

    snapshots = [
        json.loads(line)
        for line in (tmp_path / "progress_snapshots.jsonl").read_text(encoding="utf-8").splitlines()
    ]
    assert len(snapshots) == 2
    assert snapshots[-1]["completed_cases"] == 2
    assert snapshots[-1]["completion_rate"] == 1.0
    assert snapshots[-1]["score_mean"] == 0.75
