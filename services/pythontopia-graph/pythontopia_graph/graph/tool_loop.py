"""有界原生工具调用循环引擎（MVP Phase C）。

让模型在循环里自主调用带参数的工具（OpenAI 兼容 function calling）：

    call provider(tools) → tool_calls? → 执行每个工具(经 harness 治理) → 回喂 tool_result
    → 再 call provider … 直到模型不再调工具（end_turn）或触达 max_steps（有界停止）。

这是「成熟 tool use」的运行时心脏，但**严格有界、非自由 ReAct**：

- 显式 ``max_steps`` 上限 + ``BudgetGuard``（LLM 次数/token/墙钟）三道预算闸；
- 工具白名单 = 注册表 ``TOOLS``（模型臆造的未知工具一律丢弃，绝不执行）；
- 每次工具执行经 :func:`run_with_governance`（超时/退避重试/熔断/结构化观测）；治理失败
  回喂结构化 error（让模型换策略），绝不裸抛打断循环；
- provider 无工具调用能力（如 Spark）→ ``stop_reason="no_tool_calling"``，由调用方回落
  既有意图分类器路由（零回归）；
- 触上界 / 预算耗尽 → 诚实停止文案，不编造。

本模块自包含、纯逻辑 + 注入式 IO（provider / executor 均注入），默认无人调用 → 零回归；
编排接线（把它接进 orchestrator 取代/并存分类器路径）由后续阶段在 flag 下进行。
"""

from __future__ import annotations

import json
import logging
from collections.abc import Awaitable, Callable
from typing import Any, Protocol, cast, runtime_checkable

from pydantic import BaseModel, Field

from pythontopia_graph.llm.provider import LlmProvider, provider_supports_tool_calling
from pythontopia_graph.tools.harness import BudgetGuard, run_with_governance
from pythontopia_graph.tools.registry import TOOLS, Scope, tool_schemas_for_scope

LOG = logging.getLogger(__name__)

# 有界上限（非自由 ReAct）：单轮工具调用循环最多 N 步。
DEFAULT_MAX_TOOL_STEPS = 4
# 每请求 LLM 子调用次数上限（循环每轮一次模型调用）。
DEFAULT_MAX_LLM_CALLS = 6

StopReason = str  # end_turn | max_steps | budget | no_tool_calling

# 每步执行后的可选回调（供 SSE 增量推送 loop_step；fail-soft 由调用方保证）。
StepCallback = Callable[["ToolLoopStep"], Awaitable[None]]


class ToolCall(BaseModel):
    """一次结构化工具调用（已校验工具名 ∈ 注册表）。"""

    id: str
    name: str
    arguments: dict[str, Any] = Field(default_factory=dict)


class ToolLoopStep(BaseModel):
    """循环中一次工具执行的可审计记录。"""

    step: int = Field(ge=0)
    tool_name: str
    ok: bool
    status: str  # 来自 harness ToolObservation.status
    summary: str = ""


class ToolLoopResult(BaseModel):
    """有界工具调用循环终态。"""

    final_text: str = ""
    steps: list[ToolLoopStep] = Field(default_factory=list)
    tool_trail: list[str] = Field(default_factory=list)
    stop_reason: StopReason
    rounds: int = Field(default=0, ge=0)


@runtime_checkable
class ToolExecutor(Protocol):
    """工具执行器：把一次工具调用映射到既有 dispatch 机制，返回回喂模型的结果文本。

    返回值应为高信号、token 经济的字符串（建议结构化 JSON 串）；实现内部负责接既有
    TutorAgent / 资源 Agent / 前端工具语义。引擎对其结果不做信任假设（仍经 harness 治理）。
    """

    async def execute(self, call: ToolCall) -> str: ...


def _parse_arguments(raw: Any) -> dict[str, Any]:
    """把 function.arguments（JSON 串或 dict）解析为 dict；畸形 → 空 dict（fail-soft）。"""
    if isinstance(raw, dict):
        return cast("dict[str, Any]", raw)
    if isinstance(raw, str):
        try:
            parsed = json.loads(raw)
        except (ValueError, TypeError):
            return {}
        return parsed if isinstance(parsed, dict) else {}
    return {}


