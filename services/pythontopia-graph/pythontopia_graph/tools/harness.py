"""工具执行治理 harness：超时 / 退避重试 / 熔断 / 预算 / 结构化观测 / 审计日志。

设计要点（2026 agent harness 工程共识）：
- LLM/外部子调用绝不裸跑：经 :func:`run_with_governance` 包装后**永远**返回结构化
  :class:`ToolObservation`（ok / timeout / error / fallback / budget_skipped / circuit_open），
  绝无悬空异常打断 SSE 流；
- 每次调用一条审计 JSON 日志（工具名 / 耗时 / 状态 / 重试次数 / 是否降级），进程内计数器
  汇总到 :func:`harness_stats`（供 ``GET /agents/harness/stats``）；
- 熔断器按依赖 key（如 ``provider`` / ``rag``）独立计数：连续失败达阈值 → 冷却窗口内直接
  走降级（不再等超时挂死请求），半开探测自动恢复；瞬时错误（网络/超时/5xx）才计入熔断，
  永久错误（校验/认证/4xx 语义）fail fast 不重试、也不污染熔断计数；
- 预算守卫三道闸：每请求 LLM 子调用次数、累计 token、整轮 wall-clock 截止；超限的低优先级
  步骤标记 ``budget_skipped``、整轮超时标记 ``deadline_exceeded`` 直接跳过；
- 可选接入 OpenTelemetry（``observability.otel``）：execute_tool span + token 用量，缺模块 /
  缺依赖时全程降级 no-op，绝不影响主链路。

单 worker 约束友好：全部状态为进程内 asyncio 单线程访问，无跨进程共享。
"""

from __future__ import annotations

import asyncio
import contextlib
import json
import logging
import random
import time
from collections import defaultdict
from collections.abc import Awaitable, Callable, Iterator
from dataclasses import dataclass, field
from typing import Any

LOG = logging.getLogger("pythontopia.harness")

# ── 防御性接入 observability（A-otel-foundation 实现，见主契约 §5.6）──
# 该模块由另一个 agent 实现，此刻可能尚不存在；缺模块 / 缺 opentelemetry 依赖 / 任何
# 导入期异常都降级为 no-op，**绝不**让导入失败影响主链路。
try:  # pragma: no cover - 取决于 observability 模块是否落地
    from pythontopia_graph.observability.otel import (
        record_token_usage as _otel_record_token_usage,
        traced_tool as _otel_traced_tool,
    )

    _OTEL_AVAILABLE = True
except Exception:  # noqa: BLE001 - 任何导入期异常都降级 no-op
    _OTEL_AVAILABLE = False

    def _otel_record_token_usage(input_tokens: int, output_tokens: int) -> None:
        """no-op 占位：observability 模块缺失时的降级实现。"""
        return None

    @contextlib.contextmanager
    def _otel_traced_tool(name: str) -> Iterator[Any]:
        """no-op 占位：observability 模块缺失时的降级实现。"""
        yield


@contextlib.contextmanager
def _span(tool: str) -> Iterator[None]:
    """防御性包裹 execute_tool span：``traced_tool`` 缺失或行为异常时静默降级 no-op。

    无论 ``traced_tool`` 被实现成上下文管理器还是别的形态，这里只在能正常
    ``__enter__`` / ``__exit__`` 时启用 span；任何异常都被吞掉，绝不冒泡进主链路。
    """
    cm: Any = None
    try:
        cm = _otel_traced_tool(tool)
        cm.__enter__()
    except Exception:  # noqa: BLE001 - span 启用失败绝不影响主链路
        cm = None
    try:
        yield
    finally:
        if cm is not None:
            try:
                cm.__exit__(None, None, None)
            except Exception:  # noqa: BLE001 - span 收尾失败同样吞掉
                pass


