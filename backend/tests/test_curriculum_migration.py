"""Exercise revision 2 on an existing database, including persisted grades."""

import json
import os
from datetime import datetime, timezone
from pathlib import Path
import subprocess
import sys
from uuid import uuid4
import pytest
from sqlalchemy.engine import make_url
from sqlalchemy import create_engine, MetaData, event, text
from sqlalchemy.orm import Session
from app.seed import seed

BACKEND = Path(__file__).resolve().parents[1]


def migrate(url, *arguments, check=True):
    env = {**os.environ, "REBORN_DATABASE_URL": url, "PYTHONUTF8": "1", "REBORN_ENV": "development"}
    result = subprocess.run(
        [sys.executable, "-m", "alembic", *arguments], cwd=BACKEND, env=env, capture_output=True, text=True, encoding="utf-8", timeout=45
    )
    if check:
        assert result.returncode == 0, result.stderr
    return result


def sqlite_engine(path):
    engine = create_engine("sqlite:///" + path.as_posix())

    @event.listens_for(engine, "connect")
    def foreign_keys(connection, _):
        connection.execute("PRAGMA foreign_keys=ON")

    return engine


def test_upgrade_empty_sqlite_database(tmp_path):
    path = tmp_path / "fresh.sqlite3"
    url = "sqlite:///" + path.as_posix()
    migrate(url, "upgrade", "head")
    engine = sqlite_engine(path)
    with Session(engine) as db:
        counts = seed(db)
        assert counts["chapters"] == 7 and counts["knowledge_points"] == 65
        assert db.execute(text("PRAGMA foreign_keys")).scalar() == 1
        assert not db.execute(text("PRAGMA foreign_key_check")).all()
    engine.dispose()


