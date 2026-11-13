"""Two classes of the same teacher must never share editable definitions."""

import pytest
from sqlalchemy import select

from app.content_relations import set_activity_questions
from app.models import Activity, CourseInstance, Material, Question, User
from app.auth import hash_password, issue


@pytest.fixture
def independent_classes(client, teacher, db):
    first = client.post("/api/classrooms", headers=teacher["headers"], json={"name": "第一个班"}).json()
    second = client.post("/api/classrooms", headers=teacher["headers"], json={"name": "另一个班"}).json()
    classes = [first["id"], second["id"]]
    for key in classes:
        from fixture_instances import clone_current_course
        clone_current_course(db, key)
        db.commit()
    return classes


def test_same_teacher_classrooms_have_independent_editable_bank(client, teacher, independent_classes):
    first, second = independent_classes
    def bank(cid):
        response = client.get(f"/api/teacher/questions?classroom_id={cid}", headers=teacher["headers"])
        assert response.status_code == 200, response.text
        return response.json()
    a, b = bank(first), bank(second)
    assert a and b
    assert {q["id"] for q in a}.isdisjoint({q["id"] for q in b})
    target = a[0]
    response = client.put(f"/api/teacher/questions/{target['id']}?classroom_id={first}",
                          headers=teacher["headers"], json={"title": "甲班独立题目"})
    assert response.status_code == 200, response.text
    assert all(q["title"] != "甲班独立题目" for q in bank(second))
    for suffix in (f"?classroom_id={second}", ""):
        response = client.put(f"/api/teacher/questions/{target['id']}{suffix}",
                              headers=teacher["headers"], json={"title": "越界修改"})
        assert response.status_code == (404 if suffix else 422)


def test_cross_class_associations_and_private_files_are_rejected(client, db, teacher, independent_classes):
    first, second = independent_classes
    instances = [db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == cid)) for cid in (first, second)]
    activity = db.scalar(select(Activity).where(Activity.course_instance_id == instances[0].id, Activity.kind == "practice"))
    question = db.scalar(select(Question).where(Question.course_instance_id == instances[1].id))
    material = db.scalar(select(Material).where(Material.course_instance_id == instances[0].id))
    response = client.put(f"/api/teacher/activities/{activity.id}/questions?classroom_id={first}",
                          headers=teacher["headers"], json={"question_ids": [question.id]})
    assert response.status_code == 404
    with pytest.raises(ValueError, match="同一班级"):
        set_activity_questions(db, activity, [question.id])
    for suffix in (f"?classroom_id={second}", ""):
        for tail in ("", "/file"):
            response = client.get(f"/api/teacher/materials/{material.id}{tail}{suffix}", headers=teacher["headers"])
            assert response.status_code == (404 if suffix else 422)


def test_revision_and_scoped_chapter_order(client, teacher, independent_classes):
    first, second = independent_classes
    url = f"/api/teacher/classrooms/{first}/course"
    before = client.get(url, headers=teacher["headers"]).json()
    for cid in (first, second):
        response = client.post(f"/api/teacher/chapters?classroom_id={cid}", headers=teacher["headers"],
                               json={"title": "本班章节", "order_index": 99})
        assert response.status_code == 201, response.text
    after = client.get(url, headers=teacher["headers"]).json()
    assert after["revision"] == before["revision"] + 1
    repeat = client.post(url + "/empty", headers=teacher["headers"], json={"confirmed": True, "name": "课程"})
    assert repeat.status_code == 200
    assert repeat.json()["id"] == before["id"]
    assert repeat.json()["revision"] == after["revision"]


def test_initialization_is_explicit_and_authorized(client, db, teacher, student):
    other = User(student_id="9988776655", name="另一位教师", role="teacher", password_hash=hash_password("another-password"))
    db.add(other)
    db.commit()
    other_headers = {"Authorization": "Bearer " + issue(db, other)["access_token"]}
    created = client.post("/api/classrooms", headers=teacher["headers"], json={"name": "空班"}).json()
    url = f"/api/teacher/classrooms/{created['id']}/course"
    assert client.get(url, headers=teacher["headers"]).json() is None
    assert client.get(f"/api/teacher/courses?classroom_id={created['id']}", headers=teacher["headers"]).status_code == 409
    assert client.get("/api/teacher/courses", headers=teacher["headers"]).status_code == 422
    assert client.post("/api/classrooms/join", json={"join_code": created["join_code"]}, headers=student["headers"]).status_code == 200
    assert client.get(f"/api/world?classroom_id={created['id']}", headers=student["headers"]).status_code == 409
    assert client.get(f"/api/activities/ch1-lesson?classroom_id={created['id']}", headers=student["headers"]).status_code == 409
    for headers in (other_headers, student["headers"]):
        assert client.post(url + "/empty", headers=headers, json={"confirmed": True, "name": "空白课程"}).status_code == 403
    assert client.post(url + "/empty", headers=teacher["headers"], json={"confirmed": False, "name": "空白课程"}).status_code == 422


def test_material_processing_invalidates_course_preview_revision(client, db, teacher, independent_classes, tmp_path, monkeypatch):
    from sqlalchemy.orm import sessionmaker
    from app import storage

    cid = independent_classes[0]
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path))
    monkeypatch.setattr(storage, "SessionLocal", sessionmaker(db.bind, expire_on_commit=False))
    monkeypatch.setattr(storage, "parse_bounded", lambda *_: {"pages": [{"id": 1, "page_no": 1, "text": "converted", "title": "页"}]})
    response = client.post(f"/api/teacher/materials/upload?classroom_id={cid}", headers=teacher["headers"],
                           data={"title": "当前班资料"}, files={"file": ("course.txt", b"text", "text/plain")})
    assert response.status_code == 201, response.text
    instance = db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == cid))
    before = instance.revision
    storage.process_material(response.json()["id"])
    db.expire_all()
    assert instance.revision == before + 2  # Claim and final result each affect exchange consistency.
    assert db.get(Material, response.json()["id"]).status == "ready"


def test_empty_course_does_not_copy_demo_content_and_retry_keeps_edits(client, teacher):
    created = client.post("/api/classrooms", headers=teacher["headers"], json={"name": "空白课程班"}).json()
    url = f"/api/teacher/classrooms/{created['id']}/course/empty"
    response = client.post(url, headers=teacher["headers"], json={"confirmed": True, "name": "我的课程"})
    assert response.status_code == 200, response.text
    assert client.get(f"/api/teacher/questions?classroom_id={created['id']}", headers=teacher["headers"]).json() == []
    course = client.get(f"/api/teacher/courses?classroom_id={created['id']}", headers=teacher["headers"]).json()
    assert course["chapters"] == []
    retry = client.post(url, headers=teacher["headers"], json={"confirmed": True, "name": "忽略的重试名称"})
    assert retry.json() == response.json()
