from __future__ import annotations

import pytest

from pythontopia_graph.agents.code_lab_agent import CodeLabAgent
from pythontopia_graph.agents.handout_agent import HandoutAgent
from pythontopia_graph.agents.mind_map_agent import MindMapAgent
from pythontopia_graph.agents.practice_agent import PracticeAgent
from pythontopia_graph.agents.reading_agent import ReadingAgent
from pythontopia_graph.agents.storyboard_agent import StoryboardAgent
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, RagHit
from pythontopia_graph.schemas.resource import (
    HandoutContent,
    MindMapContent,
    PracticeContent,
    ProblemSpec,
    ResourceProduceRequest,
    SampleIo,
    StoryboardContent,
    Walkthrough,
)


class StubProvider(LlmProvider):
    def __init__(self, payload: dict | None) -> None:
        self.payload = payload

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_json=self.payload)


def request_of(type_: str) -> ResourceProduceRequest:
    return ResourceProduceRequest(user_id=1, kc_codes=["PY43-KC-001"], type=type_)  # type: ignore[arg-type]


EVIDENCE = EvidenceBundle(
    chunks=[
        RagHit(
            chunk_id="c1",
            page_id=101,
            page_no=12,
            chapter_id=3,
            kc_codes=["PY43-KC-001"],
            text="第 12 页讲了 input 函数的基本用法。",
            score=0.81,
        )
    ]
)


@pytest.mark.asyncio
async def test_handout_agent_uses_evidence_page_refs_in_fallback() -> None:
    agent = HandoutAgent(StubProvider(payload=None))
    draft = await agent.design(request_of("HANDOUT"), EVIDENCE)
    assert draft.handout is not None
    refs = [ref for section in draft.handout.sections for ref in section.page_refs]
    assert any(ref.page_id == 101 for ref in refs)


@pytest.mark.asyncio
async def test_handout_agent_accepts_llm_payload() -> None:
    payload = {
        "title": "讲义",
        "summary": "by llm",
        "handout": HandoutContent(
            sections=[
                {
                    "heading": "input 函数",
                    "paragraphs": ["读取一行。"],
                    "page_refs": [{"page_id": 101, "page_no": 12, "chapter_id": 3}],
                }
            ]
        ).model_dump(),
    }
    agent = HandoutAgent(StubProvider(payload))
    draft = await agent.design(request_of("HANDOUT"), EVIDENCE)
    assert draft.title == "讲义"
    assert draft.handout is not None and draft.handout.sections[0].heading == "input 函数"


@pytest.mark.asyncio
async def test_mind_map_agent_fallback_is_syntax_valid() -> None:
    agent = MindMapAgent(StubProvider(payload=None))
    draft = await agent.design(request_of("MINDMAP"), EVIDENCE)
    assert draft.mindmap is not None
    assert MindMapAgent.syntax_valid(draft.mindmap.mermaid)


@pytest.mark.asyncio
async def test_mind_map_agent_rejects_invalid_mermaid_from_llm() -> None:
    payload = {
        "mindmap": MindMapContent(mermaid="this is not mermaid", kc_map={}).model_dump()
    }
    agent = MindMapAgent(StubProvider(payload))
    draft = await agent.design(request_of("MINDMAP"), EVIDENCE)
    assert draft.mindmap is not None
    assert MindMapAgent.syntax_valid(draft.mindmap.mermaid)


@pytest.mark.asyncio
async def test_practice_agent_never_emits_hidden_tests() -> None:
    agent = PracticeAgent(StubProvider(payload=None))
    draft = await agent.design(request_of("PRACTICE"), EVIDENCE)
    assert draft.practice is not None
    for problem in draft.practice.problems:
        for key in ("hidden_test_cases", "answer_key", "secret_test"):
            assert key not in problem.statement_md.lower()
            for hint in problem.hints:
                assert key not in hint.lower()