# 结构化观测状态取值（新增 "deadline_exceeded" 不破坏既有取值）。
ObservationStatus = str  # ok|timeout|error|fallback|budget_skipped|circuit_open|deadline_exceeded


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
    """每请求预算守卫：LLM 子调用次数 + 累计 token + 整轮 wall-clock 截止。

    三道预算彼此独立、皆可单独关闭：

    - ``max_llm_calls``：子调用次数上限（默认 6）。
    - ``max_tokens``：累计 input+output token 上限；``None`` 表示不限。调用方拿到
      usage 后用 :meth:`add_tokens` 累加，达到/超过上限后续 :meth:`try_acquire` 返回
      ``False``（标 ``budget_skipped``）。
    - ``max_wall_s``：整轮墙钟预算（自 :attr:`started_at` 起）；``None`` 表示不限，
      由 :func:`run_with_governance` 在执行前用 :meth:`deadline_exceeded` 判超时。

    超限步骤记入 :attr:`skipped`（先砍低优先级），便于审计「为什么这步没跑」。
    全部状态为进程内 asyncio 单线程访问，无需加锁。
    """

    max_llm_calls: int = 6
    max_tokens: int | None = None
    max_wall_s: float | None = None
    used: int = 0
    used_tokens: int = 0
    skipped: list[str] = field(default_factory=list)
    started_at: float = field(default_factory=time.monotonic)

    def add_tokens(self, input_tokens: int = 0, output_tokens: int = 0) -> None:
        """累加一次 LLM 调用的 token 用量（调用方拿到 usage 后调用）。

        同时把用量上报 OTel（observability 缺失时为 no-op）；上报异常被吞，
        绝不影响预算记账与主链路。负数按 0 处理。
        """
        self.used_tokens += max(0, int(input_tokens)) + max(0, int(output_tokens))
        try:
            _otel_record_token_usage(int(input_tokens), int(output_tokens))
        except Exception:  # noqa: BLE001 - 观测上报绝不影响主链路
            pass

    def tokens_exhausted(self) -> bool:
        """累计 token 是否已达到/超过预算（``max_tokens=None`` 时永远 False）。"""
        return self.max_tokens is not None and self.used_tokens >= self.max_tokens

    def deadline_exceeded(self) -> bool:
        """整轮墙钟是否已超 ``max_wall_s``（``max_wall_s=None`` 时永远 False）。"""
        if self.max_wall_s is None:
            return False
        return (time.monotonic() - self.started_at) >= self.max_wall_s

    def try_acquire(self, tool: str, cost: int = 1) -> bool:
        # token 预算优先：耗尽后一切后续子调用直接跳过（与次数预算独立）。
        if self.tokens_exhausted():
            self.skipped.append(tool)
            return False
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


# ── 错误分类（瞬时 vs 永久）与重试退避 ──

# 永久错误类名（精确匹配）：校验 / schema / 权限语义，重试无意义。
# 刻意不含 ValueError/TypeError/KeyError 等通用异常——既有契约里它们仍按可重试处理。
_PERMANENT_ERROR_NAMES = frozenset(
    {
        "ValidationError",  # pydantic v1/v2、marshmallow
        "RequestValidationError",  # FastAPI
        "SchemaError",
        "SchemaValidationError",
        "PermissionError",  # builtin：权限不足，重试不会变好
    }
)

# 永久错误类名子串（小写包含匹配）：认证 / 授权 / 校验语义。
_PERMANENT_NAME_SUBSTRINGS = (
    "validation",
    "schema",
    "auth",  # AuthenticationError / Unauthorized / OAuthError…
    "permission",  # PermissionDeniedError…
    "forbidden",
)

# HTTP 4xx 中仍按瞬时处理（可重试）的状态码：请求超时 / Too Early / 限流。
_TRANSIENT_HTTP_CODES = frozenset({408, 425, 429})

_BACKOFF_BASE_S = 0.5
_BACKOFF_CAP_S = 4.0


def _http_status(exc: BaseException) -> int | None:
    """尽力从异常对象抽取 HTTP 状态码（兼容 httpx / aiohttp / openai 等形态）。"""
    response = getattr(exc, "response", None)
    code = getattr(response, "status_code", None)
    if isinstance(code, int) and not isinstance(code, bool):
        return code
    for attr in ("status_code", "status"):
        code = getattr(exc, attr, None)
        if isinstance(code, int) and not isinstance(code, bool):
            return code
    return None


