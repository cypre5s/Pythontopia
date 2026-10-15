from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.graph.debate_graph import DebateGraph, DebateResult
from pythontopia_graph.llm.provider import LlmResponse


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


class BoomProvider:
    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        raise RuntimeError("upstream down")

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object,
    ) -> AsyncIterator[str]:
        yield ""


@pytest.mark.asyncio
async def test_critic_objection_triggers_referee_revision() -> None:
    provider = ScriptedProvider([
        LlmResponse(content_json={"draft": "草答：用 for 循环遍历"}),
        LlmResponse(content_json={"has_objection": True, "critique": "漏了空列表边界"}),
        LlmResponse(content_json={"answer": "修订：先判断空列表再遍历", "confidence": 0.86}),
    ])
    debate = DebateGraph(provider)

    result = await debate.run("为什么这段会越界", evidence_chunks=[])

    assert isinstance(result, DebateResult)
    assert result.answer == "修订：先判断空列表再遍历"
    assert [r.role for r in result.rounds] == ["solver", "critic", "referee"]
    assert result.confidence == pytest.approx(0.86)
    assert len(provider.calls) == 3


@pytest.mark.asyncio
async def test_no_objection_adopts_solver_without_third_call() -> None:
    provider = ScriptedProvider([
        LlmResponse(content_json={"draft": "草答：列表是有序可变序列"}),
        LlmResponse(content_json={"has_objection": False, "critique": ""}),
    ])
    debate = DebateGraph(provider)

    result = await debate.run("什么是列表", evidence_chunks=[])

    assert result is not None
    assert result.answer == "草答：列表是有序可变序列"
    assert [r.role for r in result.rounds] == ["solver", "critic", "referee"]
    assert result.confidence >= 0.8
    # critic 无异议时 referee 直接采纳 solver，不再消耗一次 LLM 调用。
    assert len(provider.calls) == 2


@pytest.mark.asyncio
async def test_any_step_failure_falls_back_to_none() -> None:
    debate = DebateGraph(BoomProvider())

    result = await debate.run("任意问题", evidence_chunks=[])

    assert result is None


@pytest.mark.asyncio
async def test_solver_keeps_profile_and_courseware_out_of_system_messages() -> None:
    from pythontopia_graph.nodes.evidence_fetcher import RagHit

    profile_sentinel = "PROFILE_INJECTION_SENTINEL"
    evidence_sentinel = "COURSEWARE_INJECTION_SENTINEL"
    provider = ScriptedProvider([LlmResponse(content_json={"draft": "草答"})])
    debate = DebateGraph(provider)
    evidence = RagHit(
        chunk_id="c1", page_id=1, page_no=1, text=evidence_sentinel, score=0.9,
    )

    await debate._solver("解释循环", [evidence], profile_sentinel)

    messages = provider.calls[0]
    system_text = "\n".join(m["content"] for m in messages if m["role"] == "system")
    user_text = "\n".join(m["content"] for m in messages if m["role"] == "user")
    assert profile_sentinel not in system_text
    assert evidence_sentinel not in system_text
    assert profile_sentinel in user_text
    assert evidence_sentinel in user_text
