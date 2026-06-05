"""答疑「答后审查」三道关：引用 / 防幻觉 / 安全（作用于答案文本 + 证据）。"""

from __future__ import annotations

from pythontopia_graph.nodes.evidence_fetcher import RagHit
from pythontopia_graph.nodes.tutor_review import (
    grounding_review,
    hallucination_review,
    safety_review,
)


def _hit(page_no: int, text: str) -> RagHit:
    return RagHit(
        chunk_id=f"c{page_no}",
        page_id=page_no + 100,
        page_no=page_no,
        chapter_id=1,
        kc_codes=["PY43-KC-001"],
        text=text,
        score=0.8,
    )


# ── 引用审查 ──
def test_grounding_no_evidence_marks_general_knowledge() -> None:
    result = grounding_review("for 循环用于遍历。", [])
    assert result.passed is True
    assert "NO_EVIDENCE" in result.reasons


def test_grounding_passes_when_citation_in_evidence() -> None:
    hits = [_hit(12, "第 12 页讲 for 循环。")]
    result = grounding_review("参考 [页 12] 可知 for 循环。", hits)
    assert result.passed is True


def test_grounding_fails_on_fabricated_page() -> None:
    hits = [_hit(12, "第 12 页讲 for 循环。")]
    result = grounding_review("见 [页 99] 与 [页 12]。", hits)
    assert result.passed is False
    assert any("99" in reason for reason in result.reasons)


def test_grounding_passes_when_no_citation_written() -> None:
    # 导师给引导式回答、未写 [页 X] 时不判失败，只拦「编造的页码」。
    hits = [_hit(12, "第 12 页讲 for 循环。")]
    assert grounding_review("先想想循环变量怎么变化。", hits).passed is True


# ── 防幻觉（统计型数字） ──
def test_hallucination_flags_ungrounded_number() -> None:
    hits = [_hit(12, "Python 由 Guido 设计。")]
    result = hallucination_review("Python 占据了 87% 的市场份额。", hits)
    assert result.passed is False
    assert any("87%" in reason for reason in result.reasons)


def test_hallucination_passes_when_number_in_evidence() -> None:
    hits = [_hit(12, "1991 年 Python 首次发布。")]
    assert hallucination_review("Python 诞生于 1991 年。", hits).passed is True


def test_hallucination_skipped_without_evidence() -> None:
    assert hallucination_review("大约有 9999 种用法。", []).passed is True


# ── 安全 ──
def test_safety_flags_answer_leak() -> None:
    result = safety_review("这是完整答案：print(sum(range(101)))")
    assert result.passed is False
    assert any("ANSWER_LEAK" in reason for reason in result.reasons)


def test_safety_flags_pii() -> None:
    assert safety_review("联系我 13800138000。").passed is False


def test_safety_passes_clean_answer() -> None:
    assert safety_review("for 循环可以遍历列表，先想想边界条件。").passed is True
