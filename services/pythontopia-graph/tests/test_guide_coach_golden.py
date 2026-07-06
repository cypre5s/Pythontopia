from __future__ import annotations

import json
from collections.abc import AsyncIterator
from pathlib import Path
from typing import Any

import pytest

from pythontopia_graph.agents.guide_coach_agent import CoachContext, GuideCoachAgent
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.tutor_workflow import (
    TutorStudentState,
    WorkflowDiagnosisContext,
    WorkflowProblemContext,
)


class EmptyProvider(LlmProvider):
    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        return LlmResponse(content_json=None)

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        yield ""


def _scenarios() -> list[dict[str, Any]]:
    path = Path(__file__).parent / "fixtures" / "guide_coach_golden.json"
    return json.loads(path.read_text(encoding="utf-8"))


@pytest.mark.asyncio
@pytest.mark.parametrize("scenario", _scenarios(), ids=lambda s: s["name"])
async def test_guide_coach_golden_decisions(scenario: dict[str, Any]) -> None:
    verdict = scenario["verdict"]
    requested_event = "AC_REVIEW" if verdict == "AC" else "ERROR_FEEDBACK"
    agent = GuideCoachAgent(EmptyProvider())
    decision = await agent.decide(
        CoachContext(
            requested_event=requested_event,
            allowed_events=scenario["allowed_events"],
            state=TutorStudentState(
                session_id=f"wf-{scenario['name']}",
                user_id=1,
                problem_id=42,
                phase="CODING",
                latest_verdict=verdict,
                consecutive_errors=scenario["consecutive_errors"],
            ),
            problem=WorkflowProblemContext(
                problem_id=42,
                title="循环求和",
                statement="输入 n，输出 1 到 n 的和。",
                kc_codes=["PY43-KC-016"],
            ),
            diagnosis=WorkflowDiagnosisContext(
                submission_id=7,
                verdict=verdict,
                code="n=int(input())\nprint(n)",
                passed_cases=scenario.get("passed_cases", 0),
                total_cases=scenario.get("total_cases", 0),
                consecutive_errors=scenario["consecutive_errors"],
            ),
        )
    )

    assert decision.next_event in scenario["expected_events"]
    assert decision.next_event not in scenario["forbidden_events"]
    assert decision.hint_level <= 3
    assert decision.reason_summary
    assert "chain-of-thought" not in decision.reason_summary.lower()
    assert "逐步推理" not in decision.reason_summary
