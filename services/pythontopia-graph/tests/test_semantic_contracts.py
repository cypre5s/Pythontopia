from __future__ import annotations

import ast

import pytest

from pythontopia_graph.contracts.semantic_ir import (
    CodeLabSemanticIR,
    CodeLabPlanSemanticIR,
    ContractCompiler,
    EvidenceBinder,
    GuideCoachSemanticIR,
    HandoutSemanticIR,
    PracticeSemanticIR,
    ReadingSemanticIR,
    ReflectionSemanticIR,
    StoryboardSemanticIR,
)
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, RagHit
from pythontopia_graph.schemas.resource import ResourceProduceRequest


def _evidence() -> EvidenceBundle:
    return EvidenceBundle(
        chunks=[
            RagHit(
                chunk_id="c-1",
                page_id=12,
                page_no=7,
                chapter_id=2,
                kc_codes=["PY43-KC-001"],
                text="input 会读取一行文本。",
                score=0.9,
            )
        ]
    )


def test_evidence_binder_builds_opaque_allowlist() -> None:
    context = EvidenceBinder.bind(_evidence())

    assert context.allowed_ids == ["page-12"]
    assert context.items[0].page_no == 7


def test_reflection_compiler_owns_event_ids_and_product_dimension_keys() -> None:
    semantic_ir = ReflectionSemanticIR.model_validate(
        {
            "observations": [
                {
                    "claim": "同类循环边界错误连续出现。",
                    "dimension_intent": "ERROR_PATTERN",
                    "tags": ["loop_boundary"],
                    "evidence_refs": ["event-10", "event-11"],
                    "confidence": 0.82,
                }
            ]
        }
    )

    result = ContractCompiler.compile_reflection(
        semantic_ir, allowed_event_ids=[10, 11]
    )

    assert result.insights[0].dimension_key == "error_pattern"
    assert result.insights[0].evidence_event_ids == [10, 11]
    assert "evidence_event_ids" not in semantic_ir.model_dump_json()


def test_reflection_compiler_rejects_unbound_event_evidence() -> None:
    semantic_ir = ReflectionSemanticIR.model_validate(
        {
            "observations": [
                {
                    "claim": "声称存在未提供的事件。",
                    "dimension_intent": "KNOWLEDGE_BASE",
                    "evidence_refs": ["event-999"],
                    "confidence": 0.5,
                }
            ]
        }
    )

    with pytest.raises(ValueError, match="outside the live allowlist"):
        ContractCompiler.compile_reflection(semantic_ir, allowed_event_ids=[10])


def test_practice_compiler_rejects_unbound_evidence() -> None:
    ir = PracticeSemanticIR.model_validate(
        {
            "problems": [
                {
                    "title": "读取名字",
                    "statement": "读取一个名字，并输出问候语。",
                    "hints": ["先用 input 读取。"],
                    "sample_input": "Ada",
                    "sample_output": "Hello Ada",
                }
            ],
            "evidence_refs": ["page-999"],
        }
    )

    with pytest.raises(ValueError, match="unknown evidence"):
        ContractCompiler.compile_practice(
            ir,
            request=ResourceProduceRequest(
                user_id=1, kc_codes=["PY43-KC-001"], type="PRACTICE"
            ),
            difficulty="basic",
            allowed_evidence_ids=["page-12"],
        )


def test_handout_and_reading_compilers_own_product_page_refs() -> None:
    evidence = EvidenceBinder.bind(_evidence())
    handout = ContractCompiler.compile_handout(
        HandoutSemanticIR.model_validate(
            {
                "title": "input 讲义",
                "summary": "入门",
                "sections": [
                    {
                        "heading": "读取文本",
                        "paragraphs": ["input 会读取一行。"],
                        "evidence_refs": ["page-12"],
                    }
                ],
            }
        ),
        evidence=evidence,
    )
    reading = ContractCompiler.compile_reading(
        ReadingSemanticIR.model_validate(
            {
                "items": [
                    {
                        "summary_md": "input 会读取一行文本。",
                        "evidence_refs": ["page-12"],
                    },
                    {
                        "summary_md": "读取后可按需要进行类型转换。",
                        "evidence_refs": ["page-12"],
                    },
                ]
            }
        ),
        evidence=evidence,
        kc_codes=["PY43-KC-001"],
    )

    assert handout.sections[0].page_refs[0].model_dump() == {
        "page_id": 12,
        "page_no": 7,
        "chapter_id": 2,
    }
    assert reading.items[0].page_refs[0].page_id == 12
    assert reading.items[0].kc_codes == ["PY43-KC-001"]


def test_handout_compiler_rejects_invented_evidence() -> None:
    semantic_ir = HandoutSemanticIR.model_validate(
        {
            "title": "bad",
            "sections": [
                {
                    "heading": "bad",
                    "paragraphs": ["invented evidence"],
                    "evidence_refs": ["page-999"],
                }
            ],
        }
    )

    with pytest.raises(ValueError, match="unknown evidence"):
        ContractCompiler.compile_handout(
            semantic_ir, evidence=EvidenceBinder.bind(_evidence())
        )


