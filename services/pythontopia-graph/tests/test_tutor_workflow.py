"""导学工作流：阶段状态机 / 卡片 schema 校验 / SSE run 端到端（stub provider）。"""

from __future__ import annotations

import json

import pytest
import pythontopia_graph.routes.tutor_workflow as tutor_workflow_route

from pythontopia_graph.agents.tutor_workflow_cards import (
    CardGenerationError,
    TutorWorkflowCardAgent,
)
from pythontopia_graph.graph import tutor_workflow_session as session_store
from pythontopia_graph.graph.tutor_workflow_graph import (
    TransitionError,
    decide_actions,
    validate_transition,
)
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher
from pythontopia_graph.routes.tutor_workflow import (
    WorkflowRunEnvelope,
    tutor_workflow_run_stream,
)
from pythontopia_graph.schemas.tutor_workflow import WorkflowProblemContext


class JsonStubProvider(LlmProvider):
    """chat 按调用顺序返回预置 JSON（模拟卡片结构化产出）。"""

    def __init__(self, payloads: list[dict]) -> None:
        self._payloads = list(payloads)
        self.calls = 0
        self.messages: list[dict] = []
        self.kwargs: dict = {}

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.calls += 1
        self.messages = messages
        self.kwargs = kwargs
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

GUIDE_CARD = {
    "problem_restatement": "输入半径，按公式算圆面积并保留 4 位小数输出。",
    "input_output_focus": "输入是一个浮点数；输出必须恰好 4 位小数。",
    "key_observation": "关注输出格式与 π 的取值约定。",
    "starter_questions": ["输入是什么类型？", "你打算怎么保留 4 位小数？"],
    "related_kcs": ["PY43-KC-016"],
}

HIT = RagHit(
    chunk_id="c1",
    page_id=112,
    page_no=12,
    kc_codes=["PY43-KC-016"],
    text="第 12 页讲解 float 与格式化输出。",
    score=0.8,
)


# ── 阶段状态机 ──


def test_phase_transitions_legal_and_illegal() -> None:
    assert validate_transition("READING", "IDEATING") == "IDEATING"
    assert validate_transition("IDEATING", "CODING") == "CODING"
    assert validate_transition("ERROR_FEEDBACK", "READING") == "READING"
    assert validate_transition("AC_REVIEW", "TRANSFER") == "TRANSFER"
    # 辅助事件不改变 Phase。
    assert validate_transition("CODING", "CHAT") == "CODING"
    assert validate_transition("READING", "SKELETON") == "READING"
    # 非法迁移 fail fast。
    with pytest.raises(TransitionError):
        validate_transition("READING", "TRANSFER")
    with pytest.raises(TransitionError):
        validate_transition("AC_REVIEW", "READING")
    with pytest.raises(TransitionError):
        validate_transition("IDEATING", "READING")


def test_decide_actions_gates_transfer_until_post_ac() -> None:
    without = decide_actions("AC_REVIEW", has_post_ac=False)
    assert all(action.event != "TRANSFER" for action in without)
    with_post_ac = decide_actions("AC_REVIEW", has_post_ac=True)
    assert any(action.event == "TRANSFER" for action in with_post_ac)


# ── 卡片 agent ──


@pytest.mark.asyncio
async def test_problem_guide_card_validates_and_fills_defaults() -> None:
    agent = TutorWorkflowCardAgent(JsonStubProvider([GUIDE_CARD]))
    card = await agent.problem_guide(PROBLEM, [HIT], "")
    assert card["problem_restatement"].startswith("输入半径")
    # 服务端默认值补齐（teaching_goal / checkpoint_prompt / reflection_prompt / mentor_role）。
    assert card["teaching_goal"]
    assert card["mentor_role"] == "百晓"


@pytest.mark.asyncio
async def test_schema_violation_retries_once_then_fails() -> None:
    # 两次都缺必填字段 → 重试一次后 SCHEMA_VIOLATION fail fast。
    provider = JsonStubProvider([{"problem_restatement": "只有一个字段"}, {"bad": "still"}])
    agent = TutorWorkflowCardAgent(provider)
    with pytest.raises(CardGenerationError) as excinfo:
        await agent.problem_guide(PROBLEM, [], "")
    assert excinfo.value.bucket == "SCHEMA_VIOLATION"
    assert provider.calls == 2


@pytest.mark.asyncio
async def test_skeleton_requires_two_todos() -> None:
    bad = {
        "description": "骨架",
        "skeleton": "radius = float(input())\nprint(radius)",
    }
    good = {
        "description": "骨架",
        "skeleton": "radius = float(input())\n# TODO: 计算面积\n# TODO: 按 4 位小数输出",
    }
    agent = TutorWorkflowCardAgent(JsonStubProvider([bad, good]))
    card = await agent.skeleton(PROBLEM, "")
    assert card["skeleton"].count("TODO") >= 2


