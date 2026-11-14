import asyncio
import json
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace

import httpx
import pytest
from sqlalchemy.orm import sessionmaker

from app import judge_management as management, practice
from app.auth import issue
from app.go_judge import GoJudgeAdapter
from app.integration_models import JudgeService, SubmissionDiagnostic, SubmissionPayload
from app.models import Submission, User


@pytest.fixture
def submission(client, db, teacher, student, classroom, branching_course, monkeypatch):
    factory = sessionmaker(db.bind, expire_on_commit=False)
    monkeypatch.setattr(practice, "SessionLocal", factory)
    monkeypatch.setattr(management, "SessionLocal", factory)
    response = client.post("/api/submissions", headers=student["headers"], json={
        "classroom_id": classroom["id"], "activity_id": "ch2-code", "question_id": "course-476",
        "idempotency_key": "management-submission", "code": "print(input())"})
    assert response.status_code == 201, response.text
    sid = response.json()["id"]
    payload = db.get(SubmissionPayload, sid)
    payload.cases, payload.public_count = [{"input": "ok", "output": "ok"}, {"input": "PRIVATE-IN", "output": "PRIVATE-EXPECTED"}], 1
    db.commit()
    return sid


def adapter_with_outputs(outputs):
    iterator = iter(outputs)
    return GoJudgeAdapter("http://judge", httpx.MockTransport(lambda _: httpx.Response(200, json=[{
        "status": "Accepted", "exitStatus": 0, "time": 12_000_000, "memory": 1024,
        "files": {"stdout": next(iterator), "stderr": ""}}])))


def test_date_range_consistent_counts_records_events_and_live_queue(client, db, teacher, classroom, submission):
    since = datetime(2026, 11, 13, 16, tzinfo=timezone.utc)
    until = since + timedelta(days=1)
    original = db.get(Submission, submission)
    original.created_at, original.status = since, "prepared"
    for index, instant in enumerate([since - timedelta(microseconds=1), until - timedelta(microseconds=1), until]):
        db.add(Submission(user_id=original.user_id, classroom_id=original.classroom_id, activity_id=original.activity_id,
                          question_id=original.question_id, idempotency_key=f"range-{index}", code="pass",
                          status="infrastructure_error", created_at=instant))
    db.add(JudgeService(id=1, paused=False, health={"status": "healthy"}, events=[
        {"at": instant.isoformat(), "message": str(index)} for index, instant in enumerate([since - timedelta(seconds=1), since, until - timedelta(seconds=1), until])]))
    db.get(User, teacher["id"]).role = "admin"
    db.commit()
    params = {"classroom_id": classroom["id"], "since": "2026-11-14T00:00:00+08:00", "until": "2026-11-15T00:00:00+08:00"}
    listed = client.get("/api/teacher/judge/submissions", params=params, headers=teacher["headers"]).json()
    assert listed["total"] == 2
    assert {row["status"] for row in listed["items"]} == {"prepared", "infrastructure_error"}
    service = client.get("/api/teacher/judge/service", params=params, headers=teacher["headers"]).json()
    assert service["counts"] == {"prepared": 1, "infrastructure_error": 1}
    assert service["global_counts"]["infrastructure_error"] == 3
    assert service["health"]["status"] == "healthy"
    assert [event["message"] for event in service["events"]] == ["1", "2"]
    params.update(since="2026-11-16T00:00:00Z", until="2026-11-17T00:00:00Z")
    empty = client.get("/api/teacher/judge/service", params=params, headers=teacher["headers"]).json()
    assert empty["counts"] == {} and empty["events"] == [] and empty["global_counts"]["prepared"] == 1
    assert client.get("/api/teacher/judge/submissions", params={"classroom_id": classroom["id"]}, headers=teacher["headers"]).json()["total"] == 4


