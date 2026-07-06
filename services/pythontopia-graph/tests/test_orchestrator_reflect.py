"""A3 有界反思（reflect 节点，N=1）验收测试。

契约：dispatch 之后、既有路由（negotiate / recent_failures→replan）之前，对 learn 阶段
「执行类失败」（``ok=False`` 且 ``artifact.kind=="dispatch_failure"``）的资源步沿
CODE_LAB→HANDOUT→READING 降一档，经 dispatcher（受治理）**仅重派一次**：

- 命中即降级重派一次（N=1）；
- 已是链尾 READING / 不可降级 → 不重派；
- 重派仍失败 → 保留失败结果走完；
- SSE/返回 trace 以 ``reflect_retry:learn`` 标注反思重派。
"""

from __future__ import annotations

from collections.abc import AsyncIterator, Iterator

import pytest

from pythontopia_graph.agents.code_lab_agent import CodeLabAgent
from pythontopia_graph.agents.dispatch import AgentDispatcher, DispatchContext
from pythontopia_graph.agents.handout_agent import HandoutAgent
from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.graph.orchestrator_session import reset_session
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher
from pythontopia_graph.schemas.orchestrator import (
    DispatchResult,
    OrchestratorRequest,
    PlanStep,
)
from pythontopia_graph.schemas.resource import (
    HandoutContent,
    HandoutSection,
    ResourceDraft,
)
from pythontopia_graph.tools.harness import reset_harness_state


@pytest.fixture(autouse=True)
def _clean_harness() -> Iterator[None]:
    """隔离进程级熔断器/统计：重派经治理包装（breaker=provider）。"""
    reset_harness_state()
    yield
    reset_harness_state()


class StubProvider(LlmProvider):
    """understand/decompose/profile 走规则兜底（None payload），compose 吐固定 reply。"""

    def __init__(self, reply: str = "规划完成。") -> None:
        self._reply = reply

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_json=None)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        for index in range(0, len(self._reply), 6):
            yield self._reply[index : index + 6]


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
    session_id: str = "t-reflect",
    profile_brief: str | None = None,
) -> OrchestratorRequest:
    return OrchestratorRequest(
        user_id=7,
        session_id=session_id,
        user_message=message,
        profile_brief=profile_brief,
        course_kc_index=["PY43-KC-001", "PY43-KC-003"],
    )


def learn_step(resource_type: str, step_id: str = "s2-learn") -> PlanStep:
    return PlanStep(
        id=step_id,
        phase="learn",
        title="for 循环资源",
        detail="吃透 for 循环",
        agent="ResourceDesignerAgent",
        kc_codes=["PY43-KC-001"],
        panel="library",
        resource_type=resource_type,
    )


def dispatch_failure(step: PlanStep) -> DispatchResult:
    """构造一条与 dispatcher 治理失败同形的 learn 执行类失败结果。"""
    return DispatchResult(
        step_id=step.id,
        agent=step.agent,
        ok=False,
        summary=f"{step.title} 执行失败（error）",
        note="RuntimeError",
        elapsed_ms=5,
        artifact={
            "kind": "dispatch_failure",
            "status": "error",
            "error_class": "RuntimeError",
        },
    )


def make_graph(provider: LlmProvider | None = None) -> OrchestratorGraph:
    return OrchestratorGraph(
        provider=provider or StubProvider(),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )


def fake_handout_design(call_counter: dict[str, int]):
    async def _design(self, request, evidence, revision_notes=None):  # type: ignore[no-untyped-def]
        call_counter["n"] += 1
        call_counter["last_type"] = str(request.type)
        return ResourceDraft(
            title="for 循环讲义",
            summary="for 基础与缩进",
            content_format="MARKDOWN",
            handout=HandoutContent(
                sections=[
                    HandoutSection(heading="for", paragraphs=["遍历可迭代对象。"])
                ]
            ),
        )

    return _design


# ── AgentDispatcher.downgraded_learn_step 单元测试 ──


