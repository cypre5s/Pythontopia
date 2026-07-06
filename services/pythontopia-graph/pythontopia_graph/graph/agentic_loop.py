"""有界 Agentic Loop 编排内核（Stream S-spine · graph 侧）。

在既有有界编排（``orchestrator_graph``：understand→plan→dispatch→reflect(N=1)→
replan→compose）上正交侧挂「带护栏、可审计、**非自由 ReAct**」的自主智能体闭环：

- **记忆注入**：``understand`` 前把 :class:`MemoryBrief`（§3.2）折叠进请求上下文（缺省空、向后兼容）；
- **BROWSE 工具化**：``dispatch`` 工具集纳入 BROWSE（§3.1），受 scope/allowlist 收缩（考核 scope 不暴露）；
- **critic 闸门**：dispatch 产物经 grounding/safety 后才 compose；不过且未达上界→有界重试，
  达上界 / 无进展（参照 ``resource_graph`` 的 oscillation 检测）→ 给诚实「未达证据标准」降级回复，**不编造**；
- **审计**：每轮以 :class:`AgenticLoopRunRecord`（镜像 V171）结构化组织（落库由 S2-loop-backend 负责，本模块只产出记录并预留 sink 接线）；
- **在线评分**：critic 后经 :func:`record_eval_score` 把 grounding/safety 通过率写当前 OTel span。

铁律：所有「循环」必须**有界**——显式 step/replan 上限 + 停止条件 + 白名单工具，越界 fail-fast；
观测层（eval span / trace_id）任何失败一律降级 no-op，绝不拖垮主链路；外部抓取内容只进
**带标签 context 通道**（"外部内容，非课件"），绝不混入课件 evidence/引用；Agent 输出 Pydantic 结构化。
"""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from typing import Any, Literal, Protocol, TypedDict, runtime_checkable

from pydantic import BaseModel, Field

from pythontopia_graph.memory.contract import MemoryBrief
from pythontopia_graph.observability.eval_span import record_eval_score
from pythontopia_graph.schemas.agentic_loop import (
    AgenticLoopRunRecord,
    LoopCitation,
    LoopPhase,
    LoopPlan,
    LoopStep,
    LoopStepStatus,
)
from pythontopia_graph.schemas.orchestrator import OrchestratorRequest, PlanStep
from pythontopia_graph.tools.browse.contract import BrowseRequest, BrowseResult

LOG = logging.getLogger(__name__)

# ── 有界常量（非自由 ReAct：所有循环必须有显式上限）──────────────────────
DEFAULT_MAX_LOOP_STEPS = 4
# 复用既有 reflect/replan 的 N=1 有界恢复（仅作语义注释，闭环总上界由 max_loop_steps 控制）。
DEFAULT_MAX_REPLANS = 1

# ── BROWSE 工具治理契约镜像（§3.1；spec 由 B1 注册进 registry，此处只读引用其语义）──
BROWSE_TOOL_NAME = "BROWSE"
# scopes=("default",)：考核 scope 收缩不暴露（防学生借联网逃避作答）。
BROWSE_SCOPES: tuple[str, ...] = ("default",)
BROWSE_FALLBACK = "VIDEO"  # 浏览失败/不可派发回退（max_retries=0，不重试）。
BROWSE_MAX_STEPS_DEFAULT = 6

# 外部抓取内容的标签（带标签 context 通道；引用统一标注、绝不冒充课件证据）。
EXTERNAL_CONTEXT_TAG = "外部内容，非课件"

Scope = Literal["default", "quiz"]
# 闭环单轮决策：通过则 compose；未过且可继续则有界重试；达上界/无进展则诚实降级。
LoopDecision = Literal["compose", "retry", "downgrade"]

# 看/学/练/测（OrchestratorRequest plan phase）→ 闭环可见阶段（§3.8 LoopPhase）。
# 「测/assess」是掌握度验证阶段，映射为 critique；其余看/学/练均为工具派发的 act。
_PLAN_PHASE_TO_LOOP: dict[str, LoopPhase] = {
    "watch": "act",
    "learn": "act",
    "practice": "act",
    "assess": "critique",
}
# PlanStep.status → LoopStep.status（skipped 视作 done：该步已闭合）。
_PLAN_STATUS_TO_LOOP: dict[str, LoopStepStatus] = {
    "pending": "pending",
    "active": "active",
    "done": "done",
    "skipped": "done",
    "failed": "failed",
}


