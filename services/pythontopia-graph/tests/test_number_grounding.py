"""hallucination_detector 统计数字 grounding 检查单元测试。"""

from __future__ import annotations

from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, RagHit
from pythontopia_graph.nodes.hallucination_detector import hallucination_detector
from pythontopia_graph.schemas.resource import (
    HandoutContent,
    HandoutSection,
    ResourceDraft,
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
