"""收敛工具 → 既有 dispatch 的执行器桥接（MVP Phase D）。

把有界工具调用循环里的 :class:`ToolCall`（收敛工具）展开回旧意图，并：

- **生成类**（ASK/EXPLAIN_CODE → TutorAgent；FIX_BUG → DebugAgent；HANDOUT/MINDMAP/READING/STORYBOARD/
  PRACTICE/CODE_LAB → ResourceDesigner；PLAN → PathPlanner）：构造 :class:`PlanStep` 交给
  **既有** :class:`AgentDispatcher`（非流式，返回 :class:`DispatchResult`），回带 summary；
- **前端/交互/外部类**（VIDEO/PROGRESS/MY_STUDY/NAVIGATE/REVIEW/ASKME/BROWSE）：不经 dispatcher，
  回结构化前端指令（``{"frontend_action": intent, "params": ...}``），由上层在响应里承载。

设计：复用验证过的 dispatch 机制（不重造）；返回**高信号、token 经济**的 JSON 串作 tool_result。
依赖以 Protocol 注入（便于测试零 LLM）；本模块不导入具体 Agent，避免重依赖与循环 import。
"""

from __future__ import annotations

import json
from typing import Any, Protocol, runtime_checkable

from pythontopia_graph.graph.tool_loop import ToolCall
from pythontopia_graph.schemas.orchestrator import DispatchResult, Phase, PlanStep
from pythontopia_graph.tools.consolidated import expand_tool_call

# 旧意图分类：经 dispatcher 真派发的生成类工具。
_TUTOR_INTENTS: frozenset[str] = frozenset({"ASK", "EXPLAIN_CODE"})
_RESOURCE_INTENTS: frozenset[str] = frozenset(
    {"HANDOUT", "MINDMAP", "READING", "STORYBOARD", "PRACTICE", "CODE_LAB"}
)
_PRACTICE_INTENTS: frozenset[str] = frozenset({"PRACTICE", "CODE_LAB"})

_TUTOR_TITLE: dict[str, str] = {
    "ASK": "答疑",
    "EXPLAIN_CODE": "讲解代码",
    "FIX_BUG": "定位报错",
}


@runtime_checkable
class StepDispatcher(Protocol):
    """:class:`AgentDispatcher` 的结构化契约（便于注入 fake 做零 LLM 测试）。"""

    async def dispatch(
        self, step: PlanStep, user_id: int = 1, focus_hint_extra: str = ""
    ) -> DispatchResult: ...


def _kc_codes(args: dict[str, Any]) -> list[str]:
    raw = args.get("kc_codes")
    if isinstance(raw, list):
        return [str(code).strip() for code in raw if str(code).strip()]
    return []


def _build_step(intent: str, args: dict[str, Any]) -> PlanStep | None:
    """把旧意图 + 参数构造为可派发的 PlanStep；前端/交互/外部类返回 None（走指令路径）。"""
    if intent in _TUTOR_INTENTS:
        topic = str(args.get("topic") or _TUTOR_TITLE[intent])
        return PlanStep(
            id=f"tool-{intent}",
            phase="learn",
            title=topic,
            detail=str(args.get("code") or ""),
            agent="TutorAgent",
            kc_codes=_kc_codes(args),
        )
    if intent == "FIX_BUG":
        return PlanStep(
            id="tool-FIX_BUG",
            phase="practice",
            title=str(args.get("topic") or _TUTOR_TITLE[intent]),
            detail=str(args.get("code") or ""),
            agent="DebugAgent",
            kc_codes=_kc_codes(args),
            panel="bugmine",
        )
    if intent in _RESOURCE_INTENTS:
        phase: Phase = (
            "practice"
            if intent in _PRACTICE_INTENTS
            else "watch"
            if intent == "STORYBOARD"
            else "learn"
        )
        return PlanStep(
            id=f"tool-{intent}",
            phase=phase,
            title=str(args.get("topic") or intent),
            detail="",
            agent="ResourceDesignerAgent",
            kc_codes=_kc_codes(args),
            resource_type=intent,
            panel="workshop",
        )
    if intent == "PLAN":
        return PlanStep(
            id="tool-PLAN",
            phase="learn",
            title=str(args.get("topic") or "学习规划"),
            detail="",
            agent="PathPlannerAgent",
            kc_codes=_kc_codes(args),
        )
    return None


class RegistryToolExecutor:
    """收敛工具执行器：生成类经 dispatcher 真派发，前端/外部类回结构化指令。"""

    def __init__(self, dispatcher: StepDispatcher, *, user_id: int = 1) -> None:
        self._dispatcher = dispatcher
        self._user_id = user_id

    async def execute(self, call: ToolCall) -> str:
        intent, args = expand_tool_call(call.name, call.arguments)
        if intent is None:
            return json.dumps({"error": "unknown_tool", "tool": call.name}, ensure_ascii=False)

        step = _build_step(intent, args)
        if step is None:
            # 前端/交互/外部类：回结构化指令（实际 UI/能力动作由上层在响应里承载）。
            return json.dumps(
                {"frontend_action": intent, "params": args}, ensure_ascii=False
            )

        result = await self._dispatcher.dispatch(step, user_id=self._user_id)
        payload: dict[str, Any] = {
            "tool": intent,
            "ok": result.ok,
            "summary": result.summary,
        }
        if result.artifact:
            payload["artifact"] = result.artifact
        return json.dumps(payload, ensure_ascii=False)
