"""Revision-protected course-wide assistant settings, separate from character editing."""
from copy import deepcopy
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict
from sqlalchemy import select
from sqlalchemy.orm import Session

from .auth import require_teacher
from .course_assistant import validate_policy
from .course_exchange_models import CoursePackageState
from .db import get_db
from .models import CourseInstance, Npc, User
from .teacher_scope import teacher_instance_id

router = APIRouter(prefix="/teacher/classrooms/{classroom_id}/course/settings", tags=["course-settings"])


class SettingsInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    expected_revision: int
    assistant: dict


def role_choices(db, instance_id):
    return [{"id": npc.stable_id, "name": npc.name}
            for npc in db.scalars(select(Npc).where(Npc.course_instance_id == instance_id).order_by(Npc.name, Npc.key))]


@router.get("")
def read_settings(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    instance_id = teacher_instance_id(db, user, classroom_id)
    state = db.get(CoursePackageState, instance_id)
    return {"revision": db.get(CourseInstance, instance_id).revision,
            "assistant": state.configuration.get("assistant", {}) if state else {}, "roles": role_choices(db, instance_id)}


@router.put("")
def save_settings(classroom_id: int, body: SettingsInput, db: Annotated[Session, Depends(get_db)],
                  user: Annotated[User, Depends(require_teacher)]):
    instance_id = teacher_instance_id(db, user, classroom_id, exclusive=True)
    instance = db.get(CourseInstance, instance_id)
    if instance.revision != body.expected_revision:
        raise HTTPException(409, "课程已修改，请重新加载后再保存；当前草稿仍保留")
    try:
        validate_policy(body.assistant, {"npc": role_choices(db, instance_id)})
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    state = db.get(CoursePackageState, instance_id)
    if state is None:
        state = CoursePackageState(course_instance_id=instance_id, configuration={})
        db.add(state)
    state.configuration = {**deepcopy(state.configuration), "assistant": deepcopy(body.assistant)}
    instance.revision += 1
    revision = instance.revision
    db.commit()
    return {"revision": revision}
