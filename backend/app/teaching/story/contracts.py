"""Story interaction request contracts."""

from pydantic import BaseModel, ConfigDict, Field, model_validator


class InteractionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    npc_key: str | None = Field(default=None, min_length=1, max_length=80)
    interaction_id: str | None = Field(default=None, min_length=1, max_length=80)

    @model_validator(mode="after")
    def target(self):
        if (self.npc_key is None) == (self.interaction_id is None):
            raise ValueError("必须指定一个NPC或地图交互入口")
        return self


class AdvanceInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    event_id: str = Field(min_length=1, max_length=100)
    revision: int = Field(ge=0)
    choice: str | None = Field(default=None, max_length=100)


class ReturnInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    event_id: str = Field(min_length=1, max_length=100)
    revision: int = Field(ge=0)
