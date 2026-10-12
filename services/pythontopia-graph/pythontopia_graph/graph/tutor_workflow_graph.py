"""AutoGen 编排的 6 阶段导学状态机 + 卡片节点（移植 Alethicode tutor-graph）。

控制流由固定顺序的 ``RoundRobinGroupChat`` 阶段 Agent 调用现有 handler；每次只运行一轮，
状态以 JSON 信封跨 AgentChat 往返，并在边界恢复为 Pydantic/RAG 类型。Agent 不调用模型来
选择节点或工具。失败状态会停止后续阶段派发。::

    validate_transition → coach_decide → fetch_evidence → generate_card → decide_actions
              └─ (FAILED) → stop

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

from autogen_core import CancellationToken

from pythontopia_graph.agents.guide_coach_agent import CoachContext, GuideCoachAgent
from pythontopia_graph.agents.guide_episode_planner import GuideEpisodePlanner
from pythontopia_graph.agents.guide_guardrail_critic import GuideGuardrailCritic
from pythontopia_graph.agents.tutor_workflow_cards import (
    CardGenerationError,
    TutorWorkflowCardAgent,
)
from pythontopia_graph.graph.autogen_tutor_runtime import (
    PrivateImageResolver,
    StageHandler,
    run_autogen_tutor_workflow,
)
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, EvidenceFetcher, RagHit
from pythontopia_graph.schemas.tutor_workflow import (
    EVENT_OUTPUT_KEY,
    GuideCoachDecision,
    AiReplyCard,
    TutorEpisodePlan,
    TutorEpisodePlanPatch,
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
    {
        "CHAT",
        "AGENT_FEEDBACK",
        "COMPACT",
        "PLAN_RECOMMEND",
        "PLAN_START",
        "PLAN_RESPONSE",
        "PLAN_STEERING",
        "SKELETON",
        "KNOWLEDGE_REVIEW",
        "EXECUTION_TRACE",
        "VISUALIZE",
        "PARSONS",
    }
)

PLAN_EVENTS: frozenset[str] = frozenset(
    {"PLAN_RECOMMEND", "PLAN_START", "PLAN_RESPONSE", "PLAN_STEERING"}
)
_PLAN_CONTROL_EVENTS: frozenset[str] = frozenset(
    {"PLAN_START", "PLAN_RESPONSE", "PLAN_STEERING"}
)
_SUBMISSION_EVIDENCE: frozenset[str] = frozenset(
    {"SUBMISSION_OBSERVED", "AC_SUBMISSION", "TRANSFER_ATTEMPT"}
)
_COMPACT_CHAT_TURNS = 8

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
    candidates: list[str] = [] if request.event == "PLAN_RECOMMEND" else [request.event]
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


def _append_guardrail_note(profile_brief: str, reasons: list[str]) -> str:
    note = (
        "Guardrail feedback: 上一次导学卡被拦截，原因为 "
        f"{', '.join(reasons)}。请重新生成，保留引导性，不给完整可提交答案。"
    )
    return f"{profile_brief}\n{note}".strip()[:1200]


# ── Tutor 状态与工作流 ──


class WorkflowGraphState(TypedDict, total=False):
    request: WorkflowRunRequest
    current_phase: str
    new_phase: str
    last_cards: list[dict[str, Any]]
    chat_history: list[dict[str, str]]
    # AC 复盘给出的下一步方向（TRANSFER 依赖；由会话内最近 post_ac 卡回填）。
    next_practice_direction: str
    evidence_chunks: list[RagHit]
    card_key: str | None
    card: dict[str, Any] | None
    effective_event: WorkflowEvent
    coach_decision: GuideCoachDecision
    student_state: TutorStudentState
    runtime_state: str
    failure_bucket: str | None
    last_error: str | None
    available_actions: list[WorkflowAction]
    episode_plan: TutorEpisodePlan | None
    episode_patch: TutorEpisodePlanPatch | None
    resume_chat_history: list[dict[str, str]]
    # Stage-local image bytes; AutoGen runtime removes this key before encoding a message.
    _tutor_stage_private_image_data: list[str]


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
        self._guardrail = GuideGuardrailCritic()
        self._episode_planner = GuideEpisodePlanner()
        self._evidence_fetcher = evidence_fetcher
        self._emit = emit

    async def run(
        self,
        state: WorkflowGraphState,
        *,
        cancellation_token: CancellationToken | None = None,
        private_image_resolver: PrivateImageResolver | None = None,
        restore_checkpoint: dict[str, Any] | None = None,
        on_checkpoint: Callable[[dict[str, Any]], Awaitable[None]] | None = None,
    ) -> WorkflowGraphState:
        request = state.get("request")
        if request is not None and "episode_plan" not in state:
            state = {**state, "episode_plan": request.episode_plan}
        stages: list[tuple[str, StageHandler[WorkflowGraphState]]] = [
            ("validate_transition", self._node_validate_transition),
            ("coach_decide", self._node_coach_decide),
            ("fetch_evidence", self._node_fetch_evidence),
            ("generate_card", self._node_generate_card),
            ("decide_actions", self._node_decide_actions),
        ]
        return await run_autogen_tutor_workflow(
            state,
            stages,
            cancellation_token=cancellation_token,
            private_image_resolver=private_image_resolver,
            restore_checkpoint=restore_checkpoint,
            on_checkpoint=on_checkpoint,
        )

    # ── 节点 ──

    async def _node_validate_transition(self, state: WorkflowGraphState) -> WorkflowGraphState:
        request = state["request"]
        current_phase = state.get("current_phase", "READING")
        started = time.monotonic()
        await self._emit_step_start("validate_transition")
        try:
            new_phase = validate_transition(current_phase, request.event)
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
        plan = request.episode_plan
        if request.event == "PLAN_RECOMMEND":
            trigger_event = request.event_data.get("trigger_event")
            if trigger_event is not None:
                available = {
                    action.event
                    for action in decide_actions(
                        current_phase,
                        has_post_ac=bool(state.get("next_practice_direction")),
                    )
                    if action.event not in DIAGNOSIS_EVENTS or request.diagnosis is not None
                }
                if trigger_event not in available:
                    await self._emit_step_finish(
                        "validate_transition",
                        "fail",
                        started,
                        note="PLAN_RECOMMEND trigger_event is not an available action",
                    )
                    return {
                        **state,
                        "runtime_state": "FAILED",
                        "failure_bucket": "TRANSITION_VIOLATION",
                        "last_error": "PLAN_RECOMMEND trigger_event is not available in this phase",
                    }
        if plan is not None and (
            plan.session_id != request.session_id
            or plan.user_id != request.user_id
            or plan.problem_id != request.problem.problem_id
        ):
            await self._emit_step_finish(
                "validate_transition", "fail", started, note="episode plan scope mismatch"
            )
            return {
                **state,
                "runtime_state": "FAILED",
                "failure_bucket": "PLAN_SCOPE_MISMATCH",
                "last_error": "episode plan does not belong to this user, session, and problem",
            }
        patch = request.episode_patch
        if patch is not None and plan is None:
            await self._emit_step_finish(
                "validate_transition", "fail", started, note="episode plan patch has no plan"
            )
            return {
                **state,
                "runtime_state": "FAILED",
                "failure_bucket": "PLAN_NOT_FOUND",
                "last_error": "episode plan patch cannot be applied without its saved plan",
            }
        if patch is not None and plan is not None and (
            patch.plan_id != plan.plan_id
            or patch.revision != plan.revision
            or (
                patch.current_step_id is not None
                and patch.current_step_id != plan.current_step_id
            )
            or patch.status not in {None, plan.status}
        ):
            await self._emit_step_finish(
                "validate_transition", "fail", started, note="stale episode plan patch"
            )
            return {
                **state,
                "runtime_state": "FAILED",
                "failure_bucket": "PLAN_STALE",
                "last_error": "episode plan patch does not match the saved plan",
            }
        if request.event == "PLAN_START" and request.event_data.get("current_phase") != current_phase:
            await self._emit_step_finish(
                "validate_transition", "fail", started, note="stale phase in PLAN_START"
            )
            return {
                **state,
                "runtime_state": "FAILED",
                "failure_bucket": "TRANSITION_VIOLATION",
                "last_error": "PLAN_START current_phase does not match the server phase",
            }
        if request.event in _PLAN_CONTROL_EVENTS:
            if plan is None or request.event_data.get("plan_id") != plan.plan_id:
                await self._emit_step_finish(
                    "validate_transition", "fail", started, note="missing or mismatched episode plan"
                )
                return {
                    **state,
                    "runtime_state": "FAILED",
                    "failure_bucket": "PLAN_NOT_FOUND",
                    "last_error": "the referenced episode plan is not active in this session",
                }
            if request.event_data.get("revision") != plan.revision:
                await self._emit_step_finish(
                    "validate_transition", "fail", started, note="stale episode plan revision"
                )
                return {
                    **state,
                    "runtime_state": "FAILED",
                    "failure_bucket": "PLAN_STALE",
                    "last_error": "the episode plan changed after this action was prepared",
                }
            if request.event == "PLAN_RESPONSE":
                active = next((step for step in plan.steps if step.status == "ACTIVE"), None)
                if plan.status != "ACTIVE" or active is None:
                    reason = "episode plan is not active"
                elif request.event_data.get("step_id") != active.step_id:
                    reason = "PLAN_RESPONSE must reference the current active step"
                elif active.expected_evidence in _SUBMISSION_EVIDENCE:
                    reason = "this step requires a verified server submission"
                elif not str(request.event_data.get("response", "")).strip():
                    reason = "PLAN_RESPONSE requires a student response"
                else:
                    reason = ""
                if reason:
                    await self._emit_step_finish(
                        "validate_transition", "fail", started, note=reason
                    )
                    return {
                        **state,
                        "runtime_state": "FAILED",
                        "failure_bucket": "INSUFFICIENT_EVIDENCE",
                        "last_error": reason,
                    }
        await self._emit_step_finish("validate_transition", "ok", started)
        return {**state, "new_phase": new_phase}

    async def _node_coach_decide(self, state: WorkflowGraphState) -> WorkflowGraphState:
        request = state["request"]
        if request.event in {
            "AGENT_FEEDBACK",
            "COMPACT",
            "PLAN_START",
            "PLAN_RESPONSE",
            "PLAN_STEERING",
        }:
            return {**state, "effective_event": request.event}
        if not request.coach_mode:
            return {**state, "effective_event": request.event}
        started = time.monotonic()
        await self._emit_step_start("coach.decide")
        current_phase = state.get("current_phase", "READING")
        new_phase = state.get("new_phase", current_phase)
        load_started = time.monotonic()
        await self._emit_step_start("coach.load_state")
        student_state = request.student_state or TutorStudentState(
            session_id=request.session_id,
            user_id=request.user_id,
            problem_id=request.problem.problem_id,
            phase=current_phase,  # type: ignore[arg-type]
            latest_verdict=request.diagnosis.verdict if request.diagnosis else "",
            consecutive_errors=request.diagnosis.consecutive_errors if request.diagnosis else 0,
        )
        await self._emit_step_finish("coach.load_state", "ok", load_started)
        coach_request = request
        requested_event = request.event
        if request.event == "PLAN_RECOMMEND":
            trigger_event = request.event_data.get("trigger_event")
            requested_event = str(trigger_event or current_phase).upper()
            coach_request = request.model_copy(update={"event": requested_event})
        allowed_events = _coach_allowed_events(
            coach_request,
            current_phase=current_phase,
            new_phase=new_phase,
            has_post_ac=bool(state.get("next_practice_direction")),
        )
        if request.force_event and request.force_event in allowed_events:
            allowed_events = [request.force_event]
        decision = await self._coach.decide(
            CoachContext(
                requested_event=request.force_event or requested_event,  # type: ignore[arg-type]
                allowed_events=allowed_events,
                state=student_state,
                problem=request.problem,
                diagnosis=request.diagnosis,
                profile_brief=request.profile_brief,
                recent_cards=state.get("last_cards", []),
            )
        )
        update_started = time.monotonic()
        await self._emit_step_start("coach.update_state")
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
        await self._emit_step_finish("coach.update_state", "ok", update_started)
        await self._emit_step_finish(
            "coach.decide",
            "ok",
            started,
            next_event=decision.next_event,
            tool_name=decision.tool_name,
        )
        return {
            **state,
            "effective_event": "PLAN_RECOMMEND"
            if request.event == "PLAN_RECOMMEND"
            else decision.next_event,
            "coach_decision": decision,
            "student_state": updated_state,
        }

    async def _node_fetch_evidence(self, state: WorkflowGraphState) -> WorkflowGraphState:
        request = state["request"]
        event = state.get("effective_event", request.event)
        if event not in COURSEWARE_EVENTS or request.event == "PLAN_RECOMMEND":
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
        except Exception:  # noqa: BLE001 - 课件检索失败时不能生成无证据导学卡
            await self._emit_step_finish(
                "evidence_fetcher", "fail", started, chunks=0, note="courseware retrieval failed"
            )
            return {
                **state,
                "evidence_chunks": [],
                "runtime_state": "FAILED",
                "failure_bucket": "TOOL_EXECUTION_FAILED",
                "last_error": "课件检索服务暂不可用，请稍后重试",
            }
        await self._emit_step_finish("evidence_fetcher", "ok", started, chunks=len(chunks))
        return {**state, "evidence_chunks": chunks}

    async def _node_generate_card(self, state: WorkflowGraphState) -> WorkflowGraphState:
        request = state["request"]
        event = state.get("effective_event", request.event)
        if event == "COMPACT":
            history = list(state.get("chat_history", []))[-_COMPACT_CHAT_TURNS:]
            summary_lines = [
                f"{'学生' if turn.get('role') == 'user' else '导师'}：{turn.get('content', '').strip()}"
                for turn in history
                if turn.get("role") in {"user", "assistant"}
                and isinstance(turn.get("content"), str)
                and turn["content"].strip()
            ]
            summary = (
                "已整理并保留最近的对话：\n" + "\n".join(summary_lines)
                if summary_lines
                else "目前没有可整理的对话，当前题目和学习阶段已保留。"
            )[:1600]
            return {
                **state,
                "card_key": "ai_reply",
                "card": AiReplyCard(content=summary).model_dump(mode="json"),
                "resume_chat_history": history,
                "runtime_state": "SUCCEEDED",
            }
        if event in PLAN_EVENTS:
            return self._apply_plan_event(state, request, event)
        card_key = EVENT_OUTPUT_KEY.get(event)
        if event == "CODING":
            # 纯状态推进：学生宣告进入编码，不产卡（源版 coding 节点同此默认行为）。
            return {**state, "card_key": None, "card": None}
        started = time.monotonic()
        node_name = f"card.{card_key}"
        await self._emit_step_start(node_name)
        tool_node = ""
        tool_started = 0.0
        try:
            effective_request = request.model_copy(update={"event": event})
            if request.coach_mode:
                decision = state.get("coach_decision")
                tool_name = getattr(decision, "tool_name", "") or str(card_key or event).lower()
                tool_node = f"coach.tool.{tool_name}"
                tool_started = time.monotonic()
                await self._emit_step_start(tool_node)
            card = await self._dispatch_card(
                state,
                effective_request,
                images=state.get("_tutor_stage_private_image_data"),
            )
            if tool_node:
                await self._emit_step_finish(tool_node, "ok", tool_started)
            card = await self._apply_guardrail(state, effective_request, card_key or "", card)
        except CardGenerationError as exc:
            if tool_node:
                await self._emit_step_finish(tool_node, "fail", tool_started, note=str(exc))
            await self._emit_step_finish(node_name, "fail", started, note=str(exc))
            return {
                **state,
                "runtime_state": "FAILED",
                "failure_bucket": exc.bucket,
                "last_error": str(exc),
            }
        await self._emit_step_finish(node_name, "ok", started)
        return {**state, "card_key": card_key, "card": card, "runtime_state": "SUCCEEDED"}

    async def _dispatch_card(
        self,
        state: WorkflowGraphState,
        request: WorkflowRunRequest,
        *,
        images: list[str] | None = None,
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
        if event in {"CHAT", "AGENT_FEEDBACK"}:
            message = str(request.event_data.get("message", "")).strip()
            if not message:
                raise CardGenerationError(
                    "INSUFFICIENT_EVIDENCE", f"{event} requires event_data.message"
                )
            return await self._agent.chat(
                problem,
                state.get("new_phase", "READING"),
                message,
                state.get("last_cards", []),
                state.get("chat_history", []),
                brief,
                images=images,
                references=request.event_data.get("references", []),
            )
        raise CardGenerationError("SYSTEM_ERROR", f"unsupported event: {event}")

    def _apply_plan_event(
        self,
        state: WorkflowGraphState,
        request: WorkflowRunRequest,
        event: str,
    ) -> WorkflowGraphState:
        plan = state.get("episode_plan") or request.episode_plan
        if event == "PLAN_RECOMMEND":
            decision = state.get("coach_decision")
            student_state = state.get("student_state")
            if decision is None or student_state is None:
                return self._plan_failure(state, "PLAN_RECOMMEND requires a coach decision")
            if plan is not None and plan.status in {"ACTIVE", "PAUSED"}:
                return self._plan_failure(state, "an active episode plan cannot be replaced")
            trigger_event = str(
                request.event_data.get("trigger_event") or state.get("new_phase", "READING")
            ).upper()
            context = CoachContext(
                requested_event=trigger_event,  # type: ignore[arg-type]
                allowed_events=_coach_allowed_events(
                    request.model_copy(update={"event": trigger_event}),
                    current_phase=state.get("current_phase", "READING"),
                    new_phase=state.get("new_phase", "READING"),
                    has_post_ac=bool(state.get("next_practice_direction")),
                ),
                state=student_state,
                problem=request.problem,
                diagnosis=request.diagnosis,
                profile_brief=request.profile_brief,
                recent_cards=state.get("last_cards", []),
            )
            plan = self._episode_planner.plan(context, decision)
            return {
                **state,
                "episode_plan": plan,
                "episode_patch": None,
                "card_key": None,
                "card": None,
                "runtime_state": "SUCCEEDED",
            }

        if plan is None:
            return self._plan_failure(state, "episode plan is not restored for this session")

        payload = request.event_data
        plan_id = str(payload.get("plan_id", ""))
        if plan_id != plan.plan_id:
            return self._plan_failure(state, "episode plan id does not match the saved plan")

        previous = plan
        reason = ""
        changed_step_ids: list[str] = []
        if event == "PLAN_START":
            if plan.status not in {"RECOMMENDED", "PAUSED"}:
                return self._plan_failure(state, "episode plan cannot be started in its current state")
            plan = plan.model_copy(update={"status": "ACTIVE", "revision": plan.revision + 1})
            reason = str(payload.get("reason", "")).strip()
        elif event == "PLAN_RESPONSE":
            response_step = next(
                (step for step in plan.steps if step.step_id == plan.current_step_id), None
            )
            if response_step is None or response_step.status != "ACTIVE":
                return self._plan_failure(state, "episode plan has no active step")
            plan = self._advance_plan(plan, "DONE")
            changed_step_ids.append(response_step.step_id)
            reason = f"学生完成了 {response_step.title} 的引导检查点。"
        elif event == "PLAN_STEERING":
            signal = str(payload.get("signal_type", ""))
            if signal == "pause":
                if plan.status != "ACTIVE":
                    return self._plan_failure(state, "only an active episode plan can pause")
                plan = plan.model_copy(update={"status": "PAUSED", "revision": plan.revision + 1})
                reason = "学生暂停了当前计划。"
            elif signal == "resume":
                if plan.status != "PAUSED":
                    return self._plan_failure(state, "only a paused episode plan can resume")
                plan = plan.model_copy(update={"status": "ACTIVE", "revision": plan.revision + 1})
                reason = "学生恢复了当前计划。"
            elif signal == "skip":
                if plan.status != "ACTIVE":
                    return self._plan_failure(state, "only an active episode plan can skip a step")
                active = next((step for step in plan.steps if step.status == "ACTIVE"), None)
                if active is None:
                    return self._plan_failure(state, "episode plan has no active step")
                changed_step_ids.append(active.step_id)
                plan = self._advance_plan(plan, "SKIPPED")
                reason = f"学生跳过了 {active.title}。"
            elif signal == "take_over":
                if plan.status not in {"RECOMMENDED", "ACTIVE", "PAUSED"}:
                    return self._plan_failure(state, "completed episode plan cannot be taken over")
                plan = plan.model_copy(
                    update={"status": "SURRENDERED", "revision": plan.revision + 1}
                )
                reason = "学生选择自行安排后续步骤。"
            elif signal == "redirect":
                if plan.status not in {"RECOMMENDED", "ACTIVE", "PAUSED"}:
                    return self._plan_failure(state, "completed episode plan cannot be redirected")
                instruction = str(payload.get("redirect_instruction", "")).strip()
                plan = plan.model_copy(
                    update={
                        "status": "PAUSED",
                        "revision": plan.revision + 1,
                        "revision_reason": f"学生请求调整方向：{instruction}"[:500],
                    }
                )
                reason = plan.revision_reason

        if plan is previous:
            return self._plan_failure(state, "unsupported episode plan event")
        plan = plan.model_copy(update={"revision_reason": reason or plan.revision_reason})
        patch = TutorEpisodePlanPatch(
            plan_id=plan.plan_id,
            revision=plan.revision,
            current_step_id=plan.current_step_id,
            revised_step_ids=changed_step_ids,
            revision_reason=plan.revision_reason,
            stop_policy=plan.stop_policy,
            status=plan.status,
        )
        return {
            **state,
            "episode_plan": plan,
            "episode_patch": patch,
            "card_key": None,
            "card": None,
            "runtime_state": "SUCCEEDED",
        }

    def _advance_plan(self, plan: TutorEpisodePlan, final_status: str) -> TutorEpisodePlan:
        current_index = next(
            index for index, step in enumerate(plan.steps) if step.step_id == plan.current_step_id
        )
        steps = list(plan.steps)
        steps[current_index] = steps[current_index].model_copy(update={"status": final_status})
        next_index = next(
            (
                index
                for index in range(current_index + 1, len(steps))
                if steps[index].status == "PENDING"
            ),
            None,
        )
        has_active = next_index is not None
        current_step_id = plan.current_step_id
        if next_index is not None:
            steps[next_index] = steps[next_index].model_copy(update={"status": "ACTIVE"})
            current_step_id = steps[next_index].step_id
            active = steps[next_index]
            stop_policy = (
                "WAIT_FOR_SUBMISSION"
                if active.expected_evidence in _SUBMISSION_EVIDENCE
                else "WAIT_FOR_CLICK"
            )
        else:
            stop_policy = "DONE"
        return plan.model_copy(
            update={
                "steps": steps,
                "current_step_id": current_step_id,
                "status": "ACTIVE" if has_active else "COMPLETED",
                "stop_policy": stop_policy,
                "revision": plan.revision + 1,
            }
        )

    @staticmethod
    def _plan_failure(state: WorkflowGraphState, message: str) -> WorkflowGraphState:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "PLAN_TRANSITION_VIOLATION",
            "last_error": message,
        }

    async def _apply_guardrail(
        self,
        state: WorkflowGraphState,
        request: WorkflowRunRequest,
        card_key: str,
        card: dict[str, Any],
    ) -> dict[str, Any]:
        for attempt in range(2):
            started = time.monotonic()
            await self._emit_step_start("coach.guardrail")
            result = self._guardrail.check(card_key, card)
            if result.passed:
                await self._emit_step_finish("coach.guardrail", "ok", started)
                return card
            status = "retry" if attempt == 0 else "fail"
            await self._emit_step_finish(
                "coach.guardrail",
                status,
                started,
                reasons=result.reasons,
            )
            if attempt == 0:
                request = request.model_copy(
                    update={
                        "profile_brief": _append_guardrail_note(
                            request.profile_brief, result.reasons
                        )
                    }
                )
                card = await self._dispatch_card(
                    state,
                    request,
                    images=state.get("_tutor_stage_private_image_data"),
                )
                continue
            raise CardGenerationError(
                "GUIDE_GUARDRAIL_FAILED",
                f"guide card failed guardrail: {', '.join(result.reasons)}",
            )
        raise CardGenerationError("GUIDE_GUARDRAIL_FAILED", "guide guardrail exhausted")

    async def _node_decide_actions(self, state: WorkflowGraphState) -> WorkflowGraphState:
        phase = state.get("new_phase", "READING")
        has_post_ac = bool(state.get("next_practice_direction")) or state.get("card_key") == "post_ac"
        actions = decide_actions(phase, has_post_ac=has_post_ac)
        decision = state.get("coach_decision")
        if decision is not None:
            actions = _rank_actions(actions, decision)
        return {**state, "available_actions": actions}

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
