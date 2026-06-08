"""Boss 出题入口与五重审查链编排。

``generate_boss_problem`` 调用 factory 出题后串行审查；失败则重试，耗尽后可选 golden 兜底。
各阶段 ``ReviewVerdict`` 汇总于 ``BossProblemReview.verdicts`` 供诊断。
"""

from __future__ import annotations

import logging
from typing import Awaitable, Callable, Mapping, Sequence

from .difficulty_estimator import estimate_difficulty
from .grounding import verify_kc_grounding
from .plagiarism_checker import (
    DEFAULT_SIMILARITY_THRESHOLD,
    ExistingProblem,
    is_too_similar_to_existing,
)
from .schemas import BossProblem, BossProblemReview, ReviewVerdict, TestCase
from .self_tester import self_test_problem
from .struct_validator import validate_problem_struct

LOG = logging.getLogger(__name__)


def run_all_reviews(
    problem: BossProblem,
    *,
    existing_problems: Sequence[ExistingProblem] | None = None,
    kc_keywords: Mapping[str, list[str]] | None = None,
    plagiarism_threshold: float = DEFAULT_SIMILARITY_THRESHOLD,
    skip_self_test: bool = False,
) -> BossProblemReview:
    verdicts: list[ReviewVerdict] = []
    verdicts.append(validate_problem_struct(problem))
    if not verdicts[-1].passed:
        return BossProblemReview(problem=problem, verdicts=verdicts)
    if not skip_self_test:
        verdicts.append(self_test_problem(problem))
        if not verdicts[-1].passed:
            return BossProblemReview(problem=problem, verdicts=verdicts)
    verdicts.append(verify_kc_grounding(problem, kc_keywords=kc_keywords))
    if not verdicts[-1].passed:
        return BossProblemReview(problem=problem, verdicts=verdicts)
    verdicts.append(estimate_difficulty(problem))
    if not verdicts[-1].passed:
        return BossProblemReview(problem=problem, verdicts=verdicts)
    verdicts.append(
        is_too_similar_to_existing(
            problem,
            existing_problems or [],
            threshold=plagiarism_threshold,
        )
    )
    return BossProblemReview(problem=problem, verdicts=verdicts)


ProblemFactory = Callable[..., Awaitable[BossProblem]]


async def generate_boss_problem(
    chapter: int,
    stage: int,
    *,
    kc_code: str,
    difficulty: int,
    problem_factory: ProblemFactory,
    golden_fallback: BossProblem | None = None,
    existing_problems: Sequence[ExistingProblem] | None = None,
    kc_keywords: Mapping[str, list[str]] | None = None,
    max_retries: int = 3,
    skip_self_test: bool = False,
    preferred_kind: str | None = None,
    profile_signals: Mapping[str, object] | None = None,
) -> BossProblemReview:
    """编排五重审查链并返回 ``BossProblemReview``。

    Args:
        problem_factory: 异步出题函数；可选接收 ``preferred_kind``、``profile_signals``。
        golden_fallback: 重试耗尽后的兜底题，须能通过全部审查。
        preferred_kind: ArenaPage 关卡题型偏好。
        profile_signals: 学生画像信号，透传给 factory 以个性化出题。
    """

    temperature_base = 0.4
    last_review: BossProblemReview | None = None

    for attempt in range(1, max_retries + 1):
        temperature = temperature_base + 0.1 * (attempt - 1)
        try:
            # 兼容仅接受基础 kwargs 的旧 factory 签名
            try:
                problem = await problem_factory(
                    chapter=chapter,
                    stage=stage,
                    kc_code=kc_code,
                    difficulty=difficulty,
                    temperature=temperature,
                    preferred_kind=preferred_kind,
                    profile_signals=dict(profile_signals) if profile_signals else None,
                )
            except TypeError:
                problem = await problem_factory(
                    chapter=chapter,
                    stage=stage,
                    kc_code=kc_code,
                    difficulty=difficulty,
                    temperature=temperature,
                )
        except Exception as exc:  # noqa: BLE001
            LOG.warning("boss factory raised on attempt %s: %s", attempt, exc)
            continue
        review = run_all_reviews(
            problem,
            existing_problems=existing_problems,
            kc_keywords=kc_keywords,
            skip_self_test=skip_self_test,
        )
        last_review = review
        if review.passed:
            LOG.info(
                "boss problem accepted on attempt %s (chapter=%s, stage=%s, kc=%s)",
                attempt,
                chapter,
                stage,
                kc_code,
            )
            return review
        LOG.info(
            "boss problem rejected on attempt %s: %s",
            attempt,
            [issue.code for issue in review.issues if issue.severity == "error"],
        )

    if golden_fallback is not None:
        LOG.warning(
            "boss problem retries exhausted; falling back to golden (chapter=%s, stage=%s)",
            chapter,
            stage,
        )
        fallback_review = run_all_reviews(
            golden_fallback,
            existing_problems=existing_problems,
            kc_keywords=kc_keywords,
            skip_self_test=skip_self_test,
        )
        return fallback_review

    if last_review is None:
        # factory 全程异常时返回最小 failed review
        empty = BossProblem(
            problem_id="invalid",
            chapter=chapter,
            stage=stage,
            kc_code=kc_code,
            title="",
            statement="",
            reference_solution="",
            test_cases=[],
            difficulty=difficulty,
        )
        return BossProblemReview(problem=empty, verdicts=[validate_problem_struct(empty)])

    return last_review


def build_test_case(input_text: str, expected_output: str, *, is_sample: bool = False) -> TestCase:
    """构造 ``TestCase`` 的便捷入口。"""

    return TestCase(input=input_text, expected_output=expected_output, is_sample=is_sample)
