# pytest discovers the imported fixture by its name.
# ruff: noqa: F811
from copy import deepcopy

import pytest
from fastapi import HTTPException
from sqlalchemy import select

from app.experience.stages import stage_plan
from app.models import (ActivityProgress, CourseInstance, ExperienceCheckpoint, NodeState, PlayerQuest,
                        QuestDefinition, StorySession, WorldFlag)
from test_dev_tools import dev_client  # noqa: F401

BASE = "/api/dev/experience"


def definition():
    return {"id": "stage-fixture", "title": "阶段夹具", "chapter_id": 179, "nodes": [
        {"id": "start", "title": "选择路线", "type": "TALK_TO_NPC", "target": "guide", "choices": [{"id": "left", "label": "左"}, {"id": "right", "label": "右"}]},
        {"id": "left", "title": "左路", "type": "TALK_TO_NPC", "target": "guide", "dependencies": ["start"], "condition": {"node": "start", "choice": "left"}},
        {"id": "right", "title": "右路", "type": "TALK_TO_NPC", "target": "guide", "dependencies": ["start"], "condition": {"node": "start", "choice": "right"}},
        {"id": "merge", "title": "汇合", "type": "REPORT_BACK", "target": "guide", "dependencies": ["left", "right"], "aggregation": "ANY"},
    ]}


def test_stage_solver_requires_explicit_any_and_rejects_conflicting_branch():
    with pytest.raises(HTTPException):
        stage_plan(definition(), "merge", {}, {})
    plan = stage_plan(definition(), "merge", {}, {"merge": "left"})
    assert [n["node_id"] for n in plan] == ["start", "left"]
    assert plan[0]["choice"] == "left"
    with pytest.raises(HTTPException):
        stage_plan(definition(), "left", {"start": "right"}, {})
    cyclic = definition()
    cyclic["nodes"][0]["dependencies"] = ["merge"]
    with pytest.raises(HTTPException) as caught:
        stage_plan(cyclic, "merge", {}, {})
    assert caught.value.status_code == 422


def test_stage_all_skips_opposite_branch_without_completing_it():
    d = definition()
    d["nodes"][-1]["aggregation"] = "ALL"
    result = stage_plan(d, "merge", {"start": "right"}, {})
    assert [n["node_id"] for n in result] == ["start", "right"]


def test_overview_is_read_only_and_scope_checked(dev_client, db, classroom, student, teacher):
    before = db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == classroom["id"])).revision
    response = dev_client.get(BASE, params={"classroom_id": classroom["id"]}, headers=student["headers"])
    assert response.status_code == 200, response.text
    assert response.json()["chapters"]
    assert db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == classroom["id"])).revision == before
    assert dev_client.get(BASE, params={"classroom_id": classroom["id"]}, headers=teacher["headers"]).status_code == 403
    assert dev_client.get(BASE, params={"classroom_id": 9999}, headers=student["headers"]).status_code == 403
    assert dev_client.get("/api/teacher-town/experience", params={"classroom_id": classroom["id"]}, headers=student["headers"]).status_code == 403


def test_stage_preview_apply_conflict_and_no_learning_evidence(dev_client, db, classroom, student):
    db.add(QuestDefinition(id="stage-fixture", title="阶段夹具", chapter_id=179, definition=definition()))
    db.commit()
    body = {"classroom_id": classroom["id"], "quest_id": "stage-fixture", "node_id": "merge", "any_dependencies": {"merge": "left"}}
    preview = dev_client.post(BASE + "/stage/preview", json=body, headers=student["headers"])
    assert preview.status_code == 200, preview.text
    assert not db.scalar(select(PlayerQuest).where(PlayerQuest.quest_id == "stage-fixture"))
    response = dev_client.post(BASE + "/stage", json={**body, "expected": preview.json()["expected"]}, headers=student["headers"])
    assert response.status_code == 200, response.text
    quest = db.scalar(select(PlayerQuest).where(PlayerQuest.quest_id == "stage-fixture"))
    states = list(db.scalars(select(NodeState).where(NodeState.player_quest_id == quest.id)))
    assert {s.node_id for s in states} == {"start", "left"}
    assert all(s.evidence == {"source": "development_positioning"} for s in states)
    assert quest.status == "active"
    assert dev_client.post(BASE + "/stage", json={**body, "expected": preview.json()["expected"]}, headers=student["headers"]).status_code == 409


