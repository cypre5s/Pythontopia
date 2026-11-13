"""Student authorization and grading contracts for reusable library content."""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app import assistant
from app.auth import issue
from app.content_relations import set_activity_questions, set_material_chapters, set_question_chapters
from app.db import Base, get_db
from app.main import create_app
from app.models import Activity, Chapter, ChapterRelease, Classroom, CourseInstance, LearningRecord, Material, Membership, Question, QuestionSecret, User, Npc


@pytest.fixture
def library_runtime():
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    with sessionmaker(engine, expire_on_commit=False)() as db:
        teacher = User(student_id="99001", name="Teacher", role="teacher", password_hash="unused")
        other_teacher = User(student_id="99002", name="Other teacher", role="teacher", password_hash="unused")
        student = User(student_id="99003", name="Student", role="student", password_hash="unused")
        db.add_all([teacher, other_teacher, student])
        db.flush()
        classroom = Classroom(name="Reusable-content classroom", teacher_id=teacher.id, join_code="runtime-test")
        db.add_all([classroom, Chapter(id=9001, title="Open", order_index=9001),
                    Chapter(id=9002, title="Unpublished", order_index=9002)])
        db.flush()
        db.add_all([Membership(user_id=student.id, classroom_id=classroom.id),
                    ChapterRelease(classroom_id=classroom.id, chapter_id=9001, status="published")])
        db.add(Npc(key="teacher", name="老师", portrait="builtin:portrait:professor", sprite="builtin:sprite:chara_2"))
        for key in ("runtime-practice-a", "runtime-practice-b", "runtime-assessment"):
            db.add(Activity(id=key, chapter_id=9001, title=key, kind="assessment" if "assessment" in key else "practice",
                            owner_id=teacher.id, npc_key="teacher", content={"published": True}))
        db.add_all([Question(id="runtime-q1", title="First", kind="boolean", prompt="True?", status="ready", owner_id=teacher.id),
                    Question(id="runtime-q2", title="Second", kind="boolean", prompt="False?", status="ready", owner_id=teacher.id)])
        db.flush()
        for key in ("runtime-q1", "runtime-q2"):
            set_question_chapters(db, key, [9001])
            db.add(QuestionSecret(question_id=key, answer=True))
        for key in ("runtime-practice-a", "runtime-practice-b", "runtime-assessment"):
            set_activity_questions(db, key, ["runtime-q2", "runtime-q1"])
        instance = CourseInstance(classroom_id=classroom.id, package_id="runtime-test", name="Test", package_version="1")
        db.add(instance)
        db.flush()
        from app.course_instances import CONTENT_MODELS
        from sqlalchemy import select
        for model in CONTENT_MODELS:
            for row in db.scalars(select(model)):
                row.course_instance_id = instance.id
                row.stable_id = str(row.key if isinstance(row, Npc) else row.id)
        db.commit()
        app = create_app()

        def override():
            yield db

        app.dependency_overrides[get_db] = override
        headers = {"Authorization": "Bearer " + issue(db, student)["access_token"]}
        client = TestClient(app)
        yield client, db, student, teacher, other_teacher, classroom, headers
        client.close()
    Base.metadata.drop_all(engine)
    engine.dispose()


def test_shared_question_order_and_success_remain_scoped_to_activity(library_runtime):
    client, _, _, _, _, classroom, headers = library_runtime
    for activity in ("runtime-practice-a", "runtime-practice-b"):
        view = client.get(f"/api/activities/{activity}?classroom_id={classroom.id}", headers=headers)
        assert view.status_code == 200, view.text
        assert [question["id"] for question in view.json()["questions"]] == ["runtime-q2", "runtime-q1"]
    for key in ("runtime-q1", "runtime-q2"):
        response = client.post("/api/submissions", headers=headers, json={
            "classroom_id": classroom.id, "activity_id": "runtime-practice-a", "question_id": key,
            "answer": True, "idempotency_key": f"answer-in-first-{key}"})
        assert response.status_code == 201 and response.json()["status"] == "accepted"
    first = client.get(f"/api/activities/runtime-practice-a/progress?classroom_id={classroom.id}", headers=headers).json()
    second = client.get(f"/api/activities/runtime-practice-b/progress?classroom_id={classroom.id}", headers=headers).json()
    assert first["completed"] and first["accepted_question_ids"] == ["runtime-q2", "runtime-q1"]
    assert not second["completed"] and second["accepted_question_ids"] == []


@pytest.mark.parametrize("breakage,expected_status", [("draft", 409), ("foreign", 403), ("chapter", 409)])
def test_unavailable_question_blocks_entire_activity_instead_of_shortening_it(library_runtime, breakage, expected_status):
    client, db, _, _, other_teacher, classroom, headers = library_runtime
    question = db.get(Question, "runtime-q2")
    if breakage == "draft":
        question.status = "draft"
    elif breakage == "foreign":
        question.owner_id = other_teacher.id
    else:
        set_question_chapters(db, question.id, [9002])
    db.commit()
    assert client.get(f"/api/activities/runtime-practice-a?classroom_id={classroom.id}", headers=headers).status_code == expected_status
    response = client.post("/api/submissions", headers=headers, json={
        "classroom_id": classroom.id, "activity_id": "runtime-practice-a", "question_id": "runtime-q1",
        "answer": True, "idempotency_key": "reject-incomplete-paper"})
    assert response.status_code == expected_status
    assert client.get(f"/api/activities/runtime-assessment/assessment?classroom_id={classroom.id}", headers=headers).status_code == expected_status


