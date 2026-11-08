"""Self-only development replay, tested on isolated fixture data without service lifespan."""

import os
from concurrent.futures import ThreadPoolExecutor
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, func, select, text
from sqlalchemy.engine import make_url
from sqlalchemy.orm import sessionmaker

from app import dev_tools
from app.auth import issue
from app.config import dev_tools_enabled
from app.db import Base, get_db
from app.integration_models import AssistantCall, AssistantSession, SubmissionPayload
from app.main import create_app
from app.models import (
    Activity,
    ActivityProgress,
    AssessmentAttempt,
    AuthSession,
    ChapterRelease,
    Classroom,
    LearningEvent,
    LearningRecord,
    Membership,
    NodeState,
    PlayerPosition,
    PlayerQuest,
    StorySession,
    Submission,
    User,
    WorldFlag,
)
from app.world_config import load_world_config

RESET_PATH = "/api/dev/progress/reset"
RESET_MODELS = (LearningEvent, NodeState, PlayerQuest, StorySession, ActivityProgress, LearningRecord,
                SubmissionPayload, Submission, WorldFlag, PlayerPosition, AssessmentAttempt)


@pytest.fixture
def dev_client(db, monkeypatch):
    monkeypatch.setenv("REBORN_ENV", "development")
    monkeypatch.setenv("REBORN_DEV_TOOLS", "1")
    db.connection().exec_driver_sql("PRAGMA foreign_keys=ON")
    assert db.connection().exec_driver_sql("PRAGMA foreign_keys").scalar() == 1
    app = create_app()

    def override():
        yield db

    app.dependency_overrides[get_db] = override
    client = TestClient(app)
    yield client
    client.close()


def counts(db, models=RESET_MODELS):
    return {model.__tablename__: db.scalar(select(func.count()).select_from(model)) for model in models}


def add_progress(db, user_id, classroom_id):
    quest = PlayerQuest(user_id=user_id, classroom_id=classroom_id, quest_id="arrival", status="complete")
    db.add(quest)
    db.flush()
    submission = Submission(user_id=user_id, classroom_id=classroom_id, activity_id="ch1-practice", question_id="ch1-abstraction",
                            idempotency_key=str(uuid4()), status="accepted", answer="test-only answer")
    assistant = AssistantSession(user_id=user_id, classroom_id=classroom_id, activity_id="ch1-lesson", messages=[])
    db.add_all([submission, assistant])
    db.flush()
    db.add_all([
        NodeState(player_quest_id=quest.id, node_id="welcome", status="complete"),
        LearningEvent(event_id=str(uuid4()), user_id=user_id, classroom_id=classroom_id, player_quest_id=quest.id,
                      node_id="welcome", evidence={}),
        StorySession(user_id=user_id, classroom_id=classroom_id, story_id="first-steps", npc_key="mayor_py",
                     node_id="end", status="ended"),
        ActivityProgress(user_id=user_id, classroom_id=classroom_id, activity_id="ch1-lesson", visited_pages=[10723], completed=True),
        LearningRecord(user_id=user_id, classroom_id=classroom_id, activity_id="ch1-lesson", data={"test": True}),
        SubmissionPayload(submission_id=submission.id, cases=[], public_count=0),
        AssessmentAttempt(user_id=user_id, classroom_id=classroom_id, activity_id="ch1-assessment", state="draft"),
        WorldFlag(user_id=user_id, classroom_id=classroom_id, key="library_unlocked"),
        PlayerPosition(user_id=user_id, classroom_id=classroom_id, map_key="library", tile_x=9, tile_y=5, direction="up"),
        AssistantCall(user_id=user_id, classroom_id=classroom_id, session_id=assistant.id, status="complete", model_calls=1, tokens=32),
    ])
    db.commit()
    return submission.id


@pytest.mark.parametrize("environment,flag", [
    (None, "1"), ("development", None), ("development", "0"), ("production", "1"), ("production", None), ("Development", "1"),
])
def test_unavailable_environment_has_no_reset_route(monkeypatch, environment, flag):
    for key, value in (("REBORN_ENV", environment), ("REBORN_DEV_TOOLS", flag)):
        if value is None:
            monkeypatch.delenv(key, raising=False)
        else:
            monkeypatch.setenv(key, value)
    assert not dev_tools_enabled()
    app = create_app()
    client = TestClient(app)
    try:
        assert RESET_PATH not in client.get("/openapi.json").json()["paths"]
        assert client.post(RESET_PATH, json={"classroom_id": 1, "confirmed": True}).status_code == 404
    finally:
        client.close()