@pytest.mark.asyncio
async def test_workflow_chat_sends_image_parts_to_vision_model() -> None:
    image = "data:image/png;base64,iVBORw0KGgpzbWFsbCBpbWFnZQ=="
    provider = JsonStubProvider([{"content": "先观察图片中的输入和输出。"}])
    agent = TutorWorkflowCardAgent(provider)

    card = await agent.chat(
        PROBLEM,
        "READING",
        "请看这张图",
        [],
        [],
        "",
        images=[image],
    )

    assert card["content"] == "先观察图片中的输入和输出。"
    content = provider.messages[-1]["content"]
    assert content[0]["type"] == "text"
    assert "学生消息: 请看这张图" in content[0]["text"]
    assert content[1] == {"type": "image_url", "image_url": {"url": image}}
    assert provider.kwargs["model_tier"] == "vision"


# ── SSE run 端到端 ──


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


def _envelope(session_id: str, event: str, **kwargs) -> WorkflowRunEnvelope:
    return WorkflowRunEnvelope(
        session_id=session_id,
        user_id=1,
        event=event,  # type: ignore[arg-type]
        problem=PROBLEM,
        **kwargs,
    )


@pytest.mark.asyncio
async def test_reading_run_emits_traces_and_card_result() -> None:
    session_id = "t-wf-reading"
    await session_store.reset_session(1, session_id)
    response = await tutor_workflow_run_stream(
        _envelope(session_id, "READING"),
        provider=JsonStubProvider([GUIDE_CARD]),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        trusted_user_id=1,
    )
    events = await _collect_sse(response)

    trace_nodes = [payload["node"] for event, payload in events if event == "trace"]
    assert "validate_transition" in trace_nodes
    assert "evidence_fetcher" in trace_nodes
    assert "card.problem_guide" in trace_nodes

    results = [payload for event, payload in events if event == "result"]
    assert len(results) == 1
    result = results[0]
    assert result["phase"] == "READING"
    assert result["card_key"] == "problem_guide"
    assert result["card"]["card_id"].startswith(session_id)
    assert result["seq"] == 1
    # READING 阶段动作链含「思路分析」。
    assert any(action["event"] == "IDEATING" for action in result["available_actions"])


@pytest.mark.asyncio
async def test_courseware_retrieval_failure_stops_before_model_generation() -> None:
    class FailingEvidenceFetcher:
        async def fetch(self, **_kwargs):
            raise RuntimeError("rag down")

    session_id = "t-wf-rag-failure"
    await session_store.reset_session(1, session_id)
    provider = JsonStubProvider([GUIDE_CARD])
    response = await tutor_workflow_run_stream(
        _envelope(session_id, "READING"),
        provider=provider,
        evidence_fetcher=FailingEvidenceFetcher(),  # type: ignore[arg-type]
        trusted_user_id=1,
    )

    events = await _collect_sse(response)
    errors = [payload for event, payload in events if event == "error"]
    assert len(errors) == 1
    assert errors[0]["code"] == "TOOL_EXECUTION_FAILED"
    assert not any(event == "result" for event, _ in events)
    assert provider.calls == 0


@pytest.mark.asyncio
async def test_illegal_transition_fails_fast() -> None:
    session_id = "t-wf-illegal"
    await session_store.reset_session(1, session_id)
    # 新会话默认 READING，直接请求 TRANSFER 属非法迁移。
    response = await tutor_workflow_run_stream(
        _envelope(session_id, "TRANSFER"),
        provider=JsonStubProvider([]),
        evidence_fetcher=StaticEvidenceFetcher([]),
        trusted_user_id=1,
    )
    events = await _collect_sse(response)
    errors = [payload for event, payload in events if event == "error"]
    assert len(errors) == 1
    assert errors[0]["code"] == "TRANSITION_VIOLATION"
    assert not any(event == "result" for event, _ in events)


@pytest.mark.asyncio
async def test_error_feedback_requires_diagnosis() -> None:
    session_id = "t-wf-diag"
    await session_store.reset_session(1, session_id)
    response = await tutor_workflow_run_stream(
        _envelope(session_id, "ERROR_FEEDBACK"),
        provider=JsonStubProvider([]),
        evidence_fetcher=StaticEvidenceFetcher([]),
        trusted_user_id=1,
    )
    events = await _collect_sse(response)
    errors = [payload for event, payload in events if event == "error"]
    assert len(errors) == 1
    assert errors[0]["code"] == "INSUFFICIENT_EVIDENCE"


