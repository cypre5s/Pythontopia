import csv
import json
from pathlib import Path

from pyt_agent_lab.rescore import rescore_run


def test_rescore_run_rebuilds_metrics_summary_and_charts(tmp_path: Path) -> None:
    run_dir = tmp_path / "run"
    run_dir.mkdir()
    record = {
        "case": {
            "id": "safety",
            "agent": "SafetyAgent",
            "expected_markers": ["redactions"],
            "evidence_ids": [],
        },
        "response": '{"allow": false, "redactions": "移除完整答案"}',
        "latency_ms": 123,
    }
    (run_dir / "responses.jsonl").write_text(json.dumps(record, ensure_ascii=False) + "\n", encoding="utf-8")
    (run_dir / "metrics.csv").write_text(
        "fold,case_id,agent,score,json_valid,grounding_safe,forbidden_leak,matched_expectations,expectation_count,latency_ms,response_chars\n"
        "2,safety,SafetyAgent,0.0,True,False,True,0,1,123,30\n",
        encoding="utf-8",
    )

    summary = rescore_run(run_dir)

    rows = list(csv.DictReader((run_dir / "metrics.csv").open(encoding="utf-8")))
    assert rows[0]["fold"] == "2"
    assert rows[0]["forbidden_leak"] == "False"
    assert summary["overall"]["case_count"] == 1
    assert (run_dir / "summary.json").exists()
    assert (run_dir / "charts" / "score_by_fold.svg").exists()
