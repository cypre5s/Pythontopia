"""EvaluationNarrativeAgent HTTP 路由。

* ``POST /agents/evaluation/narrative`` → ``EvaluationNarrativeResponse``；审查失败 → HTTP 422 结构化 detail。
"""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, status

from pythontopia_graph.agents.evaluation_narrative_agent import EvaluationNarrativeAgent
from pythontopia_graph.schemas.evaluation import (
    EvaluationNarrativeRequest,
    EvaluationNarrativeResponse,
)

router = APIRouter()


@router.post("/agents/evaluation/narrative", response_model=EvaluationNarrativeResponse)
def narrate_evaluation(body: EvaluationNarrativeRequest) -> EvaluationNarrativeResponse:
    response = EvaluationNarrativeAgent().narrate(body)
    if response.status == "FAILED" and not response.safety_result.passed:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail={
                "code": "EVALUATION_NARRATIVE_SAFETY_FAILED",
                "message": "; ".join(response.safety_result.reasons),
            },
        )
    if response.status == "FAILED" and not response.grounding_result.passed:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail={
                "code": "EVALUATION_NARRATIVE_GROUNDING_FAILED",
                "message": "; ".join(response.grounding_result.reasons),
                "unknown_kc": response.grounding_result.unknown_kc,
                "unknown_numbers": response.grounding_result.unknown_numbers,
            },
        )
    return response
