"""R-13 §5 Phase 5：5 重审查链编排 + Boss 出题入口。

`generate_boss_problem(...)` 流程：
    1. 调 `problem_factory` 生成候选题（默认 fallback 到 golden 题模板）；
    2. 串行跑 struct / self_test / grounding / difficulty / plagiarism；
    3. 任一审查失败 → 重试（最多 3 次，难度向估算靠拢）；
    4. 仍失败 → 用 golden fallback 返回 + 全审查通过；调用方据此决定是否打 reject 标。

任何阶段失败的 review 都会出现在 ``BossProblemReview.verdicts`` 里，便于运维诊断。
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


# 类型别名：异步 factory（接 chapter / stage / kc / difficulty / temperature 返回 BossProblem）
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
    """编排 5 重审查链。

    Args:
        problem_factory: 异步出题函数，签名 ``(chapter, stage, kc_code, difficulty,
            temperature, [preferred_kind], [profile_signals]) -> BossProblem``。
        golden_fallback: 重试用完仍失败时使用的兜底题（必须保证 5 重审查通过；
            如未提供，最后返回最后一次的 review，passed=False 由调用方处理）。
        preferred_kind: 关卡题型偏好（ArenaPage 关卡 kind）。
        profile_signals: 学生画像信号 dict。R-13 §-1 新增。
    """

    temperature_base = 0.4
    last_review: BossProblemReview | None = None

    for attempt in range(1, max_retries + 1):
        temperature = temperature_base + 0.1 * (attempt - 1)
        try:
            # R-13 §-1：把 preferred_kind / profile_signals 透传给 factory。
            # 旧 factory 签名只有 5 个 kwargs 时，TypeError 兜底丢掉新参数。
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
        # 极端：factory 全程 raise，返回一个最小化的 failed review
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
    """便利构造器，避免外部代码 import TestCase 时还要写 dataclass kwargs。"""

    return TestCase(input=input_text, expected_output=expected_output, is_sample=is_sample)
