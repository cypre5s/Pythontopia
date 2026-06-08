"""答疑「代码运行验证」沙箱：受限子进程跑学生片段，仅用于定位问题，绝非正式判题。

安全约束：无 shell（argv 直传）、关 stdin（DEVNULL）、隔离解释器（-I -S）、超时 kill、输出截断。
"""

from __future__ import annotations

import subprocess
import sys
from dataclasses import dataclass

_TRUNC_MARK = "\n…[输出已截断]"


@dataclass
class CodeRunResult:
    stdout: str
    stderr: str
    exit_code: int
    timed_out: bool
    truncated: bool


def _clip(text: str, limit: int) -> tuple[str, bool]:
    if len(text) <= limit:
        return text, False
    return text[:limit] + _TRUNC_MARK, True


def run_python(code: str, *, timeout_s: float = 5.0, max_output: int = 8000) -> CodeRunResult:
    """在受限子进程里执行片段，仅用于答疑「验证」（无 shell、关 stdin、超时 kill、输出截断）。"""
    try:
        proc = subprocess.run(
            [sys.executable, "-I", "-S", "-c", code],
            capture_output=True,
            text=True,
            timeout=timeout_s,
            stdin=subprocess.DEVNULL,
        )
    except subprocess.TimeoutExpired as exc:
        partial = exc.stdout if isinstance(exc.stdout, str) else ""
        out, truncated = _clip(partial, max_output)
        return CodeRunResult(out, "执行超时", -1, True, truncated)
    out, t_out = _clip(proc.stdout, max_output)
    err, t_err = _clip(proc.stderr, max_output)
    return CodeRunResult(out, err, proc.returncode, False, t_out or t_err)
