"""Opt-in PostgreSQL contention test. Creates only a temporary schema in Reborn DB."""

import os
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
from uuid import uuid4
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select, text, func
from sqlalchemy.engine import make_url
from sqlalchemy.orm import sessionmaker
from app.auth import issue
from app.db import Base, get_db
from app.main import app
from app.models import User, Classroom, Membership, ChapterRelease, PlayerPosition, LearningEvent, NodeState, StorySession, QuestDefinition
from fixture_seed import seed


@pytest.mark.skipif(
    not os.getenv("REBORN_TEST_DATABASE_URL"), reason="Requires an explicit isolated Reborn PostgreSQL connection; SQLite does not verify row locking"
)
def test_postgres_duplicate_events_under_contention():
    url = make_url(os.environ["REBORN_TEST_DATABASE_URL"])
    assert url.get_backend_name() == "postgresql" and "reborn" in url.database.lower(), "Only explicit Reborn database may be tested"
    admin = create_engine(url)
    schema = "reborn_test_" + uuid4().hex
    assert schema.startswith("reborn_test_") and schema.replace("_", "").isalnum()
    with admin.begin() as connection:
        connection.execute(text(f'CREATE SCHEMA "{schema}"'))
    engine = create_engine(url, connect_args={"options": f"-csearch_path={schema}"}, pool_size=4)
    factory = sessionmaker(engine, expire_on_commit=False)
    previous = dict(app.dependency_overrides)
    try:
        Base.metadata.create_all(engine)
        with factory() as db:
            seed(db)
            definition = {"id": "contention-talk", "title": "Contention", "chapter_id": 179, "kind": "side",
                          "nodes": [{"id": "greet", "type": "REACH_LOCATION", "title": "Reach", "target": "town_square", "dependencies": []}], "effects": []}
            db.add(QuestDefinition(id=definition["id"], chapter_id=179, title="Contention", definition=definition))
            teacher = User(student_id="193582816979202", name="teacher", role="teacher", password_hash="unused")
            student = User(student_id="870446112008", name="student", role="student", password_hash="unused")
            db.add_all([teacher, student])
            db.flush()
            classroom = Classroom(name="isolated contention", teacher_id=teacher.id, join_code=uuid4().hex[:20])
            db.add(classroom)
            db.flush()
            db.add(Membership(user_id=student.id, classroom_id=classroom.id))
            db.add(ChapterRelease(classroom_id=classroom.id, chapter_id=179, status="published"))
            db.add(PlayerPosition(user_id=student.id, classroom_id=classroom.id, map_key="town_hub", tile_x=22, tile_y=34, direction="up"))
            db.commit()
            from conftest import bind_test_course
            bind_test_course(db, classroom.id)
            token = issue(db, student)["access_token"]
            cid = classroom.id

        def override_db():
            with factory() as db:
                yield db

        app.dependency_overrides[get_db] = override_db
        headers = {"Authorization": "Bearer " + token}
        client = TestClient(app)
        assert client.get(f"/api/world?classroom_id={cid}", headers=headers).status_code == 200
        event_id = str(uuid4())
        payload = {"classroom_id": cid, "event_id": event_id, "quest_id": "contention-talk", "node_id": "greet", "evidence": {}}

        # Do not enter lifespan: the service workers use the live app database and are unrelated to this isolated schema.
        def send(_):
            concurrent_client = TestClient(app)
            try:
                response = concurrent_client.post("/api/events", json=payload, headers=headers)
                return response.status_code, response.json()
            finally:
                concurrent_client.close()

        with ThreadPoolExecutor(max_workers=4) as executor:
            results = list(executor.map(send, range(4)))
        assert all(status == 200 for status, _ in results), results
        assert sum(not result["duplicate"] for _, result in results) == 1
        with factory() as db:
            assert db.scalar(select(func.count()).select_from(LearningEvent)) == 1
            assert db.scalar(select(func.count()).select_from(NodeState)) == 1
            second = User(student_id="161766572741808", name="story student", role="student", password_hash="unused")
            db.add(second)
            db.flush()
            second_id = second.id
            db.add(Membership(user_id=second.id, classroom_id=cid))
            db.add(PlayerPosition(user_id=second.id, classroom_id=cid, map_key="town_hub", tile_x=22, tile_y=34, direction="up"))
            db.commit()
            story_headers = {"Authorization": "Bearer " + issue(db, second)["access_token"]}
        start_barrier = Barrier(4)

        def start_story(_):
            story_client = TestClient(app)
            try:
                start_barrier.wait(timeout=10)
                response = story_client.post("/api/story/interactions", json={"classroom_id": cid, "npc_key": "mayor_py"}, headers=story_headers)
                return response.status_code, response.json()
            finally:
                story_client.close()

        with ThreadPoolExecutor(max_workers=4) as executor:
            starts = list(executor.map(start_story, range(4)))
        assert all(code == 200 for code, _ in starts), starts
        assert len({result["id"] for _, result in starts}) == 1, starts
        story = starts[0][1]
        path = f"/api/story/sessions/{story['id']}/advance"
        first_line = client.post(path, json={"classroom_id": cid, "revision": story["revision"], "event_id": str(uuid4())}, headers=story_headers)
        assert first_line.status_code == 200, first_line.text
        terminal = {"classroom_id": cid, "revision": first_line.json()["revision"], "event_id": str(uuid4())}

        # Do not enter application lifespan: only this temporary schema may be used.
        def advance_story(_):
            story_client = TestClient(app)
            try:
                response = story_client.post(path, json=terminal, headers=story_headers)
                return response.status_code, response.json()
            finally:
                story_client.close()

        with ThreadPoolExecutor(max_workers=4) as executor:
            replies = list(executor.map(advance_story, range(4)))
        assert all(code == 200 and result["revision"] == 2 and result["status"] == "ended" for code, result in replies), replies
        with factory() as db:
            assert db.scalar(select(func.count()).select_from(LearningEvent).where(LearningEvent.user_id == second_id)) == 1
            assert db.scalar(select(func.count()).select_from(NodeState).where(NodeState.node_id == "welcome")) == 1
            assert db.get(StorySession, story["id"]).revision == 2
        client.close()
    finally:
        app.dependency_overrides.clear()
        app.dependency_overrides.update(previous)
        engine.dispose()
        with admin.begin() as connection:
            connection.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        admin.dispose()
