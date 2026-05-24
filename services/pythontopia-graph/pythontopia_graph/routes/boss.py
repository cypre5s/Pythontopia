"""R-13 §5 Phase 6 续：Boss 副本 HTTP 端点。

提供两条端点，对接 backend ``BossGraphClient``：

* ``POST /agents/boss/problem``：编排 LLM 出题 + 5 重审查，失败回落 golden；
* ``POST /agents/boss/evaluate``：跑玩家代码 vs test_cases，返回通过统计 + 首条失败 hint。

任何上游异常都翻成 5xx + 结构化 detail，让 backend 能区分「LLM 余额不足」/
「沙箱崩」/「JSON 解析失败」三类故障。
"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel, Field

from pythontopia_graph.agents.boss.evaluator import build_failure_hint, evaluate_student_code
from pythontopia_graph.agents.boss.golden_problems import get_golden_problem
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


class ProfileSignalsPayload(BaseModel):
    """R-13 §-1 续：学生画像信号（与前端 ProfileSignalsDto + 后端 ProfileSignalsPayload 形状一致）。

    用于让 PracticeAgent + ProfileAgent 在 LLM 出题阶段基于画像偏好走不同 prompt
    模板：weak KC 命中本章时优先出该 KC，successRate < 0.3 时拉低难度，
    mediaPriority 含 DIAGRAM 时让题面带 mermaid 等可视化片段。
    """

    weakKcCodes: list[str] | None = Field(default=None, description="弱项 KC 编码")
    pythonSelfRatedLevel: int | None = Field(default=None, ge=0, le=5)
    successRate: float | None = Field(default=None, ge=0, le=1)
    mediaPriority: list[str] | None = Field(default=None)
    resilience: float | None = Field(default=None, ge=0, le=1)
    questPreferredKind: str | None = Field(default=None, description="Arena 关卡题型")


class BossProblemRequest(BaseModel):
    userId: int | None = Field(default=None, description="发起请求的玩家 id，可空")
    chapter: int = Field(ge=1, le=7)
    stage: int = Field(ge=1, le=4)
    kcCode: str = Field(default="", description="目标 KC 代码；空则用 chapter 默认 KC")
    difficulty: int | None = Field(default=None, ge=1, le=5)
    # R-13 §-1 续：扩展 preferredKc / preferredKind / profileSignals 让 PracticeAgent
    # 基于学生画像偏好出题；目前仅记录到 LOG，等真 LLM 接入后由 prompt builder 消费。
    preferredKc: str | None = Field(default=None)
    preferredKind: str | None = Field(default=None)
    profileSignals: ProfileSignalsPayload | None = Field(default=None)


class TestCasePayload(BaseModel):
    input: str
    expectedOutput: str
    isSample: bool = False


class BossEvaluateRequest(BaseModel):
    problemId: str
    code: str
    testCases: list[TestCasePayload]
    timeoutSec: int = Field(default=5, ge=1, le=15)


class BossEvaluateResponse(BaseModel):
    problemId: str
    passed: bool
    passedTestCases: int
    totalTestCases: int
    failureFirstHint: str | None = None
    runtimeError: str | None = None
    failedIndices: list[int] = Field(default_factory=list)


def _resolve_kc(chapter: int, override: str) -> str:
    """章号 → 默认 KC（与前端 MAIN_QUEST_BOSSES 对齐）。"""

    if override and override.strip():
        return override.strip()
    return {
        1: "PY43-KC-001",
        2: "PY43-KC-006",
        3: "PY43-KC-016",
        4: "PY43-KC-026",
        5: "PY43-KC-036",
        6: "PY43-KC-046",
        7: "PY43-KC-056",
    }.get(chapter, "PY43-KC-001")


def _safe_provider() -> LlmProvider:
    """R-13 §-1 续：provider 不可用时回落到 DryRun，保证 problem 路由永不 500。

    优先级：
      1. PYT_LLM_PROVIDER 显式指定 → 走 provider_from_env；
      2. 未指定但已 load Alethicode .env 拿到 OPENAI_API_KEY → 优先 OpenAiCompat；
      3. 其他情况 → DryRun（确保路由可用）。
    """

    import os

    try:
        return provider_from_env()
    except RuntimeError as exc:
        # PYT_LLM_PROVIDER 未设置时尝试自动检测 Alethicode 的 OPENAI/LLM env
        has_key = bool(os.environ.get("OPENAI_API_KEY") or os.environ.get("LLM_API_KEY"))
        if has_key:
            try:
                return OpenAiCompatProvider()
            except RuntimeError as inner:
                LOG.warning("OpenAi-compat auto-detect failed: %s", inner)
        LOG.warning("LLM provider unavailable, fall back to DryRun: %s", exc)
        return DryRunProvider()


@router.post("/agents/boss/problem")
async def request_boss_problem(body: BossProblemRequest) -> dict[str, Any]:
    """返回 BossProblemDto（与前端 BossProblemDto.ts 对齐的扁平结构）。

    R-13 §-1 续：preferredKc 优先于 kcCode；profileSignals 写 LOG 供后续 LLM prompt builder 消费。
    """

    chapter = body.chapter
    stage = body.stage
    # preferredKc 优先级最高
    effective_kc_input = body.preferredKc if body.preferredKc else body.kcCode
    kc_code = _resolve_kc(chapter, effective_kc_input)
    difficulty = body.difficulty if body.difficulty is not None else min(5, stage)
    if body.profileSignals or body.preferredKind:
        LOG.info(
            "boss.problem profile-aware request: chapter=%d kc=%s kind=%s signals=%s",
            chapter,
            kc_code,
            body.preferredKind,
            body.profileSignals.model_dump(exclude_none=True) if body.profileSignals else None,
        )

    provider = _safe_provider()
    golden = get_golden_problem(chapter, stage)

    factory = build_llm_problem_factory(provider)
    # R-13 §-1：把 preferredKind + profileSignals 透传给 orchestrator，
    # 让 PracticeAgent 真正按画像出题（不是只 LOG 字段后丢掉）。
    profile_signals_dict = (
        body.profileSignals.model_dump(exclude_none=True) if body.profileSignals else None
    )
    try:
        review = await generate_boss_problem(
            chapter,
            stage,
            kc_code=kc_code,
            difficulty=difficulty,
            problem_factory=factory,
            golden_fallback=golden,
            max_retries=2,
            preferred_kind=body.preferredKind,
            profile_signals=profile_signals_dict,
        )
    except LlmProviderError as exc:
        LOG.warning("LLM provider error (%s); returning golden problem", exc.kind)
        review = await generate_boss_problem(
            chapter,
            stage,
            kc_code=kc_code,
            difficulty=difficulty,
            problem_factory=_async_raise_factory,
            golden_fallback=golden,
            max_retries=0,
            preferred_kind=body.preferredKind,
            profile_signals=profile_signals_dict,
        )

    problem = review.problem if review.passed else golden
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
    result = evaluate_student_code(body.code, cases, timeout_sec=body.timeoutSec)
    return BossEvaluateResponse(
        problemId=body.problemId,
        passed=result.passed,
        passedTestCases=result.passed_count,
        totalTestCases=result.total_count,
        failureFirstHint=build_failure_hint(result),
        runtimeError=result.runtime_error,
        failedIndices=result.failed_indices,
    )


async def _async_raise_factory(**_: Any) -> Any:  # pragma: no cover - exercised via golden fallback path
    raise RuntimeError("LLM disabled; using golden fallback")
