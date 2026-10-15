from __future__ import annotations

import pytest

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import StaticEvidenceFetcher
from pythontopia_graph.routes import tutor as tutor_route
from pythontopia_graph.routes.streaming import profile_turn_stream
from pythontopia_graph.schemas.tutor import TutorAskRequest


class _NoopProvider(LlmProvider):
    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_text="")

    async def chat_stream(self, messages, **kwargs):  # type: ignore[override]
        if False:
            yield ""


@pytest.mark.asyncio
async def test_profile_stream_does_not_return_invalid_input_details() -> None:
    response = await profile_turn_stream(
        {"user_id": "profile-input-secret-sentinel"}, provider=_NoopProvider()
    )
    chunks: list[str] = []
    async for chunk in response.body_iterator:
        chunks.append(chunk if isinstance(chunk, str) else chunk.decode("utf-8"))
    body = "".join(chunks)

    assert "PROFILE_TURN_FAILED" in body
    assert "profile-input-secret-sentinel" not in body
    assert "画像服务暂时不可用，请稍后重试。" in body


@pytest.mark.asyncio
async def test_quiz_judgement_redacts_unexpected_exception(monkeypatch) -> None:
    async def fail_call(*args, **kwargs):
        raise RuntimeError("quiz-answer-secret-sentinel")

    monkeypatch.setattr(tutor_route, "run_autogen_tutor_call", fail_call)
    response = await tutor_route.tutor_ask_stream(
        TutorAskRequest(
            user_id=1,
            question="答案",
            context="QUIZ_JUDGE",
            kc_codes=["PY43-KC-001"],
            conversation_history=[
                {"role": "assistant", "content": "1+1 的结果是什么？"}
            ],
        ),
        provider=_NoopProvider(),
        evidence_fetcher=StaticEvidenceFetcher([]),
    )
    chunks: list[str] = []
    async for chunk in response.body_iterator:
        chunks.append(chunk if isinstance(chunk, str) else chunk.decode("utf-8"))
    body = "".join(chunks)

    assert "QUIZ_FAILED" in body
    assert "测验暂时不可用，请稍后重试。" in body
    assert "quiz-answer-secret-sentinel" not in body
