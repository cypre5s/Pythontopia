from app.content_relations import activity_questions
from conftest import make_material
from datetime import datetime, timedelta, timezone
from uuid import uuid4
from types import SimpleNamespace
import pytest
from sqlalchemy import func, select
from app.models import Question, QuestionSecret
from app.quests import node_statuses, validate_definition
from app.seed import seed
from conftest import move_player


def test_seed_idempotent(db):
    counts = seed(db)
    assert counts == {"documents": 7, "pages": 561, "chapters": 7, "knowledge_points": 65, "examples": 46, "problems": 39}
    assert db.scalar(select(func.count()).select_from(Question)) == 6


def test_answers_private(client, db, student, classroom):
    payload = client.get(f"/api/activities/ch1-practice?classroom_id={classroom['id']}", headers=student["headers"]).json()
    assert len(payload["questions"]) == 3
    assert all(not {"answer", "hidden_cases", "reference_solution_code"} & q.keys() for q in payload["questions"])
    assert client.get(f"/api/activities/archive-477?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 404


def test_registration_cannot_select_role_and_session_revoke(client):
    data = {"student_id": "23258500098500", "name": "新同学", "password": "test-password-123"}
    assert client.post("/api/auth/register", json={**data, "role": "teacher"}).status_code == 422
    result = client.post("/api/auth/register", json=data)
    assert result.status_code == 201
    assert result.json()["user"]["role"] == "student"
    headers = {"Authorization": "Bearer " + result.json()["access_token"]}
    assert client.get("/api/auth/me", headers=headers).status_code == 200
    assert client.post("/api/auth/logout", headers=headers).status_code == 200
    assert client.get("/api/auth/me", headers=headers).status_code == 401


def test_release_server_time_and_cross_classroom(client, db, student, teacher, classroom, branching_course):
    cid = classroom["id"]
    future = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
    path = f"/api/teacher/classrooms/{cid}/releases/180"
    assert client.put(path, json={"status": "published", "opens_at": future}, headers=teacher["headers"]).status_code == 200
    assert client.get(f"/api/activities/ch2-lesson?classroom_id={cid}", headers=student["headers"]).status_code == 403
    assert client.get(f"/api/materials/187?classroom_id={cid}", headers=student["headers"]).status_code == 403
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
        "chapter_id": 179,
        "nodes": [
            {"id": "a", "type": "TALK_TO_NPC", "title": "a", "target": "mayor_py", "dependencies": ["b"]},
            {"id": "b", "type": "TALK_TO_NPC", "title": "b", "target": "mayor_py", "dependencies": ["a"]},
        ],
        "effects": []}
    with pytest.raises(ValueError, match="循环"):
        validate_definition(definition)
    definition["nodes"][0]["dependencies"] = []
    definition["nodes"][1]["condition"] = {"node": "a", "choice": "nonexistent"}
    with pytest.raises(ValueError, match="选项"):
        validate_definition(definition)


