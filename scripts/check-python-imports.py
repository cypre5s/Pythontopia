"""Check production Python dependencies without importing application code.

Only backend/app is read. Function-local and constant dynamic imports count as
dependencies; package initializers may assemble their child modules normally.
Simple aliases and later assignments are tracked in source order. Computed
import names, arbitrary control-flow execution and object types are not inferred.
"""

from __future__ import annotations

import ast
import json
import sys
from pathlib import Path
from typing import NamedTuple


class Finding(NamedTuple):
    module: str
    line: int
    message: str


def relative_module(
    module: str, source: str | None, level: int, initializers: set[str]
) -> str:
    if not level:
        return source or ""
    package = module.split(".") if module in initializers else module.split(".")[:-1]
    if level > len(package):
        return ""
    return ".".join(package[: len(package) - level + 1] + ([source] if source else []))


def cyclic_groups(graph: dict[str, set[str]]) -> list[list[str]]:
    """Tarjan SCCs, including an explicit module importing itself."""
    indexes: dict[str, int] = {}
    low: dict[str, int] = {}
    stack: list[str] = []
    active: set[str] = set()
    groups: list[list[str]] = []

    def visit(node: str) -> None:
        indexes[node] = low[node] = len(indexes)
        stack.append(node)
        active.add(node)
        for target in sorted(graph[node]):
            if target not in indexes:
                visit(target)
                low[node] = min(low[node], low[target])
            elif target in active:
                low[node] = min(low[node], indexes[target])
        if low[node] != indexes[node]:
            return
        group = []
        while True:
            target = stack.pop()
            active.remove(target)
            group.append(target)
            if target == node:
                break
        if len(group) > 1 or node in graph[node]:
            groups.append(sorted(group))

    for node in sorted(graph):
        if node not in indexes:
            visit(node)
    return sorted(groups)


def dotted_name(node: ast.AST) -> str | None:
    if isinstance(node, ast.Name):
        return node.id
    if isinstance(node, ast.Attribute):
        parent = dotted_name(node.value)
        return f"{parent}.{node.attr}" if parent else None
    return None


def scope_nodes(node: ast.AST):
    """Walk one binding scope; nested functions/classes have their own bindings."""
    for child in ast.iter_child_nodes(node):
        yield child
        if not isinstance(
            child, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef, ast.Lambda)
        ):
            yield from scope_nodes(child)


