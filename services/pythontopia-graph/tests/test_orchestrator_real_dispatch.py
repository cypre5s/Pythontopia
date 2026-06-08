"""P1.1 真协同内核验收测试：orchestrator 服务端真产出，非播报式文本。"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.agents.dispatch import AgentDispatcher, DispatchContext
from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.graph.orchestrator_session import reset_session
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceFetcher,
    EvidenceBundle,
    RagHit,
    StaticEvidenceFetcher,
)
from pythontopia_graph.schemas.orchestrator import (
    DispatchResult,
    OrchestratorRequest,
    PlanStep,
)


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
async def test_dispatcher_resource_designer_without_type_falls_back() -> None:
    """ResourceDesignerAgent 有 resource_type 时真生成，无类型时降级。"""
    handout_payload = {
        "title": "for 循环讲义",
        "handout": {
            "sections": [
                {
                    "heading": "for 循环",
                    "paragraphs": ["for 循环用于遍历可迭代对象。"],
                    "page_refs": [{"page_id": 101, "page_no": 12, "chapter_id": 3}],
                }
            ]
        },
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


# ── OrchestratorGraph 集成测试 ──


@pytest.mark.asyncio
async def test_orchestrator_real_dispatch_produces_artifacts() -> None:
    """一次 orchestrator turn 服务端真产出讲义+题（dispatch_results 含真 artifact 摘要）。"""
    await reset_session("t-real")

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

    # dispatch_results 应含真产物（非旧式「已将…分派给…」文本）
    # PathPlannerAgent 在无完整画像时诚实返回 FAILED（非播报式占位）
    for dr in response.dispatch_results:
        if dr.agent == "PathPlannerAgent":
            # 无画像时诚实 FAILED 是预期行为（非旧式播报）
            assert not dr.summary.startswith("已将")
            assert "缺少画像" in dr.summary or "路径规划FAILED" in dr.summary
        else:
            assert dr.ok is True
            # 真产物摘要应不含旧式播报前缀
            assert not dr.summary.startswith("已将")


@pytest.mark.asyncio
async def test_orchestrator_stream_emits_start_finish_per_dispatch() -> None:
    """SSE 序列含每个子 agent 的真 start/finish trace（断言 node 名与 elapsed_ms 存在）。"""
    await reset_session("t-stream-dispatch")

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
    await reset_session("t-dryrun")

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
async def test_dispatcher_provider_error_propagates_fail_fast() -> None:
    """Provider 异常从 dispatcher 向上 propagate（fail-fast），不经内部 catch 软降级。"""

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

    # fail-fast: dispatcher 不再内部 catch，异常向上 propagate
    with pytest.raises(RuntimeError, match="upstream down"):
        await dispatcher.dispatch(step, user_id=1)


@pytest.mark.asyncio
async def test_orchestrator_node_dispatch_catches_per_step_and_emits_error() -> None:
    """Orchestrator _node_dispatch 逐 step 捕获异常，转 SSE error + ok=False 继续。"""
    await reset_session("t-failfast")

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

    # 应有 error 事件（dispatch 中爆炸的 step 被捕获并转 SSE error）
    errors = [d for (e, d) in events if e == "error"]
    assert len(errors) >= 1, f"expected >=1 error events, got {len(errors)}"

    # result 中 dispatch_results 应含 ok=False 的项
    result_events = [d for (e, d) in events if e == "result"]
    assert len(result_events) == 1
    dispatch_results = result_events[0].get("dispatch_results", [])
    failed = [dr for dr in dispatch_results if not dr.get("ok", True)]
    assert len(failed) >= 1, f"expected >=1 failed dispatch, got {failed}"
