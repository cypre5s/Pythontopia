"""World state and movement HTTP routes."""

from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth import current_user
from app.db import get_db
from app.models import User
from app.teaching.quests.state import world as _world
from app.teaching.world.contracts import PositionInput, TransitionInput
from app.teaching.world.movement import transition as _transition
from app.teaching.world.movement import update_position as _update_position

router = APIRouter(tags=["quests"])


@router.get("/world")
def world(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    return _world(classroom_id=classroom_id, db=db, user=user)


@router.put("/world/position")
def update_position(body: PositionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    return _update_position(body=body, db=db, user=user)


@router.post("/world/transition")
def transition(body: TransitionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    return _transition(body=body, db=db, user=user)
