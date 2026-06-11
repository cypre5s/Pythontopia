"""LangGraph v1 checkpointer 接入测试（R-16 升级）。

设计要点见 orchestrator_graph 模块 docstring：thread 按「session:turn-N」逐轮独立
（防 ``operator.add`` reducer 跨轮污染 trace/dispatch_results），checkpoint 价值定位为
「单轮内逐节点快照 + 按轮回放」；跨轮记忆由 orchestrator_session 权威承担（行为不变）。
"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.graph import orchestrator_session
from pythontopia_graph.graph.orchestrator_graph import (
    OrchestratorGraph,
    default_checkpointer,
)
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.orchestrator import OrchestratorRequest


class ScriptedProvider(LlmProvider):
    """understand/decompose/profile 等 JSON 调用返回固定脚本；流式回复吐固定 token。"""

    def __init__(self) -> None:
        self.chat_calls = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.chat_calls += 1
        return LlmResponse(
            content_json={
                "intent": "plan",
                "ambiguous": False,
                "clarifying_question": None,
                "goal": "学习 for 循环",
                "focus": "for 循环",
                "steps": [],
                "profile_signals": [],
            }
        )

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield "好的，"
        yield "按计划学起来。"


def request_of(session_id: str, message: str = "帮我规划 for 循环") -> OrchestratorRequest:
    return OrchestratorRequest(
        user_id=1,
        session_id=session_id,
        user_message=message,
        history=[],
        course_kc_index=["PY43-KC-001"],
    )


@pytest.fixture(autouse=True)
async def _clean_sessions() -> AsyncIterator[None]:
    yield
    # orchestrator_session 是模块级存储，逐用例清理防串台。
    await orchestrator_session.reset_session("ckpt-s1")
    await orchestrator_session.reset_session("ckpt-s2")


@pytest.mark.asyncio
async def test_turn_snapshot_captures_full_run_context() -> None:
    """单轮执行后，该轮 checkpoint 快照应含 understand→plan→compose 全上下文。"""
    graph = OrchestratorGraph(provider=ScriptedProvider())

    await graph.run(request_of("ckpt-s1"))

    snapshot = await graph.get_turn_state("ckpt-s1", 0)
    assert snapshot, "首轮（turn-0）应有 checkpoint 快照"
    assert snapshot.get("goal") == "学习 for 循环"
    assert "plan" in snapshot and isinstance(snapshot["plan"], list)
    assert snapshot.get("reply_md"), "compose 产出的回复应入快照"
    assert snapshot.get("trace"), "逐节点 trace 应入快照"


@pytest.mark.asyncio
async def test_turns_use_isolated_threads_no_reducer_pollution() -> None:
    """第二轮使用独立 thread：trace 不跨轮累加（add reducer 零污染），两轮快照各自独立。"""
    graph = OrchestratorGraph(provider=ScriptedProvider())

    await graph.run(request_of("ckpt-s1", "帮我规划 for 循环"))
    first = await graph.get_turn_state("ckpt-s1", 0)

    # 第一轮 persist 后 server_history 含 user+assistant 两条 → 第二轮 turn_no=1。
    await graph.run(request_of("ckpt-s1", "再细化一下练习安排"))
    second = await graph.get_turn_state("ckpt-s1", 1)

    assert second, "第二轮（turn-1）应有独立快照"
    assert len(first["trace"]) == len(second["trace"]), (
        "两轮 trace 长度应一致（若跨轮共 thread，add reducer 会让第二轮翻倍）"
    )
    assert second.get("req") is not None
    assert second["req"].user_message == "再细化一下练习安排"


@pytest.mark.asyncio
async def test_sessions_are_isolated() -> None:
    """不同 session 的 thread 互不可见。"""
    graph = OrchestratorGraph(provider=ScriptedProvider())

    await graph.run(request_of("ckpt-s1"))

    other = await graph.get_turn_state("ckpt-s2", 0)
    assert other == {}, "未运行过的 session 不应有快照"


@pytest.mark.asyncio
async def test_default_checkpointer_postgres_degrades_to_memory(monkeypatch) -> None:
    """构造期降级：postgres 选型但包/配置不可用 → MemorySaver，orchestrator 不受影响。"""
    from langgraph.checkpoint.memory import MemorySaver

    monkeypatch.setenv("PYT_ORCH_CHECKPOINT", "postgres")
    monkeypatch.delenv("PYT_ORCH_CHECKPOINT_PG_URL", raising=False)

    saver = default_checkpointer()

    assert isinstance(saver, MemorySaver)
    # 降级后的图仍可完整跑一轮（行为与 memory 模式一致）。
    graph = OrchestratorGraph(provider=ScriptedProvider(), checkpointer=saver)
    response = await graph.run(request_of("ckpt-s1"))
    assert response.reply_md
