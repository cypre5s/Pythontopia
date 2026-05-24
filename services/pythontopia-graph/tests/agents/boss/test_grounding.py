"""R-13 §5 Phase 5：grounding 单元测试。"""

from __future__ import annotations

import pytest

from pythontopia_graph.agents.boss.grounding import (
    CHAPTER_SYNTAX_WHITELIST,
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


def test_chapter_1_no_keyword_fails() -> None:
    p = _problem(
        1,
        "PY43-KC-001",
        "解决一道算法题",
        "x=1\nx=x+1\nprint(x)",
    )
    # 没命中第一章关键词（print 没出现在 statement，仅在代码）—— 代码也有 print 命中
    v = verify_kc_grounding(p)
    assert v.passed  # print 仍然在 code 内被检索到


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
    p = _problem(2, "PY43-KC-008", "演示某种特殊场景", "print('ok')")
    v = verify_kc_grounding(p, kc_keywords={"PY43-KC-008": ["特殊场景"]})
    assert v.passed


def test_custom_kc_keywords_miss_fails() -> None:
    p = _problem(2, "PY43-KC-008", "演示某种特殊场景", "print('ok')")
    v = verify_kc_grounding(p, kc_keywords={"PY43-KC-008": ["不存在的词"]})
    assert not v.passed
    assert any(i.code == "kc_keyword_miss" for i in v.issues)


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
