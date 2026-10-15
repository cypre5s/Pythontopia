from collections.abc import AsyncIterator
from typing import Any

import pytest

from pythontopia_graph.agents.tutor_agent import TutorAgent
from pythontopia_graph.llm.provider import LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import RagHit


class RecordingProvider:
    def __init__(self) -> None:
        self.calls: list[list[dict[str, Any]]] = []

    async def chat(self, messages: list[dict[str, Any]], **kwargs: object) -> LlmResponse:
        self.calls.append(messages)
        return LlmResponse(
            content_json={
                "stem": "循环填空 (1)",
                "blanks": [{"index": 1, "hint": "循环关键字"}],
                "verdict": "partial",
                "explanation_md": "继续检查循环条件。",
                "note": "这页介绍循环。",
                "kind": "mermaid",
                "source": "flowchart TD\nA[循环] --> B[条件]",
            },
        )

    async def chat_stream(
        self, messages: list[dict[str, Any]], **kwargs: object,
    ) -> AsyncIterator[str]:
        yield ""


def _evidence(text: str) -> RagHit:
    return RagHit(chunk_id="c1", page_id=1, page_no=1, text=text, score=0.9)


def _assert_user_context(provider: RecordingProvider, *sentinels: str) -> None:
    messages = provider.calls[-1]
    system_text = "\n".join(str(m["content"]) for m in messages if m["role"] == "system")
    user_text = "\n".join(str(m["content"]) for m in messages if m["role"] == "user")
    for sentinel in sentinels:
        assert sentinel not in system_text
        assert sentinel in user_text


@pytest.mark.asyncio
async def test_quiz_profile_is_user_context() -> None:
    provider = RecordingProvider()

    await TutorAgent(provider).ask_quiz_question(
        topic="循环", profile_brief="PROFILE_INJECTION_SENTINEL",
    )

    _assert_user_context(provider, "PROFILE_INJECTION_SENTINEL")


@pytest.mark.asyncio
async def test_quiz_evidence_and_profile_are_user_context() -> None:
    provider = RecordingProvider()

    await TutorAgent(provider).judge_quiz(
        prev_question="循环题",
        student_answer="ANSWER_INJECTION_SENTINEL",
        profile_brief="PROFILE_INJECTION_SENTINEL",
        evidence_chunks=[_evidence("COURSEWARE_INJECTION_SENTINEL")],
    )

    _assert_user_context(
        provider,
        "ANSWER_INJECTION_SENTINEL",
        "PROFILE_INJECTION_SENTINEL",
        "COURSEWARE_INJECTION_SENTINEL",
    )


@pytest.mark.asyncio
async def test_evidence_summary_profile_and_courseware_are_user_context() -> None:
    provider = RecordingProvider()

    await TutorAgent(provider).summarize_evidence(
        question="QUESTION_INJECTION_SENTINEL",
        context="CONTEXT_INJECTION_SENTINEL",
        profile_brief="PROFILE_INJECTION_SENTINEL",
        evidence_chunks=[_evidence("COURSEWARE_INJECTION_SENTINEL")],
    )

    _assert_user_context(
        provider,
        "QUESTION_INJECTION_SENTINEL",
        "CONTEXT_INJECTION_SENTINEL",
        "PROFILE_INJECTION_SENTINEL",
        "COURSEWARE_INJECTION_SENTINEL",
    )


@pytest.mark.asyncio
async def test_diagram_courseware_is_user_context() -> None:
    provider = RecordingProvider()

    await TutorAgent(provider).build_diagram(
        "循环图", [_evidence("COURSEWARE_INJECTION_SENTINEL")],
    )

    _assert_user_context(provider, "COURSEWARE_INJECTION_SENTINEL")
