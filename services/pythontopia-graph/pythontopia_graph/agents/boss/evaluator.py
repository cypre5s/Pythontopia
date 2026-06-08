"""Boss 副本玩家答案评测沙箱。

``self_tester`` 校验 ``reference_solution`` 自洽；本模块评测玩家提交，返回通过数与首条失败信息。
复用 forbidden 扫描与 rlimit 沙箱，与出题侧安全模型一致。
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Sequence

from .schemas import TestCase
from .self_tester import (
    DEFAULT_TIMEOUT_SEC,
    _maybe_rlimit_preexec,
    _scan_forbidden_imports,
)


@dataclass
class CaseResult:
    """单条测试用例评测结果。"""

    index: int
    passed: bool
    actual: str
    expected: str
    stderr: str = ""
    timed_out: bool = False

    __test__ = False


@dataclass
class EvaluationResult:
    """整体评测结果。``passed`` ⇔ 全部 case 通过。"""

    passed: bool
    passed_count: int
    total_count: int
    first_failure: CaseResult | None = None
    runtime_error: str | None = None  # 非用例级失败（禁 import、语法错误等）

    failed_indices: list[int] = field(default_factory=list)


def evaluate_student_code(
    code: str,
    test_cases: Sequence[TestCase],
    *,
    timeout_sec: int = DEFAULT_TIMEOUT_SEC,
    python_executable: str | None = None,
) -> EvaluationResult:
    """逐条比对玩家 stdout（rstrip）；失败不抛异常，写入 ``EvaluationResult``。"""

    total = len(test_cases)
    if total == 0:
        return EvaluationResult(
            passed=False,
            passed_count=0,
            total_count=0,
            runtime_error="no_test_cases",
        )

    forbidden = _scan_forbidden_imports(code)
    if forbidden:
        return EvaluationResult(
            passed=False,
            passed_count=0,
            total_count=total,
            runtime_error=f"forbidden_imports:{','.join(forbidden)}",
            failed_indices=list(range(total)),
        )

    interpreter = python_executable or sys.executable
    preexec = _maybe_rlimit_preexec()

    passed_count = 0
    first_failure: CaseResult | None = None
    failed_indices: list[int] = []

    with tempfile.TemporaryDirectory(prefix="pyt-boss-eval-") as tmpdir:
        script_path = Path(tmpdir) / "submission.py"
        script_path.write_text(code, encoding="utf-8")

        for i, tc in enumerate(test_cases):
            timed_out = False
            actual = ""
            stderr = ""
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
                actual = result.stdout.rstrip()
                stderr = result.stderr[:240]
                expected = tc.expected_output.rstrip()
                passed = result.returncode == 0 and actual == expected
            except subprocess.TimeoutExpired:
                expected = tc.expected_output.rstrip()
                stderr = f"timeout >{timeout_sec}s"
                timed_out = True
                passed = False
            except FileNotFoundError as exc:
                return EvaluationResult(
                    passed=False,
                    passed_count=passed_count,
                    total_count=total,
                    runtime_error=f"interpreter_missing:{exc}",
                    failed_indices=list(range(i, total)),
                )

            if passed:
                passed_count += 1
            else:
                failed_indices.append(i)
                if first_failure is None:
                    first_failure = CaseResult(
                        index=i,
                        passed=False,
                        actual=actual,
                        expected=expected,
                        stderr=stderr,
                        timed_out=timed_out,
                    )

    return EvaluationResult(
        passed=passed_count == total,
        passed_count=passed_count,
        total_count=total,
        first_failure=first_failure,
        failed_indices=failed_indices,
    )


def build_failure_hint(result: EvaluationResult) -> str | None:
    """将首条失败或 ``runtime_error`` 渲染为单行提示，供 BossPage 展示。"""

    if result.runtime_error:
        return result.runtime_error
    f = result.first_failure
    if f is None:
        return None
    if f.timed_out:
        return f"用例 {f.index + 1} 超时（>5s）"
    return f"用例 {f.index + 1} 期望 {f.expected!r} 实际 {f.actual!r}"
