# Test sources must be configured before application module constants are loaded.
# ruff: noqa: E402
import pytest
from source_fixtures import configure_test_sources

configure_test_sources()

from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from app.db import Base, get_db
from app.main import app
from app.models import User
from app.content_relations import (activity_questions, set_activity_questions, set_material_chapters, set_question_chapters)
from app.auth import hash_password, issue
from app.seed import seed
from app.world_config import load_world_config


def make_material(db, *, chapter_ids=None, **fields):
    """Author a test material through the same structured association as production."""
    from app.models import Material
    item = Material(**fields)
    db.add(item)
    db.flush()
    set_material_chapters(db, item.id, chapter_ids or [])
    return item


def make_question(db, *, chapter_ids, activity_id=None, **fields):
    from app.models import Question
    item = Question(status="ready", **fields)
    db.add(item)
    db.flush()
    set_question_chapters(db, item.id, chapter_ids)
    if activity_id:
        set_activity_questions(db, activity_id, [q.id for q in activity_questions(db, activity_id)] + [item.id])
    return item


def move_player(client, headers, classroom_id, map_key, tile_x, tile_y, direction="down"):
    """Use actual configured portals before a test moves within its destination map."""
    response = client.get(f"/api/world?classroom_id={classroom_id}", headers=headers)
    assert response.status_code == 200, response.text
    current = response.json()["player"]["map_key"]
    if current != map_key:
        portal = next(item for item in load_world_config()["portals"] if item["from"]["map_key"] == current and item["to"]["map_key"] == map_key)
        source = {key: portal["from"][key] for key in ("map_key", "tile_x", "tile_y")}
        response = client.put("/api/world/position", json={"classroom_id": classroom_id, **source, "direction": direction}, headers=headers)
        assert response.status_code == 200, response.text
        response = client.post("/api/world/transition", json={"classroom_id": classroom_id, "portal_id": portal["id"]}, headers=headers)
        assert response.status_code == 200, response.text
    response = client.put("/api/world/position", json={"classroom_id": classroom_id, "map_key": map_key, "tile_x": tile_x, "tile_y": tile_y,
                                                      "direction": direction}, headers=headers)
    assert response.status_code == 200, response.text
    return response


@pytest.fixture
def db():
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    with sessionmaker(engine, expire_on_commit=False)() as session:
        seed(session)
        @event.listens_for(session, "before_flush")
        def attach_test_content(session, flush_context, instances):
            """Synthetic unit-test authoring keeps stable fixture IDs in one course.

            Real import/migration tests never set this marker and retain sources.
            This does not change application authorization or HTTP request scope.
            """
            from app.course_instances import CONTENT_MODELS
            from app.models import Npc
            from uuid import uuid4
            key = session.info.get("fixture_course_instance_id")
            if key is None:
                return
            for row in session.new:
                if isinstance(row, CONTENT_MODELS) and row.course_instance_id is None:
                    row.course_instance_id = key
                    row.stable_id = row.stable_id or str(row.key if isinstance(row, Npc) else row.id or uuid4().hex)
        yield session
    Base.metadata.drop_all(engine)
    engine.dispose()


@pytest.fixture
def client(db):
    def override():
        yield db

    app.dependency_overrides[get_db] = override
    client = TestClient(app, raise_server_exceptions=True)
    yield client
    client.close()
    app.dependency_overrides.clear()


@pytest.fixture
def student(db):
    row = User(student_id="72953879102235", name="学生", role="student", password_hash=hash_password("student-password-123"))
    db.add(row)
    db.commit()
    token = issue(db, row)["access_token"]
    return {"id": row.id, "student_id": row.student_id, "headers": {"Authorization": "Bearer " + token}}


@pytest.fixture
def teacher(db):
    row = User(student_id="1748881331821", name="教师", role="teacher", password_hash=hash_password("teacher-password-123"))
    db.add(row)
    db.commit()
    token = issue(db, row)["access_token"]
    return {"id": row.id, "student_id": row.student_id, "headers": {"Authorization": "Bearer " + token}}


@pytest.fixture
def classroom(client, db, teacher, student):
    result = client.post("/api/classrooms", json={"name": "测试班级"}, headers=teacher["headers"])
    assert result.status_code == 201, result.text
    classroom = result.json()
    instance = bind_test_course(db, classroom["id"])
    db.info["fixture_course_instance_id"] = instance.id
    assert client.post("/api/classrooms/join", json={"join_code": classroom["join_code"]}, headers=student["headers"]).status_code == 200
    for chapter in (179, 180):
        assert (
            client.put(
                f"/api/teacher/classrooms/{classroom['id']}/releases/{chapter}",
                json={"status": "published"},
                headers=teacher["headers"],
            ).status_code
            == 200
        )
    return classroom


def bind_test_course(db, classroom_id):
    """Move explicitly synthetic fixture definitions into a test course namespace."""
    from app.course_instances import CONTENT_MODELS
    from app.models import CourseInstance, Npc
    from app.world_authoring import migrate_legacy_world
    instance = CourseInstance(classroom_id=classroom_id, package_id="synthetic-test", name="测试课程", package_version="1")
    db.add(instance)
    db.flush()
    for model in CONTENT_MODELS:
        for row in db.scalars(select(model).where(model.course_instance_id.is_(None))):
            row.course_instance_id = instance.id
            row.stable_id = str(row.key if isinstance(row, Npc) else row.id)
    migrate_legacy_world(db, instance)
    db.commit()
    return instance


