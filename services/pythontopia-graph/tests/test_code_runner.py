from __future__ import annotations

from pythontopia_graph.agents.boss.execution_sandbox import PythonExecutionResult
from pythontopia_graph.nodes import code_runner
from pythontopia_graph.nodes.code_runner import CodeRunResult, run_python


def test_delegates_tutor_verification_to_shared_piston_sandbox(monkeypatch) -> None:
    calls: list[tuple[str, str, int]] = []

    def fake_piston(code: str, stdin: str, timeout_sec: int) -> PythonExecutionResult:
        calls.append((code, stdin, timeout_sec))
        return PythonExecutionResult(stdout="6\n", stderr="", exit_code=0)

    monkeypatch.setattr(code_runner, "run_isolated_python", fake_piston)

    result = run_python("print(2 * 3)", timeout_s=1.2)

    assert isinstance(result, CodeRunResult)
    assert calls == [("print(2 * 3)", "", 2)]
    assert result.stdout == "6\n"
    assert result.exit_code == 0
    assert result.runtime_error is None


def test_tutor_verification_fails_closed_when_piston_is_unavailable(monkeypatch) -> None:
    monkeypatch.setattr(
        code_runner,
        "run_isolated_python",
        lambda code, stdin, timeout_sec: PythonExecutionResult(runtime_error="sandbox_unavailable"),
    )

    result = run_python("print('must not run')")

    assert result.exit_code == -1
    assert result.runtime_error == "sandbox_unavailable"
    assert "沙箱不可用" in result.stderr


def test_tutor_verification_bounds_remote_output(monkeypatch) -> None:
    monkeypatch.setattr(
        code_runner,
        "run_isolated_python",
        lambda code, stdin, timeout_sec: PythonExecutionResult(stdout="x" * 100),
    )

    result = run_python("print('x')", max_output=10)

    assert len(result.stdout) <= 34
    assert result.truncated is True
