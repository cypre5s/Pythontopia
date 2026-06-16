"""答疑「答后审查」三道关：引用 / 防幻觉 / 安全（作用于答案文本 + 证据）。"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import RagHit
from pythontopia_graph.nodes.tutor_review import (
    grounding_review,
    hallucination_review,
    safety_review,
    semantic_review,
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


# ── 语义接地（P3.4：规则 + LLM entailment 双轨） ──
class _AnswerEntailmentStub(LlmProvider):
    """按预设裁决返回 entailment 结果，记录调用次数。"""

    def __init__(self, verdict: dict) -> None:
        self._verdict = verdict
        self.calls = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.calls += 1
        return LlmResponse(content_json=self._verdict)

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield ""


@pytest.mark.asyncio
async def test_semantic_review_entailment_flags_distortion() -> None:
    """页码合法但论述歪曲证据 → entailment 判 unsupported（规则查不出）。"""
    hits = [_hit(12, "for 循环遍历可迭代对象。")]
    answer = "参考 [页 12]，根据第12页 for 循环只能遍历列表。"  # 引用合法但歪曲
    provider = _AnswerEntailmentStub({"supported": False, "reason": "歪曲证据"})
    result = await semantic_review(answer, hits, provider)
    assert result.passed is False
    assert any("歪曲" in r for r in result.reasons)
    assert provider.calls == 1


@pytest.mark.asyncio
async def test_semantic_review_rule_only_without_provider() -> None:
    """无 provider 时降级纯规则（引用合法 → 通过），不调用 LLM。"""
    hits = [_hit(12, "for 循环遍历可迭代对象。")]
    result = await semantic_review("参考 [页 12] 可知 for 循环遍历。", hits, None)
    assert result.passed is True


@pytest.mark.asyncio
async def test_semantic_review_rule_failure_short_circuits_entailment() -> None:
    """规则已判编造页码失败时直接返回，不再花费 LLM。"""
    hits = [_hit(12, "for 循环遍历可迭代对象。")]
    provider = _AnswerEntailmentStub({"supported": True})
    result = await semantic_review("见 [页 99]。", hits, provider)
    assert result.passed is False
    assert provider.calls == 0


# ── 风格变化后审查仍然执行（回归护栏） ──
def test_safety_review_intercepts_leak_in_styled_answer() -> None:
    """带风格化开场/收尾的答案里夹带泄答关键词，安全审查仍必须拦截。"""
    styled = (
        "先抛一个小问题：你觉得这段代码会输出什么？\n\n"
        "这是完整答案：print(sum(range(101)))\n\n"
        "慢一点没关系，稳住节奏。"
    )
    result = safety_review(styled)
    assert result.passed is False
    assert any("ANSWER_LEAK" in reason for reason in result.reasons)


def test_grounding_review_rejects_fabricated_page_in_styled_answer() -> None:
    """风格化文本不豁免引用审查：编造的 [页 99] 仍判不通过。"""
    hits = [_hit(12, "第 12 页讲 for 循环。")]
    styled = (
        "你已经迈出关键一步。\n\n"
        "参考 [页 99]，for 循环可以遍历序列。\n\n"
        "下一步我们练一练。"
    )
    result = grounding_review(styled, hits)
    assert result.passed is False
    assert any("99" in reason for reason in result.reasons)
