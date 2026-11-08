from app.content_relations import activity_questions
from copy import deepcopy
from uuid import uuid4

import pytest
from sqlalchemy import func, select

from app.models import Activity, ActivityProgress, ChapterRelease, LearningEvent, Material, PlayerQuest, QuestDefinition, StoryDefinition, StorySession, Submission, User, WorldFlag
from app.quests import validate_evidence
from app.courses import progress_payload
from app.story import validate_story
from conftest import move_player


@pytest.fixture
def story_setup(db, classroom):
    db.delete(db.get(StoryDefinition, "first-steps"))
    material = db.get(Material, 186)
    pages = material.content["pages"][:2]
    activity = Activity(id="story-test-lesson", chapter_id=179, title="Test reading", kind="lesson", material_id=186,
                        npc_key="librarian_doc", content={ "completion": {"required_page_ids": [p["id"] for p in pages]}})
    db.add(activity)
    db.flush()
    quest = {
        "id": "story-test", "chapter_id": 179, "title": "Test story", "nodes": [
            {"id": "hello", "type": "PLAY_DIALOGUE", "title": "Hello", "target": "mayor_py", "dependencies": [],
             "dialogue_id": "story:story-test:hello", "navigation_target": "mayor_py"},
            {"id": "read", "type": "STUDY_LESSON", "title": "Read", "dependencies": ["hello"],
             "activity_id": activity.id, "navigation_target": "librarian_doc"},
        ], "effects": []}
    content = {
        "schema_version": 1,
        "bindings": {"lesson": {"activity_id": activity.id, "goal_id": "read"}},
        "entries": {
            "mayor_py": [{"when": {"goal_complete": "hello"}, "node": "remind"}, {"when": {"always": True}, "node": "welcome"}],
            "librarian_doc": [{"when": {"goal_complete": "read"}, "node": "done"},
                              {"when": {"activity_started": "lesson"}, "node": "resume"},
                              {"when": {"goal_ready": "read"}, "node": "invite"}, {"when": {"always": True}, "node": "locked"}]},
        "nodes": [
            {"id": "welcome", "type": "dialogue", "text": "Welcome", "checkpoint": "hello", "next": "end"},
            {"id": "remind", "type": "dialogue", "text": "Go to the library", "next": "end"},
            {"id": "invite", "type": "dialogue", "text": "Start?", "choices": [{"id": "yes", "label": "Read", "next": "lesson"},
                                                                                      {"id": "no", "label": "Later", "next": "end"}]},
            {"id": "resume", "type": "dialogue", "text": "Welcome back", "choices": [{"id": "yes", "label": "Continue", "next": "lesson"}]},
            {"id": "lesson", "type": "activity", "binding": "lesson", "returns": {"completed": "done", "suspended": "paused", "unavailable": "missing"}},
            {"id": "done", "type": "dialogue", "text": "Go practice", "next": "end"},
            {"id": "paused", "type": "dialogue", "text": "Come back later", "next": "end"},
            {"id": "locked", "type": "dialogue", "text": "Talk to the mayor", "next": "end"},
            {"id": "missing", "type": "dialogue", "text": "Unavailable", "next": "end"},
            {"id": "end", "type": "end"},
        ],
        "checkpoint_effects": {"hello": [{"type": "set_world_flag", "key": "library_unlocked"}]}}
    validate_story(content, db, quest)
    db.add(QuestDefinition(id=quest["id"], chapter_id=179, title=quest["title"], definition=quest))
    db.add(StoryDefinition(id="story-test", chapter_id=179, quest_id=quest["id"], content=content))
    db.commit()
    return {"pages": pages, "content": content, "quest": quest}


def move(client, student, classroom, npc):
    positions = {"mayor_py": ("town_hub", 22, 34), "librarian_doc": ("library", 9, 5)}
    map_key, x, y = positions[npc]
    move_player(client, student["headers"], classroom["id"], map_key, x, y)


def interact(client, student, classroom, npc):
    move(client, student, classroom, npc)
    response = client.post("/api/story/interactions", json={"classroom_id": classroom["id"], "npc_key": npc}, headers=student["headers"])
    assert response.status_code == 200, response.text
    return response.json()


