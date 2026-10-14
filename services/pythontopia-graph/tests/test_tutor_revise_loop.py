"""critique→revise 闭环：审查未过触发恰一轮修订并重跑三审；通过时零额外 LLM 调用。

fake provider 全程离线；预算砍单路径通过 monkeypatch `_MAX_LLM_CALLS` 复现。
"""

from __future__ import annotations

import json
from collections.abc import AsyncIterator, Iterator

import pytest

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher
from pythontopia_graph.routes import tutor as tutor_routes
from pythontopia_graph.schemas.tutor import TutorAskRequest
from pythontopia_graph.tools.harness import reset_harness_state

HIT = RagHit(
    chunk_id="c1",
    page_id=112,
    page_no=12,
    chapter_id=3,
    kc_codes=["PY43-KC-001"],
    text="第 12 页讲解 for 循环遍历列表。",
    score=0.82,
)

GOOD_ANSWER = "参考 [页 12]，for 循环可遍历列表。"
BAD_ANSWER = "参考 [页 99]，for 循环可遍历列表。"  # 99 不在证据页 {12} → grounding 失败


@pytest.fixture(autouse=True)
def _reset_harness() -> Iterator[None]:
    """隔离熔断器/统计：避免本文件的失败计数污染其它测试（反之亦然）。"""
    reset_harness_state()
    yield
    reset_harness_state()


class RecordingProvider(LlmProvider):
    """chat_stream 吐 stream_answer；chat 按 prompt 分派：修订提示 → revise_answer，其余 → 空。"""

    def __init__(self, stream_answer: str, revise_answer: str = "") -> None:
        self.stream_answer = stream_answer
        self.revise_answer = revise_answer
        self.chat_calls: list[str] = []

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        text = "\n".join(m["content"] for m in messages)
        self.chat_calls.append(text)
        if "审查意见" in text:
            return LlmResponse(content_text=self.revise_answer)
        return LlmResponse(content_text="")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        for i in range(0, len(self.stream_answer), 16):
            yield self.stream_answer[i : i + 16]

    def revise_call_count(self) -> int:
        return sum(1 for text in self.chat_calls if "审查意见" in text)


async def _collect_sse(response) -> list[tuple[str, dict]]:
    raw_parts: list[str] = []
    async for chunk in response.body_iterator:
        raw_parts.append(chunk if isinstance(chunk, str) else chunk.decode("utf-8"))
    events: list[tuple[str, dict]] = []
    for block in "".join(raw_parts).split("\n\n"):
        block = block.strip()
        if not block or block.startswith(":"):  # 跳过 SSE 注释行（心跳 ping）
            continue
        event = "message"
        data_lines: list[str] = []
        for line in block.split("\n"):
            if line.startswith("event:"):
                event = line[len("event:"):].strip()
            elif line.startswith("data:"):
                data_lines.append(line[len("data:"):].strip())
        _ev = event
        _payload = json.loads("\n".join(data_lines))
        # 迁移后 tutor 发 AG-UI 官方名；反归一化回历史语义，保持下方断言不变
        # （AG-UI 命名正确性由 test_sse_agui 的 translate 单测覆盖）。
        if _ev in ("STEP_STARTED", "STEP_FINISHED"):
            _ev = "trace"
        elif _ev == "TEXT_MESSAGE_CONTENT":
            _ev = "token"
        elif _ev == "RUN_FINISHED":
            _ev, _payload = "result", _payload.get("result", _payload)
        elif _ev == "RUN_ERROR":
            _ev = "error"
        elif _ev == "CUSTOM":
            _ev, _payload = str(_payload.get("name", "custom")), _payload.get("value", _payload)
        events.append((_ev, _payload))
    return events


async def _run(
    provider: RecordingProvider, profile_brief: str = "",
) -> list[tuple[str, dict]]:
    body = TutorAskRequest(
        user_id=1,
        question="for 循环怎么用",
        kc_codes=["PY43-KC-001"],
        profile_brief=profile_brief,
    )
    response = await tutor_routes.tutor_ask_stream(
        body, provider=provider, evidence_fetcher=StaticEvidenceFetcher([HIT]),
    )
    return await _collect_sse(response)


def _traces(events: list[tuple[str, dict]], node: str) -> list[dict]:
    return [data for (event, data) in events if event == "trace" and data["node"] == node]


