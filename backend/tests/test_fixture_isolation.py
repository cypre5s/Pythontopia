"""Committed mutations must never leak into another test or its seed template."""
from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session
from app.models import User, Material


def test_seed_copies_isolate_commits_deletes_and_sessions(database_factory):
    with Session(database_factory()) as first, Session(database_factory()) as second:
        count = second.scalar(select(func.count()).select_from(Material))
        assert count > 0
        first.execute(delete(Material))
        first.add(User(student_id="99999900001", name="only first", role="student", password_hash="unused"))
        first.commit()
        assert second.scalar(select(func.count()).select_from(Material)) == count
        assert second.scalar(select(User).where(User.student_id == "99999900001")) is None
        with Session(database_factory()) as fresh:
            assert fresh.scalar(select(func.count()).select_from(Material)) == count
            assert fresh.scalar(select(User).where(User.student_id == "99999900001")) is None
