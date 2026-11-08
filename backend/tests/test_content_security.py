"""Authorization and current quest-reference regression tests."""

from app.content_relations import activity_questions
from conftest import make_material, make_question

from uuid import uuid4
import pytest
from app.auth import hash_password, issue
from app.models import Activity, Material, Question, QuestionSecret, User
from app.quests import validate_definition


def new_teacher(db):
    user = User(student_id="19434617522190", name="Teacher B", role="teacher", password_hash=hash_password("teacher-b-password"))
    db.add(user)
    db.commit()
    return {"id": user.id, "headers": {"Authorization": "Bearer " + issue(db, user)["access_token"]}}


def test_teacher_cannot_read_other_owner_draft_assessment_or_metadata(client, db, teacher, classroom):
    other = new_teacher(db)
    other_class = client.post("/api/classrooms", json={"name": "Teacher B private class"}, headers=other["headers"]).json()
    assessment = Activity(
        id="teacher-a-unpublished-exam",
        chapter_id=179,
        title="PRIVATE_DRAFT_TITLE",
        kind="assessment",
        owner_id=teacher["id"],
        material_id=None,
        content={"published": False, "question_ids": ["teacher-a-private-question"]},
    )
    db.add(assessment)
    db.flush()
    db.add(
        make_question(db,
            id="teacher-a-private-question",
            chapter_ids=[179],
            activity_id=assessment.id,
            title="PRIVATE_QUESTION",
            kind="boolean",
            prompt="PRIVATE_UNPUBLISHED_EXAM_TEXT",
            options=[],
            samples=[],
        )
    )
    db.commit()
    # Class B belongs to B, but that does not grant preview of A's draft.
    blocked = client.get(f"/api/activities/{assessment.id}?classroom_id={other_class['id']}", headers=other["headers"])
    assert blocked.status_code == 403
    assert "PRIVATE_UNPUBLISHED_EXAM_TEXT" not in blocked.text
    # Joining A's class grants normal member access, never A's draft-preview privileges.
    assert client.post("/api/classrooms/join", json={"join_code": classroom["join_code"]}, headers=other["headers"]).status_code == 200
    assert client.get(f"/api/activities/{assessment.id}?classroom_id={classroom['id']}", headers=other["headers"]).status_code == 403
    own_preview = client.get(f"/api/activities/{assessment.id}?classroom_id={classroom['id']}", headers=teacher["headers"])
    assert own_preview.status_code == 200 and "PRIVATE_UNPUBLISHED_EXAM_TEXT" in own_preview.text
    listing = client.get("/api/teacher/courses", headers=other["headers"])
    assert listing.status_code == 200 and "PRIVATE_DRAFT_TITLE" not in listing.text
    assert client.get(f"/api/activities/ch1-lesson?classroom_id={other_class['id']}", headers=other["headers"]).status_code == 200


def test_enrolled_external_teacher_reads_bound_current_material_only(client, db, teacher, classroom):
    other = new_teacher(db)
    client.post("/api/classrooms/join", json={"join_code": classroom["join_code"]}, headers=other["headers"])
    material = make_material(db, id=9200, chapter_ids=[179], title="Current material", kind="text", owner_id=teacher["id"], status="ready",
                        content={"pages": [{"id": 9201, "page_no": 1, "title": "page", "text": "PUBLISHED_CURRENT_MATERIAL"}]})
    db.add(material)
    db.flush()
    activity = Activity(id="a-published-reading", chapter_id=179, title="A published lesson", kind="lesson", owner_id=teacher["id"],
                        material_id=material.id, content={"published": True, "question_ids": []})
    db.add(activity)
    db.commit()
    cid, headers = classroom["id"], other["headers"]
    published = client.get(f"/api/materials/{material.id}?classroom_id={cid}", headers=headers)
    assert published.status_code == 200 and "PUBLISHED_CURRENT_MATERIAL" in published.text
    material.content = {"pages": [{"id": 9201, "page_no": 1, "title": "page", "text": "UPDATED_CURRENT_MATERIAL"}]}
    db.commit()
    updated = client.get(f"/api/materials/{material.id}?classroom_id={cid}", headers=headers)
    assert updated.status_code == 200 and "UPDATED_CURRENT_MATERIAL" in updated.text
    activity.content = {"published": False, "question_ids": []}
    db.commit()
    assert client.get(f"/api/materials/{material.id}?classroom_id={cid}", headers=headers).status_code == 403
    assert client.get(f"/api/materials/{material.id}?classroom_id={cid}", headers=teacher["headers"]).status_code == 200


