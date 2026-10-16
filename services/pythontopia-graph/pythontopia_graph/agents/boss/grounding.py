"""KC 接地审查：题面与参考程序关键词命中、超纲语法拒绝（纯静态）。

校验权威 KC 术语不能只出现在题面里，并用 ``ast.walk`` 对照章节语法白名单。
"""

from __future__ import annotations

import ast
import re
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
        # KC-039 explicitly teaches random-module use together with lists.
        "Import", "ImportFrom", "alias",
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

_KC_KEYWORD_SPLIT = re.compile(
    r"[，。；：、（）()\n\r]+|包括|从|到|以及|等|的|和|与|及|在|领域|事业|"
    r"认识|了解|发展历程|自主研发历程|前沿成就|创新贡献|超级计算机|计算机|原型机"
)
_KC_KEYWORD_NOISE = frozenset(
    {"中国", "发展", "历程", "成就", "成果", "前沿", "创新", "贡献", "领域", "事业"}
)


def extract_kc_grounding_keywords(kc_name: str, kc_summary: str) -> list[str]:
    """Use the authoritative KC title and distinctive summary terms for grounding."""
    keywords = [kc_name.strip()] if kc_name.strip() else []
    for part in _KC_KEYWORD_SPLIT.split(kc_summary):
        keyword = re.sub(r"\s+", "", part).strip(" .,:;!?\"'`")
        if len(keyword) >= 2 and keyword not in _KC_KEYWORD_NOISE and keyword not in keywords:
            keywords.append(keyword)
    for token in re.findall(r"\b[A-Za-z_][A-Za-z_0-9]*\b", kc_summary):
        if len(token) >= 3 and token.lower() != "python" and token.lower() not in {
            keyword.lower() for keyword in keywords
        }:
            keywords.append(token)
    return keywords


def _is_trivial_input_echo(code: str) -> bool:
    try:
        body = ast.parse(code).body
    except (SyntaxError, TypeError):
        return False

    input_names: set[str] = set()
    echoes_input = False
    for statement in body:
        if isinstance(statement, ast.Assign):
            if (
                len(statement.targets) != 1
                or not isinstance(statement.targets[0], ast.Name)
                or not isinstance(statement.value, ast.Call)
                or not isinstance(statement.value.func, ast.Name)
                or statement.value.func.id != "input"
                or statement.value.keywords
            ):
                return False
            input_names.add(statement.targets[0].id)
            continue

        if not isinstance(statement, ast.Expr) or not isinstance(statement.value, ast.Call):
            return False
        output = statement.value
        if not isinstance(output.func, ast.Name) or output.func.id != "print" or output.keywords:
            return False
        for argument in output.args:
            if isinstance(argument, ast.Name) and argument.id in input_names:
                echoes_input = True
            elif isinstance(argument, ast.Constant):
                continue
            elif (
                isinstance(argument, ast.Call)
                and isinstance(argument.func, ast.Name)
                and argument.func.id == "input"
                and not argument.keywords
            ):
                echoes_input = True
            else:
                return False

    return echoes_input


def _is_constant_output_only(code: str) -> bool:
    try:
        body = ast.parse(code).body
    except (SyntaxError, TypeError):
        return False

    return bool(body) and all(
        isinstance(statement, ast.Expr)
        and isinstance(statement.value, ast.Call)
        and isinstance(statement.value.func, ast.Name)
        and statement.value.func.id == "print"
        and not statement.value.keywords
        and bool(statement.value.args)
        and all(isinstance(argument, ast.Constant) for argument in statement.value.args)
        for statement in body
    )


def _uses_variable_assignment_and_arithmetic(code: str) -> bool:
    try:
        tree = ast.parse(code)
    except (SyntaxError, TypeError):
        return False

    has_variable_assignment = any(
        isinstance(node, ast.Assign)
        and any(isinstance(target, ast.Name) for target in node.targets)
        for node in ast.walk(tree)
    )
    arithmetic_operators = (ast.Add, ast.Sub, ast.Mult, ast.Div, ast.FloorDiv, ast.Mod, ast.Pow)
    has_arithmetic_operation = any(
        isinstance(node, ast.BinOp) and isinstance(node.op, arithmetic_operators)
        for node in ast.walk(tree)
    )
    return has_variable_assignment and has_arithmetic_operation


def verify_kc_grounding(
    problem: BossProblem,
    *,
    kc_keywords: Mapping[str, Iterable[str]] | None = None,
    min_keyword_hits: int = 1,
) -> ReviewVerdict:
    """拒绝空洞输出，并校验 KC 关键词接地与章节语法白名单。

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
    if _is_trivial_input_echo(problem.reference_solution):
        issues.append(
            ReviewIssue(
                review="grounding",
                severity="error",
                code="trivial_echo_solution",
                message="input-to-output echo does not assess a programming skill or the target KC",
            )
        )

    output_keywords = ("打印", "输出语句", "打印语句")
    statement = re.sub(r"\s+", "", problem.statement).lower()
    teaches_output = any(
        term in statement for term in output_keywords
    ) or bool(
        re.search(r"(?<![a-z0-9_])print(?![a-z0-9_])", statement, re.IGNORECASE)
    )
    if not override:
        teaches_output = teaches_output or "print" in keywords
    if _is_constant_output_only(problem.reference_solution) and not teaches_output:
        issues.append(
            ReviewIssue(
                review="grounding",
                severity="error",
                code="trivial_constant_output",
                message="printing fixed literals does not assess the target KC; require input processing or computation",
            )
        )

    def normalize(text: str) -> str:
        return re.sub(r"\s+", "", text).lower()

    haystack = normalize(f"{problem.statement}\n{problem.reference_solution}")
    solution = normalize(problem.reference_solution)
    hits = sum(1 for kw in keywords if normalize(kw) in haystack)
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

    uses_kc010_semantics = (
        problem.kc_code == "PY43-KC-010"
        and _uses_variable_assignment_and_arithmetic(problem.reference_solution)
    )
    if override and keywords and problem.kc_code == "PY43-KC-010" and not uses_kc010_semantics:
        issues.append(
            ReviewIssue(
                review="grounding",
                severity="error",
                code="kc_solution_miss",
                message="PY43-KC-010 reference_solution must assign a variable and execute an arithmetic operation",
            )
        )
    elif (
        override
        and keywords
        and not teaches_output
        and not any(normalize(keyword) in solution for keyword in keywords)
        and not uses_kc010_semantics
    ):
        issues.append(
            ReviewIssue(
                review="grounding",
                severity="error",
                code="kc_solution_miss",
                message=(
                    f"reference_solution uses none of the authoritative KC terms for {problem.kc_code}; "
                    "the target KC cannot appear only in story context before bank approval"
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
