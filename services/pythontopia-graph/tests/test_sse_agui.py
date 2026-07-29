"""AG-UI SSE 事件编码（sse_agui）单测。

验证线格式与前端 ``agui.ts`` 的 ``toAguiEvent`` 字段契约严格对齐，确保后端逐端点切换到 AG-UI
官方事件名后，前端双语兼容层能无缝消费。
"""

from __future__ import annotations

import pytest

from pythontopia_graph import sse_agui


def test_format_sse_line_shape() -> None:
    assert sse_agui.format_sse("PING", {"x": 1}) == 'event: PING\ndata: {"x": 1}\n\n'


def test_run_finished_wraps_result() -> None:
    assert (
        sse_agui.run_finished({"ok": True})
        == 'event: RUN_FINISHED\ndata: {"result": {"ok": true}}\n\n'
    )


def test_run_error_shape() -> None:
    assert (
        sse_agui.run_error("BOOM", "炸了")
        == 'event: RUN_ERROR\ndata: {"code": "BOOM", "message": "炸了"}\n\n'
    )


def test_text_message_content_keeps_unicode() -> None:
    assert (
        sse_agui.text_message_content("你好")
        == 'event: TEXT_MESSAGE_CONTENT\ndata: {"delta": "你好"}\n\n'
    )


def test_text_message_content_with_message_id() -> None:
    assert (
        sse_agui.text_message_content("hi", message_id="m1")
        == 'event: TEXT_MESSAGE_CONTENT\ndata: {"delta": "hi", "messageId": "m1"}\n\n'
    )


def test_step_started_default_fields_and_extra() -> None:
    out = sse_agui.step_started("evidence_fetcher", role_key="librarian")
    assert out.startswith("event: STEP_STARTED\n")
    assert '"node": "evidence_fetcher"' in out
    assert '"phase": "start"' in out
    assert '"status": "running"' in out
    assert '"elapsed_ms": 0' in out
    assert '"role_key": "librarian"' in out


def test_step_finished_fields() -> None:
    out = sse_agui.step_finished("grounding_critic", "ok", 12, chunks=3)
    assert out.startswith("event: STEP_FINISHED\n")
    assert '"phase": "finish"' in out
    assert '"status": "ok"' in out
    assert '"elapsed_ms": 12' in out
    assert '"chunks": 3' in out


def test_state_delta_wraps_delta() -> None:
    assert (
        sse_agui.state_delta([{"op": "add", "path": "/masteryAvg", "value": 55}])
        == 'event: STATE_DELTA\ndata: {"delta": [{"op": "add", "path": "/masteryAvg", "value": 55}]}\n\n'
    )


def test_state_snapshot_wraps_snapshot() -> None:
    assert (
        sse_agui.state_snapshot({"masteryAvg": 40, "energy": 90})
        == 'event: STATE_SNAPSHOT\ndata: {"snapshot": {"masteryAvg": 40, "energy": 90}}\n\n'
    )


def test_tool_call_start_camel_case() -> None:
    assert (
        sse_agui.tool_call_start("call-1", "rag_search")
        == 'event: TOOL_CALL_START\ndata: {"toolCallId": "call-1", "toolName": "rag_search"}\n\n'
    )


def test_custom_name_value() -> None:
    assert (
        sse_agui.custom("plan", {"type": "ASK", "nodes": []})
        == 'event: CUSTOM\ndata: {"name": "plan", "value": {"type": "ASK", "nodes": []}}\n\n'
    )


def test_translate_token_to_text_content() -> None:
    assert (
        sse_agui.translate("token", {"delta": "嗨"})
        == 'event: TEXT_MESSAGE_CONTENT\ndata: {"delta": "嗨"}\n\n'
    )


def test_translate_result_wraps() -> None:
    assert (
        sse_agui.translate("result", {"answer_md": "x"})
        == 'event: RUN_FINISHED\ndata: {"result": {"answer_md": "x"}}\n\n'
    )


def test_translate_error() -> None:
    assert (
        sse_agui.translate("error", {"code": "E", "message": "m"})
        == 'event: RUN_ERROR\ndata: {"code": "E", "message": "m"}\n\n'
    )


def test_translate_plan_to_custom() -> None:
    assert (
        sse_agui.translate("plan", {"type": "ASK", "nodes": []})
        == 'event: CUSTOM\ndata: {"name": "plan", "value": {"type": "ASK", "nodes": []}}\n\n'
    )


def test_translate_trace_start_and_finish_preserve_data() -> None:
    start = sse_agui.translate(
        "trace", {"node": "x", "phase": "start", "status": "running", "elapsed_ms": 0}
    )
    assert start.startswith("event: STEP_STARTED\n")
    assert '"node": "x"' in start
    assert '"phase": "start"' in start  # data 原样保留，仅换事件名

    finish = sse_agui.translate(
        "trace",
        {"node": "x", "phase": "finish", "status": "ok", "elapsed_ms": 5, "note": "done"},
    )
    assert finish.startswith("event: STEP_FINISHED\n")
    assert '"note": "done"' in finish