def test_checkpoint_restores_state_without_reusing_story_receipts(dev_client, db, classroom, student):
    owner = {"user_id": student["id"], "classroom_id": classroom["id"]}
    db.add(WorldFlag(**owner, key="fixture-before"))
    db.add(ActivityProgress(**owner, activity_id="ch1-lesson", page_no=10723, visited_pages=[10723]))
    story = StorySession(**owner, story_id="fixture", node_id="first", status="active", revision=4, receipts=[{"old": True}])
    db.add(story)
    db.commit()
    saved = dev_client.post(BASE + "/checkpoints", json={"classroom_id": classroom["id"], "name": "课前", "client_state": {"session": {"reading": "3"}}}, headers=student["headers"])
    assert saved.status_code == 200, saved.text
    checkpoint_id = saved.json()["id"]
    db.add(WorldFlag(**owner, key="fixture-after"))
    db.get(ActivityProgress, (student["id"], classroom["id"], "ch1-lesson")).page_no = 10724
    db.commit()
    preview = dev_client.get(BASE + f"/checkpoints/{checkpoint_id}/preview", params={"classroom_id": classroom["id"]}, headers=student["headers"])
    response = dev_client.post(BASE + f"/checkpoints/{checkpoint_id}/restore", json={"classroom_id": classroom["id"], "expected": preview.json()["expected"]}, headers=student["headers"])
    assert response.status_code == 200, response.text
    assert not db.get(WorldFlag, (student["id"], classroom["id"], "fixture-after"))
    assert db.get(ActivityProgress, (student["id"], classroom["id"], "ch1-lesson")).page_no == 10723
    active = db.scalar(select(StorySession).where(StorySession.status == "active"))
    assert active.id != story.id and active.receipts == [] and active.revision == 0
    assert response.json()["client_state"] == {"session": {"reading": "3"}}
    instance = db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == classroom["id"]))
    instance.revision += 1
    db.commit()
    assert dev_client.get(BASE + f"/checkpoints/{checkpoint_id}/preview", params={"classroom_id": classroom["id"]}, headers=student["headers"]).status_code == 409
    assert db.get(ExperienceCheckpoint, checkpoint_id)


def test_checkpoint_foreign_owner_and_chapter_not_published(dev_client, db, classroom, student):
    from app.models import ChapterRelease
    release = db.get(ChapterRelease, (classroom["id"], 179))
    release.status = "paused"
    db.commit()
    assert dev_client.post(BASE + "/chapter", json={"classroom_id": classroom["id"], "chapter_id": 179}, headers=student["headers"]).status_code == 403
    response = dev_client.get(BASE + "/checkpoints/not-mine/preview", params={"classroom_id": classroom["id"]}, headers=student["headers"])
    assert response.status_code == 404


def test_content_jump_does_not_change_completion(dev_client, db, classroom, student):
    body = {"classroom_id": classroom["id"], "kind": "activity", "target": "ch1-practice", "question_no": 1}
    response = dev_client.post(BASE + "/open", json=body, headers=student["headers"])
    assert response.status_code == 200, response.text
    assert response.json()["workspace"]["question_no"] == 1
    assert not db.get(ActivityProgress, (student["id"], classroom["id"], "ch1-practice"))
    assert dev_client.post(BASE + "/open", json={**body, "question_no": 999}, headers=student["headers"]).status_code == 422


def test_definition_change_invalidates_checkpoint_even_without_revision(dev_client, db, classroom, student):
    saved = dev_client.post(BASE + "/checkpoints", json={"classroom_id": classroom["id"], "name": "修改前"}, headers=student["headers"])
    assert saved.status_code == 200
    quest = db.scalar(select(QuestDefinition))
    d = deepcopy(quest.definition)
    d["title"] = "修改后的任务"
    quest.definition = d
    db.commit()
    result = dev_client.get(BASE, params={"classroom_id": classroom["id"]}, headers=student["headers"])
    assert result.json()["checkpoints"][0]["valid"] is False


