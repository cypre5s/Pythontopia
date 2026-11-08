import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
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
def classroom(client, teacher, student):
    result = client.post("/api/classrooms", json={"name": "测试班级"}, headers=teacher["headers"])
    assert result.status_code == 201, result.text
    classroom = result.json()
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


@pytest.fixture
def branching_course(db, teacher):
    """Test-only course for graph, scheduling and Judge contracts; never seeded."""
    import json
    from pathlib import Path
    from app.models import Activity, KnowledgePoint, Npc, QuestDefinition, Question, QuestionSecret
    from app.chapter_content import load_content
    from app.config import ROOT
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
    problems = {"course-" + str(p["id"]): p for p in json.loads((ROOT / "data/course-pack-43/json/course_problem.json").read_text())}
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
