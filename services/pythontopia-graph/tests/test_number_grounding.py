"""hallucination_detector 统计数字 grounding 检查单元测试。"""

from __future__ import annotations

from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, RagHit
from pythontopia_graph.nodes.hallucination_detector import hallucination_detector
from pythontopia_graph.schemas.resource import (
    HandoutContent,
    HandoutSection,
    PracticeContent,
    ProblemSpec,
    ResourceDraft,
    SampleIo,
)

VALID_KCS = {"PY43-KC-001"}


def _evidence(text: str) -> EvidenceBundle:
    return EvidenceBundle(
        chunks=[
            RagHit(
                chunk_id="c1",
                page_id=10,
                page_no=3,
                kc_codes=["PY43-KC-001"],
                text=text,
                score=0.9,
            )
        ],
        no_evidence_found=False,
    )


def _handout(paragraph: str) -> ResourceDraft:
    return ResourceDraft(
        title="讲义",
        summary="t",
        content_format="MARKDOWN",
        handout=HandoutContent(
            sections=[HandoutSection(heading="概念", paragraphs=[paragraph])]
        ),
    )


def test_flags_fabricated_percentage_not_in_evidence() -> None:
    draft = _handout("根据研究，87% 的学生认为循环很难。")
    result = hallucination_detector(draft, _evidence("循环是基础结构。"), VALID_KCS)
    assert not result.passed
    assert any("Ungrounded numbers" in reason for reason in result.reasons)


def test_allows_percentage_present_in_evidence() -> None:
    draft = _handout("课件提到 87% 场景适用。")
    result = hallucination_detector(draft, _evidence("约 87% 的案例适用此法。"), VALID_KCS)
    assert result.passed


def test_ignores_small_counts() -> None:
    # 小整数（步骤数/分钟）属正常表述，不应触发。
    draft = _handout("循环有 3 种写法，建议每天练习 10 分钟。")
    result = hallucination_detector(draft, _evidence("循环结构介绍。"), VALID_KCS)
    assert result.passed


def test_skips_when_no_evidence() -> None:
    draft = _handout("根据统计，2024 年有 95% 的人用 Python。")
    result = hallucination_detector(
        draft, EvidenceBundle(chunks=[], no_evidence_found=True), VALID_KCS
    )
    assert result.passed


# ── P3.2 难度声明校验（plan 3.2 step 2） ──


def _practice(statement: str, *, difficulty: str = "basic", title: str = "练习题") -> ResourceDraft:
    return ResourceDraft(
        title="练习",
        summary="t",
        content_format="STRUCT_JSON",
        practice=PracticeContent(
            problems=[
                ProblemSpec(
                    title=title,
                    statement_md=statement,
                    sample_io=[SampleIo(input="1", output="1")],
                    kc_codes=["PY43-KC-001"],
                    difficulty=difficulty,  # type: ignore[arg-type]
                )
            ]
        ),
    )


def test_contradictory_difficulty_declaration_flagged() -> None:
    # 字段标 basic，但题面声明「难度：困难」→ 矛盾，必须拦截。
    draft = _practice("请完成以下任务。难度：困难。", difficulty="basic")
    result = hallucination_detector(draft, _evidence("任务说明。"), VALID_KCS)
    assert not result.passed
    assert any("difficulty" in r.lower() for r in result.reasons)


def test_unknown_difficulty_declaration_flagged() -> None:
    # 题面声明未知难度标签「专家」（不属于 basic/medium/hard）→ 拦截。
    draft = _practice("请完成任务。难度：专家。", difficulty="basic")
    result = hallucination_detector(draft, _evidence("任务说明。"), VALID_KCS)
    assert not result.passed
    assert any("difficulty" in r.lower() for r in result.reasons)


def test_consistent_difficulty_declaration_passes() -> None:
    # 字段 hard 且题面「难度：困难」一致 → 通过。
    draft = _practice("挑战任务。难度：困难。", difficulty="hard")
    result = hallucination_detector(draft, _evidence("任务说明。"), VALID_KCS)
    assert result.passed, result.reasons


def test_no_difficulty_declaration_passes() -> None:
    # 题面无显式难度声明 → 不触发难度校验。
    draft = _practice("请读入数据并输出求和结果。", difficulty="medium")
    result = hallucination_detector(draft, _evidence("求和说明。"), VALID_KCS)
    assert result.passed, result.reasons


def test_invalid_difficulty_field_flagged() -> None:
    # 纵深防御：即便绕过 schema 注入非法 difficulty 字段，detector 也拦截。
    problem = ProblemSpec.model_construct(
        title="练习题",
        statement_md="完成任务。",
        hints=[],
        sample_io=[SampleIo(input="1", output="1")],
        kc_codes=["PY43-KC-001"],
        difficulty="expert",
    )
    practice = PracticeContent.model_construct(problems=[problem])
    draft = ResourceDraft.model_construct(
        title="练习",
        summary="t",
        content_format="STRUCT_JSON",
        handout=None,
        mindmap=None,
        practice=practice,
        code_lab=None,
        reading=None,
        storyboard=None,
    )
    result = hallucination_detector(draft, _evidence("任务说明。"), VALID_KCS)
    assert not result.passed
    assert any("invalid difficulty" in r.lower() for r in result.reasons)
