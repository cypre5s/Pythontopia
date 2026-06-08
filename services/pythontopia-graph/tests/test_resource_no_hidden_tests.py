"""PracticeAgent 各分支输出不得泄露 hidden test 相关字段。"""
from __future__ import annotations

import json

import pytest

from pythontopia_graph.agents.practice_agent import PracticeAgent
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import ResourceProduceRequest


class StubProvider(LlmProvider):
    def __init__(self, payload: dict | None) -> None:
        self.payload = payload

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_json=self.payload)


FORBIDDEN = ("hidden_test_cases", "answer_key", "secret_test", "hidden")


@pytest.mark.asyncio
async def test_practice_agent_output_never_carries_hidden_fields() -> None:
    payloads: list[dict | None] = [
        None,
        {"practice": {"problems": []}},
        {"practice": {"problems": [{"title": "x"}]}},
        {
            "practice": {
                "problems": [
                    {
                        "title": "ok",
                        "statement_md": "ok statement long enough",
                        "sample_io": [{"input": "1", "output": "1"}],
                        "hints": [],
                        "kc_codes": ["PY43-KC-001"],
                    }
                ]
            }
        },
    ]
    request = ResourceProduceRequest(user_id=1, kc_codes=["PY43-KC-001"], type="PRACTICE")
    for payload in payloads:
        agent = PracticeAgent(StubProvider(payload))
        draft = await agent.design(request, EvidenceBundle(chunks=[]))
        serialized = json.dumps(draft.model_dump(mode="json"), ensure_ascii=False).lower()
        for forbidden in FORBIDDEN:
            assert forbidden not in serialized, f"forbidden token '{forbidden}' leaked: {serialized}"