class DependencyVisitor(ast.NodeVisitor):
    def __init__(self, module: str, known: set[str], initializers: set[str]):
        self.module, self.known, self.initializers = module, known, initializers
        self.dependencies: set[str] = set()
        self.findings: set[Finding] = set()
        self.bindings: dict[str, str] = {}

    def source(self, node: ast.ImportFrom) -> str:
        return relative_module(self.module, node.module, node.level, self.initializers)

    def import_bindings(self, node: ast.AST) -> dict[str, str]:
        if isinstance(node, ast.Import):
            return {
                entry.asname or entry.name.split(".")[0]: entry.name
                if entry.asname
                else entry.name.split(".")[0]
                for entry in node.names
            }
        if isinstance(node, ast.ImportFrom):
            source = self.source(node)
            return {
                entry.asname or entry.name: f"{source}.{entry.name}"
                for entry in node.names
            }
        return {}

    def qualified(self, node: ast.AST) -> str | None:
        name = dotted_name(node)
        if not name:
            return None
        first, *rest = name.split(".")
        target = self.bindings.get(first)
        return (
            ".".join([target, *rest])
            if target
            else name
            if first == "__import__"
            else None
        )

    def dynamic_module(self, node: ast.AST) -> str | None:
        if not isinstance(node, ast.Call):
            return None
        name = (
            node.args[0]
            if node.args
            else next(
                (item.value for item in node.keywords if item.arg == "name"), None
            )
        )
        if not isinstance(name, ast.Constant):
            return None
        value = name.value
        if not isinstance(value, str):
            return None
        function = self.qualified(node.func)
        if function not in {"importlib.import_module", "__import__"}:
            return None
        if value.startswith("."):
            package = (
                node.args[1]
                if len(node.args) > 1
                else next(
                    (item.value for item in node.keywords if item.arg == "package"),
                    None,
                )
            )
            if isinstance(package, ast.Constant) and isinstance(package.value, str):
                level = len(value) - len(value.lstrip("."))
                return relative_module(
                    package.value, value.lstrip("."), level, {package.value}
                )
            return None
        return value

    def enter_scope(
        self, node: ast.AST, arguments: ast.arguments | None = None
    ) -> None:
        previous = self.bindings
        self.bindings = dict(previous)
        nodes = list(scope_nodes(node))
        # Python local bindings shadow outer module aliases, including parameters.
        for item in nodes:
            if isinstance(item, ast.Name) and isinstance(
                item.ctx, (ast.Store, ast.Del)
            ):
                self.bindings.pop(item.id, None)
        if arguments:
            for item in (
                *arguments.posonlyargs,
                *arguments.args,
                *arguments.kwonlyargs,
            ):
                self.bindings.pop(item.arg, None)
            for item in (arguments.vararg, arguments.kwarg):
                if item:
                    self.bindings.pop(item.arg, None)
        for item in nodes:
            self.bindings.update(self.import_bindings(item))
        self.generic_visit(node)
        self.bindings = previous

    def visit_Module(self, node: ast.Module) -> None:
        self.enter_scope(node)

    def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
        self.enter_scope(node, node.args)

    visit_AsyncFunctionDef = visit_FunctionDef

    def visit_ClassDef(self, node: ast.ClassDef) -> None:
        self.enter_scope(node)

    def visit_Lambda(self, node: ast.Lambda) -> None:
        self.enter_scope(node, node.args)

    def visit_Import(self, node: ast.Import) -> None:
        self.bindings.update(self.import_bindings(node))
        self.dependencies.update(
            entry.name for entry in node.names if entry.name in self.known
        )

    def visit_ImportFrom(self, node: ast.ImportFrom) -> None:
        self.bindings.update(self.import_bindings(node))
        source = self.source(node)
        for entry in node.names:
            child = f"{source}.{entry.name}"
            if child in self.known:
                self.dependencies.add(child)
            elif source in self.known:
                self.dependencies.add(source)
            if (
                source in self.known
                and source != self.module
                and entry.name.startswith("_")
                and not entry.name.startswith("__")
            ):
                self.findings.add(
                    Finding(
                        self.module,
                        node.lineno,
                        f"跨模块私有导入：{source}.{entry.name}",
                    )
                )

    def assign_binding(self, assigned: ast.AST, value: ast.AST | None) -> None:
        target = (
            self.dynamic_module(value) or self.qualified(value)
            if value is not None
            else None
        )
        if isinstance(assigned, ast.Name):
            if target:
                self.bindings[assigned.id] = target
            else:
                self.bindings.pop(assigned.id, None)
        else:
            for item in ast.walk(assigned):
                if isinstance(item, ast.Name) and isinstance(item.ctx, ast.Store):
                    self.bindings.pop(item.id, None)

    def visit_Assign(self, node: ast.Assign) -> None:
        self.generic_visit(node)
        for assigned in node.targets:
            self.assign_binding(assigned, node.value)

    def visit_AnnAssign(self, node: ast.AnnAssign) -> None:
        self.generic_visit(node)
        self.assign_binding(node.target, node.value)

    def visit_AugAssign(self, node: ast.AugAssign) -> None:
        self.generic_visit(node)
        self.assign_binding(node.target, None)

    def visit_NamedExpr(self, node: ast.NamedExpr) -> None:
        self.generic_visit(node)
        self.assign_binding(node.target, node.value)

    def visit_Call(self, node: ast.Call) -> None:
        target = self.dynamic_module(node)
        if target in self.known:
            self.dependencies.add(target)
        self.generic_visit(node)

    def visit_Attribute(self, node: ast.Attribute) -> None:
        source = self.qualified(node.value)
        # Only project module attributes are checked. Framework internals and
        # attributes on imported classes/objects are outside this boundary rule.
        if (
            source in self.known
            and source != self.module
            and node.attr.startswith("_")
            and not node.attr.startswith("__")
        ):
            self.findings.add(
                Finding(
                    self.module, node.lineno, f"跨模块私有访问：{source}.{node.attr}"
                )
            )
        self.generic_visit(node)


def analyze_sources(
    sources: dict[str, str], initializers: set[str] | None = None
) -> tuple[dict[str, set[str]], list[Finding]]:
    graph: dict[str, set[str]] = {module: set() for module in sources}
    findings: list[Finding] = []
    for module, source in sorted(sources.items()):
        try:
            tree = ast.parse(source)
        except SyntaxError as exc:
            findings.append(
                Finding(module, exc.lineno or 0, f"Python 语法错误：{exc.msg}")
            )
            continue
        visitor = DependencyVisitor(module, set(sources), initializers or set())
        visitor.visit(tree)
        graph[module] = visitor.dependencies
        findings.extend(visitor.findings)
    for group in cyclic_groups(graph):
        findings.append(Finding(group[0], 0, "循环依赖：" + "、".join(group)))
    return graph, sorted(findings)


def main() -> int:
    root = Path(__file__).resolve().parents[1] / "backend" / "app"
    if not root.is_dir():
        print(f"找不到生产源码目录：{root}", file=sys.stderr)
        return 1
    sources: dict[str, str] = {}
    initializers: set[str] = set()
    for path in sorted(root.rglob("*.py")):
        if not path.resolve().is_relative_to(root.resolve()):
            print(f"拒绝扫描源码目录之外的路径：{path}", file=sys.stderr)
            return 1
        parts = path.relative_to(root).with_suffix("").parts
        module = ".".join(("app", *parts)).removesuffix(".__init__")
        sources[module] = path.read_text(encoding="utf-8")
        if path.name == "__init__.py":
            initializers.add(module)
    graph, findings = analyze_sources(sources, initializers)
    if "--json" in sys.argv[1:]:
        print(
            json.dumps(
                {
                    "modules": len(graph),
                    "errors": [item._asdict() for item in findings],
                },
                ensure_ascii=False,
                indent=2,
            )
        )
    else:
        print(
            f"Python 模块依赖：检查 {len(graph)} 个生产模块，{len(findings)} 个错误。"
        )
        for item in findings:
            print(f"{item.module}:{item.line}: {item.message}")
    return int(bool(findings))


if __name__ == "__main__":
    raise SystemExit(main())
