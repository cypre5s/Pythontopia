"""P1.1 真协同内核验收测试：orchestrator 服务端真产出，非播报式文本。"""

from __future__ import annotations

from collections.abc import AsyncIterator, Iterator

import pytest

from pythontopia_graph.agents.dispatch import AgentDispatcher, DispatchContext
from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.graph.orchestrator_session import reset_session
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import (
    RagHit,
    StaticEvidenceFetcher,
)
from pythontopia_graph.schemas.intent import (
    LearnerRecentResource,
    LearnerRecentSubmission,
    LearnerRoutingContext,
    WorkshopInteractionContextRef,
)
from pythontopia_graph.schemas.orchestrator import OrchestratorRequest, PlanStep
from pythontopia_graph.tools.harness import reset_harness_state


@pytest.fixture(autouse=True)
def _clean_harness() -> Iterator[None]:
    """隔离进程级熔断器/统计：dispatch 内 LLM 子调用经治理包装（breaker=provider）。"""
    reset_harness_state()
    yield
    reset_harness_state()


class StubProvider(LlmProvider):
    """按队列返回 content_json；chat_stream 分片 reply。"""

    def __init__(
        self,
        payloads: list[dict | None] | None = None,
        reply: str = "好的，我们开始。",
    ) -> None:
        self._payloads = list(payloads or [])
        self._reply = reply
        self.chat_calls: list[list[dict[str, str]]] = []

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.chat_calls.append(messages)
        payload = self._payloads.pop(0) if self._payloads else None
        return LlmResponse(content_json=payload)

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


def request_of(message: str, session_id: str = "t-dispatch") -> OrchestratorRequest:
    return OrchestratorRequest(
        user_id=1,
        session_id=session_id,
        user_message=message,
        course_kc_index=["PY43-KC-001", "PY43-KC-003"],
    )


# ── AgentDispatcher 单元测试 ──


@pytest.mark.asyncio
async def test_dispatcher_practice_agent_produces_real_artifact() -> None:
    """PracticeAgent 经 dispatcher 产出真 DispatchResult（非纯文本摘要）。"""
    practice_payload = {
        "title": "for 循环练习",
        "practice": {
            "problems": [
                {
                    "title": "列表遍历",
                    "statement_md": "用 for 循环遍历列表 [1,2,3] 并打印每个元素。",
                    "hints": ["注意缩进"],
                    "sample_io": [{"input": "", "output": "1\n2\n3"}],
                    "kc_codes": ["PY43-KC-001"],
                    "difficulty": "basic",
                }
            ]
        },
    }
    provider = StubProvider([practice_payload])
    ctx = DispatchContext(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )
    dispatcher = AgentDispatcher(ctx)

    step = PlanStep(
        id="s-practice",
        phase="practice",
        title="for 循环练习",
        detail="用 for 循环遍历列表",
        agent="PracticeAgent",
        kc_codes=["PY43-KC-001"],
        panel="workshop",
        resource_type="PRACTICE",
    )

    result = await dispatcher.dispatch(step, user_id=1)
    assert result.ok is True
    assert result.agent == "PracticeAgent"
    assert result.elapsed_ms >= 0
    # 真产物：summary 应含资源标题而非旧式「已将…分派给…」文本
    assert "已将" not in result.summary
    assert "for 循环练习" in result.summary or "PRACTICE" in result.summary


@pytest.mark.asyncio
async def test_dispatcher_debug_agent_returns_deterministic_result() -> None:
    """DebugAgent 经 dispatcher 返回确定性分析结果。"""
    provider = StubProvider([])
    ctx = DispatchContext(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([]),
        valid_kc_codes=set(),
    )
    dispatcher = AgentDispatcher(ctx)

    step = PlanStep(
        id="s-debug",
        phase="practice",
        title="补练：矿洞修 Bug",
        detail="针对失败 KC 进入矿洞补练",
        agent="DebugAgent",
        kc_codes=["PY43-KC-001"],
        panel="workshop",
    )

    result = await dispatcher.dispatch(step, user_id=1)
    assert result.ok is True
    assert result.agent == "DebugAgent"
    # 应有实质内容（hint_layers 数量或 fallback 文本）
    assert len(result.summary) > 10


