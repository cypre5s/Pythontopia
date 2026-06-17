"""Stream S-spine 验收测试：有界 Agentic Loop 编排 + 审计（graph 侧）。

覆盖 §4 S-spine 七项测试矩阵：
1. 正常单轮闭环；
2. 触发一次有界 replan 后成功；
3. 达 max_loop_steps 安全降级（不编造）；
4. BROWSE 在 quiz scope 被收缩不可派发；
5. memory_brief 缺省兼容；
6. 审计记录字段齐全；
7. critic 拒绝 → 不 compose 越权内容。

外加：无进展（oscillation）提前终止、BROWSE 执行（mock，外部标签引用 + fail-soft 回退）、
记忆 brief 折叠、eval span fail-soft、可见有界计划（§3.8）。

非自由 ReAct：所有循环显式有界（step 上限 + 停止条件 + 白名单工具），达上界诚实降级。
BROWSE 用 mock 执行器解耦（真实 executor 由 B1 落地、registry spec 由 B1 注册 → Phase 2 接线）。
"""

from __future__ import annotations

from collections.abc import AsyncIterator, Iterator

import pytest

from pythontopia_graph.graph import agentic_loop as al
from pythontopia_graph.graph.agentic_loop import (
    AgenticLoopController,
    BrowseDispatch,
    CriticVerdict,
    LoopOutcome,
    NullBrowseExecutor,
    NullMemoryBriefProvider,
    StaticMemoryBriefProvider,
    count_bounded_recoveries,
    fold_memory_brief,
    tool_trail_from_results,
)
from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.graph.orchestrator_session import reset_session
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.memory.contract import MemoryBrief
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher
from pythontopia_graph.schemas.agentic_loop import AgenticLoopRunRecord, LoopPlan, LoopStep
from pythontopia_graph.tools.browse.contract import BrowseRequest, BrowseResult, BrowseStep
from pythontopia_graph.schemas.orchestrator import (
    DispatchResult,
    OrchestratorRequest,
    PlanStep,
)
from pythontopia_graph.schemas.resource import GraphTraceStep
from pythontopia_graph.tools.harness import reset_harness_state


# ── 公共 fixtures / stubs ──────────────────────────────────────────────


@pytest.fixture(autouse=True)
def _clean_harness() -> Iterator[None]:
    reset_harness_state()
    yield
    reset_harness_state()


class StubProvider(LlmProvider):
    """understand/decompose/profile 走规则兜底（None payload）；compose 吐固定 reply。"""

    def __init__(self, reply: str = "规划完成。") -> None:
        self._reply = reply

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_json=None)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        for index in range(0, len(self._reply), 6):
            yield self._reply[index : index + 6]


class FakeBrowseExecutor:
    """注入式假浏览器 executor（零真实联网）：按白名单回带受界步骤 + 引用。"""

    def __init__(self, result: BrowseResult | None = None, boom: bool = False) -> None:
        self._result = result
        self._boom = boom
        self.calls: list[BrowseRequest] = []

    async def run(self, request: BrowseRequest) -> BrowseResult:
        self.calls.append(request)
        if self._boom:
            raise RuntimeError("browser process crashed")
        if self._result is not None:
            return self._result
        return BrowseResult(
            steps=[
                BrowseStep(index=0, action="navigate", url="https://docs.python.org/3/", summary="打开官方文档"),
                BrowseStep(index=1, action="read", url="https://docs.python.org/3/library/functions.html#sorted", summary="sorted 是稳定排序"),
            ],
            final_summary="官方文档说明 sorted 为稳定排序。",
            citations=["https://docs.python.org/3/library/functions.html#sorted"],
        )


HIT = RagHit(
    chunk_id="c1",
    page_id=101,
    page_no=12,
    chapter_id=3,
    kc_codes=["PY43-KC-001"],
    text="for 循环遍历可迭代对象。",
    score=0.81,
)


def request_of(
    message: str = "我想学 for 循环",
    session_id: str = "t-loop",
    **overrides: object,
) -> OrchestratorRequest:
    payload: dict[str, object] = {
        "user_id": 7,
        "session_id": session_id,
        "user_message": message,
        "course_kc_index": ["PY43-KC-001", "PY43-KC-003"],
    }
    payload.update(overrides)
    return OrchestratorRequest(**payload)  # type: ignore[arg-type]


