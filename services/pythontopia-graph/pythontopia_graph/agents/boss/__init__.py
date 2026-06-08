"""Boss 副本 AI 出题模块。

入口 ``generate_boss_problem`` 编排结构校验、自测、KC 接地、难度校准与查重五道审查。
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
