"""Boss 副本 HTTP 端点。

对接 backend ``BossGraphClient``：

* ``POST /agents/boss/problem``：LLM 出题 + 5 重审查，失败回落 golden；
* ``POST /agents/boss/evaluate``：玩家代码 vs test_cases，返回通过统计 + 首条失败 hint。

上游异常 → 5xx + 结构化 detail，供 backend 区分 LLM 余额不足 / 沙箱崩溃 / JSON 解析失败。
"""

from __future__ import annotations

import logging
from typing import Any, Literal

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel, Field
from pythontopia_graph.schemas.profile import GoalKind, Major

from pythontopia_graph.agents.boss.evaluator import build_failure_hint, evaluate_boss_attempt
from pythontopia_graph.agents.boss.expedition_selector import select_frontier
from pythontopia_graph.agents.boss.golden_problems import get_golden_problem
from pythontopia_graph.agents.boss.grounding import extract_kc_grounding_keywords
from pythontopia_graph.agents.boss.kc_map import chapter_default_kc
from pythontopia_graph.agents.boss.llm_factory import build_llm_problem_factory
from pythontopia_graph.agents.boss.orchestrator import generate_boss_problem
from pythontopia_graph.agents.boss.schemas import TestCase
from pythontopia_graph.llm.provider import (
    DryRunProvider,
    LlmProvider,
    LlmProviderError,
    provider_from_env,
)

LOG = logging.getLogger(__name__)

router = APIRouter()
BOSS_PROBLEM_MAX_RETRIES = 6
BossProblemKind = Literal[
    "pdf_question",
    "fill_blank",
    "debug",
    "short_answer",
    "closed_loop",
    "dual_track",
    "multi_round",
    "teach_back",
    "bug_mine",
]


class ProfileSignalsPayload(BaseModel):
    """学生画像信号（与前端 ProfileSignalsDto、后端 ProfileSignalsPayload 字段对齐）。

    供 PracticeAgent + ProfileAgent 在 LLM 出题阶段选择 prompt 模板：weak KC 命中本章时优先该 KC，
    目标 KC 参与前沿排序；其他画像只作为题目表达偏好，不替代正式判题能力数据。
    """

    weakKcCodes: list[str] | None = Field(default=None, description="弱项 KC 编码")
    pythonSelfRatedLevel: int | None = Field(default=None, ge=0, le=5)
    successRate: float | None = Field(default=None, ge=0, le=1)
    mediaPriority: list[str] | None = Field(default=None)
    resilience: float | None = Field(default=None, ge=0, le=1)
    questPreferredKind: BossProblemKind | None = Field(default=None, description="Arena 关卡题型")
    major: Major | None = None
    learningGoalKind: GoalKind | None = None
    targetKcCodes: list[str] | None = Field(default=None, max_length=6)
    fieldDependent: float | None = Field(default=None, ge=0, le=1)
    sequentialVsHolistic: float | None = Field(default=None, ge=-1, le=1)
    sessionMinutesPerDay: int | None = Field(default=None, ge=5, le=240)


class BossProblemRequest(BaseModel):
    userId: int | None = Field(default=None, description="发起请求的玩家 id，可空")
    chapter: int = Field(ge=1, le=7)
    stage: int = Field(ge=1, le=4)
    kcCode: str = Field(default="", description="目标 KC 代码；空则用 chapter 默认 KC")
    kcName: str = Field(min_length=1, max_length=128, description="课程包中的权威 KC 名称")
    kcSummary: str = Field(default="", max_length=4000, description="课程包中的权威 KC 内容摘要")
    difficulty: int | None = Field(default=None, ge=1, le=5)
    preferredKc: str | None = Field(default=None)
    preferredKind: BossProblemKind | None = Field(default=None)
    profileSignals: ProfileSignalsPayload | None = Field(default=None)


class TestCasePayload(BaseModel):
    input: str = Field(max_length=8192)
    expectedOutput: str = Field(max_length=8192)
    isSample: bool = False


class BossEvaluateRequest(BaseModel):
    problemId: str = Field(max_length=256)
    code: str = Field(max_length=8192)
    testCases: list[TestCasePayload] = Field(max_length=10)
    timeoutSec: int = Field(default=5, ge=1, le=15)
    kind: str | None = Field(
        default=None,
        description=(
            "题型：文本类（short_answer / teach_back / pdf_question）走接地评分，"
            "其余（fill_blank / debug / closed_loop / dual_track / bug_mine / multi_round / 缺省）走 stdout 沙箱判题"
        ),
    )
    referenceAnswer: str | None = Field(
        default=None, description="文本题参考答案/要点（接地评分证据，可空）"
    )
    kcCode: str | None = Field(
        default=None, description="题目 KC 编码（文本题防幻觉校验用，可空）"
    )


