"""Bounded manager agent for AI 导学助手.

GuideCoachAgent 只决定下一步教学动作；它不生成导学卡、不改学生代码、不绕过
tutor_workflow_graph 的阶段合法性校验。确定性规则处理高风险边界，LLM 只在白名单内做
教育策略选择。
"""

from __future__ import annotations

import json
import re
from typing import Any

from pydantic import BaseModel, Field, ValidationError

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse, apply_compiled_prompt
from pythontopia_graph.schemas.tutor_workflow import (
    GuideCoachDecision,
    TutorStudentState,
    WorkflowAction,
    WorkflowDiagnosisContext,
    WorkflowEvent,
    WorkflowProblemContext,
)

_JSON_FENCE = re.compile(r"^\s*```(?:json)?\s*(.+?)\s*```\s*$", re.DOTALL)

_TOOL_BY_EVENT: dict[str, str] = {
    "READING": "read_problem",
    "IDEATING": "ideate_solution",
    "SKELETON": "skeleton_code",
    "CODING": "coding_checkpoint",
    "ERROR_FEEDBACK": "diagnose_submission",
    "AC_REVIEW": "post_ac_review",
    "TRANSFER": "transfer_practice",
    "CHAT": "tutor_chat",
    "KNOWLEDGE_REVIEW": "knowledge_review",
    "EXECUTION_TRACE": "execution_trace",
    "VISUALIZE": "visualize_flow",
    "PARSONS": "parsons_drill",
}

_LABEL_BY_EVENT: dict[str, tuple[str, str]] = {
    "READING": ("problem_guide", "题目导读"),
    "IDEATING": ("ideate", "思路分析"),
    "SKELETON": ("skeleton", "骨架代码"),
    "CODING": ("coding", "继续编码"),
    "ERROR_FEEDBACK": ("error_feedback", "错因诊断"),
    "AC_REVIEW": ("post_ac", "AC 复盘"),
    "TRANSFER": ("transfer", "迁移练习"),
    "CHAT": ("chat", "追问"),
    "KNOWLEDGE_REVIEW": ("knowledge_review", "知识点复盘"),
    "EXECUTION_TRACE": ("execution_trace", "执行轨迹讲解"),
    "VISUALIZE": ("visualize", "画一下"),
    "PARSONS": ("parsons", "拼装挑战"),
}

_LOW_LOAD_EVENTS = ("VISUALIZE", "PARSONS", "KNOWLEDGE_REVIEW", "ERROR_FEEDBACK")
_WA_FIRST_EVENTS = ("EXECUTION_TRACE", "ERROR_FEEDBACK", "KNOWLEDGE_REVIEW")


class CoachContext(BaseModel):
    """GuideCoachAgent 的一次决策上下文。"""

    requested_event: WorkflowEvent
    allowed_events: list[WorkflowEvent] = Field(default_factory=list)
    state: TutorStudentState
    problem: WorkflowProblemContext
    diagnosis: WorkflowDiagnosisContext | None = None
    profile_brief: str = ""
    recent_cards: list[dict[str, Any]] = Field(default_factory=list)


