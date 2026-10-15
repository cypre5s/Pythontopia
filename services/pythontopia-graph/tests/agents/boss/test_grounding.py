"""verify_kc_grounding 单元测试。"""

from __future__ import annotations

import pytest

from pythontopia_graph.agents.boss.grounding import (
    CHAPTER_SYNTAX_WHITELIST,
    extract_kc_grounding_keywords,
    verify_kc_grounding,
)
from pythontopia_graph.agents.boss.schemas import BossProblem, TestCase


def _problem(chapter: int, kc_code: str, statement: str, code: str) -> BossProblem:
    return BossProblem(
        problem_id="ai",
        chapter=chapter,
        stage=1,
        kc_code=kc_code,
        title="x",
        statement=statement,
        reference_solution=code,
        test_cases=[TestCase("", "", is_sample=True), TestCase("", ""), TestCase("", "")],
        difficulty=1,
    )


def test_chapter_1_with_print_keyword_passes() -> None:
    p = _problem(1, "PY43-KC-001", "学会使用 print 函数输出 hello world", "print('hello')")
    v = verify_kc_grounding(p)
    assert v.passed


def test_trivial_input_echo_is_rejected_even_when_kc_term_is_in_statement() -> None:
    p = _problem(
        1,
        "PY43-KC-001",
        "读入一个成就名称并原样输出，例如银河一号。",
        "name = input()\nprint(name)",
    )

    verdict = verify_kc_grounding(
        p,
        kc_keywords={"PY43-KC-001": ["银河一号"]},
    )

    assert not verdict.passed
    assert any(issue.code == "trivial_echo_solution" for issue in verdict.issues)


def test_input_echo_with_appended_constant_label_is_still_rejected() -> None:
    p = _problem(
        1,
        "PY43-KC-001",
        "读入一个成就名称并展示中国计算机发展成就。",
        "name = input()\nprint(name)\nprint('中国计算机发展成就')",
    )

    verdict = verify_kc_grounding(
        p,
        kc_keywords={"PY43-KC-001": ["中国计算机发展成就"]},
    )

    assert not verdict.passed
    assert any(issue.code == "trivial_echo_solution" for issue in verdict.issues)


def test_constant_output_does_not_auto_ground_non_programming_kc() -> None:
    p = _problem(
        1,
        "PY43-KC-001",
        "输出神威·太湖之光这一中国计算机发展成就。",
        "print('神威·太湖之光')",
    )

    verdict = verify_kc_grounding(
        p,
        kc_keywords={"PY43-KC-001": ["中国计算机发展成就", "神威·太湖之光"]},
    )

    assert not verdict.passed
    assert any(issue.code == "trivial_constant_output" for issue in verdict.issues)


def test_constant_output_is_rejected_when_print_is_only_a_kc_summary_keyword() -> None:
    p = _problem(
        1,
        "PY43-KC-001",
        "读取年份后输出对应的中国计算机成就名称；本题只需输出固定字符串，不要求判断逻辑。",
        "print('银河一号超级计算机')",
    )

    verdict = verify_kc_grounding(
        p,
        kc_keywords={
            "PY43-KC-001": [
                "中国计算机发展成就",
                "银河一号超级计算机",
                "print()",
            ]
        },
    )

    assert not verdict.passed
    assert any(issue.code == "trivial_constant_output" for issue in verdict.issues)


def test_chapter_1_no_keyword_fails() -> None:
    p = _problem(
        1,
        "PY43-KC-001",
        "解决一道算法题",
        "x=1\nx=x+1\nprint(x)",
    )
    # statement 无关键词时，code 内 print 仍可命中
    v = verify_kc_grounding(p)
    assert v.passed


def test_chapter_2_dict_keyword_in_statement_passes() -> None:
    p = _problem(2, "PY43-KC-008", "实现一个简单的 dict 操作", "d = {}\nprint(d)")
    v = verify_kc_grounding(p)
    assert v.passed


def test_chapter_3_unsupported_for_fails() -> None:
    """第 3 章只允许 if/elif/else，for 是 4 章语法，应判超纲。"""
    p = _problem(
        3,
        "PY43-KC-016",
        "用 if 判断奇偶",
        "n = int(input())\nfor i in range(1):\n    print(n % 2)",
    )
    v = verify_kc_grounding(p)
    assert not v.passed
    assert any(i.code == "syntax_out_of_scope" for i in v.issues)


def test_chapter_4_for_loop_allowed() -> None:
    p = _problem(
        4,
        "PY43-KC-026",
        "用 for 循环打印 0..n",
        "n = int(input())\nfor i in range(n):\n    print(i)",
    )
    v = verify_kc_grounding(p)
    assert v.passed


def test_chapter_5_function_allowed() -> None:
    p = _problem(
        5,
        "PY43-KC-036",
        "写一个 def 函数返回 a+b",
        "def add(a, b):\n    return a + b\nprint(add(int(input()), int(input())))",
    )
    v = verify_kc_grounding(p)
    assert v.passed


def test_chapter_6_class_allowed_inheritance() -> None:
    code = (
        "class Animal:\n    def name(self):\n        return 'a'\n"
        "class Dog(Animal):\n    def name(self):\n        return super().name()+'-d'\n"
        "print(Dog().name())"
    )
    p = _problem(6, "PY43-KC-046", "继承 Animal 实现 Dog 类", code)
    v = verify_kc_grounding(p)
    assert v.passed


