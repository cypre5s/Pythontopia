from __future__ import annotations

from pythontopia_graph.agents.guide_coach_agent import CoachContext, GuideCoachAgent
from pythontopia_graph.agents.practice_agent import PracticeAgent
from pythontopia_graph.llm.provider import LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, RagHit
from pythontopia_graph.schemas.resource import ResourceProduceRequest
from pythontopia_graph.schemas.tutor_workflow import TutorStudentState, WorkflowProblemContext


class CaptureProvider:
    def __init__(self, payload: dict[str, object]) -> None:
        self.payload = payload
        self.kwargs: dict[str, object] = {}

    async def chat(self, messages, **kwargs):  # type: ignore[no-untyped-def]
        self.kwargs = kwargs
        return LlmResponse(content_json=self.payload)


async def test_practice_passes_schema_and_bound_evidence_to_provider() -> None:
    provider = CaptureProvider(
        {
            "problems": [
                {
                    "title": "读取名字",
                    "statement": "读取名字并输出一行问候语。",
                    "hints": ["先调用 input。"],
                    "sample_input": "Ada",
                    "sample_output": "Hello Ada",
                }
            ],
            "evidence_refs": ["page-21"],
        }
    )
    evidence = EvidenceBundle(
        chunks=[
            RagHit(
                chunk_id="c1",
                page_id=21,
                page_no=8,
                text="input 读取文本。",
                score=0.8,
            )
        ]
    )

    draft = await PracticeAgent(provider).design(
        ResourceProduceRequest(user_id=1, kc_codes=["PY43-KC-001"], type="PRACTICE"),
        evidence,
    )

    guard = provider.kwargs["guard_context"]
    assert isinstance(guard, dict)
    assert guard["evidence_ids"] == ["page-21"]
    assert guard["json_schema"]["title"] == "PracticeSemanticIR"
    assert draft.practice is not None and draft.practice.problems[0].kc_codes == ["PY43-KC-001"]


async def test_guide_coach_passes_semantic_ir_schema_to_provider() -> None:
    provider = CaptureProvider(
        {
            "next_event": "READING",
            "action_goal": "先找出输入和输出。",
            "stop_condition": "学生说出输入输出关系。",
            "state_delta": {"hint_level": 0},
            "evidence_refs": [],
            "confidence": 0.8,
            "reason_summary": "先完成审题。",
        }
    )
    context = CoachContext(
        requested_event="SKELETON",
        allowed_events=["READING"],
        state=TutorStudentState(
            session_id="s1", user_id=1, problem_id=1, phase="READING"
        ),
        problem=WorkflowProblemContext(
            problem_id=1, title="输入", statement="读取一个整数。", kc_codes=[]
        ),
    )

    decision = await GuideCoachAgent(provider).decide(context)

    guard = provider.kwargs["guard_context"]
    assert isinstance(guard, dict)
    assert guard["json_schema"]["title"] == "GuideCoachSemanticIR"
    assert decision.next_event == "READING"
    assert decision.tool_name == "read_problem"
