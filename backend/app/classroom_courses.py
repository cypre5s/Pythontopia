"""Classroom course lifecycle; explicit initialization never runs at startup."""

from typing import Annotated, Literal
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from .auth import require_teacher
from .classrooms import own_classroom
from .db import get_db
from .models import CourseInstance, User

router = APIRouter(prefix="/teacher/classrooms/{classroom_id}/course", tags=["classroom-course"])


def instance_payload(instance):
    if instance is None:
        return None
    return {key: getattr(instance, key) for key in ("id", "classroom_id", "package_id", "name", "package_version", "revision")}


@router.get("")
def get_course(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    own_classroom(db, user, classroom_id)
    instance = db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == classroom_id))
    return instance_payload(instance)


@router.get("/ids")
def course_ids(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    from .course_instances import content_mapping
    from .teacher_scope import teacher_instance_id

    key = teacher_instance_id(db, user, classroom_id)
    return content_mapping(db, db.get(CourseInstance, key))


class EmptyCourseInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    confirmed: Literal[True]

    name: str = Field(min_length=1, max_length=200)


@router.post("/empty")
def empty_course(classroom_id: int, body: EmptyCourseInput, db: Annotated[Session, Depends(get_db)],
                 user: Annotated[User, Depends(require_teacher)]):
    from .course_scope import lock_course

    lock_course(db, classroom_id, exclusive=True)
    own_classroom(db, user, classroom_id)
    if not body.name.strip():
        raise HTTPException(422, "课程名称不能为空")
    instance = db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == classroom_id))
    if instance is None:
        instance = CourseInstance(classroom_id=classroom_id, package_id="course-" + uuid4().hex,
                                  name=body.name.strip(), package_version="1", revision=1)
        db.add(instance)
        try:
            db.commit()
        except IntegrityError as exc:
            db.rollback()
            raise HTTPException(409, "班级课程同时更新，请刷新后重试") from exc
    return instance_payload(instance)