# ── 依赖注入协议（Phase 2 由兄弟单元落地真实实现）─────────────────────────


@runtime_checkable
class MemoryBriefProvider(Protocol):
    """记忆 brief 提供者（M2 ``brief_client`` 落地；HTTP 取 ``/api/learner/memory/brief``）。"""

    async def fetch(self, *, user_id: int, query: str) -> MemoryBrief: ...


@runtime_checkable
class BrowseExecutor(Protocol):
    """白名单内有界浏览执行器（B1 ``tools/browse/executor`` 落地，封装 browser-use）。"""

    async def run(self, request: BrowseRequest) -> BrowseResult: ...


class NullMemoryBriefProvider:
    """缺省记忆提供者：恒返回空 brief（向后兼容；Phase 2 由 M2 brief_client 替换）。"""

    async def fetch(self, *, user_id: int, query: str) -> MemoryBrief:
        return MemoryBrief()


class StaticMemoryBriefProvider:
    """测试用确定性记忆提供者。"""

    def __init__(self, brief: MemoryBrief) -> None:
        self._brief = brief

    async def fetch(self, *, user_id: int, query: str) -> MemoryBrief:
        return self._brief


class NullBrowseExecutor:
    """缺省占位 executor（不真正联网；Phase 2 由 B1 真 executor 替换）。

    仅作类型占位与「未接线」标记——闭环按 ``executor is None`` 判定可用性，故缺省
    场景应直接注入 ``None``；本类用于需要显式占位对象时的语义标注。
    """

    async def run(self, request: BrowseRequest) -> BrowseResult:  # pragma: no cover - 占位
        return BrowseResult(error="browse_executor_not_wired")


# ── 结构化输出（Pydantic）─────────────────────────────────────────────────


class CriticVerdict(BaseModel):
    """critic 闸门裁决：grounding（引用接地）与 safety（安全）双轨。"""

    grounding_pass: bool = True
    safety_pass: bool = True
    reasons: list[str] = Field(default_factory=list)

    @property
    def passed(self) -> bool:
        return self.grounding_pass and self.safety_pass


class LoopOutcome(BaseModel):
    """有界闭环终态：决策 + 终轮序号 + 终轮裁决 + 是否因无进展提前终止。"""

    decision: LoopDecision
    loop_step: int = Field(ge=0)
    verdict: CriticVerdict
    oscillation: bool = False


class BrowseDispatch(BaseModel):
    """一次 BROWSE 派发的结构化结果（供编排聚合 + 前端时间线渲染）。"""

    dispatched: bool
    reason: str = ""  # 未派发原因（scope/allowlist/executor）
    fallback: str = ""  # 不可派发 / 失败时的回退工具（VIDEO）
    result: BrowseResult | None = None
    citations: list[LoopCitation] = Field(default_factory=list)  # 外部、带标签
    step: LoopStep
    error: str = ""


# ── 闭环共享状态（AgenticLoopState；orchestrator_graph 的 state 继承之）──────


class AgenticLoopState(TypedDict, total=False):
    """有界闭环编排的共享状态字段（深度要求 §4.1）。

    并入 ``OrchestratorState``：``loop_step``/``max_loop_steps`` 控有界上限，
    ``tool_trail`` 记命中工具，``critic_verdicts`` 记每轮裁决，``replan_count``
    复用既有 reflect/replan 有界计数，``memory_brief`` 为注入的记忆上下文快照，
    ``eval_scores`` 为在线评分缓存。
    """

    loop_step: int
    max_loop_steps: int
    tool_trail: list[str]
    critic_verdicts: list[CriticVerdict]
    replan_count: int
    memory_brief: dict[str, Any]
    eval_scores: dict[str, float]


# ── 控制器：critic 闸门 + 有界闭环 + BROWSE 闸门 + 审计 ────────────────────


