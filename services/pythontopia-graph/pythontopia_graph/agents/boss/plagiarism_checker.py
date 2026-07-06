"""查重与敏感词审查：与既有题库及玩家近期做题做相似度比对，并扫描敏感词。"""

from __future__ import annotations

from dataclasses import dataclass
from difflib import SequenceMatcher
from typing import Iterable, Sequence

from .schemas import BossProblem, ReviewIssue, ReviewVerdict


SENSITIVE_KEYWORDS = (
    "色情", "暴力", "歧视", "种族", "政治", "辱骂",
    "porn", "violence", "racist", "fuck", "shit",
)

DEFAULT_SIMILARITY_THRESHOLD = 0.8


@dataclass
class ExistingProblem:
    """既有题库中一道题的最小投影。"""

    problem_id: str
    statement: str


def is_too_similar_to_existing(
    problem: BossProblem,
    existing: Sequence[ExistingProblem],
    threshold: float = DEFAULT_SIMILARITY_THRESHOLD,
) -> ReviewVerdict:
    """``SequenceMatcher`` 相似度达阈值或含敏感词则 reject；默认阈值 0.8。"""
    issues: list[ReviewIssue] = []
    metadata: dict[str, object] = {"checked": len(existing), "threshold": threshold}
    high_sim: list[tuple[str, float]] = []
    for ep in existing:
        sim = SequenceMatcher(None, problem.statement, ep.statement).ratio()
        if sim >= threshold:
            high_sim.append((ep.problem_id, sim))
    if high_sim:
        for pid, sim in high_sim:
            issues.append(
                ReviewIssue(
                    review="plagiarism",
                    severity="error",
                    code="too_similar",
                    message=f"similar to existing problem id={pid} sim={sim:.2f}",
                )
            )
    metadata["high_sim"] = high_sim

    sensitive = _scan_sensitive(problem)
    if sensitive:
        issues.append(
            ReviewIssue(
                review="plagiarism",
                severity="error",
                code="sensitive_words",
                message=f"contains sensitive tokens: {sensitive}",
            )
        )

    errors = [i for i in issues if i.severity == "error"]
    return ReviewVerdict(
        review="plagiarism", passed=not errors, issues=issues, metadata=metadata
    )


def _scan_sensitive(problem: BossProblem) -> list[str]:
    text = "\n".join([problem.statement, problem.reference_solution, *problem.hints]).lower()
    found: list[str] = []
    for kw in SENSITIVE_KEYWORDS:
        if kw.lower() in text:
            found.append(kw)
    return found


def list_sensitive_keywords() -> Iterable[str]:
    return SENSITIVE_KEYWORDS
