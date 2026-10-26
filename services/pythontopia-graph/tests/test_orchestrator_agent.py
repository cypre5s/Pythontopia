"""OrchestratorAgent（学习总管）与 OrchestratorGraph 单元测试。"""

from __future__ import annotations

from collections.abc import AsyncIterator, Iterator

import pytest

from pythontopia_graph.agents.orchestrator_agent import (
    PROFILE_DIMENSION_KEYS,
    RESOURCE_TYPES,
    OrchestratorAgent,
)
from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.graph.orchestrator_session import get_history, reset_session
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.orchestrator import OrchestratorRequest, OrchestratorTurn, PlanStep
from pythontopia_graph.tools.harness import reset_harness_state


@pytest.fixture(autouse=True)
def _clean_harness() -> Iterator[None]:
    """隔离进程级熔断器/统计（dispatch 内 LLM 子调用经治理包装）。"""
    reset_harness_state()
    yield
    reset_harness_state()


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


def test_dynamic_profile_and_previous_model_text_stay_out_of_system_messages() -> None:
    agent = OrchestratorAgent(StubProvider([]))
    profile = "PROFILE_INJECTION_SENTINEL"
    clarify = "CLARIFICATION_INJECTION_SENTINEL"
    plan_detail = "PLAN_INJECTION_SENTINEL"
    req = request_of("继续", "t-prompt", profile_brief=profile)

    message_sets = [
        agent._understand_messages(req),
        agent._decompose_messages(req, "GOAL_INJECTION_SENTINEL", []),
        agent._reply_messages(req, [], clarify),
        agent._reply_messages(
            req,
            [PlanStep(
                id="step-1", phase="learn", title="讲解", detail=plan_detail,
                agent="ResourceDesignerAgent",
            )],
            None,
        ),
    ]

    for messages in message_sets:
        system_text = "\n".join(m["content"] for m in messages if m["role"] == "system")
        user_text = "\n".join(m["content"] for m in messages if m["role"] == "user")
        assert profile not in system_text
        assert profile in user_text
    clarify_system = "\n".join(
        m["content"] for m in message_sets[-2] if m["role"] == "system"
    )
    clarify_user = "\n".join(
        m["content"] for m in message_sets[-2] if m["role"] == "user"
    )
    assert clarify not in clarify_system
    assert clarify in clarify_user
    plan_system = "\n".join(
        m["content"] for m in message_sets[-1] if m["role"] == "system"
    )
    plan_user = "\n".join(
        m["content"] for m in message_sets[-1] if m["role"] == "user"
    )
    assert plan_detail not in plan_system
    assert plan_detail in plan_user


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
async def test_chat_json_logs_only_provider_exception_type(caplog) -> None:
    class BrokenProvider(StubProvider):
        async def chat(self, messages, **kwargs):  # type: ignore[override]
            raise RuntimeError("student-query-secret-sentinel")

    agent = OrchestratorAgent(BrokenProvider())

    result = await agent._chat_json([{"role": "user", "content": "private question"}])

    assert result == {}
    assert "RuntimeError" in caplog.text
    assert "student-query-secret-sentinel" not in caplog.text


@pytest.mark.asyncio
async def test_run_streaming_does_not_return_exception_text(monkeypatch) -> None:
    graph = OrchestratorGraph(provider=StubProvider([]))

    async def fail_invoke(req, emit):
        raise RuntimeError("student-content-secret-sentinel")

    monkeypatch.setattr(graph, "_invoke", fail_invoke)
    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    await graph.run_streaming(request_of("学循环", "secret-error"), emit)

    assert events == [
        (
            "error",
            {"code": "ORCHESTRATOR_FAILED", "message": "The learning workflow failed."},
        )
    ]


