"""Opt-in PostgreSQL contention test. Creates only a temporary schema in Reborn DB."""

import os
from concurrent.futures import ThreadPoolExecutor
from uuid import uuid4
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select, text, func
from sqlalchemy.engine import make_url
from sqlalchemy.orm import sessionmaker
from app.auth import issue
from app.db import Base, get_db
from app.main import app
from app.models import User, Classroom, Membership, ChapterRelease, PlayerPosition, LearningEvent, NodeState
from app.seed import seed


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
            teacher = User(email="teacher@concurrency.test", name="teacher", role="teacher", password_hash="unused")
            student = User(email="student@concurrency.test", name="student", role="student", password_hash="unused")
            db.add_all([teacher, student])
            db.flush()
            classroom = Classroom(name="isolated contention", teacher_id=teacher.id, join_code=uuid4().hex[:20])
            db.add(classroom)
            db.flush()
            db.add(Membership(user_id=student.id, classroom_id=classroom.id))
            db.add(ChapterRelease(classroom_id=classroom.id, chapter_id=179, version=1, status="published"))
            db.add(PlayerPosition(user_id=student.id, classroom_id=classroom.id, map_key="town_hub", tile_x=22, tile_y=34, direction="up"))
            db.commit()
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
        payload = {"classroom_id": cid, "event_id": event_id, "quest_id": "arrival", "node_id": "greet", "evidence": {"npc_key": "mayor_py"}}

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
        client.close()
    finally:
        app.dependency_overrides.clear()
        app.dependency_overrides.update(previous)
        engine.dispose()
        with admin.begin() as connection:
            connection.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        admin.dispose()
