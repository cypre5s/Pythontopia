from app.content_relations import activity_questions
from copy import deepcopy

import pytest

from fixture_content import load_content, load_yaml, validate_sources


@pytest.mark.parametrize("text", [
    "schema_version: 1\nschema_version: 1\n",
    "schema_version: 1\nvalue: !!python/object/apply:os.system ['echo bad']\n",
    "schema_version: 1\nvalue: &shared [one]\nother: *shared\n",
    "schema_version: 1\nvalue: 2026-11-05\n",
    "schema_version: 1\nvalue: " + "[" * 35 + "1" + "]" * 35 + "\n",
])
def test_chapter_text_rejects_ambiguous_or_executable_yaml(tmp_path, text):
    source = tmp_path / "chapter.yaml"
    source.write_text(text, encoding="utf-8")
    with pytest.raises(ValueError, match="chapter.yaml"):
        load_yaml(source)


def test_current_sources_validate_without_database():
    counts = validate_sources()
    assert counts == {"npcs": 4, "activities": 3, "quests": 1, "stories": 2, "questions": 9}
    content = load_content()
    assert all("version" not in item for items in content.values() for item in items)
    assert "version" not in content["stories"][0]["story"]["bindings"]["lesson"]


def test_repeated_seed_updates_current_story_quest_activity_and_question(db, monkeypatch):
    import fixture_seed as seed_module
    from app.models import Activity, QuestDefinition, StoryDefinition, Question

    changed = deepcopy(load_content())
    changed["stories"][0]["story"]["nodes"][0]["text"] += " updated"
    quest = changed["quests"][0]
    quest["title"] += " updated"
    activity = next(a for a in changed["activities"] if a["id"] == "ch1-practice")
    activity["title"] += " updated"
    question = changed["questions"][0]
    question["prompt"] += " revised"
    monkeypatch.setattr(seed_module, "load_content", lambda: changed)
    seed_module.seed(db)
    seed_module.seed(db)
    assert db.get(StoryDefinition, changed["stories"][0]["id"]).content == changed["stories"][0]["story"]
    assert db.get(QuestDefinition, quest["id"]).title == quest["title"]
    assert db.get(Activity, activity["id"]).title == activity["title"]
    assert db.get(Question, question["id"]).prompt == question["prompt"]


def test_reimport_revises_question_set_without_removing_grades_or_participation(db, monkeypatch, student, legacy_classroom):
    classroom = legacy_classroom
    from uuid import uuid4
    import fixture_seed as seed_module
    from app.models import ActivityProgress, LearningRecord, Submission

    changed = deepcopy(load_content())
    activity = next(a for a in changed["activities"] if a["id"] == "ch1-practice")
    original = activity["content"]["question_ids"][0]
    submission = Submission(id=str(uuid4()), user_id=student["id"], classroom_id=classroom["id"], activity_id=activity["id"],
                            question_id=original, idempotency_key=str(uuid4()), mode="submit", status="accepted", answer=True)
    record = LearningRecord(user_id=student["id"], classroom_id=classroom["id"], activity_id="ch1-lesson", data={"page_no": 34})
    progress = ActivityProgress(user_id=student["id"], classroom_id=classroom["id"], activity_id="ch1-lesson", page_no=34,
                                visited_pages=[10715], completed=False)
    db.add_all([submission, record, progress])
    db.commit()
    question = deepcopy(next(q for q in changed["questions"] if q["activity_id"] == activity["id"]))
    question["id"] = "new-current-question"
    changed["questions"].append(question)
    activity["content"]["question_ids"].append(question["id"])
    monkeypatch.setattr(seed_module, "load_content", lambda: changed)
    seed_module.seed(db)
    seed_module.seed(db)
    assert [q.id for q in activity_questions(db, activity["id"])] == activity["content"]["question_ids"]
    assert db.get(Submission, submission.id).status == "accepted"
    assert db.get(LearningRecord, record.id).data == {"page_no": 34}
    saved = db.get(ActivityProgress, (student["id"], classroom["id"], "ch1-lesson"))
    assert saved.page_no == 34 and saved.visited_pages == [10715]


def test_removing_ambient_source_ends_only_its_open_session(db, monkeypatch, student, legacy_classroom):
    classroom = legacy_classroom
    from sqlalchemy import func, select
    import fixture_seed as seed_module
    from app.models import ActivityProgress, LearningRecord, NodeState, PlayerQuest, StoryDefinition, StorySession, Submission, WorldFlag

    session = StorySession(user_id=student["id"], classroom_id=classroom["id"], story_id="town-daily", npc_key="mayor_py",
                           node_id="mayor-daily-menu", status="active", revision=3)
    db.add(session)
    db.commit()
    tracked = (ActivityProgress, LearningRecord, NodeState, PlayerQuest, Submission, WorldFlag)
    before = [db.scalar(select(func.count()).select_from(model)) for model in tracked]
    changed = load_content()
    changed["stories"] = [row for row in changed["stories"] if row["quest"] is not None]
    monkeypatch.setattr(seed_module, "load_content", lambda: changed)
    seed_module.seed(db)
    seed_module.seed(db)
    assert db.get(StoryDefinition, "town-daily") is None
    assert (session.status, session.revision, session.pending_activity_id, session.return_mapping) == ("ended", 4, None, {})
    assert [db.scalar(select(func.count()).select_from(model)) for model in tracked] == before
