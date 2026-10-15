"""难度校准：比对 AI 自报 ``difficulty`` 与 AST 特征估算值，偏差过大则 reject。"""

from __future__ import annotations

import ast
from dataclasses import dataclass

from .schemas import BossProblem, ReviewIssue, ReviewVerdict


@dataclass(frozen=True)
class DifficultyFeatures:
    statement_len: int
    solution_lines: int
    ast_depth: int
    loop_count: int
    if_count: int
    def_count: int

    def score(self) -> float:
        return (
            self.statement_len / 1000.0
            + self.solution_lines * 0.3
            + self.ast_depth * 0.5
            + self.loop_count * 1.0
            + self.if_count * 0.7
            + self.def_count * 1.2
        )


def estimate_difficulty(problem: BossProblem) -> ReviewVerdict:
    """偏差 ≤1 通过；=2 以估算值覆盖；>2 reject。"""
    issues: list[ReviewIssue] = []
    features = _extract_features(problem)
    score = features.score()
    estimated = _bucket(score)

    diff = abs(estimated - problem.difficulty)
    metadata = {
        "estimated": estimated,
        "declared": problem.difficulty,
        "diff": diff,
        "score": round(score, 3),
        "features": features.__dict__,
    }

    if diff == 0:
        return ReviewVerdict(review="difficulty", passed=True, issues=[], metadata=metadata)
    if diff == 1:
        issues.append(
            ReviewIssue(
                review="difficulty",
                severity="warning",
                code="difficulty_offset_minor",
                message=(
                    f"declared difficulty {problem.difficulty} differs by 1 from estimated {estimated}; "
                    "kept as is"
                ),
            )
        )
        return ReviewVerdict(review="difficulty", passed=True, issues=issues, metadata=metadata)
    if diff == 2:
        problem.difficulty = estimated
        issues.append(
            ReviewIssue(
                review="difficulty",
                severity="warning",
                code="difficulty_overwritten",
                message=(
                    f"declared difficulty mismatched estimated {estimated} by 2; "
                    "auto-overwriting problem.difficulty"
                ),
            )
        )
        return ReviewVerdict(review="difficulty", passed=True, issues=issues, metadata=metadata)
    issues.append(
        ReviewIssue(
            review="difficulty",
            severity="error",
            code="difficulty_far_off",
            message=(
                f"declared difficulty {problem.difficulty} differs by {diff} from estimated {estimated}; "
                "rejecting problem"
            ),
        )
    )
    return ReviewVerdict(review="difficulty", passed=False, issues=issues, metadata=metadata)


def _extract_features(problem: BossProblem) -> DifficultyFeatures:
    source = problem.reference_solution
    try:
        tree = ast.parse(source)
    except SyntaxError:
        return DifficultyFeatures(
            statement_len=len(problem.statement),
            solution_lines=len(source.splitlines()),
            ast_depth=0,
            loop_count=0,
            if_count=0,
            def_count=0,
        )
    return DifficultyFeatures(
        statement_len=len(problem.statement),
        solution_lines=len([ln for ln in source.splitlines() if ln.strip()]),
        ast_depth=_max_depth(tree),
        loop_count=sum(1 for n in ast.walk(tree) if isinstance(n, (ast.For, ast.While))),
        if_count=sum(1 for n in ast.walk(tree) if isinstance(n, ast.If)),
        def_count=sum(
            1 for n in ast.walk(tree) if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef))
        ),
    )


_NOISE_AST_NODES = (ast.Load, ast.Store, ast.Del, ast.Param)


def _max_depth(node: ast.AST, depth: int = 0) -> int:
    """跳过 ctx 类装饰节点（Load/Store/Del/Param），它们对教学复杂度无贡献。"""
    children = [c for c in ast.iter_child_nodes(node) if not isinstance(c, _NOISE_AST_NODES)]
    if not children:
        return depth
    return max(_max_depth(c, depth + 1) for c in children)


def _bucket(score: float) -> int:
    if score < 2:
        return 1
    if score < 4:
        return 2
    if score < 7:
        return 3
    if score < 11:
        return 4
    return 5
