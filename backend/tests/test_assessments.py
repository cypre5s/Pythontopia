from app.content_relations import set_activity_questions
from conftest import make_question
import asyncio
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import os
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, func, select, text
from sqlalchemy.engine import make_url
from sqlalchemy.orm import sessionmaker

from app import practice
from app.auth import issue
from app.assessments import assessment_questions
from app.db import Base, get_db
from app.judge import JudgeSubmissionUnknown
from app.main import create_app
from app.models import Activity, AssessmentAttempt, ChapterRelease, Classroom, Membership, QuestionSecret, Submission, User
from app.quests import validate_evidence
from fixture_seed import seed


def url(classroom, suffix=""):
    return f"/api/activities/ch1-assessment/assessment{suffix}"


def answers(db, *, correct=True):
    activity = db.get(Activity, "ch1-assessment")
    return [{"question_id": question.id,
             "answer": db.get(QuestionSecret, question.id).answer if correct else False if question.kind == "boolean" else question.options[-1]}
            for question in assessment_questions(db, activity)]


def hand_in(client, db, student, classroom, *, correct=True, key="paper-idempotency-1", entries=None):
    return client.post(url(classroom, "/submit"), headers=student["headers"], json={
        "classroom_id": classroom["id"], "idempotency_key": key, "answers": answers(db, correct=correct) if entries is None else entries})


def test_draft_persists_and_rejects_stale_pages_without_grading(client, db, student, teacher, classroom):
    initial = client.get(url(classroom) + f"?classroom_id={classroom['id']}", headers=student["headers"])
    assert initial.status_code == 200 and initial.json()["revision"] == 0 and initial.json()["id"] is None
    assert initial.json()["score"] is None and initial.json()["max_score"] == 100
    assert db.scalar(select(func.count()).select_from(AssessmentAttempt)) == 0
    body = {"classroom_id": classroom["id"], "revision": 0, "answers": answers(db)[:1]}
    saved = client.put(url(classroom), json=body, headers=student["headers"])
    assert saved.status_code == 200 and saved.json()["revision"] == 1 and not saved.json()["locked"]
    assert saved.json()["results"] == [] and not saved.json()["completed"]
    assert client.put(url(classroom), json=body, headers=student["headers"]).status_code == 409
    db.expire_all()
    restored = client.get(url(classroom) + f"?classroom_id={classroom['id']}", headers=student["headers"]).json()
    assert restored["answers"] == [{**body["answers"][0], "code": None}]
    assert client.put(url(classroom), json=body, headers=teacher["headers"]).status_code == 403
    assert db.scalar(select(func.count()).select_from(Submission)) == 0


def test_one_hand_in_is_atomic_immutable_and_wrong_answers_still_finish_the_paper(client, db, student, classroom):
    response = hand_in(client, db, student, classroom, correct=False)
    assert response.status_code == 201, response.text
    paper = response.json()
    assert paper["locked"] and paper["completed"] and paper["status"] == "graded"
    assert not paper["passed"] and 0 <= paper["correct_count"] < paper["total"] == 3
    before = db.scalar(select(func.count()).select_from(Submission))
    repeated = hand_in(client, db, student, classroom, correct=False)
    assert repeated.json()["results"] == paper["results"] and repeated.json()["answers"] == paper["answers"]
    assert hand_in(client, db, student, classroom, key="paper-idempotency-new").status_code == 409
    assert client.put(url(classroom), headers=student["headers"], json={
        "classroom_id": classroom["id"], "revision": paper["revision"], "answers": answers(db)}).status_code == 409
    assert db.scalar(select(func.count()).select_from(Submission)) == before == 3
    progress = client.get(f"/api/activities/ch1-assessment/progress?classroom_id={classroom['id']}", headers=student["headers"]).json()
    assert progress["completed"] and not progress["passed"]
    validate_evidence(db, db.get(User, student["id"]), classroom["id"],
                      {"type": "PASS_ASSESSMENT", "activity_id": "ch1-assessment"}, {"submission_id": paper["results"][-1]["id"]})
    # A submitted receipt remains readable even if the current configured set changes.
    activity = db.get(Activity, "ch1-assessment")
    set_activity_questions(db, activity, [paper["question_ids"][0]])
    db.commit()
    again = hand_in(client, db, student, classroom, entries=[{key: value for key, value in row.items() if key != "code"} for row in paper["answers"]])
    assert again.status_code == 201 and again.json()["question_ids"] == paper["question_ids"]


