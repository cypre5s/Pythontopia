"""R-13 §5 Phase 5 审查 2：reference_solution 自洽检查。

AI 给的 reference_solution 必须能在 5 秒内跑过自带所有 test_cases。
沙箱：subprocess + resource.setrlimit（POSIX）。

注意：
    - 不允许网络（不引入 socket）— 只靠 RLIMIT_AS 限内存 + RLIMIT_CPU 限 CPU，
      并显式 ``env={}`` 清干净环境变量；网络隔离靠系统级 firejail / seccomp
      （部署文档说明），代码里仅静态扫描 forbidden imports 作为补充防线。
    - Windows 上 ``resource`` 不可用，会自动跳过 setrlimit，仅靠 subprocess timeout。
"""

from __future__ import annotations

import platform
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Iterable

from .schemas import BossProblem, ReviewIssue, ReviewVerdict

FORBIDDEN_IMPORTS = (
    "socket",
    "subprocess",
    "shutil",
    "ctypes",
    "multiprocessing",
    "asyncio.subprocess",
    "urllib.request",
    "requests",
    "httpx",
    "smtplib",
    "ftplib",
    "ssl",
)

DEFAULT_TIMEOUT_SEC = 5
RLIMIT_CPU_SEC = 5
RLIMIT_AS_BYTES = 256 * 1024 * 1024


def self_test_problem(
    problem: BossProblem,
    python_executable: str | None = None,
    timeout_sec: int = DEFAULT_TIMEOUT_SEC,
) -> ReviewVerdict:
    issues: list[ReviewIssue] = []
    forbidden = _scan_forbidden_imports(problem.reference_solution)
    if forbidden:
        issues.append(
            ReviewIssue(
                review="self_test",
                severity="error",
                code="forbidden_imports",
                message=f"reference_solution imports forbidden modules: {','.join(forbidden)}",
            )
        )
        return ReviewVerdict(review="self_test", passed=False, issues=issues)

    interpreter = python_executable or sys.executable
    preexec = _maybe_rlimit_preexec()

    with tempfile.TemporaryDirectory(prefix="pyt-boss-") as tmpdir:
        script_path = Path(tmpdir) / "solution.py"
        script_path.write_text(problem.reference_solution, encoding="utf-8")

        for i, tc in enumerate(problem.test_cases):
            try:
                result = subprocess.run(
                    [interpreter, str(script_path)],
                    input=tc.input,
                    capture_output=True,
                    text=True,
                    timeout=timeout_sec,
                    env={"PYTHONIOENCODING": "utf-8"},
                    preexec_fn=preexec,
                    cwd=tmpdir,
                )
            except subprocess.TimeoutExpired:
                issues.append(
                    ReviewIssue(
                        review="self_test",
                        severity="error",
                        code="reference_timeout",
                        message=f"reference solution timeout on test_case[{i}] (>{timeout_sec}s)",
                    )
                )
                return ReviewVerdict(review="self_test", passed=False, issues=issues)
            except FileNotFoundError as exc:
                issues.append(
                    ReviewIssue(
                        review="self_test",
                        severity="error",
                        code="interpreter_missing",
                        message=f"python interpreter not found: {exc}",
                    )
                )
                return ReviewVerdict(review="self_test", passed=False, issues=issues)
            if result.returncode != 0:
                issues.append(
                    ReviewIssue(
                        review="self_test",
                        severity="error",
                        code="reference_nonzero_exit",
                        message=(
                            f"test_case[{i}] reference exit={result.returncode}; "
                            f"stderr={result.stderr[:240]}"
                        ),
                    )
                )
                return ReviewVerdict(review="self_test", passed=False, issues=issues)
            actual = result.stdout.strip()
            expected = tc.expected_output.strip()
            if actual != expected:
                issues.append(
                    ReviewIssue(
                        review="self_test",
                        severity="error",
                        code="reference_output_mismatch",
                        message=(
                            f"test_case[{i}] input={tc.input!r}: "
                            f"expected={expected!r}, got={actual!r}"
                        ),
                    )
                )
                return ReviewVerdict(review="self_test", passed=False, issues=issues)

    return ReviewVerdict(review="self_test", passed=True, issues=issues)


def _scan_forbidden_imports(source: str) -> list[str]:
    import ast

    try:
        tree = ast.parse(source)
    except SyntaxError as exc:
        return [f"syntax_error:{exc.msg}"]
    forbidden: list[str] = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                if _matches(alias.name):
                    forbidden.append(alias.name)
        elif isinstance(node, ast.ImportFrom):
            mod = node.module or ""
            if _matches(mod):
                forbidden.append(mod)
    return forbidden


def _matches(name: str) -> bool:
    name = name.strip()
    if not name:
        return False
    for forbidden in FORBIDDEN_IMPORTS:
        if name == forbidden or name.startswith(forbidden + "."):
            return True
    return False


def _maybe_rlimit_preexec():
    """POSIX 用 resource 模块限制 CPU / 内存；Windows / 异常时返回 None。"""
    if platform.system() == "Windows":
        return None
    try:
        import resource  # type: ignore
    except ImportError:
        return None

    def _apply() -> None:
        try:
            resource.setrlimit(resource.RLIMIT_CPU, (RLIMIT_CPU_SEC, RLIMIT_CPU_SEC))
        except (ValueError, OSError):
            pass
        try:
            resource.setrlimit(resource.RLIMIT_AS, (RLIMIT_AS_BYTES, RLIMIT_AS_BYTES))
        except (ValueError, OSError):
            pass

    return _apply


def list_forbidden_imports() -> Iterable[str]:
    """For tests / docs reference."""

    return FORBIDDEN_IMPORTS
