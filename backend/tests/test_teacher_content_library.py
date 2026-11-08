"""Independent content authoring, reuse, ownership, and reference protection."""

from sqlalchemy import select

from app.auth import hash_password, issue
from app.content_relations import activity_questions
from app.models import Activity, Material, Question, User


def question_body(key="bank-boolean", **changes):
    return {"id": key, "title": "Bank question", "kind": "boolean", "prompt": "Python is a language.",
            "answer": True, "chapter_ids": [179, 180], "source_type": "ai_generated", "status": "ready", **changes}


def test_bank_question_can_be_unclassified_draft_then_reused_without_activity_coupling(client, db, teacher):
    headers = teacher["headers"]
    draft = client.post("/api/teacher/questions", headers=headers, json=question_body(status="draft", chapter_ids=[], answer=None))
    assert draft.status_code == 201, draft.text
    assert draft.json()["chapter_ids"] == [] and draft.json()["activities"] == []
    assert draft.json()["source_type"] == "ai_generated" and draft.json()["status"] == "draft"
    ready = client.put("/api/teacher/questions/bank-boolean", headers=headers, json={"chapter_ids": [179, 180], "answer": True, "status": "ready"})
    assert ready.status_code == 200, ready.text
    for key, chapter in (("bank-practice", 179), ("bank-assessment", 180)):
        created = client.post("/api/teacher/activities", headers=headers, json={
            "id": key, "chapter_id": chapter, "title": key, "kind": "practice", "npc_key": "arena_guard"})
        assert created.status_code == 201, created.text
        selected = client.put(f"/api/teacher/activities/{key}/questions", headers=headers, json={"question_ids": ["bank-boolean"]})
        assert selected.status_code == 200, selected.text
        assert selected.json()["question_ids"] == ["bank-boolean"]
        assert [q.id for q in activity_questions(db, key)] == ["bank-boolean"]
        assert "question_ids" not in db.get(Activity, key).content
    assert db.scalar(select(Question).where(Question.id == "bank-boolean")).owner_id == teacher["id"]
    courses = client.get("/api/teacher/courses", headers=headers).json()
    assert next(a for a in courses["activities"] if a["id"] == "bank-practice")["question_ids"] == ["bank-boolean"]
    assert client.put("/api/teacher/questions/bank-boolean", headers=headers, json={"status": "disabled"}).status_code == 409
    assert client.put("/api/teacher/questions/bank-boolean", headers=headers, json={"chapter_ids": [179]}).status_code == 409
    assert client.delete("/api/teacher/questions/bank-boolean", headers=headers).status_code == 409
    listing = client.get("/api/teacher/questions", headers=headers).json()
    saved = next(q for q in listing if q["id"] == "bank-boolean")
    assert saved["answer"] is True and len(saved["activities"]) == 2


def test_question_selection_rejects_invalid_status_chapter_duplicates_and_owner(client, db, teacher):
    headers = teacher["headers"]
    assert client.post("/api/teacher/activities", headers=headers, json={"id": "bank-target", "chapter_id": 179,
        "title": "Target", "kind": "assessment", "npc_key": "arena_guard"}).status_code == 201
    for key, changes in (("draft", {"status": "draft"}), ("other-chapter", {"chapter_ids": [180]}), ("valid", {})):
        assert client.post("/api/teacher/questions", headers=headers, json=question_body(key, **changes)).status_code == 201
    target = "/api/teacher/activities/bank-target/questions"
    for ids in (["draft"], ["other-chapter"], ["valid", "valid"], ["missing"]):
        assert client.put(target, headers=headers, json={"question_ids": ids}).status_code == 422
        assert activity_questions(db, "bank-target") == []
    other = User(student_id="2938456729103", name="Other", role="teacher", password_hash=hash_password("other-password-123"))
    db.add(other)
    db.commit()
    other_headers = {"Authorization": "Bearer " + issue(db, other)["access_token"]}
    assert client.post("/api/teacher/questions", headers=other_headers, json=question_body("private")).status_code == 201
    assert client.put(target, headers=headers, json={"question_ids": ["private"]}).status_code == 403
    assert client.put("/api/teacher/questions/private", headers=headers, json={"title": "stolen"}).status_code == 403
    assert "private" not in [q["id"] for q in client.get("/api/teacher/questions", headers=headers).json()]
    assert client.post("/api/teacher/questions", headers=headers, json=question_body("bad-source", source_type="manual")).status_code == 422
    assert client.post("/api/teacher/questions", headers=headers, json=question_body("no-chapter", chapter_ids=[])).status_code == 422