def test_hand_in_requires_every_current_question_and_rolls_back_if_any_grade_is_unavailable(client, db, student, classroom):
    entries = answers(db)
    for invalid in (entries[:1], [*entries, entries[0]], [{"question_id": "not-this-paper", "answer": True}],
                    [*entries[:1], {"question_id": entries[1]["question_id"], "answer": ""}, *entries[2:]]):
        assert hand_in(client, db, student, classroom, entries=invalid).status_code == 422
    assert db.scalar(select(func.count()).select_from(AssessmentAttempt)) == 0
    # Lose a later grading secret: earlier staged submissions must not escape the transaction.
    db.delete(db.get(QuestionSecret, entries[-1]["question_id"]))
    db.commit()
    failed = hand_in(client, db, student, classroom, entries=entries)
    assert failed.status_code == 503
    assert db.scalar(select(func.count()).select_from(AssessmentAttempt)) == 0
    assert db.scalar(select(func.count()).select_from(Submission)) == 0


def test_individual_assessment_submit_cannot_bypass_hand_in_and_legacy_receipt_is_read_only(client, db, student, classroom):
    question = assessment_questions(db, db.get(Activity, "ch1-assessment"))[0]
    body = {"classroom_id": classroom["id"], "activity_id": "ch1-assessment", "question_id": question.id,
            "idempotency_key": "legacy-receipt-123", "mode": "submit", "answer": db.get(QuestionSecret, question.id).answer}
    assert client.post("/api/submissions", headers=student["headers"], json=body).status_code == 409
    assert db.scalar(select(func.count()).select_from(Submission)) == 0
    row = Submission(user_id=student["id"], classroom_id=classroom["id"], activity_id="ch1-assessment", question_id=question.id,
                     idempotency_key=body["idempotency_key"], mode="submit", answer=body["answer"], status="accepted", feedback={})
    db.add(row)
    db.commit()
    assert client.post("/api/submissions", headers=student["headers"], json=body).json()["id"] == row.id
    assert client.post("/api/submissions", headers=student["headers"], json={**body, "answer": "different"}).status_code == 409
    paper = client.get(url(classroom) + f"?classroom_id={classroom['id']}", headers=student["headers"]).json()
    assert paper["status"] == "draft" and not paper["locked"] and len(paper["answers"]) == 1
    assert paper["results"] == []


def test_complete_legacy_result_uses_real_latest_records_without_creating_or_regrading(client, db, student, classroom):
    questions = assessment_questions(db, db.get(Activity, "ch1-assessment"))
    moment = datetime(2026, 1, 1, tzinfo=timezone.utc)
    for index, question in enumerate(questions):
        db.add(Submission(user_id=student["id"], classroom_id=classroom["id"], activity_id="ch1-assessment", question_id=question.id,
            idempotency_key=f"historic-{index}", mode="submit", answer=db.get(QuestionSecret, question.id).answer,
            status="accepted", feedback={}, created_at=moment))
    db.add(Submission(user_id=student["id"], classroom_id=classroom["id"], activity_id="ch1-assessment", question_id=questions[0].id,
        idempotency_key="historic-last-wrong", mode="submit", answer="last-response", status="wrong_answer", feedback={}, created_at=moment + timedelta(seconds=1)))
    db.commit()
    before = db.scalar(select(func.count()).select_from(Submission))
    paper = client.get(url(classroom) + f"?classroom_id={classroom['id']}", headers=student["headers"]).json()
    assert paper["legacy"] and paper["locked"] and paper["completed"] and not paper["passed"]
    assert paper["correct_count"] == 2 and paper["total"] == 3
    assert paper["score"] == 66 and paper["max_score"] == 100
    assert paper["answers"][0]["answer"] == "last-response"
    assert hand_in(client, db, student, classroom).status_code == 409
    assert db.scalar(select(func.count()).select_from(AssessmentAttempt)) == 1
    assert db.scalar(select(func.count()).select_from(Submission)) == before
    fixed_ids = paper["question_ids"]
    activity = db.get(Activity, "ch1-assessment")
    extra = make_question(db, id="test-added-after-legacy", activity_id=activity.id, chapter_ids=[179], title="Later question", kind="boolean",
                     prompt="Test only", options=[], samples=[])
    db.add(extra)
    set_activity_questions(db, activity, [*fixed_ids, extra.id])
    db.commit()
    again = client.get(url(classroom) + f"?classroom_id={classroom['id']}", headers=student["headers"]).json()
    assert again["locked"] and again["legacy"] and again["completed"]
    assert again["question_ids"] == fixed_ids and again["correct_count"] == 2 and again["total"] == 3
    assert again["score"] == 66 and again["max_score"] == 100
    assert db.scalar(select(func.count()).select_from(Submission)) == before


