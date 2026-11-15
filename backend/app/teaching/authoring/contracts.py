"""Teaching editor request contracts."""

from pydantic import BaseModel, ConfigDict, Field


class AuthoringInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    expected_revision: int = Field(ge=1)
    npcs: list[dict] = Field(max_length=100)
    flows: list[dict] = Field(max_length=500)
    quests: list[dict] | None = Field(default=None, max_length=500)
    configuration: dict


class PreviewInput(AuthoringInput):
    student_id: int
    entry: str = Field(min_length=1, max_length=100)


class PublishInput(AuthoringInput):
    confirmation_token: str | None = Field(default=None, pattern=r"^[a-f0-9]{64}$")
