"""HTTP route exposing the Phase 4 code debug loop state."""

from __future__ import annotations

from fastapi import APIRouter

from pythontopia_graph.runtime.code_debug_loop import (
    CodeDebugLoop,
    CodeDebugLoopRequest,
    CodeDebugLoopResult,
)

router = APIRouter()


@router.post("/runtime/code-debug-runs", response_model=CodeDebugLoopResult)
def run_code_debug_loop(body: CodeDebugLoopRequest) -> CodeDebugLoopResult:
    return CodeDebugLoop().run(body)
