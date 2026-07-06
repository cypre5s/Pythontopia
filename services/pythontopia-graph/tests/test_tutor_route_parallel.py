"""答疑路由的并发与稳定性：尾部 gather 并行、RAG 降级空证据、SSE 心跳 ping。

fake provider 各 LLM 子调用 sleep 0.2s，用总耗时断言真并行；全程离线。
"""

from __future__ import annotations

import asyncio
import json
import time
from collections.abc import AsyncIterator, Iterator

import pytest

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, RagHit, StaticEvidenceFetcher
from pythontopia_graph.routes import tutor as tutor_routes
from pythontopia_graph.schemas.tutor import TutorAskRequest
from pythontopia_graph.tools.harness import reset_harness_state

HIT = RagHit(
    chunk_id="c1",
    page_id=112,
    page_no=12,
    chapter_id=3,
    kc_codes=["PY43-KC-001"],
    text="第 12 页讲解 for 循环遍历列表。",
    score=0.82,
)


@pytest.fixture(autouse=True)
def _reset_harness() -> Iterator[None]:
    """隔离熔断器/统计：RAG 故障用例会累计 rag 熔断失败计数，防跨测试污染。"""
    reset_harness_state()
    yield
    reset_harness_state()


async def _collect_raw(response) -> list[str]:
    chunks: list[str] = []
    async for chunk in response.body_iterator:
        chunks.append(chunk if isinstance(chunk, str) else chunk.decode("utf-8"))
    return chunks


def _parse_events(chunks: list[str]) -> list[tuple[str, dict]]:
    events: list[tuple[str, dict]] = []
    for block in "".join(chunks).split("\n\n"):
        block = block.strip()
        if not block or block.startswith(":"):  # 跳过 SSE 注释行（心跳 ping）
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


class SleepyTailProvider(LlmProvider):
    """chat 每次 sleep 0.2s 后按 prompt 分派（图解 JSON / 证据注 JSON）；chat_stream 即刻吐答案。"""

    def __init__(self, sleep_s: float = 0.2) -> None:
        self.sleep_s = sleep_s
        self.chat_calls = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.chat_calls += 1
        await asyncio.sleep(self.sleep_s)
        text = "\n".join(m["content"] for m in messages)
        if "图解生成器" in text:
            return LlmResponse(
                content_json={
                    "kind": "mermaid",
                    "source": 'flowchart TD\n  A["for 循环"] --> B["循环体"]',
                }
            )
        return LlmResponse(content_json={"note": "这些课件页讲 for 循环的用法。"})

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield "参考 [页 12]，"
        yield "for 循环可遍历列表。"


async def test_diagram_and_evidence_note_run_in_parallel() -> None:
    provider = SleepyTailProvider(sleep_s=0.2)
    body = TutorAskRequest(
        user_id=1, question="画个 for 循环的流程图", kc_codes=["PY43-KC-001"],
    )
    started = time.monotonic()
    response = await tutor_routes.tutor_ask_stream(
        body, provider=provider, evidence_fetcher=StaticEvidenceFetcher([HIT]),
    )
    chunks = await _collect_raw(response)
    elapsed = time.monotonic() - started

    # 两个 0.2s 的子调用串行需 ≥0.4s；并行后整条流 <0.35s。
    assert provider.chat_calls == 2
    assert elapsed < 0.35, f"尾部未并行：耗时 {elapsed:.3f}s"

    result = _parse_events(chunks)[-1][1]
    assert result["visual_aids"] and result["visual_aids"][0]["kind"] == "mermaid"
    assert result["evidence_note"] == "这些课件页讲 for 循环的用法。"


class ExplodingFetcher:
    """RAG 永远抛错：治理层应重试后回退空证据 bundle。"""

    def __init__(self) -> None:
        self.calls = 0

    async def fetch(
        self, *, kc_codes: list[str], query: str, top_k: int = 6
    ) -> EvidenceBundle:
        self.calls += 1
        raise RuntimeError("rag down")


class PlainStreamProvider(LlmProvider):
    """chat_stream 吐通识答案；chat 返回空（本场景不应被依赖）。"""

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_text="")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield "基于通识知识，for 循环用于遍历序列。"


async def test_rag_failure_falls_back_to_empty_evidence() -> None:
    fetcher = ExplodingFetcher()
    body = TutorAskRequest(user_id=1, question="for 循环怎么用", kc_codes=["PY43-KC-001"])
    response = await tutor_routes.tutor_ask_stream(
        body, provider=PlainStreamProvider(), evidence_fetcher=fetcher,
    )
    events = _parse_events(await _collect_raw(response))

    # 1 次重试后降级（共 2 次尝试），trace 标注降级，流程不中断。
    assert fetcher.calls == 2
    fetch_traces = [
        d for (e, d) in events if e == "trace" and d["node"] == "evidence_fetcher"
    ]
    assert fetch_traces[-1]["chunks"] == 0
    assert "fallback" in fetch_traces[-1]["note"]

    # 空证据自然落入既有「通识知识标注」路径。
    result = events[-1][1]
    assert result["grounding_status"] == "NO_EVIDENCE"
    assert result["based_on_general_knowledge"] is True
    assert result["answer_md"].startswith("基于通识知识")


class SlowReviseProvider(LlmProvider):
    """chat_stream 吐带假页码的答案触发修订；chat（修订/证据注）各 sleep 指定秒数。"""

    def __init__(self, sleep_s: float) -> None:
        self.sleep_s = sleep_s

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        await asyncio.sleep(self.sleep_s)
        text = "\n".join(m["content"] for m in messages)
        if "审查意见" in text:
            return LlmResponse(content_text="参考 [页 12]，修订后的讲解。")
        return LlmResponse(content_text="")

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield "参考 [页 99]，原始讲解。"


async def test_heartbeat_pings_during_slow_non_stream_phases(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # 把心跳阈值压到 0.05s：修订/证据注各 sleep 0.3s 的等待期内必须出现 ping 注释行。
    monkeypatch.setattr(tutor_routes, "_HEARTBEAT_S", 0.05)
    body = TutorAskRequest(user_id=1, question="for 循环怎么用", kc_codes=["PY43-KC-001"])
    response = await tutor_routes.tutor_ask_stream(
        body,
        provider=SlowReviseProvider(sleep_s=0.3),
        evidence_fetcher=StaticEvidenceFetcher([HIT]),
    )
    chunks = await _collect_raw(response)

    pings = [chunk for chunk in chunks if chunk == ": ping\n\n"]
    assert pings, "长等待阶段未发出任何 SSE 心跳注释行"

    # 心跳不破坏事件流：修订仍被采纳、result 正常收尾。
    result = _parse_events(chunks)[-1][1]
    assert result["revised"] is True
    assert result["answer_md"] == "参考 [页 12]，修订后的讲解。"


def test_heartbeat_timer_logic_with_fake_clock() -> None:
    now = {"t": 100.0}
    heartbeat = tutor_routes._SseHeartbeat(heartbeat_s=10.0, clock=lambda: now["t"])

    assert not heartbeat.due()
    now["t"] = 109.9
    assert not heartbeat.due()
    assert heartbeat.seconds_until_due() == pytest.approx(0.1, abs=0.001)

    now["t"] = 110.0
    assert heartbeat.due()
    # 已逾期 → 返回小下限（尽快触发、避免零超时空转）。
    now["t"] = 130.0
    assert heartbeat.seconds_until_due() == 0.05

    heartbeat.mark_sent()
    assert not heartbeat.due()
    assert heartbeat.seconds_until_due() == pytest.approx(10.0, abs=0.001)
