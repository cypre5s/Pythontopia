"""Golden 题库完整性：28 道题（7 章×4 stage）须通过非执行审查，越界值应 clamp。"""

from __future__ import annotations

import pytest

from pythontopia_graph.agents.boss.golden_problems import (
    all_golden_problems,
    get_golden_problem,
    get_golden_problem_for_kc,
)
from pythontopia_graph.agents.boss.orchestrator import run_all_reviews


def test_all_golden_problems_have_28_entries() -> None:
    problems = all_golden_problems()
    assert len(problems) == 28
    chapters = sorted({(p.chapter, p.stage) for p in problems})
    expected = [(c, s) for c in range(1, 8) for s in range(1, 5)]
    assert chapters == expected


@pytest.mark.parametrize("chapter", list(range(1, 8)))
@pytest.mark.parametrize("stage", list(range(1, 5)))
def test_golden_problem_passes_non_execution_reviews(chapter: int, stage: int) -> None:
    p = get_golden_problem(chapter, stage)
    # 单测假 Piston 只返回固定记录，不执行 28 份 reference_solution。
    review = run_all_reviews(p, skip_self_test=True)
    error_msgs = [
        f"[{i.review}:{i.code}] {i.message}"
        for i in review.issues
        if i.severity == "error"
    ]
    assert review.passed, (
        f"golden c{chapter}-s{stage} failed 5 reviews: {error_msgs}"
    )


def test_get_golden_problem_clamps_out_of_range_chapter() -> None:
    low = get_golden_problem(-1, 1)
    high = get_golden_problem(99, 1)
    assert low.chapter == 1
    assert high.chapter == 7


def test_get_golden_problem_clamps_out_of_range_stage() -> None:
    low = get_golden_problem(3, 0)
    high = get_golden_problem(3, 10)
    assert low.stage == 1
    assert high.stage == 4


def test_golden_problem_id_format() -> None:
    p = get_golden_problem(5, 2)
    assert p.problem_id == "golden-c5-s2"
    assert p.metadata.get("source") == "golden_fallback"


def test_golden_problem_each_chapter_has_distinct_kc() -> None:
    p1 = get_golden_problem(1, 1)
    p2 = get_golden_problem(2, 1)
    p4 = get_golden_problem(4, 1)
    assert p1.kc_code != p2.kc_code != p4.kc_code


def test_kc_golden_fallback_requires_matching_chapter_and_supported_kc() -> None:
    assert get_golden_problem_for_kc(2, 2, "PY43-KC-039", 2) is None
    assert get_golden_problem_for_kc(2, 2, "PY43-KC-010", 2) is None
