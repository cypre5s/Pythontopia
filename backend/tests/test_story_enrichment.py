"""Story DSL behavior: verified state, persistent randomness and harmless ambient scenes."""

from copy import deepcopy
from uuid import uuid4

import pytest
from sqlalchemy import select

from app import assistant, story as story_module
from app.assessments import assessment_questions
from app.models import (
    Activity,
    ActivityProgress,
    AssessmentAttempt,
    Chapter,
    ChapterRelease,
    LearningEvent,
    LearningRecord,
    NodeState,
    PlayerQuest,
    QuestDefinition,
    QuestionSecret,
    StoryDefinition,
    StorySession,
    Submission,
    User,
    WorldFlag,
)
from app.integration_models import AssistantCall, AssistantSession
from app.story import validate_story
from conftest import move_player


@pytest.fixture
def story_space(client, db, student, classroom):
    # Test-only scenes do not add or replace the shipped main quest.
    for item in db.scalars(select(StoryDefinition)):
        db.delete(item)
    db.commit()
    move_player(client, student["headers"], classroom["id"], "town_hub", 22, 34)
    # The original first-steps scene is absent here; avoid its legacy NPC fallback
    # intercepting the independent test scenes at the mayor.
    arrival = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == student["id"],
        PlayerQuest.classroom_id == classroom["id"], PlayerQuest.quest_id == "arrival"))
    arrival.status = "complete"
    db.commit()
    return classroom["id"]


def ambient_content(*, when=None, nodes=None):
    return {
        "schema_version": 1,
        "mode": "ambient",
        "when": when or {"always": True},
        "bindings": {},
        "entries": {"mayor_py": [{"when": {"always": True}, "node": "start"}]},
        "nodes": nodes or [
            {"id": "start", "type": "dialogue", "text": "A quiet afternoon.", "next": "end"},
            {"id": "end", "type": "end"},
        ],
    }


def install_story(db, content, *, story_id="test-enrichment", quest=None, chapter_id=179):
    validate_story(content, db, quest, story_id=story_id)
    item = StoryDefinition(id=story_id, chapter_id=chapter_id, quest_id=quest["id"] if quest else None, content=content)
    db.add(item)
    db.commit()
    return item


def install_task(db, *, npc="mayor_py", checkpoint=False, content=None):
    quest = {
        "id": "test-enrichment-quest", "chapter_id": 179, "title": "Test-only task", "kind": "side",
        "nodes": [{"id": "talk", "type": "TALK_TO_NPC", "title": "Talk", "target": npc, "dependencies": []}],
        "effects": [],
    }
    db.add(QuestDefinition(id=quest["id"], chapter_id=179, title=quest["title"], definition=quest))
    db.flush()
    if content is None:
        node = {"id": "task", "type": "dialogue", "text": "Task conversation", "next": "end"}
        if checkpoint:
            node["checkpoint"] = "talk"
        content = {"schema_version": 1, "bindings": {},
                   "entries": {npc: [{"when": {"always": True}, "node": "task"}]},
                   "nodes": [node, {"id": "end", "type": "end"}]}
    install_story(db, content, story_id="test-enrichment-task", quest=quest)
    return quest


def interact(client, student, classroom_id, *, expected=200):
    response = client.post("/api/story/interactions", headers=student["headers"],
                           json={"classroom_id": classroom_id, "npc_key": "mayor_py"})
    assert response.status_code == expected
    return response.json() if expected == 200 else response


def advance(client, student, classroom_id, view, *, choice=None, event_id=None, expected=200):
    body = {"classroom_id": classroom_id, "revision": view["revision"], "event_id": event_id or str(uuid4())}
    if choice is not None:
        body["choice"] = choice
    response = client.post(f"/api/story/sessions/{view['id']}/advance", headers=student["headers"], json=body)
    assert response.status_code == expected
    return response.json() if expected == 200 else response


def submit_paper(client, db, student, classroom_id, correct_count):
    questions = assessment_questions(db, db.get(Activity, "ch1-assessment"))
    entries = []
    for index, question in enumerate(questions):
        answer = db.get(QuestionSecret, question.id).answer
        if index >= correct_count:
            answer = not answer if question.kind == "boolean" else next(option for option in question.options if option != answer)
        entries.append({"question_id": question.id, "answer": answer})
    response = client.post("/api/activities/ch1-assessment/assessment/submit", headers=student["headers"], json={
        "classroom_id": classroom_id, "idempotency_key": str(uuid4()), "answers": entries})
    assert response.status_code == 201
    return response.json()


