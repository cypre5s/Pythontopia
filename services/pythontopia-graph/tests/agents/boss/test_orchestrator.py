"""orchestrator 编排链单元测试。"""

from __future__ import annotations

import asyncio

import pytest

from pythontopia_graph.agents.boss import (
    BossProblem,
    BossProblemReview,
    generate_boss_problem,
    run_all_reviews,
)
from pythontopia_graph.agents.boss.orchestrator import build_test_case
from pythontopia_graph.agents.boss.plagiarism_checker import ExistingProblem
from pythontopia_graph.agents.boss.schemas import ReviewIssue, ReviewVerdict


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


def test_debug_problem_rejects_a_correct_snippet(fake_piston_endpoint) -> None:
    problem = _good_problem()
    problem.kind = "debug"
    problem.statement = (
        "修复下面的代码。错误代码：\n"
        "a, b = map(int, input().split())\nprint(a + b)\n\n"
        "输入格式：一行两个整数。"
    )

    review = run_all_reviews(problem)

    assert not review.passed
    assert any(issue.code == "debug_snippet_not_buggy" for issue in review.issues)


def test_debug_problem_accepts_a_snippet_that_fails_a_case(fake_piston_endpoint) -> None:
    problem = _good_problem()
    problem.kind = "debug"
    problem.statement = (
        "修复下面的代码。错误代码：\n" "print('always 0')\n\n" "输入格式：一行两个整数。"
    )

    review = run_all_reviews(problem)

    assert review.passed, review.issues


def test_grounding_rejects_a_question_without_the_authoritative_kc_name() -> None:
    review = run_all_reviews(
        _good_problem(),
        kc_keywords={"PY43-KC-001": ["中国计算机发展成就"]},
        skip_self_test=True,
    )

    assert not review.passed
    assert any(issue.code == "kc_keyword_miss" for issue in review.issues)


def test_grounding_accepts_a_concrete_term_from_kc_summary() -> None:
    problem = _good_problem()
    problem.statement += " 本题展示神威·太湖之光，并将输入数加一。"
    problem.reference_solution = "value = int(input()) + 1\nprint('神威·太湖之光', value)"
    problem.test_cases = [
        build_test_case("1", "神威·太湖之光 2", is_sample=True),
        build_test_case("2", "神威·太湖之光 3"),
        build_test_case("3", "神威·太湖之光 4"),
    ]

    review = run_all_reviews(
        problem,
        kc_keywords={"PY43-KC-001": ["中国计算机发展成就", "神威·太湖之光"]},
        skip_self_test=True,
    )

    assert review.passed, review.issues


def test_run_all_reviews_fails_struct_early() -> None:
    p = _good_problem()
    p.title = ""
    review = run_all_reviews(p)
    assert not review.passed
    # 结构校验失败则早退，后续审查不执行
    assert review.verdicts[0].review == "struct"


def test_oversized_test_case_list_is_rejected_before_sandbox(fake_piston_endpoint) -> None:
    problem = _good_problem()
    problem.test_cases += [problem.test_cases[0]] * 8

    review = run_all_reviews(problem)

    assert not review.passed
    assert any(issue.code == "too_many_test_cases" for issue in review.issues)
    assert fake_piston_endpoint == []


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


