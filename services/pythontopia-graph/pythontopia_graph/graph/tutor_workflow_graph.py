"""导学工作流 StateGraph：6 阶段状态机 + 卡片节点编排（移植 Alethicode tutor-graph）。

控制流（与源版 builder.py 同构，按 SSE 单请求架构裁剪）::

    START → validate_transition
              ├─ (非法迁移) → emit_failed → END
              └─ → fetch_evidence → <事件节点> → validate_card → decide_actions → END

- 阶段迁移规则原样移植（transitions.py），非法迁移 fail fast（TRANSITION_VIOLATION）；
- 卡片节点产出经 pydantic 强校验（agents/tutor_workflow_cards），违规 SCHEMA_VIOLATION；
- CODING 为纯状态推进事件（无卡片，源版 coding 节点无执行轨迹请求时同此行为）；
- 课件 RAG 仅 READING / AC_REVIEW 需要（源版 EVENT_EVIDENCE_REQUIREMENTS 同款），
  经 EvidenceFetcher（治理：超时 + 熔断由调用方 run_with_governance 包裹）；
- 节点进度经 emit 回调发 SSE STEP_*（orchestrator_graph 同款注入模式）。

会话阶段权威存于 tutor_workflow_session（进程内单 worker）；Java 投影负责持久化。
"""

from __future__ import annotations

import time
from collections.abc import Awaitable, Callable
from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from pythontopia_graph.agents.guide_coach_agent import CoachContext, GuideCoachAgent
from pythontopia_graph.agents.tutor_workflow_cards import (
    CardGenerationError,
    TutorWorkflowCardAgent,
)
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, EvidenceFetcher
from pythontopia_graph.schemas.tutor_workflow import (
    EVENT_OUTPUT_KEY,
    GuideCoachDecision,
    TutorStudentState,
    WorkflowAction,
    WorkflowDiagnosisContext,
    WorkflowEvent,
    WorkflowProblemContext,
    WorkflowRunRequest,
)

Emit = Callable[[str, dict[str, Any]], Awaitable[None]]

# ── 阶段迁移规则（原样移植 Alethicode transitions.py，事件集裁剪后） ──

PHASE_TRANSITIONS: dict[str, frozenset[str]] = {
    "READING": frozenset({"READING", "IDEATING", "CODING", "ERROR_FEEDBACK", "AC_REVIEW"}),
    "IDEATING": frozenset({"IDEATING", "CODING", "ERROR_FEEDBACK", "AC_REVIEW"}),
    "CODING": frozenset({"CODING", "ERROR_FEEDBACK", "AC_REVIEW"}),
    "ERROR_FEEDBACK": frozenset({"ERROR_FEEDBACK", "READING", "IDEATING", "CODING", "AC_REVIEW"}),
    "AC_REVIEW": frozenset({"AC_REVIEW", "TRANSFER"}),
    "TRANSFER": frozenset({"TRANSFER", "CODING", "ERROR_FEEDBACK", "AC_REVIEW"}),
}

# 辅助事件：不改变 Phase（任意阶段可触发，与源版 AUXILIARY_EVENTS 对齐）。
# SKELETON 任意阶段可请求骨架；CHAT 任意阶段可问；KNOWLEDGE_REVIEW / EXECUTION_TRACE /
# VISUALIZE / PARSONS 为补齐的辅助导学动作，均不推进阶段。
AUXILIARY_EVENTS: frozenset[str] = frozenset(
    {"CHAT", "SKELETON", "KNOWLEDGE_REVIEW", "EXECUTION_TRACE", "VISUALIZE", "PARSONS"}
)

# 需要判题证据的事件（请求体必须带 diagnosis）。
DIAGNOSIS_EVENTS: frozenset[str] = frozenset({"ERROR_FEEDBACK", "AC_REVIEW"})

# 需要课件 RAG 证据的事件（源版 EVENT_EVIDENCE_REQUIREMENTS 中带 courseware_hits 的子集）。
# KNOWLEDGE_REVIEW 复盘需课件页支撑（review_content 须引用课件页码）。
COURSEWARE_EVENTS: frozenset[str] = frozenset({"READING", "AC_REVIEW", "KNOWLEDGE_REVIEW"})

