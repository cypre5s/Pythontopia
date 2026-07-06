"""题目对象结构校验：必填字段、KC 格式、难度范围、test_cases 数量与题面长度上限。"""

from __future__ import annotations

import re

from .schemas import BossProblem, ReviewIssue, ReviewVerdict

KC_CODE_RE = re.compile(r"^PY43-KC-\d{3}$")
MAX_STATEMENT_LEN = 1500  # 中文题面按字符计，上限略宽于纯 ASCII
MIN_TEST_CASES = 3
MAX_HINTS = 3


def validate_problem_struct(problem: BossProblem) -> ReviewVerdict:
    """校验 ``BossProblem`` 结构与字段约束，不通过则 reject。"""
    issues: list[ReviewIssue] = []

    if not problem.title or not problem.title.strip():
        issues.append(_err("missing_title", "title must be non-empty"))
    if not problem.statement or not problem.statement.strip():
        issues.append(_err("missing_statement", "statement must be non-empty"))
    if not problem.reference_solution or not problem.reference_solution.strip():
        issues.append(_err("missing_reference_solution", "reference_solution required"))

    if not isinstance(problem.kc_code, str) or not KC_CODE_RE.match(problem.kc_code):
        issues.append(
            _err("invalid_kc_code", f"kc_code must match PY43-KC-NNN, got {problem.kc_code!r}")
        )

    if not isinstance(problem.difficulty, int) or not 1 <= problem.difficulty <= 5:
        issues.append(
            _err("invalid_difficulty", f"difficulty must be int 1..5, got {problem.difficulty!r}")
        )

    if not 1 <= problem.chapter <= 7:
        issues.append(_err("invalid_chapter", f"chapter must be 1..7, got {problem.chapter}"))
    if not 1 <= problem.stage <= 4:
        issues.append(_err("invalid_stage", f"stage must be 1..4, got {problem.stage}"))

    if not isinstance(problem.test_cases, list) or len(problem.test_cases) < MIN_TEST_CASES:
        issues.append(_err("insufficient_test_cases", f"need >= {MIN_TEST_CASES} test_cases"))
    else:
        for i, tc in enumerate(problem.test_cases):
            if not hasattr(tc, "input") or tc.input is None:
                issues.append(_err("missing_test_input", f"test_case[{i}] missing input"))
            if not hasattr(tc, "expected_output") or tc.expected_output is None:
                issues.append(
                    _err("missing_test_expected", f"test_case[{i}] missing expected_output")
                )
        if not any(tc.is_sample for tc in problem.test_cases):
            issues.append(
                _err(
                    "missing_sample_flag",
                    "at least one test_case should be marked is_sample=True",
                )
            )

    if len(problem.statement) > MAX_STATEMENT_LEN:
        issues.append(
            _err(
                "statement_too_long",
                f"statement length {len(problem.statement)} exceeds {MAX_STATEMENT_LEN}",
            )
        )

    if len(problem.hints) > MAX_HINTS:
        issues.append(
            ReviewIssue(
                review="struct",
                severity="warning",
                code="too_many_hints",
                message=f"len(hints)={len(problem.hints)} exceeds soft cap {MAX_HINTS}",
            )
        )

    errors = [i for i in issues if i.severity == "error"]
    return ReviewVerdict(review="struct", passed=len(errors) == 0, issues=issues)


def _err(code: str, message: str) -> ReviewIssue:
    return ReviewIssue(review="struct", severity="error", code=code, message=message)