@pytest.mark.parametrize(
    ("current", "expected"),
    [
        ("CODE_LAB", "HANDOUT"),
        ("HANDOUT", "READING"),
        ("READING", None),
    ],
)
def test_downgrade_chain(current: str, expected: str | None) -> None:
    """降级链 CODE_LAB→HANDOUT→READING；链尾 READING 返回 None（不重派）。"""
    ctx = DispatchContext(
        provider=StubProvider(),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )
    dispatcher = AgentDispatcher(ctx)
    downgraded = dispatcher.downgraded_learn_step(learn_step(current))
    if expected is None:
        assert downgraded is None
    else:
        assert downgraded is not None
        assert downgraded.resource_type == expected
        # 仅改 resource_type，step 身份/agent 不变。
        assert downgraded.id == "s2-learn"
        assert downgraded.agent == "ResourceDesignerAgent"


def test_downgrade_none_learn_defaults_to_handout_chain() -> None:
    """resource_type=None 的 learn 步按解析默认 HANDOUT 入链 → 降级为 READING。"""
    ctx = DispatchContext(
        provider=StubProvider(),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes=set(),
    )
    dispatcher = AgentDispatcher(ctx)
    step = learn_step("HANDOUT")
    step.resource_type = None
    downgraded = dispatcher.downgraded_learn_step(step)
    assert downgraded is not None
    assert downgraded.resource_type == "READING"


def test_downgrade_non_chain_type_returns_none() -> None:
    """不在降级链上的资源类型（如 MINDMAP / STORYBOARD）→ 不降级、不重派。"""
    ctx = DispatchContext(
        provider=StubProvider(),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes=set(),
    )
    dispatcher = AgentDispatcher(ctx)
    assert dispatcher.downgraded_learn_step(learn_step("MINDMAP")) is None
    storyboard = learn_step("STORYBOARD")
    storyboard.agent = "StoryboardAgent"
    assert dispatcher.downgraded_learn_step(storyboard) is None


# ── reflect 节点单元测试 ──


