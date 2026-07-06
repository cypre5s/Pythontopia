"""IntentRouterAgent（百晓工坊意图路由）单元测试。

R-16 升级覆盖：注册表目录化 prompt、多轮上下文注入、确定性预判（FIX_BUG/EXPLAIN_CODE）、
7 新工具关键词兜底、默认兜底 ASK、情绪倾斜 REVIEW、target/secondary_intent 校验、
quiz scope 收缩、provider 失败经 harness 降级仍结构化返回。
"""

from __future__ import annotations

from collections.abc import AsyncIterator, Generator

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
def _isolate_harness_state() -> Generator[None, None, None]:
    """隔离进程级熔断器/统计：避免本文件触发的 provider 熔断污染其它测试。"""
    reset_harness_state()
    yield
    reset_harness_state()


class StubProvider(LlmProvider):
    """chat() 返回预设 content_json；空则 None。"""

    def __init__(self, payload: dict[str, object] | None = None) -> None:
        self._payload = payload
        self.calls = 0

    async def chat(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> LlmResponse:
        self.calls += 1
        return LlmResponse(content_json=self._payload)

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        yield ""


class RaisingProvider(LlmProvider):
    """chat() 永远抛异常：驱动 harness 降级路径。"""

    async def chat(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> LlmResponse:
        raise RuntimeError("provider down")

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object
    ) -> AsyncIterator[str]:
        raise RuntimeError("provider down")
        yield ""


CATALOG = [
    CourseKc(code="PY43-KC-001", name="for 循环"),
    CourseKc(code="PY43-KC-003", name="while 循环"),
    CourseKc(code="PY43-KC-010", name="列表"),
]


def request_of(message: str, **overrides: object) -> IntentRouteRequest:
    payload: dict[str, object] = {"message": message, "course_kcs": CATALOG}
    payload.update(overrides)
    return IntentRouteRequest.model_validate(payload)


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


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("message", "llm_intent", "expected"),
    [
        ("整理一份 input/print 的常见坑清单", "ASK", "HANDOUT"),
        ("给列表切片做一个可视化说明稿", "ASK", "HANDOUT"),
        ("做个海报式总结：Python 数据类型", "ASK", "HANDOUT"),
        ("我想做一次课前自测", "PRACTICE", "ASKME"),
        ("做一个不计分的小 quiz", "PRACTICE", "ASKME"),
        ("让我自己回答几题递归基础", "PRACTICE", "ASKME"),
        ("有没有我反复提交失败的题", "ASKME", "MY_STUDY"),
        ("我的最近一次训练场提交结果是什么", "ASK", "MY_STUDY"),
        ("带我到公告板", "ASK", "NAVIGATE"),
        ("我想去个人空间", "MY_STUDY", "NAVIGATE"),
    ],
)
async def test_boundary_policy_corrects_common_near_miss_intents(
    message: str, llm_intent: str, expected: str
) -> None:
    agent = IntentRouterAgent(
        StubProvider({"intent": llm_intent, "confidence": 0.82, "reason": "LLM raw route"})
    )

    resp = await agent.route(request_of(message))

    assert resp.intent == expected
    assert "边界裁决" in resp.reason


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("message", "llm_intent", "expected"),
    [
        ("不要生成资料，直接解释一下 lambda 是什么", "HANDOUT", "ASK"),
        ("别给讲义，直接测文件读写", "HANDOUT", "ASKME"),
        ("不要看历史，直接现场考我模块基础", "MY_STUDY", "ASKME"),
        ("打开学习路径界面", "MY_STUDY", "NAVIGATE"),
        ("导航到自测入口", "ASKME", "NAVIGATE"),
        ("打开我的资料夹页面", "MY_STUDY", "NAVIGATE"),
        ("用材料形式讲清楚类和对象", "ASK", "HANDOUT"),
        ("把 list.sort 与 sorted 做成对比卡", "ASK", "HANDOUT"),
        ("我的错题里有没有 KeyError 相关的", "ASKME", "MY_STUDY"),
        ("我上次自测分数是多少", "ASKME", "MY_STUDY"),
        ("现在就检查短路求值", "ASK", "ASKME"),
        ("做一次推导式转换的即时测验", "PRACTICE", "ASKME"),
        ("有没有异常处理的动画讲解", "STORYBOARD", "VIDEO"),
        ("不用做资料，解释一下对象引用是什么", "HANDOUT", "ASK"),
        ("不要小测，给我讲讲 continue 和 break 的差别", "ASKME", "ASK"),
        ("请产出一张函数返回值排错流程图", "ASK", "HANDOUT"),
        ("请产出一个输入校验模板说明", "ASK", "HANDOUT"),
        ("要准备课堂小测，今晚优先复习什么", "ASKME", "PLAN"),
        ("我想减少低级语法错，下一阶段怎么练", "ASK", "PLAN"),
        ("帮我决定先做切片题还是先看字符串课件", "ASK", "PLAN"),
        ("帮我查看上次自测哪些题没答上来", "ASKME", "MY_STUDY"),
        ("把我未读完的字符串资料列出来", "HANDOUT", "MY_STUDY"),
        ("检查我的掌握度，循环边界是不是低", "ASKME", "MY_STUDY"),
        ("你问我，我来答：异常冒泡", "ASK", "ASKME"),
        ("现场来 4 个 append 返回值判断", "PRACTICE", "ASKME"),
        ("有没有负步长切片动画", "STORYBOARD", "VIDEO"),
        ("给嵌套列表安排两道可动手的小练习，答案先别给", "PLAN", "PRACTICE"),
        ("我刚学到路径字符串，先用一个小例子讲清楚，不要生成资料", "PLAN", "ASK"),
        ("请把路径字符串处理整理成一页新手速记图，不要只口头解释", "PLAN", "HANDOUT"),
        ("查查我关于路径字符串的历史练习和生成资料，不要重新做", "PLAN", "MY_STUDY"),
    ],
)
async def test_boundary_policy_handles_negation_pages_evidence_and_live_checks(
    message: str, llm_intent: str, expected: str
) -> None:
    agent = IntentRouterAgent(
        StubProvider({"intent": llm_intent, "confidence": 0.86, "reason": "LLM raw route"})
    )

    resp = await agent.route(request_of(message))

    assert resp.intent == expected
    assert "边界裁决" in resp.reason


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("message", "llm_intent"),
    [
        ("导航到今天任务面板", "NAVIGATE"),
        ("切到班级任务页面", "NAVIGATE"),
        ("请产出一张函数返回值排错流程图", "HANDOUT"),
        ("我想减少低级语法错，下一阶段怎么练", "PLAN"),
    ],
)
async def test_boundary_policy_confirms_correct_low_confidence_boundary_routes(
    message: str, llm_intent: str
) -> None:
    agent = IntentRouterAgent(
        StubProvider({"intent": llm_intent, "confidence": 0.41, "reason": "LLM raw route"})
    )

    resp = await agent.route(request_of(message))

    assert resp.intent == llm_intent
    assert resp.confidence >= 0.82
    assert not resp.needs_clarification
    assert "边界裁决" in resp.reason


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("message", "last_intent", "expected"),
    [
        ("再来几道", "PRACTICE", "PRACTICE"),
        ("再问我两个", "ASKME", "ASKME"),
        ("继续", "PLAN", "PLAN"),
    ],
)
async def test_context_policy_uses_last_intent_for_beginner_ellipsis(
    message: str, last_intent: str, expected: str
) -> None:
    agent = IntentRouterAgent(
        StubProvider({"intent": "ASK", "confidence": 0.86, "reason": "LLM raw route"})
    )

    resp = await agent.route(
        request_of(message, last_intent=last_intent, last_kc_codes=["PY43-KC-001"])
    )

    assert resp.intent == expected
    assert resp.kc_codes == ["PY43-KC-001"]
    assert "上下文裁决" in resp.reason
    assert not resp.needs_clarification


