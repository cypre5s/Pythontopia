"""Bounded multi-step tutoring episode planner for AI 导学助手.

GuideEpisodePlanner 把 GuideCoachAgent 的单步决策展开成一个有界（2-4 步）、可审计、
可被学生逐步推进的 episode 计划，并能在后续证据出现时修订它。

设计取舍：导学动作空间小且有界，确定性规则可**完整覆盖**所有情形（plan/revise 均为
全函数）。因此本规划器是**确定性**的——不引入开放式 LLM 规划（遵循 no-ReAct、fail fast、
简单优先）。需要时再在确定性兜底之上挂结构化 LLM 规划即可，但当前并无未覆盖分支。
"""

from __future__ import annotations

from uuid import uuid4

from pydantic import BaseModel

from pythontopia_graph.agents.guide_coach_agent import CoachContext
from pythontopia_graph.schemas.tutor_workflow import (
    GuideCoachDecision,
    TutorEpisodePlan,
    TutorEpisodeStep,
    TutorEpisodeStopPolicy,
    WorkflowEvent,
)

# 连续失败时优先的低负担首步动作。
_LOW_LOAD_FIRST: frozenset[str] = frozenset({"VISUALIZE", "PARSONS", "KNOWLEDGE_REVIEW"})

# 凑足 2 步、且步骤事件必须合法时的兜底续接顺序。
_CONTINUATION_PREF: tuple[str, ...] = (
    "CODING",
    "SKELETON",
    "KNOWLEDGE_REVIEW",
    "EXECUTION_TRACE",
    "VISUALIZE",
    "PARSONS",
    "IDEATING",
    "READING",
    "AC_REVIEW",
    "TRANSFER",
    "CHAT",
)

# 学生在某步达成后期望产生的证据类型（驱动 stop_policy 与提交/点击推进）。
_EXPECTED_EVIDENCE: dict[str, str] = {
    "CODING": "SUBMISSION_OBSERVED",
    "TRANSFER": "TRANSFER_ATTEMPT",
    "AC_REVIEW": "CHECKPOINT_COMPLETED",
}

_SUBMISSION_EVIDENCE = frozenset({"SUBMISSION_OBSERVED", "AC_SUBMISSION", "TRANSFER_ATTEMPT"})

# 每个事件对应的学生可见步骤文案（不含任何原始推理/系统提示痕迹）。
_STEP_META: dict[str, dict[str, str]] = {
    "READING": {
        "tool_name": "read_problem",
        "title": "先把题读透",
        "goal": "明确输入输出与关键约束。",
        "student_visible_reason": "先一起把题目要求看清楚。",
        "stop_condition": "你能复述题目要求和边界。",
    },
    "IDEATING": {
        "tool_name": "ideate_solution",
        "title": "理一下思路",
        "goal": "把解题步骤拆成可执行的小步。",
        "student_visible_reason": "我们先把思路理顺再写。",
        "stop_condition": "你能说出整体解题步骤。",
    },
    "SKELETON": {
        "tool_name": "skeleton_code",
        "title": "搭个骨架",
        "goal": "给出留 TODO 的代码骨架，核心逻辑你来补。",
        "student_visible_reason": "给你一个空壳骨架，关键逻辑留给你写。",
        "stop_condition": "你补完骨架里的 TODO。",
    },
    "CODING": {
        "tool_name": "coding_checkpoint",
        "title": "自己改一次并重交",
        "goal": "把前面看懂的点落到代码里并重新提交。",
        "student_visible_reason": "最后让你自己改一次，再提交看看。",
        "stop_condition": "你完成修改并重新提交一次。",
    },
    "ERROR_FEEDBACK": {
        "tool_name": "diagnose_submission",
        "title": "补一下错因",
        "goal": "定位第一处导致失败的原因。",
        "student_visible_reason": "先弄清楚这次错在哪。",
        "stop_condition": "你能指出第一处错误及修正方向。",
    },
    "AC_REVIEW": {
        "tool_name": "post_ac_review",
        "title": "复盘关键做法",
        "goal": "把通过本题的关键一步沉淀成可迁移经验。",
        "student_visible_reason": "先复盘这次为什么能过。",
        "stop_condition": "你说出本题最关键的一步并选一个迁移方向。",
    },
    "TRANSFER": {
        "tool_name": "transfer_practice",
        "title": "梳理迁移题思路",
        "goal": "读一道变式题，并说明准备怎样复用同一知识点。",
        "student_visible_reason": "用一道变式题巩固刚学到的点。",
        "stop_condition": "读完变式题后，说出准备如何尝试。",
    },
    "KNOWLEDGE_REVIEW": {
        "tool_name": "knowledge_review",
        "title": "补一下知识点",
        "goal": "回顾本题相关知识点并对照课件。",
        "student_visible_reason": "再补一下相关的知识点。",
        "stop_condition": "你能用自己的话讲清这个知识点。",
    },
    "EXECUTION_TRACE": {
        "tool_name": "execution_trace",
        "title": "看变量怎么跑偏",
        "goal": "逐步对照程序实际行为与题目期望，找出第一处偏差。",
        "student_visible_reason": "先一步步看你的代码到底怎么跑的。",
        "stop_condition": "你能说出哪个变量或分支偏离了预期。",
    },
    "VISUALIZE": {
        "tool_name": "visualize_flow",
        "title": "画一下",
        "goal": "用一张图看清结构或控制流。",
        "student_visible_reason": "先画张图把过程看直观一点。",
        "stop_condition": "你看懂这张图想表达的关系。",
    },
    "PARSONS": {
        "tool_name": "parsons_drill",
        "title": "拼装小练习",
        "goal": "用一道子技能拼装题练习关键顺序。",
        "student_visible_reason": "用一个小拼装题练一下关键顺序。",
        "stop_condition": "你把积木拼成正确顺序。",
    },
    "CHAT": {
        "tool_name": "tutor_chat",
        "title": "回答你的追问",
        "goal": "回应学生当下的疑问。",
        "student_visible_reason": "先回答你刚才的问题。",
        "stop_condition": "你确认理解了这个点。",
    },
}

