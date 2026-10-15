from __future__ import annotations

import json

import pytest
from pydantic import ValidationError

from pythontopia_graph.graph.tutor_workflow_graph import TutorWorkflowGraph, validate_transition
from pythontopia_graph.graph import tutor_workflow_graph as tutor_graph_module
from pythontopia_graph.graph import tutor_workflow_session as session_store
from pythontopia_graph.graph.autogen_tutor_runtime import _decode_state, _encode_state
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import StaticEvidenceFetcher
from pythontopia_graph.routes.tutor_workflow import WorkflowRunEnvelope, tutor_workflow_run_stream
from pythontopia_graph.schemas.tutor_workflow import (
    TutorEpisodePlan,
    TutorEpisodePlanPatch,
    WorkflowRunRequest,
    WorkflowProblemContext,
)


class _StubProvider(LlmProvider):
    def __init__(self, content: str = "这张卡的关键是先看清循环边界。") -> None:
        self.content = content
        self.calls = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.calls += 1
        return LlmResponse(content_text=json.dumps({"content": self.content}, ensure_ascii=False))

    async def chat_stream(self, messages, **kwargs):  # type: ignore[override]
        yield ""


def _request(event: str, event_data: dict[str, object] | None = None) -> WorkflowRunRequest:
    return WorkflowRunRequest(
        session_id="wf-task7",
        user_id=7,
        event=event,  # type: ignore[arg-type]
        problem={"problem_id": 42, "title": "循环求和"},
        event_data=event_data or {},
    )


async def _run_graph(
    event: str,
    event_data: dict[str, object] | None = None,
    *,
    current_phase: str = "READING",
    episode_plan: TutorEpisodePlan | None = None,
    episode_patch: TutorEpisodePlanPatch | None = None,
    chat_history: list[dict[str, str]] | None = None,
    provider: LlmProvider | None = None,
) -> dict[str, object]:
    request = WorkflowRunRequest(
        session_id="wf-task7",
        user_id=7,
        event=event,  # type: ignore[arg-type]
        problem=WorkflowProblemContext(problem_id=42, title="循环求和"),
        event_data=event_data or {},
        episode_plan=episode_plan,
        episode_patch=episode_patch,
    )

    async def emit(_event: str, _data: dict[str, object]) -> None:
        return None

    graph = TutorWorkflowGraph(
        provider or _StubProvider(),
        StaticEvidenceFetcher([]),
        emit=emit,
    )
    return await graph.run(
        {
            "request": request,
            "current_phase": current_phase,
            "last_cards": [],
            "chat_history": chat_history or [],
        }
    )


@pytest.mark.parametrize(
    "event",
    [
        "AGENT_FEEDBACK",
        "COMPACT",
        "PLAN_RECOMMEND",
        "PLAN_START",
        "PLAN_RESPONSE",
        "PLAN_STEERING",
    ],
)
def test_task7_events_are_phase_preserving(event: str) -> None:
    assert validate_transition("CODING", event) == "CODING"


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("event", "coach_mode", "expected_stages"),
    [
        ("COMPACT", True, ["validate_transition", "generate_card", "decide_actions"]),
        ("CODING", False, ["validate_transition", "generate_card", "decide_actions"]),
        (
            "READING",
            False,
            ["validate_transition", "fetch_evidence", "generate_card", "decide_actions"],
        ),
        ("PLAN_RECOMMEND", True, ["validate_transition", "coach_decide", "generate_card"]),
    ],
)
async def test_workflow_uses_event_specific_autogen_stages(
    monkeypatch, event: str, coach_mode: bool, expected_stages: list[str]
) -> None:
    request = WorkflowRunRequest(
        session_id="wf-stage-selection",
        user_id=7,
        event=event,  # type: ignore[arg-type]
        coach_mode=coach_mode,
        problem=WorkflowProblemContext(problem_id=42, title="循环求和"),
    )
    observed_stages: list[str] = []

    async def capture_stages(state, stages, **_kwargs):
        observed_stages.extend(name for name, _handler in stages)
        return state

    monkeypatch.setattr(tutor_graph_module, "run_autogen_tutor_workflow", capture_stages)
    graph = TutorWorkflowGraph(_StubProvider(), StaticEvidenceFetcher([]), emit=lambda *_: None)

    await graph.run({"request": request, "current_phase": "READING"})

    assert observed_stages == expected_stages