@pytest.mark.asyncio
@pytest.mark.parametrize("message", ["这个怎么弄", "然后呢", "帮我看看", "不会"])
async def test_context_free_beginner_vague_queries_ask_for_clarification(
    message: str,
) -> None:
    agent = IntentRouterAgent(
        StubProvider({"intent": "ASK", "confidence": 0.92, "reason": "LLM raw route"})
    )

    resp = await agent.route(request_of(message))

    assert resp.intent == "ASK"
    assert resp.needs_clarification
    assert resp.render_mode == "clarification"
    assert resp.reason_code == "missing_required_context"
    assert "模糊提问" in resp.reason


def test_router_prompt_contains_general_robust_understanding_protocol() -> None:
    """鲁棒性不能靠继续补词表：prompt 必须要求模型先做语义规范化与不确定性判断。"""
    agent = IntentRouterAgent(StubProvider(None))

    messages = agent._messages(request_of("先别整那些，我这个咋办"))
    prompt = "\n".join(message["content"] for message in messages)

    assert "鲁棒意图理解协议" in prompt
    assert "normalized_request" in prompt
    assert "candidate_intents" in prompt
    assert "needs_clarification" in prompt
    assert "缺少上下文" in prompt


@pytest.mark.asyncio
async def test_model_uncertainty_forces_clarification_instead_of_hard_guess() -> None:
    """模型发现学生表达词不达意时，应能通过结构化字段触发短澄清。"""
    agent = IntentRouterAgent(
        StubProvider(
            {
                "intent": "HANDOUT",
                "confidence": 0.91,
                "reason": "可能是要资料，但缺少主题和动作",
                "needs_clarification": True,
                "clarification_prompt": "你是想让我讲概念、生成资料，还是带你去某个页面？",
            }
        )
    )

    resp = await agent.route(request_of("那个东西弄一下，别太复杂"))

    assert resp.intent == "HANDOUT"
    assert resp.confidence < 0.45
    assert resp.needs_clarification
    assert resp.render_mode == "clarification"
    assert resp.reason_code == "missing_required_context"
    assert resp.clarification_prompt == "你是想让我讲概念、生成资料，还是带你去某个页面？"