def test_chapter_7_try_except_allowed() -> None:
    code = "try:\n    x = int(input())\n    print(x)\nexcept ValueError:\n    print(-1)"
    p = _problem(7, "PY43-KC-060", "用 try/except 处理 int 转换异常", code)
    v = verify_kc_grounding(p)
    assert v.passed


def test_unknown_kc_code_falls_back_to_chapter_keywords() -> None:
    p = _problem(2, "PY43-KC-007", "使用 list 切片", "L=[1,2,3]\nprint(L[1:])")
    v = verify_kc_grounding(p)
    assert v.passed


def test_custom_kc_keywords_override_defaults() -> None:
    p = _problem(
        2,
        "PY43-KC-008",
        "读入一个数字，计算增加 1 后的特殊场景编号。",
        "value = int(input()) + 1\nprint('特殊场景', value)",
    )
    v = verify_kc_grounding(p, kc_keywords={"PY43-KC-008": ["特殊场景"]})
    assert v.passed


def test_constant_output_is_allowed_for_print_kc() -> None:
    p = _problem(1, "PY43-KC-001", "用 print() 输出 hello world", "print('hello world')")
    verdict = verify_kc_grounding(p, kc_keywords={"PY43-KC-001": ["print()"]})
    assert verdict.passed


def test_custom_kc_keywords_miss_fails() -> None:
    p = _problem(2, "PY43-KC-008", "演示某种特殊场景", "print('ok')")
    v = verify_kc_grounding(p, kc_keywords={"PY43-KC-008": ["不存在的词"]})
    assert not v.passed
    assert any(i.code == "kc_keyword_miss" for i in v.issues)


def test_authoritative_kc_term_only_in_story_blocks_bank_approval() -> None:
    p = _problem(
        1,
        "PY43-KC-001",
        "输出神威·太湖之光的相关说明",
        "value = int(input())\nprint(value + 1)",
    )

    verdict = verify_kc_grounding(
        p,
        kc_keywords={"PY43-KC-001": ["中国计算机发展成就", "神威·太湖之光"]},
    )

    assert not verdict.passed
    issue = next(issue for issue in verdict.issues if issue.code == "kc_solution_miss")
    assert issue.severity == "error"


def test_python_api_from_kc_summary_can_ground_reference_solution() -> None:
    kc_name = "format()方法的格式控制"
    kc_summary = "掌握 Python 中 format() 方法的填充字符、对齐方式与宽度设置。"
    problem = _problem(
        3,
        "PY43-KC-018",
        "使用 format() 方法控制数字格式。",
        "value = '{:>5}'.format(input())\nprint(value)",
    )

    keywords = extract_kc_grounding_keywords(kc_name, kc_summary)
    verdict = verify_kc_grounding(
        problem,
        kc_keywords={"PY43-KC-018": keywords},
    )

    assert "format" in keywords
    assert verdict.passed, verdict.issues


def test_kc010_accepts_executable_variable_and_arithmetic_use_without_outputting_topic_name() -> None:
    kc_name = "变量与运算符"
    kc_summary = "变量赋值与算术运算符。"
    problem = _problem(
        2,
        "PY43-KC-010",
        "本题练习变量与运算符：读取两个整数并使用算术运算符求和。",
        "left = int(input())\nright = int(input())\ntotal = left + right\nprint(total)",
    )

    keywords = extract_kc_grounding_keywords(kc_name, kc_summary)
    verdict = verify_kc_grounding(problem, kc_keywords={"PY43-KC-010": keywords})

    assert verdict.passed, verdict.issues


def test_kc010_rejects_a_solution_that_does_not_apply_an_operator() -> None:
    kc_name = "变量与运算符"
    kc_summary = "变量赋值与算术运算符。"
    problem = _problem(
        2,
        "PY43-KC-010",
        "本题练习变量与运算符：读取一个整数并输出。",
        "topic = '变量与运算符'\nprint(topic)",
    )

    keywords = extract_kc_grounding_keywords(kc_name, kc_summary)
    verdict = verify_kc_grounding(problem, kc_keywords={"PY43-KC-010": keywords})

    assert not verdict.passed
    assert any(issue.code == "kc_solution_miss" for issue in verdict.issues)


def test_no_keywords_provided_emits_warning_only() -> None:
    """空 kc_keywords + 默认无法识别 kc 时，仅 warning，不阻断（除非有其它 error）。"""

    p = BossProblem(
        problem_id="ai",
        chapter=1,
        stage=1,
        kc_code="UNKNOWN-CODE",
        title="x",
        statement="x",
        reference_solution="print(1)",
        test_cases=[TestCase("", "", is_sample=True), TestCase("", ""), TestCase("", "")],
        difficulty=1,
    )
    v = verify_kc_grounding(p, kc_keywords={})
    assert v.passed
    assert any(i.code == "no_keywords_provided" for i in v.issues)


@pytest.mark.parametrize("chapter", [1, 2, 3, 4, 5, 6, 7])
def test_whitelist_contains_module_for_each_chapter(chapter: int) -> None:
    assert "Module" in CHAPTER_SYNTAX_WHITELIST[chapter]


def test_chapter_1_print_only_no_loops_no_conditionals() -> None:
    """第 1 章不允许 if / for，确保白名单严格。"""
    if_code = "x = 1\nif x > 0:\n    print(x)"
    p = _problem(1, "PY43-KC-001", "print 输出", if_code)
    v = verify_kc_grounding(p)
    assert not v.passed
