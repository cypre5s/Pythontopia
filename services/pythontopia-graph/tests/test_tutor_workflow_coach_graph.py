from __future__ import annotations

import json

import pytest

from pythontopia_graph.graph import tutor_workflow_session as session_store
from pythontopia_graph.graph.tutor_workflow_graph import TutorWorkflowGraph
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import StaticEvidenceFetcher
from pythontopia_graph.routes.tutor_workflow import (
    WorkflowRunEnvelope,
    tutor_workflow_run_stream,
)
from pythontopia_graph.schemas.tutor_workflow import (
    TutorStudentState,
    WorkflowDiagnosisContext,
    WorkflowProblemContext,
    WorkflowRunRequest,
)


class JsonStubProvider(LlmProvider):
    def __init__(self, payloads: list[dict]) -> None:
        self._payloads = list(payloads)
        self.calls = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.calls += 1
        payload = self._payloads.pop(0) if self._payloads else {}
        return LlmResponse(content_text=json.dumps(payload, ensure_ascii=False))

    async def chat_stream(self, messages, **kwargs):  # type: ignore[override]
        yield ""


PROBLEM = WorkflowProblemContext(
    problem_id=476,
    title="圆面积计算",
    statement="读取半径 radius，输出圆面积，保留 4 位小数。",
    input_description="一个浮点数 radius",
    output_description="面积，保留 4 位小数",
    samples=[{"input": "25", "output": "1963.4375"}],
    kc_codes=["PY43-KC-016"],
    difficulty="Low",
)

DIAGNOSIS = WorkflowDiagnosisContext(
    submission_id=7,
    verdict="WA",
    code="radius=float(input())\nprint(radius)",
    failure_reason="答案错误",
    passed_cases=2,
    total_cases=5,
    consecutive_errors=1,
)

TRACE_CARD = {
    "summary": "用样例走一遍当前代码。",
    "trace_target": "学生提交代码",
    "input_used": "25",
    "steps": [
        {
            "step": 1,
            "line_ref": "1",
            "action": "读取 radius",
            "variables": {"radius": 25.0},
            "note": "",
        },
        {
            "step": 2,
            "line_ref": "2",
            "action": "直接输出半径",
            "variables": {"printed": 25.0},
            "note": "这里还没有计算圆面积。",
        },
    ],
    "key_insight": "程序实际输出半径，不是面积。",
}


async def _collect_sse(response) -> list[tuple[str, dict]]:
    raw_parts: list[str] = []
    async for chunk in response.body_iterator:
        raw_parts.append(chunk if isinstance(chunk, str) else chunk.decode("utf-8"))
    events: list[tuple[str, dict]] = []
    for block in "".join(raw_parts).split("\n\n"):
        block = block.strip()
        if not block or block.startswith(":"):
            continue
        event = "message"
        data_lines: list[str] = []
        for line in block.split("\n"):
            if line.startswith("event:"):
                event = line[len("event:") :].strip()
            elif line.startswith("data:"):
                data_lines.append(line[len("data:") :].strip())
        payload = json.loads("\n".join(data_lines))
        if event in ("STEP_STARTED", "STEP_FINISHED"):
            event = "trace"
        elif event == "RUN_FINISHED":
            event, payload = "result", payload.get("result", payload)
        elif event == "RUN_ERROR":
            event = "error"
        events.append((event, payload))
    return events


@pytest.mark.asyncio
async def test_coach_decision_controls_generated_card() -> None:
    events: list[tuple[str, dict]] = []

    async def emit(event: str, data: dict) -> None:
        events.append((event, data))

    graph = TutorWorkflowGraph(
        JsonStubProvider([TRACE_CARD]),
        StaticEvidenceFetcher([]),
        emit=emit,
    )
    state = await graph.run(
        {
            "request": WorkflowRunRequest(
                session_id="wf-coach-graph",
                user_id=1,
                event="ERROR_FEEDBACK",
                problem=PROBLEM,
                diagnosis=DIAGNOSIS,
                student_state=TutorStudentState(
                    session_id="wf-coach-graph",
                    user_id=1,
                    problem_id=476,
                    phase="CODING",
                    latest_verdict="WA",
                    consecutive_errors=1,
                ),
            ),
            "current_phase": "CODING",
            "last_cards": [],
            "chat_history": [],
        }
    )

    assert state["coach_decision"].next_event == "EXECUTION_TRACE"
    assert state["effective_event"] == "EXECUTION_TRACE"
    assert state["card_key"] == "execution_trace"
    assert state["card"]["key_insight"] == "程序实际输出半径，不是面积。"
    assert state["student_state"].next_best_action == "EXECUTION_TRACE"
    assert state["available_actions"][0].event == "EXECUTION_TRACE"
    assert state["available_actions"][0].recommended is True
    trace_nodes = [payload["node"] for event, payload in events if event == "trace"]
    assert "coach.decide" in trace_nodes


@pytest.mark.asyncio
async def test_stream_result_exposes_coach_decision_and_effective_event() -> None:
    session_id = "wf-coach-route"
    await session_store.reset_session(session_id)
    response = await tutor_workflow_run_stream(
        WorkflowRunEnvelope(
            session_id=session_id,
            user_id=1,
            event="ERROR_FEEDBACK",
            problem=PROBLEM,
            diagnosis=DIAGNOSIS,
            student_state=TutorStudentState(
                session_id=session_id,
                user_id=1,
                problem_id=476,
                phase="CODING",
                latest_verdict="WA",
                consecutive_errors=1,
            ),
        ),
        provider=JsonStubProvider([TRACE_CARD]),
        evidence_fetcher=StaticEvidenceFetcher([]),
    )

    events = await _collect_sse(response)
    result = next(payload for event, payload in events if event == "result")
    assert result["event"] == "EXECUTION_TRACE"
    assert result["card_key"] == "execution_trace"
    assert result["agentic"] is True
    assert result["coach_decision"]["next_event"] == "EXECUTION_TRACE"
    assert result["student_state"]["next_best_action"] == "EXECUTION_TRACE"