def populate_baseline(engine):
    metadata = MetaData()
    metadata.reflect(engine)
    tables = metadata.tables
    source = BACKEND.parent / "data/course-pack-43/json"

    def load(name):
        return json.loads((source / (name + ".json")).read_text(encoding="utf-8-sig"))

    chapters, kcs, documents, pages = [load(name) for name in ("course_chapter", "course_kc", "course_document", "course_page")]
    now = datetime.now(timezone.utc)
    with engine.begin() as connection:
        connection.execute(
            tables["users"].insert(),
            [
                {
                    "id": 1,
                    "email": "teacher@migration.test",
                    "name": "Migration teacher",
                    "role": "teacher",
                    "password_hash": "unused-migration-fixture",
                },
                {
                    "id": 2,
                    "email": "student@migration.test",
                    "name": "Migration student",
                    "role": "student",
                    "password_hash": "unused-migration-fixture",
                },
            ],
        )
        connection.execute(
            tables["chapters"].insert(), [{"id": c["id"], "title": c["title"], "order_index": c["order_index"], "course_id": 43} for c in chapters]
        )
        connection.execute(
            tables["knowledge_points"].insert(), [{"id": k["id"], "chapter_id": k["chapter_id"], "title": k["kc_name"], "content": k} for k in kcs]
        )
        for doc in documents:
            chapter = next(c for c in chapters if c["order_index"] == doc["sort_order"])
            connection.execute(
                tables["materials"].insert(),
                {"id": doc["id"], "chapter_id": chapter["id"], "title": chapter["title"], "kind": "text", "owner_id": None},
            )
            connection.execute(
                tables["material_versions"].insert(),
                {
                    "id": doc["id"],
                    "material_id": doc["id"],
                    "version": 1,
                    "content": {
                        "pages": [
                            {"id": p["id"], "page_no": p["page_no"], "title": p["title"], "text": p["content_markdown"]}
                            for p in pages
                            if p["document_id"] == doc["id"]
                        ]
                    },
                    "storage_key": None,
                    "status": "ready",
                    "error": None,
                    "created_at": now,
                },
            )
        connection.execute(tables["classrooms"].insert(), {"id": 1, "name": "Existing class", "teacher_id": 1, "join_code": "migration-code"})
        connection.execute(tables["memberships"].insert(), {"user_id": 2, "classroom_id": 1})
        connection.execute(
            tables["activities"].insert(),
            [
                {
                    "id": "historic-lesson",
                    "chapter_id": 179,
                    "title": "Original lesson",
                    "kind": "lesson",
                    "material_id": 186,
                    "version": 1,
                    "owner_id": 1,
                    "assistant_allowed": False,
                    "npc_key": "mayor_py",
                    "content": {"material_version": 1, "published": True},
                },
                {
                    "id": "historic-practice",
                    "chapter_id": 179,
                    "title": "Original practice",
                    "kind": "practice",
                    "material_id": None,
                    "version": 1,
                    "owner_id": 1,
                    "assistant_allowed": False,
                    "npc_key": "mayor_py",
                    "content": {"published": True},
                },
            ],
        )
        connection.execute(
            tables["activity_versions"].insert(),
            {
                "activity_id": "historic-lesson",
                "version": 1,
                "snapshot": {"kind": "lesson", "material_id": 186, "content": {"material_version": 1, "published": True}},
            },
        )
        connection.execute(
            tables["questions"].insert(),
            {
                "id": "historic-question",
                "chapter_id": 179,
                "activity_id": "historic-practice",
                "version": 1,
                "title": "Stored concept question",
                "kind": "boolean",
                "prompt": "Migration fixture: concept answer",
                "options": [],
                "samples": [],
                "source": {"kind": "migration_regression"},
            },
        )
        connection.execute(tables["question_secrets"].insert(), {"question_id": "historic-question", "answer": True, "hidden_cases": []})
        connection.execute(
            tables["submissions"].insert(),
            {
                "id": "historic-submission",
                "user_id": 2,
                "classroom_id": 1,
                "activity_id": "historic-practice",
                "question_id": "historic-question",
                "version": 1,
                "idempotency_key": "historic-request",
                "mode": "submit",
                "status": "accepted",
                "answer": True,
                "code": None,
                "judge_token": None,
                "feedback": {"detail": "answer correct"},
                "created_at": now,
                "updated_at": now,
            },
        )
        connection.execute(
            tables["learning_records"].insert(),
            {
                "id": "historic-reading",
                "user_id": 2,
                "classroom_id": 1,
                "activity_id": "historic-lesson",
                "version": 1,
                "kind": "participation",
                "data": {"page_no": 42},
                "created_at": now,
            },
        )
        connection.execute(
            tables["quest_definitions"].insert(),
            {
                "id": "historic-quest",
                "version": 1,
                "chapter_id": 179,
                "title": "Existing quest",
                "owner_id": 1,
                "definition": {
                    "nodes": [
                        {"id": "practice", "type": "COMPLETE_PRACTICE", "title": "Practice", "activity_id": "historic-practice", "dependencies": []}
                    ]
                },
            },
        )
        connection.execute(
            tables["player_quests"].insert(),
            {"id": 1, "user_id": 2, "classroom_id": 1, "quest_id": "historic-quest", "quest_version": 1, "status": "complete", "completed_at": now},
        )
        connection.execute(
            tables["node_states"].insert(),
            {"player_quest_id": 1, "node_id": "practice", "status": "complete", "choice": None, "evidence": {"submission_id": "historic-submission"}},
        )
        connection.execute(
            tables["learning_events"].insert(),
            {
                "id": 1,
                "event_id": "historic-event",
                "user_id": 2,
                "classroom_id": 1,
                "player_quest_id": 1,
                "node_id": "practice",
                "content_version": 1,
                "evidence": {"submission_id": "historic-submission"},
                "created_at": now,
            },
        )
        if connection.dialect.name == "sqlite":
            assert not connection.execute(text("PRAGMA foreign_key_check")).all()