def learning_state(db):
    """Compare persisted learning data, not just row counts or response claims."""
    models = (PlayerQuest, NodeState, LearningEvent, LearningRecord, ActivityProgress,
              Submission, AssessmentAttempt, WorldFlag, AssistantCall, AssistantSession)
    return {
        model.__tablename__: sorted(
            [deepcopy({column.name: getattr(row, column.name) for column in model.__table__.columns})
             for row in db.scalars(select(model))], key=repr)
        for model in models
    }


@pytest.mark.parametrize("correct_count,expected_score,expected_node", [
    (0, 0, "needs-help"), (1, 33, "needs-help"), (2, 66, "on-track"), (3, 100, "excellent"),
])
def test_grade_ranges_route_only_from_the_authenticated_whole_paper(
    client, db, student, classroom, story_space, correct_count, expected_score, expected_node,
):
    content = ambient_content(nodes=[
        {"id": "start", "type": "branch", "routes": [
            {"when": {"assessment_score": {"activity_id": "ch1-assessment", "min": 100}}, "node": "excellent"},
            {"when": {"assessment_score": {"activity_id": "ch1-assessment", "min": 60, "max": 99}}, "node": "on-track"},
            {"when": {"assessment_score": {"activity_id": "ch1-assessment", "min": 0, "max": 59}}, "node": "needs-help"},
            {"when": {"always": True}, "node": "not-graded"},
        ]},
        *[{"id": key, "type": "dialogue", "text": key, "next": "end"}
          for key in ("excellent", "on-track", "needs-help", "not-graded")],
        {"id": "end", "type": "end"},
    ])
    install_story(db, content)
    ungraded = interact(client, student, story_space)
    assert ungraded["mode"] == "ambient" and ungraded["node"]["id"] == "not-graded"
    advance(client, student, story_space, ungraded)
    paper = submit_paper(client, db, student, story_space, correct_count)
    assert paper["score"] == expected_score
    before = learning_state(db)
    view = interact(client, student, story_space)
    assert view["node"]["id"] == expected_node
    assert learning_state(db) == before


@pytest.mark.parametrize("pending_status", ["prepared", "queued", "unknown"])
def test_unfinished_judging_never_matches_even_zero_score(client, db, student, classroom, story_space, pending_status):
    paper = submit_paper(client, db, student, story_space, 0)
    db.get(Submission, paper["results"][0]["id"]).status = pending_status
    db.commit()
    content = ambient_content(nodes=[
        {"id": "start", "type": "branch", "routes": [
            {"when": {"assessment_score": {"activity_id": "ch1-assessment", "min": 0}}, "node": "graded"},
            {"when": {"always": True}, "node": "waiting"},
        ]},
        {"id": "graded", "type": "dialogue", "text": "Grade available", "next": "end"},
        {"id": "waiting", "type": "dialogue", "text": "Still judging", "next": "end"},
        {"id": "end", "type": "end"},
    ])
    install_story(db, content)
    assert interact(client, student, story_space)["node"]["id"] == "waiting"


