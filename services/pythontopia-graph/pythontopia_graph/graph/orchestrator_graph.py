"""OrchestratorGraph：学习总管的 LangGraph StateGraph 编排。

控制流（条件边）::

    START → understand
      ├─ (ambiguous?) → clarify → END
      └─ else → build_plan → dispatch → reflect
                                          ├─ (recent failures & not negotiate?) → replan
                                          └─ else → extract_profile → compose → END

各节点封装 OrchestratorAgent 既有方法；经实例 emit 回调发出与原先相同的 SSE
（每节点 trace、compose 时 token），前端 useSseStream 契约不变。trace 经 reducer
累加；多轮记忆在 orchestrator_session。

dispatch 节点两批有界并发（Semaphore=3）：无依赖的 watch/learn/assess 先并发执行，
practice 依赖 learn 工件（讲义 title/summary 注入 focus_hint）随后执行；
start/finish trace 逐 step 上报并携带 step_id 对齐（完成顺序可乱）。

reflect 节点（A3 有界反思 · N=1）：dispatch 之后、既有路由之前补一次**有界恢复**——
learn 阶段资源步若为执行类失败（``ok=False`` 且 ``artifact.kind=="dispatch_failure"``），
对该步降级资源类型（CODE_LAB→HANDOUT→READING）后经 dispatcher 仅重派一次；已是 READING /
不可降级则不重派，重派仍失败则保留失败结果。严格 N=1（非无界 ReAct），重派复用现有治理
（受 harness 熔断/超时约束）。reflect 仅补派、不改写 plan，故对 negotiate 与
recent_failures→replan 既有路由零影响（``_route_after_dispatch`` 原样接在其后）。
重派结果经 reducer 累加，由 ``_assemble`` 按 step_id 收敛为该步最终结果。

trace 节点名：understand, decompose | replan, dispatch.<agent>, reflect, extract_profile,
compose_reply（反思重派的 note 以 ``reflect_retry:learn`` 开头）。

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
from typing import Annotated, Any

from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph

from pythontopia_graph.agents.dispatch import AgentDispatcher, DispatchContext
from pythontopia_graph.agents.orchestrator_agent import OrchestratorAgent
from pythontopia_graph.graph.agentic_loop import (
    EXTERNAL_CONTEXT_TAG,
    AgenticLoopController,
    AgenticLoopState,
    BrowseExecutor,
    CriticVerdict,
    MemoryBriefProvider,
    NullMemoryBriefProvider,
    Scope,
    count_bounded_recoveries,
    current_trace_id,
    fold_memory_brief,
    loop_plan_event,
    loop_step_event,
    phase_for_plan_step,
    tool_trail_from_results,
)
from pythontopia_graph.graph.orchestrator_session import (
    append_turns,
    get_history,
    get_plan,
    merge_profile_signals,
    set_plan,
)
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.memory.contract import MemoryBrief
from pythontopia_graph.nodes.evidence_fetcher import EvidenceFetcher, StaticEvidenceFetcher
from pythontopia_graph.routing.workshop_metrics import record_execution
from pythontopia_graph.routing.workshop_policy import validate_route_decision
from pythontopia_graph.routing.workshop_task_plan import (
    TaskPlanPolicyError,
    compile_task_plan,
    rebuild_runtime_after_switch,
    switch_task_plan_candidate_once,
    validate_task_plan,
)
from pythontopia_graph.schemas.agentic_loop import AgenticLoopRunRecord, LoopStep
from pythontopia_graph.schemas.intent import IntentRouteRequest
from pythontopia_graph.schemas.orchestrator import (
    DispatchResult,
    OrchestratorRequest,
    OrchestratorResponse,
    OrchestratorTurn,
    PlanStep,
    ProfileSignal,
    TaskPlan,
)
from pythontopia_graph.schemas.resource import (
    GraphTraceStep,
    GroundingResult,
    SafetyResult,
)

# 闭环审计记录的接收回调（Phase 2 由 S2-loop-backend 注入 → 落 V171）。
LoopRecordSink = Callable[[AgenticLoopRunRecord], Awaitable[None]]

Emit = Callable[[str, dict[str, Any]], Awaitable[None]]

LOG = logging.getLogger(__name__)

# dispatch 节点单批最大并发子 agent 数（有界并发，防 provider 限流雪崩）。
_DISPATCH_CONCURRENCY = 3

# 流式回复的「先审后放」切段边界：攒到句/段末尾（或长度上限）才过一遍 scan_safety。
_SAFETY_FLUSH_BOUNDARIES: tuple[str, ...] = (
    "\n", "。", "！", "？", "；", "：", "”", "）", "!", "?", ";", ":", ")",
)
# 无标点长句的强制切段长度：越大首包越慢，越小审查粒度越碎；一个中文短句量级。
_SAFETY_FLUSH_MAX_CHARS = 48


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


class OrchestratorState(AgenticLoopState, total=False):
    """LangGraph 共享状态；trace / dispatch_results 经 reducer 累加。

    继承 :class:`AgenticLoopState` 的有界闭环字段（``loop_step`` / ``max_loop_steps`` /
    ``tool_trail`` / ``memory_brief`` 等），使闭环上下文随 checkpoint 入轮快照可审计。
    """

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
    task_plan: TaskPlan
    reused_plan: bool
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
        memory_brief_provider: MemoryBriefProvider | None = None,
        browse_executor: BrowseExecutor | None = None,
        browse_allowed_domains: list[str] | None = None,
        max_loop_steps: int = 4,
        loop_scope: Scope = "default",
        loop_record_sink: LoopRecordSink | None = None,
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
        # ── 有界 Agentic Loop 侧挂依赖（缺省全部 no-op / null，向后兼容；Phase 2 真接线）──
        self._memory_brief_provider = memory_brief_provider or NullMemoryBriefProvider()
        self._browse_executor = browse_executor
        self._browse_allowed_domains = browse_allowed_domains
        self._loop = AgenticLoopController(max_loop_steps=max_loop_steps, scope=loop_scope)
        self._loop_record_sink = loop_record_sink
        # 最近一轮闭环审计记录（结构化产出；落库由 S2 经 sink 负责）。
        self._last_loop_record: AgenticLoopRunRecord | None = None
        # checkpointer 降级发生在**构造期**（default_checkpointer：postgres 不可用→memory）。
        # 运行时不做"失败重跑降级"——节点有真实 LLM 副作用，重跑会重复调用并重发 SSE trace。
        self._checkpointer = checkpointer if checkpointer is not None else default_checkpointer()
        self._compiled = self._build_compiled(self._checkpointer)

    @property
    def last_loop_record(self) -> AgenticLoopRunRecord | None:
        """最近一轮闭环审计记录（镜像 V171；供 S2 落库 / 测试断言）。"""
        return self._last_loop_record

    async def run(self, req: OrchestratorRequest) -> OrchestratorResponse:
        return await self._invoke(req, _noop_emit)

    async def run_streaming(self, req: OrchestratorRequest, emit: Emit) -> None:
        try:
            response = await self._invoke(req, emit)
            await emit("result", response.model_dump(mode="json"))
        except Exception as exc:  # noqa: BLE001 – surface as an SSE error event
            await emit("error", {"code": "ORCHESTRATOR_FAILED", "message": str(exc)})

    def _build_compiled(self, checkpointer: Any | None) -> Any:
        graph = StateGraph(OrchestratorState)
        graph.add_node("understand", self._node_understand)
        graph.add_node("build_plan", self._node_build_plan)
        graph.add_node("dispatch", self._node_dispatch)
        graph.add_node("reflect", self._node_reflect)
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
        graph.add_conditional_edges(
            "build_plan",
            self._route_after_build_plan,
            {"dispatch": "dispatch", "compose": "compose"},
        )
        # dispatch 后无条件进入有界反思（A3 · N=1）；reflect 无可恢复项时为 no-op。
        graph.add_edge("dispatch", "reflect")
        # 既有 replan/negotiate 路由原样接在 reflect 之后（reflect 不改 plan/intent/
        # recent_failures，路由决策与接在 dispatch 后完全一致）。
        graph.add_conditional_edges(
            "reflect",
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

        # understand 前注入记忆 brief（§3.2，缺省空 brief 向后兼容）：折叠进 profile_brief /
        # decaying_kcs（OrchestratorRequest 无 context 字段、非本域可写），使理解与调度可见时序记忆。
        brief = await self._fetch_memory_brief(merged_req)
        merged_req = fold_memory_brief(merged_req, brief)
        allowed_set = set(req.course_kc_index)

        # 每轮重建调度上下文：把本轮请求的真实画像（含记忆折叠）注入 DispatchContext。
        self._dispatcher = AgentDispatcher(
            DispatchContext(
                provider=self._provider,
                evidence_fetcher=self._evidence_fetcher,
                valid_kc_codes=self._valid_kc_codes,
                profile_brief=merged_req.profile_brief or "",
                debug_context=merged_req.interaction,
                learner_context=merged_req.learner_context,
            )
        )

        initial: OrchestratorState = {
            "req": merged_req,
            "allowed_set": allowed_set,
            "trace": [],
            "dispatch_results": [],
            # 有界闭环上下文入轮快照（可审计）。
            "memory_brief": brief.model_dump(mode="json"),
            "max_loop_steps": self._loop.max_loop_steps,
            "loop_step": 0,
            "replan_count": 0,
            "tool_trail": [],
        }
        # 轮次 = 服务端已存对话轮数（user+assistant 成对，半开容忍）；thread 按轮独立。
        turn_no = len(server_history) // 2
        config = {"configurable": {"thread_id": self.turn_thread_id(req.session_id, turn_no)}}
        final: dict[str, Any] = await self._compiled.ainvoke(initial, config)

        response = self._assemble(allowed_set, final)
        # 结构化闭环审计（镜像 V171）；落库由 S2-loop-backend 经 sink 负责（fail-soft）。
        self._last_loop_record = self._build_loop_record(merged_req, final, response, turn_no)
        await self._emit_loop_record(self._last_loop_record)
        await self._persist(req, response)
        return response

    async def _fetch_memory_brief(self, req: OrchestratorRequest) -> MemoryBrief:
        """取记忆 brief（M2 brief_client；缺省 Null）。超时/异常 fail-soft 回退空 brief。"""
        try:
            return await self._memory_brief_provider.fetch(
                user_id=req.user_id, query=req.user_message
            )
        except Exception as exc:  # noqa: BLE001 - 记忆层失败不拖垮主链路
            LOG.warning("memory brief 获取失败（fail-soft 空 brief）：%s", exc)
            return MemoryBrief()

    def _build_loop_record(
        self,
        req: OrchestratorRequest,
        final: dict[str, Any],
        response: OrchestratorResponse,
        turn_no: int,
    ) -> AgenticLoopRunRecord:
        """从本轮终态组织闭环审计记录（plan/tool_trail/critic/replan/trace_id）。"""
        dispatch_results = self._collapse_redispatched_results(final.get("dispatch_results", []))
        trace = final.get("trace", [])
        tools_used = tool_trail_from_results(dispatch_results)
        replan_count = count_bounded_recoveries(trace)
        steps_used = min(1 + replan_count, self._loop.max_loop_steps)
        verdict = CriticVerdict(
            grounding_pass=response.grounding_result.passed,
            safety_pass=response.safety_result.passed,
            reasons=list(response.grounding_result.reasons) + list(response.safety_result.reasons),
        )
        return self._loop.build_run_record(
            user_id=req.user_id,
            session_id=req.session_id,
            turn=turn_no,
            verdict=verdict,
            steps_used=steps_used,
            replan_count=replan_count,
            tools_used=tools_used,
            trace_id=current_trace_id(),
        )

    async def _emit_loop_record(self, record: AgenticLoopRunRecord) -> None:
        """把闭环审计记录交给 sink（Phase 2 由 S2 落 V171）；sink 失败 fail-soft。"""
        if self._loop_record_sink is None:
            return
        try:
            await self._loop_record_sink(record)
        except Exception as exc:  # noqa: BLE001 - 落库失败不拖垮主链路
            LOG.warning("loop_record sink 失败（fail-soft）：%s", exc)

    async def _emit_loop_step(
        self,
        step: PlanStep,
        status: str,
        *,
        duration_ms: int = 0,
        reason_summary: str = "",
        error: str = "",
    ) -> None:
        """发 loop_step CUSTOM 事件（§3.8）：PlanStep + 状态 → 闭环可见 LoopStep（camelCase 载荷）。

        与既有 trace/STATE_DELTA 并存、向后兼容；id 与 loop_plan 同源（step.id），
        前端 loopSlice 按 id 单调推进（pending→active→done/failed）。
        """
        loop_step = LoopStep(
            id=step.id,
            phase=phase_for_plan_step(step),
            label=step.title,
            tool_name=step.resource_type or step.agent,
            status=status,  # type: ignore[arg-type]  # 调用方仅传 active/done/failed
            duration_ms=max(0, duration_ms),
            reason_summary=(reason_summary or "")[:200],
            error=(error or "")[:200],
        )
        await self._emit_cb("CUSTOM", {"name": "loop_step", "value": loop_step_event(loop_step)})

    @staticmethod
    def _route_validation_request(req: OrchestratorRequest) -> IntentRouteRequest:
        """Rebuild the request facts needed to verify a signed C3 search decision."""
        return IntentRouteRequest(
            message=req.user_message,
            current_chapter=req.current_chapter,
            recent_kc_codes=req.recent_kc_codes,
            interaction=req.interaction,
            learner_context=req.learner_context,
        )

    async def _node_understand(self, state: OrchestratorState) -> dict[str, Any]:
        req = state["req"]
        started = time.monotonic()
        if req.route_decision is not None and req.route_decision.task.family == "MULTI_TASK":
            # RouteDecision 由百晓路由策略门签发；编排器只验证并执行，绝不让模型二次改路由。
            # stage="execute"：签发时的 C3 search 即权威，执行期不按当前学情重算成本。
            validate_route_decision(
                req.route_decision,
                scope="default",
                request=self._route_validation_request(req),
                stage="execute",
            )
            step = GraphTraceStep(
                node="understand",
                elapsed_ms=int((time.monotonic() - started) * 1000),
                status="ok",
                note="authoritative_multi_task_route",
            )
            await self._emit_cb("trace", step.model_dump(mode="json"))
            return {
                "intent": "compound",
                "goal": req.user_message,
                "ambiguous": False,
                "clarifying": None,
                "trace": [step],
            }
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
        if req.route_decision is not None and req.route_decision.task.family == "MULTI_TASK":
            task_plan = compile_task_plan(
                req.route_decision,
                message=req.user_message,
                kc_codes=list(req.recent_kc_codes or req.decaying_kcs or req.course_kc_index[:1]),
            )
            step = self._sync_step("decompose", note=f"task_plan:{task_plan.topology}")
            await self._emit_cb("trace", step.model_dump(mode="json"))
            return {
                "plan": task_plan.steps,
                "task_plan": task_plan,
                "reused_plan": False,
                "trace": [step],
            }
        # continue/negotiate 意图复用 stored plan：这类轮次是在已有学习计划上追问或调整，
        # 不应重新 dispatch 四个子 agent，否则一个“下一步做什么”会再次生成全套资源。
        if state["intent"] in {"continue", "negotiate"}:
            stored_plan = await get_plan(req.session_id)
            if stored_plan:
                if state["intent"] == "negotiate":
                    plan = self._negotiate(req.user_message, stored_plan)
                    note = f"negotiate:{self._negotiation_kind(req.user_message)}"
                else:
                    plan = stored_plan
                    note = "continue:stored_plan"
                step = self._sync_step("replan", note=note)
                await self._emit_cb("trace", step.model_dump(mode="json"))
                return {"plan": plan, "reused_plan": True, "trace": [step]}
        started = time.monotonic()
        plan = await self._agent.decompose(req, state["goal"])
        step = self._ok_step("decompose", started)
        await self._emit_cb("trace", step.model_dump(mode="json"))
        return {"plan": plan, "reused_plan": False, "trace": [step]}

    async def _node_dispatch(self, state: OrchestratorState) -> dict[str, Any]:
        """受治理运行的指标记账包装：失败也必须进 executions 分母。

        TaskPlanPolicyError / 依赖死锁 / 路由校验失败原先一次都不计入
        ``executions.total``，导致 partialRate / switchRate 在故障期反而更好看。
        这里以 ``status="ERROR"`` 记一笔（只记可观测量，不落学生原始输入）。
        """
        started = time.monotonic()
        try:
            return await self._dispatch_governed(state)
        except Exception:
            if state.get("task_plan") is not None:
                record_execution(
                    status="ERROR",
                    dispatched_steps=0,
                    blocked_steps=0,
                    candidate_switches=0,
                    same_step_retries=0,
                    latency_ms=int((time.monotonic() - started) * 1000),
                )
            raise

    async def _dispatch_governed(self, state: OrchestratorState) -> dict[str, Any]:
        req = state["req"]
        dispatch_started = time.monotonic()
        # Phase 2/4 预检必须先于任何 SSE 事件与副作用：签名 TaskPlan 是唯一执行事实源，
        # state["plan"] 可能来自被伪造的恢复状态，越权计划不得泄漏到 plan/loop_plan 事件。
        task_plan = state.get("task_plan")
        if task_plan is not None:
            if req.route_decision is None:
                raise TaskPlanPolicyError("signed TaskPlan execution requires RouteDecision")
            validate_route_decision(
                req.route_decision,
                scope="default",
                request=self._route_validation_request(req),
                stage="execute",
            )
            validate_task_plan(task_plan, req.route_decision)
            plan = list(task_plan.steps)
            replans_used = task_plan.replans_used
        else:
            plan = list(state["plan"])
            replans_used = 0

        async def _emit_plan_nodes() -> None:
            # Emit plan SSE event listing all dispatch steps as nodes；C3 候选切换后
            # 用切换后的权威计划重发，前端计划卡与 loopSlice 都能看到新步序。
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
            # §3.8 闭环可见有界计划（Claude Code 网页版格式）：发 loop_plan CUSTOM 事件供前端
            # loopSlice 累积，LoopProgress 渲染（与既有 plan/STATE_DELTA 事件并存，向后兼容）。
            await self._emit_cb(
                "CUSTOM",
                {"name": "loop_plan", "value": loop_plan_event(self._loop.build_loop_plan(plan))},
            )

        await _emit_plan_nodes()

        # AG-UI STATE_DELTA：plan 步骤进度的累积状态映射（step_id → pending/active/done/failed），
        # 随 dispatch 真实推进实时同步前端。区别于离散 trace 日志，这是「当前完整状态」快照，
        # 以 RFC 6902 add 写入根属性；add 对对象成员同时支持首次创建与后续替换。
        # ``done`` 是持久化恢复边界：候选切换前已经成功并产生副作用的步骤必须
        # 直接视为完成，不能因进程重启再次派发。active/failed/skipped 都不是可恢复
        # 的成功证明，恢复执行时回到 pending 并由稳定 idempotency_key 保护重试。
        step_status: dict[str, str] = {
            step.id: "done" if step.status == "done" else "pending" for step in plan
        }

        async def _emit_plan_progress() -> None:
            # 发完整步骤视图（真实 PlanStep 字段 + 实时 status），前端 state.plan_steps 可直接渲染
            # 计划卡并实时点亮——比最终 result.plan（status 多停在 pending）更准。
            steps_view = [
                {**step.model_dump(mode="json"), "status": step_status[step.id]} for step in plan
            ]
            await self._emit_cb(
                "STATE_DELTA",
                {
                    "delta": [
                        {
                            "op": "add",
                            "path": "/plan_steps",
                            "value": steps_view,
                        }
                    ]
                },
            )

        await _emit_plan_progress()

        # Phase 2：TaskPlan 由显式依赖驱动；旧看学练测计划保持 practice 依赖 learn 的兼容语义。
        def _dependencies_for(current_plan: list[PlanStep]) -> dict[str, list[str]]:
            explicit_dependencies = any(step.depends_on for step in current_plan)
            legacy_learn_ids = [step.id for step in current_plan if step.phase == "learn"]
            return {
                step.id: (
                    list(step.depends_on)
                    if explicit_dependencies or task_plan is not None
                    else legacy_learn_ids
                    if step.phase == "practice"
                    else []
                )
                for step in current_plan
            }

        dependencies = _dependencies_for(plan)
        concurrency = (
            min(task_plan.max_parallel_agents, _DISPATCH_CONCURRENCY)
            if task_plan is not None
            else _DISPATCH_CONCURRENCY
        )
        semaphore = asyncio.Semaphore(concurrency)
        results_by_id: dict[str, DispatchResult] = {}
        # 真实时间线：dispatch/evaluate/replan/阻断标记按发生顺序记录，重试步骤保留每次尝试，
        # 供审计与测试还原「先失败一次、切换候选、再失败一次」的完整恢复过程。
        timeline: list[GraphTraceStep] = []
        attempts_by_id: dict[str, int] = {}

        async def _run_step(step: PlanStep, focus_hint_extra: str = "") -> None:
            """单 step 全生命周期：start trace → dispatch → error/finish trace（失败不外抛）。"""
            async with semaphore:
                attempts_by_id[step.id] = attempts_by_id.get(step.id, 0) + 1
                attempt = attempts_by_id[step.id]
                step_status[step.id] = "active"
                await _emit_plan_progress()
                await self._emit_loop_step(step, "active")
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
                    # 用 step_error（非终止）而非 error：下游 SSE 中继与前端都把 error
                    # 视为整轮终止事件，单步失败若发 error 会把后续 STATE_DELTA/result
                    # 全部截断，导致「一个 agent 失败 → 整个学习计划丢失」。
                    await self._emit_cb(
                        "step_error",
                        {
                            "code": "DISPATCH_FAILED",
                            "agent": step.agent,
                            "step_id": step.id,
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
                    # 治理观测失败（dispatch 内已转 ok=False）沿用 step_error + ok=False 模式
                    if not result.ok and self._is_dispatch_failure(result):
                        await self._emit_cb(
                            "step_error",
                            {
                                "code": "DISPATCH_FAILED",
                                "agent": step.agent,
                                "step_id": step.id,
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
                step_status[step.id] = "done" if result.ok else "failed"
                await _emit_plan_progress()
                await self._emit_loop_step(
                    step,
                    "done" if result.ok else "failed",
                    duration_ms=elapsed_ms,
                    reason_summary=result.summary,
                    error="" if result.ok else (result.note or result.summary or ""),
                )
                results_by_id[step.id] = result
                timeline.append(
                    GraphTraceStep(
                        node=f"dispatch.{step.agent}",
                        elapsed_ms=elapsed_ms,
                        status="ok" if result.ok else "fail",
                        note=result.summary,
                    )
                )
                # 增量耐久化事件：在依赖步骤被放行前先交给 Java SSE 旁路落 checkpoint /
                # agent_run / tool_call。载荷仅含结构化摘要与审计工件，不含模型思维过程；
                # idempotency_key 让重试/候选切换后的同一步发布共享稳定幂等边界。
                await self._emit_cb(
                    "CUSTOM",
                    {
                        "name": "task_step_result",
                        "value": {
                            "step_id": step.id,
                            "agent": step.agent,
                            "tool_name": step.tool_name or step.resource_type or step.agent,
                            "status": "COMPLETED" if result.ok else "FAILED",
                            "summary": result.summary,
                            "elapsed_ms": result.elapsed_ms or elapsed_ms,
                            "attempt": attempt,
                            "idempotency_key": step.idempotency_key or "",
                            "artifact": result.artifact or {},
                        },
                    },
                )

        completed: set[str] = {step.id for step in plan if step_status[step.id] == "done"}
        pending = {step.id: step for step in plan if step.id not in completed}
        failed: set[str] = set()
        while pending:
            blocked = [
                step
                for step in pending.values()
                if any(dependency in failed for dependency in dependencies[step.id])
            ]
            for step in blocked:
                result = DispatchResult(
                    step_id=step.id,
                    agent=step.agent,
                    ok=False,
                    summary="前置步骤失败，本步骤未执行",
                    note="dependency_failed",
                    artifact={"kind": "dependency_blocked"},
                )
                results_by_id[step.id] = result
                timeline.append(
                    GraphTraceStep(
                        node=f"dispatch.{step.agent}",
                        elapsed_ms=0,
                        status="fail",
                        note=result.summary,
                    )
                )
                step_status[step.id] = "skipped"
                await self._emit_loop_step(
                    step,
                    "failed",
                    reason_summary=result.summary,
                    error="dependency_failed",
                )
                failed.add(step.id)
                completed.add(step.id)
                pending.pop(step.id)
            if blocked:
                await _emit_plan_progress()
                continue

            ready = [step for step in pending.values() if set(dependencies[step.id]) <= completed]
            if not ready:
                raise RuntimeError("TaskPlan dependency graph has no runnable step")
            await asyncio.gather(
                *(
                    _run_step(
                        step,
                        self._dependency_artifact_hint(dependencies[step.id], results_by_id),
                    )
                    for step in ready
                )
            )
            candidate_switched = False
            if task_plan is not None and task_plan.topology == "PLANNER_EXECUTOR":
                for step in ready:
                    current = results_by_id[step.id]
                    evaluate = GraphTraceStep(
                        node="evaluate",
                        elapsed_ms=0,
                        status="ok" if current.ok else "fail",
                        note=f"planner_executor:{step.id}:{'pass' if current.ok else 'retryable'}",
                    )
                    await self._emit_cb("trace", evaluate.model_dump(mode="json"))
                    timeline.append(evaluate)
                    if (
                        current.ok
                        or step.on_failure != "REPLAN_ONCE"
                        or replans_used >= task_plan.max_replans
                    ):
                        continue
                    if (
                        req.route_decision is not None
                        and req.route_decision.task.complexity == "C3"
                    ):
                        previous_candidate_id = task_plan.active_candidate_id
                        successful_step_ids = {
                            step_id for step_id, status in step_status.items() if status == "done"
                        }
                        task_plan = switch_task_plan_candidate_once(
                            task_plan,
                            req.route_decision,
                            successful_step_ids=successful_step_ids,
                        )
                        # 六份运行时集合由纯函数整体一致重建；这里重绑定局部名即可，
                        # 嵌套闭包（_run_step/_emit_plan_progress）读到的就是新集合。
                        runtime = rebuild_runtime_after_switch(
                            task_plan,
                            successful_step_ids=successful_step_ids,
                        )
                        plan = runtime.plan
                        dependencies = _dependencies_for(plan)
                        completed = runtime.completed
                        failed = runtime.failed
                        pending = runtime.pending
                        step_status = runtime.step_status
                        replans_used = runtime.replans_used
                        replan = GraphTraceStep(
                            node="replan",
                            elapsed_ms=0,
                            status="retry",
                            note=(
                                f"candidate_switch:{previous_candidate_id}"
                                f"->{task_plan.active_candidate_id}"
                            ),
                        )
                        await self._emit_cb("trace", replan.model_dump(mode="json"))
                        timeline.append(replan)
                        # 候选切换改变了权威步序：重发 plan/loop_plan 与全量进度快照，
                        # 前端计划卡按切换后的路径重新渲染，而不是停留在被放弃的候选上。
                        await _emit_plan_nodes()
                        await _emit_plan_progress()
                        candidate_switched = True
                        break
                    # C2 原合同：受权 PLANNER_EXECUTOR 只重试完全相同的 Agent/tool/step。
                    # No fallback substitution and no legacy recent_failures Debug insertion.
                    replan = GraphTraceStep(
                        node="replan",
                        elapsed_ms=0,
                        status="retry",
                        note=f"authorized_same_step:{step.id}",
                    )
                    await self._emit_cb("trace", replan.model_dump(mode="json"))
                    timeline.append(replan)
                    replans_used += 1
                    await _run_step(
                        step,
                        self._dependency_artifact_hint(dependencies[step.id], results_by_id),
                    )
            if candidate_switched:
                continue
            for step in ready:
                result = results_by_id[step.id]
                if not result.ok:
                    failed.add(step.id)
                completed.add(step.id)
                pending.pop(step.id)

        # 聚合结果按 plan 原序输出（SSE 事件完成顺序可乱，step_id 已对齐）；
        # trace 保留真实时间线，重试与候选切换的每次尝试都可回放。
        results = [results_by_id[step.id] for step in plan if step.id in results_by_id]
        result: dict[str, Any] = {"dispatch_results": results, "trace": timeline}
        if task_plan is not None:
            final_statuses = [step_status.get(step.id, step.status) for step in task_plan.steps]
            status = (
                "COMPLETED"
                if final_statuses and all(step_status == "done" for step_status in final_statuses)
                else "PARTIAL"
                if any(step_status == "done" for step_status in final_statuses)
                else "FAILED"
            )
            payload = task_plan.model_dump(mode="python")
            payload["steps"] = [
                {
                    **step.model_dump(mode="python"),
                    "status": step_status.get(step.id, step.status),
                }
                for step in task_plan.steps
            ]
            payload.update({"replans_used": replans_used, "status": status})
            task_plan = TaskPlan.model_validate(payload)
            if req.route_decision is not None:
                validate_task_plan(task_plan, req.route_decision)
            result["task_plan"] = task_plan
            result["plan"] = task_plan.steps
            # 运行指标只记录可观测量（终态/派发数/阻断数/恢复次数/延迟），不含学生数据。
            record_execution(
                status=status,
                dispatched_steps=sum(attempts_by_id.values()),
                blocked_steps=sum(
                    1 for dispatch_result in results if dispatch_result.note == "dependency_failed"
                ),
                candidate_switches=sum(
                    1
                    for trace in timeline
                    if trace.node == "replan" and trace.note.startswith("candidate_switch")
                ),
                same_step_retries=sum(
                    1
                    for trace in timeline
                    if trace.node == "replan" and trace.note.startswith("authorized_same_step")
                ),
                latency_ms=int((time.monotonic() - dispatch_started) * 1000),
            )
        return result

    @staticmethod
    def _dependency_artifact_hint(
        dependency_ids: list[str], results_by_id: dict[str, DispatchResult]
    ) -> str:
        """Only pass bounded public summaries from completed predecessor artifacts."""
        hints: list[str] = []
        for dependency_id in dependency_ids:
            result = results_by_id.get(dependency_id)
            if result is None or not result.ok:
                continue
            artifact = result.artifact or {}
            title = str(artifact.get("title") or result.summary or "").strip()[:80]
            summary = str(artifact.get("summary") or "").strip()[:120]
            if title:
                hints.append(f"练习紧扣讲义《{title}》要点" + (f"：{summary}" if summary else ""))
        return ("；" + "；".join(hints)) if hints else ""

    @staticmethod
    def _learn_artifact_hint(plan: list[PlanStep], results_by_id: dict[str, DispatchResult]) -> str:
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

    @staticmethod
    def _collapse_redispatched_results(
        results: list[DispatchResult],
    ) -> list[DispatchResult]:
        """同一 step_id 多次派发（learn 有界反思重派）→ 收敛为该步**最新一次**结果，
        保持首次出现的原序位置。无重复 step_id 时为恒等映射（对既有路径零影响）。"""
        latest: dict[str, DispatchResult] = {}
        order: list[str] = []
        for result in results:
            if result.step_id not in latest:
                order.append(result.step_id)
            latest[result.step_id] = result
        return [latest[step_id] for step_id in order]

    async def _node_reflect(self, state: OrchestratorState) -> dict[str, Any]:
        """有界反思（A3 · N=1）：learn 阶段执行类失败的资源步降级后经 dispatcher 重派一次。

        触发条件：learn 阶段、``ok=False`` 且 ``artifact.kind=="dispatch_failure"``
        （治理观测失败；子 agent 诚实的业务性 FAILED 不在此列）。命中后将 ``resource_type``
        沿 CODE_LAB→HANDOUT→READING 降一档，经 ``_dispatch_step``（→ dispatcher →
        ``run_with_governance``，复用既有熔断/超时治理）**仅重派一次**：

        - 已是链尾 READING / 不可降级 → 不重派；
        - 重派仍失败 → 保留失败结果走完；
        - **严格 N=1**：每步至多一次反思重派，不构成无界 ReAct 循环。

        本节点只追加重派结果与 trace，不改写 plan / intent / recent_failures，故对
        negotiate 与 recent_failures→replan 既有路由零影响。重派结果与原失败共享 step_id，
        由 ``_assemble`` 的 ``_collapse_redispatched_results`` 收敛为该步最终结果。
        """
        # 签名 TaskPlan 的恢复只能发生在 _node_dispatch 内，并且只能原 Agent/工具重试。
        # 旧资源类型降级会改变真实工具语义，严禁用于受 RouteDecision 约束的请求。
        if state.get("task_plan") is not None:
            return {}
        req = state["req"]
        plan = state.get("plan", [])
        # dispatch 节点已按 plan 原序写入；step_id → 最近一次结果。
        latest_by_id: dict[str, DispatchResult] = {
            result.step_id: result for result in state.get("dispatch_results", [])
        }
        retry_results: list[DispatchResult] = []
        retry_traces: list[GraphTraceStep] = []
        for step in plan:
            if step.phase != "learn":
                continue
            result = latest_by_id.get(step.id)
            if result is None or result.ok or not self._is_dispatch_failure(result):
                continue
            downgraded = self._dispatcher.downgraded_learn_step(step)
            if downgraded is None:
                # 已是 READING / 不可降级 → 保留失败结果，不重派（杜绝无界重试）。
                continue

            note = f"reflect_retry:learn {step.resource_type or '-'}" f"→{downgraded.resource_type}"
            reflect_step = GraphTraceStep(node="reflect", elapsed_ms=0, status="retry", note=note)
            await self._emit_cb("trace", reflect_step.model_dump(mode="json"))
            await self._emit_cb(
                "trace",
                {
                    "node": f"dispatch.{downgraded.agent}",
                    "step_id": downgraded.id,
                    "phase": "start",
                    "status": "running",
                    "elapsed_ms": 0,
                    "agent_label": self._agent_display_name(downgraded.agent),
                    "note": f"反思重派（降级为 {downgraded.resource_type}）：{downgraded.title}",
                },
            )
            started = time.monotonic()
            retry = await self._dispatch_step(downgraded, req.user_id)
            elapsed_ms = int((time.monotonic() - started) * 1000)
            if not retry.ok and self._is_dispatch_failure(retry):
                await self._emit_cb(
                    "step_error",
                    {
                        "code": "DISPATCH_FAILED",
                        "agent": downgraded.agent,
                        "step_id": downgraded.id,
                        "message": retry.summary,
                    },
                )
            await self._emit_cb(
                "trace",
                {
                    "node": f"dispatch.{downgraded.agent}",
                    "step_id": downgraded.id,
                    "phase": "finish",
                    "status": "ok" if retry.ok else "fail",
                    "elapsed_ms": elapsed_ms,
                    "agent_label": self._agent_display_name(downgraded.agent),
                    "note": retry.summary,
                },
            )
            retry_results.append(retry)
            retry_traces.append(reflect_step)
            retry_traces.append(
                GraphTraceStep(
                    node=f"dispatch.{downgraded.agent}",
                    elapsed_ms=elapsed_ms,
                    status="ok" if retry.ok else "fail",
                    note=f"reflect_retry:learn {retry.summary}",
                )
            )

        if not retry_results:
            return {}
        return {"dispatch_results": retry_results, "trace": retry_traces}

    async def _node_replan_failures(self, state: OrchestratorState) -> dict[str, Any]:
        req = state["req"]
        plan = list(state["plan"])
        failed = self._ground_failures(req, state["allowed_set"])
        debug_step = self._build_debug_step(failed)
        self._insert_before_assess(plan, debug_step)
        replan_step = self._sync_step("replan", note=f"recent_failures:{','.join(failed[:3])}")
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
        reply_md, step = await self._stream_reply(state["req"], state["plan"], None)
        return {"reply_md": reply_md, "trace": [step]}

    async def _node_clarify(self, state: OrchestratorState) -> dict[str, Any]:
        reply_md, step = await self._stream_reply(state["req"], [], state.get("clarifying"))
        return {"reply_md": reply_md, "trace": [step], "needs_user_input": True}

    @staticmethod
    def _route_after_understand(state: OrchestratorState) -> str:
        return "clarify" if state.get("ambiguous") else "build_plan"

    @staticmethod
    def _route_after_build_plan(state: OrchestratorState) -> str:
        if not state.get("reused_plan"):
            return "dispatch"
        # 复用资源计划时只需向学生说明调整结果，避免重复生成整套工件；但 TutorAgent
        # 步骤本身就是本轮答疑产物，若在协商/继续轮被直接跳到 compose，学生画像与降阶
        # 要求都不会真正进入导师调用。仅放行 TutorAgent，保持资源步骤的去重语义。
        if any(step.agent == "TutorAgent" for step in state.get("plan", [])):
            return "dispatch"
        return "compose"

    def _route_after_dispatch(self, state: OrchestratorState) -> str:
        if state["intent"] == "negotiate":
            return "extract_profile"
        # authoritative TaskPlan 已在 dispatch 内按 route policy 完成依赖、评估和至多一次
        # 原步重规划；旧 recent_failures 分支会擅自插入 DebugAgent，必须跳过。
        if state.get("task_plan") is not None:
            return "extract_profile"
        failed = self._ground_failures(state["req"], state["allowed_set"])
        if failed and not any(step.agent == "DebugAgent" for step in state["plan"]):
            return "replan"
        return "extract_profile"

    async def _stream_reply(
        self, req: OrchestratorRequest, plan: list[PlanStep], clarifying: str | None
    ) -> tuple[str, GraphTraceStep]:
        """流式产出回复并返回 (reply_md, compose_reply trace 步)。

        token **先缓冲到句/段边界过一遍 scan_safety 再放行**：旧实现每个 token 立刻
        emit，等 _assemble 算出 safety 判决时学生早已在屏幕上读完了完整可提交答案，
        result.reply_md 换成降级模板已经来不及。命中关键词后停止放行剩余 token
        （仍继续消费上游以拿到完整文本），由 _assemble 统一给出降级回复。

        取舍：首包延迟从「第一个 token」变成「第一个句子边界（或 ``_SAFETY_FLUSH_MAX_CHARS``
        个字符，取先到者）」，量级是几百毫秒；换来的是越权内容一个字都不会到达学生屏幕。

        provider 不可用时不抛裸异常：改用 OrchestratorAgent.fallback_reply 的确定性
        教学兜底（结构化 plan 清单 / 单个澄清问题），trace note 标注 deterministic_fallback。
        """
        started = time.monotonic()
        parts: list[str] = []
        buffer: list[str] = []
        withheld = False
        try:
            async for token in self._agent.reply_stream(req, plan, clarifying):
                parts.append(token)
                if withheld:
                    continue
                buffer.append(token)
                pending = "".join(buffer)
                if not self._is_flush_boundary(pending):
                    continue
                withheld = not OrchestratorAgent.scan_safety("".join(parts))[0]
                buffer.clear()
                if withheld:
                    LOG.warning("compose_reply 流式内容命中安全关键词，已停止放行剩余 token")
                    continue
                await self._emit_cb("token", {"delta": pending})
            if not withheld and buffer:
                withheld = not OrchestratorAgent.scan_safety("".join(parts))[0]
                if not withheld:
                    await self._emit_cb("token", {"delta": "".join(buffer)})
        except Exception as exc:  # noqa: BLE001 – provider 不可用：确定性教学兜底
            LOG.warning("compose_reply 流式失败（%s），改用确定性教学兜底回复", exc)
            fallback_md = self._agent.fallback_reply(req, plan, clarifying)
            await self._emit_cb("token", {"delta": fallback_md})
            step = GraphTraceStep(
                node="compose_reply",
                elapsed_ms=int((time.monotonic() - started) * 1000),
                status="ok",
                note=f"deterministic_fallback:{type(exc).__name__}",
            )
            await self._emit_cb("trace", step.model_dump(mode="json"))
            return fallback_md, step
        step = GraphTraceStep(
            node="compose_reply",
            elapsed_ms=int((time.monotonic() - started) * 1000),
            status="fail" if withheld else "ok",
            note="safety_withheld_stream" if withheld else "",
        )
        await self._emit_cb("trace", step.model_dump(mode="json"))
        return "".join(parts), step

    @staticmethod
    def _is_flush_boundary(pending: str) -> bool:
        """句/段边界，或达到有界长度上限（无标点长句也必须按段过审，不能无限攒）。"""
        return pending.endswith(_SAFETY_FLUSH_BOUNDARIES) or len(pending) >= _SAFETY_FLUSH_MAX_CHARS

    def _assemble(self, allowed_set: set[str], final: dict[str, Any]) -> OrchestratorResponse:
        reply_md = final.get("reply_md", "")
        trace = final.get("trace", [])
        goal = final.get("goal") or ""
        safety_passed, safety_reasons = OrchestratorAgent.scan_safety(reply_md)
        safety = SafetyResult(passed=safety_passed, reasons=safety_reasons)

        if final.get("needs_user_input"):
            # 澄清轮：critic 闸门只看 safety（grounding 视作通过）；越权内容→诚实降级。
            clarify_verdict = CriticVerdict(
                grounding_pass=True, safety_pass=safety.passed, reasons=list(safety_reasons)
            )
            self._loop.record_eval(clarify_verdict)
            clarify_reply = reply_md
            if not clarify_verdict.passed:
                clarify_reply = self._loop.downgrade_reply(
                    goal=goal, reasons=clarify_verdict.reasons
                )
            return OrchestratorResponse(
                reply_md=clarify_reply,
                clarifying_question=final.get("clarifying"),
                needs_user_input=True,
                plan=[],
                route_decision=final.get("req").route_decision if final.get("req") else None,
                task_plan=final.get("task_plan"),
                dispatch_results=[],
                profile_updates=[],
                trace=trace,
                done=False,
                safety_result=safety,
                grounding_result=GroundingResult(passed=True),
            )

        plan = final.get("plan", [])
        dispatch_results = self._collapse_redispatched_results(final.get("dispatch_results", []))
        task_plan = final.get("task_plan")
        if task_plan is not None:
            step_statuses = [step.status for step in task_plan.steps]
            task_status = (
                "COMPLETED"
                if step_statuses and all(step_status == "done" for step_status in step_statuses)
                else "PARTIAL"
                if any(step_status == "done" for step_status in step_statuses)
                else "FAILED"
            )
            payload = task_plan.model_dump(mode="python")
            payload["status"] = task_status
            task_plan = TaskPlan.model_validate(payload)
            route_decision = final.get("req").route_decision if final.get("req") else None
            if route_decision is not None:
                validate_task_plan(task_plan, route_decision)
            done = task_status == "COMPLETED"
        else:
            done = bool(plan) and all(step.status in ("done", "skipped") for step in plan)
        request = final.get("req")
        decision = request.route_decision if request else None
        grounding = self._grounding(
            plan,
            allowed_set,
            require_grounding=bool(decision and decision.policies.require_grounding),
        )

        # critic 闸门（§3.6 / §4.3）：grounding + safety 双轨裁决 → critic 后调 eval_span。
        verdict = CriticVerdict(
            grounding_pass=grounding.passed,
            safety_pass=safety.passed,
            reasons=list(grounding.reasons) + list(safety_reasons),
        )
        self._loop.record_eval(verdict)
        final_reply = reply_md
        if not verdict.passed:
            # critic 拒绝：既有有界恢复（reflect N=1 + replan）已尽 → 不 surface 越权 /
            # 未达证据标准内容，给诚实「未达证据标准」降级回复（不编造）。
            final_reply = self._loop.downgrade_reply(goal=goal, reasons=verdict.reasons)

        return OrchestratorResponse(
            reply_md=final_reply,
            clarifying_question=None,
            needs_user_input=False,
            plan=plan,
            route_decision=final.get("req").route_decision if final.get("req") else None,
            task_plan=task_plan,
            dispatch_results=dispatch_results,
            profile_updates=final.get("signals", []),
            trace=trace,
            done=done,
            safety_result=safety,
            grounding_result=grounding,
        )

    async def _persist(self, req: OrchestratorRequest, response: OrchestratorResponse) -> None:
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
        """真正 await 子 agent（经 AgentDispatcher），不再只回文本摘要。

        BROWSE 工具步（``agent == "BrowseAgent"``）走有界闭环的 BROWSE 派发路径
        （§3.1，受 scope/allowlist 收缩），其余沿用既有 AgentDispatcher。
        """
        if step.agent == "BrowseAgent":
            return await self._dispatch_browse(step, user_id)
        return await self._dispatcher.dispatch(
            step, user_id=user_id, focus_hint_extra=focus_hint_extra
        )

    def _resolve_browse_allowlist(self) -> list[str]:
        """解析 BROWSE allowlist：构造注入优先，否则读 ``BROWSE_ALLOWED_DOMAINS``（逗号分隔）。

        空 = 拒绝发起（§3.4）。完整安全墙交集由 B1 ``allowlist.py`` 落地（Phase 2 接线）。
        """
        if self._browse_allowed_domains is not None:
            return list(self._browse_allowed_domains)
        raw = os.environ.get("BROWSE_ALLOWED_DOMAINS", "")
        return [domain.strip() for domain in raw.split(",") if domain.strip()]

    async def _dispatch_browse(self, step: PlanStep, user_id: int) -> DispatchResult:
        """有界 BROWSE 派发：scope/allowlist 闸门 → mock/真 executor；外部引用标注非课件。

        不可派发（考核 scope / 空 allowlist / 未接线 executor）→ ok=True 但携回退 VIDEO 提示，
        不报错；executor 异常 → ok=False（dispatch_failure）+ 回退；成功 → 外部引用带标签。
        """
        started = time.monotonic()
        dispatch = await self._loop.run_browse(
            self._browse_executor,
            goal=(step.detail or step.title),
            user_id=user_id,
            allowed_domains=self._resolve_browse_allowlist(),
            scope=self._loop.scope,
        )
        elapsed_ms = int((time.monotonic() - started) * 1000)
        if not dispatch.dispatched:
            return DispatchResult(
                step_id=step.id,
                agent="BrowseAgent",
                ok=True,
                summary=f"联网查证不可派发（{dispatch.reason}），建议回退 {dispatch.fallback}",
                note=dispatch.reason,
                elapsed_ms=elapsed_ms,
                artifact={
                    "kind": "browse_skipped",
                    "reason": dispatch.reason,
                    "fallback": dispatch.fallback,
                },
            )
        if dispatch.error:
            return DispatchResult(
                step_id=step.id,
                agent="BrowseAgent",
                ok=False,
                summary=f"联网查证失败（回退 {dispatch.fallback}）：{dispatch.error}",
                note=dispatch.error,
                elapsed_ms=elapsed_ms,
                artifact={
                    "kind": "dispatch_failure",
                    "status": "browse_error",
                    "error_class": "BrowseError",
                    "fallback": dispatch.fallback,
                },
            )
        citations = [citation.ref for citation in dispatch.citations]
        final_summary = dispatch.result.final_summary if dispatch.result else ""
        return DispatchResult(
            step_id=step.id,
            agent="BrowseAgent",
            ok=True,
            summary=(
                f"联网查证完成：{final_summary[:120]}"
                f"（{len(citations)} 条外部引用，{EXTERNAL_CONTEXT_TAG}）"
            ),
            note=final_summary[:200] or None,
            elapsed_ms=elapsed_ms,
            artifact={
                "kind": "browse_result",
                "external": True,
                "tag": EXTERNAL_CONTEXT_TAG,
                "citations": citations,
                "truncated": bool(dispatch.result.truncated) if dispatch.result else False,
            },
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
    def _grounding(
        plan: list[PlanStep], allowed_set: set[str], *, require_grounding: bool = False
    ) -> GroundingResult:
        """按 RouteDecision.policies.require_grounding 决定空目录时是过还是拒。

        目录为空原本无条件 ``passed=True``：路由明明签发了 require_grounding，
        执行期却因为证据目录没配上而静默放行，等于策略字段无人执行。
        没有 RouteDecision 的旧路径保持原语义（require_grounding=False）。
        """
        if not allowed_set:
            if require_grounding:
                return GroundingResult(
                    passed=False,
                    no_evidence_found=True,
                    reasons=["EVIDENCE_CATALOGUE_UNAVAILABLE"],
                )
            return GroundingResult(passed=True, no_evidence_found=True)
        invalid: list[str] = []
        for step in plan:
            for code in step.kc_codes:
                if code not in allowed_set and code not in invalid:
                    invalid.append(code)
        if invalid:
            return GroundingResult(passed=False, reasons=[f"UNKNOWN_KC:{code}" for code in invalid])
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
            "BrowseAgent": "联网查证员",
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