# 辅助导学动作（任意阶段可用；不推进 Phase，对齐源版「visualize/parsons 全阶段可用」）。
_AUX_ACTIONS: list[dict[str, str]] = [
    {"key": "visualize", "label": "画一下", "event": "VISUALIZE"},
    {"key": "execution_trace", "label": "执行轨迹讲解", "event": "EXECUTION_TRACE"},
    {"key": "knowledge_review", "label": "知识点复盘", "event": "KNOWLEDGE_REVIEW"},
    {"key": "parsons", "label": "拼装挑战", "event": "PARSONS"},
]

# 阶段专属动作（推进 Phase 的主线动作）。
_PHASE_SPECIFIC_ACTIONS: dict[str, list[dict[str, str]]] = {
    "READING": [
        {"key": "problem_guide", "label": "题目导读", "event": "READING"},
        {"key": "ideate", "label": "思路分析", "event": "IDEATING"},
        {"key": "skeleton", "label": "骨架代码", "event": "SKELETON"},
    ],
    "IDEATING": [
        {"key": "ideate", "label": "继续思路分析", "event": "IDEATING"},
        {"key": "skeleton", "label": "骨架代码", "event": "SKELETON"},
        {"key": "coding", "label": "开始编码", "event": "CODING"},
    ],
    "CODING": [
        {"key": "skeleton", "label": "骨架代码", "event": "SKELETON"},
        {"key": "re_ideate", "label": "重新梳理思路", "event": "IDEATING"},
    ],
    "ERROR_FEEDBACK": [
        {"key": "re_read", "label": "重新审题", "event": "READING"},
        {"key": "re_ideate", "label": "重新梳理思路", "event": "IDEATING"},
        {"key": "coding", "label": "继续编码", "event": "CODING"},
    ],
    "AC_REVIEW": [
        {"key": "transfer", "label": "迁移练习", "event": "TRANSFER"},
    ],
    "TRANSFER": [
        {"key": "transfer", "label": "重新生成迁移题", "event": "TRANSFER"},
        {"key": "coding", "label": "返回编码", "event": "CODING"},
    ],
}

# 阶段 → 可用动作（阶段专属 + 全阶段辅助动作，对齐源版补齐 visualize/parsons 等后的 PHASE_ACTIONS）。
PHASE_ACTIONS: dict[str, list[dict[str, str]]] = {
    phase: [*actions, *_AUX_ACTIONS] for phase, actions in _PHASE_SPECIFIC_ACTIONS.items()
}


class TransitionError(Exception):
    """阶段与事件迁移不合法。"""

    def __init__(self, current_phase: str, event: str, reason: str = "") -> None:
        self.current_phase = current_phase
        self.event = event
        self.reason = reason
        message = f"Illegal transition: {current_phase} -> {event}"
        if reason:
            message += f" ({reason})"
        super().__init__(message)


def validate_transition(current_phase: str, event: str) -> str:
    """校验迁移并返回新 Phase；辅助事件不改变 Phase；非法迁移抛 TransitionError。"""
    event = event.upper()
    current_phase = current_phase.upper()
    allowed = PHASE_TRANSITIONS.get(current_phase)
    if allowed is None:
        raise TransitionError(current_phase, event, "unknown current phase")
    if event in AUXILIARY_EVENTS:
        return current_phase
    if event not in allowed:
        raise TransitionError(current_phase, event, "not allowed from current phase")
    return event


def decide_actions(phase: str, *, has_post_ac: bool) -> list[WorkflowAction]:
    """阶段可用动作；TRANSFER 入口仅在已有复盘方向（post_ac）时给出。"""
    actions = [WorkflowAction(**action) for action in PHASE_ACTIONS.get(phase, [])]
    if not has_post_ac:
        actions = [action for action in actions if action.event != "TRANSFER"]
    return actions