def lesson_definition():
    return {"id": "reference-check", "title": "Reference check", "chapter_id": 179,
        "nodes": [{"id": "read", "type": "STUDY_LESSON", "title": "Read", "activity_id": "ch1-lesson", "dependencies": []}], "effects": []}


@pytest.mark.parametrize("field", ["version", "activity_version"])
def test_removed_version_fields_are_rejected(client, teacher, field):
    definition = lesson_definition()
    target = definition if field == "version" else definition["nodes"][0]
    target[field] = 1
    response = client.post("/api/teacher/quests/validate", json={"definition": definition}, headers=teacher["headers"])
    assert response.status_code == 422, response.text


def test_current_activity_kind_and_publication_are_authoritative(client, db, teacher):
    activity = db.get(Activity, "ch1-lesson")
    def validate():
        return client.post("/api/teacher/quests/validate", json={"definition": lesson_definition()}, headers=teacher["headers"])
    assert validate().status_code == 200
    activity.kind = "practice"
    db.commit()
    assert validate().status_code == 422
    activity.kind = "lesson"
    activity.content = {**activity.content, "published": False}
    db.commit()
    assert validate().status_code == 422


def test_retained_quest_progress_does_not_expose_new_private_owner_configuration(client, db, teacher, student, classroom):
    from app.models import PlayerQuest, QuestDefinition, StoryDefinition, StorySession

    other = new_teacher(db)
    definition = QuestDefinition(id="retained-private-quest", chapter_id=179, owner_id=other["id"], title="PRIVATE_CURRENT_QUEST",
        definition={"id": "retained-private-quest", "chapter_id": 179, "title": "PRIVATE_CURRENT_QUEST", "nodes": [
            {"id": "talk", "title": "PRIVATE_CURRENT_NODE", "type": "TALK_TO_NPC", "target": "mayor_py", "dependencies": []}
        ], "effects": []})
    db.add(definition)
    db.flush()
    progress = PlayerQuest(user_id=student["id"], classroom_id=classroom["id"], quest_id=definition.id)
    db.add(progress)
    db.add(StoryDefinition(id="retained-private-story", chapter_id=179, quest_id=definition.id, content={
        "entries": {"mayor_py": [{"when": {"always": True}, "node": "private-dialogue"}]},
        "bindings": {}, "nodes": [{"id": "private-dialogue", "type": "dialogue", "text": "PRIVATE_CURRENT_DIALOGUE"}]
    }))
    db.flush()
    session = StorySession(user_id=student["id"], classroom_id=classroom["id"], story_id="retained-private-story", npc_key="mayor_py",
                           node_id="private-dialogue", status="active")
    db.add(session)
    db.commit()

    world = client.get(f"/api/world?classroom_id={classroom['id']}", headers=student["headers"])
    assert world.status_code == 200 and "PRIVATE_CURRENT" not in world.text
    blocked = client.post("/api/events", json={"classroom_id": classroom["id"], "event_id": str(uuid4()),
        "quest_id": definition.id, "node_id": "talk", "evidence": {"npc_key": "mayor_py"}}, headers=student["headers"])
    assert blocked.status_code == 403
    current = client.get(f"/api/story/current?classroom_id={classroom['id']}", headers=student["headers"])
    assert current.status_code == 403 and "PRIVATE_CURRENT" not in current.text
    interaction = client.post("/api/story/interactions", json={"classroom_id": classroom["id"], "npc_key": "mayor_py"},
                              headers=student["headers"])
    assert interaction.status_code == 200 and "PRIVATE_CURRENT" not in interaction.text
    db.expire_all()
    assert db.get(StorySession, session.id).status == "ended"
    assert db.get(PlayerQuest, progress.id).status == "active"