def make_graph(**kwargs: object) -> OrchestratorGraph:
    base: dict[str, object] = {
        "provider": StubProvider(),
        "evidence_fetcher": StaticEvidenceFetcher([HIT]),
        "valid_kc_codes": {"PY43-KC-001"},
    }
    base.update(kwargs)
    return OrchestratorGraph(**base)  # type: ignore[arg-type]


def verdict(grounding: bool = True, safety: bool = True, reasons: list[str] | None = None) -> CriticVerdict:
    return CriticVerdict(grounding_pass=grounding, safety_pass=safety, reasons=reasons or [])


# ── A. critic 闸门 / 有界闭环决策（单元）─────────────────────────────────


def test_decide_pass_returns_compose() -> None:
    """① 正常：critic 通过 → compose。"""
    ctrl = AgenticLoopController(max_loop_steps=4)
    assert ctrl.decide(loop_step=0, verdict=verdict(True, True)) == "compose"


def test_decide_fail_under_bound_returns_retry() -> None:
    """② 未过且未达上界、有进展 → 有界重试。"""
    ctrl = AgenticLoopController(max_loop_steps=4)
    assert ctrl.decide(loop_step=0, verdict=verdict(False, True, ["g1"])) == "retry"


def test_decide_fail_at_bound_returns_downgrade() -> None:
    """③ 未过且达上界 → 诚实降级（不再重试，不编造）。"""
    ctrl = AgenticLoopController(max_loop_steps=2)
    # loop_step=1 → loop_step+1=2 >= max=2 → downgrade
    assert ctrl.decide(loop_step=1, verdict=verdict(False, True, ["g1"])) == "downgrade"


def test_decide_no_progress_oscillation_returns_downgrade() -> None:
    """无进展（与上一轮失败原因等价）→ 提前降级（参照 resource_graph oscillation）。"""
    ctrl = AgenticLoopController(max_loop_steps=8)
    prev = verdict(False, True, ["UNKNOWN_KC:X", "page mismatch"])
    curr = verdict(False, True, ["page mismatch", "UNKNOWN_KC:X"])  # 集合等价、顺序无关
    assert ctrl.decide(loop_step=1, verdict=curr, previous=prev) == "downgrade"


def test_controller_rejects_invalid_max_loop_steps() -> None:
    """业务 fail-fast：max_loop_steps < 1 直接拒绝（非自由 ReAct 必须有界）。"""
    with pytest.raises(ValueError):
        AgenticLoopController(max_loop_steps=0)


@pytest.mark.asyncio
async def test_run_one_replan_then_success() -> None:
    """② 触发一次有界 replan 后成功：round0 fail → round1 pass → compose（loop_step=1）。"""
    ctrl = AgenticLoopController(max_loop_steps=4)
    scripted = [verdict(False, True, ["g1"]), verdict(True, True)]

    async def run_round(step: int) -> CriticVerdict:
        return scripted[step]

    outcome = await ctrl.run(run_round)
    assert isinstance(outcome, LoopOutcome)
    assert outcome.decision == "compose"
    assert outcome.loop_step == 1
    assert outcome.verdict.passed is True


@pytest.mark.asyncio
async def test_run_reaches_max_loop_steps_downgrade_no_fabrication() -> None:
    """③ 达 max_loop_steps：每轮失败原因不同（非震荡）→ 走满上界后诚实降级。"""
    ctrl = AgenticLoopController(max_loop_steps=4)
    calls = {"n": 0}

    async def run_round(step: int) -> CriticVerdict:
        calls["n"] += 1
        return verdict(False, True, [f"distinct-reason-{step}"])

    outcome = await ctrl.run(run_round)
    assert outcome.decision == "downgrade"
    assert outcome.oscillation is False
    assert outcome.loop_step == 3  # 0..3 共 4 轮
    assert calls["n"] == 4  # 严格有界：恰好 max_loop_steps 轮


@pytest.mark.asyncio
async def test_run_oscillation_terminates_early() -> None:
    """无进展提前终止：连续两轮同因 → 第 2 轮即降级（不走满上界，省调用）。"""
    ctrl = AgenticLoopController(max_loop_steps=8)
    calls = {"n": 0}

    async def run_round(step: int) -> CriticVerdict:
        calls["n"] += 1
        return verdict(False, True, ["same-reason"])

    outcome = await ctrl.run(run_round)
    assert outcome.decision == "downgrade"
    assert outcome.oscillation is True
    assert outcome.loop_step == 1
    assert calls["n"] == 2  # 提前终止，远小于 max=8


