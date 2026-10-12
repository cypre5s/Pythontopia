"""self_tester tests with fixed responses for pass, error, timeout, and forbidden imports."""

from __future__ import annotations

import subprocess

import pytest

from pythontopia_graph.agents.boss.schemas import BossProblem, TestCase
from pythontopia_graph.agents.boss.self_tester import (
    list_forbidden_imports,
    self_test_problem,
)


def _make(reference: str, tcs: list[TestCase]) -> BossProblem:
    return BossProblem(
        problem_id="ai",
        chapter=1,
        stage=1,
        kc_code="PY43-KC-001",
        title="x",
        statement="x",
        reference_solution=reference,
        test_cases=tcs,
        difficulty=1,
    )


def test_reference_passes_all_cases() -> None:
    problem = _make(
        "a, b = map(int, input().split())\nprint(a + b)",
        [
            TestCase("1 2", "3", is_sample=True),
            TestCase("10 20", "30"),
            TestCase("100 -50", "50"),
        ],
    )
    verdict = self_test_problem(problem)
    assert verdict.passed, verdict.issues


def test_reference_wrong_output_fails() -> None:
    problem = _make(
        "print('always 0')",
        [
            TestCase("1 2", "3", is_sample=True),
            TestCase("4 5", "9"),
            TestCase("0 0", "0"),
        ],
    )
    verdict = self_test_problem(problem)
    assert not verdict.passed
    assert any(i.code == "reference_output_mismatch" for i in verdict.issues)


def test_reference_nonzero_exit_fails() -> None:
    problem = _make(
        "raise SystemExit(1)",
        [TestCase("", "", is_sample=True), TestCase("", ""), TestCase("", "")],
    )
    verdict = self_test_problem(problem)
    assert not verdict.passed
    assert any(i.code == "reference_nonzero_exit" for i in verdict.issues)


def test_reference_syntax_error_caught_by_forbidden_scan() -> None:
    # SyntaxError 时 _scan_forbidden_imports 返回 syntax_error
    problem = _make(
        "def broken(",
        [TestCase("", "", is_sample=True), TestCase("", ""), TestCase("", "")],
    )
    verdict = self_test_problem(problem)
    assert not verdict.passed
    assert any(i.code == "forbidden_imports" for i in verdict.issues)


def test_reference_timeout() -> None:
    problem = _make(
        "while True:\n    pass",
        [TestCase("", "", is_sample=True), TestCase("", ""), TestCase("", "")],
    )
    verdict = self_test_problem(problem, timeout_sec=2)
    assert not verdict.passed
    assert any(i.code == "reference_timeout" for i in verdict.issues)


@pytest.mark.parametrize(
    "code",
    [
        "import socket\nprint('x')",
        "import subprocess\nprint('x')",
        "from urllib.request import urlopen\nprint('x')",
        "import ctypes\nprint('x')",
        "import multiprocessing\nprint('x')",
        "import requests\nprint('x')",
    ],
)
def test_forbidden_imports_rejected(code: str) -> None:
    problem = _make(
        code,
        [TestCase("", "x", is_sample=True), TestCase("", "x"), TestCase("", "x")],
    )
    verdict = self_test_problem(problem)
    assert not verdict.passed
    assert any(i.code == "forbidden_imports" for i in verdict.issues)


def test_fake_piston_never_executes_submitted_source(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path,
    fake_piston_endpoint: list[tuple[str, str]],
) -> None:
    marker = tmp_path / "source-executed"
    source = f"open({str(marker)!r}, 'w').write('executed')"

    def forbidden_local_execution(*args: object, **kwargs: object) -> None:
        pytest.fail("the fake Piston fixture must never execute submitted source")

    monkeypatch.setattr(subprocess, "run", forbidden_local_execution)
    verdict = self_test_problem(_make(source, [TestCase("", "")]))

    assert not verdict.passed
    assert any(issue.code == "reference_nonzero_exit" for issue in verdict.issues)
    assert (source, "") in fake_piston_endpoint
    assert not marker.exists()


def test_list_forbidden_imports_returns_known_set() -> None:
    items = set(list_forbidden_imports())
    assert "socket" in items
    assert "subprocess" in items
    assert "requests" in items


def test_missing_isolated_executor_rejects_reference_without_local_execution(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("PYT_PISTON_URL", raising=False)
    monkeypatch.delenv("PYT_PISTON_PYTHON_VERSION", raising=False)

    def forbidden_local_execution(*args: object, **kwargs: object) -> None:
        pytest.fail("reference code must never run in the Graph process")

    monkeypatch.setattr(subprocess, "run", forbidden_local_execution)
    problem = _make("print('must not execute')", [TestCase("", "")])

    verdict = self_test_problem(problem)

    assert not verdict.passed
    assert any(issue.code == "sandbox_unavailable" for issue in verdict.issues)


def test_multiline_solution_with_input_loop_passes() -> None:
    problem = _make(
        "n = int(input())\ntotal = 0\nfor _ in range(n):\n    total += int(input())\nprint(total)",
        [
            TestCase("3\n1\n2\n3", "6", is_sample=True),
            TestCase("2\n5\n5", "10"),
            TestCase("1\n42", "42"),
        ],
    )
    verdict = self_test_problem(problem)
    assert verdict.passed