@pytest.mark.asyncio
async def test_dispatcher_debug_agent_uses_backend_verified_submission_evidence() -> None:
    provider = StubProvider([])
    context = WorkshopInteractionContextRef(
        current_problem_id=41,
        current_code="for i in range(n + 1):\n    total += i",
        latest_submission_id=901,
        error_text="WA: expected 10, got 15",
    )
    dispatcher = AgentDispatcher(
        DispatchContext(
            provider=provider,
            evidence_fetcher=StaticEvidenceFetcher([]),
            valid_kc_codes={"PY43-KC-001"},
            debug_context=context,
        )
    )
    step = PlanStep(
        id="s-debug-real",
        phase="practice",
        title="诊断失败提交",
        detail="先诊断再补练",
        agent="DebugAgent",
        kc_codes=["PY43-KC-001"],
        tool_name="FIX_BUG",
    )

    result = await dispatcher.dispatch(step, user_id=7)

    assert result.ok is True
    assert result.artifact is not None
    assert result.artifact["kind"] == "verified_debug_diagnosis"
    assert result.artifact["submission_id"] == 901
    assert result.artifact["counter_example_available"] is False
    assert "失败提交 #901" in result.summary


@pytest.mark.asyncio
async def test_dispatcher_resource_designer_without_type_falls_back() -> None:
    """ResourceDesignerAgent 有 resource_type 时真生成，无类型时降级。"""
    handout_payload = {
        "title": "for 循环讲义",
        "summary": "用课件证据解释 for 循环。",
        "sections": [{
            "heading": "for 循环",
            "paragraphs": ["for 循环用于遍历可迭代对象。"],
            "code_snippets": [],
            "evidence_refs": ["page-101"],
        }],
    }
    provider = StubProvider([handout_payload])
    ctx = DispatchContext(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )
    dispatcher = AgentDispatcher(ctx)

    step = PlanStep(
        id="s-learn",
        phase="learn",
        title="for 循环讲义",
        detail="学习 for 循环基础",
        agent="ResourceDesignerAgent",
        kc_codes=["PY43-KC-001"],
        panel="workshop",
        resource_type="HANDOUT",
    )

    result = await dispatcher.dispatch(step, user_id=1)
    assert result.ok is True
    # 真产物应含资源标题
    assert "for 循环讲义" in result.summary


@pytest.mark.asyncio
async def test_dispatcher_resource_result_includes_resource_graph_review_trace() -> None:
    """资源类 dispatcher 必须复用 ResourceGraph 审查链，而不是直调单个设计师。"""
    handout_payload = {
        "title": "for 循环讲义",
        "summary": "用课件证据解释 for 循环。",
        "sections": [{
            "heading": "for 循环",
            "paragraphs": ["for 循环遍历可迭代对象。"],
            "code_snippets": [],
            "evidence_refs": ["page-101"],
        }],
    }
    provider = StubProvider([handout_payload])
    ctx = DispatchContext(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )
    dispatcher = AgentDispatcher(ctx)
    step = PlanStep(
        id="s-reviewed",
        phase="learn",
        title="for 循环讲义",
        detail="学习 for 循环基础",
        agent="ResourceDesignerAgent",
        kc_codes=["PY43-KC-001"],
        panel="workshop",
        resource_type="HANDOUT",
    )

    result = await dispatcher.dispatch(step, user_id=1)

    assert result.ok is True
    assert result.artifact is not None
    assert result.artifact["kind"] == "resource_graph_result"
    assert result.artifact["graph_status"] in {"SUCCESS", "PARTIAL"}
    assert result.artifact["grounding_passed"] is True
    assert result.artifact["safety_passed"] is True
    trace_nodes = {step["node"] for step in result.artifact["graph_trace"]}
    assert {"evidence_fetcher", "grounding_critic", "semantic_grounding", "safety_critic"} <= trace_nodes


