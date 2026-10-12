"""导师代码验证只复用远程隔离判题服务，Graph worker 不执行学生代码。"""

from __future__ import annotations

from dataclasses import dataclass
from math import ceil

from pythontopia_graph.agents.boss.execution_sandbox import (
    PythonExecutionResult,
    run_python as run_isolated_python,
)

_TRUNC_MARK = "\n…[输出已截断]"


@dataclass
class CodeRunResult:
    stdout: str
    stderr: str
    exit_code: int
    timed_out: bool
    truncated: bool
    runtime_error: str | None = None


def _clip(text: str, limit: int) -> tuple[str, bool]:
    if len(text) <= limit:
        return text, False
    return text[:limit] + _TRUNC_MARK, True


def run_python(code: str, *, timeout_s: float = 5.0, max_output: int = 8000) -> CodeRunResult:
    """在与正式判题共用的 Piston/Isolate 中运行；未配置或故障时绝不本地回退。"""
    result: PythonExecutionResult = run_isolated_python(code, "", max(1, ceil(timeout_s)))
    if result.runtime_error:
        return CodeRunResult(
            stdout="",
            stderr="隔离沙箱不可用，代码没有实际执行",
            exit_code=-1,
            timed_out=False,
            truncated=False,
            runtime_error=result.runtime_error,
        )
    stdout, stdout_truncated = _clip(result.stdout, max_output)
    stderr, stderr_truncated = _clip(result.stderr, max_output)
    return CodeRunResult(
        stdout=stdout,
        stderr=stderr,
        exit_code=result.exit_code,
        timed_out=result.timed_out,
        truncated=stdout_truncated or stderr_truncated,
    )