def test_python_whole_paper_uses_existing_judge_and_unknown_is_never_resubmitted(client, db, student, teacher, classroom, monkeypatch):
    activity = Activity(id="test-python-paper", chapter_id=179, title="Test Python paper", kind="assessment", npc_key="arena_guard",
                        assistant_allowed=False, owner_id=teacher["id"], content={"question_ids": ["test-python-paper-q"]})
    db.add(activity)
    db.flush()
    db.add(make_question(db, id="test-python-paper-q", activity_id=activity.id, chapter_ids=[179], title="Test", kind="python", prompt="Test fixture",
                    options=[], samples=[{"input": "", "output": "public"}]))
    db.flush()
    db.add(QuestionSecret(question_id="test-python-paper-q", hidden_cases=[{"input": "secret-in", "output": "secret-out"}]))
    db.commit()
    run_body = {"classroom_id": classroom["id"], "activity_id": activity.id, "question_id": "test-python-paper-q",
                "idempotency_key": "old-public-run", "mode": "run", "code": "print('public')"}
    run = client.post("/api/submissions", headers=student["headers"], json=run_body)
    assert run.status_code == 201
    submitted = client.post(f"/api/activities/{activity.id}/assessment/submit", headers=student["headers"], json={
        "classroom_id": classroom["id"], "idempotency_key": "python-whole-paper", "answers": [{"question_id": "test-python-paper-q", "code": "print('public')"}]})
    assert submitted.status_code == 201 and submitted.json()["status"] == "grading" and submitted.json()["locked"]
    assert submitted.json()["score"] is None and submitted.json()["max_score"] == 100
    assert "secret-in" not in submitted.text and "secret-out" not in submitted.text
    sid = submitted.json()["results"][0]["id"]
    assert client.post("/api/submissions", headers=student["headers"], json={**run_body, "idempotency_key": "new-public-run"}).status_code == 409
    assert client.post("/api/submissions", headers=student["headers"], json=run_body).json()["id"] == run.json()["id"]
    monkeypatch.setattr(practice, "SessionLocal", sessionmaker(db.bind, expire_on_commit=False))

    class Unknown:
        calls = 0

        async def submit(self, code, cases):
            self.calls += 1
            assert len(cases) == 2
            raise JudgeSubmissionUnknown("unknown-test-only")

    judge = Unknown()
    asyncio.run(practice.dispatch_one(sid, judge))
    asyncio.run(practice.dispatch_one(sid, judge))
    assert judge.calls == 1
    db.expire_all()
    paper = client.get(f"/api/activities/{activity.id}/assessment?classroom_id={classroom['id']}", headers=student["headers"]).json()
    assert paper["status"] == "attention" and not paper["completed"] and paper["locked"]
    assert paper["score"] is None and paper["max_score"] == 100


def test_paper_is_private_to_authenticated_student_and_joined_class(client, db, student, teacher, classroom):
    paper = hand_in(client, db, student, classroom).json()
    other = client.post("/api/auth/register", json={"student_id": "150856393061526", "name": "Other", "password": "other-password-123"}).json()
    headers = {"Authorization": "Bearer " + other["access_token"]}
    assert client.get(url(classroom) + f"?classroom_id={classroom['id']}", headers=headers).status_code == 403
    client.post("/api/classrooms/join", json={"join_code": classroom["join_code"]}, headers=headers)
    other_paper = client.get(url(classroom) + f"?classroom_id={classroom['id']}", headers=headers).json()
    assert other_paper["id"] is None and other_paper["answers"] == [] and other_paper["results"] == []
    assert other_paper["id"] != paper["id"]
    assert hand_in(client, db, teacher, classroom).status_code == 403


