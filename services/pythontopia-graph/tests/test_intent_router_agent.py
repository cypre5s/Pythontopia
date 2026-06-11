"""IntentRouterAgent（百晓工坊意图路由）单元测试。

R-16 升级覆盖：注册表目录化 prompt、多轮上下文注入、确定性预判（FIX_BUG/EXPLAIN_CODE）、
7 新工具关键词兜底、默认兜底 ASK、情绪倾斜 REVIEW、target/secondary_intent 校验、
quiz scope 收缩、provider 失败经 harness 降级仍结构化返回。
"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.agents.intent_router_agent import (
    ALL_INTENTS,
    RESOURCE_INTENTS,
    IntentRouterAgent,
)
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.intent import (
    CourseKc,
    IntentRouteRequest,
    IntentTurn,
    SessionCorrection,
)
from pythontopia_graph.tools.harness import reset_harness_state
from pythontopia_graph.tools.registry import ALL_TOOL_NAMES


@pytest.fixture(autouse=True)
def _isolate_harness_state():
    """隔离进程级熔断器/统计：避免本文件触发的 provider 熔断污染其它测试。"""
    reset_harness_state()
    yield
    reset_harness_state()


class StubProvider(LlmProvider):
    """chat() 返回预设 content_json；空则 None。"""

    def __init__(self, payload: dict | None = None) -> None:
        self._payload = payload
        self.calls = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.calls += 1
        return LlmResponse(content_json=self._payload)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


class RaisingProvider(LlmProvider):
    """chat() 永远抛异常：驱动 harness 降级路径。"""

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        raise RuntimeError("provider down")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        raise RuntimeError("provider down")
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


def test_all_intents_aligned_with_registry() -> None:
    """agent 常量与注册表/IntentKind 同源：16 工具不漏不多。"""
    assert ALL_INTENTS == ALL_TOOL_NAMES
    assert len(ALL_INTENTS) == 16


# 确定性预判（LLM 之前，零成本）


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("message", "expected"),
    [
        ("跑出来报错了```python\nprint(1+'2')\n```", "FIX_BUG"),
        ("Traceback 看下```python\nint('a')\n```", "FIX_BUG"),
        ("这段什么意思```python\nx=[i for i in range(3)]\n```", "EXPLAIN_CODE"),
        ("帮我看看这段```python\na, b = b, a\n```", "EXPLAIN_CODE"),
    ],
)
async def test_deterministic_preroute_skips_llm(message: str, expected: str) -> None:
    provider = StubProvider({"intent": "ASK"})
    agent = IntentRouterAgent(provider)

    resp = await agent.route(request_of(message))

    assert resp.intent == expected
    assert resp.confidence == pytest.approx(0.95)
    assert provider.calls == 0  # 预判命中即直接返回，不调 LLM


@pytest.mark.asyncio
async def test_fence_with_both_signals_prefers_fix_bug() -> None:
    """围栏同时含报错与解释诉求时报错优先（修复诉求更紧急）。"""
    provider = StubProvider(None)
    agent = IntentRouterAgent(provider)

    resp = await agent.route(request_of("报错了看不懂```python\nopen('x')\n```"))

    assert resp.intent == "FIX_BUG"
    assert provider.calls == 0


@pytest.mark.asyncio
async def test_fence_without_signals_still_calls_llm() -> None:
    """只有围栏、无报错/解释特征 → 不预判，照常走 LLM。"""
    provider = StubProvider({"intent": "CODE_LAB", "confidence": 0.8})
    agent = IntentRouterAgent(provider)

    resp = await agent.route(request_of("```python\nprint('hi')\n```"))

    assert resp.intent == "CODE_LAB"
    assert provider.calls == 1


# 关键词兜底（LLM 输出无效时）


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
        # ── R-16 新工具兜底 ──
        ("考考我循环", "ASKME"),
        ("检验一下我学得怎么样", "ASKME"),
        ("帮我规划一下学习路线", "PLAN"),
        ("我哪里薄弱", "PROGRESS"),
        ("找找我之前的练习", "MY_STUDY"),
        ("我的讲义放哪了", "MY_STUDY"),
        # ── 默认兜底从 HANDOUT 改为 ASK ──
        ("随便看看列表", "ASK"),
    ],
)
async def test_keyword_fallback_when_provider_empty(message: str, expected: str) -> None:
    agent = IntentRouterAgent(StubProvider(None))

    resp = await agent.route(request_of(message))

    assert resp.intent == expected
    assert resp.intent in ALL_INTENTS
    assert resp.confidence <= 0.5  # 兜底判定信心不超过 0.5


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("message", "target"),
    [
        ("带我去图书馆", "library"),
        ("带我去闯关塔", "arena"),
        ("我想去竞技场", "arena"),
        ("带我去错题矿洞", "bugmine"),
        ("我想去小镇逛逛", "world"),
        ("带我去工坊", "workshop"),
    ],
)
async def test_keyword_fallback_navigate_with_target(message: str, target: str) -> None:
    agent = IntentRouterAgent(StubProvider(None))

    resp = await agent.route(request_of(message))

    assert resp.intent == "NAVIGATE"
    assert resp.target == target


@pytest.mark.asyncio
async def test_frustration_without_other_demands_tilts_review() -> None:
    """挫败语气且无其它明确诉求 → REVIEW，reason 标注情绪信号。"""
    agent = IntentRouterAgent(StubProvider(None))

    resp = await agent.route(request_of("太难了，我学不会"))

    assert resp.intent == "REVIEW"
    assert "情绪信号" in resp.reason


@pytest.mark.asyncio
async def test_frustration_with_explicit_demand_keeps_demand() -> None:
    """挫败语气但有明确诉求（出题）→ 诉求优先，不被情绪覆盖。"""
    agent = IntentRouterAgent(StubProvider(None))

    resp = await agent.route(request_of("太难了，再给我出几道练习题吧"))

    assert resp.intent == "PRACTICE"


@pytest.mark.asyncio
async def test_provider_failure_degrades_via_harness_to_keyword_route() -> None:
    """provider 抛异常 → run_with_governance 降级关键词路由，仍结构化返回。"""
    agent = IntentRouterAgent(RaisingProvider())

    resp = await agent.route(request_of("考考我 while 循环"))

    assert resp.intent == "ASKME"
    assert resp.confidence <= 0.5
    assert resp.intent in ALL_INTENTS


# 指代消解兜底（多轮上下文）


@pytest.mark.asyncio
async def test_continuation_reuses_last_intent_and_kcs() -> None:
    agent = IntentRouterAgent(StubProvider(None))

    resp = await agent.route(
        request_of(
            "再来几道",
            last_intent="PRACTICE",
            last_kc_codes=["PY43-KC-001"],
        )
    )

    assert resp.intent == "PRACTICE"
    assert resp.kc_codes == ["PY43-KC-001"]


@pytest.mark.asyncio
async def test_rephrase_routes_to_ask_with_last_kcs() -> None:
    agent = IntentRouterAgent(StubProvider(None))

    resp = await agent.route(
        request_of(
            "换个方式讲",
            last_intent="HANDOUT",
            last_kc_codes=["PY43-KC-003"],
        )
    )

    assert resp.intent == "ASK"
    assert resp.kc_codes == ["PY43-KC-003"]


# target / secondary_intent 校验


@pytest.mark.asyncio
async def test_navigate_target_kept_when_valid() -> None:
    payload = {"intent": "NAVIGATE", "target": "arena", "confidence": 0.9}
    agent = IntentRouterAgent(StubProvider(payload))

    resp = await agent.route(request_of("带我去闯关塔"))

    assert resp.intent == "NAVIGATE"
    assert resp.target == "arena"


@pytest.mark.asyncio
async def test_navigate_target_cleared_when_out_of_catalog() -> None:
    payload = {"intent": "NAVIGATE", "target": "moon_base", "confidence": 0.9}
    agent = IntentRouterAgent(StubProvider(payload))

    resp = await agent.route(request_of("带我去月球"))

    assert resp.intent == "NAVIGATE"
    assert resp.target == ""  # 越界地标必须清空


@pytest.mark.asyncio
async def test_target_cleared_for_non_navigate_intent() -> None:
    payload = {"intent": "ASK", "target": "arena", "confidence": 0.9}
    agent = IntentRouterAgent(StubProvider(payload))

    resp = await agent.route(request_of("什么是闭包"))

    assert resp.target == ""


@pytest.mark.asyncio
async def test_secondary_intent_kept_when_in_scope() -> None:
    payload = {"intent": "ASK", "secondary_intent": "PRACTICE", "confidence": 0.9}
    agent = IntentRouterAgent(StubProvider(payload))

    resp = await agent.route(request_of("讲讲循环再出两道题"))

    assert resp.intent == "ASK"
    assert resp.secondary_intent == "PRACTICE"


@pytest.mark.asyncio
async def test_secondary_intent_dropped_when_invalid_or_same() -> None:
    agent = IntentRouterAgent(
        StubProvider({"intent": "ASK", "secondary_intent": "TIME_TRAVEL"})
    )
    resp = await agent.route(request_of("讲讲循环"))
    assert resp.secondary_intent is None

    agent = IntentRouterAgent(StubProvider({"intent": "ASK", "secondary_intent": "ASK"}))
    resp = await agent.route(request_of("讲讲循环"))
    assert resp.secondary_intent is None


@pytest.mark.asyncio
async def test_secondary_intent_dropped_when_outside_quiz_scope() -> None:
    payload = {"intent": "ASK", "secondary_intent": "PRACTICE", "confidence": 0.9}
    agent = IntentRouterAgent(StubProvider(payload))

    resp = await agent.route(request_of("讲讲这个再出题", scope="quiz"))

    assert resp.intent == "ASK"
    assert resp.secondary_intent is None  # PRACTICE 不在 quiz 工具集


# quiz scope 收缩


@pytest.mark.asyncio
async def test_quiz_scope_clamps_out_of_scope_intent_to_ask() -> None:
    payload = {"intent": "HANDOUT", "confidence": 0.9, "reason": "想生成讲义"}
    agent = IntentRouterAgent(StubProvider(payload))

    resp = await agent.route(request_of("给我整理份讲义", scope="quiz"))

    assert resp.intent == "ASK"
    assert "考核模式" in resp.reason


@pytest.mark.asyncio
@pytest.mark.parametrize("intent", ["ASK", "REVIEW", "ASKME"])
async def test_quiz_scope_allows_ask_review_askme(intent: str) -> None:
    """quiz 合法集 = tools_for_scope("quiz") ∪ {ASKME}（再考一轮合法，退出由前端控制）。"""
    agent = IntentRouterAgent(StubProvider({"intent": intent, "confidence": 0.9}))

    resp = await agent.route(request_of("继续吧", scope="quiz"))

    assert resp.intent == intent


# prompt 组装（目录自动生成 + 多轮上下文注入）


def test_messages_use_registry_catalog_and_inject_context() -> None:
    agent = IntentRouterAgent(StubProvider(None))
    req = request_of(
        "再来几道",
        current_chapter=3,
        manual_kc_codes=["PY43-KC-001"],
        recent_turns=[
            IntentTurn(role="user", content="讲讲 for 循环"),
            IntentTurn(role="assistant", content="for 循环用于遍历……"),
        ],
        last_intent="PRACTICE",
        last_kc_codes=["PY43-KC-001"],
        profile_brief="认知风格: visual; 情绪: 需鼓励",
        session_corrections=[
            SessionCorrection(message="出几道题", corrected_intent="PRACTICE")
        ],
    )

    messages = agent._messages(req)
    joined = "\n".join(m["content"] for m in messages)
    system = messages[0]["content"]

    # 工具目录来自注册表：16 工具名与中文名都在 system 提示里。
    for name in ALL_TOOL_NAMES:
        assert name in system
    assert "考考我" in system and "带路" in system
    # target / secondary_intent schema 提示。
    assert "target" in system and "secondary_intent" in system
    assert "library|arena|bugmine|world|workshop" in system
    # 指代消解 few-shot。
    assert "再来几道" in joined and "换个方式讲" in joined and "画成导图" in joined
    # 多轮上下文注入。
    assert "最近对话" in joined and "for 循环用于遍历" in joined
    assert "上一轮已执行意图：PRACTICE" in joined
    assert "PY43-KC-001" in joined
    assert "认知风格: visual" in joined
    assert "纠错记忆" in joined and "出几道题" in joined
    assert messages[-1] == {"role": "user", "content": "再来几道"}


def test_messages_quiz_scope_shrinks_catalog() -> None:
    agent = IntentRouterAgent(StubProvider(None))

    system = agent._messages(request_of("继续", scope="quiz"))[0]["content"]

    assert '"intent":"ASK|REVIEW"' in system
    assert "考考我" not in system  # quiz 目录不再暴露 ASKME 等工具
    assert "HANDOUT=" not in system


def test_messages_omit_empty_context_blocks() -> None:
    """空上下文不注入冗余 system 消息（首轮干净 prompt）。"""
    agent = IntentRouterAgent(StubProvider(None))

    joined = "\n".join(m["content"] for m in agent._messages(request_of("什么是闭包")))

    assert "多轮上下文（指代消解依据）" not in joined
    assert "画像摘要" not in joined
    assert "纠错记忆" not in joined


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
