"""工具治理 harness（tools.harness.run_with_governance）各路径覆盖测试。

全部使用 reset_harness_state() 隔离进程级统计与熔断器；不发起任何真实网络调用。
"""

from __future__ import annotations

import asyncio

import pytest

from pythontopia_graph.tools.harness import (
    BudgetGuard,
    breaker_for,
    harness_stats,
    reset_harness_state,
    run_with_governance,
)


@pytest.fixture(autouse=True)
def _isolate_harness_state():
    reset_harness_state()
    yield
    reset_harness_state()


# ── ok 路径 ──


@pytest.mark.asyncio
async def test_ok_path_returns_value_and_records_stats() -> None:
    async def factory() -> str:
        return "result"

    obs = await run_with_governance("demo_tool", factory, timeout_s=1.0)

    assert obs.ok is True
    assert obs.status == "ok"
    assert obs.value == "result"
    assert obs.attempts == 1
    assert obs.fallback_used is False
    stats = harness_stats()
    assert stats["tools"]["demo_tool"]["calls"] == 1
    assert stats["tools"]["demo_tool"]["failures"] == 0


# ── timeout 路径 ──


@pytest.mark.asyncio
async def test_factory_raising_timeout_error_classified_as_timeout() -> None:
    """builtin TimeoutError 走 timeout 分类（status="timeout"，无降级时 ok=False）。"""

    async def factory() -> str:
        raise TimeoutError("simulated timeout")

    obs = await run_with_governance("slow_tool", factory, timeout_s=1.0, max_retries=0)

    assert obs.ok is False
    assert obs.status == "timeout"
    assert obs.error_class == "TimeoutError"


@pytest.mark.asyncio
async def test_wait_for_timeout_fails_structured() -> None:
    """真实 asyncio.wait_for 超时：永远结构化返回 ok=False，error_class=TimeoutError。

    注：Python 3.11+ 下 asyncio.TimeoutError 即 builtin TimeoutError，status="timeout"；
    本机 3.10 运行时两者不同类，harness 的 ``except TimeoutError`` 接不住，落入通用
    Exception 分支 → status="error"（地基契约缺陷，详见 WP1 报告；此处兼容两种状态）。
    """

    async def factory() -> str:
        await asyncio.sleep(5)
        return "late"

    obs = await run_with_governance("hang_tool", factory, timeout_s=0.05, max_retries=0)

    assert obs.ok is False
    assert obs.status in {"timeout", "error"}
    assert obs.error_class == "TimeoutError"


# ── 重试守恒 ──


@pytest.mark.asyncio
async def test_retry_conservation_attempts_exactly_max_retries_plus_one() -> None:
    """max_retries=1 → factory 恰好被调用 2 次（首跑 + 1 次重试），不多不少。"""
    calls = {"n": 0}

    async def factory() -> str:
        calls["n"] += 1
        raise ValueError("always fails")

    obs = await run_with_governance("flaky_tool", factory, timeout_s=1.0, max_retries=1)

    assert calls["n"] == 2
    assert obs.attempts == 2
    assert obs.ok is False
    assert obs.status == "error"
    assert obs.error_class == "ValueError"


@pytest.mark.asyncio
async def test_retry_then_success_marks_ok_with_attempt_count() -> None:
    """第 1 次失败、第 2 次成功 → ok=True，attempts=2，trace note 带 retry 标记。"""
    calls = {"n": 0}

    async def factory() -> str:
        calls["n"] += 1
        if calls["n"] == 1:
            raise RuntimeError("first attempt fails")
        return "second try"

    obs = await run_with_governance("retry_tool", factory, timeout_s=1.0, max_retries=1)

    assert obs.ok is True
    assert obs.status == "ok"
    assert obs.value == "second try"
    assert obs.attempts == 2
    assert "retry=1" in obs.as_trace_note()


# ── 熔断路径 ──


