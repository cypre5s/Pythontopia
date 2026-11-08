import asyncio
from sqlalchemy import func, select
from sqlalchemy.orm import sessionmaker
from app.integration_models import SubmissionPayload
from app.judge import JudgeResult, JudgeSubmissionUnknown
from app.models import Submission
from app import practice


def submit(client, student, classroom, **overrides):
    body = {
        "classroom_id": classroom["id"],
        "activity_id": "ch1-practice",
        "question_id": "ch1-abstraction",
        "idempotency_key": "concept-unique-0001",
        "answer": "抽象与自动化",
        "mode": "submit",
        **overrides}
    return client.post("/api/submissions", json=body, headers=student["headers"])


def test_concept_grades_are_real_persistent_and_idempotent(client, db, student, classroom):
    a = submit(client, student, classroom)
    b = submit(client, student, classroom)
    assert a.status_code == b.status_code == 201
    assert a.json()["id"] == b.json()["id"]
    assert a.json()["status"] == "accepted"
    assert db.scalar(select(func.count()).select_from(Submission)) == 1
    assert submit(client, student, classroom, answer="wrong").status_code == 409
    wrong = submit(client, student, classroom, answer="wrong", idempotency_key="concept-unique-0002")
    assert wrong.json()["status"] == "wrong_answer"
    assert "抽象与自动化" not in str(wrong.json()["feedback"])


def test_cross_activity_and_other_user_submission_denied(client, student, teacher, classroom, branching_course):
    result = submit(client, student, classroom)
    assert submit(client, student, classroom, question_id="ch2-language", idempotency_key="wrong-activity-question").status_code == 404
    assert client.get(f"/api/submissions/{result.json()['id']}?classroom_id={classroom['id']}", headers=teacher["headers"]).status_code == 404


def test_boolean_requires_boolean_and_fill_handles_whitelist(client, student, classroom):
    assert submit(client, student, classroom, question_id="ch1-computation", answer="True").json()["status"] == "wrong_answer"
    assert (
        submit(client, student, classroom, question_id="ch1-computation", answer=True, idempotency_key="concept-bool-0001").json()["status"]
        == "accepted"
    )
    assert (
        submit(client, student, classroom, question_id="ch1-model", answer=" 建模 ", idempotency_key="concept-fill-0001").json()["status"]
        == "accepted"
    )


def test_judge_dispatch_once_unknown_does_not_blindly_resend(client, db, student, classroom, monkeypatch, branching_course):
    monkeypatch.setattr(practice, "SessionLocal", sessionmaker(db.bind, expire_on_commit=False))
    result = submit(client, student, classroom, activity_id="ch2-code", question_id="course-476", code="print(input())", answer=None)
    sid = result.json()["id"]
    assert result.json()["status"] == "prepared"
    assert db.get(SubmissionPayload, sid).public_count == 3

    class Unknown:
        calls = 0

        async def submit(self, code, cases):
            self.calls += 1
            raise JudgeSubmissionUnknown("uncertain")

    adapter = Unknown()
    asyncio.run(practice.dispatch_one(sid, adapter))
    asyncio.run(practice.dispatch_one(sid, adapter))
    db.expire_all()
    assert adapter.calls == 1
    assert db.get(Submission, sid).status == "submission_unknown"


def test_server_poll_recovers_without_browser_and_run_not_official(client, db, student, classroom, monkeypatch, branching_course):
    monkeypatch.setattr(practice, "SessionLocal", sessionmaker(db.bind, expire_on_commit=False))
    result = submit(client, student, classroom, activity_id="ch2-code", question_id="course-476", code="print(input())", answer=None, mode="run")
    sid = result.json()["id"]

    class FakeJudge:
        async def submit(self, code, cases):
            assert len(cases) == 3  # Run executes public samples only.
            return ["token-" + str(i) for i in range(len(cases))]

        async def poll(self, tokens, public_count):
            assert len(tokens) == public_count == 3
            return JudgeResult("accepted", {"samples": []})

    asyncio.run(practice.dispatch_one(sid, FakeJudge()))
    asyncio.run(practice.poll_one(sid, FakeJudge()))
    db.expire_all()
    row = db.get(Submission, sid)
    assert row.status == "accepted" and row.mode == "run" and row.judge_token


def test_judge_worker_recovers_database_outage_without_replaying_dispatch(client, db, student, classroom, monkeypatch, caplog, branching_course):
    import pytest
    from sqlalchemy.exc import OperationalError

    response = submit(client, student, classroom, activity_id="ch2-code", question_id="course-476", code="print('x')", answer=None)
    sid = response.json()["id"]
    row = db.get(Submission, sid)
    row.status = "dispatching"
    db.commit()
    factory = sessionmaker(db.bind, expire_on_commit=False)
    attempts, delays = [], []

    def flaky_database():
        attempts.append(1)
        if len(attempts) == 1:
            raise OperationalError("connection", {}, RuntimeError("private-connection-value"))
        return factory()

    async def sleep(delay):
        delays.append(delay)

    async def stop_after_recovery():
        raise asyncio.CancelledError

    monkeypatch.setattr(practice, "SessionLocal", flaky_database)
    monkeypatch.setattr(practice.asyncio, "sleep", sleep)
    monkeypatch.setattr(practice, "reconcile_once", stop_after_recovery)
    with pytest.raises(asyncio.CancelledError):
        asyncio.run(practice.judge_worker())
    db.expire_all()
    assert delays == [1] and len(attempts) == 2
    assert db.get(Submission, sid).status == "submission_unknown"
    assert "private-connection-value" not in caplog.text