@pytest.mark.asyncio
async def test_dispatcher_path_planner_uses_request_user_id(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """PathPlannerAgent must receive the caller user_id, not the historical user_id=1 fallback."""
    from pythontopia_graph.agents.path_planner_agent import PathPlannerAgent
    from pythontopia_graph.schemas.path import (
        PathPlannerGroundingResult,
        PathPlannerResponse,
        PathPlannerSafetyResult,
    )

    captured: dict[str, int] = {}

    def fake_plan(self, request):  # type: ignore[no-untyped-def]
        captured["user_id"] = request.user_id
        return PathPlannerResponse(
            user_id=request.user_id,
            pack_id=request.pack_id,
            status="SUCCESS",
            goal_summary="personalized path",
            nodes=[],
            safety_result=PathPlannerSafetyResult(passed=True),
            grounding_result=PathPlannerGroundingResult(passed=True),
            deterministic_signature="test-signature",
        )

    monkeypatch.setattr(PathPlannerAgent, "plan", fake_plan)

    ctx = DispatchContext(
        provider=StubProvider([]),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )
    dispatcher = AgentDispatcher(ctx)
    step = PlanStep(
        id="s-path",
        phase="learn",
        title="规划下一步",
        detail="根据当前学生画像规划",
        agent="PathPlannerAgent",
        kc_codes=["PY43-KC-001"],
        panel="workshop",
    )

    result = await dispatcher.dispatch(step, user_id=4242)

    assert result.ok is True
    assert captured["user_id"] == 4242


def test_dispatcher_resource_profile_uses_authoritative_learning_context() -> None:
    """资源 Agent 的画像必须来自后端事实，不能继续使用空默认值。"""
    context = LearnerRoutingContext(
        profile_brief="计算机专业；偏好图示；循环边界易错",
        cognitive_style="visual",
        learning_preferences=["MINDMAP", "HANDOUT"],
        profile_major="CS",
        python_level=3,
        recent_error_kcs=["PY43-KC-001"],
        struggle_patterns=["连续两次在 range 右边界出错"],
        recent_wins=["已掌握列表索引"],
        intervention_hints=["先给图示，再给最小反例"],
    )
    dispatcher = AgentDispatcher(
        DispatchContext(
            provider=StubProvider([]),
            evidence_fetcher=StaticEvidenceFetcher([HIT]),
            valid_kc_codes={"PY43-KC-001"},
            learner_context=context,
        )
    )

    snapshot = dispatcher._resource_profile_snapshot()

    assert snapshot.major == "CS"
    assert snapshot.python_self_rated_level == 3
    assert snapshot.media_priority == ["MINDMAP", "HANDOUT"]
    assert snapshot.cognitive_style == "visual"
    assert snapshot.error_patterns == ["PY43-KC-001"]
    assert snapshot.recent_insight == "连续两次在 range 右边界出错"
    assert "已掌握列表索引" in snapshot.memory_highlights
    assert snapshot.memory_directives == ["先给图示，再给最小反例"]


@pytest.mark.asyncio
async def test_dispatcher_path_planner_uses_authoritative_learning_facts(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """路径规划必须消费真实掌握度、资源使用和正式提交，而非历史固定值。"""
    from pythontopia_graph.agents.path_planner_agent import PathPlannerAgent
    from pythontopia_graph.schemas.path import (
        PathPlannerGroundingResult,
        PathPlannerResponse,
        PathPlannerSafetyResult,
    )

    captured: dict[str, object] = {}

    def fake_plan(self, request):  # type: ignore[no-untyped-def]
        captured["request"] = request
        return PathPlannerResponse(
            user_id=request.user_id,
            pack_id=request.pack_id,
            status="SUCCESS",
            goal_summary="personalized path",
            nodes=[],
            safety_result=PathPlannerSafetyResult(passed=True),
            grounding_result=PathPlannerGroundingResult(passed=True),
            deterministic_signature="personalized-signature",
        )

    monkeypatch.setattr(PathPlannerAgent, "plan", fake_plan)
    context = LearnerRoutingContext(
        profile_brief="非计算机理工科，Python 自评 4 级",
        profile_major="NON_CS_STEM",
        python_level=4,
        kc_mastery={"PY43-KC-001": 0.23},
        kc_confidence={"PY43-KC-001": 0.71},
        recent_error_kcs=["PY43-KC-001"],
        recent_resources=[
            LearnerRecentResource(
                kc_code="PY43-KC-001",
                type="MINDMAP",
                created_at="2026-07-22T10:00:00+08:00",
            )
        ],
        recent_submissions=[
            LearnerRecentSubmission(
                problem_id=41,
                kc_code="PY43-KC-001",
                passed=False,
            )
        ],
    )
    dispatcher = AgentDispatcher(
        DispatchContext(
            provider=StubProvider([]),
            evidence_fetcher=StaticEvidenceFetcher([HIT]),
            valid_kc_codes={"PY43-KC-001"},
            learner_context=context,
        )
    )
    step = PlanStep(
        id="s-path-personalized",
        phase="learn",
        title="规划下一步",
        detail="根据真实学情规划",
        agent="PathPlannerAgent",
        kc_codes=["PY43-KC-001"],
        panel="workshop",
    )

    result = await dispatcher.dispatch(step, user_id=7)

    assert result.ok is True
    request = captured["request"]
    assert request.profile.has_profile is True  # type: ignore[union-attr]
    assert request.profile.major == "NON_CS_STEM"  # type: ignore[union-attr]
    assert request.profile.python_self_rated_level == 4  # type: ignore[union-attr]
    assert request.kcs[0].mastery_score == 0.23  # type: ignore[union-attr]
    assert request.kcs[0].confidence == 0.71  # type: ignore[union-attr]
    assert request.recent_resources[0].type == "MINDMAP"  # type: ignore[union-attr]
    assert request.recent_submissions[0].problem_id == 41  # type: ignore[union-attr]
    assert request.recent_submissions[0].passed is False  # type: ignore[union-attr]
    assert request.error_pattern_kcs == ["PY43-KC-001"]  # type: ignore[union-attr]


@pytest.mark.asyncio
async def test_dispatcher_resource_graph_failure_is_structured_failure() -> None:
    """编排内坏资源草稿必须被 ResourceGraph 拦住并作为结构化失败返回。"""
    bad_payload = {
        "title": "for 循环讲义",
        "summary": "包含未经证据支持的页码。",
        "sections": [{
            "heading": "for 循环",
            "paragraphs": ["根据第 999 页可知 for 循环总是运行 3 秒。"],
            "code_snippets": [],
            "evidence_refs": [],
        }],
    }
    provider = StubProvider([bad_payload, bad_payload, bad_payload])
    ctx = DispatchContext(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )
    dispatcher = AgentDispatcher(ctx)
    step = PlanStep(
        id="s-bad-reviewed",
        phase="learn",
        title="for 循环讲义",
        detail="学习 for 循环基础",
        agent="ResourceDesignerAgent",
        kc_codes=["PY43-KC-001"],
        panel="workshop",
        resource_type="HANDOUT",
    )

    result = await dispatcher.dispatch(step, user_id=1)

    assert result.ok is False
    assert result.artifact is not None
    assert result.artifact["kind"] == "resource_graph_failure"
    assert result.artifact["graph_status"] == "FAILED"
    assert result.artifact["grounding_passed"] is False
    assert "ResourceGraph" in result.summary
    trace_nodes = [step["node"] for step in result.artifact["graph_trace"]]
    assert "semantic_grounding" in trace_nodes


# ── OrchestratorGraph 集成测试 ──


@pytest.mark.asyncio
async def test_orchestrator_real_dispatch_produces_artifacts() -> None:
    """一次 orchestrator turn 服务端真产出讲义+题（dispatch_results 含真 artifact 摘要）。"""
    await reset_session(1, "t-real")

    # understand → decompose 走确定性规则（无 LLM payload 也用规则兜底）
    provider = StubProvider(
        payloads=[
            None,  # understand（使用规则兜底）
            None,  # decompose（使用规则兜底）
            {  # StoryboardAgent (watch) — storyboard payload
                "title": "for 循环动画",
                "storyboard": {
                    "scenes": [
                        {
                            "title": "初识 for 循环",
                            "narration": "for 循环用于遍历可迭代对象。",
                            "visual_hint": "列表元素逐个亮起",
                            "kc_codes": ["PY43-KC-001"],
                            "duration_seconds": 15,
                            "evidence_page_id": 101,
                        },
                        {
                            "title": "缩进规则",
                            "narration": "循环体必须缩进。",
                            "visual_hint": "代码缩进高亮",
                            "kc_codes": ["PY43-KC-001"],
                            "duration_seconds": 10,
                            "evidence_page_id": 101,
                        },
                        {
                            "title": "常见错误",
                            "narration": "忘记缩进会导致 IndentationError。",
                            "visual_hint": "红色叉号标记",
                            "kc_codes": ["PY43-KC-001"],
                            "duration_seconds": 12,
                            "evidence_page_id": 101,
                        },
                    ]
                },
            },
            {  # ResourceDesignerAgent (learn) — handout payload
                "title": "for 循环讲义",
                "handout": {
                    "sections": [
                        {
                            "heading": "什么是 for 循环",
                            "paragraphs": ["for 循环用于遍历可迭代对象。"],
                            "page_refs": [{"page_id": 101, "page_no": 12, "chapter_id": 3}],
                        }
                    ]
                },
            },
            {  # PracticeAgent (practice) — practice payload
                "title": "for 循环练习",
                "practice": {
                    "problems": [
                        {
                            "title": "遍历列表",
                            "statement_md": "用 for 循环遍历 [1,2,3] 并打印每个元素。",
                            "hints": ["使用 for x in ..."],
                            "sample_io": [{"input": "", "output": "1\n2\n3"}],
                            "kc_codes": ["PY43-KC-001"],
                            "difficulty": "basic",
                        }
                    ]
                },
            },
        ],
        reply="规划完成。",
    )

    graph = OrchestratorGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )

    response = await graph.run(request_of("我想学 for 循环"))
    assert response.reply_md == "规划完成。"
    assert len(response.dispatch_results) >= 4

    # dispatch_results 应含真产物（非旧式「已将…分派给…」文本）；
    # PathPlannerAgent 对无画像新学生用默认画像冷启动，仍应产出真实路径 artifact。
    for dr in response.dispatch_results:
        if dr.agent == "PathPlannerAgent":
            assert not dr.summary.startswith("已将")
            assert "缺少画像" not in dr.summary
            assert dr.ok is True
            assert dr.artifact is not None
            assert dr.artifact.get("status") in {"SUCCESS", "PARTIAL"}
            assert dr.artifact.get("node_count", 0) > 0
        else:
            assert dr.ok is True
            # 真产物摘要应不含旧式播报前缀
            assert not dr.summary.startswith("已将")


@pytest.mark.asyncio
async def test_orchestrator_stream_emits_start_finish_per_dispatch() -> None:
    """SSE 序列含每个子 agent 的真 start/finish trace（断言 node 名与 elapsed_ms 存在）。"""
    await reset_session(1, "t-stream-dispatch")

    provider = StubProvider(
        payloads=[None, None, None, None, None],
        reply="好的。",
    )

    graph = OrchestratorGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )

    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    await graph.run_streaming(request_of("学 for 循环", "t-stream-dispatch"), emit)

    # 取所有 trace 事件
    traces = [d for (e, d) in events if e == "trace"]

    # 找到所有 dispatch.* 节点
    dispatch_traces = [t for t in traces if t["node"].startswith("dispatch.")]
    assert len(dispatch_traces) >= 8  # 4 agents × 2 (start + finish)

    # 每个 dispatch agent 都有 start 和 finish
    dispatch_nodes: dict[str, list[dict]] = {}
    for t in dispatch_traces:
        dispatch_nodes.setdefault(t["node"], []).append(t)

    for node, node_traces in dispatch_nodes.items():
        phases = [t.get("phase") for t in node_traces]
        assert "start" in phases, f"{node} missing start"
        assert "finish" in phases, f"{node} missing finish"

    # finish 事件有 elapsed_ms
    finish_traces = [t for t in dispatch_traces if t.get("phase") == "finish"]
    for ft in finish_traces:
        assert "elapsed_ms" in ft
        assert "agent_label" in ft


