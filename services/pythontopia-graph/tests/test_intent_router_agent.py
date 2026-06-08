"""IntentRouterAgent（百晓工坊意图路由）单元测试。"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.agents.intent_router_agent import (
    ALL_INTENTS,
    RESOURCE_INTENTS,
    IntentRouterAgent,
)
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.intent import CourseKc, IntentRouteRequest


class StubProvider(LlmProvider):
    """chat() 返回预设 content_json；空则 None。"""

    def __init__(self, payload: dict | None = None) -> None:
        self._payload = payload

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_json=self._payload)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


CATALOG = [
    CourseKc(code="PY43-KC-001", name="for 循环"),
    CourseKc(code="PY43-KC-003", name="while 循环"),
    CourseKc(code="PY43-KC-010", name="列表"),
]


def request_of(message: str, **overrides) -> IntentRouteRequest:
    payload: dict = {"message": message, "course_kcs": CATALOG}
    payload.update(overrides)
    return IntentRouteRequest(**payload)


# LLM 路由


@pytest.mark.asyncio
async def test_llm_intent_used_grounds_kcs_and_filters_bogus() -> None:
    payload = {
        "intent": "mindmap",
        "kc_codes": ["PY43-KC-001", "BOGUS-KC"],
        "focus_hint": "for 循环执行流程",
        "confidence": 0.9,
        "reason": "用户要导图",
    }
    agent = IntentRouterAgent(StubProvider(payload))

    resp = await agent.route(request_of("帮我做个循环导图", manual_kc_codes=["PY43-KC-003"]))

    assert resp.intent == "MINDMAP"
    # 手动 @ 优先，紧跟 grounded 建议；BOGUS-KC 因不在目录被丢弃。
    assert resp.kc_codes == ["PY43-KC-003", "PY43-KC-001"]
    assert resp.focus_hint == "for 循环执行流程"
    assert resp.confidence == pytest.approx(0.9)


@pytest.mark.asyncio
async def test_llm_video_intent_routes_and_grounds_kc() -> None:
    """VIDEO 与 ASK 一样是非生成意图，需被 LLM 路由识别并锚定 KC（用于检索关键词）。"""
    payload = {
        "intent": "video",
        "kc_codes": ["PY43-KC-001"],
        "focus_hint": "for 循环讲解视频",
        "confidence": 0.8,
        "reason": "用户想看视频",
    }
    agent = IntentRouterAgent(StubProvider(payload))

    resp = await agent.route(request_of("给我找点讲 for 循环的视频"))

    assert resp.intent == "VIDEO"
    assert resp.kc_codes == ["PY43-KC-001"]
    assert "VIDEO" in ALL_INTENTS
    assert "VIDEO" not in RESOURCE_INTENTS


@pytest.mark.asyncio
async def test_llm_review_intent_routes_and_grounds_kc() -> None:
    """REVIEW 与 ASK/VIDEO 一样是非生成意图，引导去错题矿洞按到期 KC 复盘，需被 LLM 路由识别。"""
    payload = {
        "intent": "review",
        "kc_codes": ["PY43-KC-003"],
        "focus_hint": "复盘 while 循环",
        "confidence": 0.7,
        "reason": "用户想复习巩固",
    }
    agent = IntentRouterAgent(StubProvider(payload))

    resp = await agent.route(request_of("我想复习一下 while 循环"))

    assert resp.intent == "REVIEW"
    assert resp.kc_codes == ["PY43-KC-003"]
    assert "REVIEW" in ALL_INTENTS
    # REVIEW 不是工坊 6 类资源，不能走生成管线。
    assert "REVIEW" not in RESOURCE_INTENTS


@pytest.mark.asyncio
async def test_confidence_is_clamped_to_unit_interval() -> None:
    agent = IntentRouterAgent(StubProvider({"intent": "ASK", "confidence": 1.7}))

    resp = await agent.route(request_of("什么是闭包"))

    assert resp.intent == "ASK"
    assert resp.confidence == pytest.approx(1.0)


# 关键词兜底


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("message", "expected"),
    [
        ("帮我做个 for 循环的思维导图", "MINDMAP"),
        ("出几道练习题练练手", "PRACTICE"),
        ("给我一段能跑的示例代码", "CODE_LAB"),
        ("来点拓展阅读资料", "READING"),
        ("做个循环的动画分镜脚本", "STORYBOARD"),
        ("我想看看 for 循环的视频讲解", "VIDEO"),
        ("有没有 B站 上讲列表的视频", "VIDEO"),
        ("复习一下快忘了的知识点", "REVIEW"),
        ("帮我复盘一下之前学过的循环", "REVIEW"),
        ("梳理一下循环的讲义", "HANDOUT"),
        ("为什么我的循环会报错", "ASK"),
        ("随便看看列表", "HANDOUT"),
    ],
)
async def test_keyword_fallback_when_provider_empty(message: str, expected: str) -> None:
    agent = IntentRouterAgent(StubProvider(None))

    resp = await agent.route(request_of(message))

    assert resp.intent == expected
    assert resp.intent in ALL_INTENTS
    assert resp.confidence <= 0.5  # 兜底判定信心不超过 0.5


@pytest.mark.asyncio
async def test_name_substring_grounds_kc_when_no_manual_or_llm() -> None:
    agent = IntentRouterAgent(StubProvider(None))

    resp = await agent.route(request_of("帮我做个 for 循环的思维导图"))

    assert resp.intent == "MINDMAP"
    # 没有手动 @、LLM 也空时，按目录名子串把「for 循环」锚定到 PY43-KC-001。
    assert resp.kc_codes == ["PY43-KC-001"]


# 手动 KC


@pytest.mark.asyncio
async def test_manual_kc_trusted_when_catalog_absent() -> None:
    agent = IntentRouterAgent(StubProvider(None))

    resp = await agent.route(
        IntentRouteRequest(message="做个讲义", manual_kc_codes=["PY43-KC-077"])
    )

    assert resp.intent == "HANDOUT"
    # 目录为空时手动 @ 的 KC 原样信任（最终生成端会按 DB 再校验）。
    assert resp.kc_codes == ["PY43-KC-077"]


@pytest.mark.asyncio
async def test_focus_hint_falls_back_to_message() -> None:
    agent = IntentRouterAgent(StubProvider({"intent": "HANDOUT"}))

    resp = await agent.route(request_of("讲讲 while 循环的退出条件"))

    assert resp.focus_hint == "讲讲 while 循环的退出条件"
    # RESOURCE_INTENTS 应仍是 6 个，ASK 不在其中。
    assert "ASK" not in RESOURCE_INTENTS
    assert len(RESOURCE_INTENTS) == 6
