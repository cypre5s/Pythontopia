"""答疑 SSE 流：开场 plan(5 工位) + 馆员→导师→审查官→校对官→安全员 trace + 终态 result。"""

from __future__ import annotations

import asyncio
import json
from collections.abc import AsyncIterator

import pytest

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher
from pythontopia_graph.routes import tutor as tutor_route
from pythontopia_graph.routes.tutor import tutor_ask_stream
from pythontopia_graph.schemas.tutor import TutorAskRequest


class StreamStubProvider(LlmProvider):
    """chat_stream 逐 token 吐出固定答案；chat 仅供 build_diagram 兜底（本测试不触发）。"""

    def __init__(self, answer: str) -> None:
        self._answer = answer

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_text="")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        # 切成几段模拟流式分片。
        mid = len(self._answer) // 2
        for piece in (self._answer[:mid], self._answer[mid:]):
            if piece:
                yield piece


HIT = RagHit(
    chunk_id="c1",
    page_id=112,
    page_no=12,
    chapter_id=3,
    kc_codes=["PY43-KC-001"],
    text="第 12 页讲解 for 循环遍历列表。",
    score=0.82,
)


async def _collect_sse(response) -> list[tuple[str, dict]]:
    raw_parts: list[str] = []
    async for chunk in response.body_iterator:
        raw_parts.append(chunk if isinstance(chunk, str) else chunk.decode("utf-8"))
    raw = "".join(raw_parts)
    events: list[tuple[str, dict]] = []
    for block in raw.split("\n\n"):
        block = block.strip()
        if not block:
            continue
        event = "message"
        data_lines: list[str] = []
        for line in block.split("\n"):
            if line.startswith("event:"):
                event = line[len("event:"):].strip()
            elif line.startswith("data:"):
                data_lines.append(line[len("data:"):].strip())
        _ev = event
        _payload = json.loads("\n".join(data_lines))
        # 迁移后 tutor 发 AG-UI 官方名；反归一化回历史语义，保持下方断言不变
        # （AG-UI 命名正确性由 test_sse_agui 的 translate 单测覆盖）。
        if _ev in ("STEP_STARTED", "STEP_FINISHED"):
            _ev = "trace"
        elif _ev == "TEXT_MESSAGE_CONTENT":
            _ev = "token"
        elif _ev == "RUN_FINISHED":
            _ev, _payload = "result", _payload.get("result", _payload)
        elif _ev == "RUN_ERROR":
            _ev = "error"
        elif _ev == "CUSTOM":
            _ev, _payload = str(_payload.get("name", "custom")), _payload.get("value", _payload)
        events.append((_ev, _payload))
    return events


async def _run(
    question: str,
    kc_codes: list[str],
    answer: str,
    hits: list[RagHit] | None = None,
) -> list[tuple[str, dict]]:
    body = TutorAskRequest(user_id=1, question=question, kc_codes=kc_codes)
    response = await tutor_ask_stream(
        body,
        provider=StreamStubProvider(answer),
        evidence_fetcher=StaticEvidenceFetcher([HIT] if hits is None else hits),
    )
    return await _collect_sse(response)


@pytest.mark.asyncio
async def test_ask_stream_opens_with_five_station_plan() -> None:
    events = await _run("for 循环怎么用", ["PY43-KC-001"], "参考 [页 12]，for 循环可遍历列表。")

    assert events[0][0] == "plan"
    plan = events[0][1]
    assert plan["type"] == "ASK"
    assert [n["kind"] for n in plan["nodes"]] == [
        "evidence",
        "tutor",
        "grounding",
        "hallucination",
        "safety",
    ]


@pytest.mark.asyncio
async def test_ask_stream_runs_all_review_agents() -> None:
    events = await _run("for 循环怎么用", ["PY43-KC-001"], "参考 [页 12]，for 循环可遍历列表。")
    traces = [data for (event, data) in events if event == "trace"]

    # 馆员发完工 trace（带命中段数）。
    evidence = [t for t in traces if t["node"] == "evidence_fetcher"]
    assert evidence and evidence[-1].get("chunks") == 1

    # 导师 + 三道审查都要有 start→finish。
    for node in ("TutorAgent", "grounding_critic", "hallucination_detector", "safety_critic"):
        phases = [t.get("phase") for t in traces if t["node"] == node]
        assert "start" in phases, f"{node} missing start"
        assert "finish" in phases, f"{node} missing finish"


