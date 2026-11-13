"""Chapter totals use the same published main-quest rules as prerequisites."""

from uuid import uuid4
from sqlalchemy import select
from app.auth import hash_password, issue
from app.models import PlayerQuest, QuestDefinition, User


def author_quest(client, teacher, classroom_id, quest_id, *, kind="main", optional=False, expected=201):
    definition = {
        "id": quest_id,
        "title": quest_id,
        "chapter_id": 185,
        "kind": kind,
        "nodes": [{"id": "talk", "type": "REACH_LOCATION", "title": "Talk", "target": "town_square", "dependencies": []}],
        "effects": []}
    if optional:
        definition["nodes"].append(
            {"id": "optional-talk", "type": "REACH_LOCATION", "title": "Optional", "target": "town_square", "dependencies": [], "optional": True}
        )
    response = client.post("/api/teacher/quests", json={"definition": definition}, headers=teacher["headers"], params={"classroom_id": classroom_id})
    assert response.status_code == expected, response.text
    return response.json().get("id")


def release(client, teacher, classroom_id, **body):
    response = client.put(f"/api/teacher/classrooms/{classroom_id}/releases/185", json={"status": "published", **body}, headers=teacher["headers"])
    assert response.status_code == 200, response.text


def complete_goal(client, student, classroom_id, quest_id):
    position = client.put(
        "/api/world/position",
        json={"classroom_id": classroom_id, "map_key": "town_hub", "tile_x": 22, "tile_y": 34, "direction": "up"},
        headers=student["headers"],
    )
    assert position.status_code == 200, position.text
    response = client.post(
        "/api/events",
        json={"classroom_id": classroom_id, "event_id": str(uuid4()), "quest_id": quest_id, "node_id": "talk", "evidence": {}},
        headers=student["headers"],
    )
    assert response.status_code == 200, response.text
    return next(quest for quest in response.json()["world"]["quests"] if quest["id"] == quest_id)


def completed_chapters(client, teacher, classroom_id, student_id):
    response = client.get(f"/api/teacher/classrooms/{classroom_id}/progress", headers=teacher["headers"])
    assert response.status_code == 200, response.text
    count = next(member for member in response.json()["members"] if member["id"] == student_id)["completed_chapters"]
    assert isinstance(count, int) and not isinstance(count, bool)
    return count


def test_progress_requires_all_main_quests_but_not_optional_or_side_nodes(client, db, teacher, student, classroom):
    cid = classroom["id"]
    first_id = author_quest(client, teacher, cid, "progress-main-a", optional=True)
    second_id = author_quest(client, teacher, cid, "progress-main-b")
    side_id = author_quest(client, teacher, cid, "progress-unfinished-side", kind="side")
    release(client, teacher, cid)
    first = complete_goal(client, student, cid, first_id)
    assert first["status"] == "complete"
    assert next(node for node in first["nodes"] if node["id"] == "optional-talk")["status"] != "complete"
    assert completed_chapters(client, teacher, cid, student["id"]) == 0
    complete_goal(client, student, cid, second_id)
    side = db.scalar(
        select(PlayerQuest).where(
            PlayerQuest.user_id == student["id"], PlayerQuest.classroom_id == cid, PlayerQuest.quest_id == side_id
        )
    )
    assert side.status == "active"
    assert completed_chapters(client, teacher, cid, student["id"]) == 1
    # Pausing access preserves completed history in the teacher's progress view.
    release(client, teacher, cid, status="paused")
    assert completed_chapters(client, teacher, cid, student["id"]) == 1


def test_progress_only_side_quests_do_not_count_a_chapter(client, teacher, student, classroom):
    cid = classroom["id"]
    key = author_quest(client, teacher, cid, "progress-only-side", kind="side")
    release(client, teacher, cid)
    assert complete_goal(client, student, cid, key)["status"] == "complete"
    assert completed_chapters(client, teacher, cid, student["id"]) == 0


def test_current_quest_edits_preserve_completion_and_new_quests_count(client, db, teacher, student, classroom, branching_course):
    cid = classroom["id"]
    previous = author_quest(client, teacher, cid, "progress-versioned")
    release(client, teacher, cid)
    complete_goal(client, student, cid, previous)
    db.get(QuestDefinition, previous).title = "Updated current title"
    db.commit()
    new = author_quest(client, teacher, cid, "progress-new-main")
    # New main quests join the current chapter without a separate release version.
    assert completed_chapters(client, teacher, cid, student["id"]) == 0
    complete_goal(client, student, cid, new)
    old = db.scalar(
        select(PlayerQuest).where(PlayerQuest.user_id == student["id"], PlayerQuest.classroom_id == cid, PlayerQuest.quest_id == previous)
    )
    assert old.status == "complete"
    assert completed_chapters(client, teacher, cid, student["id"]) == 1
    # The same predicate permits this chapter as the next chapter's prerequisite.
    response = client.put(
        f"/api/teacher/classrooms/{cid}/releases/180", json={"status": "published", "prerequisite_chapter_id": 185}, headers=teacher["headers"]
    )
    assert response.status_code == 200
    assert client.get(f"/api/activities/ch2-lesson?classroom_id={cid}", headers=student["headers"]).status_code == 200


def test_progress_counts_are_scoped_to_student_classroom_and_teacher(client, db, teacher, student, classroom):
    cid = classroom["id"]
    key = author_quest(client, teacher, cid, "progress-isolation")
    release(client, teacher, cid)
    complete_goal(client, student, cid, key)
    assert completed_chapters(client, teacher, cid, student["id"]) == 1
    other_class = client.post("/api/classrooms", json={"name": "Different classroom"}, headers=teacher["headers"]).json()
    client.post("/api/classrooms/join", json={"join_code": other_class["join_code"]}, headers=student["headers"])
    assert client.post(f"/api/teacher/classrooms/{other_class['id']}/course/empty", json={"name": "Other", "confirmed": True}, headers=teacher["headers"]).status_code == 200
    assert completed_chapters(client, teacher, other_class["id"], student["id"]) == 0
    new_student = client.post(
        "/api/auth/register", json={"student_id": "190515614727603", "name": "Other", "password": "isolated-progress-password"}
    ).json()
    other_headers = {"Authorization": "Bearer " + new_student["access_token"]}
    client.post("/api/classrooms/join", json={"join_code": classroom["join_code"]}, headers=other_headers)
    assert completed_chapters(client, teacher, cid, new_student["user"]["id"]) == 0
    other_teacher = User(
        student_id="259066979884583", name="Other teacher", role="teacher", password_hash=hash_password("other-progress-teacher")
    )
    db.add(other_teacher)
    db.commit()
    other_teacher_headers = {"Authorization": "Bearer " + issue(db, other_teacher)["access_token"]}
    author_quest(client, {"headers": other_teacher_headers}, cid, "foreign-private-main", expected=403)
    assert completed_chapters(client, teacher, cid, student["id"]) == 1
    blocked = client.get(f"/api/teacher/classrooms/{cid}/progress", headers=other_teacher_headers)
    assert blocked.status_code == 403
    assert client.get(f"/api/teacher/classrooms/{cid}/progress", headers=student["headers"]).status_code == 403
    other_teacher.role = "admin"
    db.commit()
    assert completed_chapters(client, {"headers": other_teacher_headers}, cid, student["id"]) == 1
