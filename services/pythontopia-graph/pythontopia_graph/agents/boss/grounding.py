"""KC 接地审查：关键词命中与超纲语法拒绝（纯静态，防幻觉与超纲出题）。

校验题面与 ``reference_solution`` 是否含 KC 关键词，并用 ``ast.walk`` 对照章节语法白名单。
"""

from __future__ import annotations

import ast
from typing import Iterable, Mapping

from .kc_map import is_known_kc
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
    """校验 KC 关键词接地与章节语法白名单，未命中或超纲则 reject。

    关键词来源优先级：
    1. 外部 ``kc_keywords[kc_code]`` 显式词表；
    2. 按 ``problem.chapter`` 取本章默认关键词（关卡课程的题面主题词）。

    章节默认关键词按 ``problem.chapter`` 选取（题目自身权威的关卡章节），不再用编号边界
    反推章节——历史上那段 ``n <= 5 → 1`` 的反推与课程包真实分章不符（见 ``kc_map``）。
    另外用 ``kc_map.is_known_kc`` 做防幻觉提示：KC 不在课程包内仅 warning，不阻断。
    """
    issues: list[ReviewIssue] = []

    override = bool(kc_keywords and problem.kc_code in kc_keywords)
    if override:
        keywords = list(kc_keywords[problem.kc_code])  # type: ignore[index]
    else:
        keywords = _default_keywords_for_chapter(problem.chapter)
        if not is_known_kc(problem.kc_code):
            issues.append(
                ReviewIssue(
                    review="grounding",
                    severity="warning",
                    code="no_keywords_provided",
                    message=(
                        f"kc_code {problem.kc_code} not in course pack (packId=43); "
                        f"falling back to chapter {problem.chapter} default keywords"
                    ),
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


# 关卡（boss/arena）课程章节的题面主题词，按关卡章节 1..7 索引。
# 注意：这是「关卡课程」（golden 题库 + LLM 出题 prompt 采用的循序渐进结构）的主题词，
# 与 ``kc_map`` 的课程包 chapter↔KC 是两条不同的轴；此处按 ``problem.chapter`` 取用。
_DEFAULT_KEYWORDS_BY_CHAPTER: Mapping[int, list[str]] = {
    1: ["print", "input", "变量", "注释"],
    2: ["list", "dict", "str", "int", "float", "类型", "切片"],
    3: ["if", "elif", "else", "条件", "判断", "and", "or"],
    4: ["for", "while", "break", "continue", "循环", "range"],
    5: ["def", "return", "函数", "参数", "默认参数", "lambda"],
    6: ["class", "self", "继承", "super", "方法", "属性"],
    7: ["import", "open", "try", "except", "raise", "with", "异常"],
}


def _default_keywords_for_chapter(chapter: int) -> list[str]:
    """按关卡章号取本章默认关键词；章号越界返回空列表。"""

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