def test_capability_in_me_login_and_registration_is_student_only(dev_client, student, teacher):
    assert dev_client.get("/api/auth/me", headers=student["headers"]).json()["dev_tools_enabled"] is True
    assert dev_client.get("/api/auth/me", headers=teacher["headers"]).json()["dev_tools_enabled"] is False
    logged_in = dev_client.post("/api/auth/login", json={"email": student["email"], "password": "student-password-123"})
    assert logged_in.status_code == 200 and logged_in.json()["user"]["dev_tools_enabled"] is True
    registered = dev_client.post("/api/auth/register", json={"email": "dev-new@example.test", "name": "开发验收", "password": "student-password-123"})
    assert registered.status_code == 201 and registered.json()["user"]["dev_tools_enabled"] is True


def test_runtime_guard_disables_existing_route_and_capability(dev_client, db, student, classroom, monkeypatch):
    add_progress(db, student["id"], classroom["id"])
    before = counts(db)
    monkeypatch.setenv("REBORN_ENV", "production")
    assert dev_client.get("/api/auth/me", headers=student["headers"]).json()["dev_tools_enabled"] is False
    response = dev_client.post(RESET_PATH, json={"classroom_id": classroom["id"], "confirmed": True}, headers=student["headers"])
    assert response.status_code == 404 and counts(db) == before


def test_reset_is_atomic_scoped_and_repeatable(dev_client, db, student, teacher, classroom):
    cid = classroom["id"]
    other_classroom = Classroom(name="另一个班", teacher_id=teacher["id"], join_code=uuid4().hex[:20])
    other_student = User(email="dev-other@example.test", name="另一学生", role="student", password_hash="unused-test-only")
    db.add_all([other_classroom, other_student])
    db.flush()
    db.add_all([Membership(user_id=student["id"], classroom_id=other_classroom.id), Membership(user_id=other_student.id, classroom_id=cid)])
    db.commit()
    target_submission = add_progress(db, student["id"], cid)
    other_submission = add_progress(db, other_student.id, cid)
    other_class_submission = add_progress(db, student["id"], other_classroom.id)
    preserved = (User, AuthSession, Membership, Classroom, ChapterRelease, Activity, AssistantSession, AssistantCall)
    preserved_counts = counts(db, preserved)

    response = dev_client.post(RESET_PATH, json={"classroom_id": cid, "confirmed": True}, headers=student["headers"])
    assert response.status_code == 200, response.text
    assert response.json() == {"ok": True, "user_id": student["id"], "classroom_id": cid,
                               "deleted": {model.__tablename__: 1 for model in RESET_MODELS}}
    db.expire_all()
    assert counts(db) == {model.__tablename__: 2 for model in RESET_MODELS}
    assert counts(db, preserved) == preserved_counts
    assert db.get(Submission, target_submission) is None
    assert db.get(Submission, other_submission) and db.get(Submission, other_class_submission)
    assert db.scalar(select(func.count()).select_from(PlayerQuest).where(PlayerQuest.user_id == student["id"], PlayerQuest.classroom_id == cid)) == 0
    repeated = dev_client.post(RESET_PATH, json={"classroom_id": cid, "confirmed": True}, headers=student["headers"])
    assert repeated.status_code == 200 and all(value == 0 for value in repeated.json()["deleted"].values())

    # The reset does not recreate tasks; the next ordinary world read does.
    world = dev_client.get(f"/api/world?classroom_id={cid}", headers=student["headers"]).json()
    assert world["player"] == load_world_config()["default_spawn"] and world["flags"] == []
    assert world["quests"] and all(quest["status"] == "active" for quest in world["quests"])
    assert dev_client.get("/api/auth/me", headers=student["headers"]).status_code == 200


@pytest.mark.parametrize("status", sorted(dev_tools.PENDING_SUBMISSIONS))
def test_pending_judge_refuses_reset_without_mutation(dev_client, db, student, classroom, status):
    sid = add_progress(db, student["id"], classroom["id"])
    db.get(Submission, sid).status = status
    db.commit()
    before = counts(db)
    response = dev_client.post(RESET_PATH, json={"classroom_id": classroom["id"], "confirmed": True}, headers=student["headers"])
    assert response.status_code == 409 and counts(db) == before
    assert db.get(Submission, sid).status == status


@pytest.mark.parametrize("status", ["running", "cancel_requested"])
def test_active_assistant_refuses_reset_and_preserves_audit(dev_client, db, student, classroom, status):
    add_progress(db, student["id"], classroom["id"])
    call = db.scalar(select(AssistantCall))
    call.status = status
    db.commit()
    before = counts(db)
    response = dev_client.post(RESET_PATH, json={"classroom_id": classroom["id"], "confirmed": True}, headers=student["headers"])
    assert response.status_code == 409 and counts(db) == before
    assert db.get(AssistantCall, call.id).status == status


def test_deletion_failure_rolls_back_every_table(dev_client, db, student, classroom, monkeypatch):
    add_progress(db, student["id"], classroom["id"])
    before = counts(db)
    original_delete = dev_tools._delete_rows

    def fail_late(session, model, *conditions):
        if model is WorldFlag:
            raise RuntimeError("test-only forced rollback")
        return original_delete(session, model, *conditions)

    monkeypatch.setattr(dev_tools, "_delete_rows", fail_late)
    with pytest.raises(RuntimeError, match="test-only forced rollback"):
        dev_client.post(RESET_PATH, json={"classroom_id": classroom["id"], "confirmed": True}, headers=student["headers"])
    assert counts(db) == before
    assert db.get(Membership, (student["id"], classroom["id"]))