def copy_fixture_course(db, source_classroom_id, target_classroom_id):
    """Copy test definitions through the real mapper, deliberately excluding progress."""
    from copy import deepcopy
    from app.course_instances import CONTENT_MODELS, _clone_rows, _relations, classroom_course
    from app.course_exchange_models import CoursePackageState
    from app.models import ChapterRelease, Classroom, CourseInstance
    source = classroom_course(db, source_classroom_id)
    target_class = db.get(Classroom, target_classroom_id)
    source_class = db.get(Classroom, source_classroom_id)
    instance = CourseInstance(classroom_id=target_classroom_id, package_id=source.package_id, package_version=source.package_version, name=source.name)
    instance._teacher_id = target_class.teacher_id
    db.add(instance)
    db.flush()
    sources = {model: [row for row in db.scalars(select(model).where(model.course_instance_id == source.id))
                      if not hasattr(row, "owner_id") or row.owner_id in {None, source_class.teacher_id}]
               for model in CONTENT_MODELS}
    maps = _clone_rows(db, instance, sources)
    _relations(db, maps)
    for release in db.scalars(select(ChapterRelease).where(ChapterRelease.classroom_id == source_classroom_id)):
        if release.chapter_id in maps["chapter"]:
            db.add(ChapterRelease(classroom_id=target_classroom_id, chapter_id=maps["chapter"][release.chapter_id], status=release.status,
                prerequisite_chapter_id=maps["chapter"].get(release.prerequisite_chapter_id)))
    state = db.get(CoursePackageState, source.id)
    if state:
        db.add(CoursePackageState(course_instance_id=instance.id, configuration=deepcopy(state.configuration)))
    db.commit()
    return maps


@pytest.fixture
def legacy_classroom(client, db, teacher, student):
    """Unmigrated data is only for explicit source/migration tests, never live reads."""
    from app.models import ChapterRelease
    classroom = client.post("/api/classrooms", json={"name": "Legacy test"}, headers=teacher["headers"]).json()
    client.post("/api/classrooms/join", json={"join_code": classroom["join_code"]}, headers=student["headers"])
    db.add_all([ChapterRelease(classroom_id=classroom["id"], chapter_id=key, status="published") for key in (179, 180)])
    db.commit()
    return classroom


@pytest.fixture
def branching_course(db, teacher):
    """Test-only course for graph, scheduling and Judge contracts; never seeded."""
    import json
    import os
    from pathlib import Path
    from app.models import Activity, KnowledgePoint, Npc, QuestDefinition, Question, QuestionSecret
    from app.chapter_content import load_content
    from sqlalchemy import select

    spec = json.loads((Path(__file__).parent / "fixtures/branching_course.json").read_text(encoding="utf-8"))
    for source in spec["activities"]:
        fields = {key: source[key] for key in ("id", "chapter_id", "title", "kind", "material_id", "npc_key", "assistant_allowed", "content")}
        ids = [point.id for point in db.scalars(select(KnowledgePoint).where(KnowledgePoint.chapter_id == source["chapter_id"]))
               if set(point.content.get("page_refs", [])) & set(source.get("knowledge_point_pages", []))]
        fields["content"] = {**{key: value for key, value in fields["content"].items() if key != "question_ids"}, "knowledge_point_ids": [1844] if source["id"] == "ch2-code" else ids}
        item = db.get(Activity, source["id"])
        if not item:
            item = Activity(**fields, owner_id=teacher["id"])
            db.add(item)
        else:
            for key, value in fields.items():
                setattr(item, key, value)
            item.owner_id = teacher["id"]
    db.flush()
    question_specs = {q["id"]: q for q in load_content()["questions"]}
    problems = {"course-" + str(p["id"]): p for p in json.loads((Path(os.environ["REBORN_COURSE_ROOT"]) / "json/course_problem.json").read_text())}
    for activity in spec["activities"]:
        for key in activity["content"]["question_ids"]:
            if db.get(Question, key) is None:
                if key in question_specs:
                    source = question_specs[key]
                    question = make_question(db, id=key, chapter_ids=[activity["chapter_id"]],
                        title=source["title"], kind=source["kind"], prompt=source["prompt"],
                        options=source["options"], samples=source.get("samples", []), source=source.get("source", {}))
                    secret = QuestionSecret(question_id=question.id, answer=source["answer"], hidden_cases=source.get("hidden_cases", []))
                else:
                    source = problems[key]
                    question = make_question(db, id=key, chapter_ids=[activity["chapter_id"]], title=source["title"],
                        kind="python", prompt=source["statement_markdown"], options=[], samples=source["samples"], source={"reviewed": True})
                    secret = QuestionSecret(question_id=question.id, answer=None, hidden_cases=[
                        {"input": case["input"], "expected_output": case["expected_output"]}
                        for case in source["test_cases"] if not case.get("is_sample")])
                db.add(secret)
        set_activity_questions(db, activity["id"], activity["content"]["question_ids"])
    db.get(Npc, "professor").dialogue = spec["dialogue"]
    db.get(Npc, "mayor_py").dialogue = [
        {"id": "welcome", "text": "Test start", "next": "welcome-story"},
        {"id": "welcome-story", "text": "Test terminal"},
    ]
    quest = spec["quest"]
    db.add(QuestDefinition(id=quest["id"], chapter_id=quest["chapter_id"], title=quest["title"],
                           owner_id=teacher["id"], definition=quest))
    db.commit()
    return quest
