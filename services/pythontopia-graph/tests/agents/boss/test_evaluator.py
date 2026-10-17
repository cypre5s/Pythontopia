"""evaluate_student_code tests with fixed, non-executing fake Piston responses."""

from __future__ import annotations

import subprocess

import httpx
import pytest

from pythontopia_graph.agents.boss import execution_sandbox
from pythontopia_graph.agents.boss.evaluator import (
    build_failure_hint,
    evaluate_student_code,
)
from pythontopia_graph.agents.boss.schemas import TestCase


def _tc(input_text: str, expected: str, *, sample: bool = False) -> TestCase:
    return TestCase(input=input_text, expected_output=expected, is_sample=sample)


def test_full_pass_returns_passed_true() -> None:
    code = "a, b = map(int, input().split())\nprint(a + b)"
    cases = [_tc("1 2", "3", sample=True), _tc("10 -7", "3"), _tc("0 0", "0")]
    result = evaluate_student_code(code, cases)
    assert result.passed
    assert result.passed_count == 3
    assert result.total_count == 3
    assert result.first_failure is None
    assert result.failed_indices == []
    assert result.runtime_error is None


def test_partial_pass_records_first_failure() -> None:
    code = "n = int(input())\nprint(n + 1)"
    cases = [
        _tc("1", "2", sample=True),
        _tc("3", "5"),  # 期望 5，实际输出 4
        _tc("4", "5"),
    ]
    result = evaluate_student_code(code, cases)
    assert not result.passed
    assert result.passed_count == 2
    assert result.failed_indices == [1]
    assert result.first_failure is not None
    assert result.first_failure.index == 1
    assert result.first_failure.expected == "5"
    assert result.first_failure.actual == "4"


def test_forbidden_import_returns_runtime_error() -> None:
    code = "import socket\nprint(0)"
    cases = [_tc("", "0", sample=True), _tc("", "0"), _tc("", "0")]
    result = evaluate_student_code(code, cases)
    assert not result.passed
    assert result.passed_count == 0
    assert result.runtime_error is not None
    assert "forbidden_imports" in result.runtime_error
    assert "socket" in result.runtime_error


