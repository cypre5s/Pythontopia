"""Authorization and immutable quest-reference regression tests."""

from copy import deepcopy
from uuid import uuid4
import pytest
from sqlalchemy import select
from app.auth import hash_password, issue
from app.models import Activity, ActivityVersion, Material, MaterialVersion, Question, QuestionSecret, User
from app.quests import validate_definition


def new_teacher(db):
    user = User(email="other-teacher@example.test", name="Teacher B", role="teacher", password_hash=hash_password("teacher-b-password"))
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
        version=1,
        content={"published": False, "question_ids": ["teacher-a-private-question"]},
    )
    db.add(assessment)
    db.flush()
    db.add(
        Question(
            id="teacher-a-private-question",
            chapter_id=179,
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


def test_enrolled_external_teacher_can_read_published_version_but_not_unbound_material_draft(client, db, teacher, classroom):
    other = new_teacher(db)
    client.post("/api/classrooms/join", json={"join_code": classroom["join_code"]}, headers=other["headers"])
    material = Material(id=9200, chapter_id=179, title="A versioned material", kind="text", owner_id=teacher["id"])
    db.add(material)
    db.flush()
    for version, text in ((1, "PUBLISHED_VERSION_ONE"), (2, "UNPUBLISHED_VERSION_TWO")):
        db.add(
            MaterialVersion(
                material_id=material.id,
                version=version,
                status="ready",
                content={"pages": [{"id": 9200 + version, "page_no": 1, "title": "page", "text": text}]},
            )
        )
    activity = Activity(
        id="a-published-reading",
        chapter_id=179,
        title="A published lesson",
        kind="lesson",
        owner_id=teacher["id"],
        material_id=material.id,
        version=1,
        content={"published": True, "material_version": 1, "question_ids": []},
    )
    db.add(activity)
    db.flush()
    db.add(
        ActivityVersion(
            activity_id=activity.id,
            version=1,
            snapshot={"title": activity.title, "kind": "lesson", "material_id": material.id, "content": activity.content},
        )
    )
    db.commit()
    cid = classroom["id"]
    headers = other["headers"]
    assert client.get(f"/api/activities/{activity.id}?classroom_id={cid}", headers=headers).status_code == 200
    published = client.get(f"/api/materials/{material.id}/versions/1?classroom_id={cid}", headers=headers)
    assert published.status_code == 200 and "PUBLISHED_VERSION_ONE" in published.text
    assert client.get(f"/api/materials/{material.id}/versions/2?classroom_id={cid}", headers=headers).status_code == 403
    library = client.get(f"/api/library?classroom_id={cid}", headers=headers).json()
    assert next(item for item in library if item["id"] == material.id)["version"] == 1
    # The actual class owner still has draft preview.
    assert client.get(f"/api/materials/{material.id}/versions/2?classroom_id={cid}", headers=teacher["headers"]).status_code == 200


def lesson_definition(version=1):
    return {
        "id": "reference-check",
        "title": "Reference check",
        "chapter_id": 179,
        "version": 1,
        "nodes": [
            {"id": "read", "type": "STUDY_LESSON", "title": "Read", "activity_id": "ch1-lesson", "activity_version": version, "dependencies": []}
        ],
        "effects": [],
    }


@pytest.mark.parametrize("version", [-1, 0, True, 1.0, "1"])
def test_activity_version_must_be_positive_integer(client, db, teacher, version):
    body = {"definition": lesson_definition(version)}
    response = client.post("/api/teacher/quests/validate", json=body, headers=teacher["headers"])
    assert response.status_code == 422, response.text
    assert client.post("/api/teacher/quests", json=body, headers=teacher["headers"]).status_code == 422


def test_actual_snapshot_must_exist_and_its_kind_and_publication_are_authoritative(client, db, teacher):
    activity = db.get(Activity, "ch1-lesson")
    original = db.get(ActivityVersion, (activity.id, 1))
    old = deepcopy(original.snapshot)
    activity.version = 3
    activity.kind = "practice"
    activity.content = {"published": False}
    db.add(ActivityVersion(activity_id=activity.id, version=3, snapshot={"kind": "practice", "content": {"published": True}}))
    db.commit()

    def validate(version):
        return client.post("/api/teacher/quests/validate", json={"definition": lesson_definition(version)}, headers=teacher["headers"])

    # Version 2 is below current=3 but has no snapshot.
    assert validate(2).status_code == 422
    assert validate(99).status_code == 422
    # Current kind/publication must not silently change a lesson pinned to version 1.
    assert validate(1).status_code == 200
    assert validate(3).status_code == 422
    original.snapshot = {**old, "content": {**old["content"], "published": False}}
    db.commit()
    assert validate(1).status_code == 422


def test_all_merge_keeps_required_practice_after_chosen_branch(client, db, teacher, student, classroom):
    cid = classroom["id"]
    headers = student["headers"]
    definition = {
        "id": "required-merge",
        "version": 1,
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
                "dependencies": [],
            },
            {
                "id": "a",
                "type": "TALK_TO_NPC",
                "title": "Selected path",
                "target": "mayor_py",
                "dependencies": ["choose"],
                "condition": {"node": "choose", "choice": "concept"},
            },
            {
                "id": "b",
                "type": "TALK_TO_NPC",
                "title": "Other path",
                "target": "professor",
                "dependencies": ["choose"],
                "condition": {"node": "choose", "choice": "code"},
            },
            {
                "id": "required-practice",
                "type": "COMPLETE_PRACTICE",
                "title": "Required practice",
                "activity_id": "ch1-practice",
                "activity_version": 1,
                "dependencies": ["a", "b"],
                "aggregation": "ALL",
            },
        ],
        "effects": [{"type": "set_world_flag", "key": "required_merge_passed"}],
    }
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
    for q in db.scalars(select(Question).where(Question.activity_id == "ch1-practice")):
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
            "activity_id": "not-used-before-validation",
            "title": "Invalid cases",
            "kind": "python",
            "prompt": "Test fixture",
            "samples": samples,
            "hidden_cases": hidden_cases,
        },
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
            "activity_id": activity_id,
            "title": "Twenty cases",
            "kind": "python",
            "prompt": "Explicit test fixture, not course content",
            "samples": [{"input": "x", "output": "x"}] * 19,
            "hidden_cases": [{"input": "private", "expected_output": "private"}],
        },
        headers=teacher["headers"],
    )
    assert response.status_code == 201, response.text
    assert response.json()["activity_version"] == 2
    response = client.put(
        f"/api/teacher/classrooms/{classroom['id']}/releases/179", json={"status": "published", "version": 2}, headers=teacher["headers"]
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
            "code": "print(input())",
        },
        headers=student["headers"],
    )
    assert response.status_code == 201, response.text
    assert response.json()["status"] == "prepared"
    from app.integration_models import SubmissionPayload

    payload = db.get(SubmissionPayload, response.json()["id"])
    assert len(payload.cases) == 20 and payload.public_count == 19