@pytest.mark.parametrize("dates", [
    {"since": "2026-11-14T00:00:00Z"},
    {"until": "2026-11-14T00:00:00Z"},
    {"since": "2026-11-14T00:00:00", "until": "2026-11-15T00:00:00"},
    {"since": "2026-11-15T00:00:00Z", "until": "2026-11-14T00:00:00Z"},
    {"since": "2026-11-14T00:00:00Z", "until": "2026-11-14T00:00:00Z"},
    {"since": "bad", "until": "bad"},
])
def test_invalid_date_ranges_rejected(client, teacher, classroom, dates):
    for path in ("service", "submissions"):
        result = client.get(f"/api/teacher/judge/{path}", params={"classroom_id": classroom["id"], **dates}, headers=teacher["headers"])
        assert result.status_code == 422


def test_teacher_evidence_private_and_class_scoped(client, db, teacher, student, classroom, submission):
    asyncio.run(practice.dispatch_one(submission, adapter_with_outputs(["ok", "PRIVATE-ACTUAL"])))
    db.expire_all()
    path = f"/api/teacher/judge/submissions/{submission}?classroom_id={classroom['id']}"
    response = client.get(path, headers=teacher["headers"])
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "wrong_answer" and data["passed_count"] == 1
    assert data["code"] == "print(input())"
    assert data["cases"][1]["result"]["stdout"] == "PRIVATE-ACTUAL"
    assert data["cases"][1]["expected_output"] == "PRIVATE-EXPECTED"
    assert data["cases"][1]["result"]["time_ns"] == 12_000_000
    assert data["diagnostic"]["finished_at"]
    assert client.get(path, headers=student["headers"]).status_code == 403
    receipt = client.get(f"/api/submissions/{submission}?classroom_id={classroom['id']}", headers=student["headers"])
    assert receipt.status_code == 200 and "PRIVATE" not in receipt.text and "diagnostic" not in receipt.text
    other = User(name="其他教师", student_id="9998887776665", role="teacher", password_hash="unused")
    db.add(other)
    db.commit()
    headers = {"Authorization": "Bearer " + issue(db, other)["access_token"]}
    assert client.get(path, headers=headers).status_code == 403
    assert client.get(f"/api/teacher/judge/submissions?classroom_id={classroom['id']}", headers=headers).status_code == 403
    assert client.get(f"/api/teacher/judge/service?classroom_id={classroom['id']}", headers=headers).status_code == 403
    # A teacher's second class must not reveal a submission by guessed ID.
    second = client.post("/api/classrooms", json={"name": "另一个班"}, headers=teacher["headers"]).json()
    assert client.get(f"/api/teacher/judge/submissions/{submission}?classroom_id={second['id']}", headers=teacher["headers"]).status_code == 404
    # Preserve genuine legacy public evidence, without inventing old hidden output.
    db.delete(db.get(SubmissionDiagnostic, submission))
    db.commit()
    historical = client.get(path, headers=teacher["headers"]).json()
    assert historical["diagnostic"] is None
    assert historical["cases"][0]["result"]["stdout"] == "ok"
    assert historical["cases"][0]["result"]["source"] == "public_receipt"
    assert historical["cases"][1]["result"] is None


def test_search_filter_paging_and_old_diagnostic(client, db, teacher, student, classroom, submission):
    db.add(Submission(user_id=student["id"], classroom_id=classroom["id"], activity_id="ch1-practice",
                      question_id="ch1-abstraction", idempotency_key="old-concept-empty-code", code="", status="accepted"))
    db.commit()
    base = f"/api/teacher/judge/submissions?classroom_id={classroom['id']}"
    for query in ["学生", student["student_id"], submission]:
        result = client.get(base, params={"classroom_id": classroom["id"], "q": query}, headers=teacher["headers"])
        assert result.json()["total"] == 1
        assert "code" not in result.json()["items"][0]
    assert client.get(base, params={"classroom_id": classroom["id"], "q": "%"}, headers=teacher["headers"]).json()["total"] == 0
    assert client.get(base, params={"classroom_id": classroom["id"], "page": 2, "size": 1}, headers=teacher["headers"]).json()["items"] == []
    assert client.get(base, params={"classroom_id": classroom["id"], "status": "accepted"}, headers=teacher["headers"]).json()["total"] == 0
    assert client.get(base, params={"classroom_id": classroom["id"], "status": "invalid"}, headers=teacher["headers"]).status_code == 422
    data = client.get(f"/api/teacher/judge/submissions/{submission}?classroom_id={classroom['id']}", headers=teacher["headers"]).json()
    assert data["diagnostic"] is None and all(case["result"] is None for case in data["cases"])
    assert management.queue_counts(db, classroom["id"]) == {"prepared": 1}


