"""AutoGen adapter for Tutor stages, adapted from Alethicode's tutor-graph runtime."""

from __future__ import annotations

import asyncio
import json
from collections.abc import Awaitable, Callable, Sequence
from typing import Any, TypeVar, cast

from autogen_agentchat.agents import BaseChatAgent
from autogen_agentchat.base import ChatAgent, Response, Team
from autogen_agentchat.messages import BaseAgentEvent, BaseChatMessage, TextMessage
from autogen_agentchat.teams import RoundRobinGroupChat
from autogen_core import CancellationToken

from pythontopia_graph.nodes.evidence_fetcher import RagHit
from pythontopia_graph.schemas.tutor_workflow import (
    GuideCoachDecision,
    TutorStudentState,
    WorkflowAction,
    WorkflowRunRequest,
)

StateT = TypeVar("StateT")
StageHandler = Callable[[StateT], Awaitable[StateT]]
_STATE_MARKER = "__pythontopia_tutor_state_v1__:"


class TutorWorkflowStageAgent(BaseChatAgent):
    """将一个现有 Tutor handler 包装为固定顺序的 AutoGen Agent。"""

    def __init__(
        self, name: str, handler: StageHandler[Any], private_images: list[str] | None = None
    ) -> None:
        super().__init__(name=name, description=f"Fixed Tutor workflow stage: {name}")
        self._handler = handler
        self._private_images = private_images

    @property
    def produced_message_types(self) -> Sequence[type[BaseChatMessage]]:
        return (TextMessage,)

    async def on_messages(
        self,
        messages: Sequence[BaseChatMessage],
        cancellation_token: CancellationToken,
    ) -> Response:
        current_task = asyncio.current_task()
        if current_task is not None:
            cancellation_token.link_future(current_task)
        if cancellation_token.is_cancelled():
            raise asyncio.CancelledError

        state = _latest_state(messages)
        request = state.get("request")
        if self._private_images is not None and isinstance(request, WorkflowRunRequest):
            state["request"] = request.model_copy(
                update={
                    "event_data": {
                        **request.event_data,
                        "images": self._private_images,
                    }
                }
            )
        updated_state = await self._handler(state)
        if not isinstance(updated_state, dict):
            raise TypeError("Tutor workflow stage must return a state object")
        if cancellation_token.is_cancelled():
            raise asyncio.CancelledError
        return Response(
            chat_message=TextMessage(
                content=_encode_state(updated_state),
                source=self.name,
            )
        )

    async def on_reset(self, cancellation_token: CancellationToken) -> None:
        return None


async def run_autogen_tutor_workflow(
    state: StateT,
    stages: Sequence[tuple[str, StageHandler[StateT]]],
    *,
    cancellation_token: CancellationToken | None = None,
) -> StateT:
    """按声明顺序运行每个 Agent 一轮，并在工作流失败时停止派发。"""
    if not stages:
        raise ValueError("Tutor AutoGen workflow requires at least one stage")
    names = [name for name, _ in stages]
    if len(set(names)) != len(names):
        raise ValueError("Tutor AutoGen stage names must be unique")

    agents: list[ChatAgent | Team] = [
        TutorWorkflowStageAgent(
            name,
            cast(StageHandler[dict[str, Any]], handler),
            private_images=(
                list(state["request"].event_data.get("images", []))
                if isinstance(state.get("request"), WorkflowRunRequest)
                else None
            ),
        )
        for name, handler in stages
    ]
    team = RoundRobinGroupChat(
        agents,
        name="pythontopia_tutor_workflow",
        max_turns=1,
    )
    cancellation_token = cancellation_token or CancellationToken()
    current_state = cast(dict[str, Any], state)

    try:
        for _ in stages:
            result = await team.run(
                task=TextMessage(content=_encode_state(current_state), source="user"),
                cancellation_token=cancellation_token,
            )
            current_state = _result_state(result.messages)
            if current_state.get("runtime_state") == "FAILED":
                break
        return cast(StateT, current_state)
    except asyncio.CancelledError:
        cancellation_token.cancel()
        raise


def _encode_state(state: dict[str, Any]) -> str:
    payload = dict(state)
    for key, model_type in (
        ("request", WorkflowRunRequest),
        ("coach_decision", GuideCoachDecision),
        ("student_state", TutorStudentState),
    ):
        value = payload.get(key)
        if value is not None:
            if not isinstance(value, model_type):
                raise TypeError(f"Tutor workflow state field {key!r} must be {model_type.__name__}")
            exclude = {"event_data": {"images"}} if key == "request" else None
            payload[key] = value.model_dump(mode="json", exclude=exclude)

    actions = payload.get("available_actions")
    if actions is not None:
        payload["available_actions"] = [
            action.model_dump(mode="json")
            if isinstance(action, WorkflowAction)
            else WorkflowAction.model_validate(action).model_dump(mode="json")
            for action in actions
        ]
    chunks = payload.get("evidence_chunks")
    if chunks is not None:
        payload["evidence_chunks"] = [
            chunk.model_dump(mode="json")
            if isinstance(chunk, RagHit)
            else RagHit.model_validate(chunk).model_dump(mode="json")
            for chunk in chunks
        ]

    return _STATE_MARKER + json.dumps(
        payload,
        ensure_ascii=False,
        allow_nan=False,
        separators=(",", ":"),
    )


def _decode_state(content: str) -> dict[str, Any]:
    if not content.startswith(_STATE_MARKER):
        raise ValueError("AutoGen Tutor message does not contain a state envelope")
    state = json.loads(content[len(_STATE_MARKER) :])
    if not isinstance(state, dict):
        raise TypeError("AutoGen Tutor state envelope must contain an object")

    for key, model_type in (
        ("request", WorkflowRunRequest),
        ("coach_decision", GuideCoachDecision),
        ("student_state", TutorStudentState),
    ):
        if state.get(key) is not None:
            state[key] = model_type.model_validate(state[key])
    if state.get("available_actions") is not None:
        state["available_actions"] = [
            WorkflowAction.model_validate(action) for action in state["available_actions"]
        ]
    if state.get("evidence_chunks") is not None:
        state["evidence_chunks"] = [RagHit.model_validate(chunk) for chunk in state["evidence_chunks"]]
    return state


def _latest_state(messages: Sequence[BaseChatMessage]) -> dict[str, Any]:
    for message in reversed(messages):
        if isinstance(message, TextMessage) and message.content.startswith(_STATE_MARKER):
            return _decode_state(message.content)
    raise ValueError("AutoGen Tutor stage received no state envelope")


def _result_state(
    messages: Sequence[BaseAgentEvent | BaseChatMessage],
) -> dict[str, Any]:
    for message in reversed(messages):
        if isinstance(message, TextMessage) and message.source != "user":
            return _decode_state(message.content)
    raise RuntimeError("AutoGen Tutor stage produced no state message")
