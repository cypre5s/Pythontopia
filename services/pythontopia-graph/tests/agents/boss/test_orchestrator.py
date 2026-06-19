"""orchestrator 编排链单元测试。"""

from __future__ import annotations

import asyncio

from pythontopia_graph.agents.boss import (
    BossProblem,
    BossProblemReview,
    generate_boss_problem,
    run_all_reviews,
)
from pythontopia_graph.agents.boss.orchestrator import build_test_case
from pythontopia_graph.agents.boss.plagiarism_checker import ExistingProblem


def _good_problem() -> BossProblem:
    return BossProblem(
        problem_id="ai-good-1",
        chapter=1,
        stage=1,
        kc_code="PY43-KC-001",
        title="加法",
        statement="读入两个整数 a b，调用 print 输出 a+b",
        reference_solution="a, b = map(int, input().split())\nprint(a + b)",
        test_cases=[
            build_test_case("1 2", "3", is_sample=True),
            build_test_case("10 20", "30"),
            build_test_case("100 200", "300"),
        ],
        difficulty=1,
    )


def test_run_all_reviews_happy_path() -> None:
    review = run_all_reviews(_good_problem())
    assert isinstance(review, BossProblemReview)
    assert review.passed, review.issues


def test_run_all_reviews_fails_struct_early() -> None:
    p = _good_problem()
    p.title = ""
    review = run_all_reviews(p)
    assert not review.passed
    # 结构校验失败则早退，后续审查不执行
    assert review.verdicts[0].review == "struct"


def test_run_all_reviews_skip_self_test_param() -> None:
    p = _good_problem()
    # reference 故意错误；skip_self_test 跳过沙箱
    p.reference_solution = "print('always 0')"
    review = run_all_reviews(p, skip_self_test=True)
    assert review.passed


def test_run_all_reviews_plagiarism_blocks() -> None:
    p = _good_problem()
    review = run_all_reviews(
        p,
        skip_self_test=True,
        existing_problems=[ExistingProblem("e1", p.statement)],
    )
    assert not review.passed
    assert any(v.review == "plagiarism" and not v.passed for v in review.verdicts)


def test_generate_boss_problem_accepts_factory_output() -> None:
    async def factory(**kwargs) -> BossProblem:
        return _good_problem()

    review = asyncio.run(
        generate_boss_problem(
            chapter=1,
            stage=1,
            kc_code="PY43-KC-001",
            difficulty=1,
            problem_factory=factory,
            max_retries=1,
        )
    )
    assert review.passed


def test_generate_boss_problem_retries_then_fallback() -> None:
    call_count = {"n": 0}

    async def bad_factory(**kwargs) -> BossProblem:
        call_count["n"] += 1
        p = _good_problem()
        p.title = ""  # title 为空导致 struct 永远失败
        return p

    golden = _good_problem()
    review = asyncio.run(
        generate_boss_problem(
            chapter=1,
            stage=1,
            kc_code="PY43-KC-001",
            difficulty=1,
            problem_factory=bad_factory,
            golden_fallback=golden,
            max_retries=3,
            skip_self_test=True,
        )
    )
    assert call_count["n"] == 3
    assert review.passed  # golden fallback 后仍通过
    assert review.problem.title == golden.title


def test_generate_boss_problem_factory_raises_uses_fallback() -> None:
    async def raising_factory(**kwargs) -> BossProblem:
        raise RuntimeError("LLM rate limited")

    golden = _good_problem()
    review = asyncio.run(
        generate_boss_problem(
            chapter=1,
            stage=1,
            kc_code="PY43-KC-001",
            difficulty=1,
            problem_factory=raising_factory,
            golden_fallback=golden,
            max_retries=2,
            skip_self_test=True,
        )
    )
    assert review.passed
    assert review.problem.problem_id == golden.problem_id


def test_to_payload_returns_frontend_dto_shape() -> None:
    p = _good_problem()
    payload = p.to_payload()
    expected_keys = {
        "problemId",
        "chapter",
        "stage",
        "difficulty",
        "kcCode",
        "statement",
        "hints",
        "sampleInput",
        "sampleOutput",
        "totalTestCases",
        "reviewed",
        "aiGenerated",
    }
    assert expected_keys.issubset(payload.keys())
    assert payload["totalTestCases"] == 3
    assert payload["aiGenerated"] is True


def test_generate_boss_problem_passes_temperature_bump_to_factory() -> None:
    seen_temperatures: list[float] = []

    async def recording_factory(**kwargs) -> BossProblem:
        seen_temperatures.append(kwargs["temperature"])
        p = _good_problem()
        p.title = ""  # 触发完整重试以记录 temperature
        return p

    asyncio.run(
        generate_boss_problem(
            chapter=1,
            stage=1,
            kc_code="PY43-KC-001",
            difficulty=1,
            problem_factory=recording_factory,
            max_retries=3,
            skip_self_test=True,
        )
    )
    # 每次重试 temperature +0.1
    assert len(seen_temperatures) == 3
    assert seen_temperatures[1] > seen_temperatures[0]
    assert seen_temperatures[2] > seen_temperatures[1]


def test_run_all_reviews_real_self_test_succeeds() -> None:
    p = _good_problem()
    # 真实 Python 解释器自测
    review = run_all_reviews(p)
    assert review.passed
    assert any(v.review == "self_test" and v.passed for v in review.verdicts)


def test_run_all_reviews_with_unsupported_chapter_syntax_fails() -> None:
    p = _good_problem()
    p.chapter = 1
    # 第 1 章禁止 for，reference 含 for 应 grounding 失败
    p.reference_solution = "for i in range(3):\n    print(i)"
    # 用例与 for 解法对齐
    p.test_cases = [
        build_test_case("", "0\n1\n2", is_sample=True),
        build_test_case("", "0\n1\n2"),
        build_test_case("", "0\n1\n2"),
    ]
    review = run_all_reviews(p, skip_self_test=False)
    # self_test 通过但 grounding 拒绝
    grounding = next(v for v in review.verdicts if v.review == "grounding")
    assert not grounding.passed
    assert not review.passed
