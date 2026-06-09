from __future__ import annotations

from typing import cast

from fastapi import APIRouter, Depends, Request

from pythontopia_graph.agents.npc_dialog_agent import NpcDialogAgent
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.schemas.npc_dialog import NpcDialogInput, NpcDialogOutput

router = APIRouter()


def _provider(request: Request) -> LlmProvider:
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is not None:
        return cast(LlmProvider, provider)
    return provider_from_env()


@router.post("/agents/npc/dialog", response_model=NpcDialogOutput)
async def npc_dialog(
    body: NpcDialogInput,
    provider: LlmProvider = Depends(_provider),
) -> NpcDialogOutput:
    return await NpcDialogAgent(provider).reply(body)
