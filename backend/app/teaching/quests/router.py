"""Quest HTTP routes and authenticated dependencies."""

from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth import current_user, require_teacher
from app.db import get_db
from app.models import User
from app.teaching.quests.authoring import create_quest as _create_quest
from app.teaching.quests.authoring import list_quests as _list_quests
from app.teaching.quests.authoring import validate_quest as _validate_quest
from app.teaching.quests.contracts import DefinitionInput, EventInput
from app.teaching.quests.events import event as _event

router = APIRouter(tags=["quests"])


@router.post("/events")
def event(body: EventInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    return _event(body=body, db=db, user=user)


@router.post("/teacher/quests/validate")
def validate_quest(
    body: DefinitionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None
):
    return _validate_quest(body=body, db=db, user=user, classroom_id=classroom_id)


@router.post("/teacher/quests", status_code=201)
def create_quest(
    body: DefinitionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None
):
    return _create_quest(body=body, db=db, user=user, classroom_id=classroom_id)


@router.get("/teacher/quests")
def list_quests(db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)], classroom_id: int | None = None):
    return _list_quests(db=db, user=user, classroom_id=classroom_id)
