"""HTTP route exposing the Phase 3 student learning loop state."""

from __future__ import annotations

from fastapi import APIRouter

from pythontopia_graph.runtime.student_learning_loop import (
    StudentLearningLoop,
    StudentLearningLoopRequest,
    StudentLearningLoopResult,
)

router = APIRouter()


@router.post(
    "/runtime/student-learning-runs",
    response_model=StudentLearningLoopResult,
)
def run_student_learning_loop(
    body: StudentLearningLoopRequest,
) -> StudentLearningLoopResult:
    return StudentLearningLoop().run(body)
