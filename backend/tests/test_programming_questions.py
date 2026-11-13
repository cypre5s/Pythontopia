import pytest
from pydantic import ValidationError
from app.classrooms import QuestionInput
from app.courses import public_question
from app.models import Question
from app.practice import SubmissionInput


def test_programming_fields_and_private_boundary():
    data = dict(id="q", title="echo", kind="python", prompt="echo input", hints=["Use input()"], starter_code="print(input())",
                samples=[{"input": "a", "output": "a"}], hidden_cases=[{"input": "secret", "output": "secret"}])
    body = QuestionInput(**data)
    public = public_question(Question(**body.model_dump(exclude={"hidden_cases", "answer", "chapter_ids", "knowledge_point_ids"})))
    assert public["hints"] == ["Use input()"]
    assert public["starter_code"] == "print(input())"
    assert "secret" not in str(public)
    for hints in ([" "], ["a"] * 11, ["a" * 2001]):
        with pytest.raises(ValidationError):
            QuestionInput(**{**data, "hints": hints})


def test_old_server_run_is_rejected():
    with pytest.raises(ValidationError):
        SubmissionInput(classroom_id=1, activity_id="practice", question_id="q", idempotency_key="test-run-001", mode="run", code="print(1)")


def test_authoring_persists_programming_metadata(client, teacher, student, classroom):
    url = f"/api/teacher/questions?classroom_id={classroom['id']}"
    body = dict(id="python-echo", title="Echo", kind="python", prompt="Print input", hints=["Read a line"],
                starter_code="name = input()", chapter_ids=[179], status="ready",
                samples=[{"input": "hello", "output": "hello"}], hidden_cases=[{"input": "private", "output": "private"}])
    result = client.post(url, headers=teacher["headers"], json=body)
    assert result.status_code == 201, result.text
    q = result.json()
    assert q["hints"] == body["hints"] and q["starter_code"] == body["starter_code"]
    assert client.get(url, headers=student["headers"]).status_code == 403
    update = client.put(f"/api/teacher/questions/{q['id']}?classroom_id={classroom['id']}",
                        headers=teacher["headers"], json={"hints": ["Try print()"], "starter_code": "print(input())"})
    assert update.status_code == 200, update.text
    assert update.json()["hints"] == ["Try print()"]