def test_material_filters_unpublished_associations_and_owner_access(library_runtime):
    client, db, _, teacher, other_teacher, classroom, headers = library_runtime
    scope = db.get(Chapter, 9001).course_instance_id
    own = Material(title="Two chapters", owner_id=teacher.id, course_instance_id=scope, content={"library_available": True})
    foreign = Material(title="Private", owner_id=other_teacher.id, course_instance_id=scope, content={"library_available": True})
    unclassified = Material(title="Unclassified", owner_id=teacher.id, course_instance_id=scope, content={"library_available": True})
    db.add_all([own, foreign, unclassified])
    db.flush()
    set_material_chapters(db, own.id, [9001, 9002])
    set_material_chapters(db, foreign.id, [9001])
    db.commit()
    payload = client.get(f"/api/materials/{own.id}?classroom_id={classroom.id}", headers=headers)
    assert payload.status_code == 200
    assert payload.json()["chapter_ids"] == [9001] and payload.json()["chapter_id"] == 9001
    assert "supplementary" not in payload.json()
    library = client.get(f"/api/library?classroom_id={classroom.id}", headers=headers).json()
    assert [item["id"] for item in library] == [own.id]
    assert library[0]["chapter_ids"] == [9001]
    assert client.get(f"/api/materials/{foreign.id}?classroom_id={classroom.id}", headers=headers).status_code == 403
    assert client.get(f"/api/materials/{unclassified.id}?classroom_id={classroom.id}", headers=headers).status_code == 403
    own.content = {"library_available": False}
    db.commit()
    assert client.get(f"/api/materials/{own.id}?classroom_id={classroom.id}", headers=headers).status_code == 403
    # An activity in the still-unpublished chapter cannot publish the other association.
    db.add(Activity(id="runtime-unpublished", title="Unpublished", kind="reading", chapter_id=9002, course_instance_id=scope,
                    material_id=own.id, owner_id=teacher.id, content={"published": True}))
    db.commit()
    assert client.get(f"/api/materials/{own.id}?classroom_id={classroom.id}", headers=headers).status_code == 403


def test_locked_assessment_keeps_grade_when_bank_or_bindings_change(library_runtime):
    client, db, _, _, _, classroom, headers = library_runtime
    url = "/api/activities/runtime-assessment/assessment"
    body = {"classroom_id": classroom.id, "idempotency_key": "runtime-paper-submit",
            "answers": [{"question_id": key, "answer": True} for key in ("runtime-q2", "runtime-q1")]}
    submitted = client.post(url + "/submit", headers=headers, json=body)
    assert submitted.status_code == 201, submitted.text
    assert submitted.json()["score"] == 100 and submitted.json()["total"] == 2
    # Simulate administrative/import changes, bypassing the teacher API's edit guard.
    db.get(Question, "runtime-q2").status = "disabled"
    set_activity_questions(db, "runtime-assessment", ["runtime-q1"])
    db.commit()
    current = client.get(url + f"?classroom_id={classroom.id}", headers=headers)
    repeated = client.post(url + "/submit", headers=headers, json=body)
    assert current.status_code == 200 and repeated.status_code == 201
    for response in (current, repeated):
        assert response.json()["question_ids"] == ["runtime-q2", "runtime-q1"]
        assert response.json()["score"] == 100 and response.json()["total"] == 2
        assert response.json()["results"] == submitted.json()["results"]


def test_assistant_context_uses_current_authorized_material_questions_and_progress(library_runtime):
    client, db, student, teacher, other_teacher, classroom, headers = library_runtime
    scope = db.get(Chapter, 9001).course_instance_id
    material = Material(title="Allowed", owner_id=teacher.id, course_instance_id=scope, content={"pages": [
        {"id": 99001, "page_no": 1, "title": "Current page", "text": "authorized page text"}]})
    other = Material(title="Foreign", owner_id=other_teacher.id, course_instance_id=scope, content={"library_available": True, "pages": [
        {"id": 99002, "page_no": 1, "title": "Private page", "text": "private other teacher text"}]})
    db.add_all([material, other])
    db.flush()
    set_material_chapters(db, material.id, [9001, 9002])
    set_material_chapters(db, other.id, [9001])
    activity = db.get(Activity, "runtime-practice-a")
    activity.material_id = material.id
    db.add_all([LearningRecord(user_id=student.id, classroom_id=classroom.id, activity_id=activity.id, kind="participation"),
                LearningRecord(user_id=student.id, classroom_id=classroom.id, activity_id="runtime-practice-b", kind="participation")])
    db.commit()
    created = client.post("/api/assistant/sessions", headers=headers,
                          json={"classroom_id": classroom.id, "activity_id": activity.id})
    assert created.status_code == 201, created.text
    session, authorized_activity = assistant.authorized_session(db, student, created.json()["id"])
    context = assistant.build_context(db, student, session, authorized_activity, assistant.MessageInput(message="A question", page_no=1))
    assert [page["material_id"] for page in context["pages"]] == [material.id]
    assert [question["id"] for question in context["activity"]["questions"]] == ["runtime-q2", "runtime-q1"]
    assert context["progress"] == [{"activity_id": activity.id, "kind": "participation"}]
    assert "answer" not in context["activity"]["questions"][0]
    assert client.get("/api/assistant/status", headers=headers).json()["model_calls"] == 0
    material.owner_id = other_teacher.id
    db.commit()
    assert client.get(f"/api/assistant/sessions/{session.id}", headers=headers).status_code == 403
