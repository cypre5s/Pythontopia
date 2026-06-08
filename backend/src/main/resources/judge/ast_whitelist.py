#!/usr/bin/env python3
"""AST 白名单静态检查：只允许教学 Python 子集节点与 import。"""
from __future__ import annotations

import ast
import sys

ALLOWED_IMPORT_ROOTS = frozenset(
    {
        "math",
        "random",
        "itertools",
        "functools",
        "collections",
        "statistics",
        "decimal",
        "fractions",
        "string",
        "re",
        "copy",
        "heapq",
        "bisect",
        "operator",
        "typing",
    }
)

ALLOWED_NODE_TYPES = frozenset(
    {
        ast.Module,
        ast.FunctionDef,
        ast.AsyncFunctionDef,
        ast.Return,
        ast.Delete,
        ast.Assign,
        ast.AugAssign,
        ast.AnnAssign,
        ast.For,
        ast.AsyncFor,
        ast.While,
        ast.If,
        ast.With,
        ast.AsyncWith,
        ast.Raise,
        ast.Try,
        ast.ExceptHandler,
        ast.Assert,
        ast.Pass,
        ast.Break,
        ast.Continue,
        ast.Global,
        ast.Nonlocal,
        ast.Expr,
        ast.Call,
        ast.Constant,
        ast.Name,
        ast.Load,
        ast.Store,
        ast.Del,
        ast.BinOp,
        ast.UnaryOp,
        ast.BoolOp,
        ast.Compare,
        ast.IfExp,
        ast.Subscript,
        ast.Slice,
        ast.List,
        ast.Tuple,
        ast.Set,
        ast.Dict,
        ast.ListComp,
        ast.SetComp,
        ast.DictComp,
        ast.GeneratorExp,
        ast.comprehension,
        ast.JoinedStr,
        ast.FormattedValue,
        ast.Attribute,
        ast.Starred,
        ast.keyword,
        ast.arguments,
        ast.arg,
        ast.alias,
        ast.Import,
        ast.ImportFrom,
        ast.Add,
        ast.Sub,
        ast.Mult,
        ast.Div,
        ast.FloorDiv,
        ast.Mod,
        ast.Pow,
        ast.LShift,
        ast.RShift,
        ast.BitOr,
        ast.BitXor,
        ast.BitAnd,
        ast.MatMult,
        ast.UAdd,
        ast.USub,
        ast.Not,
        ast.Invert,
        ast.And,
        ast.Or,
        ast.Eq,
        ast.NotEq,
        ast.Lt,
        ast.LtE,
        ast.Gt,
        ast.GtE,
        ast.Is,
        ast.IsNot,
        ast.In,
        ast.NotIn,
    }
)

BANNED_NAMES = frozenset(
    {
        "__import__",
        "eval",
        "exec",
        "compile",
        "open",
        "breakpoint",
        "getattr",
        "setattr",
        "delattr",
        "globals",
        "locals",
        "vars",
        "help",
        "exit",
        "quit",
    }
)

BANNED_ATTRS = frozenset(
    {
        "__class__",
        "__mro__",
        "__bases__",
        "__subclasses__",
        "__globals__",
        "__builtins__",
        "__code__",
        "__closure__",
    }
)


def _check_import(node: ast.Import | ast.ImportFrom) -> str | None:
    if isinstance(node, ast.Import):
        for alias in node.names:
            root = (alias.name or "").split(".")[0]
            if root not in ALLOWED_IMPORT_ROOTS:
                return f"banned_import:{alias.name}"
        return None
    module = node.module or ""
    root = module.split(".")[0] if module else ""
    if root and root not in ALLOWED_IMPORT_ROOTS:
        return f"banned_import:{module}"
    return None


def _check_node(node: ast.AST) -> str | None:
    if type(node) not in ALLOWED_NODE_TYPES:
        return f"banned_node:{type(node).__name__}"
    if isinstance(node, ast.Name) and node.id in BANNED_NAMES:
        return f"banned_name:{node.id}"
    if isinstance(node, ast.Attribute) and node.attr in BANNED_ATTRS:
        return f"banned_attr:{node.attr}"
    if isinstance(node, (ast.Import, ast.ImportFrom)):
        return _check_import(node)
    for child in ast.iter_child_nodes(node):
        reason = _check_node(child)
        if reason:
            return reason
    return None


def main() -> int:
    source = sys.stdin.read()
    try:
        tree = ast.parse(source)
    except SyntaxError as exc:
        print(f"syntax_error:{exc.msg}", file=sys.stdout)
        return 1
    reason = _check_node(tree)
    if reason:
        print(reason, file=sys.stdout)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
