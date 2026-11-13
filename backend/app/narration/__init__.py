"""Per-page narration drafts for course materials.

Isolated from the rest of the backend on purpose: the package owns its table, its
state machine and its own authoring-model client, so course authoring can evolve
without touching student-facing content or the assistant runtime.
"""
from .router import router, student_router

__all__ = ["router", "student_router"]