@pytest.mark.asyncio
async def test_dryrun_provider_does_not_crash_dispatch() -> None:
    """DryRun provider 下全链不报错（兜底草稿）。"""
    await reset_session(1, "t-dryrun")

    from pythontopia_graph.llm.provider import DryRunProvider

    graph = OrchestratorGraph(
        provider=DryRunProvider(),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )

    response = await graph.run(request_of("学 for 循环", "t-dryrun"))
    # DryRun 下至少返回合法响应
    assert response.reply_md is not None
    assert isinstance(response.dispatch_results, list)
    # trace 应完整
    assert len(response.trace) > 0


@pytest.mark.asyncio
async def test_dispatcher_provider_error_converts_to_governed_failure() -> None:
    """Provider 异常经 run_with_governance 转 ok=False（summary 带 status），不抛裸异常。"""

    class BoomProvider(LlmProvider):
        async def chat(self, messages, **kwargs):  # type: ignore[override]
            raise RuntimeError("upstream down")

        async def chat_stream(self, messages, **kwargs):  # type: ignore[override]
            raise RuntimeError("upstream down")

    provider = BoomProvider()
    ctx = DispatchContext(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )
    dispatcher = AgentDispatcher(ctx)

    step = PlanStep(
        id="s-learn",
        phase="learn",
        title="for 循环讲义",
        detail="学习 for 循环基础",
        agent="ResourceDesignerAgent",
        kc_codes=["PY43-KC-001"],
        panel="workshop",
        resource_type="HANDOUT",
    )

    # 治理包装：异常转结构化 ok=False 结果，summary 携带观测 status
    result = await dispatcher.dispatch(step, user_id=1)
    assert result.ok is False
    assert "error" in result.summary
    assert result.artifact is not None
    assert result.artifact["kind"] == "dispatch_failure"
    assert result.artifact["error_class"] == "RuntimeError"


