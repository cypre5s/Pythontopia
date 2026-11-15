"""Developer experience navigation, stage and checkpoint contracts."""

from typing import Annotated, Literal
from uuid import UUID

from fastapi import Depends
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy.orm import Session

from app.auth import current_user
from app.db import get_db
from app.models import User

Db = Annotated[Session, Depends(get_db)]


Actor = Annotated[User, Depends(current_user)]


class Scope(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int = Field(gt=0)


class ChapterInput(Scope):
    chapter_id: int = Field(gt=0)


class OpenInput(Scope):
    kind: Literal["material", "activity"]
    target: str = Field(min_length=1, max_length=100)
    page_no: int = Field(default=1, ge=1)
    question_no: int = Field(default=1, ge=1)
    video_time: float = Field(default=0, ge=0, le=86400, allow_inf_nan=False)


class StageInput(Scope):
    quest_id: str = Field(max_length=100)
    node_id: str = Field(max_length=100)
    choices: dict[str, str] = Field(default_factory=dict, max_length=100)
    any_dependencies: dict[str, str] = Field(default_factory=dict, max_length=100)
    expected: str | None = Field(default=None, max_length=64)


class SaveInput(Scope):
    name: str = Field(min_length=1, max_length=80)
    client_state: dict = Field(default_factory=dict)
    request_id: UUID | None = None


class RestoreInput(Scope):
    expected: str = Field(min_length=64, max_length=64)
