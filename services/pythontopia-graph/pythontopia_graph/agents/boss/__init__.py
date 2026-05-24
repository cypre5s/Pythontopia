"""R-13 §5 Phase 5：Boss 副本 AI 出题模块。

入口：``generate_boss_problem(chapter, stage, kc_code, difficulty, ...)``，编排 5 重审查。

子模块：
    struct_validator        题目对象结构合法性
    self_tester             reference_solution 在沙箱内必须通过自带 test_cases
    grounding               KC 关键词命中 + 超纲 syntax 拒绝
    difficulty_estimator    AI 自报 difficulty 与估算的差距
    plagiarism_checker      与课程包既有 39 题 + 玩家最近做题去重
"""

from .schemas import BossProblem, BossProblemReview, ReviewIssue, ReviewVerdict
from .struct_validator import validate_problem_struct
from .self_tester import self_test_problem
from .grounding import verify_kc_grounding
from .difficulty_estimator import estimate_difficulty
from .plagiarism_checker import is_too_similar_to_existing
from .orchestrator import generate_boss_problem, run_all_reviews

__all__ = [
    "BossProblem",
    "BossProblemReview",
    "ReviewIssue",
    "ReviewVerdict",
    "validate_problem_struct",
    "self_test_problem",
    "verify_kc_grounding",
    "estimate_difficulty",
    "is_too_similar_to_existing",
    "generate_boss_problem",
    "run_all_reviews",
]