def _coach_allowed_events(
    request: WorkflowRunRequest,
    *,
    current_phase: str,
    new_phase: str,
    has_post_ac: bool,
) -> list[WorkflowEvent]:
    """Coach 决策白名单：请求事件 + 当前/目标阶段动作，诊断类事件需有判题证据。"""
    candidates: list[str] = [request.event]
    for phase in (current_phase, new_phase):
        candidates.extend(action.event for action in decide_actions(phase, has_post_ac=has_post_ac))
    allowed: list[WorkflowEvent] = []
    seen: set[str] = set()
    for candidate in candidates:
        token = str(candidate).upper()
        if token in seen:
            continue
        if token in DIAGNOSIS_EVENTS and request.diagnosis is None:
            continue
        seen.add(token)
        allowed.append(token)  # type: ignore[arg-type]
    return allowed or [request.event]


def _rank_actions(
    actions: list[WorkflowAction], decision: GuideCoachDecision
) -> list[WorkflowAction]:
    """把 coach 推荐动作排到首位；不存在于阶段动作时不强插非法按钮。"""
    ranked: list[WorkflowAction] = []
    for action in actions:
        if action.event == decision.next_event:
            ranked.append(
                action.model_copy(update={"recommended": True, "reason": decision.reason_summary})
            )
        else:
            ranked.append(action.model_copy(update={"recommended": False, "reason": ""}))
    ranked.sort(key=lambda action: 0 if action.recommended else 1)
    return ranked


# ── LangGraph 状态与图 ──


class WorkflowGraphState(TypedDict, total=False):
    request: WorkflowRunRequest
    current_phase: str
    new_phase: str
    last_cards: list[dict[str, Any]]
    chat_history: list[dict[str, str]]
    # AC 复盘给出的下一步方向（TRANSFER 依赖；由会话内最近 post_ac 卡回填）。
    next_practice_direction: str
    evidence_chunks: list[Any]
    card_key: str | None
    card: dict[str, Any] | None
    effective_event: WorkflowEvent
    coach_decision: GuideCoachDecision
    student_state: TutorStudentState
    runtime_state: str
    failure_bucket: str | None
    last_error: str | None
    available_actions: list[WorkflowAction]