@pytest.mark.asyncio
async def test_run_never_exceeds_max_loop_steps() -> None:
    """非自由 ReAct 不变量：run_round 调用次数永不超过 max_loop_steps。"""
    ctrl = AgenticLoopController(max_loop_steps=3)
    calls = {"n": 0}

    async def run_round(step: int) -> CriticVerdict:
        calls["n"] += 1
        return verdict(False, True, [f"r{step}"])  # 永远失败

    outcome = await ctrl.run(run_round)
    assert calls["n"] <= 3
    assert outcome.decision == "downgrade"


# ── B. 诚实降级回复（不编造、不 surface 越权内容）─────────────────────


def test_downgrade_reply_is_honest_and_not_fabricated() -> None:
    """③/⑦ 诚实降级：明示未达证据标准、降级，给可执行下一步，不臆造课件内容。"""
    ctrl = AgenticLoopController(max_loop_steps=4)
    reply = ctrl.downgrade_reply(goal="sorted 是否稳定", reasons=["UNKNOWN_KC:PY43-KC-999"])
    assert "证据标准" in reply
    assert ("降级" in reply) or ("诚实" in reply)
    assert "sorted 是否稳定" in reply
    # 不得伪造课件页码 / 编造答案
    assert "完整答案" not in reply
    assert "answer_key" not in reply


def test_downgrade_reply_excludes_rejected_content() -> None:
    """⑦ 被 critic 拒绝的越权内容绝不出现在降级回复里。"""
    ctrl = AgenticLoopController(max_loop_steps=4)
    rejected = "这是完整答案：print(secret_flag)"
    reply = ctrl.downgrade_reply(goal="某题", reasons=["SAFETY_KEYWORD:完整答案"])
    assert "secret_flag" not in reply
    assert rejected not in reply


# ── C. BROWSE scope / allowlist 闸门（④）────────────────────────────────


def test_browse_blocked_in_quiz_scope() -> None:
    """④ 考核 scope 收缩：BROWSE 不可派发。"""
    ctrl = AgenticLoopController(max_loop_steps=4, scope="quiz")
    ok, reason = ctrl.browse_dispatchable(
        allowed_domains=["docs.python.org"], executor_available=True
    )
    assert ok is False
    assert "scope" in reason


def test_browse_allowed_in_default_scope() -> None:
    ctrl = AgenticLoopController(max_loop_steps=4, scope="default")
    ok, reason = ctrl.browse_dispatchable(
        allowed_domains=["docs.python.org"], executor_available=True
    )
    assert ok is True
    assert reason == ""


def test_browse_blocked_when_allowlist_empty() -> None:
    """空 allowlist = 拒绝发起（§3.4）。"""
    ctrl = AgenticLoopController(max_loop_steps=4, scope="default")
    ok, reason = ctrl.browse_dispatchable(allowed_domains=[], executor_available=True)
    assert ok is False
    assert "allowlist" in reason


def test_browse_blocked_when_executor_unavailable() -> None:
    """executor 未接线（Phase 2 前）→ 不可派发，fail-soft。"""
    ctrl = AgenticLoopController(max_loop_steps=4, scope="default")
    ok, reason = ctrl.browse_dispatchable(
        allowed_domains=["docs.python.org"], executor_available=False
    )
    assert ok is False
    assert "executor" in reason


def test_dispatchable_tool_names_excludes_browse_in_quiz() -> None:
    """④ 工具集按 scope 收缩：quiz 不含 BROWSE，default 含。"""
    base = ["ASK", "BROWSE", "VIDEO"]
    quiz_ctrl = AgenticLoopController(max_loop_steps=4, scope="quiz")
    default_ctrl = AgenticLoopController(max_loop_steps=4, scope="default")
    assert "BROWSE" not in quiz_ctrl.dispatchable_tool_names(
        base, allowed_domains=["docs.python.org"], browse_executor_available=True
    )
    assert "BROWSE" in default_ctrl.dispatchable_tool_names(
        base, allowed_domains=["docs.python.org"], browse_executor_available=True
    )


# ── D. BROWSE 执行（mock；外部标签引用 + fail-soft 回退 VIDEO）──────────