def test_material_upload_without_chapter_then_multichapter_metadata_and_teacher_preview(client, db, teacher, student, classroom, tmp_path, monkeypatch):
    from app import storage
    from sqlalchemy.orm import sessionmaker

    headers = teacher["headers"]
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path))
    monkeypatch.setattr(storage, "SessionLocal", sessionmaker(db.bind, expire_on_commit=False))
    upload = client.post("/api/teacher/materials/upload", headers=headers, data={"title": "Unclassified"},
                         files={"file": ("library.txt", b"Current learning material", "text/plain")})
    assert upload.status_code == 201, upload.text
    key = upload.json()["id"]
    assert next(m for m in client.get("/api/teacher/materials", headers=headers).json() if m["id"] == key)["chapter_ids"] == []
    storage.process_material(key)
    db.expire_all()
    detail = client.get(f"/api/teacher/materials/{key}", headers=headers)
    assert detail.status_code == 200 and detail.json()["chapter_ids"] == []
    assert client.get(f"/api/teacher/materials/{key}/file", headers=headers).content == b"Current learning material"
    assert client.get(f"/api/teacher/materials/{key}/file", headers=student["headers"]).status_code == 403
    updated = client.put(f"/api/teacher/materials/{key}", headers=headers, json={"title": "Shared chapters", "chapter_ids": [179, 180]})
    assert updated.status_code == 200 and updated.json()["chapter_ids"] == [179, 180]
    assert client.post("/api/teacher/activities", headers=headers, json={"id": "library-reading", "chapter_id": 180,
        "title": "Read", "kind": "reading", "material_id": key, "npc_key": "librarian_doc"}).status_code == 201
    assert client.put(f"/api/teacher/materials/{key}", headers=headers, json={"chapter_ids": [179]}).status_code == 409
    assert client.delete(f"/api/teacher/materials/{key}", headers=headers).status_code == 409
    bad = client.post("/api/teacher/materials/upload", headers=headers, data={"title": "wrong type", "material_id": key},
                      files={"file": ("library.pdf", b"%PDF-1.4", "application/pdf")})
    assert bad.status_code == 422
    extra = client.post("/api/teacher/materials/upload", headers=headers, data={"title": "Disposable", "chapter_ids": "[179,180]"},
                        files={"file": ("delete.txt", b"temporary", "text/plain")})
    disposable = extra.json()["id"]
    assert client.delete(f"/api/teacher/materials/{disposable}", headers=headers).status_code == 200
    assert db.get(Material, disposable) is None


def test_assessment_selection_matches_hundred_answer_limit_atomically(client, db, teacher, student, classroom):
    from app.models import QuestionChapter, QuestionSecret

    headers = teacher["headers"]
    assert client.post("/api/teacher/activities", headers=headers, json={"id": "bounded-paper", "chapter_id": 179,
        "title": "Bounded paper", "kind": "assessment", "npc_key": "arena_guard", "assistant_allowed": False}).status_code == 201
    ids = [f"bounded-question-{index}" for index in range(101)]
    db.add_all(Question(id=key, title=key, kind="boolean", prompt="Test only", owner_id=teacher["id"], status="ready") for key in ids)
    db.flush()
    db.add_all(QuestionChapter(question_id=key, chapter_id=179) for key in ids)
    db.add_all(QuestionSecret(question_id=key, answer=True, hidden_cases=[]) for key in ids)
    db.commit()
    path = "/api/teacher/activities/bounded-paper/questions"
    accepted = client.put(path, headers=headers, json={"question_ids": ids[:100]})
    assert accepted.status_code == 200 and accepted.json()["question_ids"] == ids[:100]
    rejected = client.put(path, headers=headers, json={"question_ids": ids})
    assert rejected.status_code == 422
    assert [q.id for q in activity_questions(db, "bounded-paper")] == ids[:100]
    saved = client.put("/api/activities/bounded-paper/assessment", headers=student["headers"], json={
        "classroom_id": classroom["id"], "revision": 0, "answers": [{"question_id": key, "answer": True} for key in ids[:100]]})
    assert saved.status_code == 200 and len(saved.json()["answers"]) == 100


def test_shared_library_payload_hides_other_teacher_activity_and_chapter_metadata(client, db, teacher):
    from app.content_relations import set_activity_questions, set_material_chapters, set_question_chapters
    from app.models import Chapter

    other = User(student_id="5463728193017", name="Other", role="teacher", password_hash=hash_password("other-password-123"))
    db.add(other)
    db.flush()
    chapter = Chapter(title="PRIVATE_CHAPTER", owner_id=other.id, course_id=43, order_index=8)
    db.add(chapter)
    db.flush()
    question = activity_questions(db, "ch1-practice")[0]
    set_material_chapters(db, 186, [179, chapter.id])
    set_question_chapters(db, question.id, [179, chapter.id])
    activity = Activity(id="private-reference", chapter_id=179, title="PRIVATE_ACTIVITY_TITLE", kind="practice", owner_id=other.id,
                        material_id=186, content={"published": True})
    db.add(activity)
    db.flush()
    set_activity_questions(db, activity, [question.id])
    db.commit()
    materials = client.get("/api/teacher/materials", headers=teacher["headers"])
    questions = client.get("/api/teacher/questions", headers=teacher["headers"])
    assert "PRIVATE_ACTIVITY_TITLE" not in materials.text and "PRIVATE_ACTIVITY_TITLE" not in questions.text
    assert next(m for m in materials.json() if m["id"] == 186)["chapter_ids"] == [179]
    assert next(q for q in questions.json() if q["id"] == question.id)["chapter_ids"] == [179]
    assert client.get("/api/teacher/materials/186", headers=teacher["headers"]).json()["chapter_ids"] == [179]


