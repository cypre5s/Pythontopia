"""OrchestratorGraph：学习总管的 LangGraph StateGraph 编排。

控制流（条件边）::

    START → understand
      ├─ (ambiguous?) → clarify → END
      └─ else → build_plan → dispatch
                                ├─ (recent failures & not negotiate?) → replan
                                └─ else → extract_profile → compose → END

各节点封装 OrchestratorAgent 既有方法；经实例 emit 回调发出与原先相同的 SSE
（每节点 trace、compose 时 token），前端 useSseStream 契约不变。trace 经 reducer
累加；多轮记忆在 orchestrator_session。

dispatch 节点两批有界并发（Semaphore=3）：无依赖的 watch/learn/assess 先并发执行，
practice 依赖 learn 工件（讲义 title/summary 注入 focus_hint）随后执行；
start/finish trace 逐 step 上报并携带 step_id 对齐（完成顺序可乱）。

trace 节点名：understand, decompose | replan, dispatch.<agent>, extract_profile, compose_reply。

LangGraph v1 checkpointer（R-16 升级，https://docs.langchain.com/oss/python/migrate/langgraph-v1）：
- ``thread_id = "<session_id>:turn-<轮次>"``——**每轮独立 thread**。理由：``trace`` /
  ``dispatch_results`` 用 ``operator.add`` reducer，若跨轮共用 thread，次轮 ainvoke 会把上一轮
  快照里的列表继续累加（结果污染、SSE 行为变化）；跨轮记忆本就由 orchestrator_session 权威承担，
  checkpoint 的价值定位为「单轮内逐节点快照」：崩溃点可查、按轮回放排错（time-travel）。
- checkpointer 经 ``PYT_ORCH_CHECKPOINT`` 选型（``memory`` 默认 / ``postgres`` 需自装
  langgraph-checkpoint-postgres）；降级只发生在**构造期**（postgres 不可用 → memory + 告警），
  运行时不做失败重跑——节点有真实 LLM 副作用，重跑会重复调用并重发 SSE trace。
"""

from __future__ import annotations

import asyncio
import logging
import operator
import os
import time
from collections.abc import Awaitable, Callable
from typing import Annotated, Any, TypedDict

from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph

from pythontopia_graph.agents.dispatch import AgentDispatcher, DispatchContext
from pythontopia_graph.agents.orchestrator_agent import OrchestratorAgent
from pythontopia_graph.graph.orchestrator_session import (
    append_turns,
    get_history,
    get_plan,
    merge_profile_signals,
    set_plan,
)
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceFetcher, StaticEvidenceFetcher
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

LOG = logging.getLogger(__name__)

# dispatch 节点单批最大并发子 agent 数（有界并发，防 provider 限流雪崩）。
_DISPATCH_CONCURRENCY = 3


def default_checkpointer() -> Any:
    """按 ``PYT_ORCH_CHECKPOINT`` 构建 checkpointer：memory（默认）/ postgres（可选）。

    postgres 分支需要自行安装 ``langgraph-checkpoint-postgres`` 并配置
    ``PYT_ORCH_CHECKPOINT_PG_URL``；任何失败降级 MemorySaver + 告警，
    绝不让 orchestrator 整体不可用（单 worker 下 MemorySaver 线程安全足够）。
    """
    kind = (os.environ.get("PYT_ORCH_CHECKPOINT") or "memory").strip().lower()
    if kind == "postgres":
        try:
            from langgraph.checkpoint.postgres.aio import (  # type: ignore[import-not-found]
                AsyncPostgresSaver,
            )

            url = os.environ.get("PYT_ORCH_CHECKPOINT_PG_URL", "")
            if url:
                return AsyncPostgresSaver.from_conn_string(url)
            LOG.warning("PYT_ORCH_CHECKPOINT=postgres 但未配 PG_URL，降级 MemorySaver")
        except Exception as exc:  # noqa: BLE001 - 缺包/连不上一律降级
            LOG.warning("Postgres checkpointer 不可用（%s），降级 MemorySaver", exc)
    return MemorySaver()


async def _noop_emit(_event: str, _data: dict[str, Any]) -> None:
    """非流式 run 路径的默认 emit sink（丢弃事件）。"""
    return None


