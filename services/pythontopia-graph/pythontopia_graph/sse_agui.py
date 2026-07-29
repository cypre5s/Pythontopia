"""AG-UI 语义 SSE 事件编码（graph 侧统一出口）。

把 agent↔前端的流式事件统一编码为 AG-UI（Agent-User Interaction Protocol）官方事件名，
替代散落在各 route 的手写 ``_format_sse``。前端 ``useSseStream`` 是双语兼容层：AG-UI 官方名与
项目历史名（plan/trace/token/result/error）都能消费，故迁移可逐端点灰度、无需前后端同步切换。

字段命名对齐前端 ``agui.ts`` 的 ``toAguiEvent``：
* ``TEXT_MESSAGE_CONTENT`` → ``{"delta": str, "messageId"?: str}``
* ``STEP_STARTED`` / ``STEP_FINISHED`` → 沿用历史 trace 形状 ``{node, phase, status, elapsed_ms, ...}``
* ``RUN_FINISHED`` → ``{"result": <any>}``
* ``RUN_ERROR`` → ``{"code": str, "message": str}``
* ``TOOL_CALL_*`` → ``{"toolCallId": str, ...}``
* ``STATE_SNAPSHOT`` → ``{"snapshot": {...}}`` ;
  ``STATE_DELTA`` → ``{"delta": [{"op": ..., "path": ...}]}``（RFC 6902）
* ``CUSTOM`` → ``{"name": str, "value": <any>}``

Java 中转层（``GraphSseConsumer`` + 各 ``*GraphClient``）按事件名原样透传，故新事件名穿过后端零改动。
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any

from pythontopia_graph.schemas.loop_contract import LoopResult, validate_loop_result_payload


_AGUI_EVENTS = frozenset(
    {
        "RUN_STARTED",
        "RUN_FINISHED",
        "RUN_ERROR",
        "STEP_STARTED",
        "STEP_FINISHED",
        "TEXT_MESSAGE_START",
        "TEXT_MESSAGE_CONTENT",
        "TEXT_MESSAGE_END",
        "TOOL_CALL_START",
        "TOOL_CALL_ARGS",
        "TOOL_CALL_END",
        "TOOL_CALL_RESULT",
        "STATE_SNAPSHOT",
        "STATE_DELTA",
        "CUSTOM",
    }
)
_LEGACY_EVENTS = frozenset({"plan", "trace", "token", "result", "error", "step_error"})


@dataclass(frozen=True)
class StreamQuality:
    """A deterministic quality summary for one agent runtime stream.

    The score is intentionally simple: it rewards canonical AG-UI lifecycle,
    text, step, state, and terminal events, while applying a penalty for legacy
    event names. It is used as a regression gate, not as user-facing telemetry.
    """

    total_events: int
    agui_event_rate: float
    lifecycle_coverage: float
    step_coverage: float
    text_coverage: float
    tool_event_coverage: float
    state_sync_coverage: float
    terminal_coverage: float
    legacy_event_count: int
    score: float


def _round(value: float) -> float:
    return round(value, 3)


def stream_quality(events: list[tuple[str, dict[str, Any]]]) -> StreamQuality:
    """Score how close a stream is to the canonical AG-UI runtime contract.

    Legacy names are still compatible with the frontend, but canonical names
    are easier to interoperate with AG-UI clients and downstream observability.
    """

    if not events:
        return StreamQuality(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0)
    names = [name for name, _ in events]
    agui_count = sum(1 for name in names if name in _AGUI_EVENTS)
    legacy_count = sum(1 for name in names if name in _LEGACY_EVENTS)
    has_run_started = "RUN_STARTED" in names
    has_terminal = any(name in {"RUN_FINISHED", "RUN_ERROR"} for name in names)
    has_legacy_terminal = any(name in {"result", "error"} for name in names)
    has_step_start = "STEP_STARTED" in names
    has_step_finish = "STEP_FINISHED" in names
    has_legacy_trace = "trace" in names
    has_text = "TEXT_MESSAGE_CONTENT" in names
    has_legacy_token = "token" in names
    has_tool = any(name.startswith("TOOL_CALL_") for name in names)
    has_state = any(name in {"STATE_SNAPSHOT", "STATE_DELTA"} for name in names)

    lifecycle = 1.0 if has_run_started and has_terminal else (0.5 if has_legacy_terminal else 0.0)
    step = 1.0 if has_step_start and has_step_finish else (0.5 if has_legacy_trace else 0.0)
    text = 1.0 if has_text else (0.5 if has_legacy_token else 0.0)
    terminal = 1.0 if has_terminal else (0.5 if has_legacy_terminal else 0.0)
    tool = 1.0 if has_tool else 0.0
    state = 1.0 if has_state else 0.0
    agui_rate = agui_count / len(events)
    legacy_penalty = min(0.25, legacy_count * 0.04)
    score = (
        lifecycle * 0.20
        + step * 0.18
        + text * 0.15
        + terminal * 0.15
        + state * 0.12
        + tool * 0.05
        + agui_rate * 0.15
        - legacy_penalty
    )
    return StreamQuality(
        total_events=len(events),
        agui_event_rate=_round(agui_rate),
        lifecycle_coverage=_round(lifecycle),
        step_coverage=_round(step),
        text_coverage=_round(text),
        tool_event_coverage=_round(tool),
        state_sync_coverage=_round(state),
        terminal_coverage=_round(terminal),
        legacy_event_count=legacy_count,
        score=_round(max(0.0, min(1.0, score))),
    )


def format_sse(event: str, data: dict[str, Any]) -> str:
    """编码一条 SSE 记录（``event:`` + JSON ``data:``），与既有 ``_format_sse`` 字节级一致。"""
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def run_started(**extra: Any) -> str:
    """一次 agent run 的生命周期起点。"""
    return format_sse("RUN_STARTED", dict(extra))


def run_finished(result: Any) -> str:
    """运行成功结束，携带最终结构化结果（前端投影到 ``useSseStream.result``）。"""
    return format_sse("RUN_FINISHED", {"result": result})


def run_finished_loop_result(result: LoopResult | dict[str, Any]) -> str:
    """运行成功结束，并要求终态满足冻结 loop runtime contract。"""
    payload = validate_loop_result_payload(result).model_dump(mode="json")
    return run_finished(payload)


def run_error(code: str, message: str) -> str:
    """运行失败（前端投影到 ``useSseStream.error`` 并置 error 态）。"""
    return format_sse("RUN_ERROR", {"code": code, "message": message})


def step_started(node: str, **extra: Any) -> str:
    """节点/步骤开始；沿用历史 trace 形状，前端投影到 ``traces``，既有思考链 UI 零改动点亮。"""
    payload: dict[str, Any] = {
        "node": node,
        "phase": "start",
        "status": "running",
        "elapsed_ms": 0,
    }
    payload.update(extra)
    return format_sse("STEP_STARTED", payload)


def step_finished(node: str, status: str, elapsed_ms: int, **extra: Any) -> str:
    """节点/步骤结束（同样投影到 ``traces``）。"""
    payload: dict[str, Any] = {
        "node": node,
        "phase": "finish",
        "status": status,
        "elapsed_ms": elapsed_ms,
    }
    payload.update(extra)
    return format_sse("STEP_FINISHED", payload)


def text_message_start(message_id: str, role: str | None = None) -> str:
    """文本消息开始（分帧，支撑一条流承载多条消息）。"""
    payload: dict[str, Any] = {"messageId": message_id}
    if role is not None:
        payload["role"] = role
    return format_sse("TEXT_MESSAGE_START", payload)


def text_message_content(delta: str, message_id: str | None = None) -> str:
    """文本消息增量（前端投影到 ``tokens``，等价历史 token 事件）。"""
    payload: dict[str, Any] = {"delta": delta}
    if message_id is not None:
        payload["messageId"] = message_id
    return format_sse("TEXT_MESSAGE_CONTENT", payload)


def text_message_end(message_id: str | None = None) -> str:
    """文本消息结束。"""
    payload: dict[str, Any] = {}
    if message_id is not None:
        payload["messageId"] = message_id
    return format_sse("TEXT_MESSAGE_END", payload)


def tool_call_start(tool_call_id: str, tool_name: str) -> str:
    """工具调用开始（模块 C 工具卡生命周期；不污染既有 traces）。"""
    return format_sse("TOOL_CALL_START", {"toolCallId": tool_call_id, "toolName": tool_name})


def tool_call_args(tool_call_id: str, delta: str) -> str:
    """工具调用入参增量（流式 JSON 片段）。"""
    return format_sse("TOOL_CALL_ARGS", {"toolCallId": tool_call_id, "delta": delta})


def tool_call_end(tool_call_id: str) -> str:
    """工具调用结束。"""
    return format_sse("TOOL_CALL_END", {"toolCallId": tool_call_id})


def tool_call_result(tool_call_id: str, content: Any) -> str:
    """工具调用结果。"""
    return format_sse("TOOL_CALL_RESULT", {"toolCallId": tool_call_id, "content": content})


def state_snapshot(snapshot: dict[str, Any]) -> str:
    """运行态全量快照（前端覆盖 ``useSseStream.state``）。"""
    return format_sse("STATE_SNAPSHOT", {"snapshot": snapshot})


def state_delta(delta: list[dict[str, Any]]) -> str:
    """运行态增量（RFC 6902 JSON Patch；前端应用到 ``useSseStream.state``）。"""
    return format_sse("STATE_DELTA", {"delta": delta})


def custom(name: str, value: Any) -> str:
    """自定义事件（承载无官方对应的语义，如历史 plan「工位清单」）。"""
    return format_sse("CUSTOM", {"name": name, "value": value})


def translate(event: str, data: dict[str, Any]) -> str:
    """把历史事件名（trace/token/result/error/plan）统一编码为 AG-UI 官方事件。

    供既有 route 的 ``_format_sse`` 一处改写、全量灰度迁移：调用点零改动即切到 AG-UI 命名，
    前端 useSseStream 双语兼容层均可消费。已是 AG-UI 官方名或未知名 → 原样 format_sse 透传。

    trace 保留 data 原样、仅换事件名（phase==finish → STEP_FINISHED，否则 STEP_STARTED），
    确保前端 traces 投影的字段（node/status/elapsed_ms/note/role_key…）与历史完全一致。
    """
    name = event.strip()
    if name == "token":
        return text_message_content(str(data.get("delta", "")), data.get("messageId"))
    if name == "result":
        return run_finished(data)
    if name == "error":
        return run_error(str(data.get("code", "UNKNOWN")), str(data.get("message", "")))
    if name == "plan":
        return custom("plan", data)
    if name == "trace":
        agui_name = (
            "STEP_FINISHED"
            if data.get("phase") == "finish" or str(data.get("status", "")).lower() != "running"
            else "STEP_STARTED"
        )
        return format_sse(agui_name, data)
    return format_sse(name, data)