class BossEvaluateResponse(BaseModel):
    problemId: str
    passed: bool
    passedTestCases: int
    totalTestCases: int
    failureFirstHint: str | None = None
    runtimeError: str | None = None
    failedIndices: list[int] = Field(default_factory=list)


def _resolve_kc(chapter: int, override: str) -> str:
    """章号 → 默认 KC。

    覆盖值（``preferredKc`` / ``kcCode``）优先；否则用课程包派生的单一事实源
    ``kc_map.chapter_default_kc``（见 ``agents/boss/kc_map.py``）取该章首个真实 KC。

    历史上这里硬编码的 ``{2: KC-006, 3: KC-016, 4: KC-026, ...}`` 用了错误的「每章 10
    个 KC」假设，与课程包真实分章（1-7 / 8-16 / 17-30 / 31-38 / 39-47 / 48-53 / 54-65）不符，
    解析出的 KC 会落到相邻错误章节。现改为从课程包映射取值，与前端 ``useEndlessRun`` 的
    ``kcRange`` 一致；章 1 仍解析为 ``PY43-KC-001``，保持向后兼容。
    """

    if override and override.strip():
        return override.strip()
    return chapter_default_kc(chapter)


def _safe_provider() -> LlmProvider:
    """缺少 DeepSeek 配置时 fail fast；上游请求失败走明确的 golden 兜底策略。"""
    try:
        provider = provider_from_env()
    except RuntimeError as exc:
        LOG.error("DeepSeek provider configuration failed: %s", type(exc).__name__)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={
                "code": "LLM_PROVIDER_UNAVAILABLE",
                "message": "DeepSeek 尚未配置，题目生成暂不可用",
            },
        ) from exc
    if isinstance(provider, DryRunProvider):
        LOG.error("DeepSeek provider configuration resolved to DryRunProvider")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={
                "code": "LLM_PROVIDER_UNAVAILABLE",
                "message": "DeepSeek 尚未配置，题目生成暂不可用",
            },
        )
    return provider


@router.post("/agents/boss/problem")
async def request_boss_problem(body: BossProblemRequest) -> dict[str, Any]:
    """返回 BossProblemDto 扁平结构（与前端 BossProblemDto.ts 对齐）。preferredKc 优先于 kcCode。"""

    chapter = body.chapter
    stage = body.stage
    golden = get_golden_problem(chapter, stage)
    effective_kc_input = body.preferredKc if body.preferredKc else body.kcCode
    kc_code = (
        _resolve_kc(chapter, effective_kc_input)
        if effective_kc_input.strip()
        else golden.kc_code
    )
    difficulty = body.difficulty if body.difficulty is not None else min(5, stage)
    if body.profileSignals or body.preferredKind:
        LOG.info(
            "boss.problem profile-aware request: chapter=%d kc=%s kind=%s profile_signals_present=%s",
            chapter,
            kc_code,
            body.preferredKind,
            body.profileSignals is not None,
        )

    provider = _safe_provider()
    factory = build_llm_problem_factory(provider)
    kc_keywords = {kc_code: extract_kc_grounding_keywords(body.kcName, body.kcSummary)}
    # preferredKind + profileSignals 透传 orchestrator，供 PracticeAgent 按画像出题。
    profile_signals_dict = (
        body.profileSignals.model_dump(exclude_none=True) if body.profileSignals else None
    )
    try:
        review = await generate_boss_problem(
            chapter,
            stage,
            kc_code=kc_code,
            kc_name=body.kcName,
            kc_summary=body.kcSummary,
            difficulty=difficulty,
            problem_factory=factory,
            golden_fallback=golden,
            kc_keywords=kc_keywords,
            max_retries=BOSS_PROBLEM_MAX_RETRIES,
            preferred_kind=body.preferredKind,
            profile_signals=profile_signals_dict,
        )
    except LlmProviderError as exc:
        LOG.warning("LLM provider error (%s); returning golden problem", exc.kind)
        review = await generate_boss_problem(
            chapter,
            stage,
            kc_code=kc_code,
            kc_name=body.kcName,
            kc_summary=body.kcSummary,
            difficulty=difficulty,
            problem_factory=_async_raise_factory,
            golden_fallback=golden,
            kc_keywords=kc_keywords,
            max_retries=0,
            preferred_kind=body.preferredKind,
            profile_signals=profile_signals_dict,
        )

    if not review.passed:
        issue_codes = [issue.code for issue in review.issues if issue.severity == "error"]
        LOG.warning(
            "boss problem final review failed chapter=%s stage=%s kc=%s issues=%s",
            chapter,
            stage,
            kc_code,
            issue_codes,
        )
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={
                "code": "BOSS_PROBLEM_GENERATION_UNAVAILABLE",
                "message": "当前知识点没有通过审查的编程题，请稍后重试",
            },
        )

    problem = review.problem
    if problem.kc_code != kc_code:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={
                "code": "BOSS_PROBLEM_KC_MISMATCH",
                "message": "当前没有与目标知识点匹配的已审题目，请稍后重试",
            },
        )
    payload = problem.to_payload()
    payload["reviewed"] = review.passed
    payload["aiGenerated"] = problem.metadata.get("source") == "llm"
    return payload


