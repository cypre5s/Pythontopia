from __future__ import annotations

import asyncio

import pytest
from autogen_core import CancellationToken
from fastapi import HTTPException

from pythontopia_graph.agents import tutor_agent as tutor_agent_module
from pythontopia_graph.agents.tutor_agent import TutorAgent
from pythontopia_graph.graph import tutor_workflow_session as session_store
from pythontopia_graph.graph.autogen_tutor_runtime import (
    _PRIVATE_IMAGE_DATA_KEY,
    _encode_state,
    _decode_state,
    run_autogen_tutor_workflow,
    run_autogen_tutor_call,
    validate_autogen_tutor_checkpoint_scope,
)
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher
from pythontopia_graph.llm.provider import LlmResponse
from pythontopia_graph.routes.tutor_workflow import (
    WorkflowRunEnvelope,
    tutor_workflow_run_stream,
)
from pythontopia_graph.routes import tutor_workflow as tutor_workflow_route
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
async def test_autogen_single_call_preserves_nonserializable_result() -> None:
    expected = object()
    called = False

    async def call():
        nonlocal called
        called = True
        return expected

    result = await run_autogen_tutor_call("TutorAgent", call)

    assert called is True
    assert result is expected


@pytest.mark.asyncio
async def test_nonstream_tutor_ask_uses_fixed_autogen_stage(monkeypatch) -> None:
    stages: list[str] = []
    original = tutor_agent_module.run_autogen_tutor_call
    chat_calls = 0

    async def observe(stage_name, call):
        stages.append(stage_name)
        return await original(stage_name, call)

    class Provider:
        async def chat(self, _messages, **_kwargs):
            nonlocal chat_calls
            chat_calls += 1
            return LlmResponse(content_text="基于通识知识，这里是解释。")

    monkeypatch.setattr(tutor_agent_module, "run_autogen_tutor_call", observe)
    response = await TutorAgent(Provider()).ask("画个变量示意图", "", [], [])  # type: ignore[arg-type]

    assert stages == ["TutorAgent"]
    assert chat_calls == 1
    assert response.answer_md == "基于通识知识，这里是解释。"
    assert response.visual_aids == []


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
async def test_private_chat_images_reach_only_card_stage_and_stay_out_of_state() -> None:
    image = "data:image/png;base64,iVBORw0KGgo="
    request = WorkflowRunRequest(
        session_id="autogen-image-session",
        user_id=7,
        event="CHAT",
        problem=WorkflowProblemContext(problem_id=12, title="输入输出"),
        event_data={"message": "请看图", "attachment_ids": ["e780cb26-4b20-4e69-a043-750eeddf4051"]},
    )
    encoded = _encode_state({"request": request})
    assert image not in encoded

    seen_stages: list[tuple[str, bool, bool]] = []
    seen_images: list[list[str]] = []
    encoded_card_state: list[str] = []

    async def ordinary_stage(state):
        seen_stages.append(
            ("coach_decide", "images" in state["request"].event_data, _PRIVATE_IMAGE_DATA_KEY in state)
        )
        return state

    async def card_stage(state):
        seen_stages.append(
            ("generate_card", "images" in state["request"].event_data, _PRIVATE_IMAGE_DATA_KEY in state)
        )
        seen_images.append(state[_PRIVATE_IMAGE_DATA_KEY])
        encoded_card_state.append(_encode_state(state))
        return state

    async def resolve_images(stage_request):
        assert stage_request.session_id == request.session_id
        assert stage_request.event_data["attachment_ids"] == request.event_data["attachment_ids"]
        return [image]

    result = await run_autogen_tutor_workflow(
        {"request": request},
        [("coach_decide", ordinary_stage), ("generate_card", card_stage)],
        private_image_resolver=resolve_images,
    )

    assert seen_stages == [("coach_decide", False, False), ("generate_card", False, True)]
    assert seen_images == [[image]]
    assert "images" not in result["request"].event_data
    assert image not in result["request"].model_dump_json()
    assert image not in encoded_card_state[0]
    assert image not in _encode_state(result)


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
async def test_autogen_stage_checkpoint_restores_native_team_and_resumes_next_stage() -> None:
    calls: list[str] = []
    checkpoints: list[dict] = []

    async def first_stage(state):
        calls.append("first")
        return {**state, "first_stage_done": True}

    async def second_stage(state):
        calls.append("second")
        assert state["first_stage_done"] is True
        return {**state, "runtime_state": "SUCCEEDED"}

    async def save_checkpoint(checkpoint):
        checkpoints.append(checkpoint)

    stages = [("first", first_stage), ("second", second_stage)]
    await run_autogen_tutor_workflow(
        {"runtime_state": "QUEUED"},
        stages,
        on_checkpoint=save_checkpoint,
    )

    assert calls == ["first", "second"]
    assert [checkpoint["stage_name"] for checkpoint in checkpoints] == ["first", "second"]
    assert checkpoints[0]["stage_index"] == 0
    assert _decode_state(checkpoints[0]["workflow_state"])["first_stage_done"] is True
    assert checkpoints[0]["team_state"]

    calls.clear()
    resumed = await run_autogen_tutor_workflow(
        {"runtime_state": "QUEUED"},
        stages,
        restore_checkpoint=checkpoints[0],
    )

    assert calls == ["second"]
    assert resumed["runtime_state"] == "SUCCEEDED"


