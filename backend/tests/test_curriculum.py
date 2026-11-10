from uuid import uuid4
from sqlalchemy import select
from app.auth import hash_password, issue
from app.models import Activity, Chapter, KnowledgePoint, User
from app.seed import seed


def another_teacher(db):
    user = User(student_id="70497433634061", name="Other", role="teacher", password_hash=hash_password("curriculum-other-password"))
    db.add(user)
    db.commit()
    return {"id": user.id, "headers": {"Authorization": "Bearer " + issue(db, user)["access_token"]}}


def test_teacher_owns_chapter_and_kc_crud_shared_is_readonly(client, db, teacher, student, classroom):
    headers = teacher["headers"]
    assert client.post("/api/teacher/chapters", json={"title": "No student author"}, headers=student["headers"], params={"classroom_id": classroom["id"]}).status_code == 403
    chapter = client.post("/api/teacher/chapters", json={"title": "Teacher chapter"}, headers=headers, params={"classroom_id": classroom["id"]})
    assert chapter.status_code == 201, chapter.text
    chapter = chapter.json()
    cid = chapter["id"]
    assert chapter["owner_id"] == teacher["id"] and chapter["order_index"] > 7
    assert client.put(f"/api/teacher/chapters/{cid}", json={"title": "Updated"}, headers=headers, params={"classroom_id": classroom["id"]}).json()["title"] == "Updated"
    point = client.post(
        "/api/teacher/knowledge-points",
        json={"chapter_id": cid, "title": "A real authored point", "content": {"summary": "Teacher-authored curriculum metadata"}},
        headers=headers,
     params={"classroom_id": classroom["id"]})
    assert point.status_code == 201, point.text
    point = point.json()
    kid = point["id"]
    assert point["owner_id"] == teacher["id"]
    assert client.put(f"/api/teacher/knowledge-points/{kid}", json={"title": "Updated point"}, headers=headers, params={"classroom_id": classroom["id"]}).status_code == 200
    listing = client.get("/api/teacher/courses", headers=headers, params={"classroom_id": classroom["id"]}).json()
    assert next(p for p in listing["knowledge_points"] if p["id"] == kid)["title"] == "Updated point"
    assert client.put("/api/teacher/chapters/179", json={"title": "Illegal overwrite"}, headers=headers, params={"classroom_id": classroom["id"]}).status_code == 403
    assert client.put("/api/teacher/knowledge-points/1809", json={"title": "Illegal overwrite"}, headers=headers, params={"classroom_id": classroom["id"]}).status_code == 403
    assert client.delete("/api/teacher/chapters/179", headers=headers, params={"classroom_id": classroom["id"]}).status_code == 403
    assert client.delete(f"/api/teacher/chapters/{cid}", headers=headers, params={"classroom_id": classroom["id"]}).status_code == 409
    assert client.delete(f"/api/teacher/knowledge-points/{kid}", headers=headers, params={"classroom_id": classroom["id"]}).status_code == 200
    assert client.delete(f"/api/teacher/chapters/{cid}", headers=headers, params={"classroom_id": classroom["id"]}).status_code == 200