@pytest.mark.asyncio
async def test_strong_boundary_signal_overrides_model_uncertainty() -> None:
    """高精度产品边界命中时，不能被模型自报不确定压成澄清。"""
    agent = IntentRouterAgent(
        StubProvider(
            {
                "intent": "ASKME",
                "confidence": 0.92,
                "reason": "模型担心缺上下文",
                "needs_clarification": True,
                "missing_context": "主题不够完整",
            }
        )
    )

    resp = await agent.route(request_of("文件路径你直接问我，我答，你别先讲答案"))

    assert resp.intent == "ASKME"
    assert resp.confidence >= 0.82
    assert not resp.needs_clarification
    assert resp.render_mode == "answer"
    assert "边界裁决" in resp.reason


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("message", "llm_intent", "expected"),
    [
        ("给我一份循环资辽", "ASK", "HANDOUT"),
        ("帮我找个切片 shipin", "ASK", "VIDEO"),
        ("给我几道 lianxi 题", "ASK", "PRACTICE"),
        ("我上ci自测分数是多少", "ASKME", "MY_STUDY"),
        ("daikai个人空间页面", "MY_STUDY", "NAVIGATE"),
        ("不要生成资辽，讲讲字典默认值", "HANDOUT", "ASK"),
        ("先别出题也别做资辽，列表别名这个我没懂，讲讲就行", "HANDOUT", "ASK"),
        ("现场wen我两个函数闭包", "ASK", "ASKME"),
        ("daikai学习路径页", "PLAN", "NAVIGATE"),
        ("daikai课堂公告页", "ASK", "NAVIGATE"),
        ("daikai视频资源页", "VIDEO", "NAVIGATE"),
    ],
)
async def test_robust_policy_handles_typos_pinyin_and_word_noise(
    message: str, llm_intent: str, expected: str
) -> None:
    agent = IntentRouterAgent(
        StubProvider({"intent": llm_intent, "confidence": 0.84, "reason": "LLM raw route"})
    )

    resp = await agent.route(request_of(message))

    assert resp.intent == expected
    assert "边界裁决" in resp.reason


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "message",
    [
        "列表引用我说不明白，先别做材料，拿一个例子讲懂就行",
        "函数返回先不用整理成东西，就讲一下怎么想",
        "循环边界不要卡片，直接用例子解释",
        "异常处理不是要资料，我就是问它怎么冒泡",
    ],
)
async def test_resource_boundary_uses_general_nearby_negation_scope(
    message: str,
) -> None:
    agent = IntentRouterAgent(
        StubProvider({"intent": "HANDOUT", "confidence": 0.84, "reason": "LLM raw route"})
    )

    resp = await agent.route(request_of(message))

    assert resp.intent == "ASK"
    assert "否定生成资料" in resp.reason


