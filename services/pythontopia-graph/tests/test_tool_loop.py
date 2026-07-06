"""有界原生工具调用循环引擎单元测试（MVP Phase C）。

全程离线：DryRunProvider 确定性「工具回合→收到结果作答」+ 注入 fake provider/executor，
不触真实网络。覆盖：能力回落、单轮闭环、上界停止、预算停止、未知工具过滤、执行失败回喂、
arguments 解析、构造期有界校验。
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from typing import Any

import pytest

from pythontopia_graph.graph.tool_loop import (
    ToolCall,
    ToolCallingLoop,
    ToolExecutor,
    coerce_tool_calls,
)
from pythontopia_graph.llm.provider import DryRunProvider, LlmResponse
from pythontopia_graph.tools.harness import reset_harness_state


@pytest.fixture(autouse=True)
def _isolate_harness() -> None:
    reset_harness_state()


class _RecordingExecutor:
    """记录被调用的工具，返回固定结果文本。"""

    def __init__(self, result: str = '{"ok": true}') -> None:
        self.calls: list[ToolCall] = []
        self._result = result

    async def execute(self, call: ToolCall) -> str:
        self.calls.append(call)
        return self._result


class _BoomExecutor:
    async def execute(self, call: ToolCall) -> str:
        raise RuntimeError("executor boom")


class _AlwaysToolProvider:
    """每轮都返回一个合法 tool_call（用于触发 max_steps 有界停止）。"""

    supports_tool_calling = True

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        return LlmResponse(
            tool_calls=[
                {"id": "c", "type": "function", "function": {"name": "ASK", "arguments": "{}"}}
            ]
        )

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        yield ""


class _NoToolProvider:
    supports_tool_calling = False

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        return LlmResponse(content_text="no tools here")

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        yield ""


class _UnknownToolProvider:
    supports_tool_calling = True

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        return LlmResponse(
            content_text="done",
            tool_calls=[
                {"id": "c", "type": "function", "function": {"name": "NOPE", "arguments": "{}"}}
            ],
        )

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        yield ""


# ── coerce_tool_calls 纯函数 ───────────────────────────────────────────────


def test_coerce_parses_and_whitelists() -> None:
    raw: list[dict[str, Any]] = [
        {"id": "a", "function": {"name": "ASK", "arguments": '{"topic": "x"}'}},
        {"id": "b", "function": {"name": "NOPE", "arguments": "{}"}},  # 未知 → 丢弃
    ]
    calls = coerce_tool_calls(raw)
    assert len(calls) == 1
    assert calls[0].name == "ASK"
    assert calls[0].arguments == {"topic": "x"}


def test_coerce_malformed_arguments_failsoft() -> None:
    calls = coerce_tool_calls(
        [{"id": "a", "function": {"name": "ASK", "arguments": "not-json"}}]
    )
    assert calls[0].arguments == {}
    assert coerce_tool_calls(None) == []


# ── 引擎 ───────────────────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_no_tool_calling_provider_falls_back() -> None:
    loop = ToolCallingLoop(provider=_NoToolProvider())  # type: ignore[arg-type]
    result = await loop.run(
        system_prompt="s", user_message="u", executor=_RecordingExecutor()
    )
    assert result.stop_reason == "no_tool_calling"
    assert result.tool_trail == []


@pytest.mark.asyncio
async def test_single_round_closed_loop_with_dryrun() -> None:
    executor = _RecordingExecutor()
    loop = ToolCallingLoop(provider=DryRunProvider(), max_steps=4)
    result = await loop.run(system_prompt="s", user_message="讲讲循环", executor=executor)
    # DryRun：第一轮回 tool_call(首工具 ASK) → 执行 → 回喂 → 第二轮作答 end_turn。
    assert result.stop_reason == "end_turn"
    assert result.tool_trail == ["ASK"]
    assert len(executor.calls) == 1
    assert result.final_text  # dry-run 最终答复
    assert result.steps[0].ok is True


@pytest.mark.asyncio
async def test_max_steps_bounded_stop() -> None:
    loop = ToolCallingLoop(provider=_AlwaysToolProvider(), max_steps=2)  # type: ignore[arg-type]
    result = await loop.run(
        system_prompt="s", user_message="u", executor=_RecordingExecutor()
    )
    assert result.stop_reason == "max_steps"
    assert result.rounds == 2
    assert len(result.steps) == 2
    assert "安全上限" in result.final_text  # 诚实停止文案


@pytest.mark.asyncio
async def test_budget_bounded_stop() -> None:
    # max_llm_calls=1 → 首轮即耗尽次数预算之后无法再调（但首轮已 acquire），
    # 这里设 0 不合法；用 1 步预算配合 always-tool 触发 budget 停止于第二轮。
    loop = ToolCallingLoop(
        provider=_AlwaysToolProvider(),  # type: ignore[arg-type]
        max_steps=5,
        max_llm_calls=1,
    )
    result = await loop.run(
        system_prompt="s", user_message="u", executor=_RecordingExecutor()
    )
    assert result.stop_reason == "budget"
    assert result.rounds == 1  # 仅第一轮模型调用成功，第二轮被预算拦下


@pytest.mark.asyncio
async def test_unknown_tool_filtered_ends_turn() -> None:
    executor = _RecordingExecutor()
    loop = ToolCallingLoop(provider=_UnknownToolProvider())  # type: ignore[arg-type]
    result = await loop.run(system_prompt="s", user_message="u", executor=executor)
    assert result.stop_reason == "end_turn"
    assert result.final_text == "done"
    assert executor.calls == []  # 未知工具未被执行


@pytest.mark.asyncio
async def test_executor_failure_failsoft_feeds_error() -> None:
    loop = ToolCallingLoop(provider=DryRunProvider(), max_steps=4)
    result = await loop.run(
        system_prompt="s", user_message="出错路径", executor=_BoomExecutor()
    )
    # 执行失败经 harness 治理 → 回喂结构化 error → 循环不崩溃，DryRun 第二轮作答终止。
    assert result.stop_reason == "end_turn"
    assert result.steps[0].ok is False
    assert result.steps[0].status in {"error", "timeout"}


def test_invalid_max_steps_rejected() -> None:
    with pytest.raises(ValueError):
        ToolCallingLoop(provider=DryRunProvider(), max_steps=0)


def test_tool_executor_protocol_runtime_checkable() -> None:
    assert isinstance(_RecordingExecutor(), ToolExecutor)
