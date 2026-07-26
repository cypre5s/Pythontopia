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
        self.messages = None
        self.kwargs = None

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.messages = messages
        self.kwargs = kwargs
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
        "sections": [
            {
                "heading": "input 函数",
                "paragraphs": ["读取一行。"],
                "code_snippets": [],
                "evidence_refs": ["page-101"],
            }
        ],
    }
    provider = StubProvider(payload)
    agent = HandoutAgent(provider)
    draft = await agent.design(request_of("HANDOUT"), EVIDENCE)
    assert draft.title == "讲义"
    assert draft.handout is not None and draft.handout.sections[0].heading == "input 函数"
    assert draft.handout.sections[0].page_refs[0].page_id == 101
    assert provider.kwargs["guard_context"]["evidence_fields"] == [
        "sections.*.evidence_refs"
    ]


@pytest.mark.asyncio
async def test_handout_agent_does_not_trust_model_created_page_refs() -> None:
    payload = {
        "title": "伪造讲义",
        "summary": "bad",
        "handout": {
            "sections": [
                {
                    "heading": "伪造",
                    "paragraphs": ["伪造页码"],
                    "page_refs": [{"page_id": 999, "page_no": 999}],
                }
            ]
        },
    }
    draft = await HandoutAgent(StubProvider(payload)).design(
        request_of("HANDOUT"), EVIDENCE
    )

    assert draft.title != "伪造讲义"
    assert draft.handout is not None
    assert {ref.page_id for section in draft.handout.sections for ref in section.page_refs} == {
        101
    }


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
async def test_mind_map_agent_accepts_bare_adapter_payload_and_filters_kcs() -> None:
    payload = {
        "mermaid": (
            "graph TD\n"
            "  A[Python 基础] --> B[条件表达式]\n"
            "  style A fill:#ff9,stroke:#333,stroke-width:2px"
        ),
        "kc_map": {"PY43-KC-001": ["PY43-KC-002", "PY43-KC-001"]},
    }
    agent = MindMapAgent(StubProvider(payload))
    draft = await agent.design(request_of("MINDMAP"), EVIDENCE)

    assert draft.mindmap is not None
    assert draft.mindmap.mermaid.startswith("graph TD")
    assert draft.mindmap.kc_map == {"PY43-KC-001": []}


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
async def test_practice_agent_accepts_legacy_adapter_payload() -> None:
    payload = {
        "diagnostic_hints": ["注意 range 右端不包含。"],
        "practice_problems": [
            {
                "id": "loop-1",
                "type": "fill_in_blank",
                "hint": "先手算前三项。",
                "sample_io": {"input": "0 5", "output": "0 1 2 3 4"},
                "expected_output_pattern": "0 1 2 3 4",
            }
        ],
    }
    agent = PracticeAgent(StubProvider(payload))
    draft = await agent.design(request_of("PRACTICE"), EVIDENCE)

    assert draft.practice is not None
    problem = draft.practice.problems[0]
    assert problem.title == "loop-1"
    assert problem.sample_io[0].output == "0 1 2 3 4"
    assert problem.kc_codes == ["PY43-KC-001"]
    assert "range" in " ".join(problem.hints)
    assert problem.statement_md != "围绕知识点 PY43-KC-001，编写一段程序读入数据并输出处理结果。请勿粘贴外部答案，独立完成。"


@pytest.mark.asyncio
async def test_practice_agent_rejects_unsafe_legacy_adapter_payload() -> None:
    payload = {
        "practice_problems": [
            {
                "id": "unsafe",
                "type": "fill_in_blank",
                "hint": "answer_key: print(42)",
                "sample_io": {"input": "1", "output": "1"},
            }
        ],
    }
    agent = PracticeAgent(StubProvider(payload))
    draft = await agent.design(request_of("PRACTICE"), EVIDENCE)

    assert draft.practice is not None
    assert draft.practice.problems[0].title == "练习：PY43-KC-001 基础题"