def test_admin_pause_resume_preserves_queue_and_results(client, db, teacher, student, classroom, submission):
    body = {"paused": True, "reason": "维护检查"}
    assert client.put("/api/teacher/judge/service", json=body, headers=teacher["headers"]).status_code == 403
    assert client.put("/api/teacher/judge/service", json=body, headers=student["headers"]).status_code == 403
    assert client.post("/api/teacher/judge/service/probe", headers=teacher["headers"]).status_code == 403
    admin = db.get(User, teacher["id"])
    admin.role = "admin"
    db.commit()
    assert client.put("/api/teacher/judge/service", json=body, headers=teacher["headers"]).status_code == 200
    asyncio.run(practice.dispatch_one(submission, adapter_with_outputs([])))
    db.expire_all()
    assert db.get(Submission, submission).status == "prepared"
    assert practice._pending_ids() == []
    assert db.get(SubmissionDiagnostic, submission) is None
    assert client.put("/api/teacher/judge/service", json={"paused": False, "reason": "完成维护"}, headers=teacher["headers"]).status_code == 200
    asyncio.run(practice.dispatch_one(submission, adapter_with_outputs(["ok", "PRIVATE-EXPECTED"])))
    db.expire_all()
    assert db.get(Submission, submission).status == "accepted"
    data = client.get(f"/api/teacher/judge/service?classroom_id={classroom['id']}", headers=teacher["headers"]).json()
    assert len(data["events"]) == 2 and data["global_counts"]["accepted"] == 1
    assert client.put("/api/teacher/judge/service", json={"paused": True, "reason": "  "}, headers=teacher["headers"]).status_code == 422


def test_connection_failure_keeps_task_and_does_not_spin(db, submission):
    def handler(request):
        raise httpx.ConnectError("SECRET host unavailable", request=request)
    asyncio.run(practice.dispatch_one(submission, GoJudgeAdapter("http://judge", httpx.MockTransport(handler))))
    db.expire_all()
    assert db.get(Submission, submission).status == "prepared"
    assert practice._pending_ids() == []
    assert "SECRET" not in json.dumps(db.get(JudgeService, 1).health)
    management.save_health({"status": "healthy", "detail": "连接与隔离检查通过"}, worker=True)
    assert practice._pending_ids() == [submission]
    asyncio.run(practice.dispatch_one(submission, adapter_with_outputs(["ok", "PRIVATE-EXPECTED"])))
    db.expire_all()
    assert db.get(Submission, submission).status == "accepted"


def test_partial_unknown_and_crash_never_replayed(db, submission):
    calls = []
    def handler(request):
        calls.append(1)
        if len(calls) == 1:
            return httpx.Response(200, json=[{"status": "Accepted", "exitStatus": 0, "files": {"stdout": "ok"}}])
        raise httpx.ReadTimeout("SECRET", request=request)
    adapter = GoJudgeAdapter("http://judge", httpx.MockTransport(handler))
    asyncio.run(practice.dispatch_one(submission, adapter))
    asyncio.run(practice.dispatch_one(submission, adapter))
    db.expire_all()
    assert len(calls) == 2 and db.get(Submission, submission).status == "submission_unknown"
    diagnostic = db.get(SubmissionDiagnostic, submission)
    assert len(diagnostic.cases) == 1 and "SECRET" not in diagnostic.detail
    db.get(Submission, submission).status = "dispatching"
    db.commit()
    practice._recover_dispatching()
    db.expire_all()
    assert db.get(Submission, submission).status == "submission_unknown"
    assert "不自动重送" in db.get(SubmissionDiagnostic, submission).detail


