"""OrchestratorAgent（学习总管）与 OrchestratorGraph 单元测试。"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.agents.orchestrator_agent import (
    PROFILE_DIMENSION_KEYS,
    RESOURCE_TYPES,
    OrchestratorAgent,
)
from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.graph.orchestrator_session import get_history, reset_session
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.orchestrator import OrchestratorRequest


class StubProvider(LlmProvider):
    """chat() 按队列返回 content_json；chat_stream() 固定分片回复。"""

    def __init__(self, payloads: list[dict | None] | None = None, reply: str = "好的，我们开始。") -> None:
        self._payloads = list(payloads or [])
        self._reply = reply

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        payload = self._payloads.pop(0) if self._payloads else None
        return LlmResponse(content_json=payload)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        for index in range(0, len(self._reply), 6):
            yield self._reply[index : index + 6]


def request_of(message: str, session_id: str, **overrides) -> OrchestratorRequest:
    payload: dict = {
        "user_id": 1,
        "session_id": session_id,
        "user_message": message,
        "course_kc_index": ["PY43-KC-001", "PY43-KC-003"],
    }
    payload.update(overrides)
    return OrchestratorRequest(**payload)


# 任务分解


@pytest.mark.asyncio
async def test_decompose_produces_ordered_watch_learn_practice_assess() -> None:
    agent = OrchestratorAgent(StubProvider([]))
    req = request_of("我想学 for 循环", "t-a", recent_kc_codes=["PY43-KC-003"])

    plan = await agent.decompose(req, "掌握 for 循环")

    assert [step.phase for step in plan] == ["watch", "learn", "practice", "assess"]
    assert [step.agent for step in plan] == [
        "StoryboardAgent",
        "ResourceDesignerAgent",
        "PracticeAgent",
        "PathPlannerAgent",
    ]
    assert all(step.panel for step in plan)
    # KC 锚定仅允许 course_kc_index 内的编码。
    allowed = set(req.course_kc_index)
    assert all(set(step.kc_codes) <= allowed for step in plan)
    # 仅 learn 步携带 resource_type，且须在 RESOURCE_TYPES 内。
    learn = next(step for step in plan if step.phase == "learn")
    assert learn.resource_type in RESOURCE_TYPES
    assert all(step.resource_type is None for step in plan if step.phase != "learn")


@pytest.mark.asyncio
async def test_decompose_uses_llm_fields_and_filters_unknown_kc() -> None:
    payload = {
        "steps": [
            {
                "phase": "learn",
                "title": "自定义学一学",
                "detail": "看导图",
                "kc_codes": ["PY43-KC-001", "BOGUS-KC"],
                "resource_type": "MINDMAP",
            }
        ]
    }
    agent = OrchestratorAgent(StubProvider([payload]))
    req = request_of("学循环", "t-a2")

    plan = await agent.decompose(req, "目标")

    learn = next(step for step in plan if step.phase == "learn")
    assert learn.title == "自定义学一学"
    assert learn.resource_type == "MINDMAP"
    # BOGUS-KC 不在 course_kc_index 内故丢弃。
    assert learn.kc_codes == ["PY43-KC-001"]
    # LLM 缺相时仍强制 watch→learn→practice→assess 顺序。
    assert [step.phase for step in plan] == ["watch", "learn", "practice", "assess"]


@pytest.mark.asyncio
async def test_decaying_kcs_prioritised_in_plan_kc_selection() -> None:
    """记忆矿脉到期的 decaying_kcs 应优先于 recent_kc_codes 成为计划锚定 KC（FSRS → 总管 → 资源）。"""
    agent = OrchestratorAgent(StubProvider([]))  # LLM 空 → 各步回退到 _select_kcs 默认 KC
    req = request_of(
        "复习一下",
        "t-decay",
        course_kc_index=["PY43-KC-001", "PY43-KC-003", "PY43-KC-016"],
        recent_kc_codes=["PY43-KC-003"],
        decaying_kcs=["PY43-KC-016", "BOGUS-KC"],
    )

    plan = await agent.decompose(req, "复盘快遗忘的知识点")

    # 到期 KC 排在最前；目录外的 BOGUS-KC 被过滤。
    for step in plan:
        assert step.kc_codes[0] == "PY43-KC-016"
        assert "BOGUS-KC" not in step.kc_codes


# 目标歧义


@pytest.mark.asyncio
async def test_ambiguous_goal_returns_clarifying_question() -> None:
    await reset_session("t-b")
    understand_payload = {
        "ambiguous": True,
        "clarifying_question": "你想先从哪个主题开始？",
        "goal": "学习 Python",
        "intent": "plan",
        "focus": "",
    }
    graph = OrchestratorGraph(provider=StubProvider([understand_payload], reply="请问你想先学什么？"))

    resp = await graph.run(request_of("我想学编程", "t-b"))

    assert resp.needs_user_input is True
    assert resp.clarifying_question is not None
    assert resp.plan == []
    assert resp.done is False
    assert any(step.node == "understand" for step in resp.trace)


# 失败重规划


@pytest.mark.asyncio
async def test_recent_failures_insert_debug_replan_step_before_assess() -> None:
    await reset_session("t-c")
    graph = OrchestratorGraph(provider=StubProvider([], reply="安排好了"))
    req = request_of("巩固循环", "t-c", recent_failures=["PY43-KC-002"], course_kc_index=["PY43-KC-001", "PY43-KC-002"])

    resp = await graph.run(req)

    debug_steps = [step for step in resp.plan if step.agent == "DebugAgent"]
    assert len(debug_steps) == 1
    assert debug_steps[0].phase == "practice"
    assert "PY43-KC-002" in debug_steps[0].kc_codes
    debug_index = resp.plan.index(debug_steps[0])
    assess_index = next(i for i, step in enumerate(resp.plan) if step.phase == "assess")
    assert debug_index < assess_index
    assert any(step.node == "replan" for step in resp.trace)
    assert any(d.agent == "DebugAgent" for d in resp.dispatch_results)


# 画像抽取


@pytest.mark.asyncio
async def test_extract_profile_returns_signal_with_allowed_dimension() -> None:
    payload = {
        "profile_signals": [
            {"dimension": "learning_goal", "value": "期末考试通过", "confidence": 0.8}
        ]
    }
    agent = OrchestratorAgent(StubProvider([payload]))

    signals = await agent.extract_profile(request_of("我要准备期末考试", "t-d"))

    assert len(signals) == 1
    assert signals[0].dimension in PROFILE_DIMENSION_KEYS
    assert signals[0].dimension == "learning_goal"
    assert 0.0 <= signals[0].confidence <= 1.0


@pytest.mark.asyncio
async def test_extract_profile_heuristic_fallback_on_empty_llm() -> None:
    agent = OrchestratorAgent(StubProvider([]))

    signals = await agent.extract_profile(request_of("这题太难了，我有点想放弃", "t-d2"))

    assert any(signal.dimension == "emotion_persistence" for signal in signals)
    assert all(signal.dimension in PROFILE_DIMENSION_KEYS for signal in signals)


# 多轮记忆


@pytest.mark.asyncio
async def test_multi_turn_accumulates_history_in_session_store() -> None:
    await reset_session("t-e")
    graph = OrchestratorGraph(provider=StubProvider([], reply="收到"))

    await graph.run(request_of("第一句", "t-e", course_kc_index=["PY43-KC-001"]))
    first = await get_history("t-e")
    assert len(first) == 2

    await graph.run(request_of("第二句", "t-e", course_kc_index=["PY43-KC-001"]))
    second = await get_history("t-e")
    assert len(second) == 4
    assert second[0].role == "user"
    assert second[0].content == "第一句"
    assert second[2].content == "第二句"


# 计划协商


@pytest.mark.asyncio
async def test_negotiation_mutates_stored_plan_instead_of_rebuilding() -> None:
    await reset_session("t-f")
    graph = OrchestratorGraph(provider=StubProvider([], reply="好的"))

    first = await graph.run(request_of("系统学 for 循环，准备期末", "t-f"))
    learn_first = next(step for step in first.plan if step.phase == "learn")
    assert learn_first.resource_type == "HANDOUT"
    ids_first = [step.id for step in first.plan]

    second = await graph.run(request_of("太难了", "t-f"))
    ids_second = [step.id for step in second.plan]

    # step id 不变说明就地改写 plan，而非重建。
    assert ids_second == ids_first
    assert len(second.plan) == 4
    learn_second = next(step for step in second.plan if step.phase == "learn")
    assert learn_second.resource_type == "READING"
    assert "降低难度" in learn_second.detail
    node_names = [step.node for step in second.trace]
    assert "replan" in node_names
    assert "decompose" not in node_names
