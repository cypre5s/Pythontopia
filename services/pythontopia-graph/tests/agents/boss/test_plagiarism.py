"""R-13 §5 Phase 5：plagiarism / 敏感词测试。"""

from __future__ import annotations

from pythontopia_graph.agents.boss.plagiarism_checker import (
    ExistingProblem,
    is_too_similar_to_existing,
    list_sensitive_keywords,
)
from pythontopia_graph.agents.boss.schemas import BossProblem, TestCase


def _problem(statement: str) -> BossProblem:
    return BossProblem(
        problem_id="ai",
        chapter=1,
        stage=1,
        kc_code="PY43-KC-001",
        title="x",
        statement=statement,
        reference_solution="print(1)",
        test_cases=[TestCase("", "", is_sample=True), TestCase("", ""), TestCase("", "")],
        difficulty=1,
    )


def test_empty_existing_list_passes() -> None:
    v = is_too_similar_to_existing(_problem("一道全新的题目"), [])
    assert v.passed


def test_identical_statement_rejected() -> None:
    existing = [ExistingProblem("p1", "请输出 hello world")]
    v = is_too_similar_to_existing(_problem("请输出 hello world"), existing)
    assert not v.passed
    assert any(i.code == "too_similar" for i in v.issues)


def test_very_similar_statement_above_threshold_rejected() -> None:
    existing = [ExistingProblem("p1", "请输入两个数 a 和 b 并输出 a+b 的值")]
    v = is_too_similar_to_existing(
        _problem("请输入两个数 a 和 b 并输出 a+b 的值。"), existing
    )
    assert not v.passed


def test_low_similarity_passes() -> None:
    existing = [ExistingProblem("p1", "实现一个深度优先搜索")]
    v = is_too_similar_to_existing(_problem("用 print 输出 hello"), existing)
    assert v.passed


def test_threshold_override_loosens() -> None:
    existing = [ExistingProblem("p1", "请输出 hello world")]
    v = is_too_similar_to_existing(
        _problem("请输出 hello world"), existing, threshold=1.01
    )
    # threshold > 1 让任何相似度都不触发 — 简单冒烟
    assert v.passed


def test_sensitive_word_in_statement_rejected() -> None:
    v = is_too_similar_to_existing(
        _problem("题目中包含 色情 不应出现"), []
    )
    assert not v.passed
    assert any(i.code == "sensitive_words" for i in v.issues)


def test_sensitive_word_in_solution_rejected() -> None:
    p = _problem("正常题目")
    p.reference_solution = "# fuck this\nprint(1)"
    v = is_too_similar_to_existing(p, [])
    assert not v.passed


def test_multiple_existing_problems_picks_max() -> None:
    existing = [
        ExistingProblem("p1", "全然不同的题目"),
        ExistingProblem("p2", "请输出 hello world"),
        ExistingProblem("p3", "另一个完全不同"),
    ]
    v = is_too_similar_to_existing(_problem("请输出 hello world"), existing)
    assert not v.passed
    # high_sim metadata 应包含 p2 命中
    high = v.metadata["high_sim"]
    assert any(pid == "p2" for pid, _ in high)


def test_metadata_contains_checked_count() -> None:
    existing = [ExistingProblem(f"p{i}", f"题目 {i}") for i in range(5)]
    v = is_too_similar_to_existing(_problem("新题目"), existing)
    assert v.metadata["checked"] == 5


def test_list_sensitive_keywords_includes_known() -> None:
    items = set(list_sensitive_keywords())
    assert "色情" in items
    assert "歧视" in items
