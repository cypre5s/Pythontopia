"""Opt-in real PostgreSQL durable queue + real go-judge execution."""
import asyncio
import os
import time
from uuid import uuid4

import pytest
from sqlalchemy import create_engine, select, text
from sqlalchemy.engine import make_url
from sqlalchemy.orm import sessionmaker

from app import practice
from app.db import Base
from app.go_judge import GoJudgeAdapter
from app.integration_models import SubmissionPayload
from app.models import Classroom, Submission, User
from fixture_seed import seed


@pytest.mark.skipif(not (os.getenv("REBORN_TEST_DATABASE_URL") and os.getenv("REBORN_LIVE_GO_JUDGE_URL")),
                    reason="Requires isolated PostgreSQL and live go-judge")
def test_live_postgres_bounded_queue_restart_and_claim(monkeypatch):
    url = make_url(os.environ["REBORN_TEST_DATABASE_URL"])
    assert url.get_backend_name() == "postgresql" and "reborn" in url.database.lower()
    admin = create_engine(url)
    schema = "reborn_test_" + uuid4().hex
    with admin.begin() as conn:
        conn.execute(text(f'CREATE SCHEMA "{schema}"'))
    engine = create_engine(url, connect_args={"options": f"-csearch_path={schema}"}, pool_size=6)
    factory = sessionmaker(engine, expire_on_commit=False)
    monkeypatch.setattr(practice, "SessionLocal", factory)
    monkeypatch.setenv("REBORN_GO_JUDGE_URL", os.environ["REBORN_LIVE_GO_JUDGE_URL"])
    try:
        Base.metadata.create_all(engine)
        with factory() as db:
            seed(db)
            user = User(student_id="991414001", name="isolated queue test", role="teacher", password_hash="unused")
            db.add(user)
            db.flush()
            classroom = Classroom(name="isolated queue", teacher_id=user.id, join_code=uuid4().hex[:20])
            db.add(classroom)
            db.flush()
            ids = []
            for i in range(13):
                row = Submission(user_id=user.id, classroom_id=classroom.id, activity_id="ch1-practice",
                                 question_id="ch1-abstraction", idempotency_key=f"isolated-{i}", mode="submit",
                                 status="prepared", code="print(int(input())+1)")
                db.add(row)
                db.flush()
                db.add(SubmissionPayload(submission_id=row.id, public_count=1,
                                         cases=[{"input": str(n), "output": str(n+1)} for n in range(5)]))
                ids.append(row.id)
            db.commit()

        async def scenario():
            # Two dispatchers race for one receipt: DB compare-and-set allows one POST batch.
            class Counted(GoJudgeAdapter):
                calls = 0

                async def execute(self, *args):
                    self.calls += 1
                    return await super().execute(*args)

            adapter = Counted()
            await asyncio.gather(*(practice.dispatch_one(ids[0], adapter) for _ in range(2)))
            assert adapter.calls == 1
            # Simulate a process restart before dispatch. Prepared work survives.
            practice._recover_dispatching()
            start = time.perf_counter()
            await practice.reconcile_once()
            with factory() as db:
                rows = list(db.scalars(select(Submission)))
                assert sum(r.status == "accepted" for r in rows) == 5
                assert sum(r.status == "prepared" for r in rows) == 8
            await practice.reconcile_once()
            await practice.reconcile_once()
            print(f"PostgreSQL queue: 12 submissions x 5 cases in {time.perf_counter()-start:.3f}s")
            with factory() as db:
                rows = list(db.scalars(select(Submission)))
                assert all(r.status == "accepted" and r.feedback["passed_count"] == 5 for r in rows)
                assert all(len(r.feedback["samples"]) == 1 for r in rows)
                rows[0].status = "dispatching"
                db.commit()
            practice._recover_dispatching()
            await practice.reconcile_once()
            with factory() as db:
                assert db.get(Submission, ids[0]).status == "submission_unknown"

        asyncio.run(scenario())
    finally:
        engine.dispose()
        with admin.begin() as conn:
            conn.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        admin.dispose()