@pytest.mark.parametrize("correct_count,expected_score", [(0, 0), (1, 33), (2, 66), (3, 100)])
def test_assessment_score_is_integer_percentage_without_changing_stored_judgments(client, db, student, classroom, correct_count, expected_score):
    correct, incorrect = answers(db), answers(db, correct=False)
    response = hand_in(client, db, student, classroom, entries=[*correct[:correct_count], *incorrect[correct_count:]])
    assert response.status_code == 201
    paper = response.json()
    assert paper["correct_count"] == correct_count and paper["total"] == 3
    assert type(paper["score"]) is int and paper["score"] == expected_score and paper["max_score"] == 100
    before = [(row.id, row.status, row.answer, row.code, row.updated_at) for row in db.scalars(select(Submission).order_by(Submission.id))]
    progress = client.get(f"/api/activities/ch1-assessment/progress?classroom_id={classroom['id']}", headers=student["headers"]).json()
    assert progress["score"] == expected_score and progress["max_score"] == 100
    assert progress["completed"] and progress["passed"] is (correct_count == 3)
    after = [(row.id, row.status, row.answer, row.code, row.updated_at) for row in db.scalars(select(Submission).order_by(Submission.id))]
    assert after == before


@pytest.mark.skipif(not os.getenv("REBORN_TEST_DATABASE_URL"), reason="Requires explicit isolated PostgreSQL to validate row locks")
def test_postgres_concurrent_hand_ins_have_one_atomic_paper():
    url_value = make_url(os.environ["REBORN_TEST_DATABASE_URL"])
    assert url_value.get_backend_name() == "postgresql" and "reborn" in url_value.database.lower()
    admin = create_engine(url_value)
    schema = "reborn_exam_" + uuid4().hex
    with admin.begin() as connection:
        connection.execute(text(f'CREATE SCHEMA "{schema}"'))
    engine = create_engine(url_value, connect_args={"options": f"-csearch_path={schema}"}, pool_size=4)
    factory = sessionmaker(engine, expire_on_commit=False)
    app = create_app()
    client = TestClient(app)
    try:
        Base.metadata.create_all(engine)
        with factory() as session:
            seed(session)
            teacher = User(student_id="119583011949881", name="teacher", role="teacher", password_hash="unused")
            student = User(student_id="198477289005076", name="student", role="student", password_hash="unused")
            session.add_all([teacher, student])
            session.flush()
            classroom = Classroom(name="exam-test-only", teacher_id=teacher.id, join_code=uuid4().hex[:20])
            session.add(classroom)
            session.flush()
            session.add_all([Membership(user_id=student.id, classroom_id=classroom.id),
                             ChapterRelease(classroom_id=classroom.id, chapter_id=179, status="published")])
            session.commit()
            from conftest import bind_test_course
            bind_test_course(session, classroom.id)
            token = issue(session, student)["access_token"]
            body = {"classroom_id": classroom.id, "idempotency_key": "concurrent-paper-123", "answers": answers(session)}

        def override():
            with factory() as session:
                yield session

        app.dependency_overrides[get_db] = override

        def submit():
            return client.post("/api/activities/ch1-assessment/assessment/submit", json=body,
                               headers={"Authorization": "Bearer " + token})

        with ThreadPoolExecutor(max_workers=2) as pool:
            responses = list(pool.map(lambda _: submit(), range(2)))
        assert [response.status_code for response in responses] == [201, 201]
        assert responses[0].json()["id"] == responses[1].json()["id"]
        assert responses[0].json()["results"] == responses[1].json()["results"]
        with factory() as session:
            assert session.scalar(select(func.count()).select_from(AssessmentAttempt)) == 1
            assert session.scalar(select(func.count()).select_from(Submission)) == 3
    finally:
        client.close()
        engine.dispose()
        with admin.begin() as connection:
            connection.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        admin.dispose()
