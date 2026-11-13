"""Operator rehearsal/application preserves each classroom independently."""
from copy import deepcopy

import pytest
from sqlalchemy import create_engine, select, func
from sqlalchemy.orm import sessionmaker

from app.db import Base
from app.models import Activity, Chapter, ChapterRelease, Classroom, CourseInstance, Material, User
from fixture_migration import migrate_classrooms
from fixture_seed import seed


@pytest.fixture
def migration_factory(tmp_path):
    engine = create_engine("sqlite:///" + (tmp_path / "classes.sqlite").as_posix())
    Base.metadata.create_all(engine)
    factory = sessionmaker(engine, expire_on_commit=False)
    with factory() as db:
        seed(db)
        teacher = User(student_id="886001", name="Teacher", role="teacher", password_hash="test")
        db.add(teacher)
        db.flush()
        for number in range(1, 4):
            classroom = Classroom(id=number, name=f"Class {number}", teacher_id=teacher.id, join_code=f"migration-{number}")
            db.add(classroom)
        db.flush()
        db.add_all(ChapterRelease(classroom_id=number, chapter_id=179) for number in (1, 2))
        db.commit()
    yield factory
    engine.dispose()


def test_rehearsal_rolls_back_then_apply_is_idempotent_and_skips_empty_class(migration_factory):
    with migration_factory() as db:
        material = deepcopy(db.get(Material, 186).content)
    report = migrate_classrooms(migration_factory, apply=False)
    assert report["ok"]
    assert [row["status"] for row in report["classrooms"]] == ["checked", "checked", "empty_class"]
    with migration_factory() as db:
        assert db.scalar(select(func.count()).select_from(CourseInstance)) == 0
        assert db.scalar(select(func.count()).select_from(Chapter)) == 7
        assert db.get(ChapterRelease, (1, 179)) is not None
        assert db.get(Material, 186).content == material
    report = migrate_classrooms(migration_factory, apply=True)
    assert [row["status"] for row in report["classrooms"]] == ["applied", "applied", "empty_class"]
    again = migrate_classrooms(migration_factory, [1, 2, 3], apply=True)
    assert [row["status"] for row in again["classrooms"]] == ["already_active", "already_active", "empty_class"]
    with migration_factory() as db:
        assert db.scalar(select(func.count()).select_from(CourseInstance)) == 2
        assert db.get(Material, 186).content == material


def test_failure_is_per_class_and_keeps_its_old_rows(migration_factory):
    with migration_factory() as db:
        teacher = User(student_id="886002", name="Bad source owner", role="teacher", password_hash="test")
        db.add(teacher)
        db.flush()
        db.get(Classroom, 2).teacher_id = teacher.id
        db.add(Activity(id="bad-reference", owner_id=teacher.id, chapter_id=179, title="Broken", kind="lesson", npc_key="missing-npc"))
        db.commit()
    report = migrate_classrooms(migration_factory, [1, 2], apply=True)
    assert not report["ok"]
    assert [row["status"] for row in report["classrooms"]] == ["applied", "failed"]
    with migration_factory() as db:
        assert db.scalar(select(func.count()).select_from(CourseInstance)) == 1
        assert db.get(ChapterRelease, (2, 179)) is not None
        assert db.get(Activity, "bad-reference").npc_key == "missing-npc"


def test_selection_errors_do_not_touch_any_class(migration_factory):
    report = migrate_classrooms(migration_factory, [999], apply=True)
    assert not report["ok"] and report["classrooms"][0]["status"] == "failed"
    for invalid in ([], [0], [True]):
        with pytest.raises(ValueError):
            migrate_classrooms(migration_factory, invalid)
    with migration_factory() as db:
        assert db.scalar(select(func.count()).select_from(CourseInstance)) == 0
