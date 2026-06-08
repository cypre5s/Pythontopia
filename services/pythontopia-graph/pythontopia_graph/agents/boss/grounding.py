"""KC 接地审查：关键词命中与超纲语法拒绝（纯静态，防幻觉与超纲出题）。

校验题面与 ``reference_solution`` 是否含 KC 关键词，并用 ``ast.walk`` 对照章节语法白名单。
"""

from __future__ import annotations

import ast
import re
from typing import Iterable, Mapping

from .schemas import BossProblem, ReviewIssue, ReviewVerdict


CHAPTER_SYNTAX_WHITELIST: Mapping[int, frozenset[str]] = {
    # 第 1 章入门：允许 print/input/赋值/基础算术/方法调用（split/map）
    1: frozenset({
        "Module", "Expr", "Constant", "Name", "Assign", "Call", "Load", "Store",
        "BinOp", "Add", "Sub", "Mult", "Div", "FloorDiv", "Mod", "Pow",
        "UnaryOp", "USub", "UAdd",
        "Attribute", "Tuple",
        "FormattedValue", "JoinedStr",
    }),
    2: frozenset({
        "Module", "Expr", "Constant", "Name", "Assign", "Call", "Load", "Store",
        "BinOp", "Add", "Sub", "Mult", "Div", "FloorDiv", "Mod", "Pow",
        "UnaryOp", "USub", "UAdd",
        "Attribute", "Tuple",
        "List", "Dict", "Subscript", "Index", "Slice",
        "Compare", "Eq", "NotEq", "Lt", "LtE", "Gt", "GtE",
        "FormattedValue", "JoinedStr",
    }),
    3: frozenset({
        # 章 3 控制流：if/elif/else + 三元 (IfExp) 都属于条件主题。
        "Module", "Expr", "Constant", "Name", "Assign", "Call", "Load", "Store",
        "If", "IfExp", "Compare", "BoolOp", "And", "Or", "Not", "Eq", "NotEq",
        "Lt", "LtE", "Gt", "GtE", "UnaryOp", "USub", "UAdd",
        "BinOp", "Add", "Sub", "Mult", "Div", "FloorDiv", "Mod", "Pow",
        "Attribute",
        "List", "Tuple", "Dict", "Subscript", "Index", "Slice",
        "FormattedValue", "JoinedStr",
    }),
    4: frozenset({
        "Module", "Expr", "Constant", "Name", "Assign", "Call", "Load", "Store",
        "If", "IfExp", "For", "While", "Break", "Continue", "AugAssign",
        "Compare", "BoolOp", "And", "Or", "Not", "Eq", "NotEq",
        "Lt", "LtE", "Gt", "GtE", "UnaryOp", "USub", "UAdd",
        "BinOp", "Add", "Sub", "Mult", "Div", "FloorDiv", "Mod", "Pow",
        "Attribute",
        "List", "Tuple", "Dict", "Subscript", "Index", "Slice",
        "ListComp", "GeneratorExp", "comprehension",
        "FormattedValue", "JoinedStr",
    }),
    5: frozenset({
        "Module", "Expr", "Constant", "Name", "Assign", "Call", "Load", "Store",
        "If", "IfExp", "For", "While", "Break", "Continue", "AugAssign",
        "FunctionDef", "Return", "arguments", "arg", "Lambda", "Starred",
        "Compare", "BoolOp", "And", "Or", "Not", "Eq", "NotEq",
        "Lt", "LtE", "Gt", "GtE", "UnaryOp", "USub", "UAdd",
        "BinOp", "Add", "Sub", "Mult", "Div", "FloorDiv", "Mod", "Pow",
        "Attribute",
        "List", "Tuple", "Dict", "Subscript", "Index", "Slice",
        "ListComp", "GeneratorExp", "comprehension",
        "FormattedValue", "JoinedStr",
    }),
    6: frozenset({
        "Module", "Expr", "Constant", "Name", "Assign", "Call", "Load", "Store",
        "If", "IfExp", "For", "While", "Break", "Continue", "AugAssign",
        "FunctionDef", "AsyncFunctionDef", "Return", "arguments", "arg", "Lambda", "Starred",
        "ClassDef", "Attribute",
        "Compare", "BoolOp", "And", "Or", "Not", "Eq", "NotEq",
        "Lt", "LtE", "Gt", "GtE", "UnaryOp", "USub", "UAdd",
        "BinOp", "Add", "Sub", "Mult", "Div", "FloorDiv", "Mod", "Pow",
        "List", "Tuple", "Dict", "Subscript", "Index", "Slice",
        "ListComp", "GeneratorExp", "comprehension",
        "FormattedValue", "JoinedStr",
    }),
    7: frozenset({
        "Module", "Expr", "Constant", "Name", "Assign", "Call", "Load", "Store",
        "If", "IfExp", "For", "While", "Break", "Continue", "AugAssign",
        "FunctionDef", "AsyncFunctionDef", "Return", "arguments", "arg", "Lambda", "Starred",
        "ClassDef", "Attribute",
        "Import", "ImportFrom", "alias",
        "Try", "ExceptHandler", "Raise", "With", "withitem", "Pass",
        "Compare", "BoolOp", "And", "Or", "Not", "Eq", "NotEq",
        "Lt", "LtE", "Gt", "GtE", "UnaryOp", "USub", "UAdd",
        "BinOp", "Add", "Sub", "Mult", "Div", "FloorDiv", "Mod", "Pow",
        "List", "Tuple", "Dict", "Set",
        "Subscript", "Index", "Slice",
        "ListComp", "GeneratorExp", "SetComp", "DictComp", "comprehension",
        "FormattedValue", "JoinedStr",
    }),
}


