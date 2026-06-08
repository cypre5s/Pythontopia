"""OrchestratorAgent 进程内单 worker 会话记忆。

单 worker 下模块级 dict + asyncio.Lock；按 session_id 存多轮对话、plan、画像信号。
服务端为权威，客户端 history 仅冷启动回退。访问器返回深拷贝，写路径经锁保护。
"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass, field

from pythontopia_graph.schemas.orchestrator import (
    OrchestratorTurn,
    PlanStep,
    ProfileSignal,
)

MAX_TURNS = 40


@dataclass
class SessionState:
    turns: list[OrchestratorTurn] = field(default_factory=list)
    plan: list[PlanStep] = field(default_factory=list)
    profile_signals: list[ProfileSignal] = field(default_factory=list)


_STORE: dict[str, SessionState] = {}
_LOCK = asyncio.Lock()


async def get_history(session_id: str) -> list[OrchestratorTurn]:
    async with _LOCK:
        state = _STORE.get(session_id)
        return [turn.model_copy(deep=True) for turn in state.turns] if state else []


async def get_plan(session_id: str) -> list[PlanStep]:
    async with _LOCK:
        state = _STORE.get(session_id)
        return [step.model_copy(deep=True) for step in state.plan] if state else []


async def get_profile_signals(session_id: str) -> list[ProfileSignal]:
    async with _LOCK:
        state = _STORE.get(session_id)
        return (
            [signal.model_copy(deep=True) for signal in state.profile_signals]
            if state
            else []
        )


async def append_turns(session_id: str, new_turns: list[OrchestratorTurn]) -> None:
    if not new_turns:
        return
    async with _LOCK:
        state = _STORE.setdefault(session_id, SessionState())
        state.turns.extend(turn.model_copy(deep=True) for turn in new_turns)
        if len(state.turns) > MAX_TURNS:
            del state.turns[: len(state.turns) - MAX_TURNS]


async def set_plan(session_id: str, plan: list[PlanStep]) -> None:
    async with _LOCK:
        state = _STORE.setdefault(session_id, SessionState())
        state.plan = [step.model_copy(deep=True) for step in plan]


async def merge_profile_signals(session_id: str, signals: list[ProfileSignal]) -> None:
    if not signals:
        return
    async with _LOCK:
        state = _STORE.setdefault(session_id, SessionState())
        by_dimension = {signal.dimension: signal for signal in state.profile_signals}
        for signal in signals:
            by_dimension[signal.dimension] = signal.model_copy(deep=True)
        state.profile_signals = list(by_dimension.values())


async def reset_session(session_id: str) -> None:
    async with _LOCK:
        _STORE.pop(session_id, None)
