"""Migrate real relationship references without replacing student evidence."""

from datetime import datetime, timezone
import os
from pathlib import Path
import subprocess
import sys

import pytest
from sqlalchemy import MetaData, create_engine, event, inspect, select, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.content_relations import (
    activity_questions,
    material_chapter_ids,
    material_in_chapter,
    question_chapter_ids,
    question_knowledge_point_ids,
    set_activity_questions,
    set_material_chapters,
    set_question_chapters,
    set_question_knowledge_points,
)
from app.models import Activity, ActivityQuestion, Question

BACKEND = Path(__file__).resolve().parents[1]


def migrate(path, target="head", *, check=True):
    result = subprocess.run([sys.executable, "-m", "alembic", "upgrade", target], cwd=BACKEND,
                            env={**os.environ, "REBORN_DATABASE_URL": "sqlite:///" + path.as_posix()},
                            capture_output=True, text=True, timeout=45)
    if check:
        assert result.returncode == 0, result.stderr
    return result


def engine_for(path):
    engine = create_engine("sqlite:///" + path.as_posix())

    @event.listens_for(engine, "connect")
    def foreign_keys(connection, _):
        connection.execute("PRAGMA foreign_keys=ON")

    return engine


def old_content(path):
    migrate(path, "c3187f5db204")
    engine = engine_for(path)
    metadata = MetaData()
    metadata.reflect(engine)
    t, timestamp = metadata.tables, datetime.now(timezone.utc)
    with engine.begin() as db:
        db.execute(t["users"].insert(), [
            {"id": 1, "student_id": "80001", "name": "Teacher", "role": "teacher", "password_hash": "fixture"},
            {"id": 2, "student_id": "80002", "name": "Student", "role": "student", "password_hash": "fixture"},
        ])
        db.execute(t["chapters"].insert(), [
            {"id": 1, "title": "Loops", "order_index": 1, "course_id": 43, "owner_id": 1},
            {"id": 2, "title": "Review", "order_index": 2, "course_id": 43, "owner_id": 1},
        ])
        db.execute(t["knowledge_points"].insert(), {"id": 1, "chapter_id": 1, "title": "Iteration", "owner_id": 1, "content": {}})
        db.execute(t["materials"].insert(), {"id": 10, "chapter_id": 1, "title": "Notes", "kind": "pdf", "owner_id": 1,
                                             "content": {"pages": [{"id": 41, "page_no": 1}]}, "status": "ready", "created_at": timestamp})
        db.execute(t["activities"].insert(), [
            {"id": "practice", "chapter_id": 1, "title": "Practice", "kind": "practice", "owner_id": 1,
             "assistant_allowed": True, "npc_key": "professor", "content": {"question_ids": ["q2", "q1"], "published": True}},
            {"id": "review", "chapter_id": 2, "title": "Review", "kind": "practice", "owner_id": 1,
             "assistant_allowed": True, "npc_key": "professor", "content": {"published": True}},
        ])
        db.execute(t["questions"].insert(), [
            {"id": "q1", "chapter_id": 1, "activity_id": "practice", "title": "First", "kind": "boolean",
             "prompt": "One?", "options": [], "samples": [], "source": {"kind": "teacher_authored"}},
            {"id": "q2", "chapter_id": 1, "activity_id": "practice", "title": "Second", "kind": "boolean",
             "prompt": "Two?", "options": [], "samples": [], "source": {"kind": "ai_generated", "reviewed": False}},
            {"id": "q3", "chapter_id": 2, "activity_id": "review", "title": "Fallback", "kind": "boolean",
             "prompt": "Three?", "options": [], "samples": [], "source": {}},
        ])
        db.execute(t["question_secrets"].insert(), [
            {"question_id": "q1", "answer": True, "hidden_cases": []},
            {"question_id": "q2", "answer": False, "hidden_cases": []},
            {"question_id": "q3", "answer": True, "hidden_cases": []},
        ])
        db.execute(t["classrooms"].insert(), {"id": 1, "name": "Existing", "teacher_id": 1, "join_code": "migration-only"})
        db.execute(t["memberships"].insert(), {"user_id": 2, "classroom_id": 1})
        db.execute(t["submissions"].insert(), {"id": "kept-submission", "user_id": 2, "classroom_id": 1,
            "activity_id": "practice", "question_id": "q1", "idempotency_key": "kept-evidence", "mode": "submit",
            "status": "accepted", "answer": True, "feedback": {"detail": "correct"}, "created_at": timestamp, "updated_at": timestamp})
        db.execute(t["assessment_attempts"].insert(), {"id": "kept-attempt", "user_id": 2, "classroom_id": 1,
            "activity_id": "practice", "state": "submitted", "revision": 3, "question_ids": ["q1"],
            "answers": {"q1": {"answer": True}}, "submission_ids": {"q1": "kept-submission"},
            "created_at": timestamp, "updated_at": timestamp})
        assert not db.execute(text("PRAGMA foreign_key_check")).all()
    engine.dispose()