def test_checkpoint_idempotency_busy_guard_and_preserved_receipts(dev_client, db, classroom, student):
    from uuid import uuid4
    from app.models import Submission, LearningEvent
    from app.integration_models import AssistantCall
    from test_dev_tools import add_progress
    submission_id = add_progress(db, student["id"], classroom["id"])
    body = {"classroom_id": classroom["id"], "name": "可恢复", "request_id": str(uuid4())}
    first = dev_client.post(BASE + "/checkpoints", json=body, headers=student["headers"])
    assert first.status_code == 200, first.text
    assert dev_client.post(BASE + "/checkpoints", json=body, headers=student["headers"]).json() == first.json()
    assert len(list(db.scalars(select(ExperienceCheckpoint)))) == 1
    submission = db.get(Submission, submission_id)
    submission.status = "submission_unknown"
    db.commit()
    assert dev_client.post(BASE + "/spawn", json={"classroom_id": classroom["id"]}, headers=student["headers"]).status_code == 409
    submission.status = "accepted"
    call = db.scalar(select(AssistantCall))
    call.status = "running"
    db.commit()
    assert dev_client.post(BASE + "/spawn", json={"classroom_id": classroom["id"]}, headers=student["headers"]).status_code == 409
    call.status = "complete"
    db.commit()
    path = BASE + f"/checkpoints/{first.json()['id']}"
    preview = dev_client.get(path + "/preview", params={"classroom_id": classroom["id"]}, headers=student["headers"])
    result = dev_client.post(path + "/restore", json={"classroom_id": classroom["id"], "expected": preview.json()["expected"]}, headers=student["headers"])
    assert result.status_code == 200, result.text
    assert db.get(Submission, submission_id).status == "accepted"
    assert db.scalar(select(LearningEvent)) and db.scalar(select(AssistantCall)).model_calls == 1


def test_arrival_stage_rebuilds_building_access_and_continues_real_story(dev_client, db, classroom, student):
    from conftest import move_player
    from uuid import uuid4
    body = {"classroom_id": classroom["id"], "quest_id": "arrival", "node_id": "meet-librarian"}
    preview = dev_client.post(BASE + "/stage/preview", json=body, headers=student["headers"])
    result = dev_client.post(BASE + "/stage", json={**body, "expected": preview.json()["expected"]}, headers=student["headers"])
    assert result.status_code == 200, result.text
    world = dev_client.get(f"/api/world?classroom_id={classroom['id']}", headers=student["headers"]).json()
    assert "library" in world["building_unlock"]
    move_player(dev_client, student["headers"], classroom["id"], "library", 9, 5)
    response = dev_client.post("/api/story/interactions", headers=student["headers"], json={"classroom_id": classroom["id"], "npc_key": "librarian_doc"})
    assert response.status_code == 200, response.text
    view = response.json()
    saved = dev_client.post(BASE + "/checkpoints", json={"classroom_id": classroom["id"], "name": "对白中"}, headers=student["headers"]).json()
    for _ in range(1):
        response = dev_client.post(f"/api/story/sessions/{view['id']}/advance", json={"classroom_id": classroom["id"], "revision": view["revision"], "event_id": str(uuid4())}, headers=student["headers"])
        assert response.status_code == 200, response.text
        view = response.json()
    path = BASE + f"/checkpoints/{saved['id']}"
    preview = dev_client.get(path + "/preview", params={"classroom_id": classroom["id"]}, headers=student["headers"]).json()
    assert dev_client.post(path + "/restore", json={"classroom_id": classroom["id"], "expected": preview["expected"]}, headers=student["headers"]).status_code == 200
    session = db.scalar(select(StorySession).where(StorySession.status == "active"))
    response = dev_client.post(f"/api/story/sessions/{session.id}/advance", json={"classroom_id": classroom["id"], "revision": 0, "event_id": str(uuid4())}, headers=student["headers"])
    assert response.status_code == 200, response.text
    body["node_id"] = "welcome"
    preview = dev_client.post(BASE + "/stage/preview", json=body, headers=student["headers"]).json()
    assert dev_client.post(BASE + "/stage", json={**body, "expected": preview["expected"]}, headers=student["headers"]).status_code == 200
    world = dev_client.get(f"/api/world?classroom_id={classroom['id']}", headers=student["headers"]).json()
    assert "library" not in world["building_unlock"] and world["player"]["map_key"] == "town_hub"


