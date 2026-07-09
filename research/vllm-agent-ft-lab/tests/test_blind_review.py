import csv
from pathlib import Path

from pyt_agent_lab.blind_review import export_blind_review_pack


def test_export_blind_review_pack_separates_items_from_answer_key(tmp_path: Path) -> None:
    responses = [
        {
            "case": {
                "id": "case-1",
                "agent": "TutorAgent",
                "eval_slice": "new_question",
                "source_kind": "deterministic_new_question",
                "chapter": 1,
                "kc": "变量",
                "user": "学生说 input 不懂",
            },
            "response": '{"diagnosis":"x","next_hint":"y","evidence_ids":["page-1-006"],"safety_flags":[]}',
            "score": {
                "score": 1.0,
                "json_valid": True,
                "grounding_safe": True,
                "forbidden_leak": False,
            },
        }
    ]

    outputs = export_blind_review_pack(responses, tmp_path, sample_size=1, seed=1)

    with Path(outputs["items_csv"]).open(encoding="utf-8") as handle:
        items = list(csv.DictReader(handle))
    with Path(outputs["answer_key"]).open(encoding="utf-8") as handle:
        keys = list(csv.DictReader(handle))

    assert items[0]["review_item_id"] == "blind-0001"
    assert items[0]["review_json_valid"] == ""
    assert "auto_score" not in items[0]
    assert keys[0]["auto_score"] == "1.0"
    assert Path(outputs["rubric"]).read_text(encoding="utf-8").startswith("# Manual Blind Review Rubric")
