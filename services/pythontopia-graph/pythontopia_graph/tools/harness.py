"""工具执行治理 harness：超时 / 退避重试 / 熔断 / 预算 / 结构化观测 / 审计日志。

设计要点（2026 agent harness 工程共识）：
- LLM/外部子调用绝不裸跑：经 :func:`run_with_governance` 包装后**永远**返回结构化
  :class:`ToolObservation`（ok / timeout / error / fallback / budget_skipped / circuit_open），
  绝无悬空异常打断 SSE 流；
- 每次调用一条审计 JSON 日志（工具名 / 耗时 / 状态 / 重试次数 / 是否降级），进程内计数器
  汇总到 :func:`harness_stats`（供 ``GET /agents/harness/stats``）；
- 熔断器按依赖 key（如 ``provider`` / ``rag``）独立计数：连续失败达阈值 → 冷却窗口内直接
  走降级（不再等超时挂死请求），半开探测自动恢复；
- 预算守卫限制每请求 LLM 子调用次数，超限的低优先级步骤标记 ``budget_skipped`` 跳过。

单 worker 约束友好：全部状态为进程内 asyncio 单线程访问，无跨进程共享。
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
from collections import defaultdict
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from typing import Any

LOG = logging.getLogger("pythontopia.harness")

ObservationStatus = str  # "ok" | "timeout" | "error" | "fallback" | "budget_skipped" | "circuit_open"


@dataclass
class ToolObservation:
    """一次受治理工具调用的结构化观测（永远返回，绝无悬空）。"""

    tool: str
    ok: bool
    status: ObservationStatus
    elapsed_ms: int = 0
    attempts: int = 0
    fallback_used: bool = False
    error_class: str | None = None
    # 成功（或降级成功）时的返回值；失败时为 None。
    value: Any = None

    def as_trace_note(self) -> str:
        """trace note 摘要：状态 + 重试 + 降级标记（供 SSE trace / 前端工具行展示）。"""
        parts = [self.status]
        if self.attempts > 1:
            parts.append(f"retry={self.attempts - 1}")
        if self.fallback_used:
            parts.append("fallback")
        return " ".join(parts)


class CircuitBreaker:
    """按依赖 key 的简单熔断器：连续失败达阈值 → 冷却期内拒绝直连，半开探测恢复。"""

    def __init__(self, failure_threshold: int = 3, cooldown_s: float = 30.0) -> None:
        self.failure_threshold = failure_threshold
        self.cooldown_s = cooldown_s
        self._consecutive_failures = 0
        self._opened_at: float | None = None

    @property
    def is_open(self) -> bool:
        if self._opened_at is None:
            return False
        if time.monotonic() - self._opened_at >= self.cooldown_s:
            # 半开：放行一次探测（探测失败会立刻重新打开）。
            return False
        return True

    def record_success(self) -> None:
        self._consecutive_failures = 0
        self._opened_at = None

    def record_failure(self) -> None:
        self._consecutive_failures += 1
        if self._consecutive_failures >= self.failure_threshold:
            self._opened_at = time.monotonic()

    def snapshot(self) -> dict[str, Any]:
        return {
            "open": self.is_open,
            "consecutiveFailures": self._consecutive_failures,
        }


# 进程级熔断器注册（key 如 "provider" / "rag" / "orchestrator"）。
_BREAKERS: dict[str, CircuitBreaker] = {}


def breaker_for(key: str) -> CircuitBreaker:
    if key not in _BREAKERS:
        _BREAKERS[key] = CircuitBreaker()
    return _BREAKERS[key]


@dataclass
class BudgetGuard:
    """每请求 LLM 子调用预算：超限的步骤标记 budget_skipped 跳过（先砍低优先级）。"""

    max_llm_calls: int = 6
    used: int = 0
    skipped: list[str] = field(default_factory=list)

    def try_acquire(self, tool: str, cost: int = 1) -> bool:
        if self.used + cost > self.max_llm_calls:
            self.skipped.append(tool)
            return False
        self.used += cost
        return True


# ── 进程内统计（GET /agents/harness/stats）──
_STATS: dict[str, dict[str, Any]] = defaultdict(
    lambda: {"calls": 0, "failures": 0, "fallbacks": 0, "totalElapsedMs": 0}
)


def _record_stats(observation: ToolObservation) -> None:
    entry = _STATS[observation.tool]
    entry["calls"] += 1
    entry["totalElapsedMs"] += observation.elapsed_ms
    if not observation.ok:
        entry["failures"] += 1
    if observation.fallback_used:
        entry["fallbacks"] += 1


def harness_stats() -> dict[str, Any]:
    """统计快照：各工具调用数/失败数/降级数/均耗时 + 各熔断器状态。"""
    tools = {
        name: {
            **entry,
            "avgElapsedMs": (
                entry["totalElapsedMs"] // entry["calls"] if entry["calls"] else 0
            ),
        }
        for name, entry in _STATS.items()
    }
    return {
        "tools": tools,
        "breakers": {key: breaker.snapshot() for key, breaker in _BREAKERS.items()},
    }


def _audit_log(observation: ToolObservation) -> None:
    LOG.info(
        "tool_call %s",
        json.dumps(
            {
                "tool": observation.tool,
                "ok": observation.ok,
                "status": observation.status,
                "elapsedMs": observation.elapsed_ms,
                "attempts": observation.attempts,
                "fallbackUsed": observation.fallback_used,
                "errorClass": observation.error_class,
            },
            ensure_ascii=False,
        ),
    )


async def run_with_governance(
    tool: str,
    factory: Callable[[], Awaitable[Any]],
    *,
    timeout_s: float = 60.0,
    max_retries: int = 0,
    fallback: Callable[[], Awaitable[Any]] | None = None,
    breaker_key: str | None = None,
    budget: BudgetGuard | None = None,
    budget_cost: int = 1,
) -> ToolObservation:
    """受治理地执行一个异步子调用，永远返回结构化 :class:`ToolObservation`。

    流程：预算检查 → 熔断检查 → 超时执行（指数退避重试）→ 失败走降级 → 审计/统计。
    ``factory`` 每次重试重新调用（不复用已耗尽的协程）。降级成功时 ``status="fallback"``
    且 ``ok=True``；降级也失败或无降级时 ``ok=False``。
    """
    started = time.monotonic()

    def _finish(observation: ToolObservation) -> ToolObservation:
        observation.elapsed_ms = int((time.monotonic() - started) * 1000)
        _record_stats(observation)
        _audit_log(observation)
        return observation

    if budget is not None and not budget.try_acquire(tool, budget_cost):
        return _finish(
            ToolObservation(tool=tool, ok=False, status="budget_skipped", attempts=0)
        )

    breaker = breaker_for(breaker_key) if breaker_key else None
    if breaker is not None and breaker.is_open:
        if fallback is not None:
            try:
                value = await fallback()
                return _finish(
                    ToolObservation(
                        tool=tool,
                        ok=True,
                        status="circuit_open",
                        attempts=0,
                        fallback_used=True,
                        value=value,
                    )
                )
            except Exception as exc:  # noqa: BLE001 - 降级失败也必须结构化返回
                return _finish(
                    ToolObservation(
                        tool=tool,
                        ok=False,
                        status="circuit_open",
                        attempts=0,
                        error_class=type(exc).__name__,
                    )
                )
        return _finish(
            ToolObservation(tool=tool, ok=False, status="circuit_open", attempts=0)
        )

    attempts = 0
    last_error: BaseException | None = None
    timed_out = False
    while attempts <= max_retries:
        attempts += 1
        try:
            value = await asyncio.wait_for(factory(), timeout=timeout_s)
            if breaker is not None:
                breaker.record_success()
            return _finish(
                ToolObservation(
                    tool=tool, ok=True, status="ok", attempts=attempts, value=value
                )
            )
        except (TimeoutError, asyncio.TimeoutError) as exc:  # noqa: UP041 - 3.10 下两者不同
            last_error = exc
            timed_out = True
        except asyncio.CancelledError:
            raise
        except Exception as exc:  # noqa: BLE001 - 重试/降级前先记录
            last_error = exc
            timed_out = False
        if breaker is not None:
            breaker.record_failure()
        if attempts <= max_retries:
            # 指数退避（0.5s, 1s, 2s…），单 worker 下让出事件循环即可。
            await asyncio.sleep(min(0.5 * (2 ** (attempts - 1)), 4.0))

    error_class = type(last_error).__name__ if last_error else "UnknownError"
    if fallback is not None:
        try:
            value = await fallback()
            return _finish(
                ToolObservation(
                    tool=tool,
                    ok=True,
                    status="fallback",
                    attempts=attempts,
                    fallback_used=True,
                    error_class=error_class,
                    value=value,
                )
            )
        except Exception as exc:  # noqa: BLE001 - 降级失败也必须结构化返回
            error_class = type(exc).__name__

    return _finish(
        ToolObservation(
            tool=tool,
            ok=False,
            status="timeout" if timed_out else "error",
            attempts=attempts,
            error_class=error_class,
        )
    )


def reset_harness_state() -> None:
    """测试隔离用：清空统计与熔断器（生产代码不调用）。"""
    _STATS.clear()
    _BREAKERS.clear()