class AgenticLoopController:
    """有界 Agentic Loop 控制器（纯逻辑 + 注入式 IO；无进程内可变全局）。"""

    def __init__(
        self,
        *,
        max_loop_steps: int = DEFAULT_MAX_LOOP_STEPS,
        max_replans: int = DEFAULT_MAX_REPLANS,
        scope: Scope = "default",
    ) -> None:
        # 业务 fail-fast：闭环必须有界，上限非法直接拒绝（杜绝退化为无界 ReAct）。
        if max_loop_steps < 1:
            raise ValueError("max_loop_steps 必须 >= 1（有界闭环不可无界）")
        if max_replans < 0:
            raise ValueError("max_replans 不可为负")
        self.max_loop_steps = max_loop_steps
        self.max_replans = max_replans
        self.scope: Scope = scope

    # ── critic 闸门 / 有界闭环决策 ──

    def decide(
        self,
        *,
        loop_step: int,
        verdict: CriticVerdict,
        previous: CriticVerdict | None = None,
    ) -> LoopDecision:
        """单轮停止条件（非自由 ReAct）：

        - critic 通过 → ``compose``；
        - 未过但达上界（``loop_step + 1 >= max_loop_steps``）→ ``downgrade``（诚实降级，不编造）；
        - 未过且与上一轮失败原因等价（无进展 / oscillation）→ ``downgrade``（提前终止，省调用）；
        - 否则 → ``retry``（有界重试）。
        """
        if verdict.passed:
            return "compose"
        if loop_step + 1 >= self.max_loop_steps:
            return "downgrade"
        if previous is not None and self._reasons_equivalent(previous.reasons, verdict.reasons):
            return "downgrade"
        return "retry"

    @staticmethod
    def _reasons_equivalent(previous: list[str] | None, current: list[str] | None) -> bool:
        """两轮失败原因是否等价（归一去空白后比较集合，与顺序无关）——参照 ``resource_graph``。"""
        if previous is None or current is None:
            return False
        prev_set = {"".join(reason.split()) for reason in previous}
        curr_set = {"".join(reason.split()) for reason in current}
        return bool(prev_set) and prev_set == curr_set

    async def run(
        self, run_round: Callable[[int], Awaitable[CriticVerdict]]
    ) -> LoopOutcome:
        """驱动有界闭环：反复跑 ``run_round(step)`` 直至 compose / downgrade / 触上界。

        ``run_round`` 表示「一轮 dispatch + critic」，返回该轮裁决。严格有界——最多
        ``max_loop_steps`` 轮；任一轮通过即 compose；失败且达上界或无进展即诚实降级。
        """
        previous: CriticVerdict | None = None
        verdict: CriticVerdict | None = None
        step = 0
        while step < self.max_loop_steps:
            verdict = await run_round(step)
            decision = self.decide(loop_step=step, verdict=verdict, previous=previous)
            if decision == "compose":
                return LoopOutcome(decision="compose", loop_step=step, verdict=verdict)
            if decision == "downgrade":
                at_bound = step + 1 >= self.max_loop_steps
                return LoopOutcome(
                    decision="downgrade",
                    loop_step=step,
                    verdict=verdict,
                    oscillation=not at_bound,
                )
            previous = verdict
            step += 1
        # 兜底（理论不可达：decide 在上界必返回 downgrade）。
        assert verdict is not None  # noqa: S101 - 不变量自检
        return LoopOutcome(
            decision="downgrade", loop_step=self.max_loop_steps - 1, verdict=verdict
        )

    # ── 诚实降级回复（达上界 / critic 拒绝；不编造、不 surface 越权内容）──

    def downgrade_reply(self, *, goal: str, reasons: list[str]) -> str:
        """达上界或被 critic 拒绝时的诚实降级回复：明示未达证据标准，给可执行下一步。

        绝不臆造课件页码/章节/答案，绝不复述被拒绝的越权内容；仅给安全的引导。
        """
        goal_text = (goal or "这个问题").strip() or "这个问题"
        return (
            f"关于「{goal_text}」，我暂时**未达到课件证据标准**，为避免编造，"
            "我先不给可能不准确的结论（诚实优先于看起来完整）。\n\n"
            "你可以这样继续：\n"
            "1. 把问题问得更具体一点（点明想确认的知识点）；\n"
            "2. 去图书馆查阅《Python语言基础》（packId=43）对应章节核对；\n"
            "3. 让我把它拆成更小的子问题，逐步陪你确认。\n\n"
            "（本轮已达有界重试上限，按诚实降级处理，不臆造课件内容。）"
        )

    # ── 在线评分（critic 后写当前 span；fail-soft）──

    def record_eval(self, verdict: CriticVerdict) -> None:
        """把 grounding/safety 通过率（0/1）写当前 OTel span（§3.6）。观测 fail-soft。"""
        try:
            record_eval_score("grounding_pass", 1.0 if verdict.grounding_pass else 0.0)
            record_eval_score("safety_pass", 1.0 if verdict.safety_pass else 0.0)
        except Exception:  # noqa: BLE001 - 观测层失败绝不拖垮主链路
            LOG.debug("record_eval 失败（观测 fail-soft，已降级）", exc_info=True)

    # ── 审计记录（镜像 V171；落库由 S2 负责，此处只产出结构化记录）──

    def build_run_record(
        self,
        *,
        user_id: int,
        session_id: str,
        turn: int,
        verdict: CriticVerdict,
        steps_used: int,
        replan_count: int,
        tools_used: list[str],
        input_tokens: int = 0,
        output_tokens: int = 0,
        trace_id: str = "",
    ) -> AgenticLoopRunRecord:
        return AgenticLoopRunRecord(
            user_id=user_id,
            session_id=session_id,
            turn=turn,
            max_steps=self.max_loop_steps,
            steps_used=max(0, steps_used),
            replan_count=max(0, replan_count),
            tools_used=list(tools_used),
            grounding_pass=verdict.grounding_pass,
            safety_pass=verdict.safety_pass,
            input_tokens=max(0, input_tokens),
            output_tokens=max(0, output_tokens),
            trace_id=trace_id,
        )

    # ── 可见有界计划（§3.8；S 产出事件 / F 渲染）──

    def build_loop_plan(self, plan_steps: list[PlanStep]) -> LoopPlan:
        """把看/学/练/测计划转为闭环可见计划（带阶段 + checkbox 状态 + 有界标记）。"""
        steps = [
            LoopStep(
                id=ps.id,
                phase=phase_for_plan_step(ps),
                label=ps.title,
                tool_name=ps.resource_type or None,
                status=_PLAN_STATUS_TO_LOOP.get(ps.status, "pending"),
            )
            for ps in plan_steps
        ]
        return LoopPlan(steps=steps, bounded=True, max_steps=self.max_loop_steps)

    # ── BROWSE 闸门（scope / allowlist / executor）──

    def browse_dispatchable(
        self,
        *,
        allowed_domains: list[str],
        executor_available: bool,
        scope: Scope | None = None,
    ) -> tuple[bool, str]:
        """BROWSE 是否可派发：考核 scope 收缩 → 拒；空 allowlist → 拒；未接线 executor → 拒。"""
        effective_scope = scope or self.scope
        if effective_scope not in BROWSE_SCOPES:
            return (False, f"scope_restricted:{effective_scope}")
        if not allowed_domains:
            return (False, "no_allowlist")
        if not executor_available:
            return (False, "executor_unavailable")
        return (True, "")

    def dispatchable_tool_names(
        self,
        base_tools: list[str],
        *,
        allowed_domains: list[str] | None = None,
        browse_executor_available: bool = False,
        scope: Scope | None = None,
    ) -> list[str]:
        """按 scope/allowlist 收缩工具集：BROWSE 不可派发时从工具集剔除（保持其余顺序）。"""
        ok, _ = self.browse_dispatchable(
            allowed_domains=allowed_domains or [],
            executor_available=browse_executor_available,
            scope=scope,
        )
        return [tool for tool in base_tools if tool != BROWSE_TOOL_NAME or ok]

    async def run_browse(
        self,
        executor: BrowseExecutor | None,
        *,
        goal: str,
        user_id: int,
        allowed_domains: list[str],
        scope: Scope | None = None,
        max_steps: int = BROWSE_MAX_STEPS_DEFAULT,
    ) -> BrowseDispatch:
        """有界 BROWSE 派发：先过闸门，再调 executor（不重试），外部引用统一带标签。

        - 闸门未过 → ``dispatched=False`` + 回退 VIDEO（绝不发起请求）；
        - executor 异常 → fail-soft，捕获 error + 回退 VIDEO（已走步骤由 executor 自带）；
        - 成功 → 外部 citations 标注「外部内容，非课件」，``truncated`` 透传。
        """
        effective_scope = scope or self.scope
        ok, reason = self.browse_dispatchable(
            allowed_domains=allowed_domains,
            executor_available=executor is not None,
            scope=effective_scope,
        )
        if not ok or executor is None:
            reason = reason or "executor_unavailable"
            return BrowseDispatch(
                dispatched=False,
                reason=reason,
                fallback=BROWSE_FALLBACK,
                step=LoopStep(
                    id="loop-browse",
                    phase="act",
                    label="联网查证",
                    tool_name=BROWSE_TOOL_NAME,
                    status="failed",
                    reason_summary=f"BROWSE 不可派发（{reason}），回退 {BROWSE_FALLBACK}",
                    error=reason,
                ),
            )

        request = BrowseRequest(
            goal=goal,
            allowed_domains=list(allowed_domains),
            max_steps=max_steps,
            user_id=user_id,
        )
        try:
            result = await executor.run(request)  # max_retries=0：不重试
        except Exception as exc:  # noqa: BLE001 - 浏览异常 fail-soft 回退，不外抛打断编排
            LOG.warning("BROWSE executor 失败（fail-soft 回退 %s）：%s", BROWSE_FALLBACK, exc)
            return BrowseDispatch(
                dispatched=True,
                fallback=BROWSE_FALLBACK,
                error=str(exc),
                step=LoopStep(
                    id="loop-browse",
                    phase="act",
                    label="联网查证",
                    tool_name=BROWSE_TOOL_NAME,
                    status="failed",
                    reason_summary=f"BROWSE 执行失败，回退 {BROWSE_FALLBACK}",
                    error=str(exc),
                ),
            )

        citations = [
            LoopCitation(kind="external", ref=url, label=f"{EXTERNAL_CONTEXT_TAG}：{url}")
            for url in result.citations
        ]
        status: LoopStepStatus = "failed" if result.error else "done"
        step = LoopStep(
            id="loop-browse",
            phase="act",
            label="联网查证",
            tool_name=BROWSE_TOOL_NAME,
            status=status,
            citations=citations,
            reason_summary=(result.final_summary or "已联网查证")[:200],
            error=result.error,
        )
        return BrowseDispatch(
            dispatched=True,
            fallback=BROWSE_FALLBACK if result.error else "",
            result=result,
            citations=citations,
            step=step,
            error=result.error,
        )


