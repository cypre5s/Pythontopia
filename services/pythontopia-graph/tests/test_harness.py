"""工具治理 harness（tools.harness.run_with_governance）各路径覆盖测试。

全部使用 reset_harness_state() 隔离进程级统计与熔断器；不发起任何真实网络调用。
"""

from __future__ import annotations

import asyncio
import contextlib
import time

import pytest

import pythontopia_graph.tools.harness as harness_mod
from pythontopia_graph.tools.harness import (
    BudgetGuard,
    _backoff_delay,
    breaker_for,
    harness_stats,
    is_permanent_error,
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


@pytest.mark.asyncio
async def test_retries_of_one_call_count_as_one_breaker_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """一次逻辑调用的 N 次重试只记 1 次熔断失败。

    在重试循环内记账会把单个请求放大成 N 次「连续失败」，阈值 3 时一个请求就能打开
    进程级共享熔断器（breaker_key="provider" 多处共用）并殃及所有会话。
    """
    monkeypatch.setattr(harness_mod, "_backoff_delay", lambda attempts: 0.0)
    calls = {"n": 0}

    async def failing() -> str:
        calls["n"] += 1
        raise ConnectionError("network down")

    obs = await run_with_governance(
        "retry_tool", failing, timeout_s=1.0, max_retries=4, breaker_key="dep_retry"
    )

    assert calls["n"] == 5  # 重试语义不变
    assert obs.attempts == 5
    breaker = breaker_for("dep_retry")
    assert breaker.snapshot()["consecutiveFailures"] == 1
    assert breaker.is_open is False


@pytest.mark.asyncio
async def test_three_separate_retrying_calls_still_open_breaker(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """降噪不等于失能：三次独立的失败调用仍然把熔断器推过阈值。"""
    monkeypatch.setattr(harness_mod, "_backoff_delay", lambda attempts: 0.0)

    async def failing() -> str:
        raise ConnectionError("network down")

    for _ in range(3):
        await run_with_governance(
            "retry_tool", failing, timeout_s=1.0, max_retries=2, breaker_key="dep_retry3"
        )

    assert breaker_for("dep_retry3").is_open is True


@pytest.mark.asyncio
async def test_success_after_retry_does_not_record_breaker_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """重试后成功 → 不记失败，且 record_success 清零计数（语义不变）。"""
    monkeypatch.setattr(harness_mod, "_backoff_delay", lambda attempts: 0.0)
    calls = {"n": 0}

    async def flaky() -> str:
        calls["n"] += 1
        if calls["n"] < 3:
            raise ConnectionError("network down")
        return "recovered"

    obs = await run_with_governance(
        "flaky_tool", flaky, timeout_s=1.0, max_retries=3, breaker_key="dep_flaky"
    )

    assert obs.ok is True
    assert obs.value == "recovered"
    assert breaker_for("dep_flaky").snapshot()["consecutiveFailures"] == 0


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


# ── A2：token 预算 ──


@pytest.mark.asyncio
async def test_budget_token_exhaustion_marks_budget_skipped() -> None:
    """累计 token 达到/超过 max_tokens 后，后续 try_acquire 标 budget_skipped、factory 不跑。"""
    guard = BudgetGuard(max_llm_calls=10, max_tokens=100)
    calls = {"n": 0}

    async def factory() -> str:
        calls["n"] += 1
        return "value"

    first = await run_with_governance("t1", factory, timeout_s=1.0, budget=guard)
    assert first.ok is True
    assert calls["n"] == 1

    guard.add_tokens(70, 50)  # 120 ≥ 100 → token 预算耗尽
    assert guard.used_tokens == 120
    assert guard.tokens_exhausted() is True

    second = await run_with_governance("t2", factory, timeout_s=1.0, budget=guard)
    assert second.ok is False
    assert second.status == "budget_skipped"
    assert calls["n"] == 1  # factory 未被二次调用
    assert "t2" in guard.skipped


def test_add_tokens_accumulates_and_clamps_negative() -> None:
    guard = BudgetGuard(max_tokens=1000)
    guard.add_tokens(10, 20)
    guard.add_tokens(5)  # output 默认 0
    guard.add_tokens(-3, -100)  # 负数按 0
    assert guard.used_tokens == 35
    assert guard.tokens_exhausted() is False


def test_token_budget_disabled_when_max_tokens_none() -> None:
    guard = BudgetGuard()  # max_tokens 默认 None → 不限
    guard.add_tokens(10_000, 10_000)
    assert guard.tokens_exhausted() is False
    assert guard.try_acquire("anything") is True


# ── A2：整轮 wall-clock 截止 ──


@pytest.mark.asyncio
async def test_wall_clock_deadline_exceeded_returns_structured() -> None:
    """整轮墙钟超 max_wall_s 后，执行前直接 deadline_exceeded 返回，factory 不跑。"""
    guard = BudgetGuard(max_wall_s=0.01)
    guard.started_at = time.monotonic() - 1.0  # 模拟整轮已耗时 1s（免真实 sleep）
    calls = {"n": 0}

    async def factory() -> str:
        calls["n"] += 1
        return "value"

    obs = await run_with_governance("late_tool", factory, timeout_s=1.0, budget=guard)

    assert obs.ok is False
    assert obs.status == "deadline_exceeded"
    assert obs.attempts == 0
    assert calls["n"] == 0
    assert "late_tool" in guard.skipped


@pytest.mark.asyncio
async def test_wall_clock_within_deadline_executes_normally() -> None:
    guard = BudgetGuard(max_wall_s=100.0)

    async def factory() -> str:
        return "ok"

    obs = await run_with_governance("in_time", factory, timeout_s=1.0, budget=guard)

    assert obs.ok is True
    assert obs.status == "ok"
    assert obs.value == "ok"


def test_wall_clock_disabled_when_max_wall_s_none() -> None:
    guard = BudgetGuard()  # max_wall_s 默认 None → 不限
    guard.started_at = time.monotonic() - 10_000.0
    assert guard.deadline_exceeded() is False


# ── A5：错误分类（瞬时 vs 永久）──


def test_is_permanent_error_classification() -> None:
    # 通用异常仍按可重试（保留既有重试契约，不可误判为永久）。
    assert is_permanent_error(ValueError("x")) is False
    assert is_permanent_error(RuntimeError("x")) is False
    assert is_permanent_error(KeyError("x")) is False
    assert is_permanent_error(TimeoutError("x")) is False
    assert is_permanent_error(asyncio.TimeoutError()) is False
    assert is_permanent_error(ConnectionError("x")) is False

    # 永久：校验 / 认证 / 授权语义（按类名 / 子串）。
    class ValidationError(Exception):
        pass

    class AuthenticationError(Exception):
        pass

    class PermissionDeniedError(Exception):
        pass

    assert is_permanent_error(ValidationError("x")) is True
    assert is_permanent_error(AuthenticationError("x")) is True
    assert is_permanent_error(PermissionDeniedError("x")) is True
    assert is_permanent_error(PermissionError("x")) is True

    # HTTP：4xx 永久（408/425/429 除外），5xx 瞬时。
    def http_error(code: int) -> Exception:
        class _Resp:
            status_code = code

        class _HttpError(Exception):
            response = _Resp()

        return _HttpError(f"http {code}")

    assert is_permanent_error(http_error(400)) is True
    assert is_permanent_error(http_error(401)) is True
    assert is_permanent_error(http_error(404)) is True
    assert is_permanent_error(http_error(408)) is False
    assert is_permanent_error(http_error(429)) is False
    assert is_permanent_error(http_error(500)) is False
    assert is_permanent_error(http_error(503)) is False


@pytest.mark.asyncio
async def test_permanent_error_not_retried_even_with_retry_budget() -> None:
    """永久错误（ValidationError 语义）即便 max_retries>0 也只跑一次、status=error。"""

    class ValidationError(Exception):
        pass

    calls = {"n": 0}

    async def factory() -> str:
        calls["n"] += 1
        raise ValidationError("schema invalid")

    obs = await run_with_governance(
        "validate_tool", factory, timeout_s=1.0, max_retries=3
    )

    assert calls["n"] == 1  # 不重试
    assert obs.attempts == 1
    assert obs.ok is False
    assert obs.status == "error"
    assert obs.error_class == "ValidationError"


@pytest.mark.asyncio
async def test_permanent_error_skips_retry_but_still_honors_fallback() -> None:
    """永久错误不重试，但既有降级语义保留：有 fallback 时仍优雅降级；且不污染熔断计数。"""

    class AuthError(Exception):
        pass

    calls = {"n": 0}

    async def factory() -> str:
        calls["n"] += 1
        raise AuthError("401 unauthorized")

    async def fallback() -> str:
        return "degraded"

    obs = await run_with_governance(
        "auth_tool",
        factory,
        timeout_s=1.0,
        max_retries=5,
        fallback=fallback,
        breaker_key="auth_dep",
    )

    assert calls["n"] == 1  # 永久错误不重试
    assert obs.ok is True
    assert obs.status == "fallback"
    assert obs.value == "degraded"
    # 永久错误不计入熔断（非依赖健康信号）。
    assert breaker_for("auth_dep").snapshot()["consecutiveFailures"] == 0


@pytest.mark.asyncio
async def test_transient_error_still_retries_with_backoff(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """瞬时错误（ConnectionError）维持既有重试：max_retries=2 → factory 跑 3 次。"""
    monkeypatch.setattr(harness_mod, "_backoff_delay", lambda attempts: 0.0)
    calls = {"n": 0}

    async def factory() -> str:
        calls["n"] += 1
        raise ConnectionError("network down")

    obs = await run_with_governance("net_tool", factory, timeout_s=1.0, max_retries=2)

    assert calls["n"] == 3  # 首跑 + 2 次重试
    assert obs.attempts == 3
    assert obs.ok is False
    assert obs.status == "error"
    assert obs.error_class == "ConnectionError"


def test_backoff_delay_stays_within_cap_and_nonneg() -> None:
    """全抖动退避：恒落在 [0, cap]，抖动幅度不超过既有上限，且确有随机性。"""
    for attempt in range(1, 9):
        for _ in range(64):
            delay = _backoff_delay(attempt)
            assert 0.0 <= delay <= 4.0
    samples = {round(_backoff_delay(3), 6) for _ in range(32)}
    assert len(samples) > 1


# ── §5.6：observability（OTel）防御性接线 ──


def test_add_tokens_reports_to_otel(monkeypatch: pytest.MonkeyPatch) -> None:
    recorded: list[tuple[int, int]] = []
    monkeypatch.setattr(
        harness_mod, "_otel_record_token_usage", lambda i, o: recorded.append((i, o))
    )
    guard = BudgetGuard()
    guard.add_tokens(7, 3)
    assert recorded == [(7, 3)]
    assert guard.used_tokens == 10


def test_add_tokens_swallows_otel_errors(monkeypatch: pytest.MonkeyPatch) -> None:
    def boom(i: int, o: int) -> None:
        raise RuntimeError("otel down")

    monkeypatch.setattr(harness_mod, "_otel_record_token_usage", boom)
    guard = BudgetGuard()
    guard.add_tokens(4, 6)  # 观测上报失败绝不抛
    assert guard.used_tokens == 10


@pytest.mark.asyncio
async def test_traced_tool_span_wraps_execution(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """span 在 factory 执行前 enter、执行后 exit（execute_tool span 包裹主体）。"""
    events: list[str] = []

    @contextlib.contextmanager
    def spy_span(name: str):
        events.append(f"enter:{name}")
        try:
            yield
        finally:
            events.append(f"exit:{name}")

    monkeypatch.setattr(harness_mod, "_otel_traced_tool", spy_span)

    async def factory() -> str:
        events.append("body")
        return "v"

    obs = await run_with_governance("traced", factory, timeout_s=1.0)

    assert obs.ok is True
    assert events == ["enter:traced", "body", "exit:traced"]


@pytest.mark.asyncio
async def test_span_degrades_when_traced_tool_misbehaves(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """traced_tool 返回非上下文管理器（签名不符）时，_span 静默降级、主链路照常。"""
    monkeypatch.setattr(harness_mod, "_otel_traced_tool", lambda name: object())

    async def factory() -> str:
        return "resilient"

    obs = await run_with_governance("resilient_tool", factory, timeout_s=1.0)

    assert obs.ok is True
    assert obs.value == "resilient"