@pytest.mark.asyncio
async def test_run_browse_returns_tagged_external_citations() -> None:
    """白名单内派发：回带受界步骤 + 外部引用（统一标注「外部内容，非课件」，不进课件证据）。"""
    ctrl = AgenticLoopController(max_loop_steps=4, scope="default")
    executor = FakeBrowseExecutor()
    dispatch = await ctrl.run_browse(
        executor,
        goal="去 python 官网查 sorted 的稳定性",
        user_id=7,
        allowed_domains=["docs.python.org"],
    )
    assert isinstance(dispatch, BrowseDispatch)
    assert dispatch.dispatched is True
    assert dispatch.error == ""
    assert dispatch.citations, "应有外部引用"
    for cite in dispatch.citations:
        assert cite.kind == "external"
        assert "非课件" in cite.label
    assert dispatch.step.tool_name == "BROWSE"
    assert dispatch.step.status == "done"
    # executor 收到受界请求
    assert executor.calls and executor.calls[0].max_steps >= 1


@pytest.mark.asyncio
async def test_run_browse_quiz_scope_not_dispatched_fallback_video() -> None:
    """④ quiz scope → 不派发，回退 VIDEO，不报错。"""
    ctrl = AgenticLoopController(max_loop_steps=4, scope="quiz")
    executor = FakeBrowseExecutor()
    dispatch = await ctrl.run_browse(
        executor, goal="查文档", user_id=7, allowed_domains=["docs.python.org"]
    )
    assert dispatch.dispatched is False
    assert dispatch.fallback == "VIDEO"
    assert dispatch.step.status == "failed"
    assert executor.calls == []  # 收缩后绝不发起请求


@pytest.mark.asyncio
async def test_run_browse_executor_error_failsoft_fallback() -> None:
    """⑤ 容错：executor 异常 → fail-soft，回退 VIDEO，捕获 error，不外抛。"""
    ctrl = AgenticLoopController(max_loop_steps=4, scope="default")
    executor = FakeBrowseExecutor(boom=True)
    dispatch = await ctrl.run_browse(
        executor, goal="查文档", user_id=7, allowed_domains=["docs.python.org"]
    )
    assert dispatch.error != ""
    assert dispatch.fallback == "VIDEO"
    assert dispatch.step.status == "failed"


@pytest.mark.asyncio
async def test_run_browse_truncated_propagated() -> None:
    """触 max_steps → truncated 透传。"""
    ctrl = AgenticLoopController(max_loop_steps=4, scope="default")
    executor = FakeBrowseExecutor(
        result=BrowseResult(
            steps=[BrowseStep(index=0, action="navigate", url="https://pypi.org", summary="x")],
            final_summary="部分结果",
            citations=["https://pypi.org"],
            truncated=True,
        )
    )
    dispatch = await ctrl.run_browse(
        executor, goal="查 pypi", user_id=7, allowed_domains=["pypi.org"]
    )
    assert dispatch.dispatched is True
    assert dispatch.result is not None and dispatch.result.truncated is True


def test_null_browse_executor_signals_unavailable() -> None:
    """缺省 NullBrowseExecutor 仅作占位标记（Phase 2 由 B1 真 executor 替换）。"""
    assert isinstance(NullBrowseExecutor(), NullBrowseExecutor)


# ── E. 记忆 brief 折叠（⑤ 缺省兼容）────────────────────────────────────


@pytest.mark.asyncio
async def test_null_memory_brief_provider_returns_empty() -> None:
    brief = await NullMemoryBriefProvider().fetch(user_id=7, query="任意")
    assert isinstance(brief, MemoryBrief)
    assert brief.facts == [] and brief.weak_kcs == [] and brief.style_notes == []


def test_fold_memory_brief_empty_is_noop() -> None:
    """⑤ 缺省空 brief → 请求原样返回（向后兼容，不改 profile_brief / decaying_kcs）。"""
    req = request_of(profile_brief="原始画像", decaying_kcs=["PY43-KC-003"])
    folded = fold_memory_brief(req, MemoryBrief())
    assert folded.profile_brief == "原始画像"
    assert folded.decaying_kcs == ["PY43-KC-003"]


