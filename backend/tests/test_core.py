from datetime import datetime, timedelta, timezone
from uuid import uuid4
from types import SimpleNamespace
import pytest
from sqlalchemy import func, select
from app.models import Question, QuestionSecret, LearningEvent, WorldFlag
from app.quests import node_statuses, validate_definition
from app.seed import QUESTS, seed


def test_seed_idempotent_and_answers_private(client, db, student, classroom):
    counts = seed(db)
    assert counts == {"documents": 7, "pages": 561, "chapters": 7, "knowledge_points": 65, "examples": 46, "problems": 39}
    assert db.scalar(select(func.count()).select_from(Question)) == 45
    payload = client.get(f"/api/activities/ch1-practice?classroom_id={classroom['id']}", headers=student["headers"]).json()
    assert len(payload["questions"]) == 3
    assert all(not {"answer", "hidden_cases", "reference_solution_code"} & q.keys() for q in payload["questions"])
    assert client.get(f"/api/activities/archive-477?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 403


def test_registration_cannot_select_role_and_session_revoke(client):
    data = {"email": "new@example.test", "name": "新同学", "password": "test-password-123"}
    assert client.post("/api/auth/register", json={**data, "role": "teacher"}).status_code == 422
    result = client.post("/api/auth/register", json=data)
    assert result.status_code == 201
    assert result.json()["user"]["role"] == "student"
    headers = {"Authorization": "Bearer " + result.json()["access_token"]}
    assert client.get("/api/auth/me", headers=headers).status_code == 200
    assert client.post("/api/auth/logout", headers=headers).status_code == 200
    assert client.get("/api/auth/me", headers=headers).status_code == 401


def test_release_server_time_and_cross_classroom(client, db, student, teacher, classroom):
    cid = classroom["id"]
    future = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
    path = f"/api/teacher/classrooms/{cid}/releases/180"
    assert client.put(path, json={"status": "published", "opens_at": future}, headers=teacher["headers"]).status_code == 200
    assert client.get(f"/api/activities/ch2-lesson?classroom_id={cid}", headers=student["headers"]).status_code == 403
    assert client.get(f"/api/materials/187/versions/1?classroom_id={cid}", headers=student["headers"]).status_code == 403
    other = client.post("/api/classrooms", json={"name": "另一个班"}, headers=teacher["headers"]).json()
    assert client.get(f"/api/world?classroom_id={other['id']}", headers=student["headers"]).status_code == 403
    assert (
        client.put(
            path,
            json={"status": "published", "opens_at": (datetime.now(timezone.utc) - timedelta(seconds=1)).isoformat()},
            headers=teacher["headers"],
        ).status_code
        == 200
    )
    assert client.get(f"/api/activities/ch2-lesson?classroom_id={cid}", headers=student["headers"]).status_code == 200
    assert client.put(path, json={"status": "paused"}, headers=teacher["headers"]).status_code == 200
    assert client.get(f"/api/activities/ch2-lesson?classroom_id={cid}", headers=student["headers"]).status_code == 403


def test_dag_rejects_cycle_and_invalid_branch():
    definition = {
        "id": "x",
        "title": "x",
        "version": 1,
        "chapter_id": 179,
        "nodes": [
            {"id": "a", "type": "TALK_TO_NPC", "title": "a", "target": "mayor_py", "dependencies": ["b"]},
            {"id": "b", "type": "TALK_TO_NPC", "title": "b", "target": "mayor_py", "dependencies": ["a"]},
        ],
        "effects": [],
    }
    with pytest.raises(ValueError, match="循环"):
        validate_definition(definition)
    definition["nodes"][0]["dependencies"] = []
    definition["nodes"][1]["condition"] = {"node": "a", "choice": "nonexistent"}
    with pytest.raises(ValueError, match="选项"):
        validate_definition(definition)


def test_branch_any_then_parallel_all():
    definition = QUESTS[1]
    states = {id: SimpleNamespace(choice=None) for id in ("mission", "professor", "learn-language")}
    states["choose-route"] = SimpleNamespace(choice="concept")
    statuses = node_statuses(definition, states)
    assert statuses["concept-practice"] == "ready"
    assert statuses["io-study"] == "skipped" and statuses["code-practice"] == "skipped"
    states["concept-practice"] = SimpleNamespace(choice=None)
    statuses = node_statuses(definition, states)
    assert statuses["meet-librarian"] == statuses["read-shelf"] == "ready"
    states["meet-librarian"] = SimpleNamespace(choice=None)
    assert node_statuses(definition, states)["report-language"] == "locked"
    states["read-shelf"] = SimpleNamespace(choice=None)
    assert node_statuses(definition, states)["report-language"] == "ready"


def test_first_chapter_full_loop_and_idempotency(client, db, student, teacher, classroom):
    headers = student["headers"]
    cid = classroom["id"]

    def position(x, y, map_key="town_hub"):
        result = client.put(
            "/api/world/position", json={"classroom_id": cid, "map_key": map_key, "tile_x": x, "tile_y": y, "direction": "down"}, headers=headers
        )
        assert result.status_code == 200, result.text

    def event(node, evidence=None, event_id=None, quest_id="arrival"):
        result = client.post(
            "/api/events",
            json={"classroom_id": cid, "event_id": event_id or str(uuid4()), "quest_id": quest_id, "node_id": node, "evidence": evidence or {}},
            headers=headers,
        )
        assert result.status_code == 200, result.text
        return result.json()

    client.get(f"/api/world?classroom_id={cid}", headers=headers)
    initial_progress = client.get(f"/api/teacher/classrooms/{cid}/progress", headers=teacher["headers"]).json()
    assert next(member for member in initial_progress["members"] if member["id"] == student["id"])["completed_chapters"] == 0
    assert (
        client.post(
            "/api/events",
            json={"classroom_id": cid, "event_id": str(uuid4()), "quest_id": "arrival", "node_id": "report", "evidence": {"npc_key": "mayor_py"}},
            headers=headers,
        ).status_code
        == 409
    )
    position(22, 34)
    event_id = str(uuid4())
    event("greet", {"npc_key": "mayor_py"}, event_id)
    assert event("greet", {"npc_key": "mayor_py"}, event_id)["duplicate"] is True
    for node_id in ("welcome", "welcome-story"):
        response = client.post("/api/dialogues/mayor_py/steps", json={"classroom_id": cid, "node_id": node_id}, headers=headers)
        assert response.status_code == 200, response.text
    event("opening", {"npc_key": "mayor_py", "dialogue_id": "welcome"})
    record = client.post("/api/activities/ch1-lesson/participation", json={"classroom_id": cid, "page_no": 42}, headers=headers).json()["record_id"]
    event("study", {"record_id": record})
    position(38, 24)
    event("reach-practice")
    for question in db.scalars(select(Question).where(Question.activity_id == "ch1-practice")):
        answer = db.get(QuestionSecret, question.id).answer
        if question.kind == "fill" and isinstance(answer, list):
            answer = answer[0]
        result = client.post(
            "/api/submissions",
            json={
                "classroom_id": cid,
                "activity_id": question.activity_id,
                "question_id": question.id,
                "idempotency_key": str(uuid4()),
                "answer": answer,
                "mode": "submit",
            },
            headers=headers,
        )
        assert result.status_code == 201, result.text
        assert result.json()["status"] == "accepted"
    event("practice")
    position(22, 34)
    final = event("report", {"npc_key": "mayor_py"})["world"]
    assert "library_unlocked" in final["flags"]
    assert next(q for q in final["quests"] if q["id"] == "arrival")["status"] == "complete"
    assert db.scalar(select(func.count()).select_from(LearningEvent)) == 6
    assert db.scalar(select(func.count()).select_from(WorldFlag).where(~WorldFlag.key.like("dialogue:%"), ~WorldFlag.key.like("story:%"))) == 2
    again = client.get(f"/api/world?classroom_id={cid}", headers=headers).json()
    assert again["player"]["tile_x"] == 22 and again["flags"] == final["flags"]
    progress = client.get(f"/api/teacher/classrooms/{cid}/progress", headers=teacher["headers"]).json()
    assert next(member for member in progress["members"] if member["id"] == student["id"])["completed_chapters"] == 1


def test_unbound_material_and_new_versions_are_private(client, db, student, teacher, classroom):
    from app.models import Material, MaterialVersion

    cid = classroom["id"]
    headers = student["headers"]
    db.add(Material(id=9000, chapter_id=179, title="尚未绑定的教师材料", kind="text", owner_id=teacher["id"]))
    db.flush()
    db.add(
        MaterialVersion(
            material_id=9000, version=1, status="ready", content={"pages": [{"id": 9000, "page_no": 1, "title": "private", "text": "unpublished"}]}
        )
    )
    db.add(
        MaterialVersion(
            material_id=186,
            version=2,
            status="ready",
            content={"pages": [{"id": 9999, "page_no": 1, "title": "draft", "text": "unpublished-version"}]},
        )
    )
    db.commit()
    assert client.get(f"/api/materials/9000/versions/1?classroom_id={cid}", headers=headers).status_code == 403
    assert client.get(f"/api/materials/186/versions/2?classroom_id={cid}", headers=headers).status_code == 403
    assert client.get(f"/api/materials/186/versions/1?classroom_id={cid}", headers=headers).status_code == 200
    library = client.get(f"/api/library?classroom_id={cid}", headers=headers).json()
    assert all(item["id"] != 9000 for item in library)
    assert next(item for item in library if item["id"] == 186)["version"] == 1


def test_activity_edits_preserve_running_version(client, db, student, teacher, classroom):
    from app.models import Activity, ActivityVersion, PlayerQuest, QuestDefinition

    cid = classroom["id"]
    activity = Activity(
        id="teacher-lesson",
        chapter_id=179,
        title="版本一",
        kind="lesson",
        material_id=186,
        npc_key="mayor_py",
        owner_id=teacher["id"],
        assistant_allowed=True,
        version=1,
        content={"published": True, "material_version": 1},
    )
    db.add(activity)
    db.flush()
    snapshot = {key: getattr(activity, key) for key in ("title", "kind", "material_id", "npc_key", "assistant_allowed", "content")}
    db.add(ActivityVersion(activity_id=activity.id, version=1, snapshot=snapshot))
    definition = {
        "id": "versioned-quest",
        "title": "versioned",
        "chapter_id": 179,
        "version": 1,
        "nodes": [{"id": "study", "type": "STUDY_LESSON", "title": "read", "activity_id": activity.id, "activity_version": 1, "dependencies": []}],
        "effects": [],
    }
    db.add(QuestDefinition(id=definition["id"], version=1, chapter_id=179, title="versioned", owner_id=teacher["id"], definition=definition))
    db.flush()
    db.add(PlayerQuest(user_id=student["id"], classroom_id=cid, quest_id=definition["id"], quest_version=1))
    db.commit()
    update = client.put("/api/teacher/activities/teacher-lesson", json={"title": "版本二", "assistant_allowed": False}, headers=teacher["headers"])
    assert update.status_code == 200, update.text
    assert update.json()["version"] == 2
    old = client.get(f"/api/activities/teacher-lesson?classroom_id={cid}", headers=student["headers"])
    assert old.status_code == 200, old.text
    assert old.json()["title"] == "版本一" and old.json()["version"] == 1 and old.json()["assistant_allowed"] is True
    record = client.post(
        "/api/activities/teacher-lesson/participation", json={"classroom_id": cid, "page_no": 42}, headers=student["headers"]
    ).json()["record_id"]
    result = client.post(
        "/api/events",
        json={"classroom_id": cid, "event_id": str(uuid4()), "quest_id": definition["id"], "node_id": "study", "evidence": {"record_id": record}},
        headers=student["headers"],
    )
    assert result.status_code == 200, result.text


def test_second_chapter_different_branch_and_parallel_loop(client, db, student, teacher, classroom):
    test_first_chapter_full_loop_and_idempotency(client, db, student, teacher, classroom)
    headers = student["headers"]
    cid = classroom["id"]

    def position(x, y, map_key="town_hub"):
        result = client.put(
            "/api/world/position", json={"classroom_id": cid, "map_key": map_key, "tile_x": x, "tile_y": y, "direction": "down"}, headers=headers
        )
        assert result.status_code == 200, result.text

    def event(node, evidence=None):
        result = client.post(
            "/api/events",
            json={"classroom_id": cid, "event_id": str(uuid4()), "quest_id": "lost-language", "node_id": node, "evidence": evidence or {}},
            headers=headers,
        )
        assert result.status_code == 200, result.text
        return result.json()["world"]

    event("mission", {"npc_key": "mayor_py"})
    position(25, 14)
    event("professor", {"npc_key": "professor"})
    record = client.post("/api/activities/ch2-lesson/participation", json={"classroom_id": cid, "page_no": 5}, headers=headers).json()["record_id"]
    event("learn-language", {"record_id": record})
    for node_id, choice in (("language", "concept"), ("concept-path", None)):
        response = client.post("/api/dialogues/professor/steps", json={"classroom_id": cid, "node_id": node_id, "choice": choice}, headers=headers)
        assert response.status_code == 200, response.text
    event("choose-route", {"npc_key": "professor", "dialogue_id": "language", "choice": "concept"})
    position(38, 24)
    for q in db.scalars(select(Question).where(Question.activity_id == "ch2-concept")):
        answer = db.get(QuestionSecret, q.id).answer
        if q.kind == "fill":
            answer = answer[0]
        result = client.post(
            "/api/submissions",
            json={
                "classroom_id": cid,
                "activity_id": q.activity_id,
                "question_id": q.id,
                "idempotency_key": str(uuid4()),
                "mode": "submit",
                "answer": answer,
            },
            headers=headers,
        )
        assert result.status_code == 201 and result.json()["status"] == "accepted", result.text
    world = event("concept-practice")
    quest = next(q for q in world["quests"] if q["id"] == "lost-language")
    statuses = {n["id"]: n["status"] for n in quest["nodes"]}
    assert statuses["io-study"] == statuses["code-practice"] == "skipped"
    assert statuses["meet-librarian"] == statuses["read-shelf"] == "ready"
    position(9, 5, "library")
    world = event("meet-librarian", {"npc_key": "librarian_doc"})
    assert next(n for q in world["quests"] if q["id"] == "lost-language" for n in q["nodes"] if n["id"] == "report-language")["status"] == "locked"
    position(4, 4, "library")
    event("read-shelf", {"object_id": "library_shelf"})
    position(22, 34)
    world = event("report-language", {"npc_key": "mayor_py"})
    assert "language_archive_restored" in world["flags"]
    assert next(q for q in world["quests"] if q["id"] == "lost-language")["status"] == "complete"
    progress = client.get(f"/api/teacher/classrooms/{cid}/progress", headers=teacher["headers"]).json()
    assert next(member for member in progress["members"] if member["id"] == student["id"])["completed_chapters"] == 2
    from app.integration_models import AssistantCall

    assert db.scalar(select(func.count()).select_from(AssistantCall)) == 0


def test_dialogue_schema_and_sequence_and_teacher_npc_scope(client, db, student, teacher, classroom):
    from app.dialogues import validate_dialogue

    for invalid in (
        [{"id": "a", "text": "A", "next": "b"}],
        [{"id": "a", "text": "A", "next": "a"}],
        [{"id": [], "text": "A"}],
        [{"id": "a", "text": "A", "effects": [{"type": "set_world_flag", "key": "library_unlocked"}]}],
    ):
        with pytest.raises(ValueError):
            validate_dialogue(invalid)
    cid = classroom["id"]
    headers = student["headers"]
    client.put(
        "/api/world/position", json={"classroom_id": cid, "map_key": "town_hub", "tile_x": 22, "tile_y": 34, "direction": "up"}, headers=headers
    )
    assert client.post("/api/dialogues/mayor_py/steps", json={"classroom_id": cid, "node_id": "welcome-story"}, headers=headers).status_code == 409
    for node_id in ("welcome", "welcome-story"):
        result = client.post("/api/dialogues/mayor_py/steps", json={"classroom_id": cid, "node_id": node_id}, headers=headers)
        assert result.status_code == 200, result.text
    assert result.json()["completed_root"] == "welcome"
    payload = {"key": "new-npc", "name": "新增", "map_key": "town_hub", "tile_x": 22, "tile_y": 34, "dialogue": [{"id": "start", "text": "hello"}]}
    assert client.post("/api/teacher/npcs", json=payload, headers=teacher["headers"]).status_code == 403
    from app.integration_models import AssistantCall

    assert db.scalar(select(func.count()).select_from(AssistantCall)) == 0
