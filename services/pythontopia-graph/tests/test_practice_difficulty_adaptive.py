"""Task 3.2: 难度自适应出题 — target_difficulty→难度标签 + safety仍拦泄答。"""
from __future__ import annotations
import pytest
from pythontopia_graph.agents.practice_agent import PracticeAgent
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import ResourceProduceRequest

class StubProvider(LlmProvider):
    def __init__(self, p=None): self.payload = p
    async def chat(self, m, **kw): return LlmResponse(content_json=self.payload)

E = EvidenceBundle()

def req(diff): return ResourceProduceRequest(user_id=1, kc_codes=["KC01"], type="PRACTICE", target_difficulty=diff)

@pytest.mark.asyncio
async def test_fallback_basic_when_target_is_basic():
    draft = await PracticeAgent(StubProvider()).design(req("basic"), E)
    assert all(p.difficulty == "basic" for p in draft.practice.problems)

@pytest.mark.asyncio
async def test_fallback_hard_when_target_is_hard():
    draft = await PracticeAgent(StubProvider()).design(req("hard"), E)
    assert all(p.difficulty == "hard" for p in draft.practice.problems)

@pytest.mark.asyncio
async def test_forbidden_tokens_blocked_even_with_difficulty():
    bad = {"practice":{"problems":[{"title":"T","statement_md":"answer_key:42","sample_io":[{"input":"1","output":"2"}],"kc_codes":["KC01"],"difficulty":"basic"}]}}
    draft = await PracticeAgent(StubProvider(bad)).design(req("basic"), E)
    for p in draft.practice.problems:
        assert "answer_key" not in p.statement_md.lower()

@pytest.mark.asyncio
async def test_null_difficulty_uses_self_rated():
    r = ResourceProduceRequest(user_id=1, kc_codes=["KC01"], type="PRACTICE", target_difficulty=None)
    r.profile_snapshot.python_self_rated_level = 5
    draft = await PracticeAgent(StubProvider()).design(r, E)
    assert all(p.difficulty == "hard" for p in draft.practice.problems)
