"""Story runtime HTTP routes and authenticated dependencies."""

from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth import current_user
from app.db import get_db
from app.models import User
from app.teaching.story.commands import advance as _advance
from app.teaching.story.commands import current as _current
from app.teaching.story.commands import end_session as _end_session
from app.teaching.story.commands import interact as _interact
from app.teaching.story.commands import return_activity as _return_activity
from app.teaching.story.contracts import AdvanceInput, InteractionInput, ReturnInput

router = APIRouter(tags=["story"])


@router.post("/story/interactions")
def interact(body: InteractionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    return _interact(body=body, db=db, user=user)


@router.get("/story/current")
def current(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    return _current(classroom_id=classroom_id, db=db, user=user)


@router.post("/story/sessions/{session_id}/end")
def end_session(session_id: str, body: ReturnInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    return _end_session(session_id=session_id, body=body, db=db, user=user)


@router.post("/story/sessions/{session_id}/advance")
def advance(session_id: str, body: AdvanceInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    return _advance(session_id=session_id, body=body, db=db, user=user)


@router.post("/story/sessions/{session_id}/return")
def return_activity(session_id: str, body: ReturnInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    return _return_activity(session_id=session_id, body=body, db=db, user=user)