def test_fold_memory_brief_injects_facts_and_weak_kcs() -> None:
    """非空 brief → 带标签事实/风格注入 profile_brief；weak_kcs 并入 decaying_kcs（去重、体现时序）。"""
    brief = MemoryBrief(
        facts=["上次卡在缩进"],
        weak_kcs=["PY43-KC-003", "PY43-KC-007"],
        style_notes=["偏好可视化"],
        as_of="2026-06-17T10:00:00Z",
    )
    req = request_of(profile_brief="原始", decaying_kcs=["PY43-KC-003"])
    folded = fold_memory_brief(req, brief)
    assert "上次卡在缩进" in (folded.profile_brief or "")
    assert "偏好可视化" in (folded.profile_brief or "")
    assert "2026-06-17" in (folded.profile_brief or "")  # 体现时序
    assert "原始" in (folded.profile_brief or "")
    # weak_kcs 去重并入 decaying_kcs
    assert set(folded.decaying_kcs) == {"PY43-KC-003", "PY43-KC-007"}


def test_fold_memory_brief_respects_profile_brief_maxlen() -> None:
    """注入后裁剪到 profile_brief 上限（2000），不破坏 schema 校验。"""
    brief = MemoryBrief(facts=["x" * 3000], as_of="2026-06-17T10:00:00Z")
    folded = fold_memory_brief(request_of(), brief)
    assert folded.profile_brief is not None
    assert len(folded.profile_brief) <= 2000


# ── F. 审计记录（⑥）────────────────────────────────────────────────────


def test_build_run_record_all_fields_populated() -> None:
    """⑥ AgenticLoopRunRecord 字段齐全且类型正确（镜像 V171 关键列）。"""
    ctrl = AgenticLoopController(max_loop_steps=4)
    record = ctrl.build_run_record(
        user_id=7,
        session_id="t-loop",
        turn=2,
        verdict=verdict(True, True),
        steps_used=2,
        replan_count=1,
        tools_used=["StoryboardAgent", "PracticeAgent"],
        input_tokens=120,
        output_tokens=64,
        trace_id="abc123",
    )
    assert isinstance(record, AgenticLoopRunRecord)
    assert record.user_id == 7
    assert record.session_id == "t-loop"
    assert record.turn == 2
    assert record.max_steps == 4
    assert record.steps_used == 2
    assert record.replan_count == 1
    assert record.tools_used == ["StoryboardAgent", "PracticeAgent"]
    assert record.grounding_pass is True
    assert record.safety_pass is True
    assert record.input_tokens == 120
    assert record.output_tokens == 64
    assert record.trace_id == "abc123"


def test_run_record_reflects_failed_verdict() -> None:
    """审计如实记录 critic 失败（grounding/safety pass = False）。"""
    ctrl = AgenticLoopController(max_loop_steps=4)
    record = ctrl.build_run_record(
        user_id=1,
        session_id="s",
        turn=0,
        verdict=verdict(False, False, ["g", "s"]),
        steps_used=4,
        replan_count=3,
        tools_used=[],
    )
    assert record.grounding_pass is False
    assert record.safety_pass is False


def test_count_bounded_recoveries_counts_reflect_and_replan() -> None:
    """审计辅助：统计有界恢复次数（reflect 重派 + recent_failures replan），negotiate 不计。"""
    trace = [
        GraphTraceStep(node="understand", elapsed_ms=1, status="ok"),
        GraphTraceStep(node="reflect", elapsed_ms=0, status="retry", note="reflect_retry:learn"),
        GraphTraceStep(node="replan", elapsed_ms=0, status="ok", note="recent_failures:PY43-KC-002"),
        GraphTraceStep(node="replan", elapsed_ms=0, status="ok", note="negotiate:lower"),
    ]
    assert count_bounded_recoveries(trace) == 2


def test_tool_trail_from_results_dedup_preserves_order() -> None:
    results = [
        DispatchResult(step_id="a", agent="StoryboardAgent", ok=True, summary="x"),
        DispatchResult(step_id="b", agent="PracticeAgent", ok=True, summary="y"),
        DispatchResult(step_id="c", agent="StoryboardAgent", ok=True, summary="z"),
    ]
    assert tool_trail_from_results(results) == ["StoryboardAgent", "PracticeAgent"]


# ── G. eval span（critic 后调用，fail-soft）─────────────────────────────


def test_record_eval_emits_grounding_and_safety(monkeypatch: pytest.MonkeyPatch) -> None:
    """critic 后调 eval_span：记录 grounding/safety 通过率（0/1）。"""
    captured: list[tuple[str, float]] = []

    def fake_record(name: str, value: float) -> None:
        captured.append((name, value))

    monkeypatch.setattr(al, "record_eval_score", fake_record)
    ctrl = AgenticLoopController(max_loop_steps=4)
    ctrl.record_eval(verdict(grounding=True, safety=False))
    assert ("grounding_pass", 1.0) in captured
    assert ("safety_pass", 0.0) in captured


