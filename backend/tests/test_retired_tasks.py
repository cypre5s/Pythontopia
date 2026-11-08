"""Removed shared teaching tasks disappear without erasing learning history."""

from uuid import uuid4

from sqlalchemy import select

from app.models import Activity, LearningEvent, LearningRecord, NodeState, PlayerQuest, QuestDefinition, StoryDefinition, StorySession, Submission
from app.seed import seed
from conftest import make_question


def test_current_seed_exposes_only_authored_arrival(client, db, student, teacher, classroom):
    seed(db)
    seed(db)
    definitions = list(db.scalars(select(QuestDefinition).where(QuestDefinition.owner_id.is_(None))))
    assert [(item.id, item.title) for item in definitions] == [("arrival", "初来小镇：第一堂 Python 课")]
    response = client.get(f"/api/world?classroom_id={classroom['id']}", headers=student["headers"])
    assert response.status_code == 200
    assert [item["id"] for item in response.json()["quests"]] == ["arrival"]
    assert [item["id"] for item in client.get("/api/teacher/quests", headers=teacher["headers"]).json()] == ["arrival"]
    assert client.get(f"/api/activities/archive-476?classroom_id={classroom['id']}", headers=student["headers"]).status_code == 404


def test_reimport_retires_removed_shared_tasks_and_preserves_owned_tasks_and_grades(client, db, student, teacher, classroom):
    retired_id = "retired-shared-task"
    definition = {"id": retired_id, "title": "Retired teaching task", "chapter_id": 180,
                  "nodes": [{"id": "talk", "type": "TALK_TO_NPC", "title": "Talk", "target": "professor", "dependencies": []}]}
    db.add(QuestDefinition(id=retired_id, chapter_id=180, title=definition["title"], definition=definition))
    owned = {**definition, "id": "teacher-owned-task", "title": "Teacher authored"}
    db.add(QuestDefinition(id=owned["id"], chapter_id=180, title=owned["title"], owner_id=teacher["id"], definition=owned))
    activity = Activity(id="retired-reading", chapter_id=180, title="Retired card", kind="reading", material_id=187,
                        content={"published": True, "question_ids": []})
    db.add(activity)
    db.flush()
    instance = PlayerQuest(user_id=student["id"], classroom_id=classroom["id"], quest_id=retired_id, status="complete")
    db.add(instance)
    db.flush()
    state = NodeState(player_quest_id=instance.id, node_id="talk", evidence={"npc_key": "professor"})
    event = LearningEvent(event_id=str(uuid4()), user_id=student["id"], classroom_id=classroom["id"],
                          player_quest_id=instance.id, node_id="talk", evidence={"npc_key": "professor"})
    record = LearningRecord(user_id=student["id"], classroom_id=classroom["id"], activity_id=activity.id, data={"page_no": 3})
    make_question(db, id="test-retired-question", chapter_ids=[180], title="Historical question", kind="python", prompt="Test only")
    submission = Submission(user_id=student["id"], classroom_id=classroom["id"], activity_id=activity.id, question_id="test-retired-question",
                            idempotency_key=str(uuid4()), mode="submit", status="accepted", code="historic code")
    db.add_all([state, event, record, submission])
    db.add(StoryDefinition(id="retired-story", chapter_id=180, quest_id=retired_id,
                           content={"nodes": [{"id": "old", "type": "dialogue", "text": "Old"}], "bindings": {}}))
    db.add(StorySession(user_id=student["id"], classroom_id=classroom["id"], story_id="retired-story", npc_key="professor",
                        node_id="old", status="active"))
    db.commit()
    instance_id, event_id, record_id, submission_id = instance.id, event.id, record.id, submission.id
    seed(db)
    seed(db)
    assert db.get(QuestDefinition, retired_id) is None
    assert db.get(QuestDefinition, owned["id"]).owner_id == teacher["id"]
    assert db.get(StoryDefinition, "retired-story") is None
    assert db.get(PlayerQuest, instance_id).status == "complete"
    assert db.get(NodeState, (instance_id, "talk")).evidence == {"npc_key": "professor"}
    assert db.get(LearningEvent, event_id).evidence == {"npc_key": "professor"}
    assert db.get(LearningRecord, record_id).data == {"page_no": 3}
    assert db.get(Submission, submission_id).status == "accepted"
    assert db.get(Submission, submission_id).code == "historic code"
    assert db.get(Activity, activity.id).content["published"] is False
    assert db.scalar(select(StorySession).where(StorySession.story_id == "retired-story")).status == "ended"
    response = client.get(f"/api/world?classroom_id={classroom['id']}", headers=student["headers"])
    assert response.status_code == 200
    assert retired_id not in [item["id"] for item in response.json()["quests"]]
    details = client.get(f"/api/teacher/classrooms/{classroom['id']}/students/{student['id']}", headers=teacher["headers"])
    assert details.status_code == 200
    assert event_id in [item["id"] for item in details.json()["events"]]
    assert record_id in [item["id"] for item in details.json()["participation"]]
    assert submission_id in [item["id"] for item in details.json()["submissions"]]
