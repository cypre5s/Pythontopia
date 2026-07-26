"""百晓路由进程内运行指标（``GET /agents/harness/stats`` 的 ``routing`` 快照）。

只记录运行时可观测量：路由量、复杂度分布、C3 三候选与 A* 选中项、澄清与执行终态、
候选切换/同步重试、派发与阻断步数、近窗 P50/P95 延迟。Intent Accuracy 等需要真值
标注的指标由离线 golden 评测计算，这里绝不伪造线上准确率。

与 ``tools/harness.py`` 的工具级统计同为进程内计数（生产单 worker 部署约束一致）；
写入用锁保护，测试通过 :func:`reset_routing_metrics` 显式复位。
"""

from __future__ import annotations

import math
import threading
from collections import Counter, deque
from typing import Any

from pythontopia_graph.schemas.workshop_route import RouteDecision

# 近窗延迟样本上限：足够稳定估计 P50/P95，又避免长驻进程内存无界增长。
_LATENCY_WINDOW = 512


def _percentile(samples: deque[int], ratio: float) -> int:
    """Nearest-rank 百分位（1-based ``ceil(ratio*n)`` 名次）；空样本返回 0。"""
    if not samples:
        return 0
    ordered = sorted(samples)
    position = math.ceil(ratio * len(ordered))
    return ordered[max(0, min(len(ordered), position) - 1)]


def _rate(numerator: int, denominator: int) -> float:
    return round(numerator / denominator, 4) if denominator else 0.0


class _RoutingMetrics:
    """锁保护的进程内计数器；快照输出 camelCase 与 harness 工具统计风格一致。"""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._reset_locked()

    def _reset_locked(self) -> None:
        self._routes_total = 0
        self._routes_clarification = 0
        self._routes_error = 0
        self._routes_by_complexity: Counter[str] = Counter()
        self._c3_with_search = 0
        self._selected_candidates: Counter[str] = Counter()
        self._route_latencies: deque[int] = deque(maxlen=_LATENCY_WINDOW)
        self._executions_total = 0
        self._executions_by_status: Counter[str] = Counter()
        self._candidate_switches = 0
        self._same_step_retries = 0
        self._agent_dispatches = 0
        self._blocked_steps = 0
        self._execution_latencies: deque[int] = deque(maxlen=_LATENCY_WINDOW)

    def reset(self) -> None:
        with self._lock:
            self._reset_locked()

    def record_route(self, decision: RouteDecision, *, latency_ms: int) -> None:
        with self._lock:
            self._routes_total += 1
            self._routes_by_complexity[decision.task.complexity] += 1
            if decision.task.needs_clarification:
                self._routes_clarification += 1
            if decision.search is not None:
                self._c3_with_search += 1
                self._selected_candidates[decision.search.selected_candidate_id] += 1
            self._route_latencies.append(max(0, int(latency_ms)))

    def record_route_error(self, *, latency_ms: int) -> None:
        """策略门抛错（RoutePolicyError 等）也要进分母，否则故障期指标反而更好看。"""
        with self._lock:
            self._routes_total += 1
            self._routes_error += 1
            self._routes_by_complexity["ERROR"] += 1
            self._route_latencies.append(max(0, int(latency_ms)))

    def record_execution(
        self,
        *,
        status: str,
        dispatched_steps: int,
        blocked_steps: int,
        candidate_switches: int,
        same_step_retries: int,
        latency_ms: int,
    ) -> None:
        with self._lock:
            self._executions_total += 1
            self._executions_by_status[status] += 1
            self._agent_dispatches += max(0, dispatched_steps)
            self._blocked_steps += max(0, blocked_steps)
            self._candidate_switches += max(0, candidate_switches)
            self._same_step_retries += max(0, same_step_retries)
            self._execution_latencies.append(max(0, int(latency_ms)))

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            return {
                "routes": {
                    "total": self._routes_total,
                    "clarifications": self._routes_clarification,
                    "errors": self._routes_error,
                    "byComplexity": dict(self._routes_by_complexity),
                    "c3WithSearch": self._c3_with_search,
                    "selectedCandidates": dict(self._selected_candidates),
                    "latencyMs": {
                        "p50": _percentile(self._route_latencies, 0.5),
                        "p95": _percentile(self._route_latencies, 0.95),
                    },
                    "clarificationRate": _rate(self._routes_clarification, self._routes_total),
                    "c3Rate": _rate(self._c3_with_search, self._routes_total),
                    "errorRate": _rate(self._routes_error, self._routes_total),
                },
                "executions": {
                    "total": self._executions_total,
                    "byStatus": dict(self._executions_by_status),
                    "candidateSwitches": self._candidate_switches,
                    "sameStepRetries": self._same_step_retries,
                    "agentDispatches": self._agent_dispatches,
                    "blockedSteps": self._blocked_steps,
                    "latencyMs": {
                        "p50": _percentile(self._execution_latencies, 0.5),
                        "p95": _percentile(self._execution_latencies, 0.95),
                    },
                    "partialRate": _rate(
                        self._executions_by_status["PARTIAL"], self._executions_total
                    ),
                    "switchRate": _rate(self._candidate_switches, self._executions_total),
                    "errorRate": _rate(
                        self._executions_by_status["ERROR"], self._executions_total
                    ),
                },
            }


_METRICS = _RoutingMetrics()


def record_route(decision: RouteDecision, *, latency_ms: int) -> None:
    _METRICS.record_route(decision, latency_ms=latency_ms)


def record_route_error(*, latency_ms: int) -> None:
    _METRICS.record_route_error(latency_ms=latency_ms)


def record_execution(
    *,
    status: str,
    dispatched_steps: int,
    blocked_steps: int,
    candidate_switches: int,
    same_step_retries: int,
    latency_ms: int,
) -> None:
    _METRICS.record_execution(
        status=status,
        dispatched_steps=dispatched_steps,
        blocked_steps=blocked_steps,
        candidate_switches=candidate_switches,
        same_step_retries=same_step_retries,
        latency_ms=latency_ms,
    )


def routing_metrics_snapshot() -> dict[str, Any]:
    return _METRICS.snapshot()


def reset_routing_metrics() -> None:
    _METRICS.reset()
