"""工具调用 eval 的单元测试（MVP Phase E）。

零外网：scripted 确定性 provider 按 prompt 关键词选工具；fake dispatcher 零 LLM。
验证 eval 能区分「选对」与「选错」，作为工具选择质量的回归守门。
"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.eval.tool_use_eval import (
    CHALLENGE_TASKS,
    DEFAULT_TASKS,
    ToolUseTask,
    run_tool_use_eval,
    tool_use_tasks_for_set,
)
from pythontopia_graph.graph.tool_executor import RegistryToolExecutor
from pythontopia_graph.llm.provider import LlmResponse
from pythontopia_graph.schemas.orchestrator import DispatchResult, PlanStep
from pythontopia_graph.tools.harness import reset_harness_state


class _FakeDispatcher:
    async def dispatch(
        self, step: PlanStep, user_id: int = 1, focus_hint_extra: str = ""
    ) -> DispatchResult:
        return DispatchResult(step_id=step.id, agent=step.agent, ok=True, summary="ok")


class _ScriptedToolProvider:
    """按 prompt 子串映射到工具名；收到 tool 结果即终止（确定性、可终止）。"""

    supports_tool_calling = True

    def __init__(self, plan: dict[str, str]) -> None:
        self._plan = plan

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        if messages and messages[-1].get("role") == "tool":
            return LlmResponse(content_text="已根据工具结果作答。")
        user = next(
            (m.get("content", "") for m in reversed(messages) if m.get("role") == "user"),
            "",
        )
        for key, tool in self._plan.items():
            if key in user:
                return LlmResponse(
                    tool_calls=[
                        {"id": "c", "type": "function",
                         "function": {"name": tool, "arguments": "{}"}}
                    ]
                )
        return LlmResponse(content_text="无需工具。")

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        yield ""


# 确定性单测用的小型任务集（与 DEFAULT_TASKS 解耦，后者扩容不破坏本测）。
_INLINE_TASKS = [
    ToolUseTask(id="a", prompt="给我整理讲义", expected_tools=["make_resource"]),
    ToolUseTask(id="b", prompt="出几道练习题", expected_tools=["make_practice"]),
    ToolUseTask(id="c", prompt="带我去图书馆", expected_tools=["navigate"]),
]
_INLINE_PLAN = {"讲义": "make_resource", "练习题": "make_practice", "图书馆": "navigate"}


@pytest.fixture(autouse=True)
def _isolate_harness() -> None:
    reset_harness_state()


@pytest.mark.asyncio
async def test_perfect_provider_full_pass() -> None:
    executor = RegistryToolExecutor(_FakeDispatcher())
    report = await run_tool_use_eval(
        _ScriptedToolProvider(_INLINE_PLAN), executor, tasks=_INLINE_TASKS
    )
    assert report.total == len(_INLINE_TASKS)
    assert report.pass_rate == 1.0
    assert report.selection_accuracy == 1.0
    assert report.clean_termination_rate == 1.0
    assert report.avg_rounds >= 1.0


@pytest.mark.asyncio
async def test_wrong_provider_low_accuracy() -> None:
    # "" 子串恒命中 → 每个任务都选 ask；仅期望 ask 的任务命中。
    executor = RegistryToolExecutor(_FakeDispatcher())
    report = await run_tool_use_eval(
        _ScriptedToolProvider({"": "ask"}), executor
    )
    ask_tasks = sum(1 for task in DEFAULT_TASKS if "ask" in task.expected_tools)
    expected_acc = ask_tasks / len(DEFAULT_TASKS)
    assert report.selection_accuracy == pytest.approx(expected_acc, abs=0.01)
    assert report.pass_rate < 1.0


@pytest.mark.asyncio
async def test_eval_detects_no_tool_selection() -> None:
    # 空 plan → 从不调工具 → 选择正确率 0，但干净终止（end_turn）。
    executor = RegistryToolExecutor(_FakeDispatcher())
    report = await run_tool_use_eval(
        _ScriptedToolProvider({}),
        executor,
        tasks=[ToolUseTask(id="t", prompt="给我讲义", expected_tools=["make_resource"])],
    )
    assert report.selection_accuracy == 0.0
    assert report.clean_termination_rate == 1.0
    assert report.cases[0].selected_tools == []


def test_default_tasks_reference_valid_consolidated_tools() -> None:
    from pythontopia_graph.tools.consolidated import CONSOLIDATED_TOOL_NAMES

    for task in DEFAULT_TASKS:
        for tool in task.expected_tools:
            assert tool in CONSOLIDATED_TOOL_NAMES


def test_default_tasks_cover_all_consolidated_tools() -> None:
    """默认任务集应覆盖全部 9 个收敛工具（eval 才能全面守质量）。"""
    from pythontopia_graph.tools.consolidated import CONSOLIDATED_TOOL_NAMES

    covered = {tool for task in DEFAULT_TASKS for tool in task.expected_tools}
    assert covered == set(CONSOLIDATED_TOOL_NAMES)


def test_challenge_tasks_are_large_unique_and_do_not_reuse_default_prompts() -> None:
    default_prompts = {task.prompt for task in DEFAULT_TASKS}
    challenge_prompts = [task.prompt for task in CHALLENGE_TASKS]

    assert len(CHALLENGE_TASKS) == 200
    assert len({task.id for task in CHALLENGE_TASKS}) == 200
    assert len(set(challenge_prompts)) == 200
    assert not default_prompts.intersection(challenge_prompts)


def test_challenge_tasks_cover_all_tools_with_reasonable_balance() -> None:
    from collections import Counter

    from pythontopia_graph.tools.consolidated import CONSOLIDATED_TOOL_NAMES

    counts = Counter(task.expected_tools[0] for task in CHALLENGE_TASKS)

    assert set(counts) == set(CONSOLIDATED_TOOL_NAMES)
    assert min(counts.values()) >= 18
    assert max(counts.values()) <= 24


def test_tool_use_tasks_for_set_selects_default_and_challenge_sets() -> None:
    assert tool_use_tasks_for_set("default") == DEFAULT_TASKS
    assert tool_use_tasks_for_set("challenge") == CHALLENGE_TASKS

    with pytest.raises(ValueError):
        tool_use_tasks_for_set("unknown")