_DEFAULT_META: dict[str, str] = {
    "tool_name": "guide_step",
    "title": "继续这一步",
    "goal": "完成当前导学动作。",
    "student_visible_reason": "我们一步步来。",
    "stop_condition": "你完成这一步的检查点。",
}


class EpisodeRevisionEvidence(BaseModel):
    """触发 episode 修订的证据（来自后续提交/反馈，由 graph 节点装配）。"""

    latest_verdict: str = ""
    consecutive_errors: int = 0
    accepted: bool | None = None
    passed_cases: int = 0
    total_cases: int = 0
    reason: str = ""


class GuideEpisodePlanner:
    """确定性地把单步教练决策展开为有界多步 episode 计划。"""

    def plan(self, ctx: CoachContext, decision: GuideCoachDecision) -> TutorEpisodePlan:
        events = self._event_sequence(ctx, decision)
        pairs = [(events[0], "ACTIVE"), *[(event, "PENDING") for event in events[1:]]]
        steps = self._build_steps(pairs)
        return self._assemble(
            ctx,
            decision,
            steps,
            revision=0,
            created_by="coach",
            revision_reason="",
        )

    def revise(
        self,
        ctx: CoachContext,
        previous: TutorEpisodePlan,
        decision: GuideCoachDecision,
        evidence: EpisodeRevisionEvidence,
    ) -> TutorEpisodePlan:
        events = self._event_sequence(ctx, decision)
        allowed = self._allowed_set(ctx)
        pairs: list[tuple[str, str]] = []

        prev_active = next((step for step in previous.steps if step.status == "ACTIVE"), None)
        if (
            prev_active is not None
            and prev_active.event in allowed
            and prev_active.event != events[0]
            and len(events) <= 3
        ):
            pairs.append((prev_active.event, "DONE" if evidence.accepted else "REVISED"))

        pairs.append((events[0], "ACTIVE"))
        for event in events[1:]:
            if len(pairs) >= 4:
                break
            pairs.append((event, "PENDING"))

        if len(pairs) < 2:
            used = {pair[0] for pair in pairs}
            for event in _CONTINUATION_PREF:
                if event in allowed and event not in used:
                    pairs.append((event, "PENDING"))
                    break

        steps = self._build_steps(pairs[:4])
        reason = evidence.reason.strip() or self._revision_reason(evidence)
        return self._assemble(
            ctx,
            decision,
            steps,
            revision=previous.revision + 1,
            created_by="coach",
            revision_reason=reason,
            plan_id=previous.plan_id,
        )

    # ── 内部 ──

    def _event_sequence(
        self, ctx: CoachContext, decision: GuideCoachDecision
    ) -> list[WorkflowEvent]:
        allowed = self._allowed_set(ctx)
        first = decision.next_event
        verdict = (ctx.diagnosis.verdict if ctx.diagnosis else ctx.state.latest_verdict).upper()
        consecutive = max(
            ctx.state.consecutive_errors,
            ctx.diagnosis.consecutive_errors if ctx.diagnosis else 0,
        )

        if first == "AC_REVIEW":
            tail = ["TRANSFER"]
        elif first in _LOW_LOAD_FIRST and consecutive >= 3:
            tail = ["KNOWLEDGE_REVIEW", "CODING"]
        elif first == "EXECUTION_TRACE":
            tail = ["KNOWLEDGE_REVIEW", "CODING"]
        elif first == "ERROR_FEEDBACK":
            tail = ["SKELETON", "CODING"] if verdict == "CE" else ["KNOWLEDGE_REVIEW", "CODING"]
        elif first == "KNOWLEDGE_REVIEW":
            tail = ["EXECUTION_TRACE", "CODING"]
        elif first == "READING":
            tail = ["IDEATING", "CODING"]
        elif first == "IDEATING":
            tail = ["SKELETON", "CODING"]
        elif first in {"VISUALIZE", "PARSONS"}:
            tail = ["KNOWLEDGE_REVIEW", "CODING"]
        else:
            tail = ["CODING"]

        seq: list[str] = [first]
        for event in tail:
            if len(seq) >= 4:
                break
            if event in allowed and event not in seq:
                seq.append(event)

        if len(seq) < 2:
            for event in _CONTINUATION_PREF:
                if event in allowed and event not in seq:
                    seq.append(event)
                    break
        if len(seq) < 2:
            # 退化：allowed 仅有一个不同事件时，补一步「再做一次并提交」。
            seq.append(first)

        return [token for token in seq[:4]]  # type: ignore[return-value]

    def _build_steps(self, pairs: list[tuple[str, str]]) -> list[TutorEpisodeStep]:
        steps: list[TutorEpisodeStep] = []
        for index, (event, status) in enumerate(pairs, start=1):
            meta = _STEP_META.get(event, _DEFAULT_META)
            depends_on = [f"s{index - 1}"] if index > 1 else []
            steps.append(
                TutorEpisodeStep(
                    step_id=f"s{index}",
                    event=event,  # type: ignore[arg-type]
                    tool_name=meta["tool_name"],
                    title=meta["title"],
                    goal=meta["goal"],
                    student_visible_reason=meta["student_visible_reason"],
                    stop_condition=meta["stop_condition"],
                    status=status,  # type: ignore[arg-type]
                    depends_on=depends_on,
                    expected_evidence=_EXPECTED_EVIDENCE.get(event, "CHECKPOINT_COMPLETED"),  # type: ignore[arg-type]
                )
            )
        return steps

    def _assemble(
        self,
        ctx: CoachContext,
        decision: GuideCoachDecision,
        steps: list[TutorEpisodeStep],
        *,
        revision: int,
        created_by: str,
        revision_reason: str,
        plan_id: str | None = None,
    ) -> TutorEpisodePlan:
        active = next((step for step in steps if step.status == "ACTIVE"), steps[0])
        verdict = (ctx.diagnosis.verdict if ctx.diagnosis else ctx.state.latest_verdict).upper()
        return TutorEpisodePlan(
            plan_id=plan_id or f"ep-{ctx.state.session_id}-{uuid4().hex}"[:80],
            session_id=ctx.state.session_id,
            problem_id=ctx.problem.problem_id,
            user_id=ctx.state.user_id,
            objective=self._objective(decision.next_event, verdict),
            trigger_event=ctx.requested_event,
            trigger_verdict=verdict,
            revision=revision,
            current_step_id=active.step_id,
            steps=steps,
            stop_policy=self._stop_policy(active),
            revision_reason=revision_reason,
            created_by=created_by,  # type: ignore[arg-type]
            evidence_summary=self._evidence_summary(ctx, verdict),
        )

    @staticmethod
    def _allowed_set(ctx: CoachContext) -> set[str]:
        allowed = {str(event).upper() for event in ctx.allowed_events}
        return allowed or {str(ctx.requested_event).upper()}

    @staticmethod
    def _stop_policy(active: TutorEpisodeStep) -> TutorEpisodeStopPolicy:
        if active.expected_evidence in _SUBMISSION_EVIDENCE:
            return "WAIT_FOR_SUBMISSION"
        return "WAIT_FOR_CLICK"

    @staticmethod
    def _objective(first_event: str, verdict: str) -> str:
        if first_event == "AC_REVIEW":
            return "复盘这道题的关键做法，并做一道迁移练习巩固。"
        if verdict == "CE":
            return "先排掉提交前的错误，再补齐结构并自己重交一次。"
        if first_event in {"KNOWLEDGE_REVIEW", "READING"}:
            return "先补一下相关知识点，再回到这道题练习。"
        if verdict in {"WA", "RE", "TLE"}:
            return "陪你定位并改对这道题，最后自己重交一次。"
        return "一步步陪你把这道题做出来。"

    @staticmethod
    def _evidence_summary(ctx: CoachContext, verdict: str) -> str:
        consecutive = max(
            ctx.state.consecutive_errors,
            ctx.diagnosis.consecutive_errors if ctx.diagnosis else 0,
        )
        parts = [f"verdict={verdict or 'NONE'}", f"consecutive_errors={consecutive}"]
        if ctx.diagnosis and ctx.diagnosis.total_cases:
            parts.append(f"cases={ctx.diagnosis.passed_cases}/{ctx.diagnosis.total_cases}")
        return ", ".join(parts)

    @staticmethod
    def _revision_reason(evidence: EpisodeRevisionEvidence) -> str:
        if evidence.accepted:
            return "已通过当前目标，进入下一步。"
        if evidence.consecutive_errors >= 3:
            return "连续多次失败，降低认知负担后再继续。"
        return "依据最新提交证据调整后续步骤。"