def test_empty_migration_and_metadata_match(tmp_path):
    path = tmp_path / "empty.db"
    migrate(path)
    engine = engine_for(path)
    with engine.connect() as db:
        assert "chapter_id" not in {row["name"] for row in inspect(db).get_columns("materials")}
        assert not {"chapter_id", "activity_id"} & {row["name"] for row in inspect(db).get_columns("questions")}
        assert {"material_chapters", "question_chapters", "question_knowledge_points", "activity_questions"}.issubset(inspect(db).get_table_names())
        assert not db.execute(text("PRAGMA foreign_key_check")).all()
    engine.dispose()
    result = subprocess.run([sys.executable, "-m", "alembic", "check"], cwd=BACKEND,
                            env={**os.environ, "REBORN_DATABASE_URL": "sqlite:///" + path.as_posix()},
                            capture_output=True, text=True, timeout=45)
    assert result.returncode == 0, result.stderr + result.stdout


def test_existing_relationships_order_answers_and_submissions_survive(tmp_path):
    path = tmp_path / "existing.db"
    old_content(path)
    migrate(path)
    engine = engine_for(path)
    with Session(engine) as db:
        assert material_chapter_ids(db, 10) == [1]
        assert question_chapter_ids(db, "q1") == [1]
        assert [row.id for row in activity_questions(db, "practice")] == ["q2", "q1"]
        assert [row.id for row in activity_questions(db, "review")] == ["q3"]
        assert db.get(Question, "q1").owner_id == 1
        assert (db.get(Question, "q1").source_type, db.get(Question, "q1").status) == ("imported", "ready")
        assert (db.get(Question, "q2").source_type, db.get(Question, "q2").status) == ("ai_generated", "draft")
        assert db.get(Activity, "practice").content == {"published": True}
        assert db.execute(text("SELECT status FROM submissions WHERE id='kept-submission'")).scalar() == "accepted"
        assert db.execute(text("SELECT answer FROM question_secrets WHERE question_id='q1'")).scalar() == "true"
        assert db.execute(text("SELECT revision FROM assessment_attempts WHERE id='kept-attempt'")).scalar() == 3
        assert not db.execute(text("PRAGMA foreign_key_check")).all()
    engine.dispose()


def test_relations_reuse_order_validation_and_constraints(tmp_path):
    path = tmp_path / "reuse.db"
    old_content(path)
    migrate(path)
    engine = engine_for(path)
    with Session(engine) as db:
        set_material_chapters(db, 10, [2, 1])
        assert material_chapter_ids(db, 10) == [1, 2]
        assert material_in_chapter(db, 10, 2)
        set_question_chapters(db, "q1", [1, 2])
        set_question_knowledge_points(db, "q1", [1])
        set_activity_questions(db, db.get(Activity, "review"), ["q3", "q1"])
        assert [row.id for row in activity_questions(db, "review")] == ["q3", "q1"]
        assert [row.id for row in activity_questions(db, "practice")] == ["q2", "q1"]
        assert question_chapter_ids(db, "q1") == [1, 2]
        assert question_knowledge_point_ids(db, "q1") == [1]
        set_activity_questions(db, "review", ["q1", "q3"])
        assert [row.id for row in activity_questions(db, "review")] == ["q1", "q3"]
        for operation in (
            lambda: set_material_chapters(db, 10, [True]),
            lambda: set_question_chapters(db, "q1", [1, 1]),
            lambda: set_question_knowledge_points(db, "q1", [999]),
            lambda: set_activity_questions(db, "review", ["missing"]),
        ):
            with pytest.raises(ValueError):
                operation()
        assert [row.id for row in activity_questions(db, "review")] == ["q1", "q3"]
        db.commit()
        assert not db.execute(text("PRAGMA foreign_key_check")).all()
        with pytest.raises(IntegrityError):
            db.execute(text("UPDATE questions SET source_type='manual' WHERE id='q1'"))
        db.rollback()
        with pytest.raises(IntegrityError):
            db.execute(text("UPDATE questions SET status='unknown' WHERE id='q1'"))
        db.rollback()
        with pytest.raises(IntegrityError):
            db.add(ActivityQuestion(activity_id="review", question_id="q1", order_index=0))
            db.flush()
        db.rollback()
        assert len(list(db.scalars(select(ActivityQuestion).where(ActivityQuestion.question_id == "q1")))) == 2
    engine.dispose()


def test_malformed_legacy_question_list_refuses_before_schema_changes(tmp_path):
    path = tmp_path / "malformed.db"
    old_content(path)
    engine = engine_for(path)
    with engine.begin() as db:
        db.execute(text("UPDATE activities SET content=:content WHERE id='practice'"), {"content": '{"question_ids":["missing"]}'})
    engine.dispose()
    result = migrate(path, check=False)
    assert result.returncode != 0 and "missing or mismatched questions" in result.stderr
    engine = engine_for(path)
    with engine.connect() as db:
        assert "material_chapters" not in inspect(db).get_table_names()
        assert "chapter_id" in {row["name"] for row in inspect(db).get_columns("materials")}
        assert db.execute(text("SELECT count(*) FROM submissions")).scalar() == 1
        assert not db.execute(text("PRAGMA foreign_key_check")).all()
    engine.dispose()
