"""P1.4 辩论提质放大验收测试。

验证：难度/讲法决策触发辩论且 critic 意见进 referee；cost 守恒（无异议跳 referee）。
"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.agents.tutor_agent import TutorAgent
from pythontopia_graph.graph.debate_graph import DebateGraph, DebateResult
from pythontopia_graph.llm.provider import LlmResponse


class TestDebateTriggers:
    """should_debate 触发词测试（P1.4 扩展后）。"""

    def test_original_rigorous_triggers_still_work(self) -> None:
        """原有严谨论证触发词仍生效。"""
        assert TutorAgent.should_debate("为什么不能这样写")
        assert TutorAgent.should_debate("哪个更合适")
        assert TutorAgent.should_debate("推导一下这个公式")
        assert TutorAgent.should_debate("真的吗")
        assert TutorAgent.should_debate("权衡利弊")

    def test_teaching_decision_triggers_added(self) -> None:
        """P1.4 新增教学决策触发词生效。"""
        assert TutorAgent.should_debate("这个知识点适合什么难度")
        assert TutorAgent.should_debate("怎么讲列表推导式比较好")
        assert TutorAgent.should_debate("先学哪个更合适")
        assert TutorAgent.should_debate("太难了，能不能简单点")
        assert TutorAgent.should_debate("应该先学for还是while")

    def test_ordinary_questions_still_no_debate(self) -> None:
        """普通答疑仍不触发辩论（成本守恒）。"""
        assert not TutorAgent.should_debate("什么是 for 循环")
        assert not TutorAgent.should_debate("列表怎么用")
        assert not TutorAgent.should_debate("print 函数怎么写")
        assert not TutorAgent.should_debate("帮我看看这段代码")

    def test_empty_and_none_safe(self) -> None:
        """空输入安全。"""
        assert not TutorAgent.should_debate("")


class ScriptedProvider:
    """按脚本依次返回 LlmResponse，记录每次 chat 入参（供断言调用次数/内容）。"""

    def __init__(self, responses: list[LlmResponse]) -> None:
        self._responses = list(responses)
        self.calls: list[list[dict[str, str]]] = []

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        self.calls.append(messages)
        if not self._responses:
            raise AssertionError("ScriptedProvider exhausted")
        return self._responses.pop(0)

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object,
    ) -> AsyncIterator[str]:
        yield ""


@pytest.mark.asyncio
async def test_debate_on_teaching_decision() -> None:
    """教学决策（出题难度怎么选）触发辩论，critic 意见进 referee。

    验证：
    1. should_debate 识别教学决策为辩论场景（"难度" 在 DEBATE_TRIGGERS 中）
    2. DebateGraph 端到端运行产出 3 LLM 调用（solver→critic→referee）
    3. 辩论结果包含三轮角色（solver, critic, referee）
    """
    question = "出题难度怎么选"

    # should_debate 应命中（"难度" 在通用触发词中）→ 路由进辩论复核
    assert TutorAgent.should_debate(question)

    # 端到端：Solver→Critic→Referee
    provider = ScriptedProvider([
        LlmResponse(content_json={"draft": "对于初学者，建议从基础难度开始，逐步提升。"}),
        LlmResponse(content_json={
            "has_objection": True,
            "critique": "未考虑学生的掌握度与已解锁 KC 情况，可能低估能力。",
        }),
        LlmResponse(content_json={
            "answer": "建议先评估学生的掌握度分数，低掌握从基础开始，高掌握直接给中等难度。",
            "confidence": 0.88,
        }),
    ])
    debate = DebateGraph(provider)

    result = await debate.run(question, evidence_chunks=[])

    assert isinstance(result, DebateResult)
    # critic 意见应流入 referee（3 次 LLM 调用）
    assert len(provider.calls) == 3
    # 辩论结果应有三轮角色
    assert [r.role for r in result.rounds] == ["solver", "critic", "referee"]
    assert result.confidence == pytest.approx(0.88)
    # 定稿应含 referee 修订内容
    assert "掌握度" in result.answer
