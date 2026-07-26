from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from pythontopia_graph.graph.resource_graph import (
    ResourceGraph,
    ResourceGraphConfig,
    ResourceGraphError,
)
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.main import app
from pythontopia_graph.nodes.evidence_fetcher import (
    RagHit,
    StaticEvidenceFetcher,
)
from pythontopia_graph.nodes.safety_critic import safety_critic
from pythontopia_graph.schemas.resource import (
    HandoutContent,
    HandoutSection,
    ResourceDraft,
    ResourceProduceRequest,
)


class StubProvider(LlmProvider):
    def __init__(self, payloads: list[dict | None]) -> None:
        self._payloads = list(payloads)

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        payload = self._payloads.pop(0) if self._payloads else None
        return LlmResponse(content_json=payload)


class CountingStubProvider(LlmProvider):
    """StubProvider 变体：记录 chat 调用次数，用于断言震荡熔断少发了一次 design。"""

    def __init__(self, payloads: list[dict | None]) -> None:
        self._payloads = list(payloads)
        self.call_count = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.call_count += 1
        payload = self._payloads.pop(0) if self._payloads else None
        return LlmResponse(content_json=payload)


def request_of(type_: str = "HANDOUT") -> ResourceProduceRequest:
    return ResourceProduceRequest(user_id=1, kc_codes=["PY43-KC-001"], type=type_)  # type: ignore[arg-type]


HIT = RagHit(
    chunk_id="c1",
    page_id=101,
    page_no=12,
    chapter_id=3,
    kc_codes=["PY43-KC-001"],
    text="第 12 页讲解 input 函数。",
    score=0.81,
)


def bad_handout_payload(page_id: int = 999, page_no: int = 999) -> dict:
    """构造会被防幻觉审查打回的合法 v2 IR（正文声称未知 KC）。

    page_id 相同 → 相邻两轮失败原因等价（触发震荡熔断）；page_id 不同 →
    失败原因不同（正常耗尽 MAX_RETRIES）。page_no 仅保留旧 helper 调用兼容性。
    """
    del page_no
    return {
        "title": "讲义",
        "summary": "待核验讲义",
        "sections": [{
            "heading": "section",
            "paragraphs": [f"本段声称 PY43-KC-{page_id} 是课程知识点。"],
            "code_snippets": [],
            "evidence_refs": ["page-101"],
        }],
    }


@pytest.mark.asyncio
async def test_resource_graph_success_path() -> None:
    graph = ResourceGraph(
        provider=StubProvider([None]),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )
    response = await graph.run(request_of("HANDOUT"))
    assert response.status == "SUCCESS"
    assert response.evidence_page_ids == [101]
    assert response.grounding_result.passed is True
    assert response.safety_result.passed is True
    node_names = [step.node for step in response.graph_trace]
    assert any(name.startswith("evidence_fetcher") for name in node_names)
    assert any(name.startswith("ResourceDesignerAgent.handout") for name in node_names)


@pytest.mark.asyncio
async def test_resource_graph_no_evidence_marks_partial() -> None:
    graph = ResourceGraph(
        provider=StubProvider([None]),
        evidence_fetcher=StaticEvidenceFetcher([]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )
    response = await graph.run(request_of("HANDOUT"))
    assert response.status == "PARTIAL"
    assert response.grounding_result.no_evidence_found is True


@pytest.mark.asyncio
async def test_resource_graph_safety_failure_raises() -> None:
    leak_payload = {
        "title": "讲义",
        "summary": "不安全讲义",
        "sections": [{
            "heading": "原版讲义",
            "paragraphs": ["以下是 answer_key 完整答案。"],
            "code_snippets": [],
            "evidence_refs": ["page-101"],
        }],
    }
    graph = ResourceGraph(
        provider=StubProvider([leak_payload]),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )
    with pytest.raises(ResourceGraphError):
        await graph.run(request_of("HANDOUT"))


def _handout_draft(paragraph: str) -> ResourceDraft:
    return ResourceDraft(
        title="讲义",
        summary="PII 正则回归",
        content_format="MARKDOWN",
        handout=HandoutContent(
            sections=[HandoutSection(heading="section", paragraphs=[paragraph])]
        ),
    )


def test_safety_critic_does_not_flag_long_numbers_as_pii() -> None:
    """16 位毫秒时间戳不是身份证：旧正则的运算符优先级错误会误判 PII_DETECTED。

    safety_critic 失败在 resource_graph 里是终止性的（直接 raise，不重试），
    误判等于让含长数字的讲义被永久拒绝。
    """
    result = safety_critic(_handout_draft("时间戳 1719999999999999 表示毫秒。"))

    assert result.passed is True
    assert "PII_DETECTED" not in result.reasons


def test_safety_critic_still_flags_15_and_18_digit_id_numbers() -> None:
    # 中国身份证 15 位（旧）与 18 位（新，末位可为 X）两个分支都必须保留。
    for id_number in ("110101900307451", "11010119900307451X"):
        result = safety_critic(_handout_draft(f"身份证 {id_number} 请勿外泄。"))
        assert result.passed is False
        assert "PII_DETECTED" in result.reasons


@pytest.mark.asyncio
async def test_resource_graph_grounding_failure_retries_then_fails() -> None:
    # 每轮失败原因不同（page_id 递减）→ 不触发震荡熔断，正常耗尽 MAX_RETRIES。
    # MAX_RETRIES=2 → 3 attempts (0, 1, 2)，全部失败后抛 ResourceGraphError。
    provider = CountingStubProvider(
        [bad_handout_payload(999), bad_handout_payload(998), bad_handout_payload(997)]
    )
    graph = ResourceGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )
    with pytest.raises(ResourceGraphError) as exc:
        await graph.run(request_of("HANDOUT"))
    assert exc.value.response.status == "FAILED"
    # 三次 design 全部发起（无早停）
    assert provider.call_count == 3
    # 非震荡退出：trace 不含 oscillation 标记
    assert all(
        step.node != "oscillation_detected" for step in exc.value.response.graph_trace
    )


