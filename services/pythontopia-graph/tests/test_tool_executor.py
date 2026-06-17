"""收敛工具执行器桥接的单元测试（MVP Phase D）。

零 LLM：注入 fake dispatcher；端到端用 DryRunProvider + 收敛工具表 + 真执行器。
"""

from __future__ import annotations

import json

import pytest

from pythontopia_graph.graph.tool_executor import (
    RegistryToolExecutor,
    StepDispatcher,
    _build_step,
)
from pythontopia_graph.graph.tool_loop import ToolCall, ToolCallingLoop
from pythontopia_graph.llm.provider import DryRunProvider
from pythontopia_graph.schemas.orchestrator import DispatchResult, PlanStep
from pythontopia_graph.tools.consolidated import (
    CONSOLIDATED_TOOL_NAMES,
    consolidated_tool_schemas,
)
from pythontopia_graph.tools.harness import reset_harness_state


class _FakeDispatcher:
    def __init__(self) -> None:
        self.steps: list[PlanStep] = []

    async def dispatch(
        self, step: PlanStep, user_id: int = 1, focus_hint_extra: str = ""
    ) -> DispatchResult:
        self.steps.append(step)
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=True,
            summary=f"done {step.agent}",
            artifact={"title": "X"},
        )


@pytest.fixture(autouse=True)
def _isolate_harness() -> None:
    reset_harness_state()


def test_fake_dispatcher_satisfies_protocol() -> None:
    assert isinstance(_FakeDispatcher(), StepDispatcher)


@pytest.mark.asyncio
async def test_generative_tool_dispatches_resource() -> None:
    dispatcher = _FakeDispatcher()
    executor = RegistryToolExecutor(dispatcher, user_id=7)
    out = await executor.execute(
        ToolCall(
            id="c",
            name="make_resource",
            arguments={"kind": "handout", "topic": "函数", "kc_codes": ["K1"]},
        )
    )
    payload = json.loads(out)
    assert payload["ok"] is True
    assert payload["tool"] == "HANDOUT"
    step = dispatcher.steps[0]
    assert step.agent == "ResourceDesignerAgent"
    assert step.resource_type == "HANDOUT"
    assert step.kc_codes == ["K1"]


@pytest.mark.asyncio
async def test_ask_explain_code_carries_code_to_tutor() -> None:
    dispatcher = _FakeDispatcher()
    executor = RegistryToolExecutor(dispatcher)
    await executor.execute(
        ToolCall(id="c", name="ask", arguments={"mode": "explain_code", "code": "x=1"})
    )
    step = dispatcher.steps[0]
    assert step.agent == "TutorAgent"
    assert step.detail == "x=1"


@pytest.mark.asyncio
async def test_plan_study_dispatches_path_planner() -> None:
    dispatcher = _FakeDispatcher()
    executor = RegistryToolExecutor(dispatcher)
    await executor.execute(ToolCall(id="c", name="plan_study", arguments={"topic": "函数"}))
    assert dispatcher.steps[0].agent == "PathPlannerAgent"


@pytest.mark.asyncio
async def test_frontend_tool_returns_directive_no_dispatch() -> None:
    dispatcher = _FakeDispatcher()
    executor = RegistryToolExecutor(dispatcher)
    out = await executor.execute(
        ToolCall(id="c", name="navigate", arguments={"target": "library"})
    )
    payload = json.loads(out)
    assert payload["frontend_action"] == "NAVIGATE"
    assert payload["params"]["target"] == "library"
    assert dispatcher.steps == []


@pytest.mark.asyncio
async def test_quiz_modes_are_directives() -> None:
    dispatcher = _FakeDispatcher()
    executor = RegistryToolExecutor(dispatcher)
    review = json.loads(
        await executor.execute(ToolCall(id="c", name="quiz", arguments={"mode": "review"}))
    )
    assert review["frontend_action"] == "REVIEW"
    test_me = json.loads(
        await executor.execute(ToolCall(id="c", name="quiz", arguments={"mode": "test_me"}))
    )
    assert test_me["frontend_action"] == "ASKME"  # 交互式考核走指令，不单步派发
    assert dispatcher.steps == []


@pytest.mark.asyncio
async def test_unknown_tool_returns_error() -> None:
    executor = RegistryToolExecutor(_FakeDispatcher())
    out = await executor.execute(ToolCall(id="c", name="nope", arguments={}))
    assert json.loads(out)["error"] == "unknown_tool"


def test_build_step_classification() -> None:
    ask = _build_step("ASK", {})
    assert ask is not None and ask.agent == "TutorAgent"
    storyboard = _build_step("STORYBOARD", {})
    assert storyboard is not None and storyboard.phase == "watch"
    practice = _build_step("PRACTICE", {})
    assert practice is not None and practice.phase == "practice"
    assert _build_step("VIDEO", {}) is None  # 前端类


@pytest.mark.asyncio
async def test_loop_with_consolidated_tools_end_to_end() -> None:
    dispatcher = _FakeDispatcher()
    executor = RegistryToolExecutor(dispatcher)
    loop = ToolCallingLoop(
        provider=DryRunProvider(),
        tools=consolidated_tool_schemas("default"),
        valid_names=set(CONSOLIDATED_TOOL_NAMES),
        max_steps=4,
    )
    result = await loop.run(system_prompt="s", user_message="给我讲讲函数", executor=executor)
    assert result.stop_reason == "end_turn"
    # DryRun 调首个收敛工具 "ask" → 展开 ASK → TutorAgent 真派发（fake）。
    assert result.tool_trail == ["ask"]
    assert dispatcher.steps and dispatcher.steps[0].agent == "TutorAgent"