def test_generate_boss_problem_skips_factory_when_sandbox_is_unconfigured(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("PYT_PISTON_URL", raising=False)
    monkeypatch.delenv("PYT_PISTON_PYTHON_VERSION", raising=False)
    factory_calls = 0

    async def factory(**kwargs) -> BossProblem:
        nonlocal factory_calls
        factory_calls += 1
        return _good_problem()

    review = asyncio.run(
        generate_boss_problem(
            chapter=1,
            stage=1,
            kc_code="PY43-KC-001",
            difficulty=1,
            problem_factory=factory,
            max_retries=6,
        )
    )

    assert factory_calls == 0
    assert not review.passed
    assert any(issue.code == "sandbox_unavailable" for issue in review.issues)


def test_generate_boss_problem_stops_after_sandbox_becomes_unavailable(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    import pythontopia_graph.agents.boss.orchestrator as orchestrator

    factory_calls = 0

    async def factory(**kwargs) -> BossProblem:
        nonlocal factory_calls
        factory_calls += 1
        return _good_problem()

    def unavailable_review(problem: BossProblem, **kwargs) -> BossProblemReview:
        return BossProblemReview(
            problem=problem,
            verdicts=[
                ReviewVerdict(
                    review="self_test",
                    passed=False,
                    issues=[
                        ReviewIssue(
                            review="self_test",
                            severity="error",
                            code="sandbox_unavailable",
                            message="isolated Python executor is unavailable",
                        )
                    ],
                )
            ],
        )

    monkeypatch.setattr(orchestrator, "run_all_reviews", unavailable_review)
    review = asyncio.run(
        generate_boss_problem(
            chapter=1,
            stage=1,
            kc_code="PY43-KC-001",
            difficulty=1,
            problem_factory=factory,
            max_retries=6,
        )
    )

    assert factory_calls == 1
    assert not review.passed
    assert any(issue.code == "sandbox_unavailable" for issue in review.issues)


def test_golden_fallback_is_not_rejected_as_a_duplicate_of_itself() -> None:
    fallback = _good_problem()
    fallback.problem_id = "golden-c1-s1"

    async def reject_candidate(**_kwargs) -> BossProblem:
        invalid = _good_problem()
        invalid.title = ""
        return invalid

    review = asyncio.run(
        generate_boss_problem(
            chapter=1,
            stage=1,
            kc_code="PY43-KC-001",
            difficulty=1,
            problem_factory=reject_candidate,
            golden_fallback=fallback,
            existing_problems=[ExistingProblem(fallback.problem_id, fallback.statement)],
            max_retries=1,
        )
    )

    assert review.problem.problem_id == fallback.problem_id
    assert review.passed, review.issues


def test_generate_boss_problem_retries_when_calibration_misses_target_difficulty() -> None:
    feedback_by_attempt: list[str | None] = []
    candidate = BossProblem(
        problem_id="ai-calibrated-harder-than-target",
        chapter=2,
        stage=1,
        kc_code="PY43-KC-012",
        title="加一",
        statement=(
            "读取一个整数 n。先将输入文本转换为整数，再把 n 加 1，"
            "最后把结果转换成字符串并输出。请保证输出仅包含计算结果。"
        ),
        reference_solution=(
            "value = int(input())\n"
            "next_value = value + 1\n"
            "doubled = next_value * 2\n"
            "result = ((doubled + value) - value) // 2\n"
            "print(result)"
        ),
        test_cases=[
            build_test_case("1", "2", is_sample=True),
            build_test_case("9", "10"),
            build_test_case("0", "1"),
        ],
        difficulty=1,
    )
    simpler_candidate = BossProblem(
        problem_id="ai-matches-target-difficulty",
        chapter=2,
        stage=1,
        kc_code="PY43-KC-012",
        title="名字后加感叹号",
        statement="读取名字，在末尾追加感叹号后输出。",
        reference_solution="print(input() + '!')",
        test_cases=[
            build_test_case("小明", "小明!", is_sample=True),
            build_test_case("小红", "小红!"),
            build_test_case("小李", "小李!"),
        ],
        difficulty=1,
    )

    async def factory(**kwargs) -> BossProblem:
        feedback_by_attempt.append(kwargs.get("review_feedback"))
        return candidate if len(feedback_by_attempt) == 1 else simpler_candidate

    review = asyncio.run(
        generate_boss_problem(
            chapter=2,
            stage=1,
            kc_code="PY43-KC-012",
            difficulty=1,
            problem_factory=factory,
            max_retries=2,
            skip_self_test=True,
        )
    )

    assert review.passed
    assert len(feedback_by_attempt) == 2
    assert "difficulty_target_mismatch" in (feedback_by_attempt[1] or "")
    assert "requested 1, calibrated 3" in (feedback_by_attempt[1] or "")


def test_generate_boss_problem_passes_review_feedback_to_retry() -> None:
    feedback_by_attempt: list[str | None] = []
    constant_output = BossProblem(
        problem_id="ai-constant-output",
        chapter=1,
        stage=1,
        kc_code="PY43-KC-001",
        title="输出课程事实",
        statement="输出神威·太湖之光。",
        reference_solution="print('神威·太湖之光')",
        test_cases=[
            build_test_case("", "神威·太湖之光", is_sample=True),
            build_test_case("", "神威·太湖之光"),
            build_test_case("", "神威·太湖之光"),
        ],
        difficulty=1,
    )
    grounded_problem = _good_problem()
    grounded_problem.statement += " 神威·太湖之光的编号加一。"
    grounded_problem.reference_solution = "value = int(input()) + 1\nprint('神威·太湖之光', value)"
    grounded_problem.test_cases = [
        build_test_case("1", "神威·太湖之光 2", is_sample=True),
        build_test_case("2", "神威·太湖之光 3"),
        build_test_case("3", "神威·太湖之光 4"),
    ]

    async def factory(**kwargs) -> BossProblem:
        feedback_by_attempt.append(kwargs.get("review_feedback"))
        return constant_output if len(feedback_by_attempt) == 1 else grounded_problem

    review = asyncio.run(
        generate_boss_problem(
            chapter=1,
            stage=1,
            kc_code="PY43-KC-001",
            difficulty=1,
            problem_factory=factory,
            kc_keywords={"PY43-KC-001": ["神威·太湖之光"]},
            max_retries=2,
            skip_self_test=True,
        )
    )

    assert review.passed
    assert feedback_by_attempt[0] is None
    assert "trivial_constant_output" in (feedback_by_attempt[1] or "")
    assert "input processing or computation" in (feedback_by_attempt[1] or "")


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


def test_generate_boss_problem_factory_raises_uses_fallback(caplog) -> None:
    async def raising_factory(**kwargs) -> BossProblem:
        raise RuntimeError("boss-content-secret-sentinel")

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
    assert "boss-content-secret-sentinel" not in caplog.text
    assert "RuntimeError" in caplog.text


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


def test_generate_boss_problem_passes_authoritative_kc_context_to_factory() -> None:
    seen_context: dict[str, str | None] = {}

    async def recording_factory(**kwargs) -> BossProblem:
        seen_context["kc_name"] = kwargs.get("kc_name")
        seen_context["kc_summary"] = kwargs.get("kc_summary")
        return _good_problem()

    asyncio.run(
        generate_boss_problem(
            chapter=1,
            stage=1,
            kc_code="PY43-KC-001",
            difficulty=1,
            problem_factory=recording_factory,
            kc_name="中国计算机发展成就",
            kc_summary="了解龙芯 CPU 等发展成果。",
            max_retries=1,
        )
    )

    assert seen_context == {
        "kc_name": "中国计算机发展成就",
        "kc_summary": "了解龙芯 CPU 等发展成果。",
    }


def test_run_all_reviews_fixed_self_test_response_succeeds() -> None:
    p = _good_problem()
    # The unit fixture returns fixed Piston responses without executing the source.
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
