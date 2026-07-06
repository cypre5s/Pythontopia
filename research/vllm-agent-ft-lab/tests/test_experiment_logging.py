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
