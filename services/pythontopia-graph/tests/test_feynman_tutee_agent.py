from __future__ import annotations

from collections.abc import AsyncIterator

from fastapi.testclient import TestClient

from pythontopia_graph.agents.feynman_tutee_agent import FeynmanTuteeAgent
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.main import app
from pythontopia_graph.schemas.feynman import FeynmanTuteeInput, Misconception


class JsonProvider(LlmProvider):
    def __init__(self, payload: dict) -> None:
        self.payload = payload
        self.calls = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.calls += 1
        return LlmResponse(content_json=self.payload)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


class RaisingProvider(LlmProvider):
    async def chat(self, messages, **kwargs):  # type: ignore[override]
        raise RuntimeError("provider unavailable")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


def _misconception(tag: str = "off_by_one_range") -> Misconception:
    return Misconception(tag=tag, evidence_event_ids=[501], resolved=False)


def _input(
    misconceptions: list[Misconception] | None = None,
    teaching: str | None = None,
) -> FeynmanTuteeInput:
    return FeynmanTuteeInput(
        kc_code="PY43-KC-021",
        misconceptions=misconceptions or [_misconception()],
        student_teaching=teaching,
    )


async def test_injected_misconception_makes_corresponding_mistake() -> None:
    provider = JsonProvider(
        {
            "reply_md": "我以为 range(1, 5) 会包含 5，所以循环次数会多一次。",
            "updated_misconceptions": [
                {"tag": "off_by_one_range", "evidence_event_ids": [501], "resolved": False}
            ],
            "produced_code": "for i in range(1, 5):\n    print(i)",
        }
    )

    out = await FeynmanTuteeAgent(provider).step(_input())

    assert "包含 5" in out.reply_md
    assert out.updated_misconceptions[0].tag == "off_by_one_range"
    assert out.updated_misconceptions[0].resolved is False
    assert "range" in (out.produced_code or "")


async def test_correct_teaching_resolves_matching_misconception() -> None:
    provider = RaisingProvider()

    out = await FeynmanTuteeAgent(provider).step(
        _input(teaching="range 的右端点不包含，range(1, 5) 只到 4；要包含 5 就写 range(1, 6)。")
    )

    assert out.updated_misconceptions[0].resolved is True
    assert "明白" in out.reply_md
    assert out.produced_code is not None


async def test_wrong_teaching_does_not_resolve_misconception() -> None:
    provider = RaisingProvider()

    out = await FeynmanTuteeAgent(provider).step(
        _input(teaching="range 会自动包含最后一个数，所以不用改结束值。")
    )

    assert out.updated_misconceptions[0].resolved is False
    assert "还是有点困惑" in out.reply_md
    assert out.produced_code is None


async def test_provider_failure_uses_rule_fallback() -> None:
    provider = RaisingProvider()

    out = await FeynmanTuteeAgent(provider).step(_input())

    assert "range" in out.reply_md
    assert out.updated_misconceptions[0].tag == "off_by_one_range"
    assert out.updated_misconceptions[0].resolved is False


def test_feynman_route_is_registered() -> None:
    app.state.llm_provider = JsonProvider(
        {
            "reply_md": "我以为 input() 得到的就是数字，所以直接相加。",
            "updated_misconceptions": [
                {"tag": "input_type_string", "evidence_event_ids": [601], "resolved": False}
            ],
            "produced_code": "age = input('age: ')\nprint(age + 1)",
        }
    )
    client = TestClient(app)

    response = client.post(
        "/agents/feynman/tutee/step",
        json={
            "kc_code": "PY43-KC-004",
            "misconceptions": [
                {"tag": "input_type_string", "evidence_event_ids": [601], "resolved": False}
            ],
            "student_teaching": None,
        },
    )

    assert response.status_code == 200
    assert response.json()["updated_misconceptions"][0]["tag"] == "input_type_string"
    del app.state.llm_provider
