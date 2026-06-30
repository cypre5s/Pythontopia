"""HTTP route exposing the Phase 5 resource lifecycle loop state."""

from __future__ import annotations

from fastapi import APIRouter

from pythontopia_graph.runtime.resource_lifecycle_loop import (
    ResourceLifecycleLoop,
    ResourceLifecycleLoopRequest,
    ResourceLifecycleLoopResult,
)

router = APIRouter()


@router.post(
    "/runtime/resource-lifecycle-runs",
    response_model=ResourceLifecycleLoopResult,
)
def run_resource_lifecycle_loop(
    body: ResourceLifecycleLoopRequest,
) -> ResourceLifecycleLoopResult:
    return ResourceLifecycleLoop().run(body)