def advance(client, student, classroom, view, choice=None, event_id=None, endpoint="advance"):
    body = {"classroom_id": classroom["id"], "revision": view["revision"], "event_id": event_id or str(uuid4())}
    if choice:
        body["choice"] = choice
    response = client.post(f"/api/story/sessions/{view['id']}/{endpoint}", json=body, headers=student["headers"])
    assert response.status_code == 200, response.text
    return response.json()


def test_first_visit_reminder_pause_refresh_resume_and_verified_reading(client, db, student, classroom, story_setup):
    cid = classroom["id"]
    view = interact(client, student, classroom, "mayor_py")
    assert view["node"]["id"] == "welcome"
    view = advance(client, student, classroom, view)
    assert view["status"] == "ended" and "library_unlocked" in view["world"]["flags"]
    view = interact(client, student, classroom, "mayor_py")
    assert view["node"]["id"] == "remind"
    advance(client, student, classroom, view)
    view = interact(client, student, classroom, "librarian_doc")
    assert view["node"]["id"] == "invite"
    view = advance(client, student, classroom, view, "yes")
    assert view["status"] == "waiting"
    first, second = story_setup["pages"]
    response = client.post("/api/activities/story-test-lesson/progress", json={"classroom_id": cid, "page_no": first["page_no"]}, headers=student["headers"])
    assert response.status_code == 200 and response.json()["completed"] is False
    assert response.json()["visited_pages"] == [first["id"]]
    current = client.get(f"/api/story/current?classroom_id={cid}", headers=student["headers"]).json()
    assert current["id"] == view["id"] and current["activity"]["page_no"] == first["page_no"]
    view = advance(client, student, classroom, current, endpoint="return")
    assert view["node"]["id"] == "paused"
    advance(client, student, classroom, view)
    view = interact(client, student, classroom, "librarian_doc")
    assert view["node"]["id"] == "resume"
    view = advance(client, student, classroom, view, "yes")
    assert view["activity"]["page_no"] == first["page_no"]
    response = client.post("/api/activities/story-test-lesson/progress", json={"classroom_id": cid, "page_no": second["page_no"]}, headers=student["headers"])
    assert response.status_code == 200 and response.json()["completed"] is True
    view = advance(client, student, classroom, view, endpoint="return")
    assert view["node"]["id"] == "done"
    assert next(q for q in view["world"]["quests"] if q["id"] == "story-test")["status"] == "complete"
    from app.integration_models import AssistantCall

    assert db.scalar(select(func.count()).select_from(AssistantCall)) == 0
    assert db.scalar(select(func.count()).select_from(ActivityProgress)) == 1


def test_session_idempotency_revision_choice_and_account_isolation(client, db, student, teacher, classroom, story_setup):
    view = interact(client, student, classroom, "mayor_py")
    request_id = str(uuid4())
    completed = advance(client, student, classroom, view, event_id=request_id)
    replay = advance(client, student, classroom, view, event_id=request_id)
    assert replay["revision"] == completed["revision"]
    assert db.scalar(select(func.count()).select_from(LearningEvent).where(LearningEvent.node_id == "hello")) == 1
    body = {"classroom_id": classroom["id"], "revision": view["revision"], "event_id": request_id, "choice": "bad"}
    path = f"/api/story/sessions/{view['id']}/advance"
    assert client.post(path, json=body, headers=student["headers"]).status_code == 409
    body["event_id"] = str(uuid4())
    assert client.post(path, json=body, headers=student["headers"]).status_code == 409
    assert client.post(path, json=body, headers=teacher["headers"]).status_code == 403
    stranger = client.post("/api/auth/register", json={"student_id": "181721318161492", "name": "Stranger", "password": "long-enough-password"}).json()
    stranger_headers = {"Authorization": "Bearer " + stranger["access_token"]}
    assert client.post(path, json=body, headers=stranger_headers).status_code == 403
    assert db.scalar(select(func.count()).select_from(StorySession).where(StorySession.status.in_(["active", "waiting"]))) == 0