@pytest.mark.asyncio
async def test_reflect_retries_failed_learn_step_downgraded_once(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """learn 步首次 dispatch_failure → 以降级类型（CODE_LAB→HANDOUT）经 dispatcher 重派一次。"""
    counter = {"n": 0, "last_type": ""}
    monkeypatch.setattr(HandoutAgent, "design", fake_handout_design(counter))

    graph = make_graph()
    captured: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        captured.append((event, data))

    graph._emit_cb = emit

    step = learn_step("CODE_LAB")
    state = {
        "req": request_of(),
        "plan": [step],
        "dispatch_results": [dispatch_failure(step)],
    }
    out = await graph._node_reflect(state)

    # 重派恰好一次（N=1），且用降级后的 HANDOUT。
    assert counter["n"] == 1
    assert counter["last_type"] == "HANDOUT"
    retry_results = out["dispatch_results"]
    assert len(retry_results) == 1
    retry = retry_results[0]
    assert retry.ok is True
    assert retry.step_id == "s2-learn"  # 与原失败同 step_id（便于收敛）
    assert retry.artifact is not None and retry.artifact["type"] == "HANDOUT"

    # SSE trace 体现反思重派。
    trace_notes = [(d.get("note") or "") for (e, d) in captured if e == "trace"]
    assert any("reflect_retry:learn" in note for note in trace_notes)
    reflect_traces = [
        d for (e, d) in captured if e == "trace" and d.get("node") == "reflect"
    ]
    assert reflect_traces and reflect_traces[0]["status"] == "retry"
    # 返回的 trace 列表同样带 reflect_retry:learn 标注。
    assert any("reflect_retry:learn" in (t.note or "") for t in out["trace"])


@pytest.mark.asyncio
async def test_reflect_does_not_retry_when_already_reading(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """learn 步已是 READING → 不再降级、不重派（杜绝无界重试）。"""
    graph = make_graph()

    calls = {"n": 0}

    async def spy_dispatch_step(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        calls["n"] += 1
        raise AssertionError("READING learn 步不应被重派")

    monkeypatch.setattr(graph, "_dispatch_step", spy_dispatch_step)

    step = learn_step("READING")
    state = {
        "req": request_of(),
        "plan": [step],
        "dispatch_results": [dispatch_failure(step)],
    }
    out = await graph._node_reflect(state)

    assert out == {}
    assert calls["n"] == 0


@pytest.mark.asyncio
async def test_reflect_skips_success_and_non_learn_failures(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """learn 步成功、或非 learn 步失败（如 watch dispatch_failure）→ 均不重派。"""
    graph = make_graph()

    async def spy_dispatch_step(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        raise AssertionError("不满足触发条件时不应重派")

    monkeypatch.setattr(graph, "_dispatch_step", spy_dispatch_step)

    watch = PlanStep(
        id="s1-watch",
        phase="watch",
        title="看 for 分镜",
        detail="建立直观印象",
        agent="StoryboardAgent",
        kc_codes=["PY43-KC-001"],
        panel="lecture",
    )
    learn_ok = learn_step("CODE_LAB")
    state = {
        "req": request_of(),
        "plan": [watch, learn_ok],
        "dispatch_results": [
            dispatch_failure(watch),  # 非 learn 的执行类失败 → 不归 reflect 处理
            DispatchResult(
                step_id=learn_ok.id,
                agent=learn_ok.agent,
                ok=True,
                summary="已生成「for 循环代码实验」(CODE_LAB)",
                elapsed_ms=3,
                artifact={"type": "CODE_LAB"},
            ),
        ],
    }
    out = await graph._node_reflect(state)
    assert out == {}


@pytest.mark.asyncio
async def test_reflect_skips_honest_business_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """learn 步 ok=False 但非 dispatch_failure（子 agent 诚实业务失败）→ 不重派。"""
    graph = make_graph()

    async def spy_dispatch_step(step, user_id=1, focus_hint_extra=""):  # type: ignore[no-untyped-def]
        raise AssertionError("业务性 FAILED 不触发反思重派")

    monkeypatch.setattr(graph, "_dispatch_step", spy_dispatch_step)

    step = learn_step("CODE_LAB")
    honest_failure = DispatchResult(
        step_id=step.id,
        agent=step.agent,
        ok=False,
        summary="资源生成 FAILED：证据不足",
        elapsed_ms=4,
        artifact={"kind": "resource_failed"},  # 非 dispatch_failure
    )
    state = {"req": request_of(), "plan": [step], "dispatch_results": [honest_failure]}
    out = await graph._node_reflect(state)
    assert out == {}


@pytest.mark.asyncio
async def test_reflect_retry_failure_keeps_failure_result_n1(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """重派也失败 → 保留（最新一次）失败结果，且仍严格 N=1（不再二次重派）。"""
    handout_calls = {"n": 0}

    async def boom_handout(self, request, evidence, revision_notes=None):  # type: ignore[no-untyped-def]
        handout_calls["n"] += 1
        raise RuntimeError("handout upstream down")

    monkeypatch.setattr(HandoutAgent, "design", boom_handout)

    graph = make_graph()
    step = learn_step("CODE_LAB")
    failure = dispatch_failure(step)
    state = {"req": request_of(), "plan": [step], "dispatch_results": [failure]}
    out = await graph._node_reflect(state)

    assert handout_calls["n"] == 1  # 仅重派一次，未无界重试
    retry = out["dispatch_results"][0]
    assert retry.ok is False
    assert retry.artifact is not None and retry.artifact["kind"] == "dispatch_failure"

    # _assemble 收敛：同 step_id 的原失败 + 重派失败 → 单条失败结果。
    collapsed = OrchestratorGraph._collapse_redispatched_results([failure, retry])
    assert len(collapsed) == 1
    assert collapsed[0].ok is False


def test_collapse_keeps_latest_per_step_and_preserves_order() -> None:
    """收敛：同 step_id 取最新结果、保持首次出现位置；唯一 step_id 时为恒等。"""
    s1 = DispatchResult(step_id="s1-watch", agent="StoryboardAgent", ok=True, summary="w")
    learn_fail = DispatchResult(
        step_id="s2-learn", agent="ResourceDesignerAgent", ok=False, summary="fail"
    )
    s3 = DispatchResult(step_id="s3-practice", agent="PracticeAgent", ok=True, summary="p")
    learn_retry = DispatchResult(
        step_id="s2-learn", agent="ResourceDesignerAgent", ok=True, summary="retry-ok"
    )

    collapsed = OrchestratorGraph._collapse_redispatched_results(
        [s1, learn_fail, s3, learn_retry]
    )
    assert [r.step_id for r in collapsed] == ["s1-watch", "s2-learn", "s3-practice"]
    learn = next(r for r in collapsed if r.step_id == "s2-learn")
    assert learn.ok is True and learn.summary == "retry-ok"

    # 无重复 step_id → 恒等。
    assert OrchestratorGraph._collapse_redispatched_results([s1, s3]) == [s1, s3]


# ── 全图集成测试 ──


@pytest.mark.asyncio
async def test_full_graph_learn_failure_recovers_via_reflect(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """端到端：CODE_LAB learn 步首派失败 → reflect 降级 HANDOUT 重派成功；既有路由不变。"""
    await reset_session("t-reflect-e2e")

    codelab_calls = {"n": 0}
    handout_calls = {"n": 0, "last_type": ""}

    async def boom_codelab(self, request, evidence, revision_notes=None):  # type: ignore[no-untyped-def]
        codelab_calls["n"] += 1
        raise RuntimeError("code lab upstream down")

    monkeypatch.setattr(CodeLabAgent, "design", boom_codelab)
    monkeypatch.setattr(HandoutAgent, "design", fake_handout_design(handout_calls))

    graph = make_graph()
    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    # profile_brief 含「代码」→ decompose 规则兜底将 learn 步定为 CODE_LAB。
    await graph.run_streaming(
        request_of(
            session_id="t-reflect-e2e", profile_brief="偏好动手写代码实操练习"
        ),
        emit,
    )

    # CODE_LAB 首派一次失败，HANDOUT 反思重派一次成功（各 N=1）。
    assert codelab_calls["n"] == 1
    assert handout_calls["n"] == 1
    assert handout_calls["last_type"] == "HANDOUT"

    # 整轮不得出现终止性 error（单步失败 + 反思重派均为非终止）。
    assert [d for (e, d) in events if e == "error"] == []

    # 反思重派标注进入 SSE trace。
    trace_notes = [(d.get("note") or "") for (e, d) in events if e == "trace"]
    assert any("reflect_retry:learn" in note for note in trace_notes)

    # result 中 learn 步收敛为单条、ok=True、类型已降级为 HANDOUT。
    result_events = [d for (e, d) in events if e == "result"]
    assert len(result_events) == 1
    dispatch_results = result_events[0]["dispatch_results"]
    learn_entries = [dr for dr in dispatch_results if dr["step_id"] == "s2-learn"]
    assert len(learn_entries) == 1  # 已 dedup（非两条）
    assert learn_entries[0]["ok"] is True
    assert learn_entries[0]["artifact"]["type"] == "HANDOUT"


@pytest.mark.asyncio
async def test_full_graph_no_failure_keeps_reflect_noop(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """无 learn 失败：reflect 为 no-op，dispatch_results 不增不减、无 reflect trace。"""
    await reset_session("t-reflect-noop")
    graph = make_graph()

    response = await graph.run(
        request_of(session_id="t-reflect-noop")
    )
    # 标准四步计划，每步一条结果（reflect 无重派）。
    assert len(response.dispatch_results) == 4
    assert not any(
        (step.note or "").startswith("reflect_retry") for step in response.trace
    )