def test_upgrade_seeded_baseline_preserves_foreign_keys_records_and_grades(tmp_path):
    path = tmp_path / "existing.sqlite3"
    url = "sqlite:///" + path.as_posix()
    migrate(url, "upgrade", "221d0f8caf6e")
    engine = sqlite_engine(path)
    populate_baseline(engine)
    engine.dispose()
    migrate(url, "upgrade", "head")
    engine = sqlite_engine(path)
    with engine.connect() as connection:
        assert connection.execute(text("PRAGMA foreign_keys")).scalar() == 1
        assert connection.execute(text("SELECT count(*) FROM chapters WHERE owner_id IS NULL")).scalar() == 7
        assert connection.execute(text("SELECT count(*) FROM knowledge_points WHERE owner_id IS NULL")).scalar() == 65
        assert connection.execute(text("SELECT status FROM submissions WHERE id='historic-submission'")).scalar() == "accepted"
        assert connection.execute(text("SELECT version FROM learning_records WHERE id='historic-reading'")).scalar() == 1
        assert connection.execute(text("SELECT status FROM player_quests WHERE id=1")).scalar() == "complete"
        assert connection.execute(text("SELECT count(*) FROM learning_events WHERE event_id='historic-event'")).scalar() == 1
        assert not connection.execute(text("PRAGMA foreign_key_check")).all()
    with engine.begin() as connection:
        connection.execute(text("UPDATE chapters SET owner_id=1 WHERE id=179"))
    refused = migrate(url, "downgrade", "221d0f8caf6e", check=False)
    assert refused.returncode != 0 and "Cannot downgrade while private curriculum exists" in refused.stderr
    with engine.begin() as connection:
        assert connection.execute(text("SELECT owner_id FROM chapters WHERE id=179")).scalar() == 1
        connection.execute(text("UPDATE chapters SET owner_id=NULL WHERE id=179"))
    engine.dispose()
    migrate(url, "downgrade", "221d0f8caf6e")
    migrate(url, "upgrade", "head")
    engine = sqlite_engine(path)
    with Session(engine) as db:
        seed(db)
        assert db.execute(text("SELECT status FROM submissions WHERE id='historic-submission'")).scalar() == "accepted"
        assert not db.execute(text("PRAGMA foreign_key_check")).all()
    engine.dispose()


def test_postgresql_upgrade_sql_is_generated_without_a_live_connection():
    result = migrate("postgresql+psycopg://offline:offline@offline.invalid/reborn_offline", "upgrade", "head", "--sql")
    assert "ALTER TABLE chapters ADD COLUMN owner_id INTEGER" in result.stdout
    assert "ALTER TABLE knowledge_points ADD COLUMN owner_id INTEGER" in result.stdout
    assert "FOREIGN KEY(owner_id) REFERENCES users (id)" in result.stdout
    assert "CREATE INDEX ix_chapters_owner_id" in result.stdout


@pytest.mark.skipif(not os.getenv("REBORN_TEST_DATABASE_URL"), reason="Requires an explicitly configured isolated Reborn PostgreSQL connection")
def test_postgresql_revision2_preserves_existing_records_in_temporary_schema():
    url = make_url(os.environ["REBORN_TEST_DATABASE_URL"])
    assert url.get_backend_name() == "postgresql" and "reborn" in url.database.lower()
    admin = create_engine(url)
    schema = "reborn_curriculum_" + uuid4().hex
    assert schema.startswith("reborn_curriculum_") and schema.replace("_", "").isalnum()
    with admin.begin() as connection:
        connection.execute(text(f'CREATE SCHEMA "{schema}"'))
    scoped = url.update_query_dict({"options": f"-csearch_path={schema}"})
    engine = create_engine(scoped)
    try:
        migrate(scoped.render_as_string(hide_password=False), "upgrade", "221d0f8caf6e")
        populate_baseline(engine)
        engine.dispose()
        migrate(scoped.render_as_string(hide_password=False), "upgrade", "head")
        with engine.connect() as connection:
            assert connection.execute(text("SELECT count(*) FROM chapters WHERE owner_id IS NULL")).scalar() == 7
            assert connection.execute(text("SELECT count(*) FROM knowledge_points WHERE owner_id IS NULL")).scalar() == 65
            assert connection.execute(text("SELECT status FROM submissions WHERE id='historic-submission'")).scalar() == "accepted"
            assert connection.execute(text("SELECT status FROM player_quests WHERE id=1")).scalar() == "complete"
            assert connection.execute(text("SELECT count(*) FROM learning_events WHERE event_id='historic-event'")).scalar() == 1
            assert (
                connection.execute(
                    text(
                        "SELECT count(*) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND conname IN ('fk_chapters_owner_id_users','fk_knowledge_points_owner_id_users') AND contype='f' AND convalidated"
                    )
                ).scalar()
                == 2
            )
        with Session(engine) as db:
            seed(db)
            assert db.execute(text("SELECT status FROM submissions WHERE id='historic-submission'")).scalar() == "accepted"
    finally:
        engine.dispose()
        with admin.begin() as connection:
            connection.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        admin.dispose()
