"""OrchestratorGraph – a real LangGraph ``StateGraph`` for the 学习总管.

R-14: this used to be a hand-rolled async pipeline. It is now a genuine
``langgraph.graph.StateGraph`` (compiled once per instance). The graph drives all
control flow via real nodes + **conditional edges**:

    START → understand
      ├─ (ambiguous?)        → clarify → END
      └─ else → build_plan → dispatch
                                ├─ (recent failures & not negotiate?) → replan
                                │                                          ↓
                                └──────────────────────────────────→ extract_profile
                                                                          ↓
                                                                       compose → END

Each node wraps the existing ``OrchestratorAgent`` methods and emits the SAME SSE
events as before (``trace`` after each node, ``token`` while composing) through an
``emit`` callback stored on the instance for the duration of one run, so the
frontend ``useSseStream`` contract is byte-for-byte unchanged. ``trace`` accumulates
via an additive channel reducer; multi-turn memory still lives in
``orchestrator_session``.

Trace node literals (unchanged): understand, decompose | replan, dispatch.<agent>,
extract_profile, compose_reply.
"""

from __future__ import annotations

import operator
import time
from collections.abc import Awaitable, Callable
from typing import Annotated, Any, TypedDict

from langgraph.graph import END, START, StateGraph

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

Emit = Callable[[str, dict[str, Any]], Awaitable[None]]


async def _noop_emit(_event: str, _data: dict[str, Any]) -> None:
    """Default sink for the non-streaming ``run`` path (swallows events)."""
    return None


class OrchestratorState(TypedDict, total=False):
    """Shared LangGraph state. ``trace`` / ``dispatch_results`` accumulate."""

    # inputs (set once on the initial state)
    req: OrchestratorRequest
    allowed_set: set[str]
    # produced by understand
    intent: str
    goal: str
    ambiguous: bool
    clarifying: str | None
    needs_user_input: bool
    # produced downstream
    plan: list[PlanStep]
    dispatch_results: Annotated[list[DispatchResult], operator.add]
    signals: list[ProfileSignal]
    reply_md: str
    trace: Annotated[list[GraphTraceStep], operator.add]