@pytest.mark.asyncio
async def test_resource_boundary_treats_artifact_output_cues_as_strong_signal() -> None:
    agent = IntentRouterAgent(
        StubProvider(
            {
                "intent": "HANDOUT",
                "confidence": 0.91,
                "reason": "模型担心东西这个词太模糊",
                "needs_clarification": True,
                "missing_context": "产物类型不够标准",
            }
        )
    )

    resp = await agent.route(request_of("列表引用给我收成一页能复习的东西，最好别只是聊天回答"))

    assert resp.intent == "HANDOUT"
    assert resp.confidence >= 0.82
    assert not resp.needs_clarification
    assert resp.render_mode == "resource"
    assert "边界裁决" in resp.reason


@pytest.mark.asyncio
async def test_practice_boundary_prefers_generated_exercise_over_live_quiz() -> None:
    agent = IntentRouterAgent(
        StubProvider({"intent": "ASKME", "confidence": 0.88, "reason": "LLM raw route"})
    )

    resp = await agent.route(request_of("输入校验我想自己试两下，给短题，不要直接讲完"))

    assert resp.intent == "PRACTICE"
    assert resp.confidence >= 0.82
    assert not resp.needs_clarification
    assert "边界裁决" in resp.reason


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("message", "last_intent", "expected"),
    [
        ("刚才那个再弄一下", "HANDOUT", "HANDOUT"),
        ("这个继续", "PLAN", "PLAN"),
        ("还是刚刚的，换一批", "PRACTICE", "PRACTICE"),
        ("那个我再答两题", "ASKME", "ASKME"),
    ],
)
async def test_context_policy_resolves_wordless_beginner_followups(
    message: str, last_intent: str, expected: str
) -> None:
    agent = IntentRouterAgent(
        StubProvider({"intent": "ASK", "confidence": 0.82, "reason": "LLM raw route"})
    )

    resp = await agent.route(
        request_of(message, last_intent=last_intent, last_kc_codes=["PY43-KC-010"])
    )

    assert resp.intent == expected
    assert resp.kc_codes == ["PY43-KC-010"]
    assert "上下文裁决" in resp.reason
    assert not resp.needs_clarification


def test_all_intents_aligned_with_registry() -> None:
    """agent 常量与注册表/IntentKind 同源：17 工具不漏不多。"""
    assert ALL_INTENTS == ALL_TOOL_NAMES
    assert len(ALL_INTENTS) == 17


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
async def test_browse_preroute_uses_find_metadata_and_browse_renderer() -> None:
    provider = StubProvider(None)
    agent = IntentRouterAgent(provider)

    resp = await agent.route(request_of("查一下 Python 官方文档 sorted 稳定性"))

    assert resp.intent == "BROWSE"
    assert resp.namespace == "find"
    assert resp.risk_class == "navigational"
    assert resp.render_mode == "browse"
    assert "BROWSE" in resp.candidate_intents
    assert provider.calls == 0


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
    assert resp.confidence == 0.78
    assert resp.reason_code == "fallback"
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

    # 工具目录来自注册表：17 工具名与中文名都在 system 提示里。
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
