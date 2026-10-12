"""Tutor image requests are validated before they reach a provider."""

from __future__ import annotations

import base64
from collections.abc import AsyncIterator

import pytest
from pydantic import ValidationError

from pythontopia_graph.agents.tutor_agent import TutorAgent
from pythontopia_graph.llm.provider import LlmResponse
from pythontopia_graph.schemas.tutor import TutorAskRequest

_PNG = b"\x89PNG\r\n\x1a\nsmall image"


def _data_url(mime: str = "image/png", data: bytes = _PNG) -> str:
    return f"data:{mime};base64,{base64.b64encode(data).decode('ascii')}"


def test_tutor_request_accepts_matching_image_data_url() -> None:
    request = TutorAskRequest(user_id=1, question="请解释图片", images=[_data_url()])

    assert request.images == [_data_url()]


def test_tutor_request_rejects_mime_that_does_not_match_image_bytes() -> None:
    with pytest.raises(ValidationError, match="JPEG, PNG, GIF, or WebP"):
        TutorAskRequest(
            user_id=1,
            question="请解释图片",
            images=[_data_url("image/jpeg")],
        )


def test_tutor_request_rejects_more_than_three_images() -> None:
    with pytest.raises(ValidationError):
        TutorAskRequest(
            user_id=1,
            question="请解释图片",
            images=[_data_url()] * 4,
        )


class _RecordingProvider:
    def __init__(self) -> None:
        self.messages: list[dict[str, object]] = []
        self.kwargs: dict[str, object] = {}

    async def chat_stream(
        self, messages: list[dict[str, object]], **kwargs: object
    ) -> AsyncIterator[str]:
        self.messages = messages
        self.kwargs = kwargs
        yield "收到图片"

    async def chat(self, messages, **kwargs) -> LlmResponse:
        return LlmResponse(content_text="")


@pytest.mark.asyncio
async def test_tutor_agent_sends_image_parts_to_vision_model() -> None:
    provider = _RecordingProvider()
    agent = TutorAgent(provider=provider)
    image = _data_url()

    answer = [
        token
        async for token in agent.ask_stream(
            question="请解释图片",
            profile_brief="",
            conversation_history=[],
            evidence_chunks=[],
            images=[image],
        )
    ]

    assert answer == ["收到图片"]
    assert provider.messages[-1]["content"] == [
        {"type": "text", "text": "请解释图片"},
        {"type": "image_url", "image_url": {"url": image}},
    ]
    assert provider.kwargs["model_tier"] == "vision"
