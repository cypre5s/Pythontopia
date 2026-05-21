"""HTTP route for the DebugAgent."""

from __future__ import annotations

from fastapi import APIRouter

from pythontopia_graph.agents.debug_agent import DebugAgent
from pythontopia_graph.schemas.debug import DebugAgentInput, DebugAgentOutput

router = APIRouter()


@router.post("/agents/debug/analyze", response_model=DebugAgentOutput)
def analyse_failure(body: DebugAgentInput) -> DebugAgentOutput:
    return DebugAgent().analyse(body)