@pytest.mark.asyncio
async def test_workflow_restores_legacy_fixed_stage_checkpoint(monkeypatch) -> None:
    request = WorkflowRunRequest(
        session_id="wf-stage-restore",
        user_id=7,
        event="COMPACT",
        problem=WorkflowProblemContext(problem_id=42, title="循环求和"),
    )
    observed_stages: list[str] = []

    async def capture_stages(state, stages, **_kwargs):
        observed_stages.extend(name for name, _handler in stages)
        return state

    monkeypatch.setattr(tutor_graph_module, "run_autogen_tutor_workflow", capture_stages)
    graph = TutorWorkflowGraph(_StubProvider(), StaticEvidenceFetcher([]), emit=lambda *_: None)

    await graph.run(
        {"request": request, "current_phase": "READING"},
        restore_checkpoint={"stage_index": 1, "stage_name": "coach_decide"},
    )

    assert observed_stages == [
        "validate_transition",
        "coach_decide",
        "fetch_evidence",
        "generate_card",
        "decide_actions",
    ]


def test_agent_feedback_requires_text_message() -> None:
    with pytest.raises(ValidationError):
        _request("AGENT_FEEDBACK")

    request = _request("AGENT_FEEDBACK", {"message": "这张卡的例子看不懂"})
    assert request.event_data["message"] == "这张卡的例子看不懂"
    assert request.typed_event_data.message == "这张卡的例子看不懂"
    assert "typed_event_data" not in request.model_dump(mode="json")


def test_agent_feedback_accepts_server_resolved_references() -> None:
    request = _request(
        "AGENT_FEEDBACK",
        {
            "message": "@card:card-reading-1 请换一种方式解释。",
            "references": [
                {
                    "kind": "card",
                    "card_id": "card-reading-1",
                    "card_key": "problem_guide",
                    "snippet": "题目要求读取半径并输出面积。",
                }
            ],
        },
    )

    assert request.typed_event_data.references[0]["card_id"] == "card-reading-1"


def test_plan_start_requires_typed_server_phase_context() -> None:
    with pytest.raises(ValidationError):
        _request("PLAN_START", {})
    with pytest.raises(ValidationError):
        _request(
            "PLAN_START",
            {"reason": "开始计划", "trigger_source": "student", "current_phase": "HACKED"},
        )

    request = _request(
        "PLAN_START",
        {
            "plan_id": "ep-wf-task7",
            "revision": 0,
            "reason": "开始计划",
            "trigger_source": "student",
            "current_phase": "READING",
        },
    )
    assert request.event == "PLAN_START"


def test_plan_response_restricts_evidence_kind() -> None:
    with pytest.raises(ValidationError):
        _request(
            "PLAN_RESPONSE",
            {
                "plan_id": "ep-1",
                "revision": 0,
                "step_id": "s1",
                "evidence_type": "fake_submission",
            },
        )
    request = _request(
        "PLAN_RESPONSE",
        {
            "plan_id": "ep-1",
            "revision": 0,
            "step_id": "s1",
            "evidence_type": "reflection",
            "response": "先把输入读成整数，再循环累加。",
        },
    )
    assert request.event_data["evidence_type"] == "reflection"


def test_plan_redirect_requires_bounded_instruction() -> None:
    with pytest.raises(ValidationError):
        _request("PLAN_STEERING", {"plan_id": "ep-1", "signal_type": "redirect"})
    request = _request(
        "PLAN_STEERING",
        {
            "plan_id": "ep-1",
            "revision": 0,
            "signal_type": "redirect",
            "redirect_instruction": "先回到输入格式，再继续编码。",
        },
    )
    assert request.event_data["signal_type"] == "redirect"


