"""Validate ordered activity questions and server-verifiable reading requirements."""

from fastapi import HTTPException

from app.content_relations import activity_questions, question_chapter_ids


def learning_questions(db, activity, teacher_id=None):
    """Use the complete ordered binding, and reject invalid papers as a whole."""
    questions = activity_questions(db, activity)
    owner_id = teacher_id if teacher_id is not None else activity.owner_id
    for question in questions:
        if question.course_instance_id != activity.course_instance_id:
            raise HTTPException(403, "活动题目不属于当前班级课程")
        if question.status != "ready":
            raise HTTPException(409, "活动存在尚未就绪的题目，请联系教师")
        if activity.chapter_id not in question_chapter_ids(db, question.id):
            raise HTTPException(409, "活动题目尚未关联当前章节")
        if question.owner_id not in {None, owner_id}:
            raise HTTPException(403, "活动题目不属于当前班级教师")
    return questions


def reading_completed(activity, progress, materials):
    from app.course_content.resources import reading_progress
    return reading_progress(activity, progress, materials)["completed"]