def test_current_reading_requirements_use_preserved_visited_pages_not_old_completed_flag(client, db, student, classroom):
    from fastapi import HTTPException
    from app.models import ActivityProgress, LearningRecord, PlayerQuest
    from app.quests import validate_evidence

    activity = db.get(Activity, "ch1-lesson")
    visited = list(activity.content["completion"]["required_page_ids"])
    progress = ActivityProgress(user_id=student["id"], classroom_id=classroom["id"], activity_id=activity.id,
                                page_no=43, visited_pages=visited, completed=True)
    record = LearningRecord(user_id=student["id"], classroom_id=classroom["id"], activity_id=activity.id,
                            data={"page_no": 43, "page_id": visited[-1]})
    task = PlayerQuest(user_id=student["id"], classroom_id=classroom["id"], quest_id="arrival", status="complete")
    db.add_all([progress, record, task])
    db.commit()
    activity.content = {**activity.content, "completion": {"required_page_ids": [*visited, 10725]}}
    db.commit()

    response = client.get(f"/api/activities/{activity.id}/progress?classroom_id={classroom['id']}", headers=student["headers"])
    assert response.status_code == 200 and not response.json()["completed"]
    assert response.json()["visited_pages"] == visited
    with pytest.raises(HTTPException) as error:
        validate_evidence(db, db.get(User, student["id"]), classroom["id"],
                          {"type": "STUDY_LESSON", "activity_id": activity.id}, {"record_id": record.id})
    assert error.value.status_code == 422
    page = next(page for page in db.get(Material, activity.material_id).content["pages"] if page["id"] == 10725)
    saved = client.post(f"/api/activities/{activity.id}/progress", json={"classroom_id": classroom["id"], "page_no": page["page_no"]},
                        headers=student["headers"])
    assert saved.status_code == 200 and saved.json()["completed"]
    validate_evidence(db, db.get(User, student["id"]), classroom["id"],
                      {"type": "STUDY_LESSON", "activity_id": activity.id}, {"record_id": record.id})
    assert db.get(PlayerQuest, task.id).status == "complete" and db.get(LearningRecord, record.id) is not None


def test_all_merge_keeps_required_practice_after_chosen_branch(client, db, teacher, student, classroom, branching_course):
    cid = classroom["id"]
    headers = student["headers"]
    definition = {
        "id": "required-merge",
        "chapter_id": 179,
        "title": "A required practice after a branch",
        "nodes": [
            {
                "id": "choose",
                "type": "PLAY_DIALOGUE",
                "title": "Choose",
                "target": "professor",
                "dialogue_id": "language",
                "choices": [{"id": "concept", "label": "Concept"}, {"id": "code", "label": "Code"}],
                "dependencies": []},
            {
                "id": "a",
                "type": "TALK_TO_NPC",
                "title": "Selected path",
                "target": "mayor_py",
                "dependencies": ["choose"],
                "condition": {"node": "choose", "choice": "concept"}},
            {
                "id": "b",
                "type": "TALK_TO_NPC",
                "title": "Other path",
                "target": "professor",
                "dependencies": ["choose"],
                "condition": {"node": "choose", "choice": "code"}},
            {
                "id": "required-practice",
                "type": "COMPLETE_PRACTICE",
                "title": "Required practice",
                "activity_id": "ch1-practice",
                "dependencies": ["a", "b"],
                "aggregation": "ALL"},
        ],
        "effects": [{"type": "set_world_flag", "key": "required_merge_passed"}]}
    assert validate_definition(definition, db) == definition
    assert client.post("/api/teacher/quests", json={"definition": definition}, headers=teacher["headers"]).status_code == 201

    def position(x, y):
        response = client.put(
            "/api/world/position", json={"classroom_id": cid, "map_key": "town_hub", "tile_x": x, "tile_y": y, "direction": "up"}, headers=headers
        )
        assert response.status_code == 200, response.text

    def event(node, evidence):
        return client.post(
            "/api/events",
            json={"classroom_id": cid, "event_id": str(uuid4()), "quest_id": definition["id"], "node_id": node, "evidence": evidence},
            headers=headers,
        )

    position(25, 14)
    for node_id, choice in (("language", "concept"), ("concept-path", None)):
        response = client.post("/api/dialogues/professor/steps", json={"classroom_id": cid, "node_id": node_id, "choice": choice}, headers=headers)
        assert response.status_code == 200, response.text
    assert event("choose", {"npc_key": "professor", "dialogue_id": "language", "choice": "concept"}).status_code == 200
    position(22, 34)
    chosen = event("a", {"npc_key": "mayor_py"})
    assert chosen.status_code == 200, chosen.text
    world = chosen.json()["world"]
    quest = next(q for q in world["quests"] if q["id"] == definition["id"])
    statuses = {n["id"]: n["status"] for n in quest["nodes"]}
    assert statuses["b"] == "skipped" and statuses["required-practice"] == "ready"
    assert quest["status"] == "active" and "required_merge_passed" not in world["flags"]
    assert event("required-practice", {}).status_code == 422
    for q in activity_questions(db, "ch1-practice"):
        answer = db.get(QuestionSecret, q.id).answer
        if q.kind == "fill":
            answer = answer[0]
        result = client.post(
            "/api/submissions",
            json={
                "classroom_id": cid,
                "activity_id": "ch1-practice",
                "question_id": q.id,
                "idempotency_key": str(uuid4()),
                "mode": "submit",
                "answer": answer},
            headers=headers,
        )
        assert result.status_code == 201 and result.json()["status"] == "accepted", result.text
    final = event("required-practice", {})
    assert final.status_code == 200, final.text
    world = final.json()["world"]
    assert next(q for q in world["quests"] if q["id"] == definition["id"])["status"] == "complete"
    assert "required_merge_passed" in world["flags"]


