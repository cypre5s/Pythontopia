"""Import revised teaching content while retaining actual learner history."""

from copy import deepcopy
from uuid import uuid4

from sqlalchemy import select

from app import seed as importer
from app.models import Activity, ActivityProgress, Classroom, LearningRecord, Material, NodeState, PlayerQuest, Question, QuestionSecret, StoryDefinition, Submission


def test_reimport_updates_current_sources_without_erasing_legacy_progress(db, student, teacher, monkeypatch):
    classroom = Classroom(name="Unmigrated legacy", teacher_id=teacher["id"], join_code=uuid4().hex[:20])
    db.add(classroom)
    db.flush()
    cid = classroom.id
    quest = PlayerQuest(user_id=student["id"], classroom_id=cid, quest_id="arrival", status="complete")
    db.add(quest)
    db.flush()
    state = NodeState(player_quest_id=quest.id, node_id="welcome", evidence={"npc_key": "mayor_py"})
    progress = ActivityProgress(user_id=student["id"], classroom_id=cid, activity_id="ch1-lesson", page_no=34,
                                visited_pages=[10715], completed=False)
    record = LearningRecord(user_id=student["id"], classroom_id=cid, activity_id="ch1-lesson", data={"page_id": 10715})
    submission = Submission(user_id=student["id"], classroom_id=cid, activity_id="ch1-practice", question_id="ch1-abstraction",
                            idempotency_key=str(uuid4()), status="accepted", mode="submit", answer="historic answer")
    db.add_all([state, progress, record, submission])
    db.commit()
    record_id, submission_id, quest_id = record.id, submission.id, quest.id
    material = db.get(Material, 186)
    material.storage_key, material.title = "materials/teacher-current.txt", "教师上传的当前资料"
    uploaded_content = deepcopy(material.content)
    db.commit()

    content = deepcopy(importer.load_content())
    activity = next(a for a in content["activities"] if a["id"] == "ch1-lesson")
    activity["title"] = "更新后的当前阅读活动"
    question = next(q for q in content["questions"] if q["id"] == "ch1-abstraction")
    question["prompt"] = "更新后的当前题面"
    question["answer"] = "更新后的答案"
    story = content["stories"][0]
    dialogue = next(n for n in story["story"]["nodes"] if n["type"] == "dialogue")
    dialogue["text"] = "更新后的当前对白"
    monkeypatch.setattr(importer, "load_content", lambda: content)

    importer.seed(db)
    importer.seed(db)
    db.expire_all()
    assert db.get(Activity, "ch1-lesson").title == activity["title"]
    assert db.get(Question, question["id"]).prompt == question["prompt"]
    assert db.get(QuestionSecret, question["id"]).answer == question["answer"]
    assert db.get(StoryDefinition, story["id"]).content == story["story"]
    definitions = list(db.scalars(select(StoryDefinition)))
    assert {item.id for item in definitions} == {item["id"] for item in content["stories"]}
    assert [item.quest_id for item in definitions if item.quest_id is not None] == ["arrival"]
    assert db.get(PlayerQuest, quest_id).status == "complete"
    assert db.get(NodeState, (quest_id, "welcome")).evidence == {"npc_key": "mayor_py"}
    assert db.get(ActivityProgress, (student["id"], cid, "ch1-lesson")).visited_pages == [10715]
    assert db.get(ActivityProgress, (student["id"], cid, "ch1-lesson")).page_no == 34
    assert db.get(LearningRecord, record_id).data == {"page_id": 10715}
    assert db.get(Submission, submission_id).answer == "historic answer"
    assert db.get(Submission, submission_id).status == "accepted"
    assert db.get(Material, 186).title == "教师上传的当前资料"
    assert db.get(Material, 186).content == uploaded_content
