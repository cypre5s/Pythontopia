import asyncio
import json
import os

import httpx
import pytest
from sqlalchemy.orm import sessionmaker

from app import practice
from app.go_judge import GoJudgeAdapter
from app.judge import JudgeSubmissionUnknown, JudgeUnavailable
from app.models import Question, QuestionSecret, Submission


def execution(status="Accepted", stdout="ok\n", stderr="", exit_status=0):
    return {"status": status, "exitStatus": exit_status, "files": {"stdout": stdout, "stderr": stderr}}


def test_python_allowlist_comparison_and_hidden_output():
    calls = []

    def handler(request):
        body = json.loads(request.content)
        command = body["cmd"][0]
        assert request.url.path == "/run"
        assert command["args"] == ["/usr/local/bin/python3", "-I", "-B", "/w/main.py"]
        assert command["memoryLimit"] == 128 * 1024 * 1024
        assert command["clockLimit"] == 5_000_000_000 and command["procLimit"] == 16
        assert list(command["copyIn"]) == ["main.py"]
        calls.append(1)
        return httpx.Response(200, json=[execution(stdout="ok\n" if len(calls) == 1 else "PRIVATE-INPUT")])

    result = asyncio.run(GoJudgeAdapter("http://judge", httpx.MockTransport(handler)).execute(
        "print(input())", [{"output": "ok"}, {"output": "secret"}], 1))
    assert result.status == "wrong_answer"
    assert result.feedback["passed_count"] == 1 and result.feedback["total_count"] == 2
    assert len(result.feedback["samples"]) == 1 and "PRIVATE" not in json.dumps(result.feedback)


@pytest.mark.parametrize(("remote", "stderr", "expected"), [
    ("Time Limit Exceeded", "", "time_limit"), ("Memory Limit Exceeded", "", "memory_limit"),
    ("Nonzero Exit Status", "MemoryError\n", "memory_limit"),
    ("Nonzero Exit Status", "SyntaxError: invalid syntax\n", "compile_error"),
    ("Nonzero Exit Status", "ValueError: test", "runtime_error"),
    ("Output Limit Exceeded", "", "runtime_error"), ("Signalled", "", "runtime_error"),
    ("Internal Error", "", "infrastructure_error"), ("unknown-status", "", "infrastructure_error"),
])
def test_status_mapping(remote, stderr, expected):
    adapter = GoJudgeAdapter("http://judge", httpx.MockTransport(lambda _: httpx.Response(
        200, json=[execution(remote, stderr=stderr, exit_status=1)])))
    result = asyncio.run(adapter.execute("pass", [{}]))
    assert result.status == expected
    assert ("passed_count" in result.feedback) == (expected != "infrastructure_error")


@pytest.mark.parametrize("response", [{}, [], [{"status": "Accepted", "files": None}], [{"status": 3}]])
def test_invalid_response_is_unknown(response):
    adapter = GoJudgeAdapter("http://judge", httpx.MockTransport(lambda _: httpx.Response(200, json=response)))
    with pytest.raises(JudgeSubmissionUnknown):
        asyncio.run(adapter.execute("pass", [{}]))


def test_timeout_is_unknown_and_not_retried():
    calls = []

    def handler(request):
        calls.append(1)
        raise httpx.ReadTimeout("lost response", request=request)

    with pytest.raises(JudgeSubmissionUnknown):
        asyncio.run(GoJudgeAdapter("http://judge", httpx.MockTransport(handler)).execute("pass", [{}, {}]))
    assert len(calls) == 1


def test_missing_configuration_is_explicit():
    with pytest.raises(JudgeUnavailable):
        asyncio.run(GoJudgeAdapter("").execute("pass", [{}]))


def test_connection_loss_after_a_case_is_unknown():
    calls = []

    def handler(request):
        calls.append(1)
        if len(calls) == 1:
            return httpx.Response(200, json=[execution()])
        raise httpx.ConnectError("unreachable", request=request)

    with pytest.raises(JudgeSubmissionUnknown):
        asyncio.run(GoJudgeAdapter("http://judge", httpx.MockTransport(handler)).execute("pass", [{}, {}]))
    assert len(calls) == 2


def test_go_judge_unknown_dispatch_is_durable(client, db, student, classroom, monkeypatch, branching_course):
    monkeypatch.setattr(practice, "SessionLocal", sessionmaker(db.bind, expire_on_commit=False))
    response = client.post("/api/submissions", headers=student["headers"], json={
        "classroom_id": classroom["id"], "activity_id": "ch2-code", "question_id": "course-476",
        "idempotency_key": "go-judge-unknown-test", "code": "print(input())"})
    assert response.status_code == 201
    calls = []

    def handler(request):
        calls.append(1)
        raise httpx.ReadTimeout("lost response", request=request)

    sid = response.json()["id"]
    adapter = GoJudgeAdapter("http://judge", httpx.MockTransport(handler))
    asyncio.run(practice.dispatch_one(sid, adapter))
    asyncio.run(practice.dispatch_one(sid, adapter))
    db.expire_all()
    assert db.get(Submission, sid).status == "submission_unknown" and len(calls) == 1


def live_adapter():
    url = os.getenv("REBORN_LIVE_GO_JUDGE_URL")
    if not url:
        pytest.skip("Requires explicitly configured independent go-judge sandbox")
    return GoJudgeAdapter(url)


@pytest.mark.parametrize(("code", "expected"), [
    ("print('ok')", "accepted"), ("print('wrong')", "wrong_answer"),
    ("print(", "compile_error"), ("raise ValueError('test')", "runtime_error"),
    ("while True: pass", "time_limit"), ("x=bytearray(512*1024*1024)", "memory_limit"),
])
def test_live_python_grading(code, expected):
    result = asyncio.run(live_adapter().execute(code, [{"output": "ok"}], 1))
    assert result.status == expected
    assert result.feedback["passed_count"] == int(expected == "accepted")


def test_live_durable_dispatch_and_authenticated_receipt(client, db, student, classroom, monkeypatch, branching_course):
    adapter = live_adapter()
    monkeypatch.setattr(practice, "SessionLocal", sessionmaker(db.bind, expire_on_commit=False))
    question = db.get(Question, "course-476")
    question.samples = [{"input": "1", "output": "2"}]
    secret = db.get(QuestionSecret, question.id)
    secret.hidden_cases = [{"input": "7", "output": "8"}]
    db.commit()
    body = {"classroom_id": classroom["id"], "activity_id": "ch2-code", "question_id": question.id,
            "idempotency_key": "live-go-judge-receipt", "code": "print(int(input())+1)"}
    response = client.post("/api/submissions", json=body, headers=student["headers"])
    assert response.status_code == 201, response.text
    sid = response.json()["id"]
    assert response.json()["status"] == "prepared"
    asyncio.run(practice.dispatch_one(sid, adapter))
    db.expire_all()
    row = db.get(Submission, sid)
    assert row.status == "accepted" and row.feedback["passed_count"] == 2
    assert not row.judge_token
    receipt = client.get(f"/api/submissions/{sid}?classroom_id={classroom['id']}", headers=student["headers"]).json()
    assert receipt["status"] == "accepted" and len(receipt["feedback"]["samples"]) == 1
    assert client.post("/api/submissions", json=body, headers=student["headers"]).json()["id"] == sid
    # A completed receipt cannot be dispatched again, even with an unavailable adapter.
    asyncio.run(practice.dispatch_one(sid, GoJudgeAdapter("")))
    db.expire_all()
    assert db.get(Submission, sid).status == "accepted"
