"""混合工具编排单元测试（MVP Phase F）：路由器选择 + 有界循环编排。"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.agents.intent_router_agent import IntentRouterAgent
from pythontopia_graph.graph.hybrid_loop import (
    HybridToolOrchestrator,
    IntentRouterSelector,
    RouterSelection,
    ToolSelector,
    _seed_tool_call,
)
from pythontopia_graph.graph.tool_executor import RegistryToolExecutor
from pythontopia_graph.graph.tool_loop import ToolCallingLoop
from pythontopia_graph.llm.provider import DryRunProvider, LlmResponse
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
        return DispatchResult(step_id=step.id, agent=step.agent, ok=True, summary="ok")


class _StubSelector:
    def __init__(self, selection: RouterSelection) -> None:
        self._selection = selection

    async def select(self, user_message: str) -> RouterSelection:
        return self._selection


class _NoToolProvider:
    supports_tool_calling = False

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        return LlmResponse(content_text="x")

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        yield ""


@pytest.fixture(autouse=True)
def _isolate_harness() -> None:
    reset_harness_state()


def _loop(provider: object) -> ToolCallingLoop:
    return ToolCallingLoop(
        provider=provider,  # type: ignore[arg-type]
        tools=consolidated_tool_schemas("default"),
        valid_names=set(CONSOLIDATED_TOOL_NAMES),
        max_steps=4,
    )


@pytest.mark.asyncio
async def test_router_selection_seeds_loop() -> None:
    dispatcher = _FakeDispatcher()
    executor = RegistryToolExecutor(dispatcher)
    selector = _StubSelector(
        RouterSelection(intent="HANDOUT", focus_hint="函数", kc_codes=["K1"])
    )
    orch = HybridToolOrchestrator(
        selector=selector, loop=_loop(DryRunProvider()), executor=executor
    )
    result = await orch.run(system_prompt="s", user_message="整理讲义")

    assert result.seed_tool == "make_resource"
    assert result.loop is not None
    assert result.loop.tool_trail[0] == "make_resource"
    # 种子被真派发到 ResourceDesigner(HANDOUT)。
    assert dispatcher.steps and dispatcher.steps[0].resource_type == "HANDOUT"


@pytest.mark.asyncio
async def test_clarification_short_circuits_loop() -> None:
    selector = _StubSelector(
        RouterSelection(intent="ASK", needs_clarification=True, clarification="问得具体点")
    )
    orch = HybridToolOrchestrator(
        selector=selector,
        loop=_loop(DryRunProvider()),
        executor=RegistryToolExecutor(_FakeDispatcher()),
    )
    result = await orch.run(system_prompt="s", user_message="嗯")

    assert result.needs_clarification is True
    assert result.clarification == "问得具体点"
    assert result.loop is None


@pytest.mark.asyncio
async def test_degrade_to_router_only_when_no_tool_calling() -> None:
    dispatcher = _FakeDispatcher()
    executor = RegistryToolExecutor(dispatcher)
    selector = _StubSelector(RouterSelection(intent="HANDOUT", focus_hint="函数"))
    orch = HybridToolOrchestrator(
        selector=selector, loop=_loop(_NoToolProvider()), executor=executor
    )
    result = await orch.run(system_prompt="s", user_message="整理讲义")

    assert result.loop is not None
    assert result.loop.stop_reason == "router_only"
    assert result.loop.tool_trail == ["make_resource"]
    assert dispatcher.steps  # 种子仍被执行（仅选择，无多步编排）


def test_seed_tool_call_mapping() -> None:
    handout = _seed_tool_call(
        RouterSelection(intent="HANDOUT", focus_hint="函数", kc_codes=["K1"]), "整理讲义"
    )
    assert handout is not None
    assert handout.name == "make_resource"
    assert handout.arguments["kind"] == "handout"
    assert handout.arguments["topic"] == "函数"

    nav = _seed_tool_call(RouterSelection(intent="NAVIGATE", target="library"), "去图书馆")
    assert nav is not None and nav.name == "navigate" and nav.arguments == {"target": "library"}

    explain = _seed_tool_call(RouterSelection(intent="EXPLAIN_CODE"), "x=1")
    assert explain is not None and explain.name == "ask"
    assert explain.arguments["mode"] == "explain_code" and explain.arguments["code"] == "x=1"

    plan = _seed_tool_call(RouterSelection(intent="PLAN", focus_hint="学函数"), "规划")
    assert plan is not None and plan.name == "plan_study"

    assert _seed_tool_call(RouterSelection(intent="NONSENSE"), "x") is None


@pytest.mark.asyncio
async def test_intent_router_selector_keyword_fallback() -> None:
    # 真 IntentRouterAgent + DryRun（LLM 非 intent JSON → 关键词兜底）：讲义 → HANDOUT。
    selector = IntentRouterSelector(IntentRouterAgent(DryRunProvider()))
    selection = await selector.select("给我整理一份讲义")
    assert selection.intent == "HANDOUT"


def test_stub_selector_satisfies_protocol() -> None:
    assert isinstance(_StubSelector(RouterSelection(intent="ASK")), ToolSelector)
