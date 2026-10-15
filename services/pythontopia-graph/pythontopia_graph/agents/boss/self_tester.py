"""reference_solution 自洽审查：沙箱内须在超时内通过全部 test_cases。

所有代码执行都委托给隔离的 Piston 服务。配置缺失或服务不可用时 fail closed，
不在 Graph worker 中执行 reference_solution。
"""

from __future__ import annotations

import re
from typing import Iterable

from .execution_sandbox import run_python
from .schemas import BossProblem, ReviewIssue, ReviewVerdict

FORBIDDEN_IMPORTS = (
    "os",
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
    "importlib",
)

DEFAULT_TIMEOUT_SEC = 5
DEBUG_SNIPPET_RE = re.compile(
    r"(?:错误代码|有问题的代码|Bug\s*代码)\s*[:：]\s*\n"
    r"(.*?)(?=\n\s*(?:输入格式|输出格式|样例输入|样例输出)\s*[:：]|\Z)",
    re.IGNORECASE | re.DOTALL,
)


def self_test_problem(
    problem: BossProblem,
    timeout_sec: int = DEFAULT_TIMEOUT_SEC,
) -> ReviewVerdict:
    """在沙箱中运行 ``reference_solution`` 并逐条比对 test_cases。"""
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

    for i, tc in enumerate(problem.test_cases):
        result = run_python(problem.reference_solution, tc.input, timeout_sec)
        if result.runtime_error:
            issues.append(
                ReviewIssue(
                    review="self_test",
                    severity="error",
                    code="sandbox_unavailable",
                    message="isolated Python executor is unavailable",
                )
            )
            return ReviewVerdict(review="self_test", passed=False, issues=issues)
        if result.timed_out:
            issues.append(
                ReviewIssue(
                    review="self_test",
                    severity="error",
                    code="reference_timeout",
                    message=f"reference solution timeout on test_case[{i}] (>{timeout_sec}s)",
                )
            )
            return ReviewVerdict(review="self_test", passed=False, issues=issues)
        if result.exit_code != 0:
            issues.append(
                ReviewIssue(
                    review="self_test",
                    severity="error",
                    code="reference_nonzero_exit",
                    message=(
                        f"test_case[{i}] reference exit={result.exit_code}; "
                        f"stderr={result.stderr[:240]}"
                    ),
                )
            )
            return ReviewVerdict(review="self_test", passed=False, issues=issues)
        actual = result.stdout.rstrip()
        expected = tc.expected_output.rstrip()
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

    if problem.kind.strip().lower() == "debug":
        return _self_test_debug_snippet(problem, timeout_sec)

    return ReviewVerdict(review="self_test", passed=True, issues=issues)


def _self_test_debug_snippet(problem: BossProblem, timeout_sec: int) -> ReviewVerdict:
    match = DEBUG_SNIPPET_RE.search(problem.statement)
    snippet = match.group(1).strip() if match else ""
    snippet = re.sub(r"^```(?:python)?\s*", "", snippet, flags=re.IGNORECASE).strip()
    snippet = re.sub(r"\s*```$", "", snippet).strip()
    if not snippet:
        return _debug_failure("debug_snippet_missing", "debug statement must include an error-code section")

    forbidden = _scan_forbidden_imports(snippet)
    if forbidden:
        return _debug_failure(
            "debug_snippet_forbidden_imports",
            "debug statement code contains forbidden imports",
        )

    for index, case in enumerate(problem.test_cases):
        result = run_python(snippet, case.input, timeout_sec)
        if result.runtime_error:
            return _debug_failure("sandbox_unavailable", "isolated Python executor is unavailable")
        if result.timed_out:
            return _debug_failure(
                "debug_snippet_timeout",
                f"debug statement code timed out on test_case[{index}]",
            )
        if result.exit_code != 0 or result.stdout.rstrip() != case.expected_output.rstrip():
            return ReviewVerdict(review="self_test", passed=True, issues=[])

    return _debug_failure(
        "debug_snippet_not_buggy",
        "debug statement code must fail at least one declared test case",
    )


def _debug_failure(code: str, message: str) -> ReviewVerdict:
    return ReviewVerdict(
        review="self_test",
        passed=False,
        issues=[ReviewIssue(review="self_test", severity="error", code=code, message=message)],
    )


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
        elif isinstance(node, ast.Name) and node.id == "__import__":
            forbidden.append(node.id)
        elif isinstance(node, ast.Attribute) and node.attr == "__import__":
            forbidden.append(node.attr)
    return forbidden


def _matches(name: str) -> bool:
    name = name.strip()
    if not name:
        return False
    for forbidden in FORBIDDEN_IMPORTS:
        if name == forbidden or name.startswith(forbidden + "."):
            return True
    return False


def list_forbidden_imports() -> Iterable[str]:
    """返回禁止 import 模块列表，供测试与文档引用。"""

    return FORBIDDEN_IMPORTS
