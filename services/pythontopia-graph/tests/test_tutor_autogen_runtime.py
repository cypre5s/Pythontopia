from __future__ import annotations

import asyncio

import pytest
from autogen_core import CancellationToken

from pythontopia_graph.graph import tutor_workflow_session as session_store
from pythontopia_graph.graph.autogen_tutor_runtime import run_autogen_tutor_workflow
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher
from pythontopia_graph.routes.tutor_workflow import (
    WorkflowRunEnvelope,
    tutor_workflow_run_stream,
)
from pythontopia_graph.schemas.tutor_workflow import (
    GuideCoachDecision,
    TutorStudentState,
    WorkflowAction,
    WorkflowProblemContext,
    WorkflowRunRequest,
)


def _request() -> WorkflowRunRequest:
    return WorkflowRunRequest(
        session_id="autogen-session",
        user_id=7,
        event="READING",
        problem=WorkflowProblemContext(problem_id=12, title="输入输出"),
    )


@pytest.mark.asyncio
async def test_autogen_stages_round_trip_typed_workflow_state() -> None:
    seen: list[str] = []

    async def first_stage(state):
        assert isinstance(state["request"], WorkflowRunRequest)
        assert isinstance(state["evidence_chunks"][0], RagHit)
        seen.append("first")
        return {
            **state,
            "student_state": TutorStudentState(
                session_id="autogen-session",
                user_id=7,
                problem_id=12,
                phase="READING",
            ),
            "coach_decision": GuideCoachDecision(
                next_event="READING",
                tool_name="problem_guide",
                action_goal="帮助读题",
                stop_condition="读懂输入输出",
                reason_summary="先确认题意",
            ),
            "available_actions": [
                WorkflowAction(key="ideate", label="分析思路", event="IDEATING")
            ],
        }

    async def second_stage(state):
        assert isinstance(state["request"], WorkflowRunRequest)
        assert isinstance(state["evidence_chunks"][0], RagHit)
        assert isinstance(state["student_state"], TutorStudentState)
        assert isinstance(state["coach_decision"], GuideCoachDecision)
        assert isinstance(state["available_actions"][0], WorkflowAction)
        seen.append("second")
        return {**state, "runtime_state": "SUCCEEDED"}

    result = await run_autogen_tutor_workflow(
        {
            "request": _request(),
            "evidence_chunks": [
                RagHit(
                    chunk_id="chunk-1",
                    page_id=1,
                    page_no=1,
                    text="变量赋值",
                    score=0.9,
                )
            ],
        },
        [("first", first_stage), ("second", second_stage)],
    )

    assert seen == ["first", "second"]
    assert isinstance(result["request"], WorkflowRunRequest)
    assert isinstance(result["evidence_chunks"][0], RagHit)
    assert isinstance(result["coach_decision"], GuideCoachDecision)
    assert isinstance(result["available_actions"][0], WorkflowAction)


@pytest.mark.asyncio
async def test_autogen_stops_dispatching_stages_after_failure() -> None:
    calls: list[str] = []

    async def fail_stage(state):
        calls.append("validate")
        return {**state, "runtime_state": "FAILED", "failure_bucket": "TRANSITION_VIOLATION"}

    async def forbidden_stage(state):
        calls.append("downstream")
        return state

    result = await run_autogen_tutor_workflow(
        {"runtime_state": "QUEUED"},
        [("validate_transition", fail_stage), ("fetch_evidence", forbidden_stage)],
    )

    assert calls == ["validate"]
    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "TRANSITION_VIOLATION"


@pytest.mark.asyncio
async def test_autogen_workflow_propagates_request_cancellation() -> None:
    started = asyncio.Event()
    cancelled = asyncio.Event()

    async def wait_for_cancel(state):
        started.set()
        try:
            await asyncio.Event().wait()
        except asyncio.CancelledError:
            cancelled.set()
            raise
        return state

    cancellation_token = CancellationToken()
    run_task = asyncio.create_task(
        run_autogen_tutor_workflow(
            {"runtime_state": "QUEUED"},
            [("blocking", wait_for_cancel)],
            cancellation_token=cancellation_token,
        )
    )
    await asyncio.wait_for(started.wait(), timeout=1)
    cancellation_token.cancel()

    with pytest.raises(asyncio.CancelledError):
        await asyncio.wait_for(run_task, timeout=1)
    assert cancelled.is_set()


@pytest.mark.asyncio
async def test_tutor_sse_disconnect_cancels_in_flight_autogen_stage() -> None:
    started = asyncio.Event()
    cancelled = asyncio.Event()

    class BlockingProvider:
        async def chat(self, _messages, **_kwargs):
            started.set()
            try:
                await asyncio.Event().wait()
            except asyncio.CancelledError:
                cancelled.set()
                raise

        async def chat_stream(self, _messages, **_kwargs):
            yield ""

    session_id = "autogen-cancel-sse"
    await session_store.reset_session(7, session_id)
    response = await tutor_workflow_run_stream(
        WorkflowRunEnvelope(
            session_id=session_id,
            user_id=7,
            event="READING",
            problem=WorkflowProblemContext(problem_id=12, title="输入输出"),
        ),
        provider=BlockingProvider(),  # type: ignore[arg-type]
        evidence_fetcher=StaticEvidenceFetcher([]),
        trusted_user_id=7,
    )

    iterator = response.body_iterator
    while not started.is_set():
        await asyncio.wait_for(iterator.__anext__(), timeout=1)

    await asyncio.wait_for(iterator.aclose(), timeout=1)
    await asyncio.wait_for(cancelled.wait(), timeout=1)
    session = await session_store.get_state(7, session_id)
    assert session.seq == 0
    assert session.cards == []