def test_conditional_choices_are_hidden_revalidated_and_cannot_be_forged(client, db, student, classroom, story_space):
    from app.story_conditions import condition_matches

    quest = install_task(db, npc="professor")
    # No ready task at this NPC, so this independent ambient scene is selectable.
    condition = {"goal": {"quest_id": quest["id"], "id": "talk", "status": "complete"}}
    content = ambient_content(nodes=[
        {"id": "start", "type": "dialogue", "text": "Choose", "choices": [
            {"id": "private", "label": "Completed task", "next": "private", "when": condition},
            {"id": "public", "label": "Keep exploring", "next": "end"},
        ]},
        {"id": "private", "type": "dialogue", "text": "Verified completion", "next": "end"},
        {"id": "end", "type": "end"},
    ])
    scene = install_story(db, content)
    client.get(f"/api/world?classroom_id={story_space}", headers=student["headers"])
    hidden = interact(client, student, story_space)
    assert [choice["id"] for choice in hidden["node"]["choices"]] == ["public"]
    assert "when" not in hidden["node"]["choices"][0]
    advance(client, student, story_space, hidden, choice="private", expected=422)
    assert db.get(StorySession, hidden["id"]).revision == hidden["revision"]
    instance = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == student["id"],
        PlayerQuest.classroom_id == story_space, PlayerQuest.quest_id == quest["id"]))
    state = NodeState(player_quest_id=instance.id, node_id="talk")
    db.add(state)
    db.commit()
    assert condition_matches(db, db.get(User, student["id"]), story_space, scene, condition)
    visible = client.get(f"/api/story/current?classroom_id={story_space}", headers=student["headers"]).json()
    assert [choice["id"] for choice in visible["node"]["choices"]] == ["private", "public"]
    assert all("when" not in choice for choice in visible["node"]["choices"])
    # A choice visible earlier is still forbidden if its verified state changes before click.
    db.delete(state)
    db.commit()
    advance(client, student, story_space, visible, choice="private", expected=422)
    allowed = advance(client, student, story_space, visible, choice="public")
    assert allowed["status"] == "ended"


def test_random_scene_is_drawn_once_and_survives_restore_and_request_retry(client, db, student, classroom, story_space, monkeypatch):
    draws = []

    def choose(options):
        draws.append(tuple(options))
        return options[-1]

    monkeypatch.setattr(story_module.secrets, "choice", choose)
    content = ambient_content(nodes=[
        {"id": "start", "type": "dialogue", "text": "Say hello", "next": "random"},
        {"id": "random", "type": "random", "next": ["one", "two"]},
        {"id": "one", "type": "dialogue", "text": "First greeting", "next": "end"},
        {"id": "two", "type": "dialogue", "text": "Second greeting", "next": "end"},
        {"id": "end", "type": "end"},
    ])
    install_story(db, content)
    start = interact(client, student, story_space)
    event_id = str(uuid4())
    selected = advance(client, student, story_space, start, event_id=event_id)
    assert selected["node"]["id"] == "two" and draws == [("one", "two")]
    db.expire_all()
    restored = client.get(f"/api/story/current?classroom_id={story_space}", headers=student["headers"]).json()
    resumed = interact(client, student, story_space)
    repeated = advance(client, student, story_space, start, event_id=event_id)
    assert {view["node"]["id"] for view in (restored, resumed, repeated)} == {"two"}
    assert all(view["revision"] == selected["revision"] for view in (restored, resumed, repeated))
    assert draws == [("one", "two")]
    assert db.get(StorySession, selected["id"]).node_id == "two"


def test_ambient_playthrough_changes_only_story_session_and_never_calls_model(
    client, db, student, classroom, story_space, monkeypatch,
):
    def no_model(*args, **kwargs):
        pytest.fail("scripted ambient conversation invoked the AI runtime")

    monkeypatch.setattr(assistant.AgentFactory, "create", no_model)
    submit_paper(client, db, student, story_space, 2)
    db.add(ActivityProgress(user_id=student["id"], classroom_id=story_space, activity_id="ch1-lesson",
                            page_no=34, visited_pages=[10715], completed=False))
    db.add(WorldFlag(user_id=student["id"], classroom_id=story_space, key="existing-test-flag"))
    db.commit()
    new_task = install_task(db, npc="professor")
    assert db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == student["id"],
        PlayerQuest.classroom_id == story_space, PlayerQuest.quest_id == new_task["id"])) is None
    install_story(db, ambient_content(nodes=[
        {"id": "start", "type": "dialogue", "text": "Have a nice day", "choices": [
            {"id": "reply", "label": "Thank you", "next": "reply"}]},
        {"id": "reply", "type": "dialogue", "text": "See you", "next": "end"},
        {"id": "end", "type": "end"},
    ]))
    before = learning_state(db)
    first = interact(client, student, story_space)
    assert first["mode"] == "ambient"
    second = advance(client, student, story_space, first, choice="reply")
    final = advance(client, student, story_space, second)
    assert final["status"] == "ended"
    assert learning_state(db) == before
    assert client.get(f"/api/story/current?classroom_id={story_space}", headers=student["headers"]).json() is None