def test_compact_accepts_empty_payload() -> None:
    assert _request("COMPACT").event_data == {}


@pytest.mark.asyncio
async def test_typed_event_and_plan_round_trip_through_autogen_envelope() -> None:
    request = _request(
        "PLAN_START",
        {
            "plan_id": "ep-wf-task7",
            "revision": 0,
            "reason": "开始计划",
            "trigger_source": "student",
            "current_phase": "READING",
        },
    )
    plan = (await _run_graph("PLAN_RECOMMEND"))["episode_plan"]
    patch = TutorEpisodePlanPatch(
        plan_id=plan.plan_id,
        revision=plan.revision,
        current_step_id=plan.current_step_id,
        status=plan.status,
    )
    decoded = _decode_state(
        _encode_state({"request": request, "episode_plan": plan, "episode_patch": patch})
    )

    assert decoded["request"].typed_event_data.current_phase == "READING"
    assert decoded["episode_plan"] == plan
    assert decoded["episode_patch"] == patch


@pytest.mark.asyncio
async def test_agent_feedback_runs_chat_card_agent() -> None:
    provider = _StubProvider("Try the loop with the smallest input first.")
    state = await _run_graph(
        "AGENT_FEEDBACK",
        {"message": "为什么循环要从 1 开始？"},
        provider=provider,
    )

    assert state["runtime_state"] == "SUCCEEDED"
    assert state["effective_event"] == "AGENT_FEEDBACK"
    assert state["card_key"] == "ai_reply"
    assert state["card"]["content"] == "Try the loop with the smallest input first."
    assert provider.calls == 1


@pytest.mark.asyncio
async def test_compact_returns_visible_bounded_summary_without_model_call() -> None:
    provider = _StubProvider()
    history = [
        {"role": "user" if index % 2 == 0 else "assistant", "content": f"turn-{index}"}
        for index in range(20)
    ]
    state = await _run_graph("COMPACT", chat_history=history, provider=provider)

    assert state["runtime_state"] == "SUCCEEDED"
    assert state["card_key"] == "ai_reply"
    assert "turn-0" not in state["card"]["content"]
    assert "turn-12" in state["card"]["content"]
    assert len(state["resume_chat_history"]) == 8
    assert provider.calls == 0


@pytest.mark.asyncio
async def test_plan_recommend_start_and_response_update_saved_plan() -> None:
    recommended = await _run_graph("PLAN_RECOMMEND", {"trigger_event": "READING"})
    plan = recommended["episode_plan"]
    assert recommended["runtime_state"] == "SUCCEEDED"
    assert recommended["effective_event"] == "PLAN_RECOMMEND"
    assert recommended["card"] is None
    assert isinstance(plan, TutorEpisodePlan)
    assert plan.status == "RECOMMENDED"
    assert plan.user_id == 7 and plan.session_id == "wf-task7"

    start_data = {
        "plan_id": plan.plan_id,
        "revision": plan.revision,
        "reason": "先按计划练一遍",
        "trigger_source": "student",
        "current_phase": "READING",
    }
    started = await _run_graph("PLAN_START", start_data, episode_plan=plan)
    active_plan = started["episode_plan"]
    assert started["runtime_state"] == "SUCCEEDED"
    assert active_plan.status == "ACTIVE"
    assert started["episode_patch"].revision == active_plan.revision

    active_step = next(step for step in active_plan.steps if step.status == "ACTIVE")
    responded = await _run_graph(
        "PLAN_RESPONSE",
        {
            "plan_id": active_plan.plan_id,
            "revision": active_plan.revision,
            "step_id": active_step.step_id,
            "evidence_type": "reflection",
            "response": "我已经复述了题目输入和边界。",
        },
        episode_plan=active_plan,
        episode_patch=started["episode_patch"],
    )
    updated_plan = responded["episode_plan"]
    assert responded["runtime_state"] == "SUCCEEDED"
    assert updated_plan.revision == active_plan.revision + 1
    assert next(step for step in updated_plan.steps if step.step_id == active_step.step_id).status == "DONE"


