"""Upgrade a populated versioned database without losing learner history."""

from datetime import datetime, timezone
import os
from uuid import uuid4

import pytest

from sqlalchemy import MetaData, create_engine, inspect, select, text
from sqlalchemy.engine import make_url

from test_curriculum_migration import migrate, populate_baseline, sqlite_engine


@pytest.fixture(params=["sqlite", "postgresql"])
def existing_database(request, tmp_path):
    if request.param == "sqlite":
        path = tmp_path / "current-content.sqlite3"
        yield "sqlite:///" + path.as_posix(), lambda: sqlite_engine(path)
        return
    if not os.getenv("REBORN_TEST_DATABASE_URL"):
        pytest.skip("Requires an explicitly configured isolated Reborn PostgreSQL connection")
    url = make_url(os.environ["REBORN_TEST_DATABASE_URL"])
    assert url.get_backend_name() == "postgresql" and "reborn" in url.database.lower()
    admin = create_engine(url)
    schema = "reborn_current_" + uuid4().hex
    with admin.begin() as connection:
        connection.execute(text(f'CREATE SCHEMA "{schema}"'))
    scoped = url.update_query_dict({"options": f"-csearch_path={schema}"})
    try:
        yield scoped.render_as_string(hide_password=False), lambda: create_engine(scoped)
    finally:
        with admin.begin() as connection:
            connection.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        admin.dispose()