@pytest.mark.asyncio
async def test_ask_stream_semantically_checks_strong_course_claims() -> None:
    answer = "根据教材第 12 页 [页 12]，for 循环只能遍历列表。"

    class DistortionProvider(StreamStubProvider):
        def __init__(self) -> None:
            super().__init__(answer)
            self.semantic_calls = 0

        async def chat(self, messages, **kwargs):  # type: ignore[override]
            prompt = str(messages[-1].get("content", ""))
            if "判断论述是否被证据支持" in prompt:
                self.semantic_calls += 1
                return LlmResponse(content_json={"supported": False, "reason": "证据没有排除字符串"})
            return LlmResponse(content_text="")

    provider = DistortionProvider()
    response = await tutor_ask_stream(
        TutorAskRequest(user_id=1, question="for 循环能遍历哪些对象？"),
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
    )
    events = await _collect_sse(response)
    result = events[-1][1]

    assert provider.semantic_calls == 1
    assert result["grounding_result"]["passed"] is False
    assert result["grounding_status"] == "FAILED"


@pytest.mark.asyncio
async def test_ask_stream_runs_answer_and_reviews_through_fixed_autogen_stages(monkeypatch) -> None:
    seen: list[str] = []
    original = tutor_route.run_autogen_tutor_workflow

    async def observe_autogen(state, stages, **kwargs):
        seen.extend(name for name, _handler in stages)
        return await original(state, stages, **kwargs)

    monkeypatch.setattr(tutor_route, "run_autogen_tutor_workflow", observe_autogen)
    events = await _run("for 循环怎么用", ["PY43-KC-001"], "参考 [页 12]，for 循环可遍历列表。")

    assert seen == ["TutorAgent", "grounding_critic", "hallucination_detector", "safety_critic"]
    assert events[-1][0] == "result"


@pytest.mark.asyncio
async def test_ask_stream_keeps_image_payload_out_of_autogen_state(monkeypatch) -> None:
    image = "data:image/png;base64,iVBORw0KGgo="
    observed: list[dict] = []
    original = tutor_route.run_autogen_tutor_workflow

    async def observe_autogen(state, stages, **kwargs):
        observed.append(state)
        return await original(state, stages, **kwargs)

    class ImageProvider(StreamStubProvider):
        def __init__(self) -> None:
            super().__init__("图中是一个列表。")
            self.last_messages = None
            self.last_kwargs = None

        async def chat_stream(self, messages, **kwargs):  # type: ignore[override]
            self.last_messages = messages
            self.last_kwargs = kwargs
            yield "图中是一个列表。"

    provider = ImageProvider()
    monkeypatch.setattr(tutor_route, "run_autogen_tutor_workflow", observe_autogen)
    response = await tutor_ask_stream(
        TutorAskRequest(user_id=1, question="请看图", images=[image]),
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([]),
    )
    events = await _collect_sse(response)

    assert events[-1][0] == "result"
    assert observed and "images" not in observed[0]
    assert image not in str(observed[0])
    assert provider.last_messages[-1]["content"] == [
        {"type": "text", "text": "请看图"},
        {"type": "image_url", "image_url": {"url": image}},
    ]
    assert provider.last_kwargs["model_tier"] == "vision"


@pytest.mark.asyncio
async def test_ask_stream_disconnect_cancels_autogen_tutor_stage() -> None:
    started = asyncio.Event()
    cancelled = asyncio.Event()

    class BlockingProvider(StreamStubProvider):
        async def chat_stream(self, messages, **kwargs):  # type: ignore[override]
            started.set()
            try:
                await asyncio.Event().wait()
            except asyncio.CancelledError:
                cancelled.set()
                raise
            yield ""

    response = await tutor_ask_stream(
        TutorAskRequest(user_id=1, question="for 循环怎么用"),
        provider=BlockingProvider(""),
        evidence_fetcher=StaticEvidenceFetcher([]),
    )
    iterator = response.body_iterator
    while not started.is_set():
        await asyncio.wait_for(iterator.__anext__(), timeout=1)

    await asyncio.wait_for(iterator.aclose(), timeout=1)
    await asyncio.wait_for(cancelled.wait(), timeout=1)