def test_production_preview_owned_checkpoint_and_revoked_owner(db, classroom, teacher, student, monkeypatch):
    from fastapi.testclient import TestClient
    from app.main import create_app
    from app.db import get_db
    from app.models import User
    from test_town_preview import launch
    monkeypatch.setenv("REBORN_ENV", "production")
    monkeypatch.setenv("REBORN_DEV_TOOLS", "1")
    app = create_app()
    app.dependency_overrides[get_db] = lambda: db
    client = TestClient(app)
    assert BASE not in client.get("/openapi.json").json()["paths"]
    _, headers = launch(client, classroom["id"], teacher["headers"])
    base = "/api/teacher-town/experience"
    result = client.post(base + "/checkpoints", json={"classroom_id": classroom["id"], "name": "教师体验"}, headers=headers)
    assert result.status_code == 200, result.text
    assert client.get(base, params={"classroom_id": classroom["id"]}, headers=student["headers"]).status_code == 403
    assert client.get(base, params={"classroom_id": classroom["id"] + 1}, headers=headers).status_code == 403
    db.get(User, teacher["id"]).role = "student"
    db.commit()
    assert client.get(base, params={"classroom_id": classroom["id"]}, headers=headers).status_code == 403

    client.close()


def test_checkpoint_scope_stale_preview_and_capacity(dev_client, db, classroom, student):
    from app.auth import issue
    from app.models import Membership, User
    body = {"classroom_id": classroom["id"], "name": "原账号"}
    saved = dev_client.post(BASE + "/checkpoints", json=body, headers=student["headers"]).json()
    path = BASE + f"/checkpoints/{saved['id']}"
    other = User(student_id="20261115007", name="另一位学生", role="student", password_hash="not-used")
    db.add(other)
    db.flush()
    db.add(Membership(user_id=other.id, classroom_id=classroom["id"]))
    db.commit()
    headers = {"Authorization": "Bearer " + issue(db, other)["access_token"]}
    assert dev_client.get(path + "/preview", params={"classroom_id": classroom["id"]}, headers=headers).status_code == 404
    assert dev_client.delete(path, params={"classroom_id": classroom["id"]}, headers=headers).status_code == 404
    preview = dev_client.get(path + "/preview", params={"classroom_id": classroom["id"]}, headers=student["headers"]).json()
    db.add(WorldFlag(user_id=student["id"], classroom_id=classroom["id"], key="changed-since-preview"))
    db.commit()
    assert dev_client.post(path + "/restore", json={"classroom_id": classroom["id"], "expected": preview["expected"]}, headers=student["headers"]).status_code == 409
    for index in range(19):
        db.add(ExperienceCheckpoint(user_id=student["id"], classroom_id=classroom["id"], name=str(index), definition_hash="old", snapshot={}))
    db.commit()
    assert dev_client.post(BASE + "/checkpoints", json=body, headers=student["headers"]).status_code == 409


def test_chapter_prerequisite_bypass_is_self_scoped_and_keeps_release_gate(dev_client, db, classroom, student):
    from app.models import ChapterRelease, Membership, User
    from app.course_content.access import chapter_available
    release = db.get(ChapterRelease, (classroom["id"], 180))
    release.prerequisite_chapter_id = 179
    other = User(student_id="20261115008", name="其他学生", role="student", password_hash="not-used")
    db.add(other)
    db.flush()
    db.add(Membership(user_id=other.id, classroom_id=classroom["id"]))
    db.commit()
    actor = db.get(User, student["id"])
    assert not chapter_available(db, actor, classroom["id"], 180)
    result = dev_client.post(BASE + "/chapter", json={"classroom_id": classroom["id"], "chapter_id": 180}, headers=student["headers"])
    assert result.status_code == 200, result.text
    assert chapter_available(db, actor, classroom["id"], 180)
    assert not chapter_available(db, other, classroom["id"], 180)
    release.status = "paused"
    db.commit()
    assert not chapter_available(db, actor, classroom["id"], 180)