def test_translate_trace_without_phase_infers_finished_from_status() -> None:
    out = sse_agui.translate("trace", {"node": "understand", "status": "ok", "elapsed_ms": 3})
    assert out.startswith("event: STEP_FINISHED\n")
    assert '"node": "understand"' in out


def test_translate_passthrough_agui_and_unknown_names() -> None:
    # 已是 AG-UI 官方名 / 未知名 → 原样 format_sse 透传。
    assert (
        sse_agui.translate("STATE_DELTA", {"delta": [{"op": "add", "path": "/a", "value": 1}]})
        == 'event: STATE_DELTA\ndata: {"delta": [{"op": "add", "path": "/a", "value": 1}]}\n\n'
    )
    assert sse_agui.translate("custom_thing", {"x": 1}) == 'event: custom_thing\ndata: {"x": 1}\n\n'


def test_stream_quality_scores_canonical_agui_higher_than_legacy_names() -> None:
    canonical = sse_agui.stream_quality(
        [
            ("RUN_STARTED", {"runId": "r1"}),
            ("STEP_STARTED", {"node": "evidence_fetcher"}),
            (
                "STATE_DELTA",
                {
                    "delta": [
                        {
                            "op": "add",
                            "path": "/plan_steps",
                            "value": [{"id": "learn", "status": "active"}],
                        }
                    ]
                },
            ),
            ("TEXT_MESSAGE_CONTENT", {"delta": "你好"}),
            ("STEP_FINISHED", {"node": "evidence_fetcher", "status": "ok"}),
            ("RUN_FINISHED", {"result": {"ok": True}}),
        ]
    )
    legacy = sse_agui.stream_quality(
        [
            ("trace", {"node": "evidence_fetcher", "phase": "start"}),
            ("token", {"delta": "你好"}),
            ("trace", {"node": "evidence_fetcher", "phase": "finish", "status": "ok"}),
            ("result", {"ok": True}),
        ]
    )

    assert canonical.agui_event_rate == 1.0
    assert canonical.lifecycle_coverage == 1.0
    assert canonical.step_coverage == 1.0
    assert canonical.text_coverage == 1.0
    assert canonical.state_sync_coverage == 1.0
    assert canonical.legacy_event_count == 0
    assert canonical.score >= 0.85
    assert canonical.score >= legacy.score + 0.25


@pytest.mark.parametrize(
    ("name", "canonical_events", "legacy_events", "min_uplift"),
    [
        (
            "resource_generation_with_state",
            [
                ("RUN_STARTED", {"runId": "r1"}),
                ("STEP_STARTED", {"node": "resource_designer"}),
                (
                    "STATE_DELTA",
                    {"delta": [{"op": "add", "path": "/stage", "value": "drafting"}]},
                ),
                ("TEXT_MESSAGE_CONTENT", {"delta": "生成讲义"}),
                ("STEP_FINISHED", {"node": "resource_designer", "status": "ok"}),
                ("RUN_FINISHED", {"result": {"resourceId": 1}}),
            ],
            [
                ("trace", {"node": "resource_designer", "phase": "start"}),
                ("token", {"delta": "生成讲义"}),
                ("trace", {"node": "resource_designer", "phase": "finish"}),
                ("result", {"resourceId": 1}),
            ],
            0.45,
        ),
        (
            "tool_loop_with_tool_lifecycle",
            [
                ("RUN_STARTED", {"runId": "r2"}),
                ("STEP_STARTED", {"node": "tool_loop"}),
                ("TOOL_CALL_START", {"toolCallId": "c1", "toolName": "rag.search"}),
                ("TOOL_CALL_ARGS", {"toolCallId": "c1", "delta": "{}"}),
                ("TOOL_CALL_RESULT", {"toolCallId": "c1", "content": {"hits": 2}}),
                ("STEP_FINISHED", {"node": "tool_loop", "status": "ok"}),
                ("RUN_FINISHED", {"result": {"ok": True}}),
            ],
            [
                ("trace", {"node": "tool_loop", "phase": "start"}),
                ("trace", {"node": "tool_loop", "phase": "finish"}),
                ("result", {"ok": True}),
            ],
            0.30,
        ),
        (
            "error_terminal_contract",
            [
                ("RUN_STARTED", {"runId": "r3"}),
                ("STEP_STARTED", {"node": "grounding_critic"}),
                ("STEP_FINISHED", {"node": "grounding_critic", "status": "failed"}),
                ("RUN_ERROR", {"code": "GROUNDING_FAILED", "message": "no citation"}),
            ],
            [
                ("trace", {"node": "grounding_critic", "phase": "start"}),
                ("error", {"code": "GROUNDING_FAILED", "message": "no citation"}),
            ],
            0.30,
        ),
    ],
)
def test_stream_quality_stably_beats_legacy_across_runtime_scenarios(
    name: str,
    canonical_events: list[tuple[str, dict]],
    legacy_events: list[tuple[str, dict]],
    min_uplift: float,
) -> None:
    canonical = sse_agui.stream_quality(canonical_events)
    legacy = sse_agui.stream_quality(legacy_events)

    assert canonical.legacy_event_count == 0, name
    assert canonical.agui_event_rate == 1.0, name
    assert canonical.score >= legacy.score + min_uplift, name