class OrchestratorGraph:
    def __init__(self, *, provider: LlmProvider) -> None:
        self._provider = provider
        self._agent = OrchestratorAgent(provider)
        # Active emit sink for the current run (per-request instance; single run
        # at a time, so storing it on self is safe and avoids threading it
        # through every node/config).
        self._emit_cb: Emit = _noop_emit
        self._compiled = self._build_compiled()

    # ── public API (unchanged signatures) ─────────────────────────────────
    async def run(self, req: OrchestratorRequest) -> OrchestratorResponse:
        return await self._invoke(req, _noop_emit)

    async def run_streaming(self, req: OrchestratorRequest, emit: Emit) -> None:
        try:
            response = await self._invoke(req, emit)
            await emit("result", response.model_dump(mode="json"))
        except Exception as exc:  # noqa: BLE001 – surface as an SSE error event
            await emit("error", {"code": "ORCHESTRATOR_FAILED", "message": str(exc)})

    # ── graph construction ────────────────────────────────────────────────
    def _build_compiled(self) -> Any:
        graph: StateGraph = StateGraph(OrchestratorState)
        graph.add_node("understand", self._node_understand)
        graph.add_node("build_plan", self._node_build_plan)
        graph.add_node("dispatch", self._node_dispatch)
        graph.add_node("replan", self._node_replan_failures)
        graph.add_node("extract_profile", self._node_extract_profile)
        graph.add_node("compose", self._node_compose)
        graph.add_node("clarify", self._node_clarify)

        graph.add_edge(START, "understand")
        graph.add_conditional_edges(
            "understand",
            self._route_after_understand,
            {"clarify": "clarify", "build_plan": "build_plan"},
        )
        graph.add_edge("build_plan", "dispatch")
        graph.add_conditional_edges(
            "dispatch",
            self._route_after_dispatch,
            {"replan": "replan", "extract_profile": "extract_profile"},
        )
        graph.add_edge("replan", "extract_profile")
        graph.add_edge("extract_profile", "compose")
        graph.add_edge("compose", END)
        graph.add_edge("clarify", END)
        return graph.compile()

    # ── orchestration entrypoint ──────────────────────────────────────────
    async def _invoke(self, req: OrchestratorRequest, emit: Emit) -> OrchestratorResponse:
        self._emit_cb = emit

        # Server memory is the source of truth; client history is the fallback.
        server_history = await get_history(req.session_id)
        merged_history = server_history if server_history else list(req.history)
        merged_req = req.model_copy(update={"history": merged_history})
        allowed_set = set(req.course_kc_index)

        initial: OrchestratorState = {
            "req": merged_req,
            "allowed_set": allowed_set,
            "trace": [],
            "dispatch_results": [],
        }
        final: dict[str, Any] = await self._compiled.ainvoke(initial)

        response = self._assemble(allowed_set, final)
        await self._persist(req, response)
        return response

    # ── nodes ──────────────────────────────────────────────────────────────
    async def _node_understand(self, state: OrchestratorState) -> dict[str, Any]:
        req = state["req"]
        started = time.monotonic()
        understanding = await self._agent.understand(req)
        step = self._ok_step("understand", started)
        await self._emit_cb("trace", step.model_dump(mode="json"))
        return {
            "intent": understanding["intent"],
            "goal": understanding["goal"],
            "ambiguous": understanding["ambiguous"],
            "clarifying": understanding["clarifying_question"],
            "trace": [step],
        }

    async def _node_build_plan(self, state: OrchestratorState) -> dict[str, Any]:
        req = state["req"]
        # 'negotiate' mutates the stored plan in place instead of rebuilding it.
        if state["intent"] == "negotiate":
            stored_plan = await get_plan(req.session_id)
            if stored_plan:
                plan = self._negotiate(req.user_message, stored_plan)
                note = f"negotiate:{self._negotiation_kind(req.user_message)}"
                step = self._sync_step("replan", note=note)
                await self._emit_cb("trace", step.model_dump(mode="json"))
                return {"plan": plan, "trace": [step]}
        started = time.monotonic()
        plan = await self._agent.decompose(req, state["goal"])
        step = self._ok_step("decompose", started)
        await self._emit_cb("trace", step.model_dump(mode="json"))
        return {"plan": plan, "trace": [step]}

    async def _node_dispatch(self, state: OrchestratorState) -> dict[str, Any]:
        results: list[DispatchResult] = []
        traces: list[GraphTraceStep] = []
        for step in state["plan"]:
            started = time.monotonic()
            result = await self._dispatch_step(step)
            ts = self._ok_step(f"dispatch.{step.agent}", started)
            await self._emit_cb("trace", ts.model_dump(mode="json"))
            results.append(result)
            traces.append(ts)
        return {"dispatch_results": results, "trace": traces}

    async def _node_replan_failures(self, state: OrchestratorState) -> dict[str, Any]:
        req = state["req"]
        plan = list(state["plan"])
        failed = self._ground_failures(req, state["allowed_set"])
        debug_step = self._build_debug_step(failed)
        self._insert_before_assess(plan, debug_step)
        replan_step = self._sync_step(
            "replan", note=f"recent_failures:{','.join(failed[:3])}"
        )
        await self._emit_cb("trace", replan_step.model_dump(mode="json"))
        started = time.monotonic()
        result = await self._dispatch_step(debug_step)
        dispatch_step = self._ok_step("dispatch.DebugAgent", started)
        await self._emit_cb("trace", dispatch_step.model_dump(mode="json"))
        return {
            "plan": plan,
            "dispatch_results": [result],
            "trace": [replan_step, dispatch_step],
        }

    async def _node_extract_profile(self, state: OrchestratorState) -> dict[str, Any]:
        req = state["req"]
        plan = list(state["plan"])
        self._activate_first(plan)
        started = time.monotonic()
        signals = await self._agent.extract_profile(req)
        step = self._ok_step("extract_profile", started)
        await self._emit_cb("trace", step.model_dump(mode="json"))
        return {"plan": plan, "signals": signals, "trace": [step]}

    async def _node_compose(self, state: OrchestratorState) -> dict[str, Any]:
        reply_md = await self._stream_reply(state["req"], state["plan"], None)
        return {"reply_md": reply_md}

    async def _node_clarify(self, state: OrchestratorState) -> dict[str, Any]:
        reply_md = await self._stream_reply(state["req"], [], state.get("clarifying"))
        return {"reply_md": reply_md, "needs_user_input": True}

    # ── conditional routers ───────────────────────────────────────────────
    @staticmethod
    def _route_after_understand(state: OrchestratorState) -> str:
        return "clarify" if state.get("ambiguous") else "build_plan"

    def _route_after_dispatch(self, state: OrchestratorState) -> str:
        if state["intent"] == "negotiate":
            return "extract_profile"
        failed = self._ground_failures(state["req"], state["allowed_set"])
        if failed and not any(step.agent == "DebugAgent" for step in state["plan"]):
            return "replan"
        return "extract_profile"

    # ── reply composition (streams tokens through the emit sink) ───────────
    async def _stream_reply(
        self, req: OrchestratorRequest, plan: list[PlanStep], clarifying: str | None
    ) -> str:
        started = time.monotonic()
        parts: list[str] = []
        try:
            async for token in self._agent.reply_stream(req, plan, clarifying):
                parts.append(token)
                await self._emit_cb("token", {"delta": token})
        except Exception as exc:  # noqa: BLE001 – record fail trace then re-raise
            fail = GraphTraceStep(
                node="compose_reply",
                elapsed_ms=int((time.monotonic() - started) * 1000),
                status="fail",
                note=str(exc),
            )
            await self._emit_cb("trace", fail.model_dump(mode="json"))
            raise
        step = self._ok_step("compose_reply", started)
        await self._emit_cb("trace", step.model_dump(mode="json"))
        return "".join(parts)

    # ── response assembly ─────────────────────────────────────────────────
    def _assemble(
        self, allowed_set: set[str], final: dict[str, Any]
    ) -> OrchestratorResponse:
        reply_md = final.get("reply_md", "")
        trace = final.get("trace", [])
        safety_passed, safety_reasons = OrchestratorAgent.scan_safety(reply_md)
        safety = SafetyResult(passed=safety_passed, reasons=safety_reasons)

        if final.get("needs_user_input"):
            return OrchestratorResponse(
                reply_md=reply_md,
                clarifying_question=final.get("clarifying"),
                needs_user_input=True,
                plan=[],
                dispatch_results=[],
                profile_updates=[],
                trace=trace,
                done=False,
                safety_result=safety,
                grounding_result=GroundingResult(passed=True),
            )

        plan = final.get("plan", [])
        done = bool(plan) and all(step.status in ("done", "skipped") for step in plan)
        return OrchestratorResponse(
            reply_md=reply_md,
            clarifying_question=None,
            needs_user_input=False,
            plan=plan,
            dispatch_results=final.get("dispatch_results", []),
            profile_updates=final.get("signals", []),
            trace=trace,
            done=done,
            safety_result=safety,
            grounding_result=self._grounding(plan, allowed_set),
        )

    async def _persist(
        self, req: OrchestratorRequest, response: OrchestratorResponse
    ) -> None:
        await append_turns(
            req.session_id,
            [
                OrchestratorTurn(role="user", content=req.user_message),
                OrchestratorTurn(role="assistant", content=response.reply_md),
            ],
        )
        # Ambiguous turns keep any previously stored plan untouched.
        if not response.needs_user_input:
            await set_plan(req.session_id, response.plan)
        if response.profile_updates:
            await merge_profile_signals(req.session_id, response.profile_updates)

    # ── dispatch / replan helpers (unchanged) ─────────────────────────────
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

    # ── trace step builders ───────────────────────────────────────────────
    @staticmethod
    def _ok_step(name: str, started: float) -> GraphTraceStep:
        elapsed_ms = int((time.monotonic() - started) * 1000)
        return GraphTraceStep(node=name, elapsed_ms=elapsed_ms, status="ok")

    @staticmethod
    def _sync_step(name: str, note: str | None = None) -> GraphTraceStep:
        return GraphTraceStep(node=name, elapsed_ms=0, status="ok", note=note)
