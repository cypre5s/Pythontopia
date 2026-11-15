"""Player position and portal transition request contracts."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.world_config import validate_point


class PositionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    map_key: str = Field(min_length=1, max_length=40)
    tile_x: int = Field(ge=0)
    tile_y: int = Field(ge=0)
    direction: Literal["up", "down", "left", "right"] = "down"

    @model_validator(mode="after")
    def validate_map_position(self):
        validate_point(self.map_key, self.tile_x, self.tile_y)
        return self


class TransitionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    portal_id: str = Field(min_length=1, max_length=100)