def test_ready_task_and_open_task_conversation_take_priority_over_ambient(client, db, student, classroom, story_space):
    install_story(db, ambient_content(), story_id="aaa-ambient")
    install_task(db)
    current = interact(client, student, story_space)
    assert current["mode"] == "task" and current["node"]["id"] == "task"
    assert interact(client, student, story_space)["id"] == current["id"]


def test_completed_task_fallback_yields_to_independent_ambient_scene(client, db, student, classroom, story_space):
    quest = install_task(db, checkpoint=True)
    view = interact(client, student, story_space)
    ended = advance(client, student, story_space, view)
    assert next(item for item in ended["world"]["quests"] if item["id"] == quest["id"])["status"] == "complete"
    install_story(db, ambient_content())
    selected = interact(client, student, story_space)
    assert selected["mode"] == "ambient" and selected["node"]["id"] == "start"


def test_waiting_task_is_not_replaced_by_a_new_ambient_scene(client, db, student, classroom, story_space):
    quest = {
        "id": "test-enrichment-waiting", "chapter_id": 179, "title": "Test practice", "kind": "side",
        "nodes": [{"id": "practice", "type": "COMPLETE_PRACTICE", "title": "Practice", "dependencies": [],
                   "activity_id": "ch1-practice", "navigation_target": "mayor_py"}], "effects": [],
    }
    db.add(QuestDefinition(id=quest["id"], chapter_id=179, title=quest["title"], definition=quest))
    db.flush()
    install_story(db, {
        "schema_version": 1, "bindings": {"work": {"activity_id": "ch1-practice", "goal_id": "practice"}},
        "entries": {"mayor_py": [{"when": {"always": True}, "node": "work"}]},
        "nodes": [{"id": "work", "type": "activity", "binding": "work",
                   "returns": {"completed": "end", "suspended": "end", "unavailable": "end"}},
                  {"id": "end", "type": "end"}],
    }, quest=quest)
    waiting = interact(client, student, story_space)
    assert waiting["mode"] == "task" and waiting["status"] == "waiting"
    install_story(db, ambient_content(), story_id="aaa-new-ambient")
    resumed = interact(client, student, story_space)
    assert resumed["id"] == waiting["id"] and resumed["status"] == "waiting"
    assert resumed["activity"]["id"] == "ch1-practice"


def test_composed_conditions_use_real_goal_states_and_distinguish_chapter_from_all_quests(
    client, db, student, classroom, story_space,
):
    from app.story_conditions import condition_matches

    quest = {
        "id": "test-enrichment-conditions", "chapter_id": 179, "title": "Condition fixture", "kind": "side",
        "nodes": [
            {"id": "talk", "type": "TALK_TO_NPC", "title": "Choose", "target": "professor", "dependencies": []},
            {"id": "next", "type": "TALK_TO_NPC", "title": "Next", "target": "professor", "dependencies": ["talk"]},
            {"id": "alternative", "type": "TALK_TO_NPC", "title": "Alternative", "target": "professor",
             "dependencies": ["talk"], "condition": {"node": "talk", "choice": "alternative"}},
        ], "effects": [],
    }
    db.add(QuestDefinition(id=quest["id"], chapter_id=179, title=quest["title"], definition=quest))
    db.commit()
    scene = install_story(db, ambient_content())
    client.get(f"/api/world?classroom_id={story_space}", headers=student["headers"])
    user = db.get(User, student["id"])
    arrival = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == student["id"],
        PlayerQuest.classroom_id == story_space, PlayerQuest.quest_id == "arrival"))
    arrival.status = "active"
    db.commit()

    def matches(condition):
        validate_story(ambient_content(when=condition), db)
        return condition_matches(db, user, story_space, scene, condition)

    def goal(node_id, status):
        return {"goal": {"quest_id": quest["id"], "id": node_id, "status": status}}

    assert matches({"all": [goal("talk", "ready"), goal("next", "locked"), {"quest": {"id": quest["id"], "status": "active"}}]})
    assert matches({"any": [goal("talk", "complete"), {"not": goal("alternative", "skipped")}]})
    assert not matches({"chapter": {"id": 179, "status": "complete"}})
    assert matches({"chapter": {"id": 179, "status": "available"}})
    instance = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == student["id"],
        PlayerQuest.classroom_id == story_space, PlayerQuest.quest_id == quest["id"]))
    db.add(NodeState(player_quest_id=instance.id, node_id="talk", choice="ordinary"))
    db.commit()
    assert matches({"all": [goal("talk", "complete"), goal("next", "ready"), goal("alternative", "skipped")]})
    assert not matches({"not": goal("talk", "complete")})
    # Main-quest chapter completion does not pretend an unfinished optional task is complete.
    arrival.status = "complete"
    db.commit()
    assert matches({"chapter": {"id": 179, "status": "complete"}})
    assert not matches({"all_quests_complete": True})
    instance.status = "complete"
    db.commit()
    assert matches({"quest": {"id": quest["id"], "status": "complete"}})
    assert matches({"all_quests_complete": True})