@pytest.mark.asyncio
async def test_dispatch_failure_does_not_return_exception_text(monkeypatch) -> None:
    graph = OrchestratorGraph(provider=StubProvider([]))

    async def fail_dispatch(step, user_id, focus_hint_extra):
        raise RuntimeError("dispatch-content-secret-sentinel")

    monkeypatch.setattr(graph, "_dispatch_step", fail_dispatch)
    response = await graph.run(request_of("学循环", "dispatch-secret"))
    serialized = str(response.model_dump(mode="json"))

    assert "dispatch-content-secret-sentinel" not in serialized
    assert all(not item.ok or "执行失败" in item.summary for item in response.dispatch_results)


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
    await reset_session(1, "t-b")
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
    await reset_session(1, "t-c")
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
async def test_extract_profile_heuristic_does_not_infer_emotion_from_difficulty_feedback() -> None:
    agent = OrchestratorAgent(StubProvider([]))

    signals = await agent.extract_profile(request_of("这题太难了，我有点想放弃", "t-d2"))

    assert all(signal.dimension != "emotion_persistence" for signal in signals)
    assert all(signal.dimension in PROFILE_DIMENSION_KEYS for signal in signals)


# 真画像透传（请求 → DispatchContext → 子 agent）


@pytest.mark.asyncio
async def test_request_profile_brief_flows_into_tutor_dispatch(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """OrchestratorRequest.profile_brief 经 DispatchContext 注入，TutorAgent 编排步真实收到。"""
    await reset_session(1, "t-pb")

    from pythontopia_graph.agents.tutor_agent import TutorAgent
    from pythontopia_graph.graph.orchestrator_session import set_plan
    from pythontopia_graph.schemas.orchestrator import PlanStep
    from pythontopia_graph.schemas.tutor import (
        GroundingResult,
        SafetyResult,
        TutorResponse,
    )

    captured: dict[str, str] = {}

    async def fake_ask(self, question, profile_brief, conversation_history, evidence_chunks):  # type: ignore[no-untyped-def]
        captured["profile_brief"] = profile_brief
        return TutorResponse(
            answer_md="我们先回顾 range 的用法。",
            grounding_result=GroundingResult(passed=True),
            safety_result=SafetyResult(passed=True),
        )

    monkeypatch.setattr(TutorAgent, "ask", fake_ask)

    # 预存含 TutorAgent 步的 plan；「太难了」触发 negotiate 就地改写并真调度该步
    await set_plan(
        1,
        "t-pb",
        [
            PlanStep(
                id="s1-learn",
                phase="learn",
                title="for 循环答疑",
                detail="解释 range 的步长用法",
                agent="TutorAgent",
                kc_codes=["PY43-KC-001"],
                panel="workshop",
            )
        ],
    )

    graph = OrchestratorGraph(provider=StubProvider([], reply="收到"))
    req = request_of("太难了", "t-pb", profile_brief="认知风格: visual; 情绪: 需鼓励")
    resp = await graph.run(req)

    assert captured["profile_brief"] == "认知风格: visual; 情绪: 需鼓励"
    assert any(d.agent == "TutorAgent" for d in resp.dispatch_results)


# 多轮记忆


@pytest.mark.asyncio
async def test_multi_turn_accumulates_history_in_session_store() -> None:
    await reset_session(1, "t-e")
    graph = OrchestratorGraph(provider=StubProvider([], reply="收到"))

    await graph.run(request_of("第一句", "t-e", course_kc_index=["PY43-KC-001"]))
    first = await get_history(1, "t-e")
    assert len(first) == 2

    await graph.run(request_of("第二句", "t-e", course_kc_index=["PY43-KC-001"]))
    second = await get_history(1, "t-e")
    assert len(second) == 4
    assert second[0].role == "user"
    assert second[0].content == "第一句"
    assert second[2].content == "第二句"


@pytest.mark.asyncio
async def test_cold_start_fallback_history_is_restored_after_graph_turn(monkeypatch) -> None:
    await reset_session(1, "t-cold-start")
    graph = OrchestratorGraph(provider=StubProvider([], reply="继续安排。"))
    original_build_loop_record = graph._build_loop_record
    captured_turn_numbers: list[int] = []

    def capture_turn_number(req, final, response, turn_no):
        captured_turn_numbers.append(turn_no)
        return original_build_loop_record(req, final, response, turn_no)

    monkeypatch.setattr(graph, "_build_loop_record", capture_turn_number)
    req = request_of(
        "继续学习",
        "t-cold-start",
        history=[
            OrchestratorTurn(role="user", content="先学循环"),
            OrchestratorTurn(role="assistant", content="已经安排循环入门"),
        ],
        course_kc_index=["PY43-KC-001"],
    )

    await graph.run(req)

    assert [turn.content for turn in await get_history(1, "t-cold-start")] == [
        "先学循环",
        "已经安排循环入门",
        "继续学习",
        "继续安排。",
    ]
    assert captured_turn_numbers == [1]


# ── 教学风格目录 / packId=43 语境 / 确定性降级（TDD 新增） ──


class BoomProvider(LlmProvider):
    """chat 与 chat_stream 都抛错：模拟 LLM provider 整体不可用。"""

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        raise RuntimeError("student-content-secret-sentinel")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        raise RuntimeError("student-content-secret-sentinel")
        yield  # pragma: no cover - 仅为构成异步生成器


class CapturingStubProvider(StubProvider):
    """记录 chat_stream 收到的 messages，供断言回复 prompt 里的风格指令。"""

    def __init__(self, payloads: list[dict | None] | None = None, reply: str = "好的。") -> None:
        super().__init__(payloads, reply)
        self.stream_messages: list[list[dict[str, str]]] = []

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        self.stream_messages.append(list(messages))
        async for token in super().chat_stream(messages, **kwargs):
            yield token


def turns_of(count: int) -> list[dict[str, str]]:
    """构造 count 轮（user+assistant 成对）对话历史。"""
    history: list[dict[str, str]] = []
    for index in range(count):
        history.append({"role": "user", "content": f"第{index}问"})
        history.append({"role": "assistant", "content": f"第{index}答"})
    return history


def test_prompt_builders_carry_pack43_beginner_context() -> None:
    """四个 prompt 构造器都必须显式锚定 packId=43《Python语言基础》与初学者语境。"""
    agent = OrchestratorAgent(StubProvider([]))
    req = request_of("我想学 for 循环", "t-ctx")
    builders = [
        agent._understand_messages(req),
        agent._decompose_messages(req, "掌握 for 循环", ["PY43-KC-001"]),
        agent._profile_messages(req),
        agent._reply_messages(req, [], None),
    ]
    for messages in builders:
        system_text = " ".join(m["content"] for m in messages if m["role"] == "system")
        assert "packId=43" in system_text
        assert "初学者" in system_text


def test_style_candidates_respect_profile_signals() -> None:
    """只按学生明确的媒介偏好排序，不按认知类型或情绪推断排序。"""
    from pythontopia_graph.agents.orchestrator_agent import style_candidates

    assert style_candidates("情绪: 需鼓励，最近受挫") == style_candidates(None)
    assert style_candidates("认知风格: visual") == style_candidates(None)
    assert style_candidates("认知风格: logical") == style_candidates(None)
    assert style_candidates("认知风格: trial") == style_candidates(None)
    assert style_candidates("偏好: 视频")[0] == "analogy_first"
    assert style_candidates("偏好: 提问,互动")[0] == "question_first"


def test_select_reply_style_bounded_and_adjacent_turns_differ() -> None:
    """风格选择必须落在有界目录内，且相邻两轮不复用同一风格。"""
    from pythontopia_graph.agents.orchestrator_agent import (
        REPLY_STYLE_KEYS,
        select_reply_style,
        style_candidates,
    )

    for brief in (None, "认知风格: visual; 情绪: 需鼓励"):
        styles = [select_reply_style("sess-vary", turn, brief) for turn in range(6)]
        assert all(style in REPLY_STYLE_KEYS for style in styles)
        assert all(styles[i] != styles[i + 1] for i in range(len(styles) - 1))
        # 任意相邻三轮恰好轮转完画像偏好的候选集合。
        assert set(styles[:3]) == set(style_candidates(brief))


def test_reply_prompt_style_directive_rotates_between_adjacent_turns() -> None:
    """回复 prompt 注入恰好一条风格指令；相邻轮次指令不同；不暴露内部模式标识。"""
    from pythontopia_graph.agents.orchestrator_agent import REPLY_STYLE_KEYS

    agent = OrchestratorAgent(StubProvider([]))

    def directive_at(turn: int) -> str:
        req = request_of("继续讲 for 循环", "t-style-rot", history=turns_of(turn))
        messages = agent._reply_messages(req, [], None)
        hits = [
            m["content"]
            for m in messages
            if m["role"] == "system" and m["content"].startswith("本轮讲解节奏")
        ]
        assert len(hits) == 1
        return hits[0]

    d0, d1, d2 = directive_at(0), directive_at(1), directive_at(2)
    assert d0 != d1 and d1 != d2
    for directive in (d0, d1, d2):
        assert not any(key in directive for key in REPLY_STYLE_KEYS)


@pytest.mark.asyncio
async def test_default_clarifying_question_is_single_and_concise() -> None:
    """模糊请求只问一个简洁的澄清问题（恰好一个问号、不超过 60 字）。"""
    agent = OrchestratorAgent(StubProvider([]))

    understanding = await agent.understand(request_of("随便", "t-amb"))

    assert understanding["ambiguous"] is True
    question = understanding["clarifying_question"] or ""
    assert question.count("？") + question.count("?") == 1
    assert len(question) <= 60


@pytest.mark.asyncio
async def test_llm_multi_question_clarifying_condensed_to_one() -> None:
    """LLM 给出多个澄清问题时压缩到第一个，避免连环追问。"""
    payload = {
        "intent": "plan",
        "ambiguous": True,
        "clarifying_question": "你想先学哪个主题？另外你的目标是什么？",
        "goal": "学习 Python",
        "focus": "",
    }
    agent = OrchestratorAgent(StubProvider([payload]))

    understanding = await agent.understand(request_of("帮我", "t-amb2"))

    assert understanding["clarifying_question"] == "你想先学哪个主题？"


@pytest.mark.asyncio
async def test_understand_survives_provider_outage_with_heuristics() -> None:
    """provider 整体不可用时 understand 不抛裸异常，走确定性歧义启发式。"""
    agent = OrchestratorAgent(BoomProvider())

    understanding = await agent.understand(request_of("随便", "t-boom"))

    assert understanding["ambiguous"] is True
    assert understanding["clarifying_question"]


@pytest.mark.asyncio
async def test_fallback_reply_is_structured_teaching_with_pack43() -> None:
    """确定性兜底回复必须是结构化教学内容：含课程语境与四阶段，不伪造课件证据。"""
    from pythontopia_graph.agents.orchestrator_agent import REPLY_STYLE_KEYS

    agent = OrchestratorAgent(StubProvider([]))
    req = request_of("我想学 for 循环", "t-fb")
    plan = await agent.decompose(req, "掌握 for 循环")

    text = agent.fallback_reply(req, plan, None)

    assert "packId=43" in text and "Python语言基础" in text and "初学者" in text
    for label in ("[看]", "[学]", "[练]", "[测]"):
        assert label in text
    # 规划对话没有课件证据，绝不伪造 [页 X] 引用。
    assert "[页" not in text
    # 内部模式标识符不得暴露在面向学生的文本里。
    assert not any(key in text for key in REPLY_STYLE_KEYS)


@pytest.mark.asyncio
async def test_fallback_reply_varies_opening_structure_closing_across_turns() -> None:
    """相邻轮次的兜底回复开场白与收尾不得复用同一句式。"""
    agent = OrchestratorAgent(StubProvider([]))
    base = request_of("我想学 for 循环", "t-fb-vary")
    plan = await agent.decompose(base, "掌握 for 循环")

    def reply_at(turn: int) -> str:
        req = request_of("我想学 for 循环", "t-fb-vary", history=turns_of(turn))
        return agent.fallback_reply(req, plan, None)

    replies = [reply_at(turn) for turn in range(3)]
    openings = [reply.strip().splitlines()[0] for reply in replies]
    closings = [reply.strip().splitlines()[-1] for reply in replies]
    assert openings[0] != openings[1] and openings[1] != openings[2]
    assert closings[0] != closings[1] and closings[1] != closings[2]


def test_fallback_clarify_reply_asks_one_question_without_plan_dump() -> None:
    """歧义轮兜底：只问一个简洁澄清问题，不甩看/学/练/测计划。"""
    agent = OrchestratorAgent(StubProvider([]))
    req = request_of("随便", "t-fb-clar")

    text = agent.fallback_reply(req, [], "你想先从哪个主题入手，比如循环还是函数？")

    assert text.count("？") + text.count("?") == 1
    for label in ("[看]", "[学]", "[练]", "[测]"):
        assert label not in text
    assert len(text) <= 120


@pytest.mark.asyncio
async def test_graph_provider_outage_yields_deterministic_structured_reply(caplog) -> None:
    """LLM provider 不可用：整轮不崩溃，回复为确定性结构化教学内容并在 trace 标注降级。"""
    await reset_session(1, "t-outage")
    graph = OrchestratorGraph(provider=BoomProvider())

    resp = await graph.run(request_of("我想学 for 循环", "t-outage"))

    assert resp.needs_user_input is False
    assert [step.phase for step in resp.plan] == ["watch", "learn", "practice", "assess"]
    assert "packId=43" in resp.reply_md and "初学者" in resp.reply_md
    # 降级必须可观测（trace note），绝不静默。
    assert any(
        step.node == "compose_reply" and (step.note or "").startswith("deterministic_fallback")
        for step in resp.trace
    )
    assert resp.safety_result.passed is True
    assert "RuntimeError" in caplog.text
    assert "student-content-secret-sentinel" not in caplog.text
    assert resp.grounding_result.passed is True


@pytest.mark.asyncio
async def test_graph_outage_fallback_still_intercepts_full_answer_leak() -> None:
    """降级路径同样要过安全审查：兜底文本带出泄答关键词时必须拦截。"""
    await reset_session(1, "t-outage-leak")
    graph = OrchestratorGraph(provider=BoomProvider())

    resp = await graph.run(request_of("请直接给我这道题的完整答案", "t-outage-leak"))

    assert resp.safety_result.passed is False
    assert any("SAFETY_KEYWORD" in reason for reason in resp.safety_result.reasons)


@pytest.mark.asyncio
async def test_streamed_reply_style_active_safety_scan_still_executes() -> None:
    """风格指令生效的正常流式回复，泄答关键词仍被安全审查拦截。"""
    await reset_session(1, "t-style-safety")
    provider = CapturingStubProvider([], reply="这是完整答案：print(42)")
    graph = OrchestratorGraph(provider=provider)

    resp = await graph.run(request_of("我想学 while 循环", "t-style-safety"))

    assert provider.stream_messages, "compose 应通过 chat_stream 出回复"
    reply_messages = provider.stream_messages[-1]
    assert any(
        m["role"] == "system" and m["content"].startswith("本轮讲解节奏")
        for m in reply_messages
    )
    assert resp.safety_result.passed is False
    assert any("SAFETY_KEYWORD" in reason for reason in resp.safety_result.reasons)


# 计划协商


@pytest.mark.asyncio
async def test_negotiation_mutates_stored_plan_instead_of_rebuilding() -> None:
    await reset_session(1, "t-f")
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