class OrchestratorState(TypedDict, total=False):
    """LangGraph 共享状态；trace / dispatch_results 经 reducer 累加。"""

    # 输入（初始 state 一次性写入）
    req: OrchestratorRequest
    allowed_set: set[str]
    # understand 产出
    intent: str
    goal: str
    ambiguous: bool
    clarifying: str | None
    needs_user_input: bool
    # 下游产出
    plan: list[PlanStep]
    dispatch_results: Annotated[list[DispatchResult], operator.add]
    signals: list[ProfileSignal]
    reply_md: str
    trace: Annotated[list[GraphTraceStep], operator.add]


class OrchestratorGraph:
    def __init__(
        self,
        *,
        provider: LlmProvider,
        evidence_fetcher: EvidenceFetcher | None = None,
        valid_kc_codes: set[str] | None = None,
        checkpointer: Any | None = None,
    ) -> None:
        self._provider = provider
        self._agent = OrchestratorAgent(provider)
        self._evidence_fetcher = evidence_fetcher or StaticEvidenceFetcher([])
        self._valid_kc_codes = valid_kc_codes or set()
        self._dispatcher = AgentDispatcher(
            DispatchContext(
                provider=provider,
                evidence_fetcher=self._evidence_fetcher,
                valid_kc_codes=self._valid_kc_codes,
            )
        )
        # 当前 run 的 emit 回调（单 run 存 self，免逐节点传参）
        self._emit_cb: Emit = _noop_emit
        # checkpointer 降级发生在**构造期**（default_checkpointer：postgres 不可用→memory）。
        # 运行时不做"失败重跑降级"——节点有真实 LLM 副作用，重跑会重复调用并重发 SSE trace。
        self._checkpointer = checkpointer if checkpointer is not None else default_checkpointer()
        self._compiled = self._build_compiled(self._checkpointer)

    async def run(self, req: OrchestratorRequest) -> OrchestratorResponse:
        return await self._invoke(req, _noop_emit)

    async def run_streaming(self, req: OrchestratorRequest, emit: Emit) -> None:
        try:
            response = await self._invoke(req, emit)
            await emit("result", response.model_dump(mode="json"))
        except Exception as exc:  # noqa: BLE001 – surface as an SSE error event
            await emit("error", {"code": "ORCHESTRATOR_FAILED", "message": str(exc)})

    def _build_compiled(self, checkpointer: Any | None) -> Any:
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
        if checkpointer is not None:
            return graph.compile(checkpointer=checkpointer)
        return graph.compile()

    @staticmethod
    def turn_thread_id(session_id: str, turn_no: int) -> str:
        """每轮独立 checkpoint thread（见模块 docstring：防 add reducer 跨轮污染）。"""
        return f"{session_id}:turn-{turn_no}"

    async def get_turn_state(self, session_id: str, turn_no: int) -> dict[str, Any]:
        """按轮读取 checkpoint 快照 values（排错 / time-travel / 测试用；无快照返回空 dict）。"""
        config = {"configurable": {"thread_id": self.turn_thread_id(session_id, turn_no)}}
        snapshot = await self._compiled.aget_state(config)
        values = getattr(snapshot, "values", None)
        return dict(values) if values else {}

    async def _invoke(self, req: OrchestratorRequest, emit: Emit) -> OrchestratorResponse:
        self._emit_cb = emit

        # 服务端记忆为权威；客户端 history 仅冷启动回退
        server_history = await get_history(req.session_id)
        merged_history = server_history if server_history else list(req.history)
        merged_req = req.model_copy(update={"history": merged_history})
        allowed_set = set(req.course_kc_index)

        # 每轮重建调度上下文：把本轮请求的真实画像注入 DispatchContext（真画像透传）。
        self._dispatcher = AgentDispatcher(
            DispatchContext(
                provider=self._provider,
                evidence_fetcher=self._evidence_fetcher,
                valid_kc_codes=self._valid_kc_codes,
                profile_brief=merged_req.profile_brief or "",
            )
        )

        initial: OrchestratorState = {
            "req": merged_req,
            "allowed_set": allowed_set,
            "trace": [],
            "dispatch_results": [],
        }
        # 轮次 = 服务端已存对话轮数（user+assistant 成对，半开容忍）；thread 按轮独立。
        turn_no = len(server_history) // 2
        config = {"configurable": {"thread_id": self.turn_thread_id(req.session_id, turn_no)}}
        final: dict[str, Any] = await self._compiled.ainvoke(initial, config)

        response = self._assemble(allowed_set, final)
        await self._persist(req, response)
        return response

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
        # negotiate 意图就地改 stored plan，不重建
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
        req = state["req"]
        plan = state["plan"]

        # Emit plan SSE event listing all dispatch steps as nodes
        plan_nodes = [
            {
                "node": f"dispatch.{step.agent}",
                "agent_label": self._agent_display_name(step.agent),
                "kind": self._infer_kind(step),
            }
            for step in plan
        ]
        await self._emit_cb(
            "plan",
            {"type": "orchestrator", "nodes": plan_nodes},
        )

        # 两批有界并发：watch/learn/assess 无依赖先并发；practice 依赖 learn 工件随后执行。
        first_batch = [step for step in plan if step.phase != "practice"]
        second_batch = [step for step in plan if step.phase == "practice"]
        semaphore = asyncio.Semaphore(_DISPATCH_CONCURRENCY)
        results_by_id: dict[str, DispatchResult] = {}
        traces_by_id: dict[str, GraphTraceStep] = {}

        async def _run_step(step: PlanStep, focus_hint_extra: str = "") -> None:
            """单 step 全生命周期：start trace → dispatch → error/finish trace（失败不外抛）。"""
            async with semaphore:
                await self._emit_cb(
                    "trace",
                    {
                        "node": f"dispatch.{step.agent}",
                        "step_id": step.id,
                        "phase": "start",
                        "status": "running",
                        "elapsed_ms": 0,
                        "agent_label": self._agent_display_name(step.agent),
                        "note": step.title,
                    },
                )
                started = time.monotonic()
                try:
                    result = await self._dispatch_step(step, req.user_id, focus_hint_extra)
                except Exception as exc:  # noqa: BLE001 – 单步失败不拖垮全局
                    elapsed_ms = int((time.monotonic() - started) * 1000)
                    await self._emit_cb(
                        "error",
                        {
                            "code": "DISPATCH_FAILED",
                            "agent": step.agent,
                            "message": str(exc),
                        },
                    )
                    result = DispatchResult(
                        step_id=step.id,
                        agent=step.agent,
                        ok=False,
                        summary=f"{self._agent_display_name(step.agent)} 执行失败: {exc}",
                        note=str(exc),
                        elapsed_ms=elapsed_ms,
                    )
                else:
                    # 治理观测失败（dispatch 内已转 ok=False）沿用 SSE error + ok=False 模式
                    if not result.ok and self._is_dispatch_failure(result):
                        await self._emit_cb(
                            "error",
                            {
                                "code": "DISPATCH_FAILED",
                                "agent": step.agent,
                                "message": result.summary,
                            },
                        )
                elapsed_ms = int((time.monotonic() - started) * 1000)
                await self._emit_cb(
                    "trace",
                    {
                        "node": f"dispatch.{step.agent}",
                        "step_id": step.id,
                        "phase": "finish",
                        "status": "ok" if result.ok else "fail",
                        "elapsed_ms": elapsed_ms,
                        "agent_label": self._agent_display_name(step.agent),
                        "note": result.summary,
                    },
                )
                results_by_id[step.id] = result
                traces_by_id[step.id] = GraphTraceStep(
                    node=f"dispatch.{step.agent}",
                    elapsed_ms=elapsed_ms,
                    status="ok" if result.ok else "fail",
                    note=result.summary,
                )

        if first_batch:
            await asyncio.gather(*(_run_step(step) for step in first_batch))
        learn_hint = self._learn_artifact_hint(plan, results_by_id)
        if second_batch:
            await asyncio.gather(*(_run_step(step, learn_hint) for step in second_batch))

        # 聚合结果按 plan 原序输出（SSE 事件完成顺序可乱，step_id 已对齐）。
        results = [results_by_id[step.id] for step in plan if step.id in results_by_id]
        traces = [traces_by_id[step.id] for step in plan if step.id in traces_by_id]
        return {"dispatch_results": results, "trace": traces}

    @staticmethod
    def _learn_artifact_hint(
        plan: list[PlanStep], results_by_id: dict[str, DispatchResult]
    ) -> str:
        """工件链协作：learn 步真产物摘要 → practice 步 focus_hint 追加句。"""
        for step in plan:
            if step.phase != "learn":
                continue
            result = results_by_id.get(step.id)
            if result is None or not result.ok or not result.artifact:
                continue
            title = str(result.artifact.get("title") or "").strip()
            if not title:
                continue
            summary = str(result.artifact.get("summary") or "").strip()
            hint = f"；练习紧扣讲义《{title}》要点"
            if summary:
                hint = f"{hint}：{summary[:80]}"
            return hint
        return ""

    @staticmethod
    def _is_dispatch_failure(result: DispatchResult) -> bool:
        """是否为执行类失败（治理观测失败），区别于子 agent 诚实的业务性 FAILED。"""
        artifact = result.artifact or {}
        return artifact.get("kind") == "dispatch_failure"

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
        # emit trace start for DebugAgent dispatch
        await self._emit_cb(
            "trace",
            {
                "node": "dispatch.DebugAgent",
                "step_id": debug_step.id,
                "phase": "start",
                "status": "running",
                "elapsed_ms": 0,
                "agent_label": "DebugAgent",
                "note": debug_step.title,
            },
        )
        started = time.monotonic()
        result = await self._dispatch_step(debug_step, req.user_id)
        elapsed_ms = int((time.monotonic() - started) * 1000)
        dispatch_step = GraphTraceStep(
            node="dispatch.DebugAgent",
            elapsed_ms=elapsed_ms,
            status="ok" if result.ok else "fail",
            note=result.summary,
        )
        await self._emit_cb(
            "trace",
            {
                "node": "dispatch.DebugAgent",
                "step_id": debug_step.id,
                "phase": "finish",
                "status": "ok" if result.ok else "fail",
                "elapsed_ms": elapsed_ms,
                "agent_label": "DebugAgent",
                "note": result.summary,
            },
        )
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
        # 歧义澄清轮不覆盖已存 plan
        if not response.needs_user_input:
            await set_plan(req.session_id, response.plan)
        if response.profile_updates:
            await merge_profile_signals(req.session_id, response.profile_updates)

    async def _dispatch_step(
        self, step: PlanStep, user_id: int = 1, focus_hint_extra: str = ""
    ) -> DispatchResult:
        """真正 await 子 agent（经 AgentDispatcher），不再只回文本摘要。"""
        return await self._dispatcher.dispatch(
            step, user_id=user_id, focus_hint_extra=focus_hint_extra
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

    @staticmethod
    def _agent_display_name(agent: str) -> str:
        """把内部 agent 类名映射为中文工位名（前端 MarvisPanel / 向导气泡展示）。"""
        _AGENT_DISPLAY: dict[str, str] = {
            "StoryboardAgent": "故事板导演",
            "ResourceDesignerAgent": "讲义设计师",
            "PracticeAgent": "练习设计师",
            "PathPlannerAgent": "路径规划师",
            "DebugAgent": "矿洞诊断师",
            "TutorAgent": "百晓助教",
            "HandoutAgent": "讲义写手",
            "MindMapAgent": "导图绘制师",
            "CodeLabAgent": "代码实验室",
            "ReadingAgent": "阅读向导",
        }
        return _AGENT_DISPLAY.get(agent, agent)

    @staticmethod
    def _infer_kind(step: PlanStep) -> str:
        """从 PlanStep 的 phase 和 resource_type 推断 multi-agent workflow 节点类型。"""
        if step.agent == "DebugAgent":
            return "debug"
        if step.agent == "PathPlannerAgent":
            return "plan"
        if step.agent == "TutorAgent":
            return "ask"
        resource_type = (step.resource_type or "").upper()
        if resource_type in ("STORYBOARD",):
            return "storyboard"
        if resource_type in ("PRACTICE", "CODE_LAB"):
            return "practice"
        if resource_type in ("HANDOUT", "READING", "MINDMAP"):
            return "resource"
        # fallback by phase
        if step.phase == "watch":
            return "storyboard"
        if step.phase == "practice":
            return "practice"
        return "resource"

    @staticmethod
    def _ok_step(name: str, started: float) -> GraphTraceStep:
        elapsed_ms = int((time.monotonic() - started) * 1000)
        return GraphTraceStep(node=name, elapsed_ms=elapsed_ms, status="ok")

    @staticmethod
    def _sync_step(name: str, note: str | None = None) -> GraphTraceStep:
        return GraphTraceStep(node=name, elapsed_ms=0, status="ok", note=note)
