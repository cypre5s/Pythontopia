"""Boss 出题数据模型（dataclass，避免与 langgraph 状态字段冲突）。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Literal


ReviewName = Literal[
    "struct",
    "self_test",
    "grounding",
    "difficulty",
    "plagiarism",
    "safety",
]


@dataclass
class ReviewIssue:
    review: ReviewName
    severity: Literal["error", "warning"]
    code: str
    message: str


@dataclass
class TestCase:
    """单条测试用例；至少 3 条。``is_sample`` 标记公开样例，非样例不暴露给玩家（禁止输出隐藏测试）。"""

    input: str
    expected_output: str
    is_sample: bool = False

    # pytest 默认会把名为 Test* 的类当 test class；显式声明 __test__=False 避免误收集。
    __test__ = False


@dataclass
class BossProblem:
    """AI 出题输出结构。所有字段 snake_case，落库时按 BossProblemDto 转换。"""

    problem_id: str
    chapter: int
    stage: int
    kc_code: str
    title: str
    statement: str
    reference_solution: str
    test_cases: list[TestCase]
    difficulty: int
    # 题型；空串=代码题（stdout 沙箱判题）。文本题（short_answer/teach_back/pdf_question）走接地评分。
    kind: str = ""
    hints: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)

    def to_payload(self) -> dict[str, Any]:
        """转为 BossProblemDto 兼容 dict；``testCases`` 与 ``referenceSolution`` 仅后端缓存与评测用。"""
        sample = next((tc for tc in self.test_cases if tc.is_sample), self.test_cases[0])
        return {
            "problemId": self.problem_id,
            "chapter": self.chapter,
            "stage": self.stage,
            "difficulty": self.difficulty,
            "kcCode": self.kc_code,
            # 透传题型，便于 backend 落库并在 evaluate 时回传，evaluator 据此选判题路径。
            "kind": self.kind,
            "statement": self.statement,
            "hints": self.hints,
            "sampleInput": sample.input,
            "sampleOutput": sample.expected_output,
            "totalTestCases": len(self.test_cases),
            "reviewed": True,
            "aiGenerated": True,
            # backend 专用，前端 DTO 不展示
            "testCases": [
                {
                    "input": tc.input,
                    "expectedOutput": tc.expected_output,
                    "isSample": tc.is_sample,
                }
                for tc in self.test_cases
            ],
            "referenceSolution": self.reference_solution,
        }


@dataclass
class ReviewVerdict:
    review: ReviewName
    passed: bool
    issues: list[ReviewIssue] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class BossProblemReview:
    """五重审查汇总；仅 ``passed=True`` 可入库或返回前端。"""

    problem: BossProblem
    verdicts: list[ReviewVerdict]

    @property
    def passed(self) -> bool:
        return all(v.passed for v in self.verdicts)

    @property
    def issues(self) -> list[ReviewIssue]:
        merged: list[ReviewIssue] = []
        for v in self.verdicts:
            merged.extend(v.issues)
        return merged
