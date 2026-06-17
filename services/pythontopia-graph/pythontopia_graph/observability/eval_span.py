"""在线轻量评分写入当前 OTel span（O1-otel-langfuse 落地实现）。

S1 在 critic 后调用 :func:`record_eval_score` 记录 grounding/safety 通过率等在线轻量分。
与 :mod:`pythontopia_graph.observability.otel` 同范式：缺 ``opentelemetry`` / 无活动 span /
任何异常 → 全部降级 no-op，绝不影响主链路（并发构建主契约 §0 fail-soft 观测 + §3.6 第 3 条）。

属性键命名空间为项目自有的 ``pythontopia.eval.{name}``，与 OTel GenAI ``gen_ai.*`` 标准
语义约定区隔，避免污染标准约定。
"""

from __future__ import annotations

# ── 顶层导入守卫：缺 opentelemetry（API 层）时整模块降级为 no-op（沿用 otel.py 范式）──
try:
    from opentelemetry import trace as _otel_trace

    _ENABLED = True
except Exception:  # noqa: BLE001 - 任意导入异常（缺包/损坏）都降级，绝不拖垮主链路
    _otel_trace = None  # type: ignore[assignment]
    _ENABLED = False


# 项目自有评分属性命名空间（与标准 gen_ai.* 区隔）。
_EVAL_ATTR_PREFIX = "pythontopia.eval."


def is_enabled() -> bool:
    """opentelemetry（API 层）是否可用。供调用方/测试探测降级状态，不影响行为。"""
    return _ENABLED


def record_eval_score(name: str, value: float) -> None:
    """把一条在线评分写到**当前活动 span** 的 ``pythontopia.eval.{name}`` 属性。

    降级语义（与 :func:`otel.record_token_usage` 一致）：

    - 缺 ``opentelemetry`` 包（``_ENABLED`` 为 False）→ 直接返回，no-op。
    - 当前无活动 span → ``get_current_span`` 返回 INVALID_SPAN，其 ``set_attribute`` 为安全
      no-op（不会产生孤儿 span）。
    - 任何异常（含 ``value`` 不可转 ``float``）→ 一律吞掉降级 no-op，绝不影响主链路。
    """
    if not _ENABLED:
        return
    try:
        span = _otel_trace.get_current_span()
        span.set_attribute(f"{_EVAL_ATTR_PREFIX}{name}", float(value))
    except Exception:  # noqa: BLE001 - 观测失败绝不影响主链路
        return
