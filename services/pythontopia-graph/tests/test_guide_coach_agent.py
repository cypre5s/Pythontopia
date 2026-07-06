from __future__ import annotations

from collections.abc import AsyncIterator

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


def _problem() -> WorkflowProblemContext:
    return WorkflowProblemContext(
        problem_id=42,
        title="循环求和",
        statement="输入 n，输出 1 到 n 的和。",
        kc_codes=["PY43-KC-016"],
    )


def _ctx(
    *,
    requested_event: str = "ERROR_FEEDBACK",
    verdict: str = "WA",
    consecutive_errors: int = 1,
    allowed_events: list[str] | None = None,
) -> CoachContext:
    return CoachContext(
        requested_event=requested_event,
        allowed_events=allowed_events
        or ["ERROR_FEEDBACK", "EXECUTION_TRACE", "KNOWLEDGE_REVIEW", "VISUALIZE", "PARSONS"],
        state=TutorStudentState(
            session_id="wf-1",
            user_id=1,
            problem_id=42,
            phase="CODING",
            latest_verdict=verdict,
            consecutive_errors=consecutive_errors,
        ),
        problem=_problem(),
        diagnosis=WorkflowDiagnosisContext(
            submission_id=7,
            verdict=verdict,
            code="n=int(input())\nprint(n)",
            passed_cases=2,
            total_cases=5,
            consecutive_errors=consecutive_errors,
        ),
        profile_brief="知识基础: 入门; 易错: off-by-one",
    )


@pytest.mark.asyncio
async def test_wa_prefers_execution_trace_before_more_code() -> None:
    agent = GuideCoachAgent(EmptyProvider())

    decision = await agent.decide(_ctx())

    assert decision.next_event in {"EXECUTION_TRACE", "ERROR_FEEDBACK"}
    assert decision.next_event != "SKELETON"
    assert decision.hint_level <= 2
    assert decision.tool_name in {"execution_trace", "diagnose_submission"}
    assert decision.reason_summary


@pytest.mark.asyncio
async def test_ce_prefers_error_diagnosis() -> None:
    agent = GuideCoachAgent(EmptyProvider())

    decision = await agent.decide(
        _ctx(verdict="CE", allowed_events=["ERROR_FEEDBACK", "KNOWLEDGE_REVIEW"])
    )

    assert decision.next_event == "ERROR_FEEDBACK"
    assert decision.tool_name == "diagnose_submission"


@pytest.mark.asyncio
async def test_repeated_wa_lowers_cognitive_load() -> None:
    agent = GuideCoachAgent(EmptyProvider())

    decision = await agent.decide(
        _ctx(consecutive_errors=3, allowed_events=["ERROR_FEEDBACK", "VISUALIZE", "PARSONS"])
    )

    assert decision.next_event in {"VISUALIZE", "PARSONS"}
    assert decision.hint_level <= 3
    assert "降低" in decision.action_goal or "负担" in decision.reason_summary


@pytest.mark.asyncio
async def test_ac_after_retries_prefers_post_ac_review() -> None:
    agent = GuideCoachAgent(EmptyProvider())

    decision = await agent.decide(
        _ctx(
            requested_event="AC_REVIEW",
            verdict="AC",
            consecutive_errors=2,
            allowed_events=["AC_REVIEW", "TRANSFER"],
        )
    )

    assert decision.next_event == "AC_REVIEW"
    assert decision.tool_name == "post_ac_review"
    assert decision.allowed_followups[0].event == "TRANSFER"


@pytest.mark.asyncio
async def test_fallback_never_selects_outside_allowed_events() -> None:
    agent = GuideCoachAgent(EmptyProvider())

    decision = await agent.decide(
        _ctx(requested_event="SKELETON", allowed_events=["READING", "IDEATING"])
    )

    assert decision.next_event in {"READING", "IDEATING"}