def test_optional_source_evidence_is_authorized_and_blocks_resource_deletion(client, db, teacher, student, classroom):
    from conftest import make_material
    from app.integration_models import AssistantSession

    material = make_material(db, title="Question source", chapter_ids=[179], owner_id=teacher["id"], kind="text", status="ready",
                             content={"pages": [{"id": 1, "page_no": 1, "text": "Source content"}]})
    db.commit()
    headers = teacher["headers"]
    created = client.post("/api/teacher/questions", headers=headers, json=question_body("sourced-question", status="draft", chapter_ids=[],
        source={"material_id": material.id, "page_no": 1, "evidence": "Source content"}))
    assert created.status_code == 201, created.text
    assert created.json()["source"]["material_id"] == material.id
    assert client.delete(f"/api/teacher/materials/{material.id}", headers=headers).status_code == 409
    assert client.post("/api/teacher/questions", headers=headers, json=question_body("wrong-source-page",
        source={"material_id": material.id, "page_no": 2})).status_code == 422
    assert client.delete("/api/teacher/questions/sourced-question", headers=headers).status_code == 200
    db.add(AssistantSession(user_id=student["id"], classroom_id=classroom["id"], activity_id=None, material_id=material.id, messages=[]))
    db.commit()
    assert client.delete(f"/api/teacher/materials/{material.id}", headers=headers).status_code == 409


def test_admin_cannot_assign_dependencies_unavailable_to_activity_owner(client, db, teacher):
    from conftest import make_material

    headers = teacher["headers"]
    assert client.post("/api/teacher/questions", headers=headers, json=question_body("teacher-private-q")).status_code == 201
    own_material = make_material(db, title="Private material", chapter_ids=[179], owner_id=teacher["id"], kind="text", status="ready",
                                 content={"pages": [{"id": 1, "page_no": 1, "text": "Private"}]})
    admin = User(student_id="2398417968024", name="Admin", role="admin", password_hash=hash_password("admin-password-123"))
    db.add(admin)
    db.flush()
    admin_question = Question(id="admin-private-q", owner_id=admin.id, status="ready", title="Admin private", kind="boolean", prompt="Test only")
    db.add(admin_question)
    db.flush()
    from app.content_relations import set_question_chapters
    set_question_chapters(db, admin_question.id, [179])
    admin_material = make_material(db, title="Admin material", chapter_ids=[179], owner_id=admin.id, kind="text", status="ready",
                                   content={"pages": [{"id": 2, "page_no": 1, "text": "Admin private"}]})
    db.commit()
    admin_headers = {"Authorization": "Bearer " + issue(db, admin)["access_token"]}
    before_shared = [q.id for q in activity_questions(db, "ch1-practice")]
    assert client.put("/api/teacher/activities/ch1-practice/questions", headers=admin_headers, json={"question_ids": ["teacher-private-q"]}).status_code == 403
    assert [q.id for q in activity_questions(db, "ch1-practice")] == before_shared
    assert client.put("/api/teacher/activities/ch1-lesson", headers=admin_headers, json={"material_id": own_material.id}).status_code == 403
    assert db.get(Activity, "ch1-lesson").material_id == 186
    assert client.post("/api/teacher/activities", headers=headers, json={"id": "teacher-owner-activity", "chapter_id": 179,
        "title": "Teacher owned", "kind": "practice", "material_id": own_material.id, "npc_key": "arena_guard"}).status_code == 201
    assert client.put("/api/teacher/activities/teacher-owner-activity/questions", headers=admin_headers,
        json={"question_ids": ["teacher-private-q"]}).status_code == 200
    assert client.put("/api/teacher/activities/teacher-owner-activity/questions", headers=admin_headers,
        json={"question_ids": ["admin-private-q"]}).status_code == 403
    assert [q.id for q in activity_questions(db, "teacher-owner-activity")] == ["teacher-private-q"]
    assert client.put("/api/teacher/activities/teacher-owner-activity", headers=admin_headers,
        json={"material_id": admin_material.id}).status_code == 403
    assert db.get(Activity, "teacher-owner-activity").material_id == own_material.id
