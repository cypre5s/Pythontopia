"""Explicit development-only cleanup of unused legacy imports, never learner data."""

import os

from sqlalchemy import delete, select

from . import integration_models  # noqa: F401 — register assistant evidence references
from .db import Base
from .models import (
    Activity, ActivityQuestion, AssessmentAttempt, Chapter, Question,
    QuestionChapter, QuestionKnowledgePoint, QuestionSecret, QuestDefinition,
    StoryDefinition, StorySession,
)


def _strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for child in value.values():
            yield from _strings(child)
    elif isinstance(value, list):
        for child in value:
            yield from _strings(child)


def _foreign_references(db, target, excluded):
    """Include integration tables, so future evidence references fail closed."""
    references = set()
    for table in Base.metadata.sorted_tables:
        if table.name in excluded:
            continue
        for foreign in table.foreign_keys:
            if foreign.column.table.name == target:
                references.update(db.scalars(select(foreign.parent).where(foreign.parent.is_not(None))))
    return references


def cleanup_content(db, *, apply=False, course_id=43):
    """Preview by default; only remove known legacy archives without references.

    Submissions (including runs), assessment snapshots, activity progress, learning
    records, assistant sessions and authored task/story references all protect data.
    Current or teacher-owned content is never a deletion candidate.
    """
    if apply and os.getenv("REBORN_ENV") != "development":
        raise ValueError("实际清理仅允许显式REBORN_ENV=development；默认仅预览")
    chapters = set(db.scalars(select(Chapter.id).where(Chapter.course_id == course_id, Chapter.owner_id.is_(None))))
    activity_query = select(Activity).where(Activity.chapter_id.in_(chapters), Activity.owner_id.is_(None))
    question_query = select(Question).where(Question.owner_id.is_(None), Question.source_type == "imported")
    if apply:
        activity_query, question_query = activity_query.with_for_update(), question_query.with_for_update()
    activities = list(db.scalars(activity_query))
    archive_ids = {row.id for row in activities if row.content.get("published") is False and (
        row.id.startswith("archive-") or row.title == "未发布的归档题目")}
    tagged_questions = set(db.scalars(select(QuestionChapter.question_id).where(QuestionChapter.chapter_id.in_(chapters))))
    questions = [row for row in db.scalars(question_query) if row.id in tagged_questions
                 and row.source.get("kind") in {"course_export", "manually_authored_from_course"}]
    activity_refs = _foreign_references(db, "activities", {"activity_questions"})
    activity_refs.update(db.scalars(select(StorySession.pending_activity_id).where(StorySession.pending_activity_id.is_not(None))))
    for definition in db.scalars(select(QuestDefinition)):
        activity_refs.update(_strings(definition.definition))
    for story in db.scalars(select(StoryDefinition)):
        activity_refs.update(_strings(story.content))
    deletable_activities = archive_ids - activity_refs
    question_refs = _foreign_references(db, "questions", {
        "activity_questions", "question_secrets", "question_chapters", "question_knowledge_points",
    })
    for attempt in db.scalars(select(AssessmentAttempt)):
        question_refs.update(attempt.question_ids)
    for relation in db.scalars(select(ActivityQuestion)):
        if relation.activity_id not in deletable_activities:
            question_refs.add(relation.question_id)
    deletable_questions = {row.id for row in questions} - question_refs
    report = {
        "mode": "apply" if apply else "dry-run", "course_id": course_id,
        "questions": sorted(deletable_questions), "activities": sorted(deletable_activities),
        "skipped_questions": sorted({row.id for row in questions} & question_refs),
        "skipped_activities": sorted(archive_ids & activity_refs),
    }
    if apply:
        try:
            db.execute(delete(ActivityQuestion).where(ActivityQuestion.activity_id.in_(deletable_activities)))
            for model in (QuestionSecret, QuestionChapter, QuestionKnowledgePoint):
                db.execute(delete(model).where(model.question_id.in_(deletable_questions)))
            db.execute(delete(Question).where(Question.id.in_(deletable_questions)))
            db.execute(delete(Activity).where(Activity.id.in_(deletable_activities)))
            db.commit()
        except Exception:
            db.rollback()
            raise
    return report
