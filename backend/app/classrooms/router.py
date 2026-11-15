"""Classroom and member HTTP routes and authenticated dependencies."""

from typing import Annotated

from fastapi import APIRouter, Depends
from fastapi.security import HTTPAuthorizationCredentials
from sqlalchemy.orm import Session

from app.auth import bearer, current_user, require_teacher
from app.classrooms.contracts import ClassroomInput, JoinInput
from app.classrooms.service import create_classroom as _create_classroom
from app.classrooms.service import join_classroom as _join_classroom
from app.classrooms.service import list_classrooms as _list_classrooms
from app.classrooms.service import open_town_session as _open_town_session
from app.classrooms.service import remove_member as _remove_member
from app.db import get_db
from app.models import User

router = APIRouter(tags=["classrooms"])


@router.get("/classrooms")
def list_classrooms(db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    return _list_classrooms(db=db, user=user)


@router.post("/classrooms", status_code=201)
def create_classroom(body: ClassroomInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    return _create_classroom(body=body, db=db, user=user)


@router.post("/teacher/classrooms/{classroom_id}/town-session")
def open_town_session(
    classroom_id: int,
    db: Annotated[Session, Depends(get_db)],
    user: Annotated[User, Depends(require_teacher)],
    credentials: Annotated[HTTPAuthorizationCredentials, Depends(bearer)],
):
    # Serialize initial creation against another launch of this classroom.
    return _open_town_session(classroom_id=classroom_id, db=db, user=user, credentials=credentials)


@router.post("/classrooms/join")
def join_classroom(body: JoinInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    return _join_classroom(body=body, db=db, user=user)


@router.delete("/teacher/classrooms/{classroom_id}/members/{student_id}")
def remove_member(classroom_id: int, student_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    return _remove_member(classroom_id=classroom_id, student_id=student_id, db=db, user=user)