@pytest.mark.asyncio
async def test_practice_agent_filters_unsafe_llm_payload() -> None:
    payload = {
        "practice": PracticeContent(
            problems=[
                ProblemSpec(
                    title="p",
                    statement_md="纯净题面，包含完整题面说明文字，足够长。",
                    hints=["This hint contains answer key disclosure"],
                    sample_io=[SampleIo(input="1", output="1")],
                )
            ]
        ).model_dump(),
    }
    agent = PracticeAgent(StubProvider(payload))
    draft = await agent.design(request_of("PRACTICE"), EVIDENCE)
    assert draft.practice is not None
    assert "answer key" not in (draft.practice.problems[0].hints[0] if draft.practice.problems[0].hints else "").lower()


@pytest.mark.asyncio
async def test_code_lab_agent_parses_python_in_fallback() -> None:
    agent = CodeLabAgent(StubProvider(payload=None))
    draft = await agent.design(request_of("CODE_LAB"), EVIDENCE)
    assert draft.code_lab is not None
    import ast

    for walkthrough in draft.code_lab.walkthroughs:
        ast.parse(walkthrough.code)


@pytest.mark.asyncio
async def test_code_lab_agent_rejects_syntax_invalid_llm() -> None:
    payload = {
        "code_lab": {
            "walkthroughs": [
                Walkthrough(
                    title="bad",
                    narrative="bad",
                    code="def(:: this is not python",
                    expected_output="",
                    common_mistakes=[],
                ).model_dump()
            ]
        }
    }
    agent = CodeLabAgent(StubProvider(payload))
    draft = await agent.design(request_of("CODE_LAB"), EVIDENCE)
    assert draft.code_lab is not None
    import ast

    for walkthrough in draft.code_lab.walkthroughs:
        ast.parse(walkthrough.code)


@pytest.mark.asyncio
async def test_reading_agent_has_at_least_two_items() -> None:
    agent = ReadingAgent(StubProvider(payload=None))
    draft = await agent.design(request_of("READING"), EVIDENCE)
    assert draft.reading is not None
    assert len(draft.reading.items) >= 2


@pytest.mark.asyncio
async def test_reading_agent_keeps_evidence_refs_in_fallback() -> None:
    agent = ReadingAgent(StubProvider(payload=None))
    draft = await agent.design(request_of("READING"), EVIDENCE)
    assert draft.reading is not None
    has_ref = any(item.page_refs for item in draft.reading.items)
    assert has_ref


@pytest.mark.asyncio
async def test_storyboard_agent_emits_three_to_five_scenes() -> None:
    agent = StoryboardAgent(StubProvider(payload=None))
    draft = await agent.design(request_of("STORYBOARD"), EVIDENCE)
    assert draft.storyboard is not None
    assert 3 <= len(draft.storyboard.scenes) <= 5
    for scene in draft.storyboard.scenes:
        assert scene.duration_seconds <= 90


@pytest.mark.asyncio
async def test_storyboard_agent_rejects_real_people_visual_hint() -> None:
    payload = {
        "storyboard": StoryboardContent(
            scenes=[
                {
                    "title": "intro",
                    "narration": "...",
                    "visual_hint": "出现真实人物姓名",
                    "kc_codes": ["PY43-KC-001"],
                    "duration_seconds": 10,
                },
                {
                    "title": "scene2",
                    "narration": "...",
                    "visual_hint": "像素小镇",
                    "kc_codes": ["PY43-KC-001"],
                    "duration_seconds": 10,
                },
                {
                    "title": "scene3",
                    "narration": "...",
                    "visual_hint": "像素小镇",
                    "kc_codes": ["PY43-KC-001"],
                    "duration_seconds": 10,
                },
            ]
        ).model_dump(),
    }
    agent = StoryboardAgent(StubProvider(payload))
    draft = await agent.design(request_of("STORYBOARD"), EVIDENCE)
    assert draft.storyboard is not None
    for scene in draft.storyboard.scenes:
        assert "真实人物" not in scene.visual_hint