# ── 模块级辅助（记忆折叠 / 审计统计 / 阶段映射 / trace_id）──────────────────


def phase_for_plan_step(step: PlanStep) -> LoopPhase:
    """看/学/练/测 PlanStep → 闭环可见阶段（默认 act）。"""
    return _PLAN_PHASE_TO_LOOP.get(step.phase, "act")


# ── §3.8 SSE 事件载荷（camelCase；桥接 snake_case schema → 前端 loopTypes.ts 冻结契约）──
# loopSlice 期望 CUSTOM 事件 value 为 camelCase（toolName/durationMs/reasonSummary/maxSteps）；
# schemas.agentic_loop 为 snake_case，故在发射点做一次性映射，不改 schema 默认 model_dump。


def loop_step_event(step: LoopStep) -> dict[str, Any]:
    """LoopStep → 前端 loopSlice.coerceStep 可消费的 camelCase 载荷（缺省字段省略）。"""
    payload: dict[str, Any] = {
        "id": step.id,
        "phase": step.phase,
        "label": step.label,
        "status": step.status,
    }
    if step.tool_name:
        payload["toolName"] = step.tool_name
    if step.duration_ms:
        payload["durationMs"] = step.duration_ms
    if step.reason_summary:
        payload["reasonSummary"] = step.reason_summary
    if step.error:
        payload["error"] = step.error
    if step.citations:
        payload["citations"] = [
            {"kind": c.kind, "ref": c.ref, "label": c.label} for c in step.citations
        ]
    return payload