@pytest.mark.asyncio
async def test_quiz_question_and_judgement_use_autogen_stages(monkeypatch) -> None:
    seen: list[str] = []
    original = tutor_route.run_autogen_tutor_call

    async def observe_autogen(stage_name, call):
        seen.append(stage_name)
        return await original(stage_name, call)

    monkeypatch.setattr(tutor_route, "run_autogen_tutor_call", observe_autogen)
    provider = StreamStubProvider("")

    ask_response = await tutor_ask_stream(
        TutorAskRequest(user_id=1, question="for 循环", context="QUIZ_TURN_1"),
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([]),
    )
    ask_events = await _collect_sse(ask_response)

    judge_response = await tutor_ask_stream(
        TutorAskRequest(
            user_id=1,
            question="for item in items",
            context="QUIZ_JUDGE",
            conversation_history=[{"role": "assistant", "content": "请补全 for 循环语法"}],
        ),
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
    )
    judge_events = await _collect_sse(judge_response)

    assert seen == ["quiz_question", "quiz_judge"]
    assert ask_events[-1][1]["quiz"]["phase"] == "ask"
    assert judge_events[-1][1]["quiz"]["phase"] == "judge"


@pytest.mark.asyncio
async def test_ask_stream_tokens_and_result_payload() -> None:
    answer = "参考 [页 12]，for 循环可遍历列表。"
    events = await _run("for 循环怎么用", ["PY43-KC-001"], answer)

    tokens = "".join(d["delta"] for (e, d) in events if e == "token")
    assert tokens == answer

    assert events[-1][0] == "result"
    result = events[-1][1]
    # 双套键：camelCase（前端）+ snake_case（Java 落库）。
    assert result["answerMd"] == answer
    assert result["answer_md"] == answer
    assert result["groundingStatus"] == "PASSED"
    assert result["grounding_status"] == "PASSED"
    assert result["evidencePageIds"] == [112]
    assert result["review"]["safety"]["passed"] is True
    # 课件证据明细（供前端「课件证据」面板渲染）：页码 + 原文片段。
    assert result["evidence"] == [
        {
            "page_id": 112,
            "page_no": 12,
            "snippet": "第 12 页讲解 for 循环遍历列表。",
            "score": 0.82,
            # KC 图谱感知扩展（R-16）：非前置证据默认 False。
            "via_prerequisite": False,
        }
    ]


@pytest.mark.asyncio
async def test_ask_stream_general_knowledge_when_no_evidence() -> None:
    # 无 KC → StaticEvidenceFetcher 命中为空 → 通识知识。
    events = await _run("天气怎么样", [], "基于通识知识，这与 Python 无关。", hits=[])
    result = events[-1][1]
    assert result["groundingStatus"] == "NO_EVIDENCE"
    assert result["based_on_general_knowledge"] is True


@pytest.mark.asyncio
async def test_ask_stream_emits_code_runner_trace_for_verification() -> None:
    # 带 ```python 片段 + 追问「为什么输出不对」→ 触发代码运行验证，附 code_runner trace（前端「已验证」徽标）。
    question = "这段代码为什么输出不对？\n```python\nprint(2 * 3)\n```"
    events = await _run(question, ["PY43-KC-001"], "参考 [页 12]，对照真实输出定位问题。")
    trace_nodes = [data["node"] for (event, data) in events if event == "trace"]
    assert "code_runner" in trace_nodes


@pytest.mark.asyncio
async def test_ask_stream_routes_hard_question_through_debate() -> None:
    # 「哪个更…为什么不能…」触发辩论复核 → 流水线出现 solver/critic/referee 三步 trace。
    question = "列表和元组哪个更好，为什么不能混用？"
    events = await _run(question, ["PY43-KC-001"], "参考 [页 12]，二者语义不同。")
    trace_nodes = [data["node"] for (event, data) in events if event == "trace"]
    for node in ("solver", "critic", "referee"):
        assert node in trace_nodes, f"debate trace missing node: {node}"