@pytest.mark.asyncio
async def test_practice_agent_accepts_questions_adapter_payload() -> None:
    payload = {
        "questions": [
            {
                "title": "Range Boundary Check",
                "description": "Write a function that counts numbers below a threshold.",
                "hints": ["Remember that range excludes the right endpoint."],
                "sample_io": {
                    "input": {"numbers": [1, 2, 3], "threshold": 3},
                    "output": 2,
                },
            }
        ]
    }
    agent = PracticeAgent(StubProvider(payload))
    draft = await agent.design(request_of("PRACTICE"), EVIDENCE)

    assert draft.practice is not None
    problem = draft.practice.problems[0]
    assert problem.title == "Range Boundary Check"
    assert problem.statement_md.startswith("Write a function")
    assert problem.sample_io[0].input == '{"numbers": [1, 2, 3], "threshold": 3}'
    assert problem.sample_io[0].output == "2"
    assert problem.difficulty == "basic"


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
async def test_code_lab_agent_compiles_safe_semantics_and_bound_evidence() -> None:
    provider = StubProvider(
        {
            "walkthroughs": [
                {
                    "title": "读取名字",
                    "narrative": "读取一行名字，再用格式化字符串输出问候语。",
                    "example_pattern": "assignment_trace",
                    "observation_goal": "区分变量名、变量值与重新赋值。",
                    "common_mistakes": ["忘记调用 input"],
                    "evidence_refs": ["page-101"],
                }
            ]
        }
    )
    draft = await CodeLabAgent(provider).design(request_of("CODE_LAB"), EVIDENCE)

    assert draft.code_lab is not None
    assert draft.code_lab.walkthroughs[0].title == "读取名字"
    assert draft.code_lab.walkthroughs[0].code.startswith("name = 'Ada'")
    assert provider.kwargs["guard_context"]["evidence_fields"] == [
        "walkthroughs.*.evidence_refs"
    ]
    assert provider.kwargs["guard_context"]["semantic_validator"](provider.payload) is None


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
async def test_reading_agent_compiles_semantic_evidence_and_prompts_with_rag() -> None:
    provider = StubProvider(
        {
            "items": [
                {
                    "summary_md": "先理解 input 会读取一行文本。",
                    "evidence_refs": ["page-101"],
                },
                {
                    "summary_md": "再练习把读取结果转换为需要的类型。",
                    "evidence_refs": ["page-101"],
                },
            ]
        }
    )
    draft = await ReadingAgent(provider).design(request_of("READING"), EVIDENCE)

    assert draft.reading is not None
    assert all(item.page_refs[0].page_id == 101 for item in draft.reading.items)
    prompt_text = "\n".join(message["content"] for message in provider.messages)
    assert "page-101" in prompt_text
    assert "input 函数的基本用法" in prompt_text
    assert provider.kwargs["guard_context"]["evidence_fields"] == [
        "items.*.evidence_refs"
    ]


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


@pytest.mark.asyncio
async def test_storyboard_agent_compiles_semantic_pacing_and_evidence() -> None:
    provider = StubProvider(
        {
            "scenes": [
                {
                    "title": "认识 input",
                    "narration": "先观察程序如何等待用户输入一行文本。",
                    "visual_intent": "像素终端等待键盘输入",
                    "pacing": "brief",
                    "evidence_refs": ["page-101"],
                },
                {
                    "title": "保存输入",
                    "narration": "再把 input 的返回值保存到变量中。",
                    "visual_intent": "文本从终端流入变量盒子",
                    "pacing": "normal",
                    "evidence_refs": ["page-101"],
                },
                {
                    "title": "检查类型",
                    "narration": "最后提醒 input 返回文本，需要时再转换类型。",
                    "visual_intent": "字符串标签与整数标签对比",
                    "pacing": "deep",
                    "evidence_refs": ["page-101"],
                },
            ]
        }
    )
    draft = await StoryboardAgent(provider).design(
        request_of("STORYBOARD"), EVIDENCE
    )

    assert draft.storyboard is not None
    assert [scene.duration_seconds for scene in draft.storyboard.scenes] == [20, 45, 75]
    assert all(scene.evidence_page_id == 101 for scene in draft.storyboard.scenes)
    assert all(scene.kc_codes == ["PY43-KC-001"] for scene in draft.storyboard.scenes)
    assert all("暖米黄色调" in scene.image_prompt for scene in draft.storyboard.scenes)
    assert provider.kwargs["guard_context"]["evidence_fields"] == [
        "scenes.*.evidence_refs"
    ]