def test_ambient_respects_release_gate_and_cannot_restore_after_unpublish(client, db, student, classroom, story_space):
    install_story(db, ambient_content())
    release = db.get(ChapterRelease, (story_space, 179))
    release.status = "draft"
    db.commit()
    interact(client, student, story_space, expected=404)
    release.status = "published"
    db.commit()
    view = interact(client, student, story_space)
    release.status = "draft"
    db.commit()
    assert client.get(f"/api/story/current?classroom_id={story_space}", headers=student["headers"]).status_code == 403
    advance(client, student, story_space, view, expected=403)


def test_score_conditions_and_scene_sessions_are_isolated_by_actor_and_joined_classroom(
    client, db, student, teacher, classroom, story_space,
):
    content = ambient_content(when={"assessment_score": {"activity_id": "ch1-assessment", "min": 100}})
    install_story(db, content)
    submit_paper(client, db, student, story_space, 3)
    view = interact(client, student, story_space)
    stranger_response = client.post("/api/auth/register", json={
        "student_id": "66523241585256", "name": "Other student", "password": "long-enough-test-password"})
    assert stranger_response.status_code == 201
    other = {"headers": {"Authorization": "Bearer " + stranger_response.json()["access_token"]}}
    assert client.post("/api/classrooms/join", headers=other["headers"], json={"join_code": classroom["join_code"]}).status_code == 200
    move_player(client, other["headers"], story_space, "town_hub", 22, 34)
    other_arrival = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == stranger_response.json()["user"]["id"],
        PlayerQuest.classroom_id == story_space, PlayerQuest.quest_id == "arrival"))
    other_arrival.status = "complete"
    db.commit()
    interact(client, other, story_space, expected=404)
    advance(client, other, story_space, view, expected=404)
    assert client.get(f"/api/story/current?classroom_id={story_space}", headers=other["headers"]).json() is None
    created = client.post("/api/classrooms", headers=teacher["headers"], json={"name": "Another isolated classroom"})
    assert created.status_code == 201
    second = created.json()
    from conftest import copy_fixture_course
    mapped = copy_fixture_course(db, classroom["id"], second["id"])
    assert client.post("/api/classrooms/join", headers=student["headers"], json={"join_code": second["join_code"]}).status_code == 200
    move_player(client, student["headers"], second["id"], "town_hub", 22, 34)
    second_arrival = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == student["id"],
        PlayerQuest.classroom_id == second["id"], PlayerQuest.quest_id == mapped["quest"]["arrival"]))
    second_arrival.status = "complete"
    db.commit()
    assert client.post("/api/story/interactions", headers=student["headers"],
                       json={"classroom_id": second["id"], "npc_key": mapped["npc"]["mayor_py"]}).status_code == 404
    advance(client, student, second["id"], view, expected=404)
    assert client.get(f"/api/story/current?classroom_id={second['id']}", headers=student["headers"]).json() is None


@pytest.mark.parametrize("condition", [
    {"python": "eval('untrusted')"}, {"always": True, "all_quests_complete": True},
    {"all": []}, {"any": []}, {"not": []}, {"all_quests_complete": False},
    {"assessment_score": {"activity_id": "ch1-assessment"}},
    {"assessment_score": {"activity_id": "ch1-assessment", "min": True}},
    {"assessment_score": {"activity_id": "ch1-assessment", "min": 70, "max": 30}},
    {"goal": {"quest_id": "arrival", "id": "missing", "status": "accepted"}},
    {"quest": {"id": "arrival", "status": "ready"}},
    {"chapter": {"id": 179, "status": "published"}},
])
def test_story_condition_validation_rejects_ambiguous_executable_or_invalid_conditions(condition):
    with pytest.raises(ValueError):
        validate_story(ambient_content(when=condition))


