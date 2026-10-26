"""OrchestratorAgent 进程内单 worker 会话记忆。

单 worker 下模块级 dict + asyncio.Lock；按 (user_id, session_id) 存多轮对话、plan、画像信号。
服务端为权威，客户端 history 仅冷启动回退。访问器返回深拷贝，写路径经锁保护。
"""

from __future__ import annotations

import asyncio
from collections import OrderedDict
from dataclasses import dataclass, field

from pythontopia_graph.schemas.orchestrator import (
    MAX_HISTORY_MESSAGES,
    OrchestratorTurn,
    PlanStep,
    ProfileSignal,
)

MAX_TURNS = MAX_HISTORY_MESSAGES
# shortcut: evicted plans/signals are process-local; persist them if they must survive cache churn.
MAX_CACHED_SESSIONS = 512


@dataclass
class SessionState:
    turns: list[OrchestratorTurn] = field(default_factory=list)
    plan: list[PlanStep] = field(default_factory=list)
    profile_signals: list[ProfileSignal] = field(default_factory=list)


SessionKey = tuple[int, str]

_STORE: OrderedDict[SessionKey, SessionState] = OrderedDict()
_ACTIVE_RUNS: set[SessionKey] = set()
_LOCK = asyncio.Lock()


def _trim_store() -> None:
    while len(_STORE) > MAX_CACHED_SESSIONS:
        oldest_inactive = next((key for key in _STORE if key not in _ACTIVE_RUNS), None)
        if oldest_inactive is None:
            return
        del _STORE[oldest_inactive]


def _key(user_id: int, session_id: str) -> SessionKey:
    if user_id < 1:
        raise ValueError("user_id must be positive")
    if not session_id:
        raise ValueError("session_id must not be empty")
    return user_id, session_id


async def get_history(user_id: int, session_id: str) -> list[OrchestratorTurn]:
    async with _LOCK:
        key = _key(user_id, session_id)
        state = _STORE.get(key)
        if state is not None:
            _STORE.move_to_end(key)
        return [turn.model_copy(deep=True) for turn in state.turns] if state else []


async def get_plan(user_id: int, session_id: str) -> list[PlanStep]:
    async with _LOCK:
        key = _key(user_id, session_id)
        state = _STORE.get(key)
        if state is not None:
            _STORE.move_to_end(key)
        return [step.model_copy(deep=True) for step in state.plan] if state else []


async def get_profile_signals(user_id: int, session_id: str) -> list[ProfileSignal]:
    async with _LOCK:
        key = _key(user_id, session_id)
        state = _STORE.get(key)
        if state is not None:
            _STORE.move_to_end(key)
        return (
            [signal.model_copy(deep=True) for signal in state.profile_signals]
            if state
            else []
        )


async def append_turns(
    user_id: int,
    session_id: str,
    new_turns: list[OrchestratorTurn],
    *,
    history_fallback: list[OrchestratorTurn] | None = None,
) -> None:
    if not new_turns:
        return
    async with _LOCK:
        key = _key(user_id, session_id)
        state = _STORE.setdefault(key, SessionState())
        _STORE.move_to_end(key)
        if not state.turns and history_fallback:
            state.turns.extend(
                turn.model_copy(deep=True) for turn in history_fallback[-MAX_TURNS:]
            )
        state.turns.extend(turn.model_copy(deep=True) for turn in new_turns)
        if len(state.turns) > MAX_TURNS:
            del state.turns[: len(state.turns) - MAX_TURNS]
        _trim_store()


async def set_plan(user_id: int, session_id: str, plan: list[PlanStep]) -> None:
    async with _LOCK:
        key = _key(user_id, session_id)
        state = _STORE.setdefault(key, SessionState())
        _STORE.move_to_end(key)
        state.plan = [step.model_copy(deep=True) for step in plan]
        _trim_store()


async def merge_profile_signals(
    user_id: int, session_id: str, signals: list[ProfileSignal]
) -> None:
    if not signals:
        return
    async with _LOCK:
        key = _key(user_id, session_id)
        state = _STORE.setdefault(key, SessionState())
        _STORE.move_to_end(key)
        by_dimension = {signal.dimension: signal for signal in state.profile_signals}
        for signal in signals:
            by_dimension[signal.dimension] = signal.model_copy(deep=True)
        state.profile_signals = list(by_dimension.values())
        _trim_store()


async def begin_session_run(user_id: int, session_id: str) -> None:
    async with _LOCK:
        key = _key(user_id, session_id)
        _ACTIVE_RUNS.add(key)
        if key in _STORE:
            _STORE.move_to_end(key)


async def end_session_run(user_id: int, session_id: str) -> None:
    async with _LOCK:
        _ACTIVE_RUNS.discard(_key(user_id, session_id))
        _trim_store()


async def reset_session(user_id: int, session_id: str) -> None:
    async with _LOCK:
        key = _key(user_id, session_id)
        _STORE.pop(key, None)
