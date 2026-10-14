"""Boss 副本玩家答案评测。

``self_tester`` 校验 ``reference_solution`` 自洽；本模块评测玩家提交，返回通过数与首条失败信息。
所有 Python 执行都委托给隔离的 Piston 服务，Graph worker 内不执行用户代码。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Sequence

from .execution_sandbox import run_python
from .schemas import TestCase
from .self_tester import DEFAULT_TIMEOUT_SEC, _scan_forbidden_imports


@dataclass
class CaseResult:
    """单条测试用例评测结果。"""

    index: int
    passed: bool
    actual: str
    expected: str
    stderr: str = ""
    timed_out: bool = False
    is_sample: bool = False

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
    # 文本题接地评分给出的结构化反馈；代码题保持 None（沿用 first_failure / runtime_error 渲染）。
    feedback: str | None = None


def evaluate_student_code(
    code: str,
    test_cases: Sequence[TestCase],
    *,
    timeout_sec: int = DEFAULT_TIMEOUT_SEC,
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

    passed_count = 0
    first_failure: CaseResult | None = None
    failed_indices: list[int] = []

    for i, tc in enumerate(test_cases):
        result = run_python(code, tc.input, timeout_sec)
        if result.runtime_error:
            return EvaluationResult(
                passed=False,
                passed_count=passed_count,
                total_count=total,
                runtime_error=result.runtime_error,
                failed_indices=list(range(i, total)),
            )

        actual = result.stdout.rstrip()
        stderr = result.stderr[:240]
        expected = tc.expected_output.rstrip()
        passed = not result.timed_out and result.exit_code == 0 and actual == expected
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
                    stderr=(f"timeout >{timeout_sec}s" if result.timed_out else stderr),
                    timed_out=result.timed_out,
                    is_sample=tc.is_sample,
                )

    return EvaluationResult(
        passed=passed_count == total,
        passed_count=passed_count,
        total_count=total,
        first_failure=first_failure,
        failed_indices=failed_indices,
    )


def build_failure_hint(result: EvaluationResult) -> str | None:
    """将接地反馈 / 首条失败 / ``runtime_error`` 渲染为单行提示，供 BossPage 展示。"""

    # 文本题接地评分优先用结构化 feedback。
    if result.feedback:
        return result.feedback
    if result.runtime_error:
        return result.runtime_error
    f = result.first_failure
    if f is None:
        return None
    if not f.is_sample:
        return "隐藏测试未通过，请检查边界条件。"
    if f.timed_out:
        return f"用例 {f.index + 1} 超时（>5s）"
    return f"用例 {f.index + 1} 期望 {f.expected!r} 实际 {f.actual!r}"


def evaluate_boss_attempt(
    *,
    kind: str | None,
    submission: str,
    test_cases: Sequence[TestCase],
    timeout_sec: int = DEFAULT_TIMEOUT_SEC,
    reference_answer: str | None = None,
    kc_code: str | None = None,
) -> EvaluationResult:
    """按 ``kind`` 分派判题：文本题走接地评分，其余走 stdout 沙箱。

    显式分派、永不把自然语言当 Python 跑；未知/缺省 ``kind`` 按代码题处理（向后兼容）。
    文本评分为确定性接地算法，fail fast、不静默降级为闲聊。
    """

    # 局部导入规避 text_evaluator → evaluator 的循环依赖。
    from .kinds import is_text_kind

    if is_text_kind(kind):
        from .text_evaluator import evaluate_text_answer

        return evaluate_text_answer(
            submission,
            test_cases,
            reference_answer=reference_answer,
            kc_code=kc_code,
        )
    return evaluate_student_code(submission, test_cases, timeout_sec=timeout_sec)