@pytest.mark.asyncio
async def test_dispatcher_tutor_receives_profile_brief(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """真画像：DispatchContext.profile_brief 真实透传给 TutorAgent.ask（不再写死空串）。"""
    from pythontopia_graph.agents.tutor_agent import TutorAgent
    from pythontopia_graph.schemas.tutor import (
        GroundingResult,
        SafetyResult,
        TutorResponse,
    )

    captured: dict[str, object] = {}

    async def fake_ask(self, question, profile_brief, conversation_history, evidence_chunks):  # type: ignore[no-untyped-def]
        captured["profile_brief"] = profile_brief
        captured["evidence_count"] = len(evidence_chunks)
        return TutorResponse(
            answer_md="for 循环用于遍历可迭代对象。",
            grounding_result=GroundingResult(passed=True),
            safety_result=SafetyResult(passed=True),
        )

    monkeypatch.setattr(TutorAgent, "ask", fake_ask)

    ctx = DispatchContext(
        provider=StubProvider([]),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
        profile_brief="认知风格: visual; 目标: 期末备考",
    )
    dispatcher = AgentDispatcher(ctx)
    step = PlanStep(
        id="s-ask",
        phase="learn",
        title="for 循环答疑",
        detail="解释 range 的步长用法",
        agent="TutorAgent",
        kc_codes=["PY43-KC-001"],
        panel="workshop",
    )

    result = await dispatcher.dispatch(step, user_id=1)

    assert result.ok is True
    assert captured["profile_brief"] == "认知风格: visual; 目标: 期末备考"
    # evidence_chunks 是真 RagHit 列表（EvidenceBundle.chunks），非 pydantic 字段元组
    assert captured["evidence_count"] == 1
    assert result.artifact is not None
    assert result.artifact["kind"] == "tutor_answer"
    assert result.artifact["evidence_count"] == 1


@pytest.mark.asyncio
async def test_practice_focus_hint_carries_learn_artifact(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """工件链协作：learn 讲义产物（title/summary）注入 practice 步的 focus_hint。"""
    await reset_session(1, "t-chain")

    from pythontopia_graph.agents.handout_agent import HandoutAgent
    from pythontopia_graph.agents.practice_agent import PracticeAgent
    from pythontopia_graph.schemas.resource import (
        HandoutContent,
        HandoutSection,
        PracticeContent,
        ProblemSpec,
        ResourceDraft,
        SampleIo,
    )

    async def fake_handout_design(self, request, evidence, revision_notes=None):  # type: ignore[no-untyped-def]
        return ResourceDraft(
            title="for 循环精讲讲义",
            summary="覆盖 for 语法、range 用法与缩进陷阱",
            content_format="MARKDOWN",
            handout=HandoutContent(
                sections=[
                    HandoutSection(
                        heading="for 循环",
                        paragraphs=["for 循环用于遍历可迭代对象。"],
                    )
                ]
            ),
        )

    captured: dict[str, object] = {}

    async def fake_practice_design(self, request, evidence, revision_notes=None):  # type: ignore[no-untyped-def]
        captured["focus_hint"] = request.focus_hint
        return ResourceDraft(
            title="for 循环练习",
            content_format="STRUCT_JSON",
            practice=PracticeContent(
                problems=[
                    ProblemSpec(
                        title="遍历列表",
                        statement_md="用 for 循环遍历 [1,2,3] 并打印每个元素。",
                        sample_io=[SampleIo(input="", output="1\n2\n3")],
                        kc_codes=["PY43-KC-001"],
                    )
                ]
            ),
        )

    monkeypatch.setattr(HandoutAgent, "design", fake_handout_design)
    monkeypatch.setattr(PracticeAgent, "design", fake_practice_design)

    # understand/decompose 走规则兜底（None payload）→ learn 默认 HANDOUT
    provider = StubProvider([], reply="安排好了。")
    graph = OrchestratorGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )

    response = await graph.run(request_of("我想学 for 循环", "t-chain"))

    focus_hint = str(captured["focus_hint"])
    assert "练习紧扣讲义《for 循环精讲讲义》" in focus_hint
    assert "覆盖 for 语法" in focus_hint  # summary 前 80 字注入
    practice_result = next(
        dr for dr in response.dispatch_results if dr.agent == "PracticeAgent"
    )
    assert practice_result.ok is True


@pytest.mark.asyncio
async def test_orchestrator_node_dispatch_catches_per_step_and_emits_error() -> None:
    """单步 provider 爆炸（经治理转 ok=False）→ SSE step_error 上报（非终止），其余 step 继续。

    单步失败必须用 step_error 而非 error：下游 SSE 中继与前端把 error 视为整轮
    终止事件，若发 error 会截断后续 STATE_DELTA / result，导致整个学习计划丢失。
    """
    await reset_session(1, "t-failfast")

    # understand + decompose 先用 None（规则兜底），dispatch 阶段第一个 agent 爆炸
    call_seq = [0]

    class FlakyProvider(LlmProvider):
        async def chat(self, messages, **kwargs):  # type: ignore[override]
            call_seq[0] += 1
            # understand=1, decompose=2 → None（规则兜底）; dispatch=3 爆炸
            if call_seq[0] >= 3 and call_seq[0] <= 3:
                raise RuntimeError("storyboard generation failed")
            return LlmResponse(content_json=None)

        async def chat_stream(self, messages, **kwargs):  # type: ignore[override]
            for i in range(0, 6, 6):
                yield "测试"

    provider = FlakyProvider()
    graph = OrchestratorGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )

    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    await graph.run_streaming(request_of("学 for 循环", "t-failfast"), emit)

    # 应有 step_error 事件（dispatch 中爆炸的 step 被捕获并转非终止 step_error）
    errors = [d for (e, d) in events if e == "step_error"]
    assert len(errors) >= 1, f"expected >=1 step_error events, got {len(errors)}"
    assert all(d.get("code") == "DISPATCH_FAILED" for d in errors)
    assert all(d.get("step_id") for d in errors), "step_error 必须携带 step_id"

    # 整轮不得出现终止性 error（否则下游会把流截断、计划丢失）
    terminal_errors = [d for (e, d) in events if e == "error"]
    assert terminal_errors == [], f"单步失败不应发终止性 error：{terminal_errors}"

    # result 中 dispatch_results 应含 ok=False 的项
    result_events = [d for (e, d) in events if e == "result"]
    assert len(result_events) == 1
    dispatch_results = result_events[0].get("dispatch_results", [])
    failed = [dr for dr in dispatch_results if not dr.get("ok", True)]
    assert len(failed) >= 1, f"expected >=1 failed dispatch, got {failed}"
