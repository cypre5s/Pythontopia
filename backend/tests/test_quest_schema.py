from copy import deepcopy
from uuid import uuid4
import pytest
from app.quests import validate_definition
from app.models import PlayerQuest
from test_core import test_first_chapter_full_loop_and_idempotency as complete_first_chapter


def basic_definition():
    return {
        "id": "schema-check",
        "title": "Schema check",
        "chapter_id": 179,
        "nodes": [{"id": "talk", "type": "TALK_TO_NPC", "title": "Talk", "target": "mayor_py", "dependencies": []}],
        "effects": []}


def invalid_definitions():
    values = [
        None,
        [],
        {**basic_definition(), "nodes": [None]},
        {**basic_definition(), "nodes": [[]]},
        {**basic_definition(), "nodes": None},
        {**basic_definition(), "version": True},
        {**basic_definition(), "kind": "unknown"},
        {**basic_definition(), "effects": None},
    ]
    for field, value in [
        ("id", {}),
        ("title", 12),
        ("dependencies", [{}]),
        ("dependencies", None),
        ("choices", None),
        ("choices", [{"id": {}, "label": "bad"}]),
        ("choices", [{"id": "choice", "label": []}]),
        ("condition", []),
        ("condition", {}),
        ("condition", {"node": {}, "choice": "x"}),
        ("optional", "false"),
        ("optional", 1),
        ("target", None),
        ("activity_id", ""),
        ("dialogue_id", 42),
        ("aggregation", {}),
    ]:
        definition = deepcopy(basic_definition())
        definition["nodes"][0][field] = value
        values.append(definition)
    return values


@pytest.mark.parametrize("definition", invalid_definitions())
def test_invalid_task_json_is_rejected_before_publication(client, teacher, definition, classroom):
    response = client.post("/api/teacher/quests/validate", json={"definition": definition}, headers=teacher["headers"], params={"classroom_id": classroom["id"]})
    assert response.status_code == 422, response.text
    assert client.post("/api/teacher/quests", json={"definition": definition}, headers=teacher["headers"], params={"classroom_id": classroom["id"]}).status_code == 422


def test_kind_default_and_side_are_valid():
    definition = basic_definition()
    assert validate_definition(definition) == definition
    definition["kind"] = "side"
    assert validate_definition(definition) == definition


def test_unfinished_side_quest_does_not_block_next_main_chapter(client, db, teacher, student, classroom, branching_course):
    definition = {**basic_definition(), "id": "unfinished-side", "kind": "side"}
    created = client.post("/api/teacher/quests", json={"definition": definition}, headers=teacher["headers"], params={"classroom_id": classroom["id"]})
    assert created.status_code == 201
    definition["id"] = created.json()["id"]
    complete_first_chapter(client, db, student, teacher, classroom)
    response = client.put(
        f"/api/teacher/classrooms/{classroom['id']}/releases/180",
        json={"status": "published", "prerequisite_chapter_id": 179},
        headers=teacher["headers"],
    )
    assert response.status_code == 200
    side = db.query(PlayerQuest).filter_by(user_id=student["id"], classroom_id=classroom["id"], quest_id=definition["id"]).one()
    assert side.status == "active"
    assert client.get(f"/api/activities/ch2-lesson?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 200


def test_chapter_with_only_side_quests_does_not_automatically_satisfy_prerequisite(client, db, teacher, student, classroom, branching_course):
    chapter = client.post("/api/teacher/chapters", json={"title": "Only side activities"}, headers=teacher["headers"], params={"classroom_id": classroom["id"]}).json()
    definition = {**basic_definition(), "id": "only-side", "kind": "side", "chapter_id": chapter["id"]}
    created = client.post("/api/teacher/quests", json={"definition": definition}, headers=teacher["headers"], params={"classroom_id": classroom["id"]})
    assert created.status_code == 201
    definition["id"] = created.json()["id"]
    assert (
        client.put(
            f"/api/teacher/classrooms/{classroom['id']}/releases/{chapter['id']}", json={"status": "published"}, headers=teacher["headers"]
        ).status_code
        == 200
    )
    client.put(
        "/api/world/position",
        json={"classroom_id": classroom["id"], "map_key": "town_hub", "tile_x": 22, "tile_y": 34, "direction": "up"},
        headers=student["headers"],
    )
    result = client.post(
        "/api/events",
        json={
            "classroom_id": classroom["id"],
            "event_id": str(uuid4()),
            "quest_id": definition["id"],
            "node_id": "talk",
            "evidence": {"npc_key": "mayor_py"}},
        headers=student["headers"],
    )
    assert result.status_code == 200, result.text
    assert next(q for q in result.json()["world"]["quests"] if q["id"] == definition["id"])["status"] == "complete"
    assert (
        client.put(
            f"/api/teacher/classrooms/{classroom['id']}/releases/180",
            json={"status": "published", "prerequisite_chapter_id": chapter["id"]},
            headers=teacher["headers"],
        ).status_code
        == 200
    )
    assert client.get(f"/api/activities/ch2-lesson?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 403