@pytest.mark.asyncio
async def test_plan_recommend_rejects_trigger_outside_phase_actions() -> None:
    state = await _run_graph("PLAN_RECOMMEND", {"trigger_event": "TRANSFER"})

    assert state["runtime_state"] == "FAILED"
    assert state["failure_bucket"] == "TRANSITION_VIOLATION"


@pytest.mark.asyncio
async def test_plan_control_rejects_replacement_id_and_wrong_owner() -> None:
    recommended = await _run_graph("PLAN_RECOMMEND")
    plan = recommended["episode_plan"]
    replacement = await _run_graph(
        "PLAN_START",
        {
            "plan_id": "client-selected-plan",
            "revision": plan.revision,
            "reason": "开始",
            "trigger_source": "student",
            "current_phase": "READING",
        },
        episode_plan=plan,
    )
    assert replacement["runtime_state"] == "FAILED"
    assert replacement["failure_bucket"] == "PLAN_NOT_FOUND"

    wrong_owner = plan.model_copy(update={"user_id": 8})
    state = await _run_graph(
        "PLAN_START",
        {
            "plan_id": wrong_owner.plan_id,
            "revision": wrong_owner.revision,
            "reason": "开始",
            "trigger_source": "student",
            "current_phase": "READING",
        },
        episode_plan=wrong_owner,
    )
    assert state["runtime_state"] == "FAILED"
    assert state["failure_bucket"] == "PLAN_SCOPE_MISMATCH"


@pytest.mark.asyncio
async def test_plan_start_rejects_stale_phase_and_patch() -> None:
    recommended = await _run_graph("PLAN_RECOMMEND")
    plan = recommended["episode_plan"]
    start_data = {
        "plan_id": plan.plan_id,
        "revision": plan.revision,
        "reason": "开始计划",
        "trigger_source": "student",
        "current_phase": "READING",
    }
    stale_phase = await _run_graph(
        "PLAN_START", start_data, current_phase="CODING", episode_plan=plan
    )
    assert stale_phase["failure_bucket"] == "TRANSITION_VIOLATION"

    stale_patch = TutorEpisodePlanPatch(
        plan_id=plan.plan_id,
        revision=plan.revision + 1,
        current_step_id=plan.current_step_id,
        status=plan.status,
    )
    stale_plan = await _run_graph(
        "PLAN_START",
        start_data,
        episode_plan=plan,
        episode_patch=stale_patch,
    )
    assert stale_plan["failure_bucket"] == "PLAN_STALE"


@pytest.mark.asyncio
async def test_plan_control_rejects_stale_revision() -> None:
    recommended = await _run_graph("PLAN_RECOMMEND")
    plan = recommended["episode_plan"]
    started = await _run_graph(
        "PLAN_START",
        {
            "plan_id": plan.plan_id,
            "revision": plan.revision,
            "reason": "开始计划",
            "trigger_source": "student",
            "current_phase": "READING",
        },
        episode_plan=plan,
    )
    active_plan = started["episode_plan"]

    stale = await _run_graph(
        "PLAN_STEERING",
        {
            "plan_id": active_plan.plan_id,
            "revision": plan.revision,
            "signal_type": "pause",
        },
        episode_plan=active_plan,
    )

    assert stale["runtime_state"] == "FAILED"
    assert stale["failure_bucket"] == "PLAN_STALE"


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("signal", "initial_status", "expected_status"),
    [
        ("pause", "ACTIVE", "PAUSED"),
        ("resume", "PAUSED", "ACTIVE"),
        ("skip", "ACTIVE", "ACTIVE"),
        ("take_over", "RECOMMENDED", "SURRENDERED"),
        ("redirect", "ACTIVE", "PAUSED"),
    ],
)
async def test_plan_steering_is_deterministic(
    signal: str, initial_status: str, expected_status: str
) -> None:
    recommended = await _run_graph("PLAN_RECOMMEND")
    plan = recommended["episode_plan"].model_copy(update={"status": initial_status})
    payload: dict[str, object] = {
        "plan_id": plan.plan_id,
        "revision": plan.revision,
        "signal_type": signal,
    }
    if signal == "redirect":
        payload["redirect_instruction"] = "先回顾输入格式，再继续。"
    result = await _run_graph("PLAN_STEERING", payload, episode_plan=plan)

    assert result["runtime_state"] == "SUCCEEDED"
    assert result["episode_plan"].status == expected_status
    assert result["episode_patch"].revision == result["episode_plan"].revision
    if signal == "skip":
        assert next(step for step in result["episode_plan"].steps if step.status == "ACTIVE")
        assert any(step.status == "SKIPPED" for step in result["episode_plan"].steps)
    if signal == "redirect":
        assert "输入格式" in result["episode_plan"].revision_reason