def loop_plan_event(plan: LoopPlan) -> dict[str, Any]:
    """LoopPlan → 前端 loopSlice.coercePlan 可消费的 camelCase 载荷（含 maxSteps）。"""
    return {
        "steps": [loop_step_event(step) for step in plan.steps],
        "bounded": plan.bounded,
        "maxSteps": plan.max_steps,
    }


def memory_brief_note(brief: MemoryBrief) -> str:
    """把记忆 brief 压成一句带标签上下文（含 ``as_of`` 体现时序）；空 brief → 空串。"""
    if not (brief.facts or brief.weak_kcs or brief.style_notes):
        return ""
    parts: list[str] = []
    if brief.facts:
        parts.append("稳定事实：" + "；".join(brief.facts))
    if brief.weak_kcs:
        parts.append("近期薄弱KC：" + "、".join(brief.weak_kcs))
    if brief.style_notes:
        parts.append("学习风格：" + "；".join(brief.style_notes))
    header = "【记忆回放"
    if brief.as_of:
        header += f"·截至{brief.as_of}"
    header += "】"
    return header + " ".join(parts)


def fold_memory_brief(
    req: OrchestratorRequest, brief: MemoryBrief
) -> OrchestratorRequest:
    """把记忆 brief 折叠进请求上下文（understand/dispatch 可见）；空 brief 原样返回。

    - ``profile_brief`` 前置一句带标签记忆回放（含 ``as_of`` 时序），裁剪到 2000 上限；
    - ``weak_kcs``（反复受阻 KC）并入 ``decaying_kcs``（去重）以驱动计划优先锚定。

    向后兼容：``OrchestratorRequest`` 无 ``context`` 字段（非 S1 可写域），故经既有
    ``profile_brief`` / ``decaying_kcs`` 字段无侵入注入（Phase 2 接线见模块说明）。
    """
    note = memory_brief_note(brief)
    update: dict[str, Any] = {}
    if note:
        existing = (req.profile_brief or "").strip()
        merged = f"{note}\n{existing}" if existing else note
        update["profile_brief"] = merged[:2000]
    if brief.weak_kcs:
        merged_decay = list(dict.fromkeys([*req.decaying_kcs, *brief.weak_kcs]))
        update["decaying_kcs"] = merged_decay
    if not update:
        return req
    return req.model_copy(update=update)