@pytest.mark.asyncio
async def test_phase_progression_and_chat_keeps_phase(monkeypatch) -> None:
    session_id = "t-wf-progress"
    await session_store.reset_session(1, session_id)
    ideate_card = {"analysis": "先读入，再计算，最后格式化输出。", "steps": ["读入", "计算", "输出"]}
    chat_card = {"content": "想想输出格式怎么写。", "referenced_card_ids": []}

    response = await tutor_workflow_run_stream(
        _envelope(session_id, "IDEATING", event_data={"thought_text": "先算面积"}),
        provider=JsonStubProvider([ideate_card]),
        evidence_fetcher=StaticEvidenceFetcher([]),
        trusted_user_id=1,
    )
    events = await _collect_sse(response)
    result = next(payload for event, payload in events if event == "result")
    assert result["phase"] == "IDEATING"

    # CHAT 不改变阶段，且能携带会话内已生成的卡片上下文。
    image = "data:image/png;base64,iVBORw0KGgpzbWFsbCBpbWFnZQ=="
    fetched: list[tuple[int, str, str]] = []

    async def fetch_attachment(user_id, requested_session, attachment_id):
        fetched.append((user_id, requested_session, attachment_id))
        return image

    monkeypatch.setattr(tutor_workflow_route, "fetch_tutor_workflow_attachment", fetch_attachment)
    chat_provider = JsonStubProvider([chat_card])
    response = await tutor_workflow_run_stream(
        _envelope(
            session_id,
            "CHAT",
            event_data={
                "message": "我该怎么保留小数？",
                "attachment_ids": ["5db6f261-913a-4c12-b1c8-f40303dbf8a9"],
            },
        ),
        provider=chat_provider,
        evidence_fetcher=StaticEvidenceFetcher([]),
        trusted_user_id=1,
    )
    events = await _collect_sse(response)
    assert image not in json.dumps([payload for _, payload in events], ensure_ascii=False)
    result = next(payload for event, payload in events if event == "result")
    assert result["phase"] == "IDEATING"
    assert result["card_key"] == "ai_reply"
    assert result["seq"] == 2
    assert chat_provider.kwargs["model_tier"] == "vision"
    assert fetched == [(1, session_id, "5db6f261-913a-4c12-b1c8-f40303dbf8a9")]
    image_part = chat_provider.messages[-1]["content"][-1]
    assert image_part == {"type": "image_url", "image_url": {"url": image}}


@pytest.mark.asyncio
async def test_restore_phase_replays_java_projection() -> None:
    session_id = "t-wf-restore"
    await session_store.reset_session(1, session_id)
    chat_card = {"content": "复盘后可以试试迁移题。", "referenced_card_ids": []}
    restore_cards = [
        {
            "card_id": "card-old",
            "card_key": "post_ac",
            "short_text": "已完成本题复盘",
            "next_practice_direction": "尝试列表变式",
        }
    ]
    restore_chat_history = [
        {"role": "user", "content": "旧问题"},
        {"role": "assistant", "content": "旧回答"},
    ]
    provider = JsonStubProvider([chat_card])
    # 进程内无会话 + Java 投影说阶段是 AC_REVIEW → CHAT 应在 AC_REVIEW 上运行。
    response = await tutor_workflow_run_stream(
        WorkflowRunEnvelope(
            session_id=session_id,
            user_id=1,
            event="CHAT",
            problem=PROBLEM,
            event_data={"message": "接下来练什么？"},
            restore_phase="AC_REVIEW",
            restore_seq=3,
            restore_cards=restore_cards,
            restore_chat_history=restore_chat_history,
        ),
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([]),
        trusted_user_id=1,
    )
    events = await _collect_sse(response)
    result = next(payload for event, payload in events if event == "result")
    assert result["phase"] == "AC_REVIEW"
    assert result["seq"] == 4
    messages = json.dumps(provider.messages, ensure_ascii=False)
    assert "旧问题" in messages
    assert "旧回答" in messages
    assert "[card-old|post_ac]" in messages
    assert any(action["event"] == "TRANSFER" for action in result["available_actions"])


@pytest.mark.asyncio
async def test_chat_uses_server_resolved_card_and_courseware_references() -> None:
    provider = JsonStubProvider([
        {
            "content": "卡片中的循环边界与课件页讲解可以一起对照。",
            "referenced_card_ids": ["wf-test:card-1", "invented-card"],
        }
    ])
    agent = TutorWorkflowCardAgent(provider)

    card = await agent.chat(
        PROBLEM,
        "READING",
        "@card:wf-test:card-1 @courseware:10757 请结合这两处说明。",
        [],
        [],
        "",
        references=[
            {
                "kind": "card",
                "card_id": "wf-test:card-1",
                "card_key": "execution_trace",
                "snippet": "循环变量从 0 变化到 4。",
            },
            {
                "kind": "courseware",
                "page_id": 10757,
                "page_no": 12,
                "chapter_id": 3,
                "title": "for 循环",
                "snippet": "for 循环会依次处理可迭代对象中的元素。",
            },
        ],
    )

    prompt = provider.messages[-1]["content"]
    assert "循环变量从 0 变化到 4。" in prompt
    assert "第 12 页" in prompt
    assert card["referenced_card_ids"] == ["wf-test:card-1"]
    assert card["courseware_refs"] == [
        {
            "page_no": 12,
            "page_id": 10757,
            "chapter_id": 3,
            "snippet": "for 循环会依次处理可迭代对象中的元素。",
        }
    ]