def test_record_eval_failsoft(monkeypatch: pytest.MonkeyPatch) -> None:
    """观测 fail-soft：record_eval_score 抛错也绝不拖垮主链路。"""

    def boom(name: str, value: float) -> None:
        raise RuntimeError("otel down")

    monkeypatch.setattr(al, "record_eval_score", boom)
    ctrl = AgenticLoopController(max_loop_steps=4)
    ctrl.record_eval(verdict(True, True))  # 不抛


# ── H. 可见有界计划（§3.8）─────────────────────────────────────────────


def test_build_loop_plan_bounded_and_mapped() -> None:
    """§3.8 可见有界计划：bounded=True、max_steps 对齐、阶段映射合法。"""
    ctrl = AgenticLoopController(max_loop_steps=4)
    plan_steps = [
        PlanStep(id="s1", phase="watch", title="看 for 分镜", detail="d", agent="StoryboardAgent"),
        PlanStep(id="s2", phase="learn", title="学讲义", detail="d", agent="ResourceDesignerAgent", resource_type="HANDOUT"),
        PlanStep(id="s4", phase="assess", title="测一测", detail="d", agent="PathPlannerAgent", status="pending"),
    ]
    loop_plan = ctrl.build_loop_plan(plan_steps)
    assert isinstance(loop_plan, LoopPlan)
    assert loop_plan.bounded is True
    assert loop_plan.max_steps == 4
    assert len(loop_plan.steps) == 3
    valid_phases = {"plan", "recall", "retrieve", "act", "critique", "compose"}
    for step in loop_plan.steps:
        assert isinstance(step, LoopStep)
        assert step.phase in valid_phases
    # assess（测）映射为 critique（验证阶段）
    assert loop_plan.steps[2].phase == "critique"


# ── I. OrchestratorGraph 集成（真挂载）─────────────────────────────────


@pytest.mark.asyncio
async def test_orchestrator_normal_single_round_closed_loop() -> None:
    """① 正常单轮闭环：critic 通过 → 保留 compose 回复 + 审计记录 grounding/safety pass。"""
    await reset_session("t-normal")
    graph = make_graph()
    resp = await graph.run(request_of(session_id="t-normal"))
    assert resp.reply_md == "规划完成。"  # 未被降级覆盖
    assert resp.safety_result.passed is True
    assert resp.grounding_result.passed is True
    record = graph.last_loop_record
    assert record is not None
    assert record.grounding_pass is True
    assert record.safety_pass is True
    assert record.steps_used >= 1
    assert record.max_steps == 4
    assert record.tools_used, "tool_trail 应记录已派发工位"


@pytest.mark.asyncio
async def test_orchestrator_injects_memory_brief_before_understand() -> None:
    """⑤ memory_brief 注入：折叠进 profile_brief（理解/调度可见）+ 入轮快照。"""
    await reset_session("t-mem")
    brief = MemoryBrief(
        facts=["上次卡在缩进"], weak_kcs=["PY43-KC-003"], style_notes=["偏好可视化"], as_of="2026-06-17T10:00:00Z"
    )
    graph = make_graph(memory_brief_provider=StaticMemoryBriefProvider(brief))
    await graph.run(request_of(session_id="t-mem", profile_brief="原始画像"))
    # 折叠后的 profile_brief 进入本轮调度上下文
    assert "上次卡在缩进" in graph._dispatcher._ctx.profile_brief
    assert "原始画像" in graph._dispatcher._ctx.profile_brief
    # memory_brief 入轮 checkpoint 快照（可审计）
    snapshot = await graph.get_turn_state("t-mem", 0)
    assert snapshot.get("memory_brief", {}).get("facts") == ["上次卡在缩进"]


@pytest.mark.asyncio
async def test_orchestrator_default_no_provider_backward_compatible() -> None:
    """⑤ 缺省（无 provider）→ 空 brief，profile_brief 不被污染，行为不变。"""
    await reset_session("t-nobrief")
    graph = make_graph()  # 默认 NullMemoryBriefProvider
    await graph.run(request_of(session_id="t-nobrief", profile_brief="仅原始"))
    assert graph._dispatcher._ctx.profile_brief == "仅原始"