def test_worker_health_gate_keeps_pending_until_recovery(db, submission, monkeypatch):
    probes = iter([{"status": "unavailable", "detail": "连接失败"}, {"status": "healthy", "detail": "已恢复"}])
    ticks = iter([6, 6, 12, 12])
    sleeps = []

    async def probe():
        return next(probes)

    async def sleep(delay):
        sleeps.append(delay)
        db.expire_all()
        assert db.get(Submission, submission).status == "prepared"
        assert practice._pending_ids() == []

    async def recovered_dispatch():
        assert practice._pending_ids() == [submission]
        raise asyncio.CancelledError

    monkeypatch.setattr(practice, "time", SimpleNamespace(monotonic=lambda: next(ticks)))
    monkeypatch.setattr(practice, "probe_service", probe)
    monkeypatch.setattr(practice.asyncio, "sleep", sleep)
    monkeypatch.setattr(practice, "reconcile_once", recovered_dispatch)
    with pytest.raises(asyncio.CancelledError):
        asyncio.run(practice.judge_worker())
    db.expire_all()
    assert sleeps == [0.1]
    assert len(db.get(JudgeService, 1).events) == 2


@pytest.mark.parametrize("condition", ["healthy", "unsafe", "auth", "timeout", "malformed", "unconfigured"])
def test_health_is_safe_and_checks_auth(monkeypatch, condition):
    monkeypatch.setenv("REBORN_GO_JUDGE_URL", "" if condition == "unconfigured" else "http://SECRET-host")
    monkeypatch.setenv("REBORN_GO_JUDGE_AUTH_TOKEN", "SECRET-token")
    calls = []
    def handler(request):
        calls.append(request.url.path)
        if condition == "timeout":
            raise httpx.ReadTimeout("SECRET", request=request)
        if request.url.path == "/file":
            return httpx.Response(401 if condition == "auth" else 200, json={"SECRET-file": "SECRET"})
        if condition == "malformed":
            return httpx.Response(200, json={})
        return httpx.Response(200, json={"runnerConfig": {"cgroupType": 2, "seccomp": condition != "unsafe",
                               "uid": 1536, "cgroupControllers": ["cpu", "memory", "pids"], "mount": "SECRET"}})
    result = asyncio.run(management.probe_service(httpx.MockTransport(handler)))
    assert result["status"] == (condition if condition in ("healthy", "unsafe") else "unavailable")
    assert "SECRET" not in json.dumps(result)
    if condition == "healthy":
        assert calls == ["/config", "/file"]


def test_deleted_content_keeps_pending_judge_work_and_private_diagnostics(client, db, teacher, student, classroom, submission):
    from test_activity_deletion import preview, confirm
    from app.models import Question
    db.get(User, teacher['id']).role = 'admin'
    db.commit()
    row = db.get(Submission, submission)
    code = row.code
    plan = preview(client, teacher, classroom, 'question', row.question_id)
    assert not plan['blockers'], plan
    assert confirm(client, teacher, classroom, plan).status_code == 200
    db.expire_all()
    assert db.get(Question, row.question_id) is None
    asyncio.run(practice.dispatch_one(submission, adapter_with_outputs(['ok', 'PRIVATE-EXPECTED'])))
    db.expire_all()
    assert db.get(Submission, submission).status == 'accepted'
    params = {'classroom_id': classroom['id']}
    listing = client.get('/api/teacher/judge/submissions', params=params, headers=teacher['headers'])
    assert listing.status_code == 200 and listing.json()['total'] == 1
    details = client.get(f'/api/teacher/judge/submissions/{submission}', params=params, headers=teacher['headers'])
    assert details.status_code == 200 and details.json()['code'] == code
    public = client.get(f'/api/submissions/{submission}', params=params, headers=student['headers'])
    assert public.status_code == 200 and 'PRIVATE' not in public.text