@pytest.mark.parametrize(
    "unsafe_code",
    [
        "import os\nos.system('echo unsafe')",
        "open('/tmp/unsafe', 'w')",
        "eval(input())",
    ],
)
def test_code_lab_compiler_rejects_execution_boundaries(unsafe_code: str) -> None:
    semantic_ir = CodeLabSemanticIR.model_validate(
        {
            "walkthroughs": [
                {
                    "title": "unsafe",
                    "narrative": "这段代码不应进入初学者代码实验室。",
                    "code": unsafe_code,
                    "expected_output": "",
                    "evidence_refs": [],
                }
            ]
        }
    )

    with pytest.raises(ValueError):
        ContractCompiler.compile_code_lab(
            semantic_ir, evidence=EvidenceBinder.bind(_evidence())
        )


@pytest.mark.parametrize(
    "pattern",
    [
        "assignment_trace",
        "conditional_branch",
        "while_control",
        "for_accumulate",
        "list_iteration",
        "function_scope",
        "string_cleanup",
        "exception_guard",
    ],
)
def test_code_plan_compiler_owns_valid_python_syntax(pattern: str) -> None:
    semantic_ir = CodeLabPlanSemanticIR.model_validate(
        {
            "walkthroughs": [
                {
                    "title": "教学模式",
                    "narrative": "先观察执行路径，再解释变量变化。",
                    "example_pattern": pattern,
                    "observation_goal": "说出关键状态变化。",
                    "common_mistakes": ["只看结果，没有追踪过程。"],
                    "evidence_refs": ["page-12"],
                }
            ]
        }
    )

    result = ContractCompiler.compile_code_lab_plan(
        semantic_ir, evidence=EvidenceBinder.bind(_evidence())
    )

    walkthrough = result.walkthroughs[0]
    ast.parse(walkthrough.code)
    assert walkthrough.expected_output
    assert "观察目标" in walkthrough.narrative


def test_code_plan_rejects_model_owned_free_code_fields() -> None:
    with pytest.raises(ValueError):
        CodeLabPlanSemanticIR.model_validate(
            {
                "walkthroughs": [
                    {
                        "title": "越权代码",
                        "narrative": "模型不应绕过 compiler 直接生成 Python 代码。",
                        "example_pattern": "assignment_trace",
                        "observation_goal": "观察变量绑定。",
                        "common_mistakes": ["把变量名和值混为一谈。"],
                        "evidence_refs": ["page-12"],
                        "code": "print('bypass')",
                    }
                ]
            }
        )


def test_storyboard_compiler_owns_product_mechanics() -> None:
    semantic_ir = StoryboardSemanticIR.model_validate(
        {
            "scenes": [
                {
                    "title": f"scene-{index}",
                    "narration": "用教学动画解释 input 的语义和返回值。",
                    "visual_intent": "像素终端与变量盒子",
                    "pacing": pacing,
                    "evidence_refs": ["page-12"],
                }
                for index, pacing in enumerate(("brief", "normal", "deep"), start=1)
            ]
        }
    )

    result = ContractCompiler.compile_storyboard(
        semantic_ir,
        evidence=EvidenceBinder.bind(_evidence()),
        kc_codes=["PY43-KC-001"],
    )

    assert [scene.duration_seconds for scene in result.scenes] == [20, 45, 75]
    assert all(scene.evidence_page_id == 12 for scene in result.scenes)
    assert all(scene.narration_tts_voice == "x4_yezi" for scene in result.scenes)


def test_guide_coach_ir_excludes_product_mechanics() -> None:
    properties = GuideCoachSemanticIR.model_json_schema()["properties"]

    assert "tool_name" not in properties
    assert "allowed_followups" not in properties


@pytest.mark.parametrize(
    ("intent", "expected"),
    [
        ("READ", "READING"),
        ("REVIEW", "KNOWLEDGE_REVIEW"),
        ("TRACE", "EXECUTION_TRACE"),
        ("CHECK", "AC_REVIEW"),
    ],
)
def test_guide_event_intent_is_compiled_to_product_workflow_event(
    intent: str, expected: str
) -> None:
    resolved = ContractCompiler.resolve_guide_event(
        intent,
        ["READING", "KNOWLEDGE_REVIEW", "EXECUTION_TRACE", "AC_REVIEW"],
    )

    assert resolved == expected


def test_guide_event_intent_fails_fast_when_unrecognized_or_disallowed() -> None:
    with pytest.raises(ValueError, match="unrecognized"):
        ContractCompiler.resolve_guide_event("MAKE_COFFEE", ["READING"])
    with pytest.raises(ValueError, match="live allowlist"):
        ContractCompiler.resolve_guide_event("CHECK", ["READING"])