def test_collapse_versions_preserves_grades_and_merges_reading_history(existing_database):
    url, engine_factory = existing_database
    migrate(url, "upgrade", "b63e1a4920d7")
    engine = engine_factory()
    populate_baseline(engine)
    metadata = MetaData()
    metadata.reflect(engine)
    t = metadata.tables
    with engine.begin() as connection:
        old = dict(connection.execute(select(t["material_versions"]).where(t["material_versions"].c.material_id == 186)).mappings().one())
        connection.execute(t["material_versions"].insert(), {**old, "id": 99999, "version": 2, "content": {"pages": [{"id": 2, "page_no": 2, "text": "current"}]}})
        quest = dict(connection.execute(select(t["quest_definitions"])).mappings().one())
        connection.execute(t["quest_definitions"].insert(), {**quest, "version": 2, "title": "Current quest", "definition": {"version": 2, "nodes": [{"id": "practice", "activity_version": 2}]}})
        connection.execute(t["npcs"].insert(), {"key": "fixture-npc", "name": "Fixture", "map_key": "town_hub", "tile_x": 1, "tile_y": 1, "portrait": "", "dialogue": []})
        for version in (1, 2):
            connection.execute(t["story_definitions"].insert(), {"id": "fixture-story", "version": version, "chapter_id": 179,
                "quest_id": "historic-quest", "quest_version": version, "content": {"schema_version": 1, "bindings": {"lesson": {"activity_id": "historic-lesson", "version": version, "goal_id": "practice"}}}})
            connection.execute(t["activity_progress"].insert(), {"user_id": 2, "classroom_id": 1, "activity_id": "historic-lesson", "version": version,
                "page_no": version, "visited_pages": [version], "completed": version == 1})
            connection.execute(t["world_flags"].insert(), {"user_id": 2, "classroom_id": 1, "key": f"dialogue:completed:fixture-npc:story:fixture-story:v{version}:hello"})
        connection.execute(t["story_sessions"].insert(), {"id": "saved-story", "user_id": 2, "classroom_id": 1, "story_id": "fixture-story", "story_version": 1,
            "npc_key": "fixture-npc", "node_id": "read", "status": "waiting", "revision": 3, "pending_activity_id": "historic-lesson", "pending_activity_version": 1,
            "return_mapping": {"completed": "done"}, "receipts": [{"response": {"activity": {"id": "historic-lesson", "version": 1}}}]})
        connection.execute(t["assistant_sessions"].insert(), {"id": "saved-assistant", "user_id": 2, "classroom_id": 1, "activity_id": "historic-lesson",
            "version": 1, "material_id": 186, "material_version": 1, "messages": [{"role": "assistant", "references": [{"material_id": 186, "version": 1, "page_no": 1}]}], "created_at": datetime.now(timezone.utc)})
    engine.dispose()
    migrate(url, "upgrade", "head")
    engine = engine_factory()
    metadata = MetaData()
    metadata.reflect(engine)
    with engine.connect() as connection:
        assert not {"material_versions", "activity_versions"} & set(inspect(connection).get_table_names())
        for table in metadata.tables.values():
            assert not {"version", "quest_version", "story_version", "material_version", "content_version", "pending_activity_version"} & set(table.c.keys())
        progress = connection.execute(select(metadata.tables["activity_progress"])).mappings().one()
        assert progress["visited_pages"] == [1, 2] and progress["page_no"] == 2 and progress["completed"]
        assert connection.execute(text("SELECT status FROM submissions WHERE id='historic-submission'")).scalar() == "accepted"
        assert connection.execute(text("SELECT count(*) FROM learning_records")).scalar() == 1
        assert connection.execute(text("SELECT count(*) FROM learning_events")).scalar() == 1
        assert connection.execute(text("SELECT status FROM player_quests WHERE id=1")).scalar() == "complete"
        assert connection.execute(text("SELECT count(*) FROM node_states")).scalar() == 1
        assert connection.execute(text("SELECT count(*) FROM world_flags")).scalar() == 1
        assert connection.execute(text("SELECT title FROM quest_definitions")).scalar() == "Current quest"
        material = connection.execute(select(metadata.tables["materials"]).where(metadata.tables["materials"].c.id == 186)).mappings().one()
        assert material["content"]["pages"][0]["text"] == "current"
        session = connection.execute(select(metadata.tables["story_sessions"])).mappings().one()
        assert session["revision"] == 3 and session["status"] == "waiting"
        assert "version" not in session["receipts"][0]["response"]["activity"]
        story = connection.execute(select(metadata.tables["story_definitions"])).mappings().one()
        assert story["content"]["schema_version"] == 1
        assert "version" not in story["content"]["bindings"]["lesson"]
        assistant = connection.execute(select(metadata.tables["assistant_sessions"])).mappings().one()
        assert assistant["messages"][0]["references"] == [{"material_id": 186, "page_no": 1}]
        if connection.dialect.name == "sqlite":
            assert not connection.execute(text("PRAGMA foreign_key_check")).all()
    refused = migrate(url, "downgrade", "b63e1a4920d7", check=False)
    # The newest identity migration also deliberately refuses destructive downgrade.
    assert refused.returncode != 0 and any(message in refused.stderr for message in (
        "Cannot reconstruct single-owner content relationships", "Cannot reconstruct removed content versions", "Email identities cannot be reconstructed",
        "Class-owned definitions cannot be merged losslessly", "Generic interaction sessions and building unlock evidence",
        "Downgrading teacher town saves requires restoring an isolated backup"))
    engine.dispose()