async def test_failed_grounding_triggers_one_revise_and_rereviews() -> None:
    provider = RecordingProvider(stream_answer=BAD_ANSWER, revise_answer=GOOD_ANSWER)
    events = await _run(provider, profile_brief="认知风格: visual; 目标: 期末备考")

    # trace：TutorAgent.revise start→finish，修订被采纳。
    revise_traces = _traces(events, "TutorAgent.revise")
    assert [t["phase"] for t in revise_traces] == ["start", "finish"]
    assert revise_traces[-1]["status"] == "ok"
    assert revise_traces[-1]["note"] == "revised"

    # 恰好 1 轮修订；修订 prompt 含原答案与审查意见。
    assert provider.revise_call_count() == 1
    revise_prompt = next(text for text in provider.chat_calls if "审查意见" in text)
    assert BAD_ANSWER in revise_prompt
    assert "答案引用第99页不在证据中" in revise_prompt

    # result：修订稿替换答案，重跑三审后以新结果组装。
    result = events[-1][1]
    assert result["revised"] is True
    assert result["answer_md"] == GOOD_ANSWER
    assert result["grounding_status"] == "PASSED"
    assert result["review"]["grounding"]["passed"] is True
    # personalization_note 同请求落盘（确定性拼接，零 LLM）。
    assert result["personalization_note"] == "本回答按你的【视觉型偏好、期末备考目标】定制"


async def test_passing_reviews_skip_revise_with_zero_extra_llm_calls() -> None:
    provider = RecordingProvider(stream_answer=GOOD_ANSWER)
    events = await _run(provider)

    assert _traces(events, "TutorAgent.revise") == []
    assert provider.revise_call_count() == 0
    # 守恒：除证据注（尾部既有步骤）外无任何额外 chat 调用。
    assert len(provider.chat_calls) == 1
    result = events[-1][1]
    assert result["revised"] is False
    assert result["grounding_status"] == "PASSED"
    assert result["personalization_note"] == ""


async def test_revise_still_failing_keeps_original_answer_and_reviews() -> None:
    # 修订稿引用 [页 98] 仍不在证据页 → 不采纳：保留原答案与原审查结果（答案优先不丢弃）。
    provider = RecordingProvider(
        stream_answer=BAD_ANSWER,
        revise_answer="参考 [页 98]，for 循环可遍历列表。",
    )
    events = await _run(provider)

    revise_traces = _traces(events, "TutorAgent.revise")
    assert revise_traces[-1]["status"] == "retry"
    assert revise_traces[-1]["note"] == "kept_original"
    assert provider.revise_call_count() == 1  # 最多 1 轮，不重复修订

    result = events[-1][1]
    assert result["revised"] is False
    assert result["answer_md"] == BAD_ANSWER
    assert result["grounding_status"] == "FAILED"
    # 原审查结果（页 99 的意见）原样返回，而非修订稿的页 98。
    assert result["review"]["grounding"]["reasons"] == ["答案引用第99页不在证据中"]


async def test_revise_failure_keeps_original_answer(monkeypatch: pytest.MonkeyPatch) -> None:
    # 修订调用抛异常（provider 故障）→ 治理兜住：保留原答案与原审查结果。
    provider = RecordingProvider(stream_answer=BAD_ANSWER)

    async def _boom(messages, **kwargs):  # type: ignore[no-untyped-def]
        raise RuntimeError("provider down")

    monkeypatch.setattr(provider, "chat", _boom)
    events = await _run(provider)

    revise_traces = _traces(events, "TutorAgent.revise")
    assert revise_traces[-1]["note"] == "kept_original"
    result = events[-1][1]
    assert result["revised"] is False
    assert result["answer_md"] == BAD_ANSWER
    assert result["grounding_status"] == "FAILED"


async def test_budget_exhausted_skips_revise_and_tail_steps(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(tutor_routes, "_MAX_LLM_CALLS", 0)
    provider = RecordingProvider(stream_answer=BAD_ANSWER, revise_answer=GOOD_ANSWER)
    events = await _run(provider)

    # 修订被预算砍掉：trace 标 budget_skipped，零 LLM 调用，保留原答案。
    revise_traces = _traces(events, "TutorAgent.revise")
    assert revise_traces[-1]["status"] == "skipped"
    assert revise_traces[-1]["note"] == "budget_skipped"
    assert provider.chat_calls == []

    # 证据注同样被砍（有证据场景下它本会调用 LLM）。
    note_traces = _traces(events, "tutor_evidence_note")
    assert note_traces and note_traces[-1]["note"] == "budget_skipped"

    result = events[-1][1]
    assert result["revised"] is False
    assert result["answer_md"] == BAD_ANSWER
    assert result["evidence_note"] == ""