def test_branch_any_then_parallel_all(branching_course):
    definition = branching_course
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
    from app.models import Activity, Npc

    headers, cid = student["headers"], classroom["id"]
    world = client.get(f"/api/world?classroom_id={cid}", headers=headers).json()
    assert next(q for q in world["quests"] if q["id"] == "arrival")["status"] == "active"
    replay_checked = False
    for npc_key in ("mayor_py", "librarian_doc", "arena_guard", "mayor_py"):
        npc = db.get(Npc, npc_key)
        move_player(client, headers, cid, npc.map_key, npc.tile_x, npc.tile_y)
        result = client.post("/api/story/interactions", json={"classroom_id": cid, "npc_key": npc_key}, headers=headers)
        assert result.status_code == 200, result.text
        view = result.json()
        for _ in range(30):
            if view["status"] == "ended":
                break
            endpoint = "advance"
            body = {"classroom_id": cid, "revision": view["revision"], "event_id": str(uuid4())}
            if view["status"] == "waiting":
                activity = db.get(Activity, view["activity"]["id"])
                if activity.kind in {"lesson", "reading"}:
                    payload = client.get(f"/api/activities/{activity.id}?classroom_id={cid}", headers=headers).json()
                    for page in payload["material"]["pages"]:
                        if page["id"] in activity.content["completion"]["required_page_ids"]:
                            response = client.post(f"/api/activities/{activity.id}/progress", json={"classroom_id": cid, "page_no": page["page_no"]}, headers=headers)
                            assert response.status_code == 200, response.text
                else:
                    paper_answers = []
                    for question in activity_questions(db, activity):
                        if question.id not in [q.id for q in activity_questions(db, activity)]:
                            continue
                        answer = db.get(QuestionSecret, question.id).answer
                        if question.kind == "fill" and isinstance(answer, list):
                            answer = answer[0]
                        if activity.kind == "assessment":
                            paper_answers.append({"question_id": question.id, "answer": answer})
                            continue
                        response = client.post("/api/submissions", json={"classroom_id": cid, "activity_id": activity.id, "question_id": question.id,
                            "idempotency_key": str(uuid4()), "mode": "submit", "answer": answer}, headers=headers)
                        assert response.status_code == 201 and response.json()["status"] == "accepted", response.text
                    if activity.kind == "assessment":
                        response = client.post(f"/api/activities/{activity.id}/assessment/submit", json={
                            "classroom_id": cid, "idempotency_key": str(uuid4()), "answers": paper_answers}, headers=headers)
                        assert response.status_code == 201, response.text
                        paper = response.json()
                        assert paper["status"] == "graded" and paper["locked"] and paper["completed"] and paper["passed"]
                        assert len(paper["results"]) == len(paper_answers)
                        assert all(result["status"] == "accepted" for result in paper["results"])
                endpoint = "return"
            elif view["node"].get("choices"):
                body["choice"] = view["node"]["choices"][0]["id"]
            path = f"/api/story/sessions/{view['id']}/{endpoint}"
            response = client.post(path, json=body, headers=headers)
            assert response.status_code == 200, response.text
            if not replay_checked:
                replay = client.post(path, json=body, headers=headers)
                assert replay.status_code == 200 and replay.json()["revision"] == response.json()["revision"]
                replay_checked = True
            view = response.json()
        else:
            raise AssertionError("Story failed to reach an end")
    final = client.get(f"/api/world?classroom_id={cid}", headers=headers).json()
    assert "library" in final["building_unlock"]
    assert next(q for q in final["quests"] if q["id"] == "arrival")["status"] == "complete"
    progress = client.get(f"/api/teacher/classrooms/{cid}/progress", headers=teacher["headers"]).json()
    assert next(member for member in progress["members"] if member["id"] == student["id"])["completed_chapters"] == 1
    from app.integration_models import AssistantCall
    assert db.scalar(select(func.count()).select_from(AssistantCall)) == 0


def test_unbound_material_is_private(client, db, student, teacher, classroom):

    cid, headers = classroom["id"], student["headers"]
    db.add(make_material(db, id=9000, chapter_ids=[179], title="Unbound material", kind="text", owner_id=teacher["id"],
                    status="ready", content={"pages": [{"id": 9000, "page_no": 1, "title": "private", "text": "unpublished"}]}))
    db.commit()
    assert client.get(f"/api/materials/9000?classroom_id={cid}", headers=headers).status_code == 403
    assert client.get(f"/api/materials/186?classroom_id={cid}", headers=headers).status_code == 200
    library = client.get(f"/api/library?classroom_id={cid}", headers=headers).json()
    assert all(item["id"] != 9000 for item in library)
    assert "version" not in next(item for item in library if item["id"] == 186)


