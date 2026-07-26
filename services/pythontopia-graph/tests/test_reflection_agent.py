"""ReflectionAgent（学情反思器）单元测试。

覆盖契约 C：①空记忆→空洞察且 0 次 LLM 调用 ②解析+过滤（合法/空证据/非法维度/
引用输入外 id）③provider 抛异常向上传播（fail-fast）④DryRun 路由返回空 insights 不报错。
"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest
from fastapi.testclient import TestClient

from pythontopia_graph.agents.reflection_agent import ReflectionAgent
from pythontopia_graph.llm.provider import DryRunProvider, LlmProvider, LlmResponse
from pythontopia_graph.main import app
from pythontopia_graph.schemas.reflection import MemoryItem, ReflectionInput


class CountingProvider(LlmProvider):
    """统计 chat 调用次数；用于断言空记忆时零 LLM 调用。"""

    def __init__(self) -> None:
        self.calls = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.calls += 1
        return LlmResponse(content_json={"insights": []})

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


class FakeProvider(LlmProvider):
    """返回预设 content_json。"""

    def __init__(self, content_json: dict | None) -> None:
        self.content_json = content_json
        self.calls = 0
        self.kwargs = None

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.calls += 1
        self.kwargs = kwargs
        return LlmResponse(content_json=self.content_json)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


class RaisingProvider(LlmProvider):
    """chat 必抛异常，用于验证 fail-fast 传播。"""

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        raise RuntimeError("upstream LLM exploded")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


def _memory(event_id: int = 10) -> MemoryItem:
    return MemoryItem(
        event_id=event_id,
        event_type="practice_failed",
        kc_codes=["PY43-KC-016"],
        importance=4,
        created_at="2026-06-01T08:00:00Z",
        summary="列表别名 WA",
    )


async def test_returns_empty_without_memories_and_no_llm_call() -> None:
    spy = CountingProvider()
    out = await ReflectionAgent(spy).reflect(ReflectionInput(user_id=1, memories=[]))
    assert out.insights == []
    assert spy.calls == 0


async def test_parses_and_filters_insights() -> None:
    mems = [_memory(10)]
    fake = FakeProvider(
        content_json={
            "observations": [
                {
                    "claim": "列表别名可变性反复出错",
                    "dimension_intent": "ERROR_PATTERN",
                    "tags": ["mutable_alias"],
                    "evidence_refs": ["event-10"],
                    "confidence": 0.8,
                },
                {
                    "claim": "无证据洞察",
                    "dimension_intent": "ERROR_PATTERN",
                    "evidence_refs": [],
                    "confidence": 0.9,
                },
                {
                    "claim": "非法维度",
                    "dimension_intent": "FOO",
                    "evidence_refs": ["event-10"],
                    "confidence": 0.5,
                },
                {
                    "claim": "引用了不存在的事件",
                    "dimension_intent": "ERROR_PATTERN",
                    "evidence_refs": ["event-999"],
                    "confidence": 0.5,
                },
            ]
        }
    )
    out = await ReflectionAgent(fake).reflect(ReflectionInput(user_id=1, memories=mems))
    assert len(out.insights) == 1
    only = out.insights[0]
    assert only.text == "列表别名可变性反复出错"
    assert only.dimension_key == "error_pattern"
    assert only.tags == ["mutable_alias"]
    assert only.evidence_event_ids == [10]
    assert only.confidence == 0.8
    assert fake.calls == 1
    assert fake.kwargs["guard_context"]["evidence_ids"] == ["event-10"]
    assert fake.kwargs["guard_context"]["evidence_fields"] == [
        "observations.*.evidence_refs"
    ]


async def test_keeps_at_most_three_insights() -> None:
    mems = [_memory(1), _memory(2), _memory(3), _memory(4)]
    insights = [
        {
            "claim": f"洞察 {i}",
            "dimension_intent": "ERROR_PATTERN",
            "evidence_refs": [f"event-{i}"],
            "confidence": 0.5,
        }
        for i in range(1, 5)
    ]
    fake = FakeProvider(content_json={"observations": insights})
    out = await ReflectionAgent(fake).reflect(ReflectionInput(user_id=1, memories=mems))
    assert len(out.insights) == 3


async def test_provider_failure_propagates() -> None:
    mems = [_memory(10)]
    with pytest.raises(RuntimeError):
        await ReflectionAgent(RaisingProvider()).reflect(
            ReflectionInput(user_id=1, memories=mems)
        )


def test_route_with_dry_run_returns_empty_insights() -> None:
    app.state.llm_provider = DryRunProvider()
    client = TestClient(app)
    body = {
        "user_id": 1,
        "memories": [
            {
                "event_id": 10,
                "event_type": "practice_failed",
                "kc_codes": ["PY43-KC-016"],
                "importance": 4,
                "created_at": "2026-06-01T08:00:00Z",
                "summary": "WA",
            }
        ],
    }
    response = client.post("/agents/reflection", json=body)
    assert response.status_code == 200
    assert response.json() == {"insights": []}
    del app.state.llm_provider
