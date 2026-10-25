"""导学工作流进程内会话状态（单 worker dict + asyncio.Lock，orchestrator_session 同款）。

服务端权威保存：当前阶段、卡片时间线（CHAT 引用上下文）、对话历史。
Java 侧另有持久化投影（tutor_workflow_session/card 表）承担跨重启恢复；
本存储只服务图运行期的阶段状态机与 CHAT 上下文（重启后由 Java 投影回放 phase）。
"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from typing import Any

# CHAT 上下文最多引用最近几张卡（与 Alethicode get_last_cards(limit=5) 对齐）。
MAX_CARDS = 12
MAX_CHAT_TURNS = 20


@dataclass
class WorkflowSessionState:
    phase: str = "READING"
    seq: int = 0
    # [{card_id, card_key, short_text}]，给 CHAT 节点做「引用最近卡片」上下文。
    cards: list[dict[str, Any]] = field(default_factory=list)
    # [{role, content}]
    chat_history: list[dict[str, str]] = field(default_factory=list)


SessionKey = tuple[int, str]

_STORE: dict[SessionKey, WorkflowSessionState] = {}
_ACTIVE_RUNS: set[SessionKey] = set()
_LOCK = asyncio.Lock()


def _key(user_id: int, session_id: str) -> SessionKey:
    if user_id < 1:
        raise ValueError("user_id must be positive")
    if not session_id:
        raise ValueError("session_id must not be empty")
    return user_id, session_id


async def get_state(user_id: int, session_id: str) -> WorkflowSessionState:
    async with _LOCK:
        state = _STORE.get(_key(user_id, session_id))
        if state is None:
            return WorkflowSessionState()
        return WorkflowSessionState(
            phase=state.phase,
            seq=state.seq,
            cards=[dict(card) for card in state.cards],
            chat_history=[dict(turn) for turn in state.chat_history],
        )


async def restore_phase(
    user_id: int,
    session_id: str,
    phase: str,
    seq: int,
    *,
    cards: list[dict[str, Any]] | None = None,
    chat_history: list[dict[str, str]] | None = None,
    force: bool = False,
) -> None:
    """从 Java 投影恢复权威会话上下文；常规调用不覆盖进程内状态。"""
    async with _LOCK:
        key = _key(user_id, session_id)
        if force or key not in _STORE:
            restored_cards = [
                {
                    "card_id": str(card.get("card_id", ""))[:120],
                    "card_key": str(card.get("card_key", ""))[:64],
                    "short_text": str(card.get("short_text", ""))[:200],
                    **(
                        {"next_practice_direction": str(card["next_practice_direction"])[:500]}
                        if isinstance(card.get("next_practice_direction"), str)
                        else {}
                    ),
                }
                for card in (cards or [])[-MAX_CARDS:]
                if isinstance(card, dict)
            ]
            restored_history = [
                {"role": turn["role"], "content": turn["content"][:2000]}
                for turn in (chat_history or [])[-MAX_CHAT_TURNS:]
                if isinstance(turn, dict)
                and turn.get("role") in {"user", "assistant"}
                and isinstance(turn.get("content"), str)
            ]
            _STORE[key] = WorkflowSessionState(
                phase=phase,
                seq=seq,
                cards=restored_cards,
                chat_history=restored_history,
            )


async def commit_run(
    user_id: int,
    session_id: str,
    *,
    phase: str,
    card_key: str | None,
    card_id: str | None,
    short_text: str,
    extra: dict[str, Any] | None = None,
    chat_turns: list[dict[str, str]] | None = None,
    replace_chat_history: list[dict[str, str]] | None = None,
) -> int:
    """run 成功后提交阶段与时间线；返回该卡片的会话内 seq（无卡片返回当前 seq）。

    extra：随时间线条目保存的少量结构化字段（如 post_ac 的 next_practice_direction，
    供 TRANSFER 事件回查），不存整卡（整卡持久化归 Java 投影）。
    """
    async with _LOCK:
        state = _STORE.setdefault(_key(user_id, session_id), WorkflowSessionState())
        state.phase = phase
        if card_key is not None and card_id is not None:
            state.seq += 1
            entry = {"card_id": card_id, "card_key": card_key, "short_text": short_text[:200]}
            if extra:
                entry.update(extra)
            state.cards.append(entry)
            if len(state.cards) > MAX_CARDS:
                del state.cards[: len(state.cards) - MAX_CARDS]
        if chat_turns:
            state.chat_history.extend(
                {"role": turn.get("role", ""), "content": turn.get("content", "")}
                for turn in chat_turns
            )
            if len(state.chat_history) > MAX_CHAT_TURNS:
                del state.chat_history[: len(state.chat_history) - MAX_CHAT_TURNS]
        if replace_chat_history is not None:
            state.chat_history = [
                {"role": turn["role"], "content": turn["content"][:2000]}
                for turn in replace_chat_history[-MAX_CHAT_TURNS:]
                if turn.get("role") in {"user", "assistant"}
                and isinstance(turn.get("content"), str)
            ]
        return state.seq


async def reset_session(user_id: int, session_id: str) -> None:
    async with _LOCK:
        key = _key(user_id, session_id)
        _STORE.pop(key, None)
        _ACTIVE_RUNS.discard(key)


async def claim_run(user_id: int, session_id: str) -> bool:
    """Allow one in-flight AutoGen run per owner/session in this single-worker process."""
    async with _LOCK:
        key = _key(user_id, session_id)
        if key in _ACTIVE_RUNS:
            return False
        _ACTIVE_RUNS.add(key)
        return True


async def release_run(user_id: int, session_id: str) -> None:
    async with _LOCK:
        _ACTIVE_RUNS.discard(_key(user_id, session_id))
