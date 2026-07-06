"""PathPlanner 图 HTTP 路由。

* ``POST /agents/path/plan`` → ``PathPlannerResponse``；画像未完成 → HTTP 400；安全/引用审查失败 → HTTP 422。
"""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, status

from pythontopia_graph.graph.path_graph import PathGraph
from pythontopia_graph.schemas.path import PathPlannerRequest, PathPlannerResponse

router = APIRouter()


@router.post("/agents/path/plan", response_model=PathPlannerResponse)
def plan_path(body: PathPlannerRequest) -> PathPlannerResponse:
    if not body.profile.has_profile:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"code": "PROFILE_REQUIRED", "message": "请先完成画像访谈再规划路径。"},
        )
    response = PathGraph().run(body)
    if response.status == "FAILED" and not response.safety_result.passed:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail={
                "code": "PATH_SAFETY_FAILED",
                "message": "; ".join(response.safety_result.reasons),
            },
        )
    if response.status == "FAILED" and not response.grounding_result.passed:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail={
                "code": "PATH_GROUNDING_FAILED",
                "message": "; ".join(response.grounding_result.reasons),
            },
        )
    return response
