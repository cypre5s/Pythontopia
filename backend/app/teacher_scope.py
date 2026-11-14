"""Explicit classroom ownership for teacher content operations."""

from uuid import uuid4, uuid5, NAMESPACE_URL

from fastapi import HTTPException
from sqlalchemy import select, update

from .models import Classroom, CourseInstance
from .course_scope import lock_course


def teacher_instance_id(db, user, classroom_id, *, exclusive=False):
    if classroom_id is None:
        raise HTTPException(422, "请明确选择班级课程")
    lock_course(db, classroom_id, exclusive=exclusive)
    classroom = db.get(Classroom, classroom_id)
    if classroom is None:
        raise HTTPException(404, "班级不存在")
    if user.role != "admin" and classroom.teacher_id != user.id:
        raise HTTPException(403, "只能管理自己的班级")
    instance = db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == classroom_id))
    if instance is None:
        raise HTTPException(409, "本班尚未导入课程")
    return instance.id


def require_scope(item, instance_id):
    if item is None or getattr(item, "deleted_at", None) is not None or item.course_instance_id != instance_id:
        raise HTTPException(404, "内容不属于当前班级课程")
    return item


def scoped_key(instance_id, kind, stable_id):
    if instance_id is None:
        return stable_id
    return f"c{instance_id}-{kind}-{uuid5(NAMESPACE_URL, str(stable_id)).hex}"


def new_stable_id():
    return uuid4().hex


def commit_content(db, instance_id):
    if instance_id is not None:
        db.execute(update(CourseInstance).where(CourseInstance.id == instance_id).values(revision=CourseInstance.revision + 1))
    db.commit()