def tool_trail_from_results(results: list[Any]) -> list[str]:
    """从派发结果提取命中工具/工位轨迹（去重、保序）。"""
    seen: set[str] = set()
    trail: list[str] = []
    for result in results:
        agent = getattr(result, "agent", None)
        if agent and agent not in seen:
            seen.add(agent)
            trail.append(agent)
    return trail


def count_bounded_recoveries(trace_steps: list[Any]) -> int:
    """统计有界恢复次数：reflect 重派（status=retry）+ recent_failures 触发的 replan。

    negotiate 触发的 replan 是计划改写而非恢复，不计入。
    """
    count = 0
    for step in trace_steps:
        node = getattr(step, "node", "") or ""
        status = getattr(step, "status", "") or ""
        note = getattr(step, "note", "") or ""
        if node == "reflect" and status == "retry":
            count += 1
        elif node == "replan" and note.startswith("recent_failures"):
            count += 1
    return count


def current_trace_id() -> str:
    """读取当前 OTel span 的 trace id（32 位 hex）；无 otel/无活动 span/异常 → 空串。"""
    try:
        from opentelemetry import trace  # 局部导入：缺包即降级，绝不拖垮主链路

        span = trace.get_current_span()
        ctx = span.get_span_context()
        if ctx is not None and getattr(ctx, "trace_id", 0):
            return format(ctx.trace_id, "032x")
    except Exception:  # noqa: BLE001 - 观测 fail-soft
        return ""
    return ""
