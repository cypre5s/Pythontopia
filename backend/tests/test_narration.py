"""Page narration: teacher review state, staleness after a re-upload, and student visibility."""
from conftest import make_material


def pages(*texts):
    """Library-published material, so a student may read it without an activity."""
    return {"library_available": True,
            "pages": [{"id": n, "page_no": n, "title": f"第{n}页", "text": text} for n, text in enumerate(texts, 1)]}


def material_fixture(db, classroom, texts, **fields):
    """Library-published material of the test course; students may read it without an activity."""
    fields.setdefault("kind", "presentation")
    return make_material(db, chapter_ids=[179, 180], course_instance_id=db.info["fixture_course_instance_id"],
                         title="讲义", status="ready", content=pages(*texts), **fields)


def url(classroom, material, suffix=""):
    return f"/api/teacher/classrooms/{classroom['id']}/materials/{material.id}/narrations{suffix}"


def test_teacher_authors_reviews_and_student_sees_only_approved(client, db, classroom, teacher, student):
    material = material_fixture(db, classroom, ["第一页正文", "第二页正文"])
    db.commit()

    listed = client.get(url(classroom, material), headers=teacher["headers"])
    assert listed.status_code == 200, listed.text
    body = listed.json()
    assert body["coverage"] == {"pages": 2, "covered": 0, "missing": 2,
                                "draft": 0, "accepted": 0, "rejected": 0, "unavailable": 0, "stale": 0}
    assert [page["page_no"] for page in body["pages"]] == [1, 2]
    assert body["pages"][0]["has_text"] is True and body["pages"][0]["narration"] is None

    written = client.put(url(classroom, material, "/1"), json={"text": "这一页讲变量。", "target_seconds": 60},
                         headers=teacher["headers"])
    assert written.status_code == 200, written.text
    assert written.json()["status"] == "draft"

    # A draft is invisible to students until a teacher accepts it.
    hidden = client.get(f"/api/materials/{material.id}/narrations?classroom_id={classroom['id']}", headers=student["headers"])
    assert hidden.status_code == 200, hidden.text
    assert hidden.json()["pages"] == []

    accepted = client.post(url(classroom, material, "/1/accept"), headers=teacher["headers"])
    assert accepted.status_code == 200, accepted.text
    assert accepted.json()["status"] == "accepted"

    shown = client.get(f"/api/materials/{material.id}/narrations?classroom_id={classroom['id']}", headers=student["headers"])
    assert shown.status_code == 200, shown.text
    assert shown.json()["pages"] == [{"page_no": 1, "text": "这一页讲变量。", "bridge_in": "", "target_seconds": 60,
                                       "audio": False, "audio_seconds": None}]

    # Rejecting the approval hides it again.
    assert client.post(url(classroom, material, "/1/reject"), headers=teacher["headers"]).status_code == 200
    assert client.get(f"/api/materials/{material.id}/narrations?classroom_id={classroom['id']}",
                      headers=student["headers"]).json()["pages"] == []


def test_editing_one_page_twice_keeps_a_single_row(client, db, classroom, teacher):
    material = material_fixture(db, classroom, ["第一页正文"])
    db.commit()
    for text in ("初稿", "改过一版"):
        assert client.put(url(classroom, material, "/1"), json={"text": text}, headers=teacher["headers"]).status_code == 200
    body = client.get(url(classroom, material), headers=teacher["headers"]).json()
    assert body["pages"][0]["narration"]["text"] == "改过一版"
    assert body["pages"][0]["narration"]["edited_by_teacher"] is True
    assert body["coverage"]["covered"] == 1


def test_reupload_voids_an_approved_narration(client, db, classroom, teacher, student):
    material = material_fixture(db, classroom, ["第一页正文"])
    db.commit()
    client.put(url(classroom, material, "/1"), json={"text": "这一页讲变量。"}, headers=teacher["headers"])
    client.post(url(classroom, material, "/1/accept"), headers=teacher["headers"])

    material.content = pages("第一页正文（改过了）")
    db.commit()

    body = client.get(url(classroom, material), headers=teacher["headers"]).json()
    assert body["pages"][0]["narration"]["status"] == "stale"
    assert "原文已变化" in body["pages"][0]["narration"]["stale_reason"]
    # A stale approval must not keep leaking the old wording to students.
    assert client.get(f"/api/materials/{material.id}/narrations?classroom_id={classroom['id']}",
                      headers=student["headers"]).json()["pages"] == []
    # Re-review is refused until the wording is regenerated or edited again.
    assert client.post(url(classroom, material, "/1/accept"), headers=teacher["headers"]).status_code == 409
    assert client.put(url(classroom, material, "/1"), json={"text": "按新原文重写。"}, headers=teacher["headers"]).json()["status"] == "draft"


def test_narration_rejects_unknown_pages_and_empty_accepts(client, db, classroom, teacher):
    material = material_fixture(db, classroom, ["第一页正文"])
    db.commit()
    assert client.put(url(classroom, material, "/9"), json={"text": "越界"}, headers=teacher["headers"]).status_code == 404
    assert client.put(url(classroom, material, "/1"), json={"text": "   "}, headers=teacher["headers"]).status_code == 422
    assert client.put(url(classroom, material, "/1"), json={"text": "讲解", "target_seconds": 5},
                      headers=teacher["headers"]).status_code == 422
    assert client.post(url(classroom, material, "/1/accept"), headers=teacher["headers"]).status_code == 404


def test_narration_is_scoped_to_the_owning_classroom(client, db, classroom, teacher, student):
    material = material_fixture(db, classroom, ["第一页正文"])
    db.commit()
    other = client.post("/api/classrooms", json={"name": "别人的班级"}, headers=teacher["headers"])
    assert other.status_code == 201, other.text
    moved = url({"id": other.json()["id"]}, material)
    # The other classroom has no course instance yet, so the material cannot belong to it.
    assert client.get(moved, headers=teacher["headers"]).status_code == 409
    assert client.get(f"/api/materials/{material.id}/narrations", headers=student["headers"]).status_code == 422


def test_deleting_a_draft_removes_it_from_the_list(client, db, classroom, teacher):
    material = material_fixture(db, classroom, ["第一页正文"])
    db.commit()
    client.put(url(classroom, material, "/1"), json={"text": "讲解"}, headers=teacher["headers"])
    assert client.delete(url(classroom, material, "/1"), headers=teacher["headers"]).status_code == 200
    body = client.get(url(classroom, material), headers=teacher["headers"]).json()
    assert body["pages"][0]["narration"] is None
    assert client.delete(url(classroom, material, "/1"), headers=teacher["headers"]).status_code == 404
