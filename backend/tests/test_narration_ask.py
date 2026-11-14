"""Narration uses the existing authorized learning assistant, never a second model path."""
from app import assistant
from app.assistant_context import MessageInput, build_tools
from app.course_exchange_models import CoursePackageState
from app.models import User
from app.narration import repository
from test_narration_generation import doc_material


def test_removed_endpoint_cannot_bypass_disabled_assistant(client, db, classroom, student, monkeypatch):
    calls = []
    monkeypatch.setattr("app.narration.client.chat", lambda *a, **kw: calls.append(True))
    material = doc_material(db, classroom, "授权原文")
    state = db.get(CoursePackageState, db.info["fixture_course_instance_id"])
    state.configuration = {**state.configuration, "assistant": {**state.configuration.get("assistant", {}), "enabled": False}}
    db.commit()
    assert client.post("/api/assistant/sessions", json={"classroom_id": classroom["id"], "material_id": material.id}, headers=student["headers"]).status_code == 403
    assert client.post(f"/api/materials/{material.id}/narrations/1/ask?classroom_id={classroom['id']}", json={"question": "为什么"}, headers=student["headers"]).status_code == 404
    assert calls == []


def test_learning_tools_receive_only_current_approved_narration(client, db, classroom, student, teacher):
    material = doc_material(db, classroom, "本页原文", "下页原文")
    approved = repository.upsert(db, material, 1, text="教师解释" * 800)
    repository.decide(db, material, 1, "accepted", db.get(User, teacher["id"]))
    repository.upsert(db, material, 2, text="秘密草稿")
    db.commit()
    response = client.post("/api/assistant/sessions", json={"classroom_id": classroom["id"], "material_id": material.id}, headers=student["headers"])
    assert response.status_code == 201, response.text
    user = db.get(User, student["id"])
    session, activity = assistant.authorized_session(db, user, response.json()["id"])
    body = MessageInput(message="解释讲解", state={"page_no": 1})
    context = assistant.build_context(db, user, session, activity, body)
    assert "教师解释" in context["pages"][0]["excerpt"]
    assert "秘密草稿" not in str(context)
    tools = {t.name: t for t in build_tools(context, {})}
    assert set(tools) == {"list_material_pages", "search_material_pages", "read_material_page", "get_reading_progress"}
    tail = tools["read_material_page"].invoke({"page_no": 1, "offset": 2200, "limit": 800})
    assert "教师解释" in tail["text"]
    # A frozen running turn is unaffected by a later replacement; the next turn excludes stale narration.
    material.storage_key = "replacement.pdf"
    db.commit()
    newer = assistant.build_context(db, user, session, activity, body)
    assert "教师解释" not in str(newer)
    assert "教师解释" in context["pages"][0]["excerpt"]
    assert approved.status == "accepted"  # Read-only context filtering does not rewrite history.