def test_dynamic_import_returns_runtime_error_before_sandbox(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("PYT_PISTON_URL", raising=False)
    result = evaluate_student_code("__import__('os').system('true')", [_tc("", "")])

    assert not result.passed
    assert result.runtime_error is not None
    assert "forbidden_imports" in result.runtime_error
    assert "__import__" in result.runtime_error


def test_importing_builtin_importer_under_alias_is_rejected_before_sandbox(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("PYT_PISTON_URL", raising=False)
    code = "from builtins import __import__ as load\nload('os').getcwd()"

    result = evaluate_student_code(code, [_tc("", "")])

    assert not result.passed
    assert result.runtime_error is not None
    assert "forbidden_imports" in result.runtime_error
    assert "__import__" in result.runtime_error


@pytest.mark.parametrize(
    "code",
    [
        "builtins_ref = __builtins__\nload = getattr(builtins_ref, '__import__')\nload('os').system('id')",
        "load = globals()['__builtins__']['__import__']\nload('os').system('id')",
        "eval(\"__import__('os').system('id')\")",
        "import builtins\nbuiltins.getattr(builtins, '__import__')('os').system('id')",
    ],
)
def test_reflective_import_is_rejected_before_sandbox(
    code: str,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("PYT_PISTON_URL", raising=False)

    def forbidden_sandbox_call(*args: object, **kwargs: object) -> None:
        pytest.fail("forbidden dynamic access must be rejected before reaching Piston")

    monkeypatch.setattr(
        "pythontopia_graph.agents.boss.evaluator.run_python", forbidden_sandbox_call
    )
    result = evaluate_student_code(code, [_tc("", "")])

    assert not result.passed
    assert result.runtime_error is not None
    assert "forbidden_imports" in result.runtime_error


def test_syntax_error_is_reported_as_first_failure() -> None:
    code = "this is not python"
    cases = [_tc("", "ignored", sample=True), _tc("", "x"), _tc("", "y")]
    result = evaluate_student_code(code, cases)
    assert not result.passed
    assert result.passed_count == 0
    assert result.first_failure is not None
    assert "SyntaxError" in result.first_failure.stderr


def test_nonzero_exit_is_reported_as_failed_case() -> None:
    result = evaluate_student_code("raise SystemExit(1)", [_tc("", "")])

    assert not result.passed
    assert result.first_failure is not None
    assert result.first_failure.stderr == "exit status 1"


@pytest.mark.parametrize("timeout_sec", [1])
def test_timeout_marks_first_case_as_timed_out(timeout_sec: int) -> None:
    code = "while True:\n    pass"
    cases = [_tc("", "never", sample=True), _tc("", "x"), _tc("", "y")]
    result = evaluate_student_code(code, cases, timeout_sec=timeout_sec)
    assert not result.passed
    assert result.first_failure is not None
    assert result.first_failure.timed_out


def test_empty_test_cases_returns_runtime_error() -> None:
    result = evaluate_student_code("print(0)", [])
    assert not result.passed
    assert result.total_count == 0
    assert result.runtime_error == "no_test_cases"


def test_missing_isolated_executor_fails_closed(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("PYT_PISTON_URL", raising=False)
    monkeypatch.delenv("PYT_PISTON_PYTHON_VERSION", raising=False)

    def forbidden_local_execution(*args: object, **kwargs: object) -> None:
        pytest.fail("student code must never run in the Graph process")

    monkeypatch.setattr(subprocess, "run", forbidden_local_execution)

    result = evaluate_student_code("print('must not execute')", [_tc("", "")])

    assert not result.passed
    assert result.runtime_error == "sandbox_unavailable"
    assert result.failed_indices == [0]


def test_unavailable_piston_fails_closed_without_local_fallback(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("PYT_PISTON_URL", "http://piston.test")
    monkeypatch.setenv("PYT_PISTON_PYTHON_VERSION", "3.11.13")

    def unreachable(*args: object, **kwargs: object) -> None:
        raise httpx.ConnectError("test endpoint unavailable")

    def forbidden_local_execution(*args: object, **kwargs: object) -> None:
        pytest.fail("Piston errors must not fall back to Graph worker execution")

    monkeypatch.setattr(execution_sandbox.httpx, "stream", unreachable)
    monkeypatch.setattr(subprocess, "run", forbidden_local_execution)

    result = evaluate_student_code("print('must not execute')", [_tc("", "")])

    assert not result.passed
    assert result.runtime_error == "sandbox_unavailable"


def test_build_failure_hint_uses_runtime_error_first() -> None:
    result = evaluate_student_code(
        "import socket\nprint(0)", [_tc("", "0", sample=True), _tc("", "0"), _tc("", "0")]
    )
    hint = build_failure_hint(result)
    assert hint is not None
    assert "forbidden_imports" in hint


def test_build_failure_hint_hides_hidden_case_values() -> None:
    code = "print(int(input()) * 2)"
    cases = [_tc("2", "4", sample=True), _tc("3", "7"), _tc("4", "8")]
    result = evaluate_student_code(code, cases)
    hint = build_failure_hint(result)
    assert hint is not None
    assert "隐藏测试" in hint
    assert "'7'" not in hint
    assert "'6'" not in hint


def test_build_failure_hint_shows_public_sample_mismatch() -> None:
    result = evaluate_student_code(
        "print(int(input()) * 2)", [_tc("2", "0", sample=True), _tc("3", "4")]
    )

    hint = build_failure_hint(result)

    assert hint == "用例 1 期望 '0' 实际 '4'"


def test_build_failure_hint_for_timeout() -> None:
    result = evaluate_student_code(
        "while True:\n    pass",
        [_tc("", "never", sample=True), _tc("", "x"), _tc("", "y")],
        timeout_sec=1,
    )
    hint = build_failure_hint(result)
    assert hint is not None
    assert "超时" in hint


def test_build_failure_hint_returns_none_on_full_pass() -> None:
    code = "print(input())"
    cases = [_tc("a", "a", sample=True), _tc("b", "b"), _tc("c", "c")]
    result = evaluate_student_code(code, cases)
    assert result.passed
    assert build_failure_hint(result) is None
