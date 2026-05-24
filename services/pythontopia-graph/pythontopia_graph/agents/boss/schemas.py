"""R-13 §5 Phase 5：Boss 出题数据模型。

不引入 pydantic 以避免与 langgraph 0.2 状态字段冲突；用普通 dataclass 即可。
"""

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
    """单条测试用例。Boss 出题至少 3 条；隐藏 vs 公开由 caller 控制。"""

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
    hints: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)

    def to_payload(self) -> dict[str, Any]:
        """转成 BossProblemDto 兼容的 dict（前端 / backend 共用）。

        额外把 ``testCases`` + ``referenceSolution`` 附在响应里给 backend 缓存
        （前端 BossProblemDto 不解析这俩字段，但后端 BossInternalProblem record
        会反序列化 → 用作 graph evaluate 输入）。
        """
        sample = next((tc for tc in self.test_cases if tc.is_sample), self.test_cases[0])
        return {
            "problemId": self.problem_id,
            "chapter": self.chapter,
            "stage": self.stage,
            "difficulty": self.difficulty,
            "kcCode": self.kc_code,
            "statement": self.statement,
            "hints": self.hints,
            "sampleInput": sample.input,
            "sampleOutput": sample.expected_output,
            "totalTestCases": len(self.test_cases),
            "reviewed": True,
            "aiGenerated": True,
            # 下面两个字段 backend 专用，前端 DTO 不展示：
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
    """5 重审查链汇总结果。``passed=True`` 才允许入库 / 返给前端。"""

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
