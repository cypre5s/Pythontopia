"""struct_validator 单元测试。"""

from __future__ import annotations

import pytest

from pythontopia_graph.agents.boss import validate_problem_struct
from pythontopia_graph.agents.boss.schemas import BossProblem, TestCase


def _valid_problem(**overrides) -> BossProblem:
    base = BossProblem(
        problem_id="ai-test-1",
        chapter=1,
        stage=1,
        kc_code="PY43-KC-001",
        title="加法",
        statement="读入两个整数 a b，输出 a+b",
        reference_solution="a, b = map(int, input().split())\nprint(a + b)",
        test_cases=[
            TestCase(input="1 2", expected_output="3", is_sample=True),
            TestCase(input="10 20", expected_output="30"),
            TestCase(input="100 200", expected_output="300"),
        ],
        difficulty=1,
        hints=["用 input()", "用 map(int, ...)"],
    )
    for k, v in overrides.items():
        setattr(base, k, v)
    return base


def test_valid_problem_passes() -> None:
    verdict = validate_problem_struct(_valid_problem())
    assert verdict.passed
    assert all(i.severity != "error" for i in verdict.issues)


@pytest.mark.parametrize("field", ["title", "statement", "reference_solution"])
def test_missing_required_string_field(field: str) -> None:
    verdict = validate_problem_struct(_valid_problem(**{field: ""}))
    assert not verdict.passed
    assert any(f"missing_{field.split('_')[0]}" in i.code for i in verdict.issues)


def test_invalid_kc_code() -> None:
    verdict = validate_problem_struct(_valid_problem(kc_code="KC-001"))
    assert not verdict.passed
    assert any(i.code == "invalid_kc_code" for i in verdict.issues)


@pytest.mark.parametrize("difficulty", [0, 6, -1, 99, "easy"])
def test_invalid_difficulty(difficulty) -> None:
    verdict = validate_problem_struct(_valid_problem(difficulty=difficulty))
    assert not verdict.passed
    assert any(i.code == "invalid_difficulty" for i in verdict.issues)


@pytest.mark.parametrize("chapter", [0, 8, 99])
def test_invalid_chapter(chapter: int) -> None:
    verdict = validate_problem_struct(_valid_problem(chapter=chapter))
    assert not verdict.passed


@pytest.mark.parametrize("stage", [0, 5, 99])
def test_invalid_stage(stage: int) -> None:
    verdict = validate_problem_struct(_valid_problem(stage=stage))
    assert not verdict.passed


def test_too_few_test_cases() -> None:
    verdict = validate_problem_struct(_valid_problem(test_cases=[]))
    assert not verdict.passed
    assert any(i.code == "insufficient_test_cases" for i in verdict.issues)


def test_test_case_must_have_sample() -> None:
    problem = _valid_problem()
    problem.test_cases = [
        TestCase(input="1", expected_output="1", is_sample=False),
        TestCase(input="2", expected_output="2", is_sample=False),
        TestCase(input="3", expected_output="3", is_sample=False),
    ]
    verdict = validate_problem_struct(problem)
    assert not verdict.passed
    assert any(i.code == "missing_sample_flag" for i in verdict.issues)


def test_statement_length_limit() -> None:
    long_statement = "a" * 2000
    verdict = validate_problem_struct(_valid_problem(statement=long_statement))
    assert not verdict.passed
    assert any(i.code == "statement_too_long" for i in verdict.issues)


def test_too_many_hints_warning_only() -> None:
    verdict = validate_problem_struct(_valid_problem(hints=["a", "b", "c", "d", "e"]))
    # warning 不阻断，issues 仍含 too_many_hints
    assert verdict.passed
    assert any(i.code == "too_many_hints" for i in verdict.issues)