def test_autogen_checkpoint_cannot_restore_a_different_request_scope() -> None:
    original = _request()
    checkpoint = {
        "stage_index": 0,
        "stage_name": "validate_transition",
        "team_state": {"agent_states": []},
        "workflow_state": _encode_state({"request": original}),
    }

    with pytest.raises(ValueError, match="does not belong to this request"):
        validate_autogen_tutor_checkpoint_scope(
            checkpoint,
            original.model_copy(update={"session_id": "other-owner-session"}),
        )


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


@pytest.mark.asyncio
async def test_same_tutor_session_rejects_a_second_active_run() -> None:
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

    session_id = "autogen-single-active-run"
    await session_store.reset_session(7, session_id)
    first = await tutor_workflow_run_stream(
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
    iterator = first.body_iterator
    while not started.is_set():
        await asyncio.wait_for(iterator.__anext__(), timeout=1)

    with pytest.raises(HTTPException) as duplicate:
        await tutor_workflow_run_stream(
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
    assert duplicate.value.status_code == 409

    await asyncio.wait_for(iterator.aclose(), timeout=1)
    await asyncio.wait_for(cancelled.wait(), timeout=1)


@pytest.mark.asyncio
async def test_route_persists_each_autogen_stage_checkpoint(monkeypatch) -> None:
    request = _request()
    run_id = "2d4c90d4-20f4-4a1b-90ee-e909e5e6d7b5"
    fingerprint = "a" * 64
    checkpoint = {
        "stage_index": 0,
        "stage_name": "validate_transition",
        "team_state": {"agent_states": []},
        "workflow_state": _encode_state({"request": request}),
    }
    saved: list[tuple[object, ...]] = []

    class FakeGraph:
        def __init__(self, _provider, _evidence_fetcher, *, emit) -> None:
            self.emit = emit

        async def run(self, state, *, on_checkpoint=None, **_kwargs):
            assert state["request"] == request
            assert on_checkpoint is not None
            await on_checkpoint(checkpoint)
            return {
                "runtime_state": "SUCCEEDED",
                "effective_event": "READING",
                "new_phase": "READING",
                "card_key": None,
                "card": None,
                "available_actions": [],
            }

    async def persist(*args) -> None:
        saved.append(args)

    monkeypatch.setattr(tutor_workflow_route, "TutorWorkflowGraph", FakeGraph)
    monkeypatch.setattr(tutor_workflow_route, "persist_tutor_workflow_checkpoint", persist)
    await session_store.reset_session(7, request.session_id)
    response = await tutor_workflow_run_stream(
        WorkflowRunEnvelope(
            **request.model_dump(), run_id=run_id, request_fingerprint=fingerprint
        ),
        provider=object(),  # type: ignore[arg-type]
        evidence_fetcher=StaticEvidenceFetcher([]),
        trusted_user_id=7,
    )

    events = [event async for event in response.body_iterator]
    assert any("event: RUN_FINISHED" in event for event in events)
    assert saved == [(7, request.session_id, run_id, fingerprint, checkpoint)]


@pytest.mark.asyncio
async def test_route_rejects_checkpoint_for_another_problem_before_claiming_session() -> None:
    original = _request()
    checkpoint = {
        "stage_index": 0,
        "stage_name": "validate_transition",
        "team_state": {"agent_states": []},
        "workflow_state": _encode_state({"request": original}),
    }
    changed_request = original.model_copy(
        update={"problem": WorkflowProblemContext(problem_id=13, title="其他题目")}
    )

    with pytest.raises(HTTPException) as rejected:
        await tutor_workflow_run_stream(
            WorkflowRunEnvelope(
                **changed_request.model_dump(),
                run_id="2d4c90d4-20f4-4a1b-90ee-e909e5e6d7b5",
                request_fingerprint="a" * 64,
                restore_autogen_checkpoint=checkpoint,
            ),
            provider=object(),  # type: ignore[arg-type]
            evidence_fetcher=StaticEvidenceFetcher([]),
            trusted_user_id=7,
        )

    assert rejected.value.status_code == 409