def is_permanent_error(exc: BaseException) -> bool:
    """区分永久错误（不可重试）与瞬时错误（可退避重试）。

    永久（返回 True）：校验/schema、认证/授权、HTTP 4xx（408/425/429 除外）。
    瞬时（返回 False）：超时、网络/连接错误、HTTP 5xx，以及**判不准的一切**——
    兜底按瞬时处理，宁可多重试也不误杀可恢复故障。
    """
    # 超时永远可重试。
    if isinstance(exc, (TimeoutError, asyncio.TimeoutError)):  # noqa: UP041 - 3.10 下两者不同
        return False

    # HTTP 状态码语义最可靠，优先判定。
    status = _http_status(exc)
    if status is not None:
        if status in _TRANSIENT_HTTP_CODES:
            return False
        return 400 <= status < 500

    name = type(exc).__name__
    if name in _PERMANENT_ERROR_NAMES:
        return True
    lowered = name.lower()
    if any(token in lowered for token in _PERMANENT_NAME_SUBSTRINGS):
        return True

    # 连接 / 网络 / OS 错误按瞬时处理（PermissionError 等已在上面按名命中永久）。
    if isinstance(exc, (ConnectionError, OSError)):
        return False

    # 兜底：判不准按瞬时，保留重试。
    return False


def _backoff_delay(attempts: int) -> float:
    """瞬时错误重试退避：指数 ``base·2^(n-1)`` 截到 cap，再叠加全抖动（full jitter）。

    返回落在 ``[0, ceiling]``，``ceiling`` ≤ :data:`_BACKOFF_CAP_S`，抖动幅度不超过
    既有上限；用于打散并发重试、缓解惊群（thundering herd）。
    """
    ceiling = min(_BACKOFF_BASE_S * (2 ** max(0, attempts - 1)), _BACKOFF_CAP_S)
    return random.uniform(0.0, ceiling)


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

    流程：整轮 wall-clock 截止检查 → 预算（次数/token）检查 → 熔断检查 →
    超时执行（瞬时错误指数退避+抖动重试，永久错误 fail fast 不重试）→ 失败走降级 →
    审计/统计。``factory`` 每次重试重新调用（不复用已耗尽的协程）。降级成功时
    ``status="fallback"`` 且 ``ok=True``；降级也失败或无降级时 ``ok=False``。
    """
    started = time.monotonic()

    def _finish(observation: ToolObservation) -> ToolObservation:
        observation.elapsed_ms = int((time.monotonic() - started) * 1000)
        _record_stats(observation)
        _audit_log(observation)
        return observation

    # 整轮 wall-clock 截止：执行前若本请求总预算已超时，不再启新子调用，直接结构化返回。
    if budget is not None and budget.deadline_exceeded():
        budget.skipped.append(tool)
        return _finish(
            ToolObservation(tool=tool, ok=False, status="deadline_exceeded", attempts=0)
        )

    # 预算守卫（子调用次数 + 累计 token）：超限步骤标记 budget_skipped 跳过。
    if budget is not None and not budget.try_acquire(tool, budget_cost):
        return _finish(
            ToolObservation(tool=tool, ok=False, status="budget_skipped", attempts=0)
        )

    with _span(tool):
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
        permanent = False
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
            except Exception as exc:  # noqa: BLE001 - 重试/降级前先分类记录
                last_error = exc
                timed_out = False
                permanent = is_permanent_error(exc)
            if permanent:
                # 永久错误（校验/认证/4xx 语义）：即便仍有重试额度也 fail fast；
                # 且不计入熔断（非依赖健康信号，避免客户端错误误开熔断器）。
                break
            if attempts <= max_retries:
                # 瞬时错误：指数退避 + 全抖动，单 worker 下让出事件循环即可。
                await asyncio.sleep(_backoff_delay(attempts))

        # 一次逻辑调用只记一次失败：重试额度耗尽后才计入熔断。放在循环内会把
        # 单个请求的 N 次重试放大成 N 次「连续失败」——阈值 3 时一个请求就能打开
        # 进程级共享熔断器（breaker_key="provider" 被答疑/意图/派发多处共用）30s，
        # 殃及所有会话。永久错误不计入（非依赖健康信号）。
        if breaker is not None and not permanent:
            breaker.record_failure()

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