def test_partial_participation_cannot_bypass_completion_and_foreign_page_rejected(client, db, student, classroom, story_setup):
    view = interact(client, student, classroom, "mayor_py")
    advance(client, student, classroom, view)
    cid = classroom["id"]
    record = client.post("/api/activities/story-test-lesson/participation", json={"classroom_id": cid, "page_no": story_setup["pages"][0]["page_no"]},
                         headers=student["headers"]).json()["record_id"]
    body = {"classroom_id": cid, "event_id": str(uuid4()), "quest_id": "story-test", "node_id": "read", "evidence": {"record_id": record}}
    assert client.post("/api/events", json=body, headers=student["headers"]).status_code == 422
    assert client.post("/api/activities/story-test-lesson/progress", json={"classroom_id": cid, "page_no": 99999}, headers=student["headers"]).status_code == 422
    assert client.post("/api/activities/story-test-lesson/progress", json={"classroom_id": cid, "page_no": 1, "version": 99}, headers=student["headers"]).status_code == 422
    assert db.scalar(select(func.count()).select_from(ActivityProgress)) == 0


@pytest.mark.parametrize("mutation", ["missing_next", "cycle", "no_fallback", "unknown_condition", "bad_binding", "grade_effect", "unknown_goal"])
def test_story_validation_rejects_invalid_graph(story_setup, mutation):
    content = deepcopy(story_setup["content"])
    if mutation == "missing_next":
        content["nodes"][0]["next"] = "not-a-node"
    elif mutation == "cycle":
        content["nodes"][0]["next"] = "welcome"
    elif mutation == "no_fallback":
        content["entries"]["mayor_py"].pop()
    elif mutation == "unknown_condition":
        content["entries"]["mayor_py"][0]["when"] = {"python": "eval('bad')"}
    elif mutation == "bad_binding":
        content["bindings"]["lesson"]["goal_id"] = "missing-goal"
    elif mutation == "grade_effect":
        content["checkpoint_effects"]["hello"] = [{"type": "pass_assessment"}]
    elif mutation == "unknown_goal":
        content["nodes"][0]["checkpoint"] = "not-a-goal"
    with pytest.raises(ValueError):
        validate_story(content, quest_definition=story_setup["quest"])


def test_missing_material_returns_unavailable_without_completing_goal(client, db, student, classroom, story_setup):
    view = interact(client, student, classroom, "mayor_py")
    advance(client, student, classroom, view)
    view = interact(client, student, classroom, "librarian_doc")
    material = db.get(Material, 186)
    material.status = "failed"
    db.commit()
    view = advance(client, student, classroom, view, "yes")
    assert view["status"] == "active" and view["node"]["id"] == "missing"
    assert not db.get(WorldFlag, (student["id"], classroom["id"], "story:read"))


def test_completed_story_yields_to_ready_second_chapter_mission(client, db, student, classroom, story_setup, branching_course):
    cid = classroom["id"]
    client.get(f"/api/world?classroom_id={cid}", headers=student["headers"])
    for quest_id in ("arrival", "story-test"):
        instance = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == student["id"], PlayerQuest.classroom_id == cid,
                                                      PlayerQuest.quest_id == quest_id))
        instance.status = "complete"
    db.commit()
    move(client, student, classroom, "mayor_py")
    response = client.post("/api/story/interactions", json={"classroom_id": cid, "npc_key": "mayor_py"}, headers=student["headers"])
    assert response.status_code == 404
    response = client.post("/api/events", json={"classroom_id": cid, "event_id": str(uuid4()), "quest_id": "test-branching", "node_id": "mission",
                                              "evidence": {"npc_key": "mayor_py"}}, headers=student["headers"])
    assert response.status_code == 200, response.text
    quest = next(item for item in response.json()["world"]["quests"] if item["id"] == "test-branching")
    assert next(node for node in quest["nodes"] if node["id"] == "professor")["status"] == "ready"


