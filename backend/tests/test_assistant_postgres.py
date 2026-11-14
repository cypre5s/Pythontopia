"""Actual row locks prevent duplicate assistant dispatch across HTTP requests."""
import asyncio
import os
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessageChunk
from sqlalchemy import create_engine, select, text, func
from sqlalchemy.orm import sessionmaker

from app import assistant
from app.auth import issue
from app.db import Base, get_db
from app.integration_models import AssistantCall
from app.main import app
from app.models import User, Classroom, Membership, ChapterRelease
from fixture_seed import seed
from conftest import bind_test_course


@pytest.mark.skipif(not os.getenv("REBORN_TEST_DATABASE_URL"), reason="Requires isolated PostgreSQL")
def test_duplicate_turn_reserves_and_dispatches_once(monkeypatch):
    url = os.environ["REBORN_TEST_DATABASE_URL"]
    admin = create_engine(url)
    schema = "reborn_assistant_" + uuid4().hex
    with admin.begin() as connection:
        connection.execute(text(f'CREATE SCHEMA "{schema}"'))
    engine = create_engine(url, connect_args={"options": f"-csearch_path={schema}"})
    factory = sessionmaker(engine, expire_on_commit=False)
    original = dict(app.dependency_overrides)
    invoked = []

    class DelayedAgent:
        async def astream(self, *args, **kwargs):
            invoked.append(True)
            await asyncio.sleep(.2)
            yield AIMessageChunk(content="测试提示", id="fixture", usage_metadata={"input_tokens": 1, "output_tokens": 1, "total_tokens": 2}), {"langgraph_node": "model"}

    monkeypatch.setattr(assistant.AgentFactory, "create", lambda *args: DelayedAgent())
    monkeypatch.setattr(assistant, "SessionLocal", factory)
    monkeypatch.setenv("REBORN_AI_API_KEY", "test-only")
    monkeypatch.setenv("REBORN_AI_MODEL", "test-only")
    try:
        Base.metadata.create_all(engine)
        with factory() as db:
            seed(db)
            teacher = User(student_id="99001", name="teacher", role="teacher", password_hash="unused")
            student = User(student_id="99002", name="student", role="student", password_hash="unused")
            db.add_all([teacher, student])
            db.flush()
            classroom = Classroom(name="isolated assistant", teacher_id=teacher.id, join_code=uuid4().hex[:20])
            db.add(classroom)
            db.flush()
            db.add(Membership(user_id=student.id, classroom_id=classroom.id))
            db.add(ChapterRelease(classroom_id=classroom.id, chapter_id=179, status="published"))
            db.commit()
            bind_test_course(db, classroom.id)
            headers = {"Authorization": "Bearer " + issue(db, student)["access_token"]}
            cid = classroom.id

        def dependency():
            with factory() as db:
                yield db
        app.dependency_overrides[get_db] = dependency
        # Do not enter lifespan: unrelated background workers use the app database.
        client = TestClient(app)
        response = client.post("/api/assistant/sessions", headers=headers, json={"classroom_id": cid, "activity_id": "ch1-lesson"})
        sid = response.json()["id"]
        barrier = Barrier(2)
        payload = {"message": "解释", "state": {"page_no": 42}, "expected_revision": 0, "client_request_id": "duplicate"}

        def send(_):
            barrier.wait()
            with_factory_client = TestClient(app)
            try:
                return with_factory_client.post(f"/api/assistant/sessions/{sid}/messages", headers=headers, json=payload)
            finally:
                with_factory_client.close()
        with ThreadPoolExecutor(max_workers=2) as pool:
            responses = list(pool.map(send, range(2)))
        assert any("event: done" in response.text for response in responses)
        assert all(response.status_code in {200, 409} for response in responses)
        assert len(invoked) == 1
        with factory() as db:
            assert db.scalar(select(func.count()).select_from(AssistantCall)) == 1
        assert "replayed" in client.post(f"/api/assistant/sessions/{sid}/messages", headers=headers, json=payload).text
        assert len(invoked) == 1
        client.close()
    finally:
        app.dependency_overrides = original
        engine.dispose()
        with admin.begin() as connection:
            connection.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        admin.dispose()