@pytest.mark.parametrize("body", [
    {"confirmed": False}, {"confirmed": "true"}, {"confirmed": 1}, {"confirmed": None},
    {"confirmed": True, "userId": 999}, {"confirmed": True, "user_id": 999},
    {"confirmed": True, "classroom_id": True}, {"confirmed": True, "classroom_id": "1"}, {},
])
def test_reset_requires_strict_confirmation_and_rejects_user_selection(dev_client, db, student, classroom, body):
    add_progress(db, student["id"], classroom["id"])
    before = counts(db)
    response = dev_client.post(RESET_PATH, json={"classroom_id": classroom["id"], **body}, headers=student["headers"])
    assert response.status_code == 422 and counts(db) == before


def test_authentication_and_membership_are_required(dev_client, db, student, teacher, classroom):
    body = {"classroom_id": classroom["id"], "confirmed": True}
    assert dev_client.post(RESET_PATH, json=body).status_code == 401
    assert dev_client.post(RESET_PATH, json=body, headers=teacher["headers"]).status_code == 403
    admin = User(email="dev-admin@example.test", name="管理员", role="admin", password_hash="unused-test-only")
    db.add(admin)
    db.commit()
    admin_headers = {"Authorization": "Bearer " + issue(db, admin)["access_token"]}
    assert dev_client.get("/api/auth/me", headers=admin_headers).json()["dev_tools_enabled"] is False
    assert dev_client.post(RESET_PATH, json=body, headers=admin_headers).status_code == 403
    other = Classroom(name="无权班级", teacher_id=teacher["id"], join_code=uuid4().hex[:20])
    db.add(other)
    db.commit()
    assert dev_client.post(RESET_PATH, json={**body, "classroom_id": other.id}, headers=student["headers"]).status_code == 403
    assert dev_client.post(RESET_PATH, json={**body, "classroom_id": other.id + 1000}, headers=student["headers"]).status_code == 403


@pytest.mark.skipif(not os.getenv("REBORN_TEST_DATABASE_URL"), reason="Requires explicit isolated Reborn PostgreSQL; SQLite does not verify row locks")
def test_postgres_repeated_resets_are_serialized(monkeypatch):
    url = make_url(os.environ["REBORN_TEST_DATABASE_URL"])
    assert url.get_backend_name() == "postgresql" and "reborn" in url.database.lower()
    monkeypatch.setenv("REBORN_ENV", "development")
    monkeypatch.setenv("REBORN_DEV_TOOLS", "1")
    admin = create_engine(url)
    schema = "reborn_test_" + uuid4().hex
    with admin.begin() as connection:
        connection.execute(text(f'CREATE SCHEMA "{schema}"'))
    engine = create_engine(url, connect_args={"options": f"-csearch_path={schema}"}, pool_size=4)
    factory = sessionmaker(engine, expire_on_commit=False)
    app = create_app()
    try:
        Base.metadata.create_all(engine)
        with factory() as db:
            teacher = User(email="dev-teacher@contention.test", name="teacher", role="teacher", password_hash="unused")
            actor = User(email="dev-student@contention.test", name="student", role="student", password_hash="unused")
            db.add_all([teacher, actor])
            db.flush()
            classroom = Classroom(name="isolated reset", teacher_id=teacher.id, join_code=uuid4().hex[:20])
            db.add(classroom)
            db.flush()
            cid, user_id = classroom.id, actor.id
            db.add(Membership(user_id=user_id, classroom_id=cid))
            db.add(WorldFlag(user_id=user_id, classroom_id=cid, key="test-reset"))
            db.commit()
            headers = {"Authorization": "Bearer " + issue(db, actor)["access_token"]}

        def override():
            with factory() as db:
                yield db

        app.dependency_overrides[get_db] = override

        def reset(_):
            client = TestClient(app)
            try:
                response = client.post(RESET_PATH, json={"classroom_id": cid, "confirmed": True}, headers=headers)
                return response.status_code, response.json()
            finally:
                client.close()

        with ThreadPoolExecutor(max_workers=4) as executor:
            replies = list(executor.map(reset, range(4)))
        assert all(status == 200 for status, _ in replies), replies
        assert sum(reply["deleted"]["world_flags"] for _, reply in replies) == 1
        with factory() as db:
            assert db.get(Membership, (user_id, cid)) and db.get(User, user_id)
            assert db.scalar(select(func.count()).select_from(WorldFlag)) == 0
    finally:
        engine.dispose()
        with admin.begin() as connection:
            connection.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        admin.dispose()