@pytest.mark.parametrize(
    "samples,hidden_cases",
    [
        ([{"input": "x", "output": "x"}] * 19, [{"input": "x", "expected_output": "x"}] * 2),
        ([{"input": "x"}], [{"input": "x", "expected_output": "x"}]),
        ([{"input": 42, "output": "x"}], [{"input": "x", "expected_output": "x"}]),
        ([{"input": "x", "output": "x", "callback_url": "http://invalid.test"}], [{"input": "x", "expected_output": "x"}]),
        ([{"input": "x", "output": "x"}], [{"input": {"nested": "invalid"}, "expected_output": "x"}]),
        ([{"input": "x", "output": "x" * 10001}], [{"input": "x", "expected_output": "x"}]),
    ],
)
def test_teacher_cannot_publish_unjudgeable_cases(client, db, teacher, samples, hidden_cases):
    response = client.post(
        "/api/teacher/questions",
        json={
            "id": "invalid-cases",
            "chapter_ids": [179], "status": "ready",
            "title": "Invalid cases",
            "kind": "python",
            "prompt": "Test fixture",
            "samples": samples,
            "hidden_cases": hidden_cases},
        headers=teacher["headers"],
    )
    assert response.status_code == 422, response.text
    assert db.get(Question, "invalid-cases") is None


def test_twenty_bounded_cases_publish_and_formally_submit_without_runtime_config_error(client, db, teacher, student, classroom):
    activity_id = "twenty-cases-practice"
    response = client.post(
        "/api/teacher/activities",
        json={"id": activity_id, "chapter_id": 179, "title": "Bounded cases", "kind": "practice", "npc_key": "mayor_py", "assistant_allowed": False},
        headers=teacher["headers"],
    )
    assert response.status_code == 201, response.text
    response = client.post(
        "/api/teacher/questions",
        json={
            "id": "twenty-cases",
            "chapter_ids": [179], "status": "ready",
            "title": "Twenty cases",
            "kind": "python",
            "prompt": "Explicit test fixture, not course content",
            "samples": [{"input": "x", "output": "x"}] * 19,
            "hidden_cases": [{"input": "private", "expected_output": "private"}]},
        headers=teacher["headers"],
    )
    assert response.status_code == 201, response.text
    assert "activity_version" not in response.json()
    assert client.put(f"/api/teacher/activities/{activity_id}/questions", json={"question_ids": ["twenty-cases"]}, headers=teacher["headers"]).status_code == 200
    response = client.put(
        f"/api/teacher/classrooms/{classroom['id']}/releases/179", json={"status": "published"}, headers=teacher["headers"]
    )
    assert response.status_code == 200
    response = client.post(
        "/api/submissions",
        json={
            "classroom_id": classroom["id"],
            "activity_id": activity_id,
            "question_id": "twenty-cases",
            "idempotency_key": str(uuid4()),
            "mode": "submit",
            "code": "print(input())"},
        headers=student["headers"],
    )
    assert response.status_code == 201, response.text
    assert response.json()["status"] == "prepared"
    from app.integration_models import SubmissionPayload

    payload = db.get(SubmissionPayload, response.json()["id"])
    assert len(payload.cases) == 20 and payload.public_count == 19