@pytest.mark.parametrize("condition", [
    {"quest": {"id": "missing-quest", "status": "complete"}},
    {"goal": {"quest_id": "arrival", "id": "missing-goal", "status": "ready"}},
    {"chapter": {"id": 999999, "status": "available"}},
    {"assessment_score": {"activity_id": "ch1-practice", "min": 0}},
])
def test_database_validation_rejects_nonexistent_or_wrong_kind_condition_references(db, condition):
    with pytest.raises(ValueError):
        validate_story(ambient_content(when=condition), db)


def test_condition_nesting_is_bounded_without_evaluating_configured_code():
    condition = {"always": True}
    for _ in range(12):
        condition = {"not": condition}
    with pytest.raises(ValueError):
        validate_story(ambient_content(when=condition))


@pytest.mark.parametrize("mutation", ["binding", "activity", "checkpoint", "effects", "branch-fallback", "branch-cycle", "random-empty", "random-missing"])
def test_ambient_validation_rejects_progress_effects_and_invalid_auto_node_graphs(mutation):
    content = ambient_content()
    if mutation == "binding":
        content["bindings"] = {"reading": {"activity_id": "ch1-lesson", "goal_id": "read"}}
    elif mutation == "activity":
        content["nodes"][0] = {"id": "start", "type": "activity", "binding": "reading",
                               "returns": {"completed": "end", "suspended": "end", "unavailable": "end"}}
    elif mutation == "checkpoint":
        content["nodes"][0]["checkpoint"] = "talk"
    elif mutation == "effects":
        content["checkpoint_effects"] = {"talk": [{"type": "set_world_flag", "key": "library_unlocked"}]}
    elif mutation == "branch-fallback":
        content["nodes"][0] = {"id": "start", "type": "branch", "routes": [
            {"when": {"all_quests_complete": True}, "node": "end"}]}
    elif mutation == "branch-cycle":
        content["nodes"][0] = {"id": "start", "type": "branch", "routes": [
            {"when": {"always": True}, "node": "start"}]}
    else:
        content["nodes"][0] = {"id": "start", "type": "random", "next": [] if mutation == "random-empty" else ["missing"]}
    with pytest.raises(ValueError):
        validate_story(content)


@pytest.mark.parametrize("reference", [
    "missing-assessment", "private-assessment", "missing-quest", "private-quest",
    "missing-goal", "private-goal", "missing-chapter", "private-chapter",
])
def test_negation_and_nested_any_do_not_turn_unavailable_references_into_permission(
    db, student, classroom, story_space, reference,
):
    from app.story_conditions import condition_matches

    foreign_teacher = User(student_id="87963449590180", name="Other author", role="teacher", password_hash="unused-test-only")
    db.add(foreign_teacher)
    db.flush()
    private_quest = {"id": "test-private-condition-quest", "chapter_id": 179, "title": "Private task", "kind": "side",
                     "nodes": [{"id": "talk", "type": "TALK_TO_NPC", "title": "Talk", "target": "mayor_py", "dependencies": []}]}
    db.add_all([
        QuestDefinition(id=private_quest["id"], chapter_id=179, title=private_quest["title"],
                        owner_id=foreign_teacher.id, definition=private_quest),
        Activity(id="test-private-assessment", chapter_id=179, title="Private assessment", kind="assessment",
                 owner_id=foreign_teacher.id, content={"question_ids": []}),
        Chapter(id=990001, title="Private chapter", order_index=990001, owner_id=foreign_teacher.id),
    ])
    db.flush()
    db.add(ChapterRelease(classroom_id=story_space, chapter_id=990001, status="published"))
    db.commit()
    conditions = {
        "missing-assessment": {"assessment_score": {"activity_id": "removed-assessment", "min": 0}},
        "private-assessment": {"assessment_score": {"activity_id": "test-private-assessment", "min": 0}},
        "missing-quest": {"quest": {"id": "removed-quest", "status": "complete"}},
        "private-quest": {"quest": {"id": private_quest["id"], "status": "complete"}},
        "missing-goal": {"goal": {"quest_id": "arrival", "id": "removed-goal", "status": "complete"}},
        "private-goal": {"goal": {"quest_id": private_quest["id"], "id": "talk", "status": "complete"}},
        "missing-chapter": {"chapter": {"id": 990002, "status": "available"}},
        "private-chapter": {"chapter": {"id": 990001, "status": "available"}},
    }
    scene = install_story(db, ambient_content())
    unavailable = conditions[reference]
    visible_false = {"assessment_score": {"activity_id": "ch1-assessment", "min": 0}}
    expressions = [
        {"not": unavailable},
        {"any": [{"not": unavailable}, visible_false]},
        {"not": {"all": [{"always": True}, unavailable]}},
    ]
    before = learning_state(db)
    for condition in expressions:
        assert condition_matches(db, db.get(User, student["id"]), story_space, scene, condition) is False
    assert learning_state(db) == before


