"""文本题接地评分 + kind 分派测试。

核心诉求：
* 文本题（short_answer/teach_back/pdf_question）走接地评分——好答案能过关；
* 同一段自然语言：当代码判会语法错（旧行为，永远过不了），当文本判则能过关；
* 代码题仍由 stdout 沙箱正确判题；
* 无证据时返回 no_evidence_found（NFR-AIH，不臆造通过）。
"""

from __future__ import annotations

from pythontopia_graph.agents.boss.evaluator import (
    EvaluationResult,
    build_failure_hint,
    evaluate_boss_attempt,
)
from pythontopia_graph.agents.boss.kinds import is_code_kind, is_text_kind
from pythontopia_graph.agents.boss.schemas import TestCase
from pythontopia_graph.agents.boss.text_evaluator import evaluate_text_answer


def _tc(expected: str, *, sample: bool = False) -> TestCase:
    # 文本题里 expected_output 承载「参考要点」，input 留空。
    return TestCase(input="", expected_output=expected, is_sample=sample)


# ───────── kind 分类 ─────────


def test_kind_classification() -> None:
    for k in ("short_answer", "teach_back", "pdf_question"):
        assert is_text_kind(k)
        assert not is_code_kind(k)
    for k in ("fill_blank", "debug", "closed_loop", "dual_track", "bug_mine", "multi_round"):
        assert is_code_kind(k)
        assert not is_text_kind(k)
    # 缺省/未知 → 代码题（向后兼容）
    assert is_code_kind(None)
    assert is_code_kind("")
    assert is_code_kind("something_new")
    # 大小写/空白归一化
    assert is_text_kind("  Short_Answer ")


# ───────── 文本题接地评分 ─────────


def test_good_text_answer_passes() -> None:
    cases = [_tc("递归 函数 基准情形 终止条件", sample=True), _tc("调用 自身")]
    answer = (
        "递归函数是指在函数定义中调用自身的函数，"
        "必须设置基准情形作为终止条件，否则会无限递归。"
    )
    result = evaluate_text_answer(answer, cases)
    assert result.passed
    assert result.passed_count == result.total_count == 2
    assert result.runtime_error is None


def test_partial_text_answer_fails_with_feedback() -> None:
    # 每条 expected_output = 一个评分要点；答案只覆盖第 1 个要点。
    cases = [_tc("递归 函数"), _tc("基准情形 终止条件"), _tc("调用 自身")]
    answer = "递归其实就是一种特殊的函数。"  # 命中要点1，漏掉要点2、要点3
    result = evaluate_text_answer(answer, cases)
    assert not result.passed
    assert result.passed_count == 1
    assert result.total_count == 3
    assert result.feedback is not None
    assert "命中 1/3" in result.feedback


def test_short_answer_hits_quality_gate() -> None:
    cases = [_tc("变量 存储 数据 名字")]
    result = evaluate_text_answer("对", cases)  # 过短
    assert not result.passed
    assert result.passed_count == 0
    assert result.feedback is not None
    assert "过短" in result.feedback


def test_no_evidence_returns_no_evidence_found() -> None:
    # 题目没带任何参考要点，且无 reference_answer → 拒绝臆造。
    cases = [_tc(""), _tc("")]
    result = evaluate_text_answer("随便答一段话占位看看", cases)
    assert not result.passed
    assert result.runtime_error == "no_evidence_found"
    assert result.feedback is not None


def test_reference_answer_used_when_no_expected_points() -> None:
    cases = [_tc("")]  # 无 expected 要点
    answer = "字典是键值对集合，通过键快速查找对应的值。"
    result = evaluate_text_answer(
        answer, cases, reference_answer="字典 键值对 通过 键 查找 值"
    )
    assert result.passed


# ───────── prose-as-code：分派修复了「自然语言当 Python 跑」 ─────────


def test_prose_answer_passes_as_text_but_fails_as_code() -> None:
    answer = "变量是用来存储数据的名字，可以随时修改它的值。"
    cases = [_tc("变量 存储 数据 名字")]

    # 文本题：接地评分通过
    text_result = evaluate_boss_attempt(
        kind="short_answer", submission=answer, test_cases=cases
    )
    assert text_result.passed

    # 同一段散文若当代码判（旧行为）：语法错误，永远过不了
    code_result = evaluate_boss_attempt(
        kind="fill_blank", submission=answer, test_cases=cases
    )
    assert not code_result.passed
    assert code_result.passed_count == 0


# ───────── 代码题仍由 stdout 沙箱正确判题 ─────────


def test_code_kind_still_judged_by_sandbox_pass() -> None:
    cases = [
        TestCase(input="1 2", expected_output="3", is_sample=True),
        TestCase(input="10 20", expected_output="30"),
        TestCase(input="-1 1", expected_output="0"),
    ]
    code = "a, b = map(int, input().split())\nprint(a + b)"
    for kind in ("fill_blank", "debug", "closed_loop", "dual_track", "bug_mine", "multi_round", None, ""):
        result = evaluate_boss_attempt(kind=kind, submission=code, test_cases=cases)
        assert result.passed, f"code kind={kind!r} should pass sandbox"
        assert result.passed_count == 3


def test_code_kind_sandbox_reports_failure() -> None:
    cases = [
        TestCase(input="1", expected_output="2", is_sample=True),
        TestCase(input="5", expected_output="7"),
        TestCase(input="9", expected_output="10"),
    ]
    code = "n = int(input())\nprint(n + 1)"  # 用例2 期望 7，实际 6
    result = evaluate_boss_attempt(kind="debug", submission=code, test_cases=cases)
    assert not result.passed
    assert result.passed_count == 2
    assert result.failed_indices == [1]


def test_text_kind_does_not_execute_code_submission() -> None:
    # teach_back 提交含死循环代码也不会被执行（不会超时），只按文本接地评分。
    cases = [_tc("封装 继承 多态")]
    answer = "while True: pass"  # 若被当作代码跑会死循环；文本路径只做 token 匹配
    result = evaluate_boss_attempt(kind="teach_back", submission=answer, test_cases=cases)
    # 该答案与参考要点无重叠 → 未过，但绝不执行、不超时
    assert not result.passed
    assert result.runtime_error != "no_test_cases"


# ───────── build_failure_hint 优先反馈 ─────────


def test_build_failure_hint_prefers_feedback() -> None:
    r = EvaluationResult(
        passed=False,
        passed_count=1,
        total_count=2,
        runtime_error="some_runtime",
        feedback="命中 1/2 个关键点",
    )
    assert build_failure_hint(r) == "命中 1/2 个关键点"


def test_build_failure_hint_none_on_text_pass() -> None:
    cases = [_tc("递归 函数 基准情形"), _tc("调用 自身")]
    answer = "递归函数会调用自身，并以基准情形作为终止条件。"
    result = evaluate_text_answer(answer, cases)
    assert result.passed
    assert build_failure_hint(result) is None
