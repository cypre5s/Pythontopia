"""答疑 SSE 流：开场 plan(5 工位) + 馆员→导师→审查官→校对官→安全员 trace + 终态 result。"""

from __future__ import annotations

import json
from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher
from pythontopia_graph.routes.tutor import tutor_ask_stream
from pythontopia_graph.schemas.tutor import TutorAskRequest


class StreamStubProvider(LlmProvider):
    """chat_stream 逐 token 吐出固定答案；chat 仅供 build_diagram 兜底（本测试不触发）。"""

    def __init__(self, answer: str) -> None:
        self._answer = answer

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_text="")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        # 切成几段模拟流式分片。
        mid = len(self._answer) // 2
        for piece in (self._answer[:mid], self._answer[mid:]):
            if piece:
                yield piece


HIT = RagHit(
    chunk_id="c1",
    page_id=112,
    page_no=12,
    chapter_id=3,
    kc_codes=["PY43-KC-001"],
    text="第 12 页讲解 for 循环遍历列表。",
    score=0.82,
)


async def _collect_sse(response) -> list[tuple[str, dict]]:
    raw_parts: list[str] = []
    async for chunk in response.body_iterator:
        raw_parts.append(chunk if isinstance(chunk, str) else chunk.decode("utf-8"))
    raw = "".join(raw_parts)
    events: list[tuple[str, dict]] = []
    for block in raw.split("\n\n"):
        block = block.strip()
        if not block:
            continue
        event = "message"
        data_lines: list[str] = []
        for line in block.split("\n"):
            if line.startswith("event:"):
                event = line[len("event:"):].strip()
            elif line.startswith("data:"):
                data_lines.append(line[len("data:"):].strip())
        events.append((event, json.loads("\n".join(data_lines))))
    return events


async def _run(question: str, kc_codes: list[str], answer: str) -> list[tuple[str, dict]]:
    body = TutorAskRequest(user_id=1, question=question, kc_codes=kc_codes)
    response = await tutor_ask_stream(
        body,
        provider=StreamStubProvider(answer),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
    )
    return await _collect_sse(response)


@pytest.mark.asyncio
async def test_ask_stream_opens_with_five_station_plan() -> None:
    events = await _run("for 循环怎么用", ["PY43-KC-001"], "参考 [页 12]，for 循环可遍历列表。")

    assert events[0][0] == "plan"
    plan = events[0][1]
    assert plan["type"] == "ASK"
    assert [n["kind"] for n in plan["nodes"]] == [
        "evidence",
        "tutor",
        "grounding",
        "hallucination",
        "safety",
    ]


@pytest.mark.asyncio
async def test_ask_stream_runs_all_review_agents() -> None:
    events = await _run("for 循环怎么用", ["PY43-KC-001"], "参考 [页 12]，for 循环可遍历列表。")
    traces = [data for (event, data) in events if event == "trace"]

    # 馆员发完工 trace（带命中段数）。
    evidence = [t for t in traces if t["node"] == "evidence_fetcher"]
    assert evidence and evidence[-1].get("chunks") == 1

    # 导师 + 三道审查都要有 start→finish。
    for node in ("TutorAgent", "grounding_critic", "hallucination_detector", "safety_critic"):
        phases = [t.get("phase") for t in traces if t["node"] == node]
        assert "start" in phases, f"{node} missing start"
        assert "finish" in phases, f"{node} missing finish"


@pytest.mark.asyncio
async def test_ask_stream_tokens_and_result_payload() -> None:
    answer = "参考 [页 12]，for 循环可遍历列表。"
    events = await _run("for 循环怎么用", ["PY43-KC-001"], answer)

    tokens = "".join(d["delta"] for (e, d) in events if e == "token")
    assert tokens == answer

    assert events[-1][0] == "result"
    result = events[-1][1]
    # 双套键：camelCase（前端）+ snake_case（Java 落库）。
    assert result["answerMd"] == answer
    assert result["answer_md"] == answer
    assert result["groundingStatus"] == "PASSED"
    assert result["grounding_status"] == "PASSED"
    assert result["evidencePageIds"] == [112]
    assert result["review"]["safety"]["passed"] is True
    # 课件证据明细（供前端「课件证据」面板渲染）：页码 + 原文片段。
    assert result["evidence"] == [
        {"page_id": 112, "page_no": 12, "snippet": "第 12 页讲解 for 循环遍历列表。", "score": 0.82}
    ]


@pytest.mark.asyncio
async def test_ask_stream_general_knowledge_when_no_evidence() -> None:
    # 无 KC → StaticEvidenceFetcher 命中为空 → 通识知识。
    events = await _run("天气怎么样", [], "基于通识知识，这与 Python 无关。")
    result = events[-1][1]
    assert result["groundingStatus"] == "NO_EVIDENCE"
    assert result["based_on_general_knowledge"] is True
