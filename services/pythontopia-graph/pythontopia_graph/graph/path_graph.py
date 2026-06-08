"""路径规划编排：request → planner → safety → response。"""

from __future__ import annotations

from pythontopia_graph.agents.path_planner_agent import PathPlannerAgent
from pythontopia_graph.schemas.path import (
    PathPlannerGroundingResult,
    PathPlannerRequest,
    PathPlannerResponse,
    PathPlannerSafetyResult,
    PlannerNodeDraft,
)


class PathGraph:
    """PathPlannerAgent 封装：规划后安全校验与状态回写。"""

    def __init__(self, *, agent: PathPlannerAgent | None = None) -> None:
        self._agent = agent or PathPlannerAgent()

    def run(self, request: PathPlannerRequest) -> PathPlannerResponse:
        response = self._agent.plan(request)
        safety = self._safety_check(response.nodes)
        if not safety.passed:
            return response.model_copy(
                update={
                    "safety_result": safety,
                    "status": "FAILED",
                }
            )
        return response

    def _safety_check(
        self, nodes: list[PlannerNodeDraft]
    ) -> PathPlannerSafetyResult:
        reasons: list[str] = []
        for node in nodes:
            for forbidden in ("standard_answer", "answer_key", "hidden_test", "标准答案", "完整答案"):
                if forbidden in node.reason_summary.lower() or forbidden in node.reason_summary:
                    reasons.append(f"PATH_LEAK:{forbidden}:{node.kc_code}")
        return PathPlannerSafetyResult(passed=not reasons, reasons=reasons)


__all__ = [
    "PathGraph",
    "PathPlannerRequest",
    "PathPlannerResponse",
    "PathPlannerGroundingResult",
]
