"""Validate ordered activity questions and server-verifiable reading requirements."""

from fastapi import HTTPException

from app.content_relations import activity_questions, material_in_chapter, question_chapter_ids
from app.learning_rules import required_pages, validate_learning, video_watched
from app.models import (
    Material,
)


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


def learning_material(db, activity):
    material = db.get(Material, activity.material_id) if activity.material_id else None
    if material and (material.course_instance_id != activity.course_instance_id or not material_in_chapter(db, material.id, activity.chapter_id)):
        raise HTTPException(409, "学习资料必须属于活动章节")
    try:
        validate_learning(activity.content, activity.kind, material, resolve=True)
    except ValueError as exc:
        raise HTTPException(409, str(exc)) from exc
    return material


def reading_completed(activity, progress, material=None):
    video = activity.content.get("learning", {}).get("video")
    if video:
        return bool(progress and video_watched(progress.media_progress or {}, activity.material_id) >= video["minimum_seconds"])
    required = required_pages(activity, material)
    return bool(progress and required and set(required).issubset(progress.visited_pages))