def test_private_chapter_cannot_be_seen_bound_or_published_by_another_teacher(client, db, teacher, student, classroom):
    other = another_teacher(db)
    other_class = client.post("/api/classrooms", json={"name": "Other class"}, headers=other["headers"]).json()
    assert client.post(f"/api/teacher/classrooms/{other_class['id']}/course/empty", json={"name": "Other", "confirmed": True}, headers=other["headers"]).status_code == 200
    chapter = client.post("/api/teacher/chapters", json={"title": "PRIVATE_CHAPTER"}, headers=teacher["headers"], params={"classroom_id": classroom["id"]}).json()
    cid = chapter["id"]
    point = client.post("/api/teacher/knowledge-points", json={"chapter_id": cid, "title": "PRIVATE_POINT"}, headers=teacher["headers"], params={"classroom_id": classroom["id"]}).json()
    for path, payload in ((f"/api/teacher/chapters/{cid}", {"title": "Steal"}), (f"/api/teacher/knowledge-points/{point['id']}", {"title": "Steal"})):
        assert client.put(path, json=payload, headers=other["headers"], params={"classroom_id": classroom["id"]}).status_code == 403
    other_listing = client.get("/api/teacher/courses", headers=other["headers"], params={"classroom_id": classroom["id"]})
    assert "PRIVATE_CHAPTER" not in other_listing.text and "PRIVATE_POINT" not in other_listing.text
    assert client.post("/api/teacher/knowledge-points", json={"chapter_id": cid, "title": "Cannot bind"}, headers=other["headers"], params={"classroom_id": classroom["id"]}).status_code == 403
    assert (
        client.put(f"/api/teacher/classrooms/{other_class['id']}/releases/{cid}", json={"status": "published"}, headers=other["headers"]).status_code
        == 404
    )
    bad_activity = {
        "id": "cross-chapter",
        "chapter_id": cid,
        "title": "Cross private chapter",
        "kind": "practice",
        "npc_key": "mayor_py",
        "knowledge_point_ids": [point["id"]]}
    assert client.post("/api/teacher/activities", json=bad_activity, headers=other["headers"], params={"classroom_id": classroom["id"]}).status_code == 403
    assert (
        client.post(
            "/api/teacher/materials/upload",
            data={"chapter_ids": "[" + str(cid) + "]", "title": "Cross private chapter"},
            files={"file": ("test.txt", b"test", "text/plain")},
            headers=other["headers"],
         params={"classroom_id": classroom["id"]}).status_code
        == 403
    )
    definition = {
        "id": "stolen-quest",
        "title": "Stolen",
        "chapter_id": cid,
        "nodes": [{"id": "talk", "type": "TALK_TO_NPC", "title": "Talk", "target": "mayor_py", "dependencies": []}],
        "effects": []}
    assert client.post("/api/teacher/quests", json={"definition": definition}, headers=other["headers"], params={"classroom_id": classroom["id"]}).status_code == 403
    # Private chapter is hidden from students until their own class owner publishes it.
    assert "PRIVATE_CHAPTER" not in client.get(f"/api/world?classroom_id={classroom['id']}", headers=student["headers"]).text
    own_activity = {**bad_activity, "id": "private-own"}
    created = client.post("/api/teacher/activities", json=own_activity, headers=teacher["headers"], params={"classroom_id": classroom["id"]})
    assert created.status_code == 201
    activity_id = created.json()["id"]
    assert client.get(f"/api/activities/{activity_id}?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 403
    assert (
        client.put(f"/api/teacher/classrooms/{classroom['id']}/releases/{cid}", json={"status": "published"}, headers=teacher["headers"]).status_code
        == 200
    )
    allowed = client.get(f"/api/activities/{activity_id}?classroom_id={classroom['id']}", headers=student["headers"])
    assert allowed.status_code == 200, allowed.text
    assert allowed.json()["knowledge_point_ids"] == [point["id"]]
    assert allowed.json()["knowledge_points"][0]["title"] == "PRIVATE_POINT"
    assert client.get(f"/api/activities/{activity_id}?classroom_id={other_class['id']}", headers=other["headers"]).status_code == 403


def test_activity_kc_binding_validates_owner_chapter_and_current_binding(client, db, teacher, classroom):
    other = another_teacher(db)
    own = client.post("/api/teacher/knowledge-points", json={"chapter_id": 179, "title": "Own addition"}, headers=teacher["headers"], params={"classroom_id": classroom["id"]}).json()
    foreign = KnowledgePoint(chapter_id=179, title="Other teacher retained point", owner_id=other["id"], content={})
    db.add(foreign)
    db.commit()
    payload = {
        "id": "point-bound",
        "chapter_id": 179,
        "title": "Bound",
        "kind": "lesson",
        "npc_key": "mayor_py",
        "material_id": 186,
        "knowledge_point_ids": [1809, own["id"]]}
    response = client.post("/api/teacher/activities", json=payload, headers=teacher["headers"], params={"classroom_id": classroom["id"]})
    assert response.status_code == 201, response.text
    activity_id = response.json()["id"]
    assert db.get(Activity, activity_id).content["knowledge_point_ids"] == [1809, own["id"]]
    for ids in ([0], [-1], [True], ["1809"], [1809, 1809], None):
        bad = {**payload, "id": "bad-" + uuid4().hex, "knowledge_point_ids": ids}
        assert client.post("/api/teacher/activities", json=bad, headers=teacher["headers"], params={"classroom_id": classroom["id"]}).status_code == 422
    assert (
        client.put(f"/api/teacher/activities/{activity_id}", json={"knowledge_point_ids": [foreign.id]}, headers=teacher["headers"], params={"classroom_id": classroom["id"]}).status_code
        == 403
    )
    assert client.put(f"/api/teacher/activities/{activity_id}", json={"knowledge_point_ids": [1843]}, headers=teacher["headers"], params={"classroom_id": classroom["id"]}).status_code == 422
    update = client.put(f"/api/teacher/activities/{activity_id}", json={"knowledge_point_ids": [1809]}, headers=teacher["headers"], params={"classroom_id": classroom["id"]})
    assert update.status_code == 200
    assert db.get(Activity, activity_id).content["knowledge_point_ids"] == [1809]
    assert client.delete(f"/api/teacher/knowledge-points/{own['id']}", headers=teacher["headers"], params={"classroom_id": classroom["id"]}).status_code == 200


def test_seed_binds_test_course_kcs_idempotently(db, teacher, branching_course):
    seed(db)
    seed(db)
    for activity in db.scalars(
        select(Activity).where(Activity.id.in_(["ch1-lesson", "ch1-practice", "ch2-lesson", "ch2-concept", "ch2-io", "ch2-code"]))
    ):
        assert activity.content["knowledge_point_ids"]
        assert all(db.get(KnowledgePoint, id).chapter_id == activity.chapter_id for id in activity.content["knowledge_point_ids"])
    assert db.get(Activity, "ch2-code").content["knowledge_point_ids"] == [1844]


def test_admin_can_manage_selected_classroom_and_seed_cannot_overwrite_it(client, db, teacher, classroom):
    import pytest
    with pytest.raises(ValueError, match="班级课程冲突"):
        seed(db)
    user = db.get(User, teacher["id"])
    user.role = "admin"
    db.commit()
    assert client.put("/api/teacher/chapters/179", json={"title": "Admin maintained shared"}, headers=teacher["headers"], params={"classroom_id": classroom["id"]}).status_code == 200
    assert (
        client.put("/api/teacher/knowledge-points/1809", json={"title": "Admin maintained shared point"}, headers=teacher["headers"], params={"classroom_id": classroom["id"]}).status_code
        == 200
    )
    assert db.get(Chapter, 179).owner_id is None and db.get(KnowledgePoint, 1809).owner_id is None