def test_negation_still_handles_authorized_unopened_chapters_and_ungraded_assessments_normally(
    db, student, classroom, story_space,
):
    from app.story_conditions import condition_matches

    scene = install_story(db, ambient_content())
    db.get(ChapterRelease, (story_space, 180)).status = "draft"
    db.commit()
    user = db.get(User, student["id"])
    chapter = {"chapter": {"id": 180, "status": "available"}}
    score = {"assessment_score": {"activity_id": "ch1-assessment", "min": 0}}
    assert condition_matches(db, user, story_space, scene, chapter) is False
    assert condition_matches(db, user, story_space, scene, score) is False
    assert condition_matches(db, user, story_space, scene, {"not": chapter}) is True
    assert condition_matches(db, user, story_space, scene, {"any": [score, {"not": chapter}]}) is True
    assert condition_matches(db, user, story_space, scene, {"not": score}) is True


def test_private_score_cannot_reveal_or_enable_a_negated_dialogue_choice(client, db, student, classroom, story_space):
    foreign_teacher = User(student_id="208272298728807", name="Other author", role="teacher", password_hash="unused-test-only")
    db.add(foreign_teacher)
    db.flush()
    db.add(Activity(id="test-private-choice-assessment", chapter_id=179, title="Private exam", kind="assessment",
                    owner_id=foreign_teacher.id, content={"question_ids": []}))
    db.commit()
    unavailable = {"assessment_score": {"activity_id": "test-private-choice-assessment", "min": 0}}
    visible_false = {"assessment_score": {"activity_id": "ch1-assessment", "min": 0}}
    install_story(db, ambient_content(nodes=[
        {"id": "start", "type": "dialogue", "text": "Choose", "choices": [
            {"id": "forbidden", "label": "Private result", "next": "end", "when": {"any": [{"not": unavailable}, visible_false]}},
            {"id": "public", "label": "Goodbye", "next": "end"},
        ]}, {"id": "end", "type": "end"},
    ]))
    before = learning_state(db)
    view = interact(client, student, story_space)
    assert [choice["id"] for choice in view["node"]["choices"]] == ["public"]
    advance(client, student, story_space, view, choice="forbidden", expected=422)
    assert db.get(StorySession, view["id"]).revision == view["revision"]
    assert learning_state(db) == before


@pytest.mark.parametrize("npc,expected_status", [("mayor_py", 404), ("professor", 200)])
def test_new_legacy_task_takes_priority_and_initializes_only_for_its_current_npc(
    client, db, student, classroom, story_space, npc, expected_status,
):
    quest = {"id": "test-new-legacy-npc-task", "chapter_id": 179, "title": "New legacy task", "kind": "side",
             "nodes": [{"id": "talk", "type": "TALK_TO_NPC", "title": "Talk", "target": npc, "dependencies": []}]}
    db.add(QuestDefinition(id=quest["id"], chapter_id=179, title=quest["title"], definition=quest))
    db.commit()
    install_story(db, ambient_content())

    def instance():
        return db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == student["id"],
            PlayerQuest.classroom_id == story_space, PlayerQuest.quest_id == quest["id"]))

    assert instance() is None
    before = learning_state(db)
    response = interact(client, student, story_space, expected=expected_status)
    if npc == "mayor_py":
        assert instance() is not None and instance().status == "active"
        assert list(db.scalars(select(StorySession))) == []
        assert list(db.scalars(select(NodeState))) == []
        assert list(db.scalars(select(LearningEvent))) == []
    else:
        assert response["mode"] == "ambient"
        assert instance() is None
        assert learning_state(db) == before
