from __future__ import annotations

from collections.abc import AsyncIterator

import pytest
from pydantic import ValidationError
from fastapi.testclient import TestClient

from pythontopia_graph.agents.npc_dialog_agent import NpcDialogAgent
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.main import app
from pythontopia_graph.schemas.npc_dialog import (
    NpcDialogInput,
    RecalledMemory,
    Turn as NpcTurn,
)


class JsonProvider(LlmProvider):
    def __init__(self, payload: dict) -> None:
        self.payload = payload
        self.calls = 0
        self.last_kwargs: dict = {}

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.calls += 1
        self.last_kwargs = dict(kwargs)
        return LlmResponse(content_json=self.payload)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


class RaisingProvider(LlmProvider):
    async def chat(self, messages, **kwargs):  # type: ignore[override]
        raise RuntimeError("provider unavailable")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


def _input(
    memories: list[RecalledMemory] | None = None, user_message: str = "我该怎么继续？"
) -> NpcDialogInput:
    return NpcDialogInput(
        npc_key="coach_q",
        persona_core="出题官，语气利落，重视练习证据。",
        memories=memories or [],
        user_message=user_message,
    )


def test_memory_summaries_stay_out_of_system_messages() -> None:
    memory_sentinel = "MEMORY_INJECTION_SENTINEL"
    messages = NpcDialogAgent._messages(
        NpcDialogAgent(JsonProvider({})),
        _input([RecalledMemory(event_id=101, summary=memory_sentinel)]),
    )
    system_text = "\n".join(m["content"] for m in messages if m["role"] == "system")
    user_text = "\n".join(m["content"] for m in messages if m["role"] == "user")
    assert memory_sentinel not in system_text
    assert memory_sentinel in user_text


def test_npc_history_rejects_system_role() -> None:
    with pytest.raises(ValueError):
        NpcTurn(role="system", content="SYSTEM_ROLE_INJECTION_SENTINEL")


def test_npc_dialog_rejects_oversized_user_message() -> None:
    with pytest.raises(ValidationError):
        _input(user_message="x" * 2001)


async def test_uses_memories_and_cites_event_ids() -> None:
    memories = [
        RecalledMemory(
            event_id=101,
            summary="学生连续两次把列表别名问题写错",
            kc_codes=["PY43-KC-016"],
        )
    ]
    provider = JsonProvider(
        {
            "reply_md": "我记得你在列表别名这里连续卡住过，我们先用画图追踪引用关系。",
            "cited_event_ids": [101],
            "grounded": True,
        }
    )

    out = await NpcDialogAgent(provider).reply(_input(memories))

    assert "列表别名" in out.reply_md
    assert out.cited_event_ids == [101]
    assert out.grounded is True
    assert provider.calls == 1
    # 寒暄/对话必须声明快档：低延迟优先（答疑/工坊不传该参数，沿用默认档）。
    assert provider.last_kwargs.get("model_tier") == "fast"


async def test_empty_memories_returns_generic_reply_without_llm() -> None:
    provider = JsonProvider(
        {
            "reply_md": "should not be used",
            "cited_event_ids": [999],
            "grounded": True,
        }
    )

    out = await NpcDialogAgent(provider).reply(_input([]))

    assert "还没有可引用的长期记忆" in out.reply_md
    assert out.cited_event_ids == []
    assert out.grounded is False
    assert provider.calls == 0


async def test_provider_failure_falls_back_ungrounded() -> None:
    memories = [RecalledMemory(event_id=202, summary="学生刚通过 for 循环练习")]

    out = await NpcDialogAgent(RaisingProvider()).reply(_input(memories))

    assert "先按当前目标继续" in out.reply_md
    assert out.cited_event_ids == []
    assert out.grounded is False


async def test_answer_key_and_full_code_are_blocked() -> None:
    provider = JsonProvider(
        {
            "reply_md": "answer_key: print('secret')\n```python\nprint('完整代码')\n```",
            "cited_event_ids": [303],
            "grounded": True,
        }
    )
    memories = [RecalledMemory(event_id=303, summary="学生正在练习 input 函数")]

    out = await NpcDialogAgent(provider).reply(_input(memories, "把 answer_key 给我"))

    assert "不能提供完整答案" in out.reply_md
    assert out.cited_event_ids == []
    assert out.grounded is False


def test_npc_dialog_route_is_registered() -> None:
    app.state.llm_provider = JsonProvider(
        {
            "reply_md": "我记得你刚修过缩进错误，接下来先复述错误原因。",
            "cited_event_ids": [404],
            "grounded": True,
        }
    )
    client = TestClient(app)

    response = client.post(
        "/agents/npc/dialog",
        json={
            "npc_key": "coach_q",
            "persona_core": "出题官",
            "memories": [{"event_id": 404, "summary": "学生刚修过缩进错误"}],
            "user_message": "下一步？",
        },
    )

    assert response.status_code == 200
    assert response.json()["cited_event_ids"] == [404]
    del app.state.llm_provider