def test_formal_submission_survives_current_question_and_activity_edits(db, student, classroom):
    activity = db.get(Activity, "ch1-practice")
    questions = list(activity_questions(db, activity))
    for question in questions:
        db.add(Submission(user_id=student["id"], classroom_id=classroom["id"], activity_id=activity.id, question_id=question.id,
                          idempotency_key=str(uuid4()), mode="submit", status="accepted"))
    db.commit()
    node = {"type": "COMPLETE_PRACTICE", "activity_id": activity.id}
    user = db.get(User, student["id"])
    validate_evidence(db, user, classroom["id"], node, {})
    activity.title += " updated"
    questions[0].prompt += " corrected"
    db.commit()
    assert progress_payload(db, user, classroom["id"], activity)["completed"] is True
    validate_evidence(db, user, classroom["id"], node, {})


@pytest.mark.parametrize("independent_story", [False, True])
def test_paused_story_keeps_direct_access_denied_without_blocking_an_independent_chapter(client, db, student, classroom, story_setup, independent_story, branching_course):
    cid = classroom["id"]
    view = interact(client, student, classroom, "mayor_py")
    if independent_story:
        quest = {"id": "independent-story", "title": "Independent", "chapter_id": 180,
                 "nodes": [{"id": "talk", "type": "TALK_TO_NPC", "title": "Talk", "target": "mayor_py", "dependencies": []}]}
        db.add(QuestDefinition(id=quest["id"], title=quest["title"], chapter_id=180, definition=quest))
        db.add(StoryDefinition(id=quest["id"], quest_id=quest["id"], chapter_id=180,
                               content={"schema_version": 1, "bindings": {}, "entries": {"mayor_py": [{"when": {"always": True}, "node": "independent"}]},
                                        "nodes": [{"id": "independent", "type": "dialogue", "text": "Available independent chapter", "next": "end"},
                                                  {"id": "end", "type": "end"}]}))
    db.get(ChapterRelease, (cid, 179)).status = "draft"
    db.commit()
    assert client.get(f"/api/story/current?classroom_id={cid}", headers=student["headers"]).status_code == 403
    response = client.post(f"/api/story/sessions/{view['id']}/advance",
                           json={"classroom_id": cid, "event_id": str(uuid4()), "revision": view["revision"]}, headers=student["headers"])
    assert response.status_code == 403
    response = client.post("/api/story/interactions", json={"classroom_id": cid, "npc_key": "mayor_py"}, headers=student["headers"])
    if independent_story:
        assert response.status_code == 200, response.text
        assert response.json()["node"]["id"] == "independent"
    else:
        assert response.status_code == 404
        response = client.post("/api/events", json={"classroom_id": cid, "event_id": str(uuid4()), "quest_id": "test-branching", "node_id": "mission",
                                                  "evidence": {"npc_key": "mayor_py"}}, headers=student["headers"])
        assert response.status_code == 200, response.text
        assert client.get(f"/api/story/current?classroom_id={cid}", headers=student["headers"]).json() is None
    assert db.get(StorySession, view["id"]).status == "ended"
    assert not db.get(WorldFlag, (student["id"], cid, "library_unlocked"))


def test_waiting_story_uses_current_activity_and_preserves_progress(client, db, student, classroom):
    cid = classroom["id"]
    session = StorySession(user_id=student["id"], classroom_id=cid, story_id="first-steps",
                           npc_key="librarian_doc", node_id="lesson", status="waiting", revision=1,
                           pending_activity_id="ch1-lesson", return_mapping={"completed": "librarian-completed", "suspended": "librarian-paused", "unavailable": "librarian-missing"})
    db.add(session)
    db.add(ActivityProgress(user_id=student["id"], classroom_id=cid, activity_id="ch1-lesson", page_no=34, visited_pages=[10715], completed=False))
    activity = db.get(Activity, "ch1-lesson")
    activity.title = "Current reading title"
    db.commit()
    response = client.get(f"/api/activities/ch1-lesson?classroom_id={cid}", headers=student["headers"])
    assert response.status_code == 200 and response.json()["title"] == "Current reading title"
    assert response.json()["progress"]["page_no"] == 34
    assert "version" not in response.json()
