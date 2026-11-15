"""Authorize teacher ownership of classrooms and enforce unique class codes."""

from fastapi import HTTPException

from app.models import Classroom


def own_classroom(db, user, classroom_id):
    classroom = db.get(Classroom, classroom_id)
    if not classroom:
        raise HTTPException(404, "班级不存在")
    if user.role != "admin" and classroom.teacher_id != user.id:
        raise HTTPException(403, "只能管理自己的班级")
    return classroom


def classroom_payload(item, user, teacher_name=None):
    payload = {"id": item.id, "name": item.name, "teacher_id": item.teacher_id}
    if teacher_name is not None:
        payload["teacher_name"] = teacher_name
    if item.teacher_id == user.id or user.role == "admin":
        payload["join_code"] = item.join_code
    return payload
