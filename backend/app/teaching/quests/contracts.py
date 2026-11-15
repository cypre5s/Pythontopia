"""Quest, movement and world interaction request contracts."""

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.teaching.quests.validation import DIALOGUE_EVIDENCE_ID_MAX_LENGTH


class EventEvidence(BaseModel):
    model_config = ConfigDict(extra="forbid")
    npc_key: str | None = Field(default=None, max_length=80)
    dialogue_id: str | None = Field(default=None, max_length=DIALOGUE_EVIDENCE_ID_MAX_LENGTH)
    choice: str | None = Field(default=None, max_length=80)
    object_id: str | None = Field(default=None, max_length=100)
    map_key: str | None = Field(default=None, max_length=40)
    tile_x: int | None = Field(default=None, ge=0, le=49)
    tile_y: int | None = Field(default=None, ge=0, le=39)
    record_id: str | None = Field(default=None, max_length=100)
    submission_id: str | None = Field(default=None, max_length=100)


class EventInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    event_id: str = Field(min_length=8, max_length=100)
    quest_id: str = Field(min_length=1, max_length=100)
    node_id: str = Field(min_length=1, max_length=100)
    evidence: dict = Field(default_factory=dict)

    @field_validator("evidence")
    @classmethod
    def validate_payload(cls, value):
        return EventEvidence.model_validate(value).model_dump(exclude_none=True)


class DefinitionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    definition: dict
