"""Authorize quest visibility within the selected course and classroom."""

from fastapi import HTTPException

from app.course_content.access import authorize_classroom
from app.course_scope import belongs_to_course
from app.models import QuestDefinition


def quest_visible(definition, user, classroom, db):
    return bool(belongs_to_course(db, definition, classroom.id) and (user.role == "admin" or definition.owner_id in {None, classroom.teacher_id}))


def authorize_quest(db, user, classroom_id, quest_id):
    classroom = authorize_classroom(db, user, classroom_id)
    definition = db.get(QuestDefinition, quest_id)
    if not definition:
        raise HTTPException(404, "任务定义不存在")
    if not quest_visible(definition, user, classroom, db):
        raise HTTPException(403, "任务不属于当前班级教师")
    return definition