class GuideCoachAgent:
    """在合法白名单内选择下一张导学卡的 manager agent。"""

    def __init__(self, provider: LlmProvider) -> None:
        self._provider = provider

    async def decide(self, ctx: CoachContext) -> GuideCoachDecision:
        allowed = self._allowed(ctx)
        rule_decision = self._rule_decision(ctx, allowed)
        if rule_decision is not None:
            return rule_decision

        llm_decision = await self._llm_decision(ctx, allowed)
        if llm_decision is not None and llm_decision.next_event in allowed:
            return self._normalize_decision(llm_decision, allowed)

        return self._fallback_decision(ctx, allowed)

    def _rule_decision(
        self, ctx: CoachContext, allowed: list[WorkflowEvent]
    ) -> GuideCoachDecision | None:
        verdict = (ctx.diagnosis.verdict if ctx.diagnosis else ctx.state.latest_verdict).upper()
        requested = ctx.requested_event
        consecutive_errors = max(
            ctx.state.consecutive_errors,
            ctx.diagnosis.consecutive_errors if ctx.diagnosis else 0,
        )

        if requested == "AC_REVIEW" and "AC_REVIEW" in allowed:
            return self._build_decision(
                "AC_REVIEW",
                allowed,
                ctx,
                hint_level=min(ctx.state.hint_level, 1),
                action_goal="把已经通过的关键做法沉淀成可迁移经验。",
                stop_condition="学生说出本题最关键的一步，并选择一个迁移方向。",
                confidence=0.92,
                reason_summary="AC 后优先做短复盘，再引导迁移练习。",
            )

        if verdict == "CE" and "ERROR_FEEDBACK" in allowed:
            return self._build_decision(
                "ERROR_FEEDBACK",
                allowed,
                ctx,
                hint_level=min(max(ctx.state.hint_level, 1), 2),
                diagnosis_label="compile_error",
                action_goal="先定位语法或运行前错误，避免学生继续猜逻辑。",
                stop_condition="学生能指出第一处语法/结构错误并完成最小修正。",
                confidence=0.9,
                reason_summary="CE 属于提交前错误，先做错因诊断最省认知成本。",
            )

        if verdict in {"WA", "RE", "TLE"} and consecutive_errors >= 3:
            event = self._first_allowed(allowed, _LOW_LOAD_EVENTS)
            if event is not None:
                return self._build_decision(
                    event,
                    allowed,
                    ctx,
                    hint_level=min(max(ctx.state.hint_level, 2), 3),
                    diagnosis_label=verdict.lower(),
                    student_state_label="repeated_error",
                    action_goal="降低认知负担，让学生先看见一个更小的结构或操作。",
                    stop_condition="学生完成一个小检查点后再回到代码。",
                    confidence=0.84,
                    reason_summary="连续失败次数较多，先降低负担，避免继续堆提示或直接给代码。",
                )

        if verdict == "WA":
            event = self._first_allowed(allowed, _WA_FIRST_EVENTS)
            if event is not None:
                return self._build_decision(
                    event,
                    allowed,
                    ctx,
                    hint_level=min(max(ctx.state.hint_level + 1, 1), 2),
                    diagnosis_label="wrong_answer",
                    action_goal="对照程序实际行为和题目期望，找出第一处偏差。",
                    stop_condition="学生说出一个变量或分支为何偏离预期。",
                    confidence=0.82,
                    reason_summary="WA 且仍在早期错误阶段，先看执行轨迹或第一层错因，不给完整代码。",
                )

        if requested in allowed:
            return self._build_decision(
                requested,
                allowed,
                ctx,
                hint_level=ctx.state.hint_level,
                action_goal="按学生主动请求提供下一张导学卡。",
                stop_condition="学生完成该卡片的检查点。",
                confidence=0.72,
                reason_summary="学生主动请求的动作在合法白名单内。",
            )

        return None

    async def _llm_decision(
        self, ctx: CoachContext, allowed: list[WorkflowEvent]
    ) -> GuideCoachDecision | None:
        if not allowed:
            return None
        try:
            # P2-dspy-loader（§3.5）：命中离线 GEPA 冻结产物则套用冻结 instruction+demos，
            # 缺/损坏产物时原样返回手写 prompt（零回归）。运行时绝不重编译。
            response = await self._provider.chat(
                apply_compiled_prompt(self._messages(ctx, allowed), "guide_coach_decision"),
                response_format={"type": "json_object"},
                temperature=0.1,
            )
        except Exception:  # noqa: BLE001 - manager 不因 provider 失败打断导学入口
            return None
        payload = _coerce_json(response)
        if not payload:
            return None
        try:
            decision = GuideCoachDecision.model_validate(payload)
        except ValidationError:
            return None
        return decision if decision.next_event in allowed else None

    def _fallback_decision(
        self, ctx: CoachContext, allowed: list[WorkflowEvent]
    ) -> GuideCoachDecision:
        event = ctx.requested_event if ctx.requested_event in allowed else allowed[0]
        return self._build_decision(
            event,
            allowed,
            ctx,
            hint_level=ctx.state.hint_level,
            action_goal="在合法动作中选择一个可继续推进的导学步骤。",
            stop_condition="学生完成该步骤的检查点。",
            confidence=0.5,
            reason_summary="LLM 决策不可用，使用白名单内的安全 fallback。",
        )

    def _normalize_decision(
        self, decision: GuideCoachDecision, allowed: list[WorkflowEvent]
    ) -> GuideCoachDecision:
        followups = decision.allowed_followups or self._followups(
            decision.next_event, allowed, decision.reason_summary
        )
        return decision.model_copy(
            update={
                "tool_name": decision.tool_name or _TOOL_BY_EVENT.get(decision.next_event, ""),
                "hint_level": min(max(decision.hint_level, 0), 3),
                "allowed_followups": followups,
            }
        )

    def _build_decision(
        self,
        event: WorkflowEvent,
        allowed: list[WorkflowEvent],
        ctx: CoachContext,
        *,
        hint_level: int,
        action_goal: str,
        stop_condition: str,
        confidence: float,
        reason_summary: str,
        diagnosis_label: str = "",
        student_state_label: str = "",
    ) -> GuideCoachDecision:
        return GuideCoachDecision(
            next_event=event,
            tool_name=_TOOL_BY_EVENT.get(event, ""),
            hint_level=hint_level,
            diagnosis_label=diagnosis_label,
            student_state_label=student_state_label or self._state_label(ctx),
            action_goal=action_goal,
            stop_condition=stop_condition,
            allowed_followups=self._followups(event, allowed, reason_summary),
            confidence=confidence,
            reason_summary=reason_summary,
        )

    @staticmethod
    def _allowed(ctx: CoachContext) -> list[WorkflowEvent]:
        seen: set[str] = set()
        allowed: list[WorkflowEvent] = []
        for event in ctx.allowed_events:
            token = str(event).upper()
            if token in seen:
                continue
            seen.add(token)
            allowed.append(event)
        if not allowed:
            allowed.append(ctx.requested_event)
        return allowed

    @staticmethod
    def _first_allowed(
        allowed: list[WorkflowEvent], candidates: tuple[str, ...]
    ) -> WorkflowEvent | None:
        for candidate in candidates:
            if candidate in allowed:
                return candidate  # type: ignore[return-value]
        return None

    @staticmethod
    def _followups(
        event: WorkflowEvent, allowed: list[WorkflowEvent], reason: str
    ) -> list[WorkflowAction]:
        ordered = [item for item in allowed if item != event]
        if event == "AC_REVIEW" and "TRANSFER" in allowed:
            ordered = ["TRANSFER", *[item for item in ordered if item != "TRANSFER"]]  # type: ignore[list-item]
        followups: list[WorkflowAction] = []
        for candidate in ordered[:3]:
            key, label = _LABEL_BY_EVENT.get(candidate, (str(candidate).lower(), str(candidate)))
            followups.append(
                WorkflowAction(
                    key=key,
                    label=label,
                    event=candidate,
                    recommended=not followups,
                    reason=reason if not followups else "",
                )
            )
        return followups

    @staticmethod
    def _state_label(ctx: CoachContext) -> str:
        if ctx.state.consecutive_errors >= 3:
            return "needs_lower_cognitive_load"
        if ctx.state.last_action_effective is False:
            return "previous_action_not_effective"
        if ctx.state.hint_level >= 2:
            return "advanced_hint"
        return "normal"

    @staticmethod
    def _messages(ctx: CoachContext, allowed: list[WorkflowEvent]) -> list[dict[str, str]]:
        diagnosis = ctx.diagnosis.model_dump(mode="json") if ctx.diagnosis else None
        payload = {
            "requested_event": ctx.requested_event,
            "allowed_events": allowed,
            "student_state": ctx.state.model_dump(mode="json"),
            "problem": ctx.problem.model_dump(mode="json"),
            "diagnosis": diagnosis,
            "profile_brief": ctx.profile_brief,
            "recent_cards": ctx.recent_cards[-3:],
        }
        return [
            {
                "role": "system",
                "content": (
                    "你是 Pythontopia 的 GuideCoachAgent，只在 allowed_events 中选择下一步教育动作。"
                    "输出 JSON，字段必须符合 GuideCoachDecision。不要输出完整可提交代码、隐藏测试、"
                    "chain-of-thought 或逐步隐性推理；reason_summary 只写一句可审计摘要。"
                ),
            },
            {
                "role": "user",
                "content": json.dumps(payload, ensure_ascii=False),
            },
        ]


def _coerce_json(response: LlmResponse) -> dict[str, Any] | None:
    if response.content_json is not None:
        return response.content_json
    if response.content_text:
        try:
            parsed = json.loads(_strip_json_fence(response.content_text))
        except json.JSONDecodeError:
            return None
        if isinstance(parsed, dict):
            return parsed
    return None


def _strip_json_fence(text: str) -> str:
    match = _JSON_FENCE.match(text)
    return match.group(1) if match else text
