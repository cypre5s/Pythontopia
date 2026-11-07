"""Chapter totals use the same published main-quest rules as prerequisites."""

from uuid import uuid4
from sqlalchemy import select
from app.auth import hash_password, issue
from app.models import PlayerQuest, QuestDefinition, User


def author_quest(client, teacher, quest_id, *, kind="main", optional=False):
    definition = {
        "id": quest_id,
        "title": quest_id,
        "chapter_id": 185,
        "kind": kind,
        "nodes": [{"id": "talk", "type": "TALK_TO_NPC", "title": "Talk", "target": "mayor_py", "dependencies": []}],
        "effects": []}
    if optional:
        definition["nodes"].append(
            {"id": "optional-talk", "type": "TALK_TO_NPC", "title": "Optional", "target": "mayor_py", "dependencies": [], "optional": True}
        )
    response = client.post("/api/teacher/quests", json={"definition": definition}, headers=teacher["headers"])
    assert response.status_code in {200, 201}, response.text


def release(client, teacher, classroom_id, **body):
    response = client.put(f"/api/teacher/classrooms/{classroom_id}/releases/185", json={"status": "published", **body}, headers=teacher["headers"])
    assert response.status_code == 200, response.text


def complete_talk(client, student, classroom_id, quest_id):
    position = client.put(
        "/api/world/position",
        json={"classroom_id": classroom_id, "map_key": "town_hub", "tile_x": 22, "tile_y": 34, "direction": "up"},
        headers=student["headers"],
    )
    assert position.status_code == 200, position.text
    response = client.post(
        "/api/events",
        json={"classroom_id": classroom_id, "event_id": str(uuid4()), "quest_id": quest_id, "node_id": "talk", "evidence": {"npc_key": "mayor_py"}},
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
    author_quest(client, teacher, "progress-main-a", optional=True)
    author_quest(client, teacher, "progress-main-b")
    author_quest(client, teacher, "progress-unfinished-side", kind="side")
    release(client, teacher, cid)
    first = complete_talk(client, student, cid, "progress-main-a")
    assert first["status"] == "complete"
    assert next(node for node in first["nodes"] if node["id"] == "optional-talk")["status"] != "complete"
    assert completed_chapters(client, teacher, cid, student["id"]) == 0
    complete_talk(client, student, cid, "progress-main-b")
    side = db.scalar(
        select(PlayerQuest).where(
            PlayerQuest.user_id == student["id"], PlayerQuest.classroom_id == cid, PlayerQuest.quest_id == "progress-unfinished-side"
        )
    )
    assert side.status == "active"
    assert completed_chapters(client, teacher, cid, student["id"]) == 1
    # Pausing access preserves completed history in the teacher's progress view.
    release(client, teacher, cid, status="paused")
    assert completed_chapters(client, teacher, cid, student["id"]) == 1


def test_progress_only_side_quests_do_not_count_a_chapter(client, teacher, student, classroom):
    cid = classroom["id"]
    author_quest(client, teacher, "progress-only-side", kind="side")
    release(client, teacher, cid)
    assert complete_talk(client, student, cid, "progress-only-side")["status"] == "complete"
    assert completed_chapters(client, teacher, cid, student["id"]) == 0


def test_current_quest_edits_preserve_completion_and_new_quests_count(client, db, teacher, student, classroom, branching_course):
    cid = classroom["id"]
    author_quest(client, teacher, "progress-versioned")
    release(client, teacher, cid)
    complete_talk(client, student, cid, "progress-versioned")
    db.get(QuestDefinition, "progress-versioned").title = "Updated current title"
    db.commit()
    author_quest(client, teacher, "progress-new-main")
    # New main quests join the current chapter without a separate release version.
    assert completed_chapters(client, teacher, cid, student["id"]) == 0
    complete_talk(client, student, cid, "progress-new-main")
    old = db.scalar(
        select(PlayerQuest).where(PlayerQuest.user_id == student["id"], PlayerQuest.classroom_id == cid, PlayerQuest.quest_id == "progress-versioned")
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
    author_quest(client, teacher, "progress-isolation")
    release(client, teacher, cid)
    complete_talk(client, student, cid, "progress-isolation")
    assert completed_chapters(client, teacher, cid, student["id"]) == 1
    other_class = client.post("/api/classrooms", json={"name": "Different classroom"}, headers=teacher["headers"]).json()
    client.post("/api/classrooms/join", json={"join_code": other_class["join_code"]}, headers=student["headers"])
    release(client, teacher, other_class["id"])
    assert completed_chapters(client, teacher, other_class["id"], student["id"]) == 0
    new_student = client.post(
        "/api/auth/register", json={"email": "progress-other@example.test", "name": "Other", "password": "isolated-progress-password"}
    ).json()
    other_headers = {"Authorization": "Bearer " + new_student["access_token"]}
    client.post("/api/classrooms/join", json={"join_code": classroom["join_code"]}, headers=other_headers)
    assert completed_chapters(client, teacher, cid, new_student["user"]["id"]) == 0
    other_teacher = User(
        email="progress-teacher@example.test", name="Other teacher", role="teacher", password_hash=hash_password("other-progress-teacher")
    )
    db.add(other_teacher)
    db.commit()
    other_teacher_headers = {"Authorization": "Bearer " + issue(db, other_teacher)["access_token"]}
    author_quest(client, {"headers": other_teacher_headers}, "foreign-private-main")
    assert completed_chapters(client, teacher, cid, student["id"]) == 1
    blocked = client.get(f"/api/teacher/classrooms/{cid}/progress", headers=other_teacher_headers)
    assert blocked.status_code == 403
    assert client.get(f"/api/teacher/classrooms/{cid}/progress", headers=student["headers"]).status_code == 403
    other_teacher.role = "admin"
    db.commit()
    assert completed_chapters(client, {"headers": other_teacher_headers}, cid, student["id"]) == 1