def verify_kc_grounding(
    problem: BossProblem,
    *,
    kc_keywords: Mapping[str, Iterable[str]] | None = None,
    min_keyword_hits: int = 1,
) -> ReviewVerdict:
    """校验 KC 关键词接地与章节语法白名单，未命中或超纲则 reject。"""
    issues: list[ReviewIssue] = []

    keywords = list(_resolve_keywords(problem.kc_code, kc_keywords))
    if not keywords:
        # 无 KC 关键词列表时仅做超纲检查
        issues.append(
            ReviewIssue(
                review="grounding",
                severity="warning",
                code="no_keywords_provided",
                message=f"kc_code {problem.kc_code} has no keyword list; only checking syntax allowlist",
            )
        )

    haystack = f"{problem.statement}\n{problem.reference_solution}".lower()
    hits = sum(1 for kw in keywords if kw.lower() in haystack)
    if keywords and hits < min_keyword_hits:
        issues.append(
            ReviewIssue(
                review="grounding",
                severity="error",
                code="kc_keyword_miss",
                message=(
                    f"statement+solution hits {hits}/{len(keywords)} keywords for {problem.kc_code}; "
                    f"need >= {min_keyword_hits}"
                ),
            )
        )

    forbidden_nodes = _scan_unsupported_syntax(problem.chapter, problem.reference_solution)
    if forbidden_nodes:
        issues.append(
            ReviewIssue(
                review="grounding",
                severity="error",
                code="syntax_out_of_scope",
                message=(
                    f"chapter {problem.chapter} forbids AST nodes: {sorted(set(forbidden_nodes))}; "
                    "AI generated solution beyond curriculum scope"
                ),
            )
        )

    errors = [i for i in issues if i.severity == "error"]
    return ReviewVerdict(review="grounding", passed=not errors, issues=issues)


def _resolve_keywords(
    kc_code: str,
    kc_keywords: Mapping[str, Iterable[str]] | None,
) -> list[str]:
    if kc_keywords and kc_code in kc_keywords:
        return list(kc_keywords[kc_code])
    # 无外部词表时按 kc_code 推断章节默认关键词
    return _default_keywords(kc_code)


_DEFAULT_KEYWORDS_BY_CHAPTER: Mapping[int, list[str]] = {
    1: ["print", "input", "变量", "注释"],
    2: ["list", "dict", "str", "int", "float", "类型", "切片"],
    3: ["if", "elif", "else", "条件", "判断", "and", "or"],
    4: ["for", "while", "break", "continue", "循环", "range"],
    5: ["def", "return", "函数", "参数", "默认参数", "lambda"],
    6: ["class", "self", "继承", "super", "方法", "属性"],
    7: ["import", "open", "try", "except", "raise", "with", "异常"],
}


def _default_keywords(kc_code: str) -> list[str]:
    m = re.match(r"^PY43-KC-(\d{3})$", kc_code)
    if not m:
        return []
    n = int(m.group(1))
    if n <= 5:
        chapter = 1
    elif n <= 15:
        chapter = 2
    elif n <= 25:
        chapter = 3
    elif n <= 35:
        chapter = 4
    elif n <= 45:
        chapter = 5
    elif n <= 55:
        chapter = 6
    else:
        chapter = 7
    return list(_DEFAULT_KEYWORDS_BY_CHAPTER.get(chapter, []))


def _scan_unsupported_syntax(chapter: int, source: str) -> list[str]:
    allowed = CHAPTER_SYNTAX_WHITELIST.get(chapter)
    if not allowed:
        return []
    try:
        tree = ast.parse(source)
    except SyntaxError:
        return ["SyntaxError"]
    bad: list[str] = []
    for node in ast.walk(tree):
        name = type(node).__name__
        if name in allowed:
            continue
        if name in {"Load", "Store", "Del", "Param"}:
            continue
        if name in {"USub", "UAdd", "UnaryOp", "BoolOp", "keyword", "Tuple"}:
            continue
        if name not in allowed:
            bad.append(name)
    return bad
