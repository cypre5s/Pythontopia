from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.agents import tutor_agent as tutor_module
from pythontopia_graph.agents.tutor_agent import TutorAgent
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.code_runner import CodeRunResult


class CapturingProvider(LlmProvider):
    def __init__(self) -> None:
        self.messages: list[dict[str, str]] = []

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        self.messages = messages
        return LlmResponse(content_text="ok")

    async def chat_stream(
        self, messages: list[dict[str, str]], **kwargs: object,
    ) -> AsyncIterator[str]:
        self.messages = messages
        yield "参考真实输出定位问题。"


def test_should_verify_code_questions_only() -> None:
    assert TutorAgent.should_verify(
        "这段代码为什么输出不对？\n```python\nprint(2 * 3)\n```",
    ) is True
    assert TutorAgent.should_verify("什么是列表推导？") is False


@pytest.mark.asyncio
async def test_ask_stream_injects_real_code_output(monkeypatch: pytest.MonkeyPatch) -> None:
    provider = CapturingProvider()
    seen: list[str] = []

    def fake_run_python(code: str, **kwargs: object) -> CodeRunResult:
        seen.append(code)
        return CodeRunResult(stdout="6\n", stderr="", exit_code=0, timed_out=False, truncated=False)

    monkeypatch.setattr(tutor_module, "run_python", fake_run_python)

    agent = TutorAgent(provider)
    chunks = [
        token
        async for token in agent.ask_stream(
            question="这段代码为什么输出不对？\n```python\nprint(2 * 3)\n```",
            profile_brief="",
            conversation_history=[],
            evidence_chunks=[],
        )
    ]

    assert chunks == ["参考真实输出定位问题。"]
    assert seen == ["print(2 * 3)"]
    verification_messages = [
        message["content"] for message in provider.messages if "代码运行验证" in message["content"]
    ]
    assert verification_messages
    assert "stdout=6" in verification_messages[0]