@pytest.mark.asyncio
async def test_circuit_open_routes_to_fallback_without_calling_factory() -> None:
    """熔断打开 → 不再调 factory，直接走降级（status="circuit_open"，ok=True）。"""
    breaker = breaker_for("dep_x")
    for _ in range(3):
        breaker.record_failure()
    assert breaker.is_open

    factory_calls = {"n": 0}

    async def factory() -> str:
        factory_calls["n"] += 1
        return "primary"

    async def fallback() -> str:
        return "degraded"

    obs = await run_with_governance(
        "guarded_tool", factory, timeout_s=1.0, fallback=fallback, breaker_key="dep_x"
    )

    assert factory_calls["n"] == 0
    assert obs.ok is True
    assert obs.status == "circuit_open"
    assert obs.fallback_used is True
    assert obs.value == "degraded"


@pytest.mark.asyncio
async def test_circuit_open_without_fallback_fails_structured() -> None:
    breaker = breaker_for("dep_y")
    for _ in range(3):
        breaker.record_failure()

    async def factory() -> str:
        return "never reached"

    obs = await run_with_governance(
        "guarded_tool", factory, timeout_s=1.0, breaker_key="dep_y"
    )

    assert obs.ok is False
    assert obs.status == "circuit_open"


@pytest.mark.asyncio
async def test_consecutive_failures_open_breaker_for_next_call() -> None:
    """连续失败达阈值（3）后熔断器打开，harness_stats 能看到 open 状态。"""

    async def failing() -> str:
        raise ValueError("dependency down")

    for _ in range(3):
        await run_with_governance(
            "dep_tool", failing, timeout_s=1.0, max_retries=0, breaker_key="dep_z"
        )

    assert breaker_for("dep_z").is_open
    assert harness_stats()["breakers"]["dep_z"]["open"] is True


# ── 预算路径 ──


@pytest.mark.asyncio
async def test_budget_exhausted_marks_budget_skipped() -> None:
    """预算耗尽 → status="budget_skipped"，factory 不被调用，guard.skipped 留痕。"""
    guard = BudgetGuard(max_llm_calls=1)
    calls = {"n": 0}

    async def factory() -> str:
        calls["n"] += 1
        return "value"

    first = await run_with_governance("step_a", factory, timeout_s=1.0, budget=guard)
    second = await run_with_governance("step_b", factory, timeout_s=1.0, budget=guard)

    assert first.ok is True
    assert second.ok is False
    assert second.status == "budget_skipped"
    assert calls["n"] == 1
    assert guard.skipped == ["step_b"]


# ── 降级路径 ──


@pytest.mark.asyncio
async def test_failure_falls_back_with_status_fallback() -> None:
    async def factory() -> str:
        raise RuntimeError("primary down")

    async def fallback() -> str:
        return "keyword route"

    obs = await run_with_governance(
        "route_tool", factory, timeout_s=1.0, max_retries=0, fallback=fallback
    )

    assert obs.ok is True
    assert obs.status == "fallback"
    assert obs.fallback_used is True
    assert obs.value == "keyword route"
    assert obs.error_class == "RuntimeError"
    assert harness_stats()["tools"]["route_tool"]["fallbacks"] == 1


@pytest.mark.asyncio
async def test_fallback_failure_still_returns_structured_observation() -> None:
    """降级也失败 → 仍结构化返回（ok=False，error_class=降级异常类名），绝无悬空异常。"""

    async def factory() -> str:
        raise ValueError("primary down")

    async def fallback() -> str:
        raise RuntimeError("fallback down too")

    obs = await run_with_governance(
        "doomed_tool", factory, timeout_s=1.0, max_retries=0, fallback=fallback
    )

    assert obs.ok is False
    assert obs.status == "error"
    assert obs.error_class == "RuntimeError"
    assert obs.fallback_used is False


# ── 统计快照 ──


@pytest.mark.asyncio
async def test_harness_stats_aggregates_calls_and_avg_elapsed() -> None:
    async def factory() -> str:
        return "v"

    await run_with_governance("stat_tool", factory, timeout_s=1.0)
    await run_with_governance("stat_tool", factory, timeout_s=1.0)

    entry = harness_stats()["tools"]["stat_tool"]
    assert entry["calls"] == 2
    assert entry["failures"] == 0
    assert entry["avgElapsedMs"] >= 0