class TutorWorkflowGraph:
    """编排一次工作流事件 run；emit 回调逐节点发 SSE STEP_* 事件。"""

    def __init__(
        self,
        provider: LlmProvider,
        evidence_fetcher: EvidenceFetcher,
        *,
        emit: Emit,
    ) -> None:
        self._agent = TutorWorkflowCardAgent(provider)
        self._coach = GuideCoachAgent(provider)
        self._evidence_fetcher = evidence_fetcher
        self._emit = emit
        self._compiled = self._build()

    async def run(self, state: WorkflowGraphState) -> WorkflowGraphState:
        return await self._compiled.ainvoke(state)

    # ── 节点 ──

    async def _node_validate_transition(self, state: WorkflowGraphState) -> WorkflowGraphState:
        request = state["request"]
        started = time.monotonic()
        await self._emit_step_start("validate_transition")
        try:
            new_phase = validate_transition(state.get("current_phase", "READING"), request.event)
        except TransitionError as exc:
            await self._emit_step_finish("validate_transition", "fail", started, note=str(exc))
            return {
                **state,
                "runtime_state": "FAILED",
                "failure_bucket": "TRANSITION_VIOLATION",
                "last_error": str(exc),
            }
        if request.event in DIAGNOSIS_EVENTS and request.diagnosis is None:
            await self._emit_step_finish(
                "validate_transition", "fail", started, note="diagnosis required"
            )
            return {
                **state,
                "runtime_state": "FAILED",
                "failure_bucket": "INSUFFICIENT_EVIDENCE",
                "last_error": f"diagnosis evidence required for {request.event}",
            }
        await self._emit_step_finish("validate_transition", "ok", started)
        return {**state, "new_phase": new_phase}

    async def _node_coach_decide(self, state: WorkflowGraphState) -> WorkflowGraphState:
        request = state["request"]
        if not request.coach_mode:
            return {**state, "effective_event": request.event}
        started = time.monotonic()
        await self._emit_step_start("coach.decide")
        current_phase = state.get("current_phase", "READING")
        new_phase = state.get("new_phase", current_phase)
        student_state = request.student_state or TutorStudentState(
            session_id=request.session_id,
            user_id=request.user_id,
            problem_id=request.problem.problem_id,
            phase=current_phase,  # type: ignore[arg-type]
            latest_verdict=request.diagnosis.verdict if request.diagnosis else "",
            consecutive_errors=request.diagnosis.consecutive_errors if request.diagnosis else 0,
        )
        allowed_events = _coach_allowed_events(
            request,
            current_phase=current_phase,
            new_phase=new_phase,
            has_post_ac=bool(state.get("next_practice_direction")),
        )
        if request.force_event and request.force_event in allowed_events:
            allowed_events = [request.force_event]
        decision = await self._coach.decide(
            CoachContext(
                requested_event=request.force_event or request.event,
                allowed_events=allowed_events,
                state=student_state,
                problem=request.problem,
                diagnosis=request.diagnosis,
                profile_brief=request.profile_brief,
                recent_cards=state.get("last_cards", []),
            )
        )
        updated_state = student_state.model_copy(
            update={
                "phase": new_phase,
                "latest_verdict": request.diagnosis.verdict
                if request.diagnosis
                else student_state.latest_verdict,
                "consecutive_errors": max(
                    student_state.consecutive_errors,
                    request.diagnosis.consecutive_errors if request.diagnosis else 0,
                ),
                "hint_level": decision.hint_level,
                "last_action": decision.tool_name,
                "next_best_action": decision.next_event,
            }
        )
        await self._emit_step_finish(
            "coach.decide",
            "ok",
            started,
            next_event=decision.next_event,
            tool_name=decision.tool_name,
        )
        return {
            **state,
            "effective_event": decision.next_event,
            "coach_decision": decision,
            "student_state": updated_state,
        }

    async def _node_fetch_evidence(self, state: WorkflowGraphState) -> WorkflowGraphState:
        request = state["request"]
        event = state.get("effective_event", request.event)
        if event not in COURSEWARE_EVENTS:
            return {**state, "evidence_chunks": []}
        started = time.monotonic()
        await self._emit_step_start("evidence_fetcher")
        try:
            bundle: EvidenceBundle = await self._evidence_fetcher.fetch(
                kc_codes=request.problem.kc_codes,
                query=f"{request.problem.title}\n{request.problem.statement}"[:500],
                top_k=3,
            )
            chunks = list(bundle.chunks)
            status = "ok"
        except Exception:  # noqa: BLE001 - RAG 故障降级为无证据（导学卡仍可产出）
            chunks = []
            status = "retry"
        await self._emit_step_finish("evidence_fetcher", status, started, chunks=len(chunks))
        return {**state, "evidence_chunks": chunks}

    async def _node_generate_card(self, state: WorkflowGraphState) -> WorkflowGraphState:
        request = state["request"]
        event = state.get("effective_event", request.event)
        card_key = EVENT_OUTPUT_KEY.get(event)
        if event == "CODING":
            # 纯状态推进：学生宣告进入编码，不产卡（源版 coding 节点同此默认行为）。
            return {**state, "card_key": None, "card": None}
        started = time.monotonic()
        node_name = f"card.{card_key}"
        await self._emit_step_start(node_name)
        try:
            card = await self._dispatch_card(state, request.model_copy(update={"event": event}))
        except CardGenerationError as exc:
            await self._emit_step_finish(node_name, "fail", started, note=str(exc))
            return {
                **state,
                "runtime_state": "FAILED",
                "failure_bucket": exc.bucket,
                "last_error": str(exc),
            }
        await self._emit_step_finish(node_name, "ok", started)
        return {**state, "card_key": card_key, "card": card}

    async def _dispatch_card(
        self, state: WorkflowGraphState, request: WorkflowRunRequest
    ) -> dict[str, Any]:
        problem: WorkflowProblemContext = request.problem
        diagnosis: WorkflowDiagnosisContext | None = request.diagnosis
        brief = request.profile_brief
        event = request.event
        if event == "READING":
            return await self._agent.problem_guide(problem, state.get("evidence_chunks", []), brief)
        if event == "IDEATING":
            thought = str(request.event_data.get("thought_text", ""))
            return await self._agent.ideate(problem, thought, brief)
        if event == "SKELETON":
            return await self._agent.skeleton(problem, brief)
        if event == "ERROR_FEEDBACK":
            assert diagnosis is not None  # validate_transition 已校验
            return await self._agent.error_diagnosis(problem, diagnosis, brief)
        if event == "AC_REVIEW":
            assert diagnosis is not None
            return await self._agent.post_ac(
                problem, diagnosis, state.get("evidence_chunks", []), brief
            )
        if event == "TRANSFER":
            direction = state.get("next_practice_direction", "") or str(
                request.event_data.get("next_practice_direction", "")
            )
            return await self._agent.transfer(problem, direction, brief)
        if event == "KNOWLEDGE_REVIEW":
            return await self._agent.knowledge_review(
                problem, state.get("evidence_chunks", []), brief
            )
        if event == "EXECUTION_TRACE":
            code = str(request.event_data.get("code", ""))
            return await self._agent.execution_trace(problem, code, brief)
        if event == "VISUALIZE":
            return await self._agent.visualize(
                problem,
                state.get("new_phase", "READING"),
                str(request.event_data.get("intent", "")),
                str(request.event_data.get("code", "")),
                brief,
            )
        if event == "PARSONS":
            return await self._agent.parsons(
                problem, str(request.event_data.get("sub_skill", "")), brief
            )
        if event == "CHAT":
            message = str(request.event_data.get("message", "")).strip()
            if not message:
                raise CardGenerationError("INSUFFICIENT_EVIDENCE", "CHAT requires event_data.message")
            return await self._agent.chat(
                problem,
                state.get("new_phase", "READING"),
                message,
                state.get("last_cards", []),
                state.get("chat_history", []),
                brief,
            )
        raise CardGenerationError("SYSTEM_ERROR", f"unsupported event: {event}")

    async def _node_decide_actions(self, state: WorkflowGraphState) -> WorkflowGraphState:
        phase = state.get("new_phase", "READING")
        has_post_ac = bool(state.get("next_practice_direction")) or state.get("card_key") == "post_ac"
        actions = decide_actions(phase, has_post_ac=has_post_ac)
        decision = state.get("coach_decision")
        if decision is not None:
            actions = _rank_actions(actions, decision)
        return {**state, "available_actions": actions}

    # ── 图装配 ──

    def _build(self) -> Any:
        graph: StateGraph = StateGraph(WorkflowGraphState)
        graph.add_node("validate_transition", self._node_validate_transition)
        graph.add_node("coach_decide", self._node_coach_decide)
        graph.add_node("fetch_evidence", self._node_fetch_evidence)
        graph.add_node("generate_card", self._node_generate_card)
        graph.add_node("decide_actions", self._node_decide_actions)

        graph.add_edge(START, "validate_transition")
        graph.add_conditional_edges(
            "validate_transition",
            lambda state: "end" if state.get("runtime_state") == "FAILED" else "coach_decide",
            {"end": END, "coach_decide": "coach_decide"},
        )
        graph.add_edge("coach_decide", "fetch_evidence")
        graph.add_edge("fetch_evidence", "generate_card")
        graph.add_conditional_edges(
            "generate_card",
            lambda state: "end" if state.get("runtime_state") == "FAILED" else "decide_actions",
            {"end": END, "decide_actions": "decide_actions"},
        )
        graph.add_edge("decide_actions", END)
        return graph.compile()

    # ── SSE 协助 ──

    async def _emit_step_start(self, node: str) -> None:
        await self._emit(
            "trace",
            {"node": node, "phase": "start", "status": "running", "elapsed_ms": 0},
        )

    async def _emit_step_finish(
        self, node: str, status: str, started: float, **extra: Any
    ) -> None:
        payload: dict[str, Any] = {
            "node": node,
            "phase": "finish",
            "status": status,
            "elapsed_ms": int((time.monotonic() - started) * 1000),
        }
        payload.update(extra)
        await self._emit("trace", payload)
