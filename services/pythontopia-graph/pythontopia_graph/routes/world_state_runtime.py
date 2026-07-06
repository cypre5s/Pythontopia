"""HTTP route exposing the Phase 6 world state loop."""

from __future__ import annotations

from fastapi import APIRouter

from pythontopia_graph.runtime.world_state_loop import (
    WorldStateLoop,
    WorldStateLoopRequest,
    WorldStateLoopResult,
)

router = APIRouter()


@router.post("/runtime/world-state-runs", response_model=WorldStateLoopResult)
def run_world_state_loop(body: WorldStateLoopRequest) -> WorldStateLoopResult:
    return WorldStateLoop().run(body)
