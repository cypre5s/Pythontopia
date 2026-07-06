import json
from pathlib import Path

from pyt_agent_lab.dataset_normalization import normalize_ed_record, write_normalized_jsonl


def test_normalize_ed_record_maps_teacher_student_turns() -> None:
    record = {
        "background_info": {
            "topic": "for loops",
            "student_prefrences": "hands-on activities",
            "teacher_prefrences": "lecture-based teaching",
            "student_reactions": "gets disengaged without examples",
            "teacher_reactions": "adapts to the student",
        },
        "conversation": [
            {"role": "Teacher", "text": "A loop repeats work.\"\", "},
            {"role": "Student", "text": "Can I try one?"},
        ],
    }

    normalized = normalize_ed_record(record, split="eval", index=7)

    assert normalized["id"] == "ed-eval-000007"
    assert normalized["source_dataset"] == "google-education-dialogue-dataset"
    assert normalized["split"] == "eval"
    assert normalized["student_state"]["preferences"] == "hands-on activities"
    assert normalized["dialogue_context"][0] == {"role": "teacher", "text": "A loop repeats work."}
    assert normalized["target_json"]["pedagogy_signal"] == "teacher_student_dialogue"


def test_write_normalized_jsonl_writes_one_record_per_line(tmp_path: Path) -> None:
    target = tmp_path / "normalized.jsonl"
    rows = [{"id": "a"}, {"id": "b"}]

    write_normalized_jsonl(rows, target)

    assert [json.loads(line)["id"] for line in target.read_text(encoding="utf-8").splitlines()] == ["a", "b"]
