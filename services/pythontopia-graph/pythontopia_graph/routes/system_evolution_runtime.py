"""HTTP route exposing the Phase 7 system evolution loop."""

from __future__ import annotations

from fastapi import APIRouter

from pythontopia_graph.runtime.system_evolution_loop import (
    SystemEvolutionLoop,
    SystemEvolutionLoopRequest,
    SystemEvolutionLoopResult,
)

router = APIRouter()


@router.post(
    "/runtime/system-evolution-runs",
    response_model=SystemEvolutionLoopResult,
)
def run_system_evolution_loop(
    body: SystemEvolutionLoopRequest,
) -> SystemEvolutionLoopResult:
    return SystemEvolutionLoop().run(body)
