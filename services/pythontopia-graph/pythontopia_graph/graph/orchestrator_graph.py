"""OrchestratorGraph – hand-rolled async orchestrator for the 学习总管.

Mirrors ``graph/resource_graph.py``: a ``GraphTraceStep`` list is the state, each
node is timed via ``_step``, and ``run_streaming`` emits SSE events. There is no
real LangGraph ``StateGraph`` here (consistent with the rest of the service).

Pipeline (non-streaming ``run`` and streaming ``run_streaming`` share
``_run_core``):

    understand
      ├─ ambiguous → compose_reply(clarifying) → needs_user_input
      └─ else:
           decompose (or, on a 'negotiate' intent, mutate the stored plan)
           dispatch.<agent> for every step
           replan: insert a DebugAgent 矿洞/补练 step before assess on failures
           extract_profile
           compose_reply

Trace node literals: understand, decompose, dispatch.<agent>, replan,
extract_profile, compose_reply.
"""

from __future__ import annotations

import time
from collections.abc import Awaitable, Callable
from typing import Any, TypeVar

from pythontopia_graph.agents.orchestrator_agent import OrchestratorAgent
from pythontopia_graph.graph.orchestrator_session import (
    append_turns,
    get_history,
    get_plan,
    merge_profile_signals,
    set_plan,
)
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.schemas.orchestrator import (
    DispatchResult,
    OrchestratorRequest,
    OrchestratorResponse,
    OrchestratorTurn,
    PlanStep,
    ProfileSignal,
)
from pythontopia_graph.schemas.resource import (
    GraphTraceStep,
    GroundingResult,
    SafetyResult,
)

T = TypeVar("T")

Emit = Callable[[str, dict[str, Any]], Awaitable[None]]


