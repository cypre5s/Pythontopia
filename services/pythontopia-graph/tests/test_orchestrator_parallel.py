"""WP3 编排并发验收：两批有界并发、step_id 对齐、单步失败不拖垮全局。

dispatch 节点契约：watch/learn/assess 无依赖步骤经 Semaphore(3) 并发执行；
practice 依赖 learn 工件单独成批；SSE trace 逐 step 携带 step_id（顺序可乱、id 对齐）。
"""

from __future__ import annotations

import asyncio
import time
from collections.abc import AsyncIterator, Iterator

import pytest

from pythontopia_graph.agents.storyboard_agent import StoryboardAgent
from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.graph.orchestrator_session import reset_session
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher
from pythontopia_graph.schemas.orchestrator import OrchestratorRequest, PlanStep
from pythontopia_graph.tools.harness import reset_harness_state


@pytest.fixture(autouse=True)
def _clean_harness() -> Iterator[None]:
    """隔离进程级熔断器/统计（dispatch 内 LLM 子调用经治理包装，breaker=provider）。"""
    reset_harness_state()
    yield
    reset_harness_state()


HIT = RagHit(
    chunk_id="c1",
    page_id=101,
    page_no=12,
    chapter_id=3,
    kc_codes=["PY43-KC-001"],
    text="for 循环遍历可迭代对象。",
    score=0.81,
)


class SleepProvider(LlmProvider):
    """每次 chat 睡 delay_s 后返回空 JSON（各 agent 走确定性兜底草稿），用于计时断言。"""

    def __init__(self, delay_s: float) -> None:
        self._delay_s = delay_s
        self.calls = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.calls += 1
        await asyncio.sleep(self._delay_s)
        return LlmResponse(content_json=None)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield "好的。"


def request_of(message: str, session_id: str) -> OrchestratorRequest:
    return OrchestratorRequest(
        user_id=1,
        session_id=session_id,
        user_message=message,
        course_kc_index=["PY43-KC-001", "PY43-KC-003"],
    )


def standard_plan() -> list[PlanStep]:
    """标准看→学→练→测四步计划（watch/learn/practice 各触发一次 LLM 调用）。"""
    return [
        PlanStep(
            id="s1-watch",
            phase="watch",
            title="看 for 循环分镜",
            detail="先建立直观印象",
            agent="StoryboardAgent",
            kc_codes=["PY43-KC-001"],
            panel="lecture",
        ),
        PlanStep(
            id="s2-learn",
            phase="learn",
            title="学 for 循环讲义",
            detail="研读讲义吃透概念",
            agent="ResourceDesignerAgent",
            kc_codes=["PY43-KC-001"],
            panel="library",
            resource_type="HANDOUT",
        ),
        PlanStep(
            id="s3-practice",
            phase="practice",
            title="练 for 循环",
            detail="完成配套练习",
            agent="PracticeAgent",
            kc_codes=["PY43-KC-001"],
            panel="training",
            resource_type="PRACTICE",
        ),
        PlanStep(
            id="s4-assess",
            phase="assess",
            title="测掌握程度",
            detail="正式提交评估",
            agent="PathPlannerAgent",
            kc_codes=["PY43-KC-001"],
            panel="problems",
        ),
    ]


@pytest.mark.asyncio
async def test_dispatch_node_parallel_faster_than_serial() -> None:
    """并发计时：每次 LLM 调用睡 0.2s，3 个独立步骤并发 + practice 随后 → 总耗时 <0.45s。

    串行执行需 ≥0.6s（watch/learn/practice 三次 LLM 调用）；两批有界并发下
    watch∥learn（assess 确定性不调 LLM）约 0.2s + practice 0.2s ≈ 0.4s。
    """
    provider = SleepProvider(0.2)
    graph = OrchestratorGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )
    plan = standard_plan()
    req = request_of("学 for 循环", "t-par-timing")

    started = time.monotonic()
    out = await graph._node_dispatch({"req": req, "plan": plan})
    elapsed = time.monotonic() - started

    assert elapsed < 0.45, f"expected parallel dispatch <0.45s, got {elapsed:.3f}s"
    results = out["dispatch_results"]
    assert len(results) == 4
    # 聚合结果保持 plan 原序（SSE 事件可乱序，结果输出稳定）
    assert [r.step_id for r in results] == [step.id for step in plan]
    # watch + learn + practice 各 1 次 LLM 调用；assess（PathPlanner）确定性不调 LLM
    assert provider.calls == 3


@pytest.mark.asyncio
async def test_dispatch_trace_start_finish_aligned_by_step_id() -> None:
    """SSE trace 逐 step 上报：每个 dispatch step_id 都有 start 与 finish（顺序可乱、id 对齐）。"""
    await reset_session("t-par-trace")
    provider = SleepProvider(0.05)
    graph = OrchestratorGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )

    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    await graph.run_streaming(request_of("学 for 循环", "t-par-trace"), emit)

    dispatch_traces = [
        d
        for (e, d) in events
        if e == "trace" and str(d.get("node", "")).startswith("dispatch.")
    ]
    assert dispatch_traces, "expected dispatch trace events"

    phases_by_step: dict[str, set[str]] = {}
    for trace in dispatch_traces:
        step_id = trace.get("step_id")
        assert step_id, f"dispatch trace missing step_id: {trace}"
        phases_by_step.setdefault(str(step_id), set()).add(str(trace.get("phase")))

    # 规则兜底计划共 4 步，每步 start+finish 成对
    assert len(phases_by_step) == 4
    for step_id, phases in phases_by_step.items():
        assert {"start", "finish"} <= phases, f"{step_id} 缺 start/finish: {phases}"


@pytest.mark.asyncio
async def test_single_step_failure_keeps_other_steps_alive(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """单步抛异常：watch 步爆炸 → 该步 ok=False（summary 带 status）+ SSE error；
    learn/practice 正常完成；PathPlanner 诚实 FAILED 不冒充执行错误。"""
    await reset_session("t-par-fail")

    async def boom_design(self, request, evidence, revision_notes=None):  # type: ignore[no-untyped-def]
        raise RuntimeError("storyboard exploded")

    monkeypatch.setattr(StoryboardAgent, "design", boom_design)

    provider = SleepProvider(0.0)
    graph = OrchestratorGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        valid_kc_codes={"PY43-KC-001"},
    )

    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    await graph.run_streaming(request_of("学 for 循环", "t-par-fail"), emit)

    result_events = [d for (e, d) in events if e == "result"]
    assert len(result_events) == 1
    by_agent = {dr["agent"]: dr for dr in result_events[0]["dispatch_results"]}

    watch = by_agent["StoryboardAgent"]
    assert watch["ok"] is False
    assert "error" in watch["summary"]  # 治理观测 status 进 summary
    # 其余步骤不受单步失败影响
    assert by_agent["ResourceDesignerAgent"]["ok"] is True
    assert by_agent["PracticeAgent"]["ok"] is True

    # 仅执行类失败上报 SSE error；PathPlanner 无画像的诚实 FAILED 不触发 error 事件
    errors = [d for (e, d) in events if e == "error"]
    assert len(errors) == 1, f"expected exactly 1 error event, got {errors}"
    assert errors[0]["agent"] == "StoryboardAgent"
