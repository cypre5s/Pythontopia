import csv
import json
from pathlib import Path

from pyt_agent_lab.semantic_adjudication import export_paired_semantic_adjudication


def _record(case_id: str, response: str) -> dict:
    return {
        "case": {
            "id": case_id,
            "chapter": 1,
            "kc": "变量",
            "learner_profile": "初学者",
            "learning_signal": "微测错误",
            "misconception_signal": "把赋值当等式",
            "task_focus": "安排下一步",
            "evidence_ids": ["page-1"],
            "expected_event_intent": "REVIEW",
        },
        "response": response,
    }


def test_semantic_adjudication_hides_identity_and_separates_answer_key(tmp_path: Path) -> None:
    outputs = export_paired_semantic_adjudication(
        {
            "champion": [_record("case-1", json.dumps({"event_intent": "READ"}))],
            "candidate": [_record("case-1", json.dumps({"event_intent": "REVIEW"}))],
        },
        tmp_path,
        seed=7,
    )

    items = list(csv.DictReader(Path(outputs["items_csv"]).open(encoding="utf-8")))
    keys = list(csv.DictReader(Path(outputs["answer_key"]).open(encoding="utf-8")))
    assert "champion" not in json.dumps(items)
    assert "candidate" not in json.dumps(items)
    assert {keys[0]["model_a"], keys[0]["model_b"]} == {"champion", "candidate"}
    assert items[0]["acceptable_intents"] == ""
    assert Path(outputs["manifest"]).exists()