def test_activity_edits_update_current_configuration_and_keep_progress(client, db, student, teacher, classroom):
    from app.models import Activity, ActivityProgress, PlayerQuest, QuestDefinition

    cid = classroom["id"]
    activity = Activity(id="teacher-lesson", chapter_id=179, title="Initial title", kind="lesson", material_id=186,
                        npc_key="mayor_py", owner_id=teacher["id"], assistant_allowed=True, content={"published": True})
    db.add(activity)
    db.flush()
    definition = {"id": "current-quest", "title": "Current", "chapter_id": 179,
        "nodes": [{"id": "study", "type": "STUDY_LESSON", "title": "read", "activity_id": activity.id, "dependencies": []}], "effects": []}
    db.add(QuestDefinition(id=definition["id"], chapter_id=179, title="Current", owner_id=teacher["id"], definition=definition))
    db.flush()
    db.add(PlayerQuest(user_id=student["id"], classroom_id=cid, quest_id=definition["id"]))
    db.add(ActivityProgress(user_id=student["id"], classroom_id=cid, activity_id=activity.id, page_no=42, visited_pages=[10723], completed=False))
    db.commit()
    update = client.put("/api/teacher/activities/teacher-lesson", json={"title": "Updated title", "assistant_allowed": False}, headers=teacher["headers"], params={"classroom_id": classroom["id"]})
    assert update.status_code == 200, update.text
    current = client.get(f"/api/activities/teacher-lesson?classroom_id={cid}", headers=student["headers"])
    assert current.status_code == 200, current.text
    assert current.json()["title"] == "Updated title" and current.json()["assistant_allowed"] is False
    assert "version" not in current.json()
    assert current.json()["progress"]["page_no"] == 42
    record = client.post("/api/activities/teacher-lesson/participation", json={"classroom_id": cid, "page_no": 42}, headers=student["headers"]).json()["record_id"]
    result = client.post("/api/events", json={"classroom_id": cid, "event_id": str(uuid4()), "quest_id": definition["id"], "node_id": "study", "evidence": {"record_id": record}}, headers=student["headers"])
    assert result.status_code == 200, result.text


def test_second_chapter_different_branch_and_parallel_loop(client, db, student, teacher, classroom, branching_course):
    test_first_chapter_full_loop_and_idempotency(client, db, student, teacher, classroom)
    headers = student["headers"]
    cid = classroom["id"]

    def position(x, y, map_key="town_hub"):
        move_player(client, headers, cid, map_key, x, y)

    def event(node, evidence=None):
        result = client.post(
            "/api/events",
            json={"classroom_id": cid, "event_id": str(uuid4()), "quest_id": "test-branching", "node_id": node, "evidence": evidence or {}},
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
    for q in activity_questions(db, "ch2-concept"):
        answer = db.get(QuestionSecret, q.id).answer
        if q.kind == "fill":
            answer = answer[0]
        result = client.post(
            "/api/submissions",
            json={
                "classroom_id": cid,
                "activity_id": "ch2-concept",
                "question_id": q.id,
                "idempotency_key": str(uuid4()),
                "mode": "submit",
                "answer": answer},
            headers=headers,
        )
        assert result.status_code == 201 and result.json()["status"] == "accepted", result.text
    world = event("concept-practice")
    quest = next(q for q in world["quests"] if q["id"] == "test-branching")
    statuses = {n["id"]: n["status"] for n in quest["nodes"]}
    assert statuses["io-study"] == statuses["code-practice"] == "skipped"
    assert statuses["meet-librarian"] == statuses["read-shelf"] == "ready"
    position(9, 5, "library")
    world = event("meet-librarian", {"npc_key": "librarian_doc"})
    assert next(n for q in world["quests"] if q["id"] == "test-branching" for n in q["nodes"] if n["id"] == "report-language")["status"] == "locked"
    position(4, 4, "library")
    event("read-shelf", {"object_id": "library_shelf"})
    position(22, 34)
    world = event("report-language", {"npc_key": "mayor_py"})
    assert "language_archive_restored" in world["flags"]
    assert next(q for q in world["quests"] if q["id"] == "test-branching")["status"] == "complete"
    progress = client.get(f"/api/teacher/classrooms/{cid}/progress", headers=teacher["headers"]).json()
    assert next(member for member in progress["members"] if member["id"] == student["id"])["completed_chapters"] == 2
    from app.integration_models import AssistantCall

    assert db.scalar(select(func.count()).select_from(AssistantCall)) == 0


def test_dialogue_schema_and_sequence_and_teacher_npc_scope(client, db, student, teacher, classroom, branching_course):
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
    assert client.post("/api/teacher/npcs", json=payload, headers=teacher["headers"], params={"classroom_id": classroom["id"]}).status_code == 201
    from app.integration_models import AssistantCall

    assert db.scalar(select(func.count()).select_from(AssistantCall)) == 0
