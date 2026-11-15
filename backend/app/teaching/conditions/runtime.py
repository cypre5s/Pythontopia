"""Evaluate authorized progress conditions against server-verified evidence."""

from fastapi import HTTPException
from sqlalchemy import select

from app.course_content.access import authorize_activity, authorize_classroom, chapter_available, chapter_completion_requirements
from app.course_scope import belongs_to_course, course_clause
from app.models import ActivityProgress, Chapter, NodeState, PlayerQuest, QuestDefinition
from app.teaching.quests.access import quest_visible
from app.teaching.quests.graph import node_statuses


class UnavailableCondition(Exception):
    """A missing or unauthorized target must not become true through negation."""


def condition_matches(db, user, classroom_id, story, condition):
    try:
        if not belongs_to_course(db, story, classroom_id):
            raise UnavailableCondition
        return _matches(db, user, classroom_id, story, condition)
    except UnavailableCondition:
        return False


def _chapter_available(db, user, classroom, chapter_id):
    chapter = db.get(Chapter, chapter_id)
    if not belongs_to_course(db, chapter, classroom.id) or chapter.owner_id not in {None, classroom.teacher_id} and user.role != "admin":
        raise UnavailableCondition
    return chapter_available(db, user, classroom.id, chapter_id)


def _matches(db, user, classroom_id, story, condition):
    key, value = next(iter(condition.items()))
    if key == "always":
        return True
    if key == "all":
        return all(_matches(db, user, classroom_id, story, child) for child in value)
    if key == "any":
        return any(_matches(db, user, classroom_id, story, child) for child in value)
    if key == "not":
        return not _matches(db, user, classroom_id, story, value)
    classroom = authorize_classroom(db, user, classroom_id)
    if key == "building_unlocked":
        from app.teaching.world.state import unlocked_buildings

        return value in unlocked_buildings(db, user, classroom_id)
    if key == "activity_completed":
        return activity_completed(db, user, classroom_id, value)
    if key == "activity_started":
        activity_id = story.content["bindings"][value]["activity_id"]
        try:
            authorize_activity(db, user, classroom_id, activity_id)
        except HTTPException as exc:
            raise UnavailableCondition from exc
        return bool(db.get(ActivityProgress, (user.id, classroom_id, activity_id)))
    if key == "assessment_score":
        from app.assessments import assessment_payload

        try:
            activity = authorize_activity(db, user, classroom_id, value["activity_id"])
        except HTTPException as exc:
            raise UnavailableCondition from exc
        if activity.kind != "assessment":
            raise UnavailableCondition
        score = assessment_payload(db, user, classroom_id, activity)["score"]
        return score is not None and value.get("min", 0) <= score <= value.get("max", 100)
    if key in {"chapter", "all_quests_complete"}:
        if key == "chapter":
            if not _chapter_available(db, user, classroom, value["id"]):
                return False
            if value["status"] == "available":
                return True
            ids = chapter_completion_requirements(db, classroom_id, value["id"]).get(value["id"], set())
        else:
            ids = {
                item.id
                for item in db.scalars(select(QuestDefinition).where(course_clause(db, QuestDefinition, classroom_id)))
                if quest_visible(item, user, classroom, db) and chapter_available(db, user, classroom_id, item.chapter_id)
            }
        completed = set(
            db.scalars(
                select(PlayerQuest.quest_id).where(
                    PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id, PlayerQuest.status == "complete"
                )
            )
        )
        return bool(ids) and ids.issubset(completed)
    quest_id = value["quest_id"] if key == "goal" else value["id"] if key == "quest" else story.quest_id
    definition = (getattr(story, "preview_definitions", {}).get(quest_id) or db.get(QuestDefinition, quest_id)) if quest_id else None
    if not quest_visible(definition, user, classroom, db):
        raise UnavailableCondition
    if key != "quest" and (value["id"] if key == "goal" else value) not in {node["id"] for node in definition.definition["nodes"]}:
        raise UnavailableCondition
    if not _chapter_available(db, user, classroom, definition.chapter_id):
        return False
    instance = db.scalar(
        select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id, PlayerQuest.quest_id == quest_id)
    )
    if key == "quest":
        return (instance.status if instance else "active") == value["status"]
    states = {row.node_id: row for row in db.scalars(select(NodeState).where(NodeState.player_quest_id == instance.id))} if instance else {}
    statuses = node_statuses(definition.definition, states)
    goal_id = value["id"] if key == "goal" else value
    expected = value["status"] if key == "goal" else "complete" if key == "goal_complete" else "ready"
    return statuses.get(goal_id) == expected


def activity_completed(db, user, classroom_id, activity_id):
    """Reuse the formal evidence validator; UI dismissal never completes work."""
    from app.course_content.evidence import validate_learning_evidence
    from app.models import LearningRecord

    try:
        activity = authorize_activity(db, user, classroom_id, activity_id)
        kind = {"lesson": "STUDY_LESSON", "reading": "STUDY_LESSON", "practice": "COMPLETE_PRACTICE", "assessment": "PASS_ASSESSMENT"}.get(
            activity.kind
        )
        if not kind:
            return False
        evidence = {}
        if kind == "STUDY_LESSON":
            record = db.scalar(
                select(LearningRecord)
                .where(LearningRecord.user_id == user.id, LearningRecord.classroom_id == classroom_id, LearningRecord.activity_id == activity_id)
                .order_by(LearningRecord.created_at.desc())
            )
            evidence = {"record_id": record.id} if record else {}
        validate_learning_evidence(db, user, classroom_id, {"type": kind, "activity_id": activity_id}, evidence)
        return True
    except HTTPException as exc:
        if exc.status_code in {403, 404}:
            raise UnavailableCondition from exc
        if exc.status_code in {409, 422, 503}:
            return False
        raise
