"""difficulty_estimator 单元测试。"""

from __future__ import annotations

import pytest

from pythontopia_graph.agents.boss.difficulty_estimator import estimate_difficulty
from pythontopia_graph.agents.boss.schemas import BossProblem, TestCase


def _problem(
    code: str,
    *,
    declared: int,
    statement: str = "一道题",
) -> BossProblem:
    return BossProblem(
        problem_id="ai",
        chapter=1,
        stage=1,
        kc_code="PY43-KC-001",
        title="x",
        statement=statement,
        reference_solution=code,
        test_cases=[TestCase("", "", is_sample=True), TestCase("", ""), TestCase("", "")],
        difficulty=declared,
    )


def test_basic_print_declared_1_matches_estimated_1() -> None:
    p = _problem("print('hello')", declared=1)
    v = estimate_difficulty(p)
    assert v.passed
    assert v.metadata["estimated"] == 1


def test_declared_off_by_one_passes_with_warning() -> None:
    p = _problem("print('hello')", declared=2)
    v = estimate_difficulty(p)
    assert v.passed
    assert any(i.code == "difficulty_offset_minor" for i in v.issues)


def test_declared_off_by_two_overwrites_with_warning() -> None:
    p = _problem("print('hello')", declared=3)
    v = estimate_difficulty(p)
    assert v.passed
    assert p.difficulty == 1
    assert any(i.code == "difficulty_overwritten" for i in v.issues)


def test_declared_far_off_rejects() -> None:
    p = _problem("print('hello')", declared=5)
    v = estimate_difficulty(p)
    assert not v.passed
    assert any(i.code == "difficulty_far_off" for i in v.issues)


def test_loop_increases_score() -> None:
    code = "n=int(input())\nfor i in range(n):\n    print(i)"
    p = _problem(code, declared=2)
    v = estimate_difficulty(p)
    assert v.passed
    assert v.metadata["features"]["loop_count"] == 1


def test_nested_loops_pushes_to_medium() -> None:
    code = (
        "n=int(input())\nfor i in range(n):\n    for j in range(n):\n        print(i*j)"
    )
    p = _problem(code, declared=3)
    v = estimate_difficulty(p)
    assert v.passed
    assert v.metadata["estimated"] >= 2


def test_function_def_increases_score() -> None:
    code = "def f(a, b):\n    return a + b\nprint(f(1,2))"
    p = _problem(code, declared=2)
    v = estimate_difficulty(p)
    assert v.passed
    assert v.metadata["features"]["def_count"] == 1


def test_class_pushes_to_harder() -> None:
    code = "class A:\n    def m(self):\n        return 1\nprint(A().m())"
    p = _problem(code, declared=3)
    v = estimate_difficulty(p)
    assert v.passed


def test_max_complexity_solution_estimated_high() -> None:
    code = (
        "class T:\n    def __init__(self):\n        self.v=0\n"
        "    def run(self, n):\n        for i in range(n):\n"
        "            for j in range(n):\n                if i+j > 0:\n"
        "                    while i>0:\n                        self.v += 1\n"
        "                        break\n        return self.v\n"
        "print(T().run(int(input())))"
    )
    p = _problem(code, declared=5)
    v = estimate_difficulty(p)
    assert v.passed
    assert v.metadata["estimated"] >= 4


def test_syntax_error_solution_still_yields_features() -> None:
    p = _problem("def broken(", declared=1)
    v = estimate_difficulty(p)
    # SyntaxError 不直接 reject；无 AST 时仍应给出 estimated score
    assert "estimated" in v.metadata


@pytest.mark.parametrize(
    "code,declared,expected_passed",
    [
        ("print(1)", 1, True),
        ("print(1)", 2, True),
        ("print(1)", 3, True),
        ("print(1)", 4, False),
        ("print(1)", 5, False),
    ],
)
def test_threshold_grid(code: str, declared: int, expected_passed: bool) -> None:
    p = _problem(code, declared=declared)
    v = estimate_difficulty(p)
    assert v.passed == expected_passed


def test_metadata_includes_features_dict() -> None:
    p = _problem("print(1)", declared=1)
    v = estimate_difficulty(p)
    feats = v.metadata["features"]
    assert "statement_len" in feats
    assert "ast_depth" in feats
    assert "loop_count" in feats
    assert "if_count" in feats
    assert "def_count" in feats
    assert "solution_lines" in feats


def test_zero_diff_is_passed_no_issues() -> None:
    p = _problem("print(1)", declared=1)
    v = estimate_difficulty(p)
    assert v.passed
    assert v.issues == []


def test_long_statement_affects_score() -> None:
    code = "print(1)"
    long_stmt = "a" * 1000
    p = _problem(code, declared=1, statement=long_stmt)
    v = estimate_difficulty(p)
    assert v.metadata["features"]["statement_len"] == 1000


def test_multiple_ifs_increase_score() -> None:
    code = "x=int(input())\nif x>0:\n    print(1)\nif x>1:\n    print(2)\nif x>2:\n    print(3)"
    p = _problem(code, declared=2)
    v = estimate_difficulty(p)
    assert v.metadata["features"]["if_count"] == 3