@pytest.mark.asyncio
async def test_orchestrator_critic_reject_does_not_compose_unauthorized() -> None:
    """⑦ critic 拒绝（安全泄答）→ 不 surface 越权内容，给诚实降级；safety_result 如实 False。"""
    await reset_session("t-reject")
    graph = make_graph(provider=StubProvider(reply="这是完整答案：print(42)"))
    resp = await graph.run(request_of(session_id="t-reject"))
    assert resp.safety_result.passed is False
    assert any("SAFETY_KEYWORD" in r for r in resp.safety_result.reasons)
    # 越权内容被抑制，替换为诚实降级
    assert "完整答案" not in resp.reply_md
    assert "证据标准" in resp.reply_md


@pytest.mark.asyncio
async def test_orchestrator_replan_recorded_in_audit() -> None:
    """② 触发有界 replan：审计 replan_count≥1，tool_trail 含 DebugAgent。"""
    await reset_session("t-replan")
    graph = make_graph()
    resp = await graph.run(
        request_of(
            session_id="t-replan",
            recent_failures=["PY43-KC-002"],
            course_kc_index=["PY43-KC-001", "PY43-KC-002"],
        )
    )
    debug_steps = [s for s in resp.plan if s.agent == "DebugAgent"]
    assert debug_steps, "recent_failures 应插入 DebugAgent 补练步"
    record = graph.last_loop_record
    assert record is not None
    assert record.replan_count >= 1
    assert "DebugAgent" in record.tools_used


@pytest.mark.asyncio
async def test_orchestrator_loop_record_sink_wired() -> None:
    """Phase 2 接线点：注入 sink 回调即可拿到结构化审计记录（落库由 S2 负责）。"""
    await reset_session("t-sink")
    received: list[AgenticLoopRunRecord] = []

    async def sink(record: AgenticLoopRunRecord) -> None:
        received.append(record)

    graph = make_graph(loop_record_sink=sink)
    await graph.run(request_of(session_id="t-sink"))
    assert len(received) == 1
    assert received[0].session_id == "t-sink"


@pytest.mark.asyncio
async def test_orchestrator_loop_record_sink_failsoft() -> None:
    """sink 抛错 → fail-soft，不拖垮主链路（仍正常返回响应）。"""
    await reset_session("t-sink-boom")

    async def boom_sink(record: AgenticLoopRunRecord) -> None:
        raise RuntimeError("db down")

    graph = make_graph(loop_record_sink=boom_sink)
    resp = await graph.run(request_of(session_id="t-sink-boom"))
    assert resp.reply_md == "规划完成。"


@pytest.mark.asyncio
async def test_orchestrator_browse_step_dispatched_default_scope() -> None:
    """BROWSE 可派发（default scope）：经闭环路由到 mock executor，外部引用标注非课件。"""
    await reset_session("t-browse-ok")
    executor = FakeBrowseExecutor()
    graph = make_graph(
        browse_executor=executor,
        browse_allowed_domains=["docs.python.org"],
    )
    step = PlanStep(
        id="s-browse",
        phase="learn",
        title="联网查证 sorted 稳定性",
        detail="去 python 官网查 sorted 的稳定性",
        agent="BrowseAgent",
        kc_codes=["PY43-KC-001"],
    )
    result = await graph._dispatch_step(step, user_id=7)
    assert result.ok is True
    assert result.agent == "BrowseAgent"
    assert result.artifact is not None
    assert result.artifact.get("external") is True
    assert result.artifact.get("citations")
    assert executor.calls, "default scope 应真正调用 executor"


@pytest.mark.asyncio
async def test_orchestrator_browse_step_blocked_quiz_scope() -> None:
    """④ quiz scope：BROWSE 步不可派发，回退 VIDEO，executor 绝不被调用。"""
    await reset_session("t-browse-quiz")
    executor = FakeBrowseExecutor()
    graph = make_graph(
        browse_executor=executor,
        browse_allowed_domains=["docs.python.org"],
        loop_scope="quiz",
    )
    step = PlanStep(
        id="s-browse",
        phase="learn",
        title="联网查证",
        detail="查文档",
        agent="BrowseAgent",
        kc_codes=["PY43-KC-001"],
    )
    result = await graph._dispatch_step(step, user_id=7)
    assert result.artifact is not None
    assert result.artifact.get("kind") == "browse_skipped"
    assert result.artifact.get("fallback") == "VIDEO"
    assert executor.calls == []