def coerce_tool_calls(
    raw: list[dict[str, Any]] | None,
    valid_names: set[str] | frozenset[str] | None = None,
) -> list[ToolCall]:
    """把 provider 返回的 tool_calls 收敛为已校验 ToolCall 列表。

    白名单 ``valid_names``：仅放行其中的工具（缺省 = 注册表 17 工具）；模型臆造/越界的
    工具一律丢弃，绝不执行。
    """
    names = valid_names if valid_names is not None else frozenset(TOOLS)
    calls: list[ToolCall] = []
    for index, item in enumerate(raw or []):
        function = item.get("function") or {}
        name = function.get("name")
        if not isinstance(name, str) or name not in names:
            LOG.debug("丢弃未知/越界工具调用：%r", name)
            continue
        call_id = item.get("id")
        calls.append(
            ToolCall(
                id=str(call_id) if call_id else f"call_{index}",
                name=name,
                arguments=_parse_arguments(function.get("arguments")),
            )
        )
    return calls


class ToolCallingLoop:
    """有界原生工具调用循环（纯逻辑 + 注入式 provider/executor；无进程内可变全局）。"""

    def __init__(
        self,
        *,
        provider: LlmProvider,
        scope: Scope = "default",
        max_steps: int = DEFAULT_MAX_TOOL_STEPS,
        max_llm_calls: int = DEFAULT_MAX_LLM_CALLS,
        max_tokens: int | None = None,
        max_wall_s: float | None = None,
        tools: list[dict[str, Any]] | None = None,
        valid_names: set[str] | frozenset[str] | None = None,
        temperature: float | None = None,
    ) -> None:
        # 业务 fail-fast：循环必须有界，上限非法直接拒绝（杜绝退化为无界 ReAct）。
        if max_steps < 1:
            raise ValueError("max_steps 必须 >= 1（有界循环不可无界）")
        self._provider = provider
        self._scope = scope
        self._max_steps = max_steps
        self._max_llm_calls = max_llm_calls
        self._max_tokens = max_tokens
        self._max_wall_s = max_wall_s
        # 可选采样温度（低温 → 工具选择更可复现，便于 eval 降噪）；None = 用 provider 默认。
        self._temperature = temperature
        # 工具表与白名单可注入（默认 = 注册表 17 工具；Phase D 注入收敛后的 9 工具）。
        self._tools = tools if tools is not None else tool_schemas_for_scope(scope)
        self._valid_names: frozenset[str] = (
            frozenset(valid_names) if valid_names is not None else frozenset(TOOLS)
        )

    async def run(
        self,
        *,
        system_prompt: str,
        user_message: str,
        executor: ToolExecutor,
        forced_first_tool: ToolCall | None = None,
        on_step: StepCallback | None = None,
    ) -> ToolLoopResult:
        """驱动有界工具调用循环；provider 无能力时 stop_reason=no_tool_calling（调用方回落）。

        ``forced_first_tool``（混合架构）：用既有路由器选定的首工具作"种子"先确定性执行一步
        （稳健选择），再进循环让模型据结果做多步编排（编排）。种子不消耗模型预算。
        ``on_step``：每步执行后回调（供 SSE 增量推送）；异常由调用方 fail-soft 处理。
        """
        if not provider_supports_tool_calling(self._provider):
            return ToolLoopResult(stop_reason="no_tool_calling")

        messages: list[dict[str, Any]] = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_message},
        ]
        budget = BudgetGuard(
            max_llm_calls=self._max_llm_calls,
            max_tokens=self._max_tokens,
            max_wall_s=self._max_wall_s,
        )
        steps: list[ToolLoopStep] = []
        trail: list[str] = []
        rounds = 0

        if forced_first_tool is not None and forced_first_tool.name in self._valid_names:
            seed_step, seed_result = await self._run_one(0, forced_first_tool, executor)
            steps.append(seed_step)
            trail.append(forced_first_tool.name)
            if on_step is not None:
                await on_step(seed_step)
            messages.append(
                {
                    "role": "assistant",
                    "content": "",
                    "tool_calls": [
                        {
                            "id": forced_first_tool.id,
                            "type": "function",
                            "function": {
                                "name": forced_first_tool.name,
                                "arguments": json.dumps(
                                    forced_first_tool.arguments, ensure_ascii=False
                                ),
                            },
                        }
                    ],
                }
            )
            messages.append(
                {"role": "tool", "tool_call_id": forced_first_tool.id, "content": seed_result}
            )

        for _ in range(self._max_steps):
            if budget.deadline_exceeded() or not budget.try_acquire("tool_loop_llm"):
                return self._finish(steps, trail, rounds, "budget")
            rounds += 1
            chat_kwargs: dict[str, Any] = {"tools": self._tools, "json_mode": False}
            if self._temperature is not None:
                chat_kwargs["temperature"] = self._temperature
            response = await self._provider.chat(
                cast("list[dict[str, str]]", messages), **chat_kwargs
            )
            calls = coerce_tool_calls(response.tool_calls, self._valid_names)
            if not calls:
                # 模型不再调工具 → 终态文本即最终答复。
                return ToolLoopResult(
                    final_text=response.content_text or "",
                    steps=steps,
                    tool_trail=trail,
                    stop_reason="end_turn",
                    rounds=rounds,
                )
            # assistant 轮必须携带原始 tool_calls 回填（API 契约）。
            messages.append(
                {
                    "role": "assistant",
                    "content": response.content_text or "",
                    "tool_calls": response.tool_calls,
                }
            )
            for call in calls:
                step, result_text = await self._run_one(len(steps), call, executor)
                steps.append(step)
                trail.append(call.name)
                if on_step is not None:
                    await on_step(step)
                messages.append(
                    {"role": "tool", "tool_call_id": call.id, "content": result_text}
                )

        # 触达 max_steps：有界停止（诚实，不再继续调工具）。
        return self._finish(steps, trail, rounds, "max_steps")

    async def _run_one(
        self, step_index: int, call: ToolCall, executor: ToolExecutor
    ) -> tuple[ToolLoopStep, str]:
        """经 harness 治理执行一次工具调用；治理失败回喂结构化 error（不裸抛）。"""
        # 治理参数取自注册表（legacy 17 工具）；收敛工具名不在注册表 → 用安全默认值。
        spec = TOOLS.get(call.name)
        timeout_s = (spec.timeout_s if spec else 0.0) or 60.0
        max_retries = spec.max_retries if spec else 0

        async def _factory() -> str:
            return await executor.execute(call)

        observation = await run_with_governance(
            call.name,
            _factory,
            timeout_s=timeout_s,
            max_retries=max_retries,
            breaker_key="tool_loop",
        )
        if observation.ok and isinstance(observation.value, str):
            result_text = observation.value
        elif observation.ok and observation.value is not None:
            result_text = json.dumps(observation.value, ensure_ascii=False, default=str)
        else:
            # 治理失败（超时/熔断/错误）：回喂结构化 error，让模型换策略而非崩溃。
            result_text = json.dumps(
                {"error": observation.status, "tool": call.name}, ensure_ascii=False
            )
        step = ToolLoopStep(
            step=step_index,
            tool_name=call.name,
            ok=observation.ok,
            status=observation.status,
            summary=result_text[:160],
        )
        return step, result_text

    def _finish(
        self,
        steps: list[ToolLoopStep],
        trail: list[str],
        rounds: int,
        reason: StopReason,
    ) -> ToolLoopResult:
        return ToolLoopResult(
            final_text=self._honest_stop(reason),
            steps=steps,
            tool_trail=trail,
            stop_reason=reason,
            rounds=rounds,
        )

    @staticmethod
    def _honest_stop(reason: StopReason) -> str:
        """触上界/预算耗尽时的诚实停止文案（不编造、给可执行下一步）。"""
        return (
            "我已达到本轮工具调用的安全上限，先停在这里（避免无界循环）。\n"
            "可以把问题拆小一点，或告诉我你最想先解决哪一步，我们继续。"
        )
