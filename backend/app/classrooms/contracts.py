"""Classroom, member and teacher content input contracts."""

from pydantic import BaseModel, ConfigDict, Field


class ClassroomInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: str = Field(min_length=1, max_length=100)


class JoinInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    join_code: str = Field(min_length=4, max_length=24)