class OrchestratorGraph:
    def __init__(self, *, provider: LlmProvider) -> None:
        self._provider = provider
        self._agent = OrchestratorAgent(provider)

    async def run(self, req: OrchestratorRequest) -> OrchestratorResponse:
        return await self._run_core(req, None)

    async def run_streaming(self, req: OrchestratorRequest, emit: Emit) -> None:
        try:
            response = await self._run_core(req, emit)
            await emit("result", response.model_dump(mode="json"))
        except Exception as exc:  # noqa: BLE001 – surface as an SSE error event
            await emit("error", {"code": "ORCHESTRATOR_FAILED", "message": str(exc)})

    # ── shared pipeline ───────────────────────────────────────────────────
    async def _run_core(
        self, req: OrchestratorRequest, emit: Emit | None
    ) -> OrchestratorResponse:
        trace: list[GraphTraceStep] = []

        # Server memory is the source of truth; client history is the fallback.
        server_history = await get_history(req.session_id)
        merged_history = server_history if server_history else list(req.history)
        merged_req = req.model_copy(update={"history": merged_history})
        allowed_set = set(req.course_kc_index)

        understanding = await self._timed(
            trace, emit, "understand", self._agent.understand(merged_req)
        )
        intent = understanding["intent"]
        goal = understanding["goal"]

        if understanding["ambiguous"]:
            return await self._handle_ambiguous(
                req, merged_req, trace, emit, understanding["clarifying_question"]
            )

        plan = await self._build_plan(req, merged_req, goal, intent, trace, emit)

        dispatch_results: list[DispatchResult] = []
        for step in plan:
            result = await self._timed(
                trace, emit, f"dispatch.{step.agent}", self._dispatch_step(step)
            )
            dispatch_results.append(result)

        if intent != "negotiate" and req.recent_failures:
            await self._maybe_replan_failures(
                req, plan, dispatch_results, allowed_set, trace, emit
            )

        self._activate_first(plan)

        signals = await self._timed(
            trace, emit, "extract_profile", self._agent.extract_profile(merged_req)
        )

        reply_md = await self._compose(trace, emit, merged_req, plan, None)

        safety_passed, safety_reasons = OrchestratorAgent.scan_safety(reply_md)
        grounding = self._grounding(plan, allowed_set)
        done = bool(plan) and all(step.status in ("done", "skipped") for step in plan)

        response = OrchestratorResponse(
            reply_md=reply_md,
            clarifying_question=None,
            needs_user_input=False,
            plan=plan,
            dispatch_results=dispatch_results,
            profile_updates=signals,
            trace=trace,
            done=done,
            safety_result=SafetyResult(passed=safety_passed, reasons=safety_reasons),
            grounding_result=grounding,
        )
        await self._persist(req, reply_md, plan=plan, signals=signals)
        return response

    async def _handle_ambiguous(
        self,
        req: OrchestratorRequest,
        merged_req: OrchestratorRequest,
        trace: list[GraphTraceStep],
        emit: Emit | None,
        clarifying: str | None,
    ) -> OrchestratorResponse:
        reply_md = await self._compose(trace, emit, merged_req, [], clarifying)
        safety_passed, safety_reasons = OrchestratorAgent.scan_safety(reply_md)
        response = OrchestratorResponse(
            reply_md=reply_md,
            clarifying_question=clarifying,
            needs_user_input=True,
            plan=[],
            dispatch_results=[],
            profile_updates=[],
            trace=trace,
            done=False,
            safety_result=SafetyResult(passed=safety_passed, reasons=safety_reasons),
            grounding_result=GroundingResult(passed=True),
        )
        # Keep any previously stored plan untouched while we wait for clarification.
        await self._persist(req, reply_md, plan=None, signals=[])
        return response

    async def _build_plan(
        self,
        req: OrchestratorRequest,
        merged_req: OrchestratorRequest,
        goal: str,
        intent: str,
        trace: list[GraphTraceStep],
        emit: Emit | None,
    ) -> list[PlanStep]:
        if intent == "negotiate":
            stored_plan = await get_plan(req.session_id)
            if stored_plan:
                plan = self._negotiate(merged_req.user_message, stored_plan)
                self._record_sync(
                    trace,
                    "replan",
                    True,
                    note=f"negotiate:{self._negotiation_kind(merged_req.user_message)}",
                )
                await self._emit_trace(trace, emit)
                return plan
        return await self._timed(
            trace, emit, "decompose", self._agent.decompose(merged_req, goal)
        )

    async def _maybe_replan_failures(
        self,
        req: OrchestratorRequest,
        plan: list[PlanStep],
        dispatch_results: list[DispatchResult],
        allowed_set: set[str],
        trace: list[GraphTraceStep],
        emit: Emit | None,
    ) -> None:
        failed = self._ground_failures(req, allowed_set)
        if not failed or any(step.agent == "DebugAgent" for step in plan):
            return
        debug_step = self._build_debug_step(failed)
        self._insert_before_assess(plan, debug_step)
        self._record_sync(
            trace, "replan", True, note=f"recent_failures:{','.join(failed[:3])}"
        )
        await self._emit_trace(trace, emit)
        result = await self._timed(
            trace, emit, "dispatch.DebugAgent", self._dispatch_step(debug_step)
        )
        dispatch_results.append(result)

    # ── reply composition (streams tokens when emit is set) ───────────────
    async def _compose(
        self,
        trace: list[GraphTraceStep],
        emit: Emit | None,
        req: OrchestratorRequest,
        plan: list[PlanStep],
        clarifying: str | None,
    ) -> str:
        started = time.monotonic()
        parts: list[str] = []
        try:
            async for token in self._agent.reply_stream(req, plan, clarifying):
                parts.append(token)
                if emit is not None:
                    await emit("token", {"delta": token})
        except Exception as exc:  # noqa: BLE001 – record then re-raise to error event
            elapsed_ms = int((time.monotonic() - started) * 1000)
            trace.append(
                GraphTraceStep(
                    node="compose_reply", elapsed_ms=elapsed_ms, status="fail", note=str(exc)
                )
            )
            raise
        elapsed_ms = int((time.monotonic() - started) * 1000)
        trace.append(GraphTraceStep(node="compose_reply", elapsed_ms=elapsed_ms, status="ok"))
        await self._emit_trace(trace, emit)
        return "".join(parts)

    # ── dispatch / replan helpers ─────────────────────────────────────────
    async def _dispatch_step(self, step: PlanStep) -> DispatchResult:
        panel = step.panel or "-"
        if step.agent == "DebugAgent":
            summary = f"已将「{step.title}」分派给 DebugAgent，前往矿洞补练并复盘（面板: {panel}）"
        else:
            summary = f"已将「{step.title}」分派给 {step.agent}（面板: {panel}）"
        return DispatchResult(
            step_id=step.id, agent=step.agent, ok=True, summary=summary, note=None
        )

    @staticmethod
    def _ground_failures(req: OrchestratorRequest, allowed_set: set[str]) -> list[str]:
        grounded: list[str] = []
        for code in req.recent_failures:
            if allowed_set and code not in allowed_set:
                continue
            if code not in grounded:
                grounded.append(code)
        return grounded

    @staticmethod
    def _build_debug_step(failed: list[str]) -> PlanStep:
        return PlanStep(
            id="s-debug-replan",
            phase="practice",
            title="补练：矿洞修 Bug",
            detail=(
                f"针对最近失败的 KC {'、'.join(failed[:3])} 进入矿洞补练并复盘，"
                "先定位错误再重练。"
            ),
            agent="DebugAgent",
            kc_codes=failed[:3],
            panel="workshop",
            status="pending",
            resource_type=None,
        )

    @staticmethod
    def _insert_before_assess(plan: list[PlanStep], step: PlanStep) -> None:
        for index, existing in enumerate(plan):
            if existing.phase == "assess":
                plan.insert(index, step)
                return
        plan.append(step)

    @staticmethod
    def _activate_first(plan: list[PlanStep]) -> None:
        if any(step.status == "active" for step in plan):
            return
        for step in plan:
            if step.status == "pending":
                step.status = "active"
                return

    # ── plan negotiation (mutate, do not rebuild) ─────────────────────────
    def _negotiate(self, message: str, stored_plan: list[PlanStep]) -> list[PlanStep]:
        kind = self._negotiation_kind(message)
        plan = [step.model_copy(deep=True) for step in stored_plan]
        if kind == "skip":
            for step in plan:
                if step.status in ("pending", "active"):
                    step.status = "skipped"
                    step.detail = self._annotate(step.detail, "（已按你的要求跳过）")
                    break
            return plan
        for step in plan:
            if kind == "lower":
                if step.phase == "learn":
                    step.resource_type = "READING"
                step.detail = self._annotate(step.detail, "（已降低难度，放慢节奏）")
            elif kind == "raise":
                if step.phase == "learn":
                    step.resource_type = "CODE_LAB"
                step.detail = self._annotate(step.detail, "（已提升难度与综合度）")
            elif kind == "swap_example" and step.phase in ("learn", "practice"):
                step.detail = self._annotate(step.detail, "（已更换示例/场景）")
        return plan

    @staticmethod
    def _negotiation_kind(message: str) -> str:
        lowered = message.lower()
        if any(token in lowered for token in ("太简单", "太容易", "会了", "无聊")):
            return "raise"
        if any(token in lowered for token in ("跳过", "下一个", "skip")):
            return "skip"
        if any(token in lowered for token in ("换个例子", "换一个", "换种", "另一个例子")):
            return "swap_example"
        return "lower"

    @staticmethod
    def _annotate(detail: str, marker: str) -> str:
        return detail if marker in detail else f"{detail}{marker}"

    # ── grounding ─────────────────────────────────────────────────────────
    @staticmethod
    def _grounding(plan: list[PlanStep], allowed_set: set[str]) -> GroundingResult:
        if not allowed_set:
            return GroundingResult(passed=True, no_evidence_found=True)
        invalid: list[str] = []
        for step in plan:
            for code in step.kc_codes:
                if code not in allowed_set and code not in invalid:
                    invalid.append(code)
        if invalid:
            return GroundingResult(
                passed=False, reasons=[f"UNKNOWN_KC:{code}" for code in invalid]
            )
        return GroundingResult(passed=True)

    # ── session persistence ───────────────────────────────────────────────
    async def _persist(
        self,
        req: OrchestratorRequest,
        reply_md: str,
        *,
        plan: list[PlanStep] | None,
        signals: list[ProfileSignal],
    ) -> None:
        await append_turns(
            req.session_id,
            [
                OrchestratorTurn(role="user", content=req.user_message),
                OrchestratorTurn(role="assistant", content=reply_md),
            ],
        )
        if plan is not None:
            await set_plan(req.session_id, plan)
        if signals:
            await merge_profile_signals(req.session_id, signals)

    # ── trace plumbing (mirrors resource_graph) ───────────────────────────
    async def _timed(
        self,
        trace: list[GraphTraceStep],
        emit: Emit | None,
        name: str,
        awaitable: Awaitable[T],
    ) -> T:
        result = await self._step(trace, name, awaitable)
        await self._emit_trace(trace, emit)
        return result

    async def _step(
        self, trace: list[GraphTraceStep], name: str, awaitable: Awaitable[T]
    ) -> T:
        started = time.monotonic()
        try:
            result = await awaitable
        except Exception as exc:  # noqa: BLE001 – record fail then re-raise
            elapsed_ms = int((time.monotonic() - started) * 1000)
            trace.append(
                GraphTraceStep(node=name, elapsed_ms=elapsed_ms, status="fail", note=str(exc))
            )
            raise
        elapsed_ms = int((time.monotonic() - started) * 1000)
        trace.append(GraphTraceStep(node=name, elapsed_ms=elapsed_ms, status="ok"))
        return result

    async def _emit_trace(self, trace: list[GraphTraceStep], emit: Emit | None) -> None:
        if emit is not None:
            await emit("trace", trace[-1].model_dump(mode="json"))

    @staticmethod
    def _record_sync(
        trace: list[GraphTraceStep], name: str, passed: bool, note: str | None = None
    ) -> None:
        trace.append(
            GraphTraceStep(
                node=name,
                elapsed_ms=0,
                status="ok" if passed else "retry",
                note=note,
            )
        )
