from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class RecalledMemory(BaseModel):
    """后端按 (user, npc) 检索出的长期记忆投影。"""

    event_id: int
    summary: str = Field(default="", max_length=500)
    kc_codes: list[str] = Field(default_factory=list)


class Turn(BaseModel):
    role: Literal["user", "assistant"]
    content: str


class NpcDialogInput(BaseModel):
    npc_key: str
    persona_core: str
    memories: list[RecalledMemory] = Field(default_factory=list)
    user_message: str
    history: list[Turn] = Field(default_factory=list)


class NpcDialogOutput(BaseModel):
    reply_md: str
    cited_event_ids: list[int] = Field(default_factory=list)
    grounded: bool = False