@pytest.mark.asyncio
async def test_resource_graph_oscillation_breaks_early() -> None:
    # 连续两轮失败原因相同（同一 page_id）→ 震荡熔断：attempt1 后提前终止，
    # 第 3 次 design 不再发起。
    provider = CountingStubProvider(
        [bad_handout_payload(999), bad_handout_payload(999), bad_handout_payload(999)]
    )
    graph = ResourceGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )
    with pytest.raises(ResourceGraphError) as exc:
        await graph.run(request_of("HANDOUT"))

    response = exc.value.response
    assert response.status == "FAILED"
    # 提前终止：只发 2 次 design（attempt0/1），比耗尽 MAX_RETRIES 少 1 次 LLM 调用
    assert provider.call_count == 2
    # trace 标注震荡
    osc = [s for s in response.graph_trace if s.node == "oscillation_detected"]
    assert len(osc) == 1
    assert osc[0].note is not None and "no_progress" in osc[0].note
    # 设计师工位只出现 attempt0/attempt1，没有 attempt2
    designer_attempts = [s.node for s in response.graph_trace if ".attempt" in s.node]
    assert designer_attempts == [
        "ResourceDesignerAgent.handout.attempt0",
        "ResourceDesignerAgent.handout.attempt1",
    ]
    # grounding reasons 说明震荡原因
    assert any("OSCILLATION_DETECTED" in r for r in response.grounding_result.reasons)


@pytest.mark.asyncio
async def test_resource_graph_streaming_oscillation_matches_run() -> None:
    # 流式路径与 run() 行为一致：同样在 attempt1 后震荡熔断，少发一次 design。
    provider = CountingStubProvider(
        [bad_handout_payload(999), bad_handout_payload(999), bad_handout_payload(999)]
    )
    graph = ResourceGraph(
        provider=provider,
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
        config=ResourceGraphConfig(valid_kc_codes={"PY43-KC-001"}),
    )
    events: list[tuple[str, dict]] = []

    async def emit(event: str, payload: dict) -> None:
        events.append((event, payload))

    await graph.run_streaming(request_of("HANDOUT"), emit)

    # 与 run() 一致：只发了 2 次 design
    assert provider.call_count == 2
    trace_nodes = [p.get("node") for (e, p) in events if e == "trace"]
    # 发出了 oscillation_detected 的 trace 事件（start+finish）
    assert "oscillation_detected" in trace_nodes
    # 没有 attempt2 的设计事件
    assert "ResourceDesignerAgent.handout.attempt2" not in trace_nodes
    # 终端为 error 事件（FAILED 分支）
    assert events[-1][0] == "error"
    assert events[-1][1]["code"] == "RESOURCE_GRAPH_FAILED"


def test_route_returns_success_response(monkeypatch) -> None:
    app.state.llm_provider = StubProvider([None])
    app.state.evidence_fetcher = StaticEvidenceFetcher([HIT])
    app.state.valid_kc_codes = {"PY43-KC-001"}
    client = TestClient(app)
    body = {
        "user_id": 1,
        "kc_codes": ["PY43-KC-001"],
        "type": "HANDOUT",
    }
    response = client.post("/agents/resource/produce", json=body)
    assert response.status_code == 200
    assert response.json()["status"] == "SUCCESS"
    del app.state.llm_provider
    del app.state.evidence_fetcher
    del app.state.valid_kc_codes
