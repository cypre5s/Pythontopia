"""混合工具编排（MVP Phase F）：确定性路由器选择 + 有界循环编排。

依据 2026 实证（Codex/Claude Code 的工具选择稳健性主要来自**模型在环后训练**——这条
DeepSeek 用不了）：对未微调模型，"纯 LLM 从 schema 选工具"有结构性上限（held-out 实测仅
64%）。正确架构是**混合**——各取所长：

- **工具选择**交给既有 :class:`IntentRouterAgent`（确定性预判 + 关键词兜底 + LLM + 低置信
  澄清）——这是 DeepSeek 上能拿到的最稳健选择；
- **多步编排**交给有界 :class:`ToolCallingLoop`：把路由器选定的首工具作"种子"先执行，
  再让模型据结果决定是否续调（看结果再决策才是循环的价值）；
- provider 不支持工具调用（如 Spark）→ 优雅降级为"仅路由 + 执行首工具"，不做编排。

红线不变：有界、白名单、harness 治理、教育安全。默认无人调用（flag-gated 接线由后续负责）。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Protocol, runtime_checkable

from pydantic import BaseModel

from pythontopia_graph.agents.intent_router_agent import IntentRouterAgent
from pythontopia_graph.graph.tool_loop import (
    StepCallback,
    ToolCall,
    ToolCallingLoop,
    ToolExecutor,
    ToolLoopResult,
)
from pythontopia_graph.schemas.intent import CourseKc, IntentRouteRequest
from pythontopia_graph.tools.consolidated import consolidated_for_legacy
from pythontopia_graph.tools.registry import Scope


@dataclass
class RouterSelection:
    """路由器的工具选择结果（精简自 IntentRouteResponse）。"""

    intent: str
    kc_codes: list[str] = field(default_factory=list)
    focus_hint: str = ""
    target: str = ""
    needs_clarification: bool = False
    clarification: str = ""


@runtime_checkable
class ToolSelector(Protocol):
    """工具选择器：把用户消息映射到一个（旧意图）选择。便于注入 stub 测试。"""

    async def select(self, user_message: str) -> RouterSelection: ...


class IntentRouterSelector:
    """用既有 :class:`IntentRouterAgent` 做稳健选择（确定性 + 关键词 + LLM + 澄清）。"""

    def __init__(
        self,
        agent: IntentRouterAgent,
        *,
        course_kcs: list[CourseKc] | None = None,
        scope: Scope = "default",
    ) -> None:
        self._agent = agent
        self._course_kcs = course_kcs or []
        self._scope = scope

    async def select(self, user_message: str) -> RouterSelection:
        response = await self._agent.route(
            IntentRouteRequest(
                message=user_message, course_kcs=self._course_kcs, scope=self._scope
            )
        )
        return RouterSelection(
            intent=response.intent,
            kc_codes=list(response.kc_codes),
            focus_hint=response.focus_hint,
            target=response.target,
            needs_clarification=response.needs_clarification,
            clarification=response.clarification_prompt,
        )


class HybridResult(BaseModel):
    """混合编排终态：路由选择 + 种子工具 + 循环结果（或澄清短路）。"""

    intent: str
    seed_tool: str | None = None
    needs_clarification: bool = False
    clarification: str = ""
    loop: ToolLoopResult | None = None


def _seed_tool_call(selection: RouterSelection, user_message: str) -> ToolCall | None:
    """把路由器选的旧意图映射成收敛工具的种子调用；无法映射 → None（循环自选）。"""
    mapping = consolidated_for_legacy(selection.intent)
    if mapping is None:
        return None
    name, patch = mapping
    args: dict[str, object] = dict(patch)
    if selection.kc_codes:
        args["kc_codes"] = list(selection.kc_codes)
    if selection.focus_hint:
        args["topic"] = selection.focus_hint
    # 工具特定参数（覆盖通用 topic/kc_codes）。
    if name == "navigate":
        args = {"target": selection.target} if selection.target else {}
    elif name == "browse":
        args = {"goal": selection.focus_hint or user_message}
    elif name == "ask" and patch.get("mode") in {"explain_code", "fix_bug"}:
        args["code"] = user_message
    return ToolCall(id="seed-0", name=name, arguments=args)


class HybridToolOrchestrator:
    """混合编排器：路由器选首工具 → 种子进有界循环做多步编排。"""

    def __init__(
        self,
        *,
        selector: ToolSelector,
        loop: ToolCallingLoop,
        executor: ToolExecutor,
    ) -> None:
        self._selector = selector
        self._loop = loop
        self._executor = executor

    async def run(
        self,
        *,
        system_prompt: str,
        user_message: str,
        on_step: StepCallback | None = None,
    ) -> HybridResult:
        selection = await self._selector.select(user_message)
        if selection.needs_clarification:
            # 低置信 → 先澄清，不进循环（Claude Code 同款：歧义就问，不硬猜）。
            return HybridResult(
                intent=selection.intent,
                needs_clarification=True,
                clarification=selection.clarification,
            )
        seed = _seed_tool_call(selection, user_message)
        loop_result = await self._loop.run(
            system_prompt=system_prompt,
            user_message=user_message,
            executor=self._executor,
            forced_first_tool=seed,
            on_step=on_step,
        )
        # 优雅降级：provider 不支持工具调用 → 仅执行路由器选定的首工具（无多步编排）。
        if loop_result.stop_reason == "no_tool_calling" and seed is not None:
            result_text = await self._executor.execute(seed)
            loop_result = ToolLoopResult(
                final_text=result_text,
                steps=[],
                tool_trail=[seed.name],
                stop_reason="router_only",
                rounds=0,
            )
        return HybridResult(
            intent=selection.intent,
            seed_tool=seed.name if seed else None,
            loop=loop_result,
        )