@router.post("/agents/boss/evaluate", response_model=BossEvaluateResponse)
def evaluate_attempt(body: BossEvaluateRequest) -> BossEvaluateResponse:
    if not body.testCases:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"code": "NO_TEST_CASES", "message": "testCases must be non-empty"},
        )
    cases = [
        TestCase(
            input=tc.input,
            expected_output=tc.expectedOutput,
            is_sample=tc.isSample,
        )
        for tc in body.testCases
    ]
    # kind 决定判题路径：文本题接地评分 vs 代码题 stdout 沙箱（见 agents/boss/kinds.py）。
    # 文本题绝不把自然语言当 Python 跑，且 fail fast（确定性评分，不静默降级为闲聊）。
    result = evaluate_boss_attempt(
        kind=body.kind,
        submission=body.code,
        test_cases=cases,
        timeout_sec=body.timeoutSec,
        reference_answer=body.referenceAnswer,
        kc_code=body.kcCode,
    )
    return BossEvaluateResponse(
        problemId=body.problemId,
        passed=result.passed,
        passedTestCases=result.passed_count,
        totalTestCases=result.total_count,
        failureFirstHint=build_failure_hint(result),
        runtimeError=result.runtime_error,
        failedIndices=result.failed_indices,
    )


class MasteryEntryPayload(BaseModel):
    kcCode: str
    masteryScore: float = Field(ge=0, le=1)
    confidence: float | None = Field(default=None, ge=0, le=1)


class FrontierSelectRequest(BaseModel):
    """无尽远征「下一题选择器」请求：掌握度 + 画像 + 正式练习时间 → 前沿候选。"""

    mastery: list[MasteryEntryPayload] = Field(default_factory=list)
    profileSignals: ProfileSignalsPayload | None = None
    lastPracticed: dict[str, str] = Field(
        default_factory=dict,
        description="每个 KC 的后端权威最近练习时间（ISO-8601），用于间隔重复",
    )
    focusChapter: int | None = Field(default=None, ge=1, le=7)
    exclude: list[str] = Field(default_factory=list)
    count: int = Field(default=1, ge=1, le=40)


class FrontierPickPayload(BaseModel):
    kcCode: str
    chapter: int
    difficulty: int
    reason: str


class FrontierSelectResponse(BaseModel):
    picks: list[FrontierPickPayload]


@router.post("/agents/boss/select-frontier", response_model=FrontierSelectResponse)
def select_frontier_endpoint(body: FrontierSelectRequest) -> FrontierSelectResponse:
    """知识前沿选择器（下沉自前端 useKcFrontier）：权威排序 + 间隔重复，供无尽远征生成地图。"""

    mastery = {e.kcCode: e.masteryScore for e in body.mastery}
    confidence = {e.kcCode: e.confidence for e in body.mastery if e.confidence is not None}
    signals = (
        body.profileSignals.model_dump(exclude_none=True) if body.profileSignals else None
    )
    picks = select_frontier(
        mastery=mastery,
        confidence=confidence,
        profile_signals=signals,
        last_practiced=body.lastPracticed,
        focus_chapter=body.focusChapter,
        exclude=body.exclude,
        count=body.count,
    )
    return FrontierSelectResponse(
        picks=[
            FrontierPickPayload(
                kcCode=p.kc_code,
                chapter=p.chapter,
                difficulty=p.difficulty,
                reason=p.reason,
            )
            for p in picks
        ]
    )


async def _async_raise_factory(**_: Any) -> Any:  # pragma: no cover - exercised via golden fallback path
    raise RuntimeError("LLM disabled; using golden fallback")