def test_open_story_resumes_after_checkpoint_evidence_migration(existing_database):
    from fastapi.testclient import TestClient
    from sqlalchemy.orm import Session
    from app.auth import issue
    from app.db import get_db
    from app.main import app
    from app.models import Chapter, ChapterRelease, Classroom, CourseInstance, LearningEvent, Membership, Npc, QuestDefinition, StoryDefinition, User, WorldFlag
    from app.quests import validate_definition
    from app.story import validate_story

    url, engine_factory = existing_database
    migrate(url, "upgrade", "5a3d09d4c2b1")
    engine = engine_factory()
    assert next(column for column in inspect(engine).get_columns("world_flags") if column["name"] == "key")["type"].length == 100
    try:
        with Session(engine, expire_on_commit=False) as db:
            teacher = User(student_id="81001", name="Teacher", role="teacher", password_hash="test-only")
            student = User(student_id="81002", name="Student", role="student", password_hash="test-only")
            db.add_all([teacher, student])
            db.flush()
            classroom = Classroom(name="Checkpoint migration", teacher_id=teacher.id, join_code="checkpoint-test")
            db.add(classroom)
            db.flush()
            course = CourseInstance(classroom_id=classroom.id, package_id="checkpoint-test", name="Checkpoint test", package_version="1")
            db.add(course)
            db.flush()
            chapter = Chapter(course_instance_id=course.id, stable_id="chapter", title="Chapter", order_index=1)
            npc = Npc(key="n" * 80, stable_id="librarian", course_instance_id=course.id, name="Librarian", map_key="town_hub", tile_x=22, tile_y=36)
            db.add_all([chapter, npc])
            db.flush()
            cid, sid = classroom.id, student.id
            db.add_all([Membership(classroom_id=cid, user_id=sid), ChapterRelease(classroom_id=cid, chapter_id=chapter.id, status="published"),
                        WorldFlag(classroom_id=cid, user_id=sid, key="kept-before-upgrade")])
            story_id, goal_id = "s" * 100, "g" * 100
            marker = f"story:{story_id}:{goal_id}"
            definition = {"id": "checkpoint-quest", "chapter_id": chapter.id, "title": "Librarian dialogue", "kind": "side", "effects": [],
                          "nodes": [{"id": goal_id, "type": "TALK_TO_NPC", "target": npc.key, "title": "Talk", "dependencies": []}]}
            content = {"schema_version": 1, "mode": "task", "bindings": {},
                       "entries": {npc.key: [{"when": {"always": True}, "node": "choice"}]},
                       "nodes": [{"id": "choice", "type": "dialogue", "text": "Read the next line?", "checkpoint": goal_id,
                                  "choices": [{"id": "yes", "label": "Continue", "next": "after"}, {"id": "no", "label": "Later", "next": "end"}]},
                                 {"id": "after", "type": "dialogue", "text": "The choice advanced.", "next": "end"}, {"id": "end", "type": "end"}]}
            validate_definition(definition, db)
            validate_story(content, db, definition, story_id)
            db.add(QuestDefinition(id=definition["id"], stable_id="checkpoint-quest", course_instance_id=course.id, chapter_id=chapter.id,
                                   title=definition["title"], definition=definition))
            db.add(StoryDefinition(id=story_id, stable_id="librarian-flow", course_instance_id=course.id, chapter_id=chapter.id,
                                   quest_id=definition["id"], content=content))
            db.commit()
            headers = {"Authorization": "Bearer " + issue(db, student)["access_token"]}

        def override_db():
            with Session(engine, expire_on_commit=False) as db:
                yield db

        previous = dict(app.dependency_overrides)
        app.dependency_overrides[get_db] = override_db
        try:
            client = TestClient(app, raise_server_exceptions=False)
            response = client.post("/api/story/interactions", headers=headers, json={"classroom_id": cid, "npc_key": npc.key})
            assert response.status_code == 200, response.text
            session = response.json()
            assert session["node"]["id"] == "choice"
            path = f"/api/story/sessions/{session['id']}/advance"
            choice = {"classroom_id": cid, "revision": session["revision"], "event_id": str(uuid4()), "choice": "yes"}
            if engine.dialect.name == "postgresql":
                # Reproduce the reported request on the same session before upgrading the column.
                assert client.post(path, json=choice, headers=headers).status_code == 500
            engine.dispose()
            migrate(url, "upgrade", "head")
            assert next(column for column in inspect(engine).get_columns("world_flags") if column["name"] == "key")["type"].length == 320
            resumed = client.get(f"/api/story/current?classroom_id={cid}", headers=headers)
            assert resumed.status_code == 200 and resumed.json()["revision"] == session["revision"]
            result = client.post(path, json=choice, headers=headers)
            assert result.status_code == 200, result.text
            assert result.json()["node"]["id"] == "after"
            assert client.post(path, json=choice, headers=headers).json()["revision"] == result.json()["revision"]
            with Session(engine) as db:
                assert db.get(WorldFlag, (sid, cid, "kept-before-upgrade"))
                assert db.get(WorldFlag, (sid, cid, f"dialogue:completed:{npc.key}:{marker}"))
                assert [event.evidence for event in db.scalars(select(LearningEvent))] == [{"npc_key": npc.key, "dialogue_id": marker}]
        finally:
            app.dependency_overrides.clear()
            app.dependency_overrides.update(previous)
            client.close()
    finally:
        engine.dispose()
