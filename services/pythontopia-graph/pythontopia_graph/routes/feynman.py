from __future__ import annotations

from typing import cast

from fastapi import APIRouter, Depends, Request

from pythontopia_graph.agents.feynman_tutee_agent import FeynmanTuteeAgent
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.schemas.feynman import (
    FeynmanBugExposureInput,
    FeynmanBugExposureOutput,
    FeynmanTuteeInput,
    FeynmanTuteeOutput,
)

router = APIRouter()


def _provider(request: Request) -> LlmProvider:
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is not None:
        return cast(LlmProvider, provider)
    return provider_from_env()


@router.post("/agents/feynman/tutee/step", response_model=FeynmanTuteeOutput)
async def feynman_tutee_step(
    body: FeynmanTuteeInput,
    provider: LlmProvider = Depends(_provider),
) -> FeynmanTuteeOutput:
    return await FeynmanTuteeAgent(provider).step(body)


@router.post("/agents/feynman/bug-exposure", response_model=FeynmanBugExposureOutput)
async def feynman_bug_exposure(
    body: FeynmanBugExposureInput,
    provider: LlmProvider = Depends(_provider),
) -> FeynmanBugExposureOutput:
    """错误猎人：学弟写出含 bug 的代码 + 真实 bug 行号，供学生限时找茬。"""
    return await FeynmanTuteeAgent(provider).expose_bug(body)