async def _collect_events(response) -> list[tuple[str, dict[str, object]]]:
    raw = "".join([
        chunk if isinstance(chunk, str) else chunk.decode("utf-8")
        async for chunk in response.body_iterator
    ])
    events: list[tuple[str, dict[str, object]]] = []
    for block in raw.split("\n\n"):
        if not block.strip() or block.startswith(":"):
            continue
        name = "message"
        payload: dict[str, object] = {}
        for line in block.split("\n"):
            if line.startswith("event:"):
                name = line[len("event:") :].strip()
            elif line.startswith("data:"):
                payload = json.loads(line[len("data:") :].strip())
        if name == "RUN_FINISHED":
            name = "result"
            payload = payload.get("result", payload)  # type: ignore[assignment]
        elif name == "RUN_ERROR":
            name = "error"
        events.append((name, payload))
    return events


@pytest.mark.asyncio
async def test_route_binds_plan_to_trusted_user_and_session() -> None:
    plan_state = await _run_graph("PLAN_RECOMMEND")
    plan = plan_state["episode_plan"].model_copy(update={"user_id": 8})
    response = await tutor_workflow_run_stream(
        WorkflowRunEnvelope(
            session_id="wf-task7-route-scope",
            user_id=7,
            event="PLAN_START",
            problem={"problem_id": 42, "title": "循环求和"},
            event_data={
                "plan_id": plan.plan_id,
                "revision": plan.revision,
                "reason": "开始计划",
                "trigger_source": "student",
                "current_phase": "READING",
            },
            episode_plan=plan,
        ),
        provider=_StubProvider(),
        evidence_fetcher=StaticEvidenceFetcher([]),
        trusted_user_id=7,
    )
    events = await _collect_events(response)

    assert any(name == "error" for name, _ in events)
    await session_store.reset_session(7, "wf-task7-route-scope")


@pytest.mark.asyncio
async def test_compact_route_returns_card_and_persists_bounded_history() -> None:
    session_id = "wf-task7-compact-route"
    await session_store.reset_session(7, session_id)
    history = [
        {"role": "user" if index % 2 == 0 else "assistant", "content": f"turn-{index}"}
        for index in range(20)
    ]
    await session_store.restore_phase(7, session_id, "READING", 0, chat_history=history)
    response = await tutor_workflow_run_stream(
        WorkflowRunEnvelope(
            session_id=session_id,
            user_id=7,
            event="COMPACT",
            problem={"problem_id": 42, "title": "循环求和"},
            event_data={},
        ),
        provider=_StubProvider(),
        evidence_fetcher=StaticEvidenceFetcher([]),
        trusted_user_id=7,
    )
    events = await _collect_events(response)
    result = next(payload for name, payload in events if name == "result")
    compacted = await session_store.get_state(7, session_id)

    assert result["event"] == "COMPACT"
    assert result["card_key"] == "ai_reply"
    assert "turn-12" in result["card"]["content"]
    assert len(result["resume_chat_history"]) == 8
    assert len(compacted.chat_history) == 8
    await session_store.reset_session(7, session_id)
